# APIトランザクション一覧・rollback詳細設計

## 1. 文書情報

| 項目 | 内容 |
|---|---|
| 文書ID | D-TX-MATRIX |
| 対象 | Backendの全HTTP APIと、APIから呼び出されるDB/外部API |
| 実装確認日 | 2026-10-19 |
| 基本設計 | [基本設計書](../basic-design.md) |
| API契約 | [API詳細設計](api-design.md) |
| DB | [DB詳細設計](database-design.md) |
| 認証・認可 | [認証・認可詳細設計](security-design.md) |
| 外部連携 | [外部API連携詳細設計](external-api-design.md) |
| トランザクション制御の説明 | [既存のトランザクション制御設計](transaction-control-design.md) |

本書では、全HTTP endpointの通常処理境界を俯瞰表にし、例外・失敗ごとのrollback範囲を詳細表にする。現行ソースで確認できる動作を記載し、フレームワーク規約による推定と未確認事項は区別する。

## 2. 表の読み方

### 2.1 APIごとのトランザクション一覧

この表は各APIが行うDB操作、外部I/O、明示的トランザクション境界を俯瞰する。正常系の処理単位ごとに1行とし、APIが複数のTXや副作用を持つ場合は同じAPIを繰り返す。たとえばユーザ作成は、業務TX内のユーザINSERT、履歴INSERT、独立した監査TXの3行に分ける。

| 列 | 意味 |
|---|---|
| API | HTTPメソッドと実際のパス |
| TX単位 | Controller/Service/Repository/外部クライアントなど、その行の処理責務 |
| 明示的TX境界 | コードに宣言された`@Transactional`の範囲・属性。未指定は「アプリ明示TXなし」 |
| DB操作/対象 | その行が行うDB操作と対象を1項目 |
| 外部・非DB操作 | 外部HTTP、副作用を持つセッション操作などを1項目 |
| 正常時 | その処理単位の正常終了、commitまたは応答 |
| 失敗時 | API別rollback表への参照、またはローカルTXがない場合の結果 |

### 2.2 API別rollback表

この表は、API内の失敗点ごとに、例外の種類、例外が発生するTX境界、rollbackされるデータを示す。1行は**1つの例外発生点と1つのrollback対象**。同一例外で複数の先行書き込みがrollbackされる場合は、API・発生点・例外・TX境界を繰り返し、rollback対象を1セル1項目で記す。

| 列 | 意味 |
|---|---|
| API | HTTPメソッドと実際のパス |
| 例外発生点 | 実行順で例外が発生、またはTX境界へ伝播する処理 |
| 例外/エラー | ソース上確認できる例外型・エラーコード。型が確定しないcommit障害はその旨を記載 |
| トランザクション境界 | 例外が抜けるTXの所有者/属性。TX開始前・ローカル業務TXなしも明記 |
| rollback対象 | そのTXが取り消す、同一TX内で先行して書き込んだ永続データ1項目。該当なしは理由付きで記載 |

**読み方の例:** `PUT /api/users/{id}` でロール変更後に`app_user` UPDATEのflushが失敗すると、同じUserService業務TX内で先にINSERTした`role_history`もrollbackされる。このため、同じ例外を2行に分けて、rollback対象をそれぞれ`app_user`更新内容と`role_history` INSERTとする。失敗したINSERT自体が未保存なら、rollback対象は「なし（成功した先行書き込みなし）」とする。

### 2.3 共通の読み方

- `@Transactional` の既定伝播は`REQUIRED`。コード上の明示設定とSpringの既定規則を混ぜずに読む。
- 例外でrollbackされるかは、HTTP statusではなく、例外型と例外がTX proxy境界を越えるかで判断する。
- 表内のrollback対象はローカルDBの永続データに限る。外部API、HTTPセッション等の状態はローカルDB rollbackで戻らないため、別途説明する。
- `AuditService.record()`は`REQUIRES_NEW`。監査TXのcommit/rollbackと、呼出元業務TXのcommit/rollbackを別々に見る。
- `なし（読み取りのみ）`、`なし（書き込み前）`、`なし（ローカルDB更新なし）`は、それぞれ異なる理由でローカルDBのrollback対象がないことを表す。

