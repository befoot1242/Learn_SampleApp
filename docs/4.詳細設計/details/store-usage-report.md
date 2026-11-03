# ストア状態管理詳細設計

## 1. 対象と読み方

本書は、画面操作に伴うPiniaストアの変更を、現行実装に基づいて整理する。画面内だけで保持する入力値・表示状態、Cookie、サーバーのセッションは対象外。

| ストア名 | ID | 主な保持値 |
|---|---|---|
| 認証 | `auth` | `user`、`initialized` |
| 開発タスク | `developmentTask` | `taskId`、`tabs` |

### 表の表記

| 表記 | 意味 |
|---|---|
| 設定 | 対象データやIDを保存 |
| 更新 | 保持値を変更 |
| クリア | 対象の保持値を破棄 |
| なし | ストアの変更またはAPI呼び出しなし |
| （成功時） | 対象APIの成功時のみ |
| （401時） | 対象APIの401検知時のみ |
| （取得時） | タブを新たに取得する場合のみ |

１行に１ストアを記載する。同じ操作で複数ストアが変わる場合は複数行に分ける。ストアは参照のみの場合も「なし」とし、参照する値は表の対象外とする。

API列は、その画面操作に関連して呼び出すAPIを示す。必ずしもストア自体がAPIを呼ぶことを意味しない。`{id}`、`{tab}` は対象のタスクやタブなどの値に置き換わる。条件・呼び出し順序は[表に収まらない情報](#3-表に収まらない情報)を参照する。

## 2. 画面操作とストア

### 2.1 共通操作

「全画面」はルーターを通るすべての画面。「ログイン後の画面」は共通ヘッダーを表示する画面。

| 画面 | イベント | ストア | 処理概要 | 対応API |
|---|---|---|---|---|
| 全画面 | 初回の画面遷移 | 認証（`auth`） | 設定 | `GET /api/auth/me` |
| ログイン後の画面 | ログアウトボタン押下 | 開発タスク（`developmentTask`） | クリア | `POST /api/auth/logout` |
| ログイン後の画面 | ログアウトボタン押下 | 認証（`auth`） | 更新 | `POST /api/auth/logout` |

### 2.2 画面別の操作

同じイベントでのストア変更が401時だけ発生する行は、通常の成功時には変更しない。

| 画面 | イベント | ストア | 処理概要 | 対応API |
|---|---|---|---|---|
| ログイン | 初期表示 | 認証（`auth`） | なし | なし |
| ログイン | ログインボタン押下 | 認証（`auth`） | 設定（成功時） | `POST /api/auth/login` |
| ホーム | 初期表示 | 認証（`auth`） | なし | なし |
| ユーザ管理 | 初期表示 | 認証（`auth`） | 更新（401時） | `GET /api/users` |
| ユーザ管理 | 初期表示 | 開発タスク（`developmentTask`） | クリア（401時） | `GET /api/users` |
| ユーザ管理 | 検索ボタン押下 | 認証（`auth`） | 更新（401時） | `GET /api/users` |
| ユーザ管理 | 検索ボタン押下 | 開発タスク（`developmentTask`） | クリア（401時） | `GET /api/users` |
| ユーザ管理 | 編集ボタン押下 | 認証（`auth`） | なし | `GET /api/users/{id}/role-history` |
| ユーザ管理 | 登録確定ボタン押下 | 認証（`auth`） | 更新（401時） | `POST /api/users`、`GET /api/users` |
| ユーザ管理 | 登録確定ボタン押下 | 開発タスク（`developmentTask`） | クリア（401時） | `POST /api/users`、`GET /api/users` |
| ユーザ管理 | 更新ボタン押下 | 認証（`auth`） | 更新（401時） | `PUT /api/users/{id}`、`GET /api/users` |
| ユーザ管理 | 更新ボタン押下 | 開発タスク（`developmentTask`） | クリア（401時） | `PUT /api/users/{id}`、`GET /api/users` |
| ユーザ管理 | 無効化ボタン押下 | 認証（`auth`） | 更新（401時） | `POST /api/users/{id}/disable`、`GET /api/users` |
| ユーザ管理 | 無効化ボタン押下 | 開発タスク（`developmentTask`） | クリア（401時） | `POST /api/users/{id}/disable`、`GET /api/users` |
| 取引一覧 | 初期表示 | 認証（`auth`） | 更新（401時） | `GET /api/transactions` |
| 取引一覧 | 初期表示 | 開発タスク（`developmentTask`） | クリア（401時） | `GET /api/transactions` |
| 取引一覧 | 検索ボタン押下 | 認証（`auth`） | 更新（401時） | `GET /api/transactions` |
| 取引一覧 | 検索ボタン押下 | 開発タスク（`developmentTask`） | クリア（401時） | `GET /api/transactions` |
| 取引一覧 | 前へボタン押下 | 認証（`auth`） | 更新（401時） | `GET /api/transactions` |
| 取引一覧 | 前へボタン押下 | 開発タスク（`developmentTask`） | クリア（401時） | `GET /api/transactions` |
| 取引一覧 | 次へボタン押下 | 認証（`auth`） | 更新（401時） | `GET /api/transactions` |
| 取引一覧 | 次へボタン押下 | 開発タスク（`developmentTask`） | クリア（401時） | `GET /api/transactions` |
| 取引一覧 | 再試行ボタン押下 | 認証（`auth`） | 更新（401時） | `GET /api/transactions` |
| 取引一覧 | 再試行ボタン押下 | 開発タスク（`developmentTask`） | クリア（401時） | `GET /api/transactions` |
| 取引登録 | 初期表示 | なし | なし | なし |
| 取引登録 | 登録確定ボタン押下 | 認証（`auth`） | 更新（401時） | `POST /api/transactions` |
| 取引登録 | 登録確定ボタン押下 | 開発タスク（`developmentTask`） | クリア（401時） | `POST /api/transactions` |
| 取引詳細 | 初期表示 | 認証（`auth`） | 更新（401時） | `GET /api/transactions/{id}` |
| 取引詳細 | 初期表示 | 開発タスク（`developmentTask`） | クリア（401時） | `GET /api/transactions/{id}` |
| 取引詳細 | 再読込ボタン押下 | 認証（`auth`） | 更新（401時） | `GET /api/transactions/{id}` |
| 取引詳細 | 再読込ボタン押下 | 開発タスク（`developmentTask`） | クリア（401時） | `GET /api/transactions/{id}` |
| 取引詳細 | 再試行ボタン押下 | 認証（`auth`） | 更新（401時） | `GET /api/transactions/{id}` |
| 取引詳細 | 再試行ボタン押下 | 開発タスク（`developmentTask`） | クリア（401時） | `GET /api/transactions/{id}` |
| 入力フォーム | 初期表示 | なし | なし | なし |
| 各種申請 | 初期表示 | なし | なし | なし |
| 各種申請 | 登録確定ボタン押下 | 認証（`auth`） | 更新（401時） | `POST /api/applications` |
| 各種申請 | 登録確定ボタン押下 | 開発タスク（`developmentTask`） | クリア（401時） | `POST /api/applications` |
| 開発タスク一覧 | 初期表示 | 認証（`auth`） | 更新（401時） | `GET /api/development-tasks` |
| 開発タスク一覧 | 初期表示 | 開発タスク（`developmentTask`） | クリア（401時） | `GET /api/development-tasks` |
| 開発タスク一覧 | 再試行ボタン押下 | 認証（`auth`） | 更新（401時） | `GET /api/development-tasks` |
| 開発タスク一覧 | 再試行ボタン押下 | 開発タスク（`developmentTask`） | クリア（401時） | `GET /api/development-tasks` |
| 開発タスク詳細 | 初期表示 | 開発タスク（`developmentTask`） | クリア・設定・更新 | `GET /api/development-tasks/{id}/tabs/requirements` |
| 開発タスク詳細 | 初期表示 | 認証（`auth`） | 更新（401時） | `GET /api/development-tasks/{id}/tabs/requirements` |
| 開発タスク詳細 | タスクID変更 | 開発タスク（`developmentTask`） | クリア・設定・更新 | `GET /api/development-tasks/{id}/tabs/requirements` |
| 開発タスク詳細 | タスクID変更 | 認証（`auth`） | 更新（401時） | `GET /api/development-tasks/{id}/tabs/requirements` |
| 開発タスク詳細 | タブ押下 | 開発タスク（`developmentTask`） | 設定・更新（取得時）／クリア（401時） | `GET /api/development-tasks/{id}/tabs/{tab}` |
| 開発タスク詳細 | タブ押下 | 認証（`auth`） | 更新（401時） | `GET /api/development-tasks/{id}/tabs/{tab}` |
| 開発タスク詳細 | 再試行ボタン押下 | 開発タスク（`developmentTask`） | 設定・更新（取得時）／クリア（401時） | `GET /api/development-tasks/{id}/tabs/{tab}` |
| 開発タスク詳細 | 再試行ボタン押下 | 認証（`auth`） | 更新（401時） | `GET /api/development-tasks/{id}/tabs/{tab}` |
| 開発タスク詳細 | 画面離脱 | 開発タスク（`developmentTask`） | クリア | なし |

上記以外の画面固有の操作は、Piniaストアを変更しない。画面遷移に伴う初期表示・離脱の処理は、対象画面の行に記載する。全画面に共通する操作は§2.1を参照。

## 3. 表に収まらない情報

上の５列だけでは、次の条件やタイミングを正確には表現できない。

- **初回の認証確認**：ルーターが初期化済みかを毎回判定し、未初期化の場合だけ `GET /api/auth/me` を呼ぶ。成功時は応答、失敗時は未認証を `auth.user` に設定し、どちらも `auth.initialized` を `true` にする。ブラウザー再読み込み後はメモリ上のストアが再生成される。
- **ログイン・ログアウト**：ログイン失敗時は `auth` を変更しない。ログアウトは、APIを呼ぶ前に開発タスクをクリアし、APIの成功・失敗にかかわらず終了時に `auth.user` を未認証に更新する。`auth.initialized` は変更しない。
- **401時の順序と例外**：401を検知した画面は、開発タスクをクリアした後、認証を未認証に更新する。ユーザ管理の編集ボタンによるロール履歴取得は、401を含む失敗時にもセッション切れ処理を呼ばない。ログインAPIの401もログイン失敗として扱う。
- **ユーザ管理の再検索**：登録・更新・無効化の成功後に `GET /api/users` を呼ぶ。登録前の入力検証失敗や無効化の確認取消時は、該当する更新APIを呼ばない。
- **開発タスク詳細の開始・離脱**：初期表示やタスクID変更時は、旧データのクリア後に `taskId` を設定し、要件タブを取得する。画面離脱時のクリアはコンポーネントのアンマウント時に行う。タブの切り替えだけでは全データをクリアしない。
- **タブ取得の条件**：取得済みのタブはAPIを呼ばず保持値を再利用する。取得中の同じタブへの要求は共有する。未取得またはエラー後の取得では、開始時と応答時に `tabs` を変更する。空の内容も取得済みとして保持する。タブのキーボード操作もタブ押下と同じ処理を行う。
- **開発タスク固有の状態**：`tabs` の各タブには、未取得のほか `loading`、`success`、`error` がある。これらはこのストア固有の状態であり、他ストアには適用しない。
- **通信中のクリア**：開発タスクをクリアすると取得中の通信を中止する。クリア前に開始した通信が後から完了しても、その応答はストアに反映しない。
- **参照と画面ローカル状態**：表の「なし」には、認証情報の表示・権限判定などストアを参照するだけの場合も含む。各画面の一覧結果やフォーム入力など、Pinia以外の状態の変更は表に含めない。

## 4. 実装参照・調査の限界

- [認証ストア](../../../frontend/src/stores/auth.ts)
- [開発タスクストア](../../../frontend/src/stores/developmentTask.ts)
- [ルーター](../../../frontend/src/router/index.ts)
- [共通レイアウト](../../../frontend/src/components/AppLayout.vue)
- [画面別詳細設計](../screens/README.md)
- [開発タスク技術詳細設計](development-task-design.md)

本書はソースコードの静的な調査に基づく現行動作であり、すべての操作順序を実行検証したことを意味しない。API・画面操作を追加した場合は、共通処理と401時のストア変更も含めて本書を更新する。
