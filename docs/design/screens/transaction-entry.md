# S-05 取引登録画面 詳細設計

## 1. 画面概要

| 項目 | 内容 |
|---|---|
| 画面ID | S-05 |
| 画面名 | 取引登録 |
| URL | `/transactions/entry` |
| 認証・権限 | 認証必須、USER・ADMIN |
| コンポーネント | `frontend/src/views/TransactionEntryView.vue` |
| 共通枠 | `AppLayout` |

![取引登録フォーム](../images/transaction-entry.png)

## 2. 入力項目

| 項目 | UI | 必須 | 制約・入力形式 |
|---|---|---|---|
| 取引先 | テキスト入力 | 必須 | 前後空白を除去して送信。最大100文字 |
| 金額(円) | 数値入力 | 必須 | 1以上、整数。小数不可 |
| 取引日 | 日付入力 | 必須 | HTML date形式の値を送信 |
| 備考 | 複数行テキスト | 任意 | 最大500文字。未入力は空文字として送信 |

入力チェックは確認操作時に行う。クライアントで検証した後、バックエンドでも必須・数値・長さを検証する。

## 3. 操作

| 操作 | 処理 |
|---|---|
| クリア | 全入力、項目エラー、APIエラー、requestIdを初期化 |
| 確認 | 入力を検証し、エラーがなければrequestIdを採番して確認ダイアログを開く |
| 確認ダイアログの戻る | ダイアログを閉じて入力画面に戻る。入力とrequestIdは維持 |
| 登録する | 登録APIを呼び出す。送信中はボタンを無効化し「登録中...」と表示 |
| 登録結果の取引一覧へ | `/transactions` へ遷移 |
| 登録結果の続けて登録 | 結果を閉じ、フォームとrequestIdをクリア |

## 4. 確認・結果表示

確認ダイアログには取引先、金額、取引日、備考を表示する。登録成功後は結果モーダルに受付番号、取引ID、状態を表示する。

### 確認ダイアログ

![登録内容の確認](../images/transaction-entry-confirm.png)

### 入力エラー表示

![取引登録の入力エラー](../images/transaction-entry-validation.png)

登録成功モーダルの専用キャプチャは未取得。表示項目は実装テンプレートに基づく。

## 5. API・冪等性

| 項目 | 内容 |
|---|---|
| メソッド・パス | `POST /api/transactions` |
| 要求 | `requestId`, `partnerName`, `amount`, `transactionDate`, `note` |
| 成功応答 | `receiptNumber`, `transactionId`, `status` |

`requestId` は確認ダイアログを初めて開く時点でUUIDとして採番する。失敗後の再操作では同じIDを維持し、登録成功時またはフォームクリア時にリセットする。外部API側は同一requestIdの再送を冪等に処理する。

## 6. エラー・状態

- 入力チェックエラーは各項目の下に表示し、確認ダイアログを開かない。
- APIエラー時は入力値を保持し、確認ダイアログを閉じて画面上部に理由を表示する。
- `EXTERNAL_TIMEOUT` の場合は、外部処理が完了済みの可能性を案内し、取引一覧で状態を確認するリンクを表示する。
- その他の再試行可能エラーは、入力を保持して再度「確認」から操作するよう案内する。
- HTTP 401を受けた場合はセッション期限切れとしてログイン画面へ遷移する。
- `FAIL` を含む取引先名は外部システムエラー、`NG` を含む取引先名は業務エラーを発生させる動作確認用の条件として画面に案内される。

## 7. 要件案との差分

確認ダイアログ、成功時の受付番号・結果表示、失敗時の入力保持、requestIdによる重複送信防止を実装している。要件案の「タイムアウト時の状態照会導線」に対して、現行実装は状態照会APIを設けず、取引一覧へのリンクと状態確認メッセージを表示する。

## 8. 参照元

- [取引登録画面コンポーネント](../../../frontend/src/views/TransactionEntryView.vue)
- [取引APIクライアント](../../../frontend/src/api/transactions.ts)
- [共有データ型](../../../frontend/src/types.ts)
- [取引API](../../../backend/src/main/java/com/example/backend/transaction/TransactionController.java)
- [外部APIクライアント](../../../backend/src/main/java/com/example/backend/transaction/ExternalApiClient.java)