## 3. APIごとのトランザクション一覧

| API | TX単位 | 明示的TX境界 | DB操作/対象 | 外部・非DB操作 | 正常時 | 失敗時 |
|---|---|---|---|---|---|---|
| `GET /api/auth/me` | `AuthController.me` 未認証分岐 | アプリ明示TXなし | なし | なし | `authenticated=false`を返却 | rollback詳細表を参照 |
| `GET /api/auth/me` | `AuthController.currentUserBody` | アプリ明示TXなし | `app_user SELECT` | なし | `CurrentUser`を返却 | rollback詳細表を参照 |
| `POST /api/auth/login` | `AppUserDetailsService.loadUserByUsername` | アプリ明示TXなし | `app_user SELECT` | なし | `UserDetails`を返却 | rollback詳細表を参照 |
| `POST /api/auth/login` | `AuthController.login` セッションID変更 | アプリ明示TXなし | なし | HTTPセッションID変更 | 新しいセッションIDを設定 | rollback詳細表を参照 |
| `POST /api/auth/login` | `AuthController.login` SecurityContext保存 | アプリ明示TXなし | なし | SecurityContextをHTTPセッションへ保存 | SecurityContextを保存 | rollback詳細表を参照 |
| `POST /api/auth/login` | `AuditService.record(LOGIN)` | `REQUIRES_NEW` | `audit_log INSERT` | なし | LOGIN監査TXをcommit | rollback詳細表を参照 |
| `POST /api/auth/login` | `AuthController.currentUserBody` | アプリ明示TXなし | `app_user SELECT` | なし | `CurrentUser`を返却 | rollback詳細表を参照 |
| `POST /api/auth/logout` | `AuthController.logout` セッション無効化 | アプリ明示TXなし | なし | HTTPセッション無効化 | セッションを無効化 | rollback詳細表を参照 |
| `POST /api/auth/logout` | `AuthController.logout` SecurityContext消去 | アプリ明示TXなし | なし | SecurityContext消去 | SecurityContextを消去 | rollback詳細表を参照 |
| `POST /api/auth/logout` | `AuditService.record(LOGOUT)` | `REQUIRES_NEW` | `audit_log INSERT` | なし | LOGOUT監査TXをcommit | rollback詳細表を参照 |
| `GET /api/users` | `UserService.search` | `readOnly=true` | `app_user SELECT` | なし | 検索結果を返却し読取TXを終了 | rollback詳細表を参照 |
| `POST /api/users` | `UserService.create` 重複確認 | `REQUIRED` | `app_user SELECT` | なし | 重複なしで登録を続行 | rollback詳細表を参照 |
| `POST /api/users` | `UserService.create` | `REQUIRED` | `app_user INSERT` | なし | 業務TXに参加 | rollback詳細表を参照 |
| `POST /api/users` | `UserService.create` | `REQUIRED` | `role_history INSERT` | なし | 業務TXに参加 | rollback詳細表を参照 |
| `POST /api/users` | `AuditService.record(USER_CREATE)` | `REQUIRES_NEW` | `audit_log INSERT` | なし | USER_CREATE監査TXをcommit | rollback詳細表を参照 |
| `PUT /api/users/{id}` | `UserService.update` 対象取得 | `REQUIRED` | `app_user SELECT` | なし | 更新処理を続行 | rollback詳細表を参照 |
| `PUT /api/users/{id}` | `UserService.update` ロール変更 | `REQUIRED` | `role_history INSERT` | なし | 業務TXに参加 | rollback詳細表を参照 |
| `PUT /api/users/{id}` | `UserService.update` | `REQUIRED` | `app_user UPDATE` | なし | 業務TXに参加 | rollback詳細表を参照 |
| `PUT /api/users/{id}` | `AuditService.record(USER_UPDATE)` | `REQUIRES_NEW` | `audit_log INSERT` | なし | USER_UPDATE監査TXをcommit | rollback詳細表を参照 |
| `POST /api/users/{id}/disable` | `UserService.disable` 対象取得 | `REQUIRED` | `app_user SELECT` | なし | 無効化処理を続行 | rollback詳細表を参照 |
| `POST /api/users/{id}/disable` | `UserService.disable` | `REQUIRED` | `app_user.status UPDATE` | なし | 業務TXに参加 | rollback詳細表を参照 |
| `POST /api/users/{id}/disable` | `AuditService.record(USER_DISABLE)` | `REQUIRES_NEW` | `audit_log INSERT` | なし | USER_DISABLE監査TXをcommit | rollback詳細表を参照 |
| `GET /api/users/{id}/role-history` | `UserService.roleHistories` | `readOnly=true` | `role_history SELECT` | なし | 履歴を返却し読取TXを終了 | rollback詳細表を参照 |
| `GET /api/transactions` | `ExternalApiClient.getWithRetry` | アプリ明示TXなし | なし | `GET /external/transactions` | 外部一覧応答を返却 | rollback詳細表を参照 |
| `GET /api/transactions/{id}` | `ExternalApiClient.getWithRetry` | アプリ明示TXなし | なし | `GET /external/transactions/{id}` | 外部詳細応答を返却 | rollback詳細表を参照 |
| `POST /api/transactions` | `ExternalApiClient.post` | アプリ明示TXなし | なし | `POST /external/transactions` | 外部取引登録結果を受け取る | rollback詳細表を参照 |
| `POST /api/transactions` | `AuditService.record(TX_REGISTER)` | `REQUIRES_NEW` | `audit_log INSERT` | なし | TX_REGISTER監査TXをcommit | rollback詳細表を参照 |

