# API詳細設計

## 1. 文書情報

| 項目 | 内容 |
|---|---|
| 文書ID | D-API |
| 対象 | Backendの `/api/**` |
| 基本設計 | [基本設計書](../basic-design.md) |
| 画面設計 | [画面別詳細設計](../screens/README.md) |
| 外部API | [外部API連携詳細設計](external-api-design.md) |

## 2. 共通仕様

- Backendのベースパスは `/api`。フロントエンドは同一オリジン相対パスで呼び出す。
- 認証はHTTPセッションCookie。状態変更リクエストはCSRFトークンを要求する。
- JSONの文字コードやAPIバージョニング、OpenAPI定義は確認できないため未定義／要確認。
- 成功レスポンスの全スキーマは実装DTO・返却値に基づく。エラー形式は発生レイヤーにより異なる。

### 2.1 共通エラー応答

| 発生元 | HTTP | 応答項目 | 主なコード |
|---|---:|---|---|
| Spring Security未認証 | 401 | `code`, `message` | `UNAUTHORIZED` |
| Spring Security権限不足 | 403 | `code`, `message` | `FORBIDDEN` |
| 入力検証エラー | 400 | `code`, `message`, `fieldErrors` | `VALIDATION_ERROR` |
| JSON形式不正 | 400 | `code`, `message` | `INVALID_BODY` |
| 業務エラー | 409 | `code`, `message` | `DUPLICATE_USER`, `USER_NOT_FOUND`, `SELF_DISABLE` 等 |
| 外部APIエラー | 可変 | `code`, `message`, `retryable` | 外部API連携詳細を参照 |
| 想定外エラー | 500 | `code`, `message` | `INTERNAL_ERROR` |

すべてのエラーに `retryable` が付くわけではない。401/403、入力検証、業務エラーの応答には `retryable` を含まない。

## 3. 認証API

### 3.1 ログイン

| 項目 | 内容 |
|---|---|
| Method / Path | `POST /api/auth/login` |
| 認証 | 不要。CSRF保護は適用 |
| Request | `{ "username": string, "password": string }` |
| 成功 | 200。`authenticated: true`, `username`, `displayName`, `role` |
| 認証失敗 | 401。`LOGIN_FAILED` または `USER_DISABLED` と `message` |
| 副作用 | セッションID再生成、SecurityContext保存、監査ログ記録 |

`username`、`password` はNotBlank検証対象。Bean Validationエラーは共通400形式となる。

### 3.2 現在の認証状態

| 項目 | 内容 |
|---|---|
| Method / Path | `GET /api/auth/me` |
| 認証 | 不要 |
| 未認証応答 | 200 `{ "authenticated": false }` |
| 認証済み応答 | 200 `{ "authenticated": true, "username": string, "displayName": string, "role": "ADMIN" | "USER" }` |
| 用途 | セッション確認。初回アクセス時のCSRF Cookie取得も兼ねる |

### 3.3 ログアウト

| 項目 | 内容 |
|---|---|
| Method / Path | `POST /api/auth/logout` |
| 認証 | 必要 |
| Request | Bodyなし |
| 成功 | 200 `{ "message": "ログアウトしました" }` |
| 副作用 | HTTPセッション無効化、SecurityContext消去、監査ログ記録 |

## 4. 取引API

### 4.1 一覧

| 項目 | 内容 |
|---|---|
| Method / Path | `GET /api/transactions` |
| 認証 | 必要 |
| Query | `dateFrom`, `dateTo`, `status`, `partnerName`, `page`, `size` |
| Query既定値 | `page=0`, `size=10` |
| 成功 | 200。`content[]`, `page`, `size`, `totalElements`, `totalPages` |
| 外部API | GET `/external/transactions` へ中継。GET再試行対象 |

`content` の各要素は `transactionId`, `partnerName`, `amount`, `transactionDate`, `status`。日付はISO形式の文字列。status値は `COMPLETED`, `PENDING`, `CANCELLED`。

空文字の任意条件は外部APIへのクエリから除外する。Backendはpage/sizeの値をそのまま転送し、Mock External API側で範囲を補正する。

### 4.2 詳細

| 項目 | 内容 |
|---|---|
| Method / Path | `GET /api/transactions/{id}` |
| 認証 | 必要 |
| 成功 | 200。取引サマリーに `note`, `lines[]`, `histories[]` を追加 |
| 外部API | GET `/external/transactions/{id}` へ中継。GET再試行対象 |

`lines[]` の項目は `lineNo`, `itemName`, `quantity`, `unitPrice`。`histories[]` は `occurredAt`, `event`, `operator`。

### 4.3 登録

| 項目 | 内容 |
|---|---|
| Method / Path | `POST /api/transactions` |
| 認証 | 必要 |
| 外部API | POST `/external/transactions`。自動再試行なし |
| 成功 | 200。`receiptNumber`, `transactionId`, `status` |
| 副作用 | 成功/外部API例外の結果を監査ログへ記録 |

| Request項目 | 型 | 制約 |
|---|---|---|
| `requestId` | string | 必須、空白不可 |
| `partnerName` | string | 必須、最大100文字 |
| `amount` | decimal | 必須、1以上 |
| `transactionDate` | string | 必須。外部APIはISO日付として解釈 |
| `note` | string | 任意、最大500文字。nullは空文字で外部APIへ渡す |

Bean Validationで拒否された要求は400 `VALIDATION_ERROR`。通信タイムアウトは外部連携設計のエラー応答となる。

## 5. ユーザ管理API

すべてADMIN認証を要求する。

### 5.1 検索

`GET /api/users?keyword=&role=&status=`。任意条件のAND絞り込み。keywordはユーザID・表示名・メールの大文字小文字を区別しない部分一致。応答はユーザ応答の配列で、ID昇順。

ユーザ応答項目: `id`, `username`, `displayName`, `email`, `status`, `role`, `createdAt`, `updatedAt`。

### 5.2 登録

`POST /api/users`。Request:

| 項目 | 型 | 制約 |
|---|---|---|
| `username` | string | 必須、最大50、`[a-zA-Z0-9_.-]+` |
| `displayName` | string | 必須、最大100 |
| `email` | string | 必須、メール形式、最大255 |
| `role` | enum | 必須、`ADMIN` または `USER` |
| `password` | string | 必須、8〜72文字 |

成功時201ではなく200でユーザ応答を返す。状態はACTIVEで作成し、パスワード平文は応答しない。username重複は409 `DUPLICATE_USER`。

### 5.3 更新

`PUT /api/users/{id}`。Requestは `displayName`（必須・最大100）、`email`（必須・メール形式・最大255）、`role`（必須）、`status`（必須）。成功時200でユーザ応答を返す。ロールが変化した場合に履歴を追加する。本人をDISABLEDにする要求は409 `SELF_DISABLE`。

### 5.4 無効化

`POST /api/users/{id}/disable`。Request Bodyなし。成功時200で更新後ユーザ応答。本人の無効化は409 `SELF_DISABLE`、対象不在は409 `USER_NOT_FOUND`。

### 5.5 ロール変更履歴

`GET /api/users/{id}/role-history`。成功時200で `id`, `oldRole`, `newRole`, `changedBy`, `changedAt` の配列を返す。新規作成時の `oldRole` はnull。更新日時の降順。

## 6. 参照元

- Backend Controllers: `auth/AuthController`, `transaction/TransactionController`, `user/UserController`
- DTO・処理: `user/UserDtos`, `user/UserService`
- 認証・共通エラー: `config/SecurityConfig`, `common/GlobalExceptionHandler`
- 外部エラー変換: [外部API連携詳細設計](external-api-design.md)
