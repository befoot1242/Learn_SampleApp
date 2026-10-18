# S-04 取引一覧画面 詳細設計

## 1. 画面概要

| 項目 | 内容 |
|---|---|
| 画面ID | S-04 |
| 画面名 | 取引一覧 |
| URL | `/transactions` |
| 認証・権限 | 認証必須、USER・ADMIN |
| コンポーネント | `frontend/src/views/TransactionListView.vue` |
| 共通枠 | `AppLayout` |

![取引一覧](../images/transactions-list.png)

## 2. 検索条件

| 項目 | UI | 条件・初期値 |
|---|---|---|
| 取引日 From | 日付入力 | 任意、初期値なし |
| 取引日 To | 日付入力 | 任意、初期値なし |
| 状態 | セレクト | 任意。すべて、COMPLETED、PENDING、CANCELLED |
| 取引先名 | テキスト入力 | 任意、部分一致検索用 |
| 検索 | ボタン | 検索条件で先頭ページを取得。検索中は無効化 |

## 3. 一覧・ページング

| 列 | 表示 |
|---|---|
| 取引ID | `transactionId` |
| 取引日 | `transactionDate` |
| 取引先 | `partnerName` |
| 金額(円) | `amount` を日本語ロケールで3桁区切り |
| 状態 | COMPLETEDを「完了」、PENDINGを「処理中」、CANCELLEDを「取消」と表示 |

1ページの件数は10件固定。画面初期表示時にページ0を検索する。ページャーには総件数、現在ページ、総ページ数を表示し、先頭/末尾では対応するボタンを無効化する。行クリックで取引詳細へ遷移する。検索条件を変更して検索した場合はページ0へ戻る。

検索結果のcontentが空の場合「該当する取引がありません」と表示する。

## 4. API

| 項目 | 内容 |
|---|---|
| メソッド・パス | `GET /api/transactions` |
| クエリ | `dateFrom`, `dateTo`, `status`, `partnerName`, `page`, `size` |
| ページ | 初期 `page=0`、固定 `size=10` |
| 応答 | `content`, `page`, `size`, `totalElements`, `totalPages` |
| 外部連携 | BackendからMock External APIへ中継 |

空欄の検索条件はクエリに含めない。

## 5. 表示状態・エラー

- API呼び出し中は「読み込み中...」を表示し、検索ボタンを無効化する。
- 読込中は検索結果テーブルを表示しない。
- APIエラーは画面上部に表示する。`retryable !== false` の場合のみ「再試行」ボタンを表示し、同じページ・検索条件で再取得する。
- HTTP 401を受けた場合はセッション期限切れとしてログイン画面へ遷移する。

## 6. 遷移

| 操作 | 遷移先 |
|---|---|
| 一覧行クリック | `/transactions/{transactionId}` |
| サイドメニュー「取引登録」 | `/transactions/entry` |
| ログアウト | セッションを破棄し `/login` |

## 7. 要件案との対応

要件案の条件検索、一覧表示、ページング、外部APIをBFF経由で呼び出す構成に対応する。外部API障害時は同一画面にエラーと再試行導線を表示する。

## 8. 参照元

- [取引一覧画面コンポーネント](../../../frontend/src/views/TransactionListView.vue)
- [取引APIクライアント](../../../frontend/src/api/transactions.ts)
- [取引API](../../../backend/src/main/java/com/example/backend/transaction/TransactionController.java)
- [外部APIクライアント](../../../backend/src/main/java/com/example/backend/transaction/ExternalApiClient.java)