同一の`REQUIRED`行は同じServiceトランザクションに参加する。Repositoryメソッド単体の既定境界はコード上の明示設定ではなく、[APIトランザクション制御詳細設計](transaction-control-design.md)のRepository規約を参照。

## 4. API別rollback表

| API | 例外発生点 | 例外/エラー | トランザクション境界 | rollback対象（1行1項目） |
|---|---|---|---|---|
| `GET /api/auth/me` | 認証済みユーザ検索でユーザ行がなく`orElseThrow()`が実行される | `NoSuchElementException` | アプリ明示TXなし | なし（DB更新なし） |
| `GET /api/auth/me` | `findByUsername`のDB読み取りが失敗する | `DataAccessException` | アプリ明示TXなし | なし（DB更新なし） |
| `POST /api/auth/login` | JSON要求の解析に失敗する | `HttpMessageNotReadableException` | Controller到達前 | なし（DB更新なし） |
| `POST /api/auth/login` | 資格情報が一致せず認証例外がcatchされる | `AuthenticationException` | アプリ明示TXなし | なし（ユーザ行の更新なし） |
| `POST /api/auth/login` | ユーザ状態がDISABLEDで認証例外がcatchされる | `DisabledException` | アプリ明示TXなし | なし（ユーザ行の更新なし） |
| `POST /api/auth/login` | 認証用ユーザ検索がDB障害となる | `DataAccessException` | アプリ明示TXなし | なし（ユーザ行の更新なし） |
| `POST /api/auth/login` | 認証失敗監査`save`が失敗する | `DataAccessException` | AuditServiceの`REQUIRES_NEW` | なし（監査INSERT未完了） |
| `POST /api/auth/login` | 認証失敗監査commitが失敗する | commit例外（具体型は実行時依存） | AuditServiceの`REQUIRES_NEW` | `audit_log`へのLOGIN監査INSERT |
| `POST /api/auth/login` | 認証成功監査`save`が失敗する | `DataAccessException` | AuditServiceの`REQUIRES_NEW` | なし（監査INSERT未完了） |
| `POST /api/auth/login` | 認証成功監査commitが失敗する | commit例外（具体型は実行時依存） | AuditServiceの`REQUIRES_NEW` | `audit_log`へのLOGIN監査INSERT |
| `POST /api/auth/login` | セッション保存後の`currentUserBody()`検索でユーザ行がない | `NoSuchElementException` | アプリ明示TXなし | なし（DB更新なし） |
| `POST /api/auth/login` | セッション保存後のユーザ再検索がDB障害となる | `DataAccessException` | アプリ明示TXなし | なし（DB更新なし） |
| `POST /api/auth/logout` | LOGOUT監査`save`が失敗する | `DataAccessException` | AuditServiceの`REQUIRES_NEW` | なし（監査INSERT未完了） |
| `POST /api/auth/logout` | LOGOUT監査commitが失敗する | commit例外（具体型は実行時依存） | AuditServiceの`REQUIRES_NEW` | `audit_log`へのLOGOUT監査INSERT |
| `GET /api/users` | role/statusの型変換に失敗する | `MethodArgumentTypeMismatchException` | Service TX開始前 | なし（DB更新なし） |
| `GET /api/users` | `UserService.search()`のSELECTがDB障害となる | `DataAccessException` | UserService.searchの`readOnly` TX | なし（読み取りのみ） |
| `GET /api/users` | readOnly TX終了処理が失敗する | TX終了例外（具体型は実行時依存） | UserService.searchの`readOnly` TX | なし（読み取りのみ） |
| `POST /api/users` | JSON要求の解析に失敗する | `HttpMessageNotReadableException` | Controller到達前 | なし（DB更新なし） |
| `POST /api/users` | `@Valid`入力検証に失敗する | `MethodArgumentNotValidException` | UserService TX開始前 | なし（書き込み前） |
| `POST /api/users` | username重複確認SELECTがDB障害となる | `DataAccessException` | UserServiceの`REQUIRED` TX | なし（保存前） |
| `POST /api/users` | username重複が検出される | `BusinessException`（DUPLICATE_USER） | UserServiceの`REQUIRED` TX | なし（保存前） |
| `POST /api/users` | `app_user` INSERTが失敗する | `DataAccessException` | UserServiceの`REQUIRED` TX | なし（成功した先行書き込みなし） |
| `POST /api/users` | `role_history` INSERTが失敗する | `DataAccessException` | UserServiceの`REQUIRED` TX | `app_user`へのINSERT |
| `POST /api/users` | USER_CREATE監査`save`が失敗する | `DataAccessException` | AuditServiceの`REQUIRES_NEW` | なし（監査INSERT未完了） |
| `POST /api/users` | USER_CREATE監査例外が業務TXへ伝播する | `DataAccessException` | UserServiceの`REQUIRED` TX | `app_user`へのINSERT |
| `POST /api/users` | USER_CREATE監査例外が業務TXへ伝播する | `DataAccessException` | UserServiceの`REQUIRED` TX | `role_history`へのINSERT |
| `POST /api/users` | USER_CREATE監査TX commitが失敗する | commit例外（具体型は実行時依存） | AuditServiceの`REQUIRES_NEW` | `audit_log`へのUSER_CREATE監査INSERT |
| `POST /api/users` | 監査commit例外が業務TXへ伝播する | commit例外（具体型は実行時依存） | UserServiceの`REQUIRED` TX | `app_user`へのINSERT |
| `POST /api/users` | 監査commit例外が業務TXへ伝播する | commit例外（具体型は実行時依存） | UserServiceの`REQUIRED` TX | `role_history`へのINSERT |
| `POST /api/users` | UserService業務TXのcommitが失敗する | commit例外（具体型は実行時依存） | UserServiceの`REQUIRED` TX | `app_user`へのINSERT |
| `POST /api/users` | UserService業務TXのcommitが失敗する | commit例外（具体型は実行時依存） | UserServiceの`REQUIRED` TX | `role_history`へのINSERT |
| `PUT /api/users/{id}` | JSON要求の解析に失敗する | `HttpMessageNotReadableException` | Controller到達前 | なし（DB更新なし） |
| `PUT /api/users/{id}` | `@Valid`入力検証に失敗する | `MethodArgumentNotValidException` | UserService TX開始前 | なし（書き込み前） |
| `PUT /api/users/{id}` | 対象ユーザSELECTがDB障害となる | `DataAccessException` | UserServiceの`REQUIRED` TX | なし（更新前） |
| `PUT /api/users/{id}` | 対象ユーザが見つからない | `BusinessException`（USER_NOT_FOUND） | UserServiceの`REQUIRED` TX | なし（更新前） |
| `PUT /api/users/{id}` | 操作者本人をDISABLEDにしようとする | `BusinessException`（SELF_DISABLE） | UserServiceの`REQUIRED` TX | なし（更新前） |
| `PUT /api/users/{id}` | ロール変更時の`role_history` INSERTが失敗する | `DataAccessException` | UserServiceの`REQUIRED` TX | なし（app_user更新前） |
| `PUT /api/users/{id}` | `app_user` UPDATEのflushが失敗する | `DataAccessException` | UserServiceの`REQUIRED` TX | `app_user`の更新内容 |
| `PUT /api/users/{id}` | `app_user` UPDATEのflushが失敗する（ロール変更時） | `DataAccessException` | UserServiceの`REQUIRED` TX | `role_history`へのINSERT |
| `PUT /api/users/{id}` | USER_UPDATE監査`save`が失敗する | `DataAccessException` | AuditServiceの`REQUIRES_NEW` | なし（監査INSERT未完了） |
| `PUT /api/users/{id}` | USER_UPDATE監査例外が業務TXへ伝播する | `DataAccessException` | UserServiceの`REQUIRED` TX | `app_user`の更新内容 |
| `PUT /api/users/{id}` | USER_UPDATE監査例外が業務TXへ伝播する（ロール変更時） | `DataAccessException` | UserServiceの`REQUIRED` TX | `role_history`へのINSERT |
| `PUT /api/users/{id}` | USER_UPDATE監査TX commitが失敗する | commit例外（具体型は実行時依存） | AuditServiceの`REQUIRES_NEW` | `audit_log`へのUSER_UPDATE監査INSERT |
| `PUT /api/users/{id}` | 監査commit例外が業務TXへ伝播する | commit例外（具体型は実行時依存） | UserServiceの`REQUIRED` TX | `app_user`の更新内容 |
| `PUT /api/users/{id}` | 監査commit例外が業務TXへ伝播する（ロール変更時） | commit例外（具体型は実行時依存） | UserServiceの`REQUIRED` TX | `role_history`へのINSERT |
| `PUT /api/users/{id}` | UserService業務TXのcommitが失敗する | commit例外（具体型は実行時依存） | UserServiceの`REQUIRED` TX | `app_user`の更新内容 |
| `PUT /api/users/{id}` | UserService業務TXのcommitが失敗する（ロール変更時） | commit例外（具体型は実行時依存） | UserServiceの`REQUIRED` TX | `role_history`へのINSERT |
| `POST /api/users/{id}/disable` | 対象ユーザSELECTがDB障害となる | `DataAccessException` | UserServiceの`REQUIRED` TX | なし（更新前） |
| `POST /api/users/{id}/disable` | 対象ユーザが見つからない | `BusinessException`（USER_NOT_FOUND） | UserServiceの`REQUIRED` TX | なし（更新前） |
| `POST /api/users/{id}/disable` | 操作者本人を無効化しようとする | `BusinessException`（SELF_DISABLE） | UserServiceの`REQUIRED` TX | なし（更新前） |
| `POST /api/users/{id}/disable` | `app_user.status` UPDATEのflushが失敗する | `DataAccessException` | UserServiceの`REQUIRED` TX | なし（成功した先行書き込みなし） |
| `POST /api/users/{id}/disable` | USER_DISABLE監査`save`が失敗する | `DataAccessException` | AuditServiceの`REQUIRES_NEW` | なし（監査INSERT未完了） |
| `POST /api/users/{id}/disable` | USER_DISABLE監査例外が業務TXへ伝播する | `DataAccessException` | UserServiceの`REQUIRED` TX | `app_user.status`の更新内容 |
| `POST /api/users/{id}/disable` | USER_DISABLE監査TX commitが失敗する | commit例外（具体型は実行時依存） | AuditServiceの`REQUIRES_NEW` | `audit_log`へのUSER_DISABLE監査INSERT |
| `POST /api/users/{id}/disable` | 監査commit例外が業務TXへ伝播する | commit例外（具体型は実行時依存） | UserServiceの`REQUIRED` TX | `app_user.status`の更新内容 |
| `POST /api/users/{id}/disable` | UserService業務TXのcommitが失敗する | commit例外（具体型は実行時依存） | UserServiceの`REQUIRED` TX | `app_user.status`の更新内容 |
| `GET /api/users/{id}/role-history` | `role_history` SELECTがDB障害となる | `DataAccessException` | UserService.roleHistoriesの`readOnly` TX | なし（読み取りのみ） |
| `GET /api/users/{id}/role-history` | readOnly TX終了処理が失敗する | TX終了例外（具体型は実行時依存） | UserService.roleHistoriesの`readOnly` TX | なし（読み取りのみ） |
| `GET /api/transactions` | 外部一覧APIがHTTPエラー応答を返す | `ExternalApiException`（normalizeでHTTP状態別に生成） | ローカル業務TXなし | なし（ローカルDB更新なし） |
| `GET /api/transactions` | 外部一覧APIの通信障害が再試行上限に達する | `ExternalApiException`（EXTERNAL_UNAVAILABLE） | ローカル業務TXなし | なし（ローカルDB更新なし） |
| `GET /api/transactions/{id}` | 外部詳細APIが404を返す | `ExternalApiException`（NOT_FOUND） | ローカル業務TXなし | なし（ローカルDB更新なし） |
| `GET /api/transactions/{id}` | 外部詳細APIがその他のHTTPエラーを返す | `ExternalApiException`（normalizeでHTTP状態別に生成） | ローカル業務TXなし | なし（ローカルDB更新なし） |
| `GET /api/transactions/{id}` | 外部詳細APIの通信障害が再試行上限に達する | `ExternalApiException`（EXTERNAL_UNAVAILABLE） | ローカル業務TXなし | なし（ローカルDB更新なし） |
| `POST /api/transactions` | JSON要求の解析に失敗する | `HttpMessageNotReadableException` | Controller到達前 | なし（外部API未呼び出し） |
| `POST /api/transactions` | `@Valid`入力検証に失敗する | `MethodArgumentNotValidException` | 外部呼び出し前 | なし（外部API未呼び出し） |
| `POST /api/transactions` | 外部POSTが400を返す | `ExternalApiException`（INVALID_REQUEST） | ローカル業務TXなし | なし（ローカルDB更新なし） |
| `POST /api/transactions` | 外部POSTが422を返す | `ExternalApiException`（BUSINESS_ERROR） | ローカル業務TXなし | なし（ローカルDB更新なし） |
| `POST /api/transactions` | 外部POSTがその他のHTTPエラーを返す | `ExternalApiException`（EXTERNAL_ERROR） | ローカル業務TXなし | なし（ローカルDB更新なし） |
| `POST /api/transactions` | 外部POSTで通信障害が発生する | `ExternalApiException`（EXTERNAL_TIMEOUT） | ローカル業務TXなし | なし（ローカルDB更新なし） |
| `POST /api/transactions` | 取引失敗監査`save`が失敗する | `DataAccessException` | AuditServiceの`REQUIRES_NEW` | なし（監査INSERT未完了） |
| `POST /api/transactions` | 取引失敗監査commitが失敗する | commit例外（具体型は実行時依存） | AuditServiceの`REQUIRES_NEW` | `audit_log`へのTX_REGISTER失敗監査INSERT |
| `POST /api/transactions` | 外部登録成功後の成功監査`save`が失敗する | `DataAccessException` | AuditServiceの`REQUIRES_NEW` | なし（監査INSERT未完了） |
| `POST /api/transactions` | 外部登録成功後の成功監査commitが失敗する | commit例外（具体型は実行時依存） | AuditServiceの`REQUIRES_NEW` | `audit_log`へのTX_REGISTER成功監査INSERT |

### 共通のTX開始前/後の失敗

- Spring Securityの認証/認可/CSRFフィルターが拒否する場合、Controller/Serviceの業務TXは開始しない。rollback対象はない。
- JSON要求の解析、Controller引数変換、Bean Validationで拒否する場合も、Serviceの業務TX開始前でありrollback対象はない。LoginRequestには `@Valid` がないため、そのDTOの `@NotBlank` 検証はControllerで実行されない。
- Service TXのcommit後に応答生成/送信が失敗しても、完了済みTXはrollbackされない。認証成功後のセッション変更やログアウト時のセッション無効化もDB rollbackの対象外。
- POST `/api/transactions` のタイムアウトでは外部側の登録結果が不明な場合がある。外部状態はローカルrollbackされず、同じ `requestId` の再送で重複登録を抑止する。
*** End Patch