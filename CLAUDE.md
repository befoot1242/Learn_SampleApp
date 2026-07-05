# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## プロジェクト概要

[vue-springboot-external-api-requirements.html](vue-springboot-external-api-requirements.html) の要件案を実装した学習用サンプル(取引管理システム)。Gradleマルチプロジェクト + Vueフロントエンドの3モジュール構成。

```
frontend (Vue3+TS, :5173) → /api → backend (Spring Boot BFF, :8080) → mock-external-api (:9090)
                                        ↓
                                   H2 (file: ./data/appdb)
```

## コマンド

```powershell
# ビルド(Java両モジュール)
.\gradlew.bat build

# 起動(3プロセス必要。それぞれ別ターミナルで)
.\gradlew.bat :mock-external-api:bootRun   # 先に起動(:9090)
.\gradlew.bat :backend:bootRun             # BFF(:8080)
cd frontend; npm run dev                   # SPA(:5173)

# フロントエンド
cd frontend
npm run build     # vue-tsc型チェック + viteビルド(型エラー検出はこれで行う)
```

- Javaのテストは現状未整備(`test` タスクは NO-SOURCE)
- デモアカウント: `admin` / `password123`(ADMIN)、`user1` / `password123`(USER)。初回起動時に `DataSeeder` が投入
- H2コンソール: http://localhost:8080/h2-console(JDBC URL: `jdbc:h2:file:./data/appdb;MODE=LEGACY`)
- ポート5173が使用中の場合、vite.config.ts が `PORT` 環境変数を参照する(.claude/launch.json は autoPort 有効)

## 環境の注意点(Spring Boot 4系)

- スターター名が旧世代と異なる: `spring-boot-starter-webmvc`(旧 web)、`spring-boot-h2console`、テスト用は `spring-boot-starter-webmvc-test` 等
- **JSONはJackson 3**: パッケージは `tools.jackson.*`。`com.fasterxml.jackson` はクラスパスに存在しないためimportするとコンパイルエラーになる
- Git Bashからcurlで日本語を含むJSONを `-d` 直書きするとCP932化けして400になる。UTF-8ファイルに書いて `--data-binary @file` で送ること

## アーキテクチャ

### BFFパターンと外部API連携

フロントは外部API(mock-external-api)を直接呼ばず、必ずbackend経由。外部API関連の横断的関心事は `backend/.../transaction/ExternalApiClient.java` に集約されている:

- 接続先・タイムアウト・再試行回数は `application.properties` の `external.api.*` → `ExternalApiProperties`(record + `@ConfigurationPropertiesScan`)
- 参照系(GET)のみIO障害時に自動再試行。更新系(POST)は再試行せず、重複防止はフロントが採番する `requestId` を外部APIが冪等処理することで担保
- 外部エラーは `ExternalApiException`(code / httpStatus / retryable)へ正規化し、`GlobalExceptionHandler` が `{code, message, retryable}` 形式のJSONで返す。フロントは `retryable` を見て再試行導線を出す

### 認証・認可

- OAuth2ではなく**セッションベース簡易ログイン**(要件からの意図的な変更。ユーザ確認済み)。`AuthController` が `AuthenticationManager` で認証し、`SecurityContextRepository` へ手動保存 + セッションID再生成
- CSRFは `CookieCsrfTokenRepository` + SPA向けハンドラ(`SecurityConfig` 内の `SpaCsrfTokenRequestHandler` / `CsrfCookieFilter`、Spring Securityリファレンスの推奨実装)。axiosが `XSRF-TOKEN` Cookieを自動で `X-XSRF-TOKEN` ヘッダに転記する
- `GET /api/auth/me` は未認証でも200で `{authenticated: false}` を返す設計(フロント起動時のセッション確認とCSRF Cookie取得を兼ねる)
- 認可は三層: Vueメニューの表示制御 / Vue Routerガード(`meta.adminOnly`)/ Spring Securityの `requestMatchers("/api/users/**").hasRole("ADMIN")`。API側が最終防衛線

### フロントエンド

- 画面は `frontend/src/views/`、認証状態は Pinia の `stores/auth.ts` のみ。API呼出は `src/api/` に集約し、エラーは `toApiError()` で `ApiError` 型({code, message, retryable, fieldErrors})に統一
- 各画面は401検知時に `auth.markSessionExpired()` → ログイン画面へ遷移するパターンで統一
- 取引登録画面の `requestId` は確認ダイアログを開いた時点で採番し、**失敗→再試行では同じIDを使い、成功時のみリセット**する(冪等性の要)

### モック外部API

`mock-external-api` はインメモリストア(再起動でリセット、初期データ35件)。動作確認用の特殊ルールが `ExternalTransactionController` にある:

- 取引先名に `FAIL` を含む登録 → 500(BFFが502 `EXTERNAL_ERROR` に正規化)
- 取引先名に `NG` を含む登録 → 422(業務エラー)
- 同一 `requestId` の再送 → 初回結果をそのまま返す

### 監査ログ

認証・ユーザ操作・取引登録は `AuditService.record()` で `audit_log` テーブルに記録。`REQUIRES_NEW` トランザクションなので業務処理が失敗してもログは残る。新しい操作系機能を追加する場合はこのパターンを踏襲する。
