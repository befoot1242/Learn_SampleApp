# 主要処理フロー詳細設計

## 1. 文書情報

| 項目 | 内容 |
|---|---|
| 文書ID | D-FLOW |
| 対象 | ログイン、ユーザ管理、取引参照・登録の主要処理順 |
| API契約 | [API詳細設計](api-design.md) |
| 認証 | [認証・認可詳細設計](security-design.md) |
| 外部連携 | [外部API連携詳細設計](external-api-design.md) |

本書では画面のレイアウトやテスト観点を扱わない。

## 2. ログイン・初期化

```mermaid
sequenceDiagram
    participant FE as Frontend
    participant Router as Vue Router/Auth Store
    participant BFF as Backend
    participant DB as H2

    FE->>Router: 画面遷移前にinitialize
    Router->>BFF: GET /api/auth/me
    BFF->>DB: 現在セッションのユーザを解決
    BFF-->>Router: 認証状態 + CSRF Cookie
    Router->>BFF: POST /api/auth/login (username/password + CSRF)
    BFF->>DB: username検索、status確認、BCrypt照合
    alt 認証成功
        BFF->>BFF: セッションID変更、SecurityContext保存
        BFF->>DB: LOGIN SUCCESSをREQUIRES_NEW記録
        BFF-->>Router: CurrentUser、session cookie
        Router-->>FE: redirect先またはホームへ遷移
    else 認証失敗
        BFF->>DB: LOGIN FAILUREをREQUIRES_NEW記録
        BFF-->>Router: 401 LOGIN_FAILED / USER_DISABLED
        Router-->>FE: エラーメッセージ表示
    end
```

未認証時の `/api/auth/me` は200 `{authenticated:false}` を返す。遷移開始時にユーザ情報を初期化できない場合、Auth Storeは未認証として扱う。

## 3. ユーザ登録・ロール変更

```mermaid
sequenceDiagram
    participant Admin as ADMIN画面
    participant BFF as UserController/UserService
    participant DB as H2
    participant Audit as AuditService

    Admin->>BFF: POST /api/users (入力)
    BFF->>BFF: Bean Validation
    BFF->>DB: username重複確認
    alt 重複なし
        BFF->>BFF: パスワードをBCrypt化
        BFF->>DB: app_user保存 (ACTIVE)
        BFF->>DB: role_history保存 (oldRole=null)
        BFF->>Audit: USER_CREATE SUCCESS (REQUIRES_NEW)
        BFF-->>Admin: 200 UserResponse
    else 重複あり
        BFF-->>Admin: 409 DUPLICATE_USER
    end

    Admin->>BFF: PUT /api/users/{id} (変更値)
    BFF->>DB: 対象ユーザ取得
    alt ロール変更
        BFF->>DB: role_history保存 (変更前/後)
    else ロール変更なし
        Note over BFF,DB: 履歴は追加しない
    end
    BFF->>DB: app_user更新
    BFF->>Audit: USER_UPDATE SUCCESS (REQUIRES_NEW)
    BFF-->>Admin: 200 UserResponse
```

ユーザ無効化はACTIVEからDISABLEDへの状態更新であり物理削除しない。本人を無効化する要求はサービスで拒否する。監査ログは各処理の記録対象。

## 4. 取引参照

```mermaid
sequenceDiagram
    participant FE as 取引画面
    participant BFF as TransactionController
    participant Client as ExternalApiClient
    participant EXT as Mock External API

    FE->>BFF: GET /api/transactions (検索条件/page/size)
    BFF->>Client: GET /external/transactions
    loop 通信障害時、初回+最大2回再試行
        Client->>EXT: GET一覧
        EXT-->>Client: 200 page response / HTTP error
    end
    Client-->>BFF: データまたは正規化済みExternalApiException
    BFF-->>FE: 一覧データまたはJSONエラー

    FE->>BFF: GET /api/transactions/{id}
    BFF->>Client: GET /external/transactions/{id}
    Client->>EXT: GET詳細
    EXT-->>Client: 200 detail / 404
    Client-->>BFF: データまたは正規化済みExternalApiException
    BFF-->>FE: 詳細データまたはJSONエラー
```

GETの通信障害だけを設定回数再試行する。HTTPエラー応答は再試行せず正規化する。

## 5. 取引登録・冪等性

```mermaid
sequenceDiagram
    participant FE as 取引登録画面
    participant BFF as TransactionController
    participant Client as ExternalApiClient
    participant EXT as Mock External API
    participant DB as H2監査ログ

    FE->>FE: 入力検証、確認時にrequestId採番
    FE->>BFF: POST /api/transactions (requestId含む)
    BFF->>BFF: Bean Validation
    BFF->>Client: POST /external/transactions (自動再試行なし)
    Client->>EXT: 登録要求
    alt 登録成功
        EXT-->>Client: receiptNumber/transactionId/status
        Client-->>BFF: 成功結果
        BFF->>DB: TX_REGISTER SUCCESS
        BFF-->>FE: 200登録結果
        FE->>FE: requestIdをリセット、結果表示
    else 業務/外部エラーまたは通信タイムアウト
        EXT-->>Client: HTTP error または応答なし
        Client-->>BFF: 正規化済み例外
        BFF->>DB: TX_REGISTER FAILURE
        BFF-->>FE: JSONエラー
        FE->>FE: 入力とrequestIdを保持、エラー表示
    end
```

POSTは更新系であるため自動再試行しない。失敗後の再送に同じrequestIdを用いる。Mock External APIは成功結果をメモリ上に保持し、成功済みIDへの再送では保存結果を返す。失敗応答はMock側に記録しない。

## 6. 監査トランザクション

`AuditService.record()` は `Propagation.REQUIRES_NEW` で監査行を保存する。このため呼出元の業務トランザクションとは独立する。記録できる失敗範囲は呼出し箇所に依存し、全例外を自動記録する仕組みではない。

## 7. 参照元

- [AuthController](../../../backend/src/main/java/com/example/backend/auth/AuthController.java)
- [UserService](../../../backend/src/main/java/com/example/backend/user/UserService.java)
- [TransactionController](../../../backend/src/main/java/com/example/backend/transaction/TransactionController.java)
- [ExternalApiClient](../../../backend/src/main/java/com/example/backend/transaction/ExternalApiClient.java)
- [AuditService](../../../backend/src/main/java/com/example/backend/audit/AuditService.java)
- [Mock ExternalTransactionController](../../../mock-external-api/src/main/java/com/example/mockapi/ExternalTransactionController.java)
