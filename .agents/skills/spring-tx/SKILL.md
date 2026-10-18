---
name: spring-tx
description: "SpringのHTTP APIごとにトランザクション境界・コミット/ロールバック条件・例外別のrollback対象を調査して文書化する。API別のトランザクション制御、rollback契機、戻るDBデータの設計資料を作成・更新するときに使用する。"
---

# Springトランザクション制御の整理

HTTP APIごとに、例外が発生する時点、トランザクションの有無、rollbackされる永続データをソースコードに基づいて文書化する。

## 手順

1. **資料の置き場所を確認する。** 既存の設計書一覧、トランザクション設計、API設計、DB設計を読む。更新依頼なら既存資料を拡張し、専用資料の新設は既存構成またはユーザーの指定に合わせる。API契約の詳細はAPI設計に置き、相互リンクする。
2. **HTTPエンドポイントを全件列挙する。** Springのクラス/メソッドマッピング（`@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping`）を検索する。DBを操作しないAPIも含め、HTTPメソッドとパスを1件ずつ記録し、既存API一覧と突合する。
3. **呼び出し経路を追う。** ControllerからService、Repository、外部クライアント、監査サービス、例外ハンドラーまで追跡する。書き込み内容と実行順序を特定し、API名から動作を推測しない。
4. **トランザクション境界を特定する。** メソッド/クラス/メタアノテーションの `@Transactional`、propagation、`readOnly`、isolation、timeout、`rollbackFor` / `noRollbackFor`、プログラムによるトランザクション操作、rollback-only指定を検索する。呼び出しがSpring Proxyを越えるか確認する。自己呼び出しにはProxyのAdviceが適用されない。Serviceの明示境界とSpring Data Repositoryの既定設定を分けて記載し、フレームワーク既定値をプロジェクトの明示設定として書かない。
5. **実行順に失敗点を洗い出す。** 要求解析/入力検証、認証/認可、検索/業務チェック、各DB書き込み・flush・commit、監査保存、外部HTTP応答/通信障害、commit後の失敗を確認する。コードや境界から特定できる例外型を記載し、「など」のような曖昧な包括表現を避ける。実行環境依存で型を断定できない障害は、発生境界を示して型は未確定とする。
6. **失敗点ごとにrollback範囲を確定する。** 例外が当該TX境界から伝播したとき、同一TX内で先に書き込まれた具体的なDBレコード/論理データを列挙する。Repository呼び出しが複数でも、同一TXならそのすべての書き込みがrollbackされる。対象が複数なら対象ごとに行を分ける。rollbackされるローカルDB書き込みがなければ `なし（ローカルDB更新なし）` と記載し、外部状態や非DB状態が残る可能性は別途説明する。
7. **例外処理からHTTP応答まで追う。** 例外がcatch、握り潰し、変換、再throwのどれかと、`@RestControllerAdvice`/Controller処理に対してrollbackが判断される位置を記す。HTTP 4xx/5xxとrollbackを同一視しない。catch後に正常終了すればTXが継続/commitし得る。再throwならTXのrollback規則に従う。commit後の例外では完了済みTXをrollbackできない。
8. **独立した副作用を分ける。** `REQUIRES_NEW`、nested transaction、非同期処理、メッセージ、ファイル、セッション変更、リモートHTTP呼び出しを外側のDB TXと分けて扱う。ローカルrollbackで戻らない副作用と、独立監査ログだけが残る/業務TXだけがrollbackするケースを明記する。
9. **俯瞰表と例外別表を分ける。** APIの正常系/境界を示す「APIごとのトランザクション一覧」と、失敗点ごとのrollback対象を示す「API別rollback表」を作る。一覧はAPI×TX単位ごと、rollback表はAPI×例外発生点×rollback対象ごとに行を作る。
10. **横断規則と処理フローを説明する。** rollback既定規則とプロジェクト固有設定、commit点、read-only挙動、Proxyの注意、HTTP応答への変換を記す。外側DB TX、`REQUIRES_NEW`、外部副作用が関係する処理はMermaidシーケンス図にし、表と矛盾しないようにする。
11. **事実と推論を区別する。** ソース確認済み、フレームワーク既定動作、未確認/実行時依存をラベル分けする。コードに仕組みがなければローカルDBとリモートAPIの原子性を主張しない。現行動作とリスクを説明し、依頼がない限り設計変更案を混ぜない。

## APIごとのトランザクション一覧の形式

この表はAPI全体とトランザクション/副作用の境界を俯瞰する。一覧は例外ごとの詳細を載せる表ではなく、具体的な例外・rollback対象は別の「API別rollback表」に記す。

列は次の順に固定する。

| API | TX単位 | 明示的TX境界 | DB操作/対象 | 外部・非DB操作 | 正常時 | 失敗時 |
|---|---|---|---|---|---|---|
| `METHOD /path` | Serviceまたは独立TXの単位 | annotation/属性、または「アプリ明示TXなし」 | 1つのDB操作/対象、または「なし」 | 1つの外部/非DB操作、または「なし」 | 1つのcommit/応答結果 | rollback表への参照、またはTX外の失敗結果 |

### 記載ルール

