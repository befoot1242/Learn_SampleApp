# 開発タスク 技術詳細設計

## 1. 対象

| 項目 | 内容 |
|---|---|
| 文書ID | D-DT |
| 対象 | 開発タスク一覧・タブ別参照 |
| 状態 | 実装済み。実データの運用・アクセス制限は未決 |
| 根拠 | [要件](../../2.要件資料/development-task-requirements.md)、[基本設計](../../3.基本設計/development-task-basic-design.md) |
| 画面 | [開発タスク画面](../screens/development-tasks.md) |

## 2. API

セッション認証必須。USER・ADMINが利用可能。タスク単位の認可はない。いずれもGETで、更新・外部API呼出し・操作監査はない。

| メソッド・パス | 正常応答 |
|---|---|
| `GET /api/development-tasks` | 200、`[{id, name, status, updatedDate}]`。ID昇順、0件は `[]` |
| `GET /api/development-tasks/{id}/tabs/{tab}` | 200、`{taskId, tab, content}`。`content` は文字列または `null` |

一覧のID・名前・状態・日付は文字列で、日付は `YYYY-MM-DD`。タブ種別は `requirements`（要件）、`basic`（基本）、`detail`（詳細）、`implementation`（実装）、`remaining`（残タスク）。内容が未設定でも対象タスクがあれば200で `content: null` を返す。

| 状態 | HTTP | 応答 |
|---|---:|---|
| 未認証 | 401 | `UNAUTHORIZED`、メッセージ |
| 不正なタブ | 400 | `INVALID_TAB`、メッセージ、`retryable: false` |
| 対象タスクなし | 404 | `TASK_NOT_FOUND`、メッセージ、`retryable: false` |
| 想定外の例外 | 500 | `INTERNAL_ERROR`、メッセージ |

タブ種別の検査はタスクの存在確認より先に行う。共通エラーは[API詳細設計](api-design.md)に従う。

## 3. データ

H2 `development_task`。型・制約はJPA宣言に基づく。物理列名はHibernateの命名規則による。

| Java項目 | 型 | NULL | 制約・意味 |
|---|---|---|---|
| `id` | String | 不可 | 主キー、最大40文字 |
| `name` | String | 不可 | 最大200文字 |
| `status` | String | 不可 | 最大30文字、状態の表示文字列 |
| `updatedDate` | LocalDate | 不可 | 更新日。自動更新は行わない |
| `requirements`, `basic`, `detail`, `implementation`, `remaining` | String | 可 | 各タブのプレーンテキスト、各最大10000文字 |

起動時にテーブルが0件であれば、`DT-001` と `DT-002` のサンプルを一括投入する。既存データが1件以上ある場合は投入・上書きしない。全件削除後の次回起動では再投入する。アプリからの登録・編集APIはない。実データの投入や更新の運用は未決。

## 4. ストア・処理

画面イベントとストアの保持・取得・破棄は[ストア状態管理詳細設計](store-usage-report.md)を参照する。

Controllerには明示的なトランザクションはなく、Repository標準の読取トランザクションでSELECTする。APIは更新しないためrollback対象の業務データはない。初期データ投入はAPIとは別の起動時トランザクション。

## 5. 実装参照

- [API](../../../backend/src/main/java/com/example/backend/developmenttask/DevelopmentTaskController.java)
- [エンティティ](../../../backend/src/main/java/com/example/backend/developmenttask/DevelopmentTask.java)
- [初期データ](../../../backend/src/main/java/com/example/backend/developmenttask/DevelopmentTaskSeeder.java)
- [ストア](../../../frontend/src/stores/developmentTask.ts)
- [認証ストア](../../../frontend/src/stores/auth.ts)
