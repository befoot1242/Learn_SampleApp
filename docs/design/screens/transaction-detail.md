# S-06 取引詳細画面 詳細設計

## 1. 画面概要

| 項目 | 内容 |
|---|---|
| 画面ID | S-06 |
| 画面名 | 取引詳細 |
| URL | `/transactions/:id` |
| 認証・権限 | 認証必須、USER・ADMIN |
| コンポーネント | `frontend/src/views/TransactionDetailView.vue` |
| 共通枠 | `AppLayout` |
| パラメータ | `id`: 取引ID |

![取引詳細](../images/transaction-detail.png)

## 2. 画面項目

### ヘッダ情報

| 項目 | 内容 |
|---|---|
| 取引ID | `transactionId` |
| 取引日 | `transactionDate` |
| 取引先 | `partnerName` |
| 金額 | `amount` を3桁区切りし円表示 |
| 状態 | COMPLETEDを「完了」、PENDINGを「処理中」、CANCELLEDを「取消」と表示 |
| 備考 | `note`。空の場合は `-` |

### 明細

| 列 | 内容 |
|---|---|
| 行 | `lineNo` |
| 品目 | `itemName` |
| 数量 | `quantity` |
| 単価(円) | `unitPrice` を3桁区切り |
| 金額(円) | `quantity × unitPrice` を3桁区切り |

`lines` が空の場合「明細情報はありません」と表示する。

### 履歴

| 列 | 内容 |
|---|---|
| 日時 | `occurredAt` の秒以下を表示せず、日時の `T` を空白に置換 |
| イベント | REGISTEREDを「登録」、APPROVEDを「承認」、CANCELLEDを「取消」と表示 |
| 操作者 | `operator` |

## 3. 操作・遷移

| 操作 | 処理 |
|---|---|
| 初期表示 | URLの `id` で取引詳細APIを呼び出す |
| 一覧へ戻る | `/transactions` へ遷移 |
| 再読込 | 同じIDで詳細APIを再呼び出し。読込中は無効化 |
| 再試行 | エラーが再試行可能な場合のみ表示し、同じIDで再取得 |

## 4. API

| 項目 | 内容 |
|---|---|
| メソッド・パス | `GET /api/transactions/{id}` |
| パスパラメータ | 画面ルートの `id` |
| 応答 | 取引サマリー項目に `note`, `lines`, `histories` を含む詳細 |
| 外部連携 | BackendからMock External APIへ中継 |

応答の項目型は共有型定義に基づく。API契約の全項目・エラーコードは別のAPI詳細設計で扱う。

## 5. 表示状態・エラー

- API呼び出し中は「読み込み中...」を表示する。
- 取引が取得できた場合に限り、ヘッダ、明細、履歴を表示する。
- エラー時はメッセージを表示する。`retryable !== false` の場合のみ再試行ボタンを表示する。
- HTTP 401を受けた場合はセッション期限切れとしてログイン画面へ遷移する。
- 対象なしの場合の画面表示はAPI共通エラーメッセージに従う。

## 6. 要件案との対応

要件案のヘッダ情報・明細・履歴の表示、一覧への復帰、再読込に対応する。画面の取得先はBackend経由の外部APIである。

## 7. 参照元

- [取引詳細画面コンポーネント](../../../frontend/src/views/TransactionDetailView.vue)
- [取引APIクライアント](../../../frontend/src/api/transactions.ts)
- [共有データ型](../../../frontend/src/types.ts)
- [取引API](../../../backend/src/main/java/com/example/backend/transaction/TransactionController.java)
- [外部APIクライアント](../../../backend/src/main/java/com/example/backend/transaction/ExternalApiClient.java)