- **行の単位**: 1行は「API × TX境界/処理単位 × DBまたは外部副作用1項目」。APIは同じでも、業務TX、`REQUIRES_NEW` 監査TX、外部API呼び出しなどが別ならAPI名を繰り返して行を分ける。
- **API**: HTTPメソッドと完全なパスを記す。クラスレベルmappingを含む実際のrouteを書く。
- **TX単位**: 境界の持ち主となるServiceメソッド、監査処理、外部呼出し等を1つ記す。
- **明示的TX境界**: `@Transactional` の位置、propagation、`readOnly` 等を記す。アプリ側に明示TXがなければ `アプリ明示TXなし` とする。Repository既定動作はこの欄へ混ぜず、別の注記で出典/推定を区別する。
- **DB操作/対象**: 1セルに1つの操作と対象だけを書く（例: `app_userへのINSERT`, `role_historyへのINSERT`, `app_user.statusのUPDATE`, `app_userのSELECT`）。複数テーブルを書き込む場合は行を分ける。同じTX内の書込みには同じTX単位を記して関係を示す。
- **外部・非DB操作**: 1セルに外部endpoint、セッション変更等の1副作用だけを書く。複数の副作用がある場合は行を分ける。該当しなければ `なし`。
- **正常時**: 1つの境界の正常終了結果だけを書く（例: `UserService正常終了後に業務TX commit`, `監査TX commit`, `外部応答を返却`）。
- **失敗時**: 詳細を重複させず、rollback表へのリンクを記す。TXがなければ `ローカルDB rollbackなし。外部通信エラーは外部API設計参照` のように1つの説明にする。
- **1セル1情報**: セル内に複数のテーブル/例外/副作用を読点・スラッシュ・箇条書きで列挙しない。該当情報ごとに行を追加する。「なし」は1つの情報として許可する。
- **網羅性**: DB更新の有無にかかわらず全HTTP APIを含める。API一覧と照合し、欠落/重複を確認する。同じAPIの複数TX単位は意図した重複として扱う。

## API別rollback表の形式

| API | 例外発生点 | 例外/エラー | トランザクション境界 | rollback対象（1行1項目） |
|---|---|---|---|---|
| `METHOD /path` | 1つの発生点 | 具体的例外/エラー | 対象TXを1つ | rollbackされる1つの永続データ、または `なし` |

- 例外発生点ごとに行を作る。同じ例外が複数DB項目をrollbackする場合は、API/発生点/例外/TXを同じにして、rollback対象だけを変えた行に分ける。
- 失敗した書込み自身が未保存なら、その行は「rollbackされる既存データ」ではない。先に同じTXで成功した書込みを列挙する。書込み済みか曖昧な場合はflush/commit挙動を確認し、推測で記さない。
- TX開始前の検証失敗や、書込み前の業務例外は、rollback対象を `なし（書き込み前）` とする。
- TX外の外部API/セッション操作はrollback対象に記載せず、成功後に残る可能性を本文または別欄で説明する。

## 必ず確認するrollback規則

- Spring宣言的TXは、Proxyが適用されるメソッド境界で有効なpropagation/rollback規則を確認する。すべての例外がrollbackするとは仮定しない。
- 既定では未処理のunchecked exception（`RuntimeException`）と `Error` がTX境界を越えるとrollbackする。checked exceptionは明示設定がない限り自動rollbackしない。プロジェクト側の上書き有無を確認する。
- catch/rethrow動作を確認する。例外をcatchして正常returnすると、rollback-only指定がない限りcommitされる場合がある。
- 永続化エラーは `save`、flush、TX commitの各時点で起こり得る。commit/flush失敗点と、同じTXでそれ以前に書き込まれた全対象を含める。
- `REQUIRES_NEW` は独立してcommit/rollbackする。内側のTXがcommit済みなら、後の外側rollbackでは取り消されない。
- Service TX開始前の入力検証/認証認可拒否には、そのService TXでrollbackされるデータはない。
- 外部API書き込み、HTTPセッション変更、その他の非DB副作用はローカルDB rollbackでは元に戻らない。タイムアウトなど結果不明と、外部から確定エラー応答を受けた場合を区別する。
- Repositoryの既定値はメソッド種別とフレームワーク版で異なる。結論に影響する場合は対象バージョンまたは公式資料で確認し、それ以外は推論/未確認と明記する。

## 表の不変条件

完了前にすべて確認する。

- DB TXがないAPIも含め、マッピングされたHTTP endpointがすべて載っている。
- 「APIごとのトランザクション一覧」はAPI×TX単位の俯瞰表、「API別rollback表」はAPI×例外発生点×rollback対象の詳細表として分離されている。
- 一覧表は指定した7列を保ち、1行がDB/外部操作の複数項目を抱えていない。
- API表の各行は1つの失敗点と該当する例外/エラーを示し、複数条件を曖昧に束ねていない。
- rollback対象セルは永続データ1項目、または明示した「なし」のどちらかだけを含む。
- 同じTX内の先行書き込みがrollbackされる失敗点では、すべての対象が個別に記載されている。
- 独立した `REQUIRES_NEW` の書き込みと外部/非DB副作用が、残存または独立rollbackとして正しく示されている。
- HTTP応答への変換と、TXのcommit/rollback時点が区別されている。
- Markdown表の列数、相対リンク、Mermaid図を確認している。
- テスト/テストケースは別資料にし、ユーザーから明示的に要求された場合を除き本書に含めない。

完了条件: endpoint一覧とソース/API一覧が一致し、全rollback行が表の不変条件を満たし、各主張が「確認済み」「フレームワーク由来」「未確認」のいずれかに分類されている。