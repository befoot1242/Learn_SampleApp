# 詳細設計書一覧

## 1. 文書の位置付け

本資料群は、[基本設計書](../basic-design.md)および[画面別詳細設計](../screens/README.md)を、現行実装に基づいて補足する。実装で確認できた仕様、要件案との差分、未確認事項を区別する。

画面項目・画面操作は画面別詳細設計、API契約はAPI設計、永続データはDB設計に記載する。テスト観点・テストケースは含めない。

## 2. 詳細設計一覧

| ID | 文書 | 対象 |
|---|---|---|
| D-API | [API詳細設計](api-design.md) | Backend APIのエンドポイント、要求・応答、入力制約、共通エラー |
| D-DB | [DB詳細設計](database-design.md) | H2に永続化するエンティティ、項目、制約、関連、初期データ |
| D-SEC | [認証・認可詳細設計](security-design.md) | セッション、ログイン、CSRF、画面/API認可 |
| D-EXT | [外部API連携詳細設計](external-api-design.md) | BFFとMock External APIの契約、タイムアウト、再試行、エラー正規化、冪等性 |
| D-FLOW | [主要処理フロー詳細設計](processing-flow-design.md) | 認証、ユーザ管理、取引参照・登録の処理順とシーケンス |
| D-TX | [APIトランザクション制御詳細設計](transaction-control-design.md) | APIごとの境界、コミット/ロールバック契機、監査・外部APIとの独立性 |

## 3. 共通の読み方

- APIのHTTP契約を確認する場合はD-APIを参照する。外部API側の契約や障害時の変換はD-EXTを参照する。
- テーブル項目や制約を確認する場合はD-DBを参照する。画面項目との対応は[各画面設計書](../screens/README.md)を参照する。
- 認証・認可境界はD-SECを参照する。画面遷移条件の概要は[基本設計書](../basic-design.md)に記載する。
- 処理の時系列はD-FLOWを参照する。個別画面の表示・操作は画面別詳細設計に記載する。
