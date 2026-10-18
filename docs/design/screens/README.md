# 画面別詳細設計

## 1. 文書の位置付け

本資料は、[基本設計書](../basic-design.md)の画面一覧を実装に基づいて具体化する。画面項目、入力制約、操作、遷移、表示状態、画面から呼び出すAPIを記載する。実装で確認できない事項は推測で補わず、「未確認」または「対象外」と明記する。

API契約・DBカラムの詳細は画面別資料に重複記載しない。[API・DB・認証・外部連携の詳細設計](../details/README.md)を参照する。

## 2. 画面一覧

| ID | 画面 | パス | 権限 | 設計書 |
|---|---|---|---|---|
| S-01 | ログイン | `/login` | 未認証 | [ログイン画面](login.md) |
| S-02 | ホーム | `/` | USER、ADMIN | [ホーム画面](home.md) |
| S-03 | ユーザ管理 | `/users` | ADMIN | [ユーザ管理画面](user-management.md) |
| S-04 | 取引一覧 | `/transactions` | USER、ADMIN | [取引一覧画面](transaction-list.md) |
| S-05 | 取引登録 | `/transactions/entry` | USER、ADMIN | [取引登録画面](transaction-entry.md) |
| S-06 | 取引詳細 | `/transactions/:id` | USER、ADMIN | [取引詳細画面](transaction-detail.md) |

## 3. 共通レイアウト

認証後の画面は [AppLayout](../../../frontend/src/components/AppLayout.vue) を共通利用する。

| 領域 | 表示・動作 |
|---|---|
| ヘッダー | アプリ名「取引管理システム」、ログインユーザの表示名とロール、ログアウトボタン |
| サイドメニュー | ホーム、取引一覧、取引登録を表示。ユーザ管理はADMINのみ表示 |
| コンテンツ | 各画面の見出しと画面固有の内容 |
| ログアウト | `POST /api/auth/logout` を呼び出し、ログイン画面へ遷移 |

ログイン画面は共通レイアウトを使用しない。

## 4. 共通認証・通信仕様

- [Vue Router](../../../frontend/src/router/index.ts) のガードが各遷移前に認証状態を初期化する。未認証時は `/login?redirect=<遷移先>` へ遷移する。
- ADMIN専用画面にUSERがアクセスした場合はホームへ遷移する。サーバー側でもユーザ管理APIをADMINに限定する。
- API呼び出しは [共通HTTPクライアント](../../../frontend/src/api/client.ts) を使い、base URLは `/api`、タイムアウトは30秒。`XSRF-TOKEN` Cookieを `X-XSRF-TOKEN` ヘッダーへ転記する。
- APIエラーは `code`、`message`、`retryable`、`fieldErrors` を持つ `ApiError` として画面表示に利用する。通信タイムアウトと接続失敗は再試行可能なエラーに変換する。
- 画面操作中にHTTP 401を受けた場合、画面側でセッションを期限切れとして扱い、ログイン画面へ遷移する。

## 5. 画面設計書

各画面の詳細は上の一覧から参照する。画面画像は `../images/` 配下に置いたローカル開発環境のキャプチャを利用する。

## 6. 記載対象外

テスト観点・テストケースは本設計書群に含めない。テスト資料は画面設計書とは分けて管理する。