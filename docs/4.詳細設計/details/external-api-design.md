# 外部API連携詳細設計

## 1. 文書情報

| 項目 | 内容 |
|---|---|
| 文書ID | D-EXT |
| 呼出元 | Backend `ExternalApiClient` |
| 対向 | Mock External API |
| 接続先 | `http://localhost:9090`（設定値） |
| API一覧 | [API詳細設計](api-design.md) |
| 設定 | 接続3,000ms、読取10,000ms、最大再試行2回 |

## 2. 責務・接続設定

外部接続先、接続/読取タイムアウト、最大再試行回数は `external.api.*` の設定として `ExternalApiProperties` に集約する。`ExternalApiClient` がHTTP呼出し、GET再試行、POST非再試行、エラー正規化を担う。

## 3. 外部API契約

| Method / Path | 用途 | 呼出方式 |
|---|---|---|
| `GET /external/transactions` | 取引一覧、条件検索、ページング | `getWithRetry` |
| `GET /external/transactions/{id}` | 取引詳細 | `getWithRetry` |
| `POST /external/transactions` | 取引登録 | `post` |

一覧条件: `dateFrom`, `dateTo`, `status`, `partnerName`, `page`, `size`。外部APIは日付をISO dateとして解釈する。Mock APIはsizeを1〜100に、pageを実在範囲へ補正する。取引一覧は取引日の降順、次いでID降順。

詳細応答はサマリーに `note`, `lines[]`, `histories[]` を含む。

登録要求は `requestId`, `partnerName`, `amount`, `transactionDate`, `note`。成功応答は `receiptNumber`, `transactionId`, `status`。

## 4. 再試行・タイムアウト

### GET

- `ResourceAccessException`（接続/読み取りなどの通信障害）の場合、`maxRetries` 回まで追加試行する。
- 設定値2の場合は初回を含め最大3回呼び出す。
- HTTPエラー応答（4xx/5xx）はGET再試行ループ内で正規化し、再試行しない。
- 上限後も通信できない場合は503 `EXTERNAL_UNAVAILABLE`、`retryable=true`。

### POST

- 自動再試行しない。通信タイムアウトは504 `EXTERNAL_TIMEOUT`、`retryable=false`。
- タイムアウト時に対向側で処理済みか不明となるため、同一requestIdを用いた再送を冪等性で扱う。
- `requestId` はフロントが確認ダイアログ表示時に採番し、成功時のみ次回登録用にリセットする。失敗時は保持する。

## 5. エラー正規化

| 対向結果 | Backend応答 | retryable | 説明 |
|---|---:|---:|---|
| HTTP 404 | 404 `NOT_FOUND` | false | 対象なし |
| HTTP 422 | 422 `BUSINESS_ERROR` | false | 業務エラー |
| HTTP 400 | 400 `INVALID_REQUEST` | false | 不正要求 |
| その他HTTPエラー | 502 `EXTERNAL_ERROR` | true | 外部システムエラー |
| GET通信障害、再試行上限 | 503 `EXTERNAL_UNAVAILABLE` | true | 接続不可 |
| POST通信障害 | 504 `EXTERNAL_TIMEOUT` | false | 登録結果が不明の可能性。画面は一覧で状態確認を促す |

対向応答のmessageがJSONとして読み取れ、対象分岐で採用する場合はmessageを利用する。その他HTTPエラーのmessageはBackend側固定文言。

## 6. 冪等性とモック動作

- Mock APIは成功した登録要求の結果をrequestIdごとにメモリ保存し、同じIDの再要求に同じ結果を返す。
- 失敗応答はprocessedRequestsへ保存されない。失敗後の同じIDの要求は再度処理され、成功すれば結果を保存する。
- `partnerName` に `FAIL` を含むと500となり、Backendで502 `EXTERNAL_ERROR` に正規化される。
- `partnerName` に `NG` を含むと422 `BUSINESS_ERROR` となる。
- Mock APIのrequestId結果・取引データはいずれもインメモリで、再起動により消去される。

## 7. 監査

取引登録の成功と `ExternalApiException` はBackendの取引登録処理で監査ログに記録する。外部タイムアウト等により呼出し結果が不明の場合も失敗結果として記録される。

## 8. 未定義／要確認

- 本番の接続先、TLS、認証方式、DNS/Proxy構成
- circuit breaker、bulkhead、backoff、retry間隔の設定有無（現行実装で確認できない）
- Mock APIの冪等キー保持期間・永続化（現行はプロセス内メモリ）
- 外部APIの正式なOpenAPI/契約版

## 9. 参照元

- [ExternalApiClient](../../../backend/src/main/java/com/example/backend/transaction/ExternalApiClient.java)
- [ExternalApiProperties](../../../backend/src/main/java/com/example/backend/transaction/ExternalApiProperties.java)
- [Backend設定](../../../backend/src/main/resources/application.properties)
- [Mock ExternalTransactionController](../../../mock-external-api/src/main/java/com/example/mockapi/ExternalTransactionController.java)
- [Mock TransactionStore](../../../mock-external-api/src/main/java/com/example/mockapi/TransactionStore.java)
