# 取引管理システム(学習用サンプル)

[vue-springboot-external-api-requirements.html](vue-springboot-external-api-requirements.html) の要件案を基に実装した、
Vue 3 + Spring Boot + 外部API連携のサンプルアプリケーションです。

## 構成

```
┌──────────────┐      ┌──────────────────┐      ┌────────────────────┐
│  frontend    │ /api │  backend (BFF)   │      │  mock-external-api │
│  Vue 3 + TS  ├─────→│  Spring Boot     ├─────→│  Spring Boot       │
│  :5173       │      │  :8080 (+H2 DB)  │      │  :9090             │
└──────────────┘      └──────────────────┘      └────────────────────┘
```

| モジュール | 技術 | 役割 |
|---|---|---|
| `frontend` | Vue 3 / TypeScript / Vite / Vue Router / Pinia / Axios | SPA。`/api` はViteのプロキシでBFFへ転送 |
| `backend` | Spring Boot 4 / Spring Security / JPA / H2 | BFF。認証・ユーザ管理・外部API中継・監査ログ |
| `mock-external-api` | Spring Boot 4 | 対向システム(取引参照・登録API)のモック |

## 起動方法

3プロセスをそれぞれ起動します(Java 21 / Node 18+ が必要)。

```powershell
# 1. モック外部API (ポート9090)
.\gradlew.bat :mock-external-api:bootRun

# 2. バックエンドBFF (ポート8080)
.\gradlew.bat :backend:bootRun

# 3. フロントエンド (ポート5173)
cd frontend
npm install
npm run dev
```

ブラウザで http://localhost:5173 を開きます。

### デモアカウント

| ユーザID | パスワード | ロール |
|---|---|---|
| `admin` | `password123` | 管理者(全機能) |
| `user1` | `password123` | 一般(取引参照・登録のみ) |

初回起動時に自動投入されます。H2はファイル永続化(`backend実行ディレクトリ/data/`)です。

## 機能

- **認証**: セッションベースのログイン/ログアウト(HttpOnly Cookie + CSRFトークン)
- **認可**: ロールベース制御(ユーザ管理は管理者のみ。画面メニュー・ルーターガード・API両方で制御)
- **ユーザ管理**: 検索/新規登録/編集/無効化、ロール変更履歴の保存・表示
- **取引一覧/詳細**: 条件検索(日付・状態・取引先)、ページング、外部API障害時の再試行導線
- **取引登録**: 必須チェック → 確認ダイアログ → 登録 → 受付番号表示。失敗時は入力保持+エラー理由表示
  - 重複送信防止: フロントで採番したリクエストIDを外部APIが冪等に処理
  - タイムアウト時: 取引一覧への状態照会導線を表示
- **外部API連携**: 接続先・タイムアウト・再試行回数を `application.properties` で一元管理、
  外部エラーをコード付き業務エラーへ正規化(`BUSINESS_ERROR` / `EXTERNAL_UNAVAILABLE` など)
- **監査ログ**: ログイン/ログアウト/ユーザ操作/取引登録を `audit_log` テーブルへ記録

### 動作確認用の特殊ルール(モック外部API)

- 取引先名に **`FAIL`** を含む登録 → 500(システムエラー → BFFで502に正規化、再試行可)
- 取引先名に **`NG`** を含む登録 → 422(業務エラー)
- 同一リクエストIDの再送 → 初回と同じ結果を返す(冪等)

## 要件からの変更点

| 要件 | 実装 | 理由 |
|---|---|---|
| OAuth2 Authorization Code + PKCE | Spring Security によるセッションベース簡易ログイン | ローカル学習用にIdP不要の構成とした(ユーザ確認済み)。このためパスワードハッシュを自前DBに保持している |
| 外部API(対向システム) | モックサーバー(`mock-external-api`) | ローカル完結で動作確認できるようにするため |

## 開発メモ

- H2コンソール: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:file:./data/appdb;MODE=LEGACY`)
- ビルド: `.\gradlew.bat build`(Java)、`cd frontend && npm run build`(型チェック込み)
- Spring Boot 4系のため、スターター名は `spring-boot-starter-webmvc`、JSONは Jackson 3(`tools.jackson`)
