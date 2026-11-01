# 文書種別ごとの読み方と出力スキーマ

`dump_excel.py` の `template_guess` が返す種別と対応する。種別は確定したものを `document_type` に入れる。
どれにも当てはまらなければ `generic` を使う。

全スキーマ共通のフィールド:

```json
{
  "document_type": "screen_spec",
  "source_file": "受注一覧_画面設計書.xlsx",
  "sheet": "受注一覧",
  "_meta": {
    "legend": {"#FFFF00": "変更箇所", "◎": "必須", "取消線": "削除"},
    "hidden_sheets": ["作業用"],
    "render_used": true
  },
  "review_comments": [{"cell": "C11", "author": "reviewer", "text": "2026/10 追加項目"}],
  "cross_references": [{"text": "共通部品定義書 参照", "cell": "U20", "target": "共通部品定義書.xlsx"}],
  "unresolved": [{"where": "B30:F40", "issue": "矢印の接続先が判別できない（画像では『却下』と『保留』の間）"}],
  "hidden_notes": [{"cell": "Z3", "text": "白文字で書かれた注記"}]
}
```

目次:
- [screen_spec 画面設計書](#screen_spec)
- [table_spec テーブル定義書](#table_spec)
- [interface_spec IF/API 仕様書](#interface_spec)
- [test_spec 試験項目表](#test_spec)
- [flow 業務フロー・処理フロー](#flow)
- [batch_spec バッチ仕様書](#batch_spec)
- [change_history 改訂履歴](#change_history)
- [generic 汎用](#generic)

---

## screen_spec

典型構成: 上部にヘッダ（画面ID・画面名・作成者・日付）、画面イメージ（図形または罫線で描いた
モック）、項目定義表（No/項目名/物理名/型/桁/必須/初期値/備考）、イベント定義表
（ボタン/アクション/遷移先）、メッセージ一覧。

読み方の注意:
- 画面イメージ中の図形テキスト（ボタン名）と項目定義表の項目名を対応づける
- 「必須」列は ○/◎/●/レ/Y など記号が混在する。凡例で意味を確定してから `required` に変換する
- 「桁」が `10,2` のように精度を含むことがある → `length` と `precision` に分ける
- 入力規則（`data_validations`）はプルダウン候補を表していることがある

```json
{
  "document_type": "screen_spec",
  "screen": {"id": "SCR-ORD-001", "name": "受注一覧画面", "author": "山田", "created": "2026-09-01",
             "source_range": "B2:L5"},
  "items": [
    {"no": 1, "name": "受注番号", "physical_name": "order_no", "type": "文字", "length": 10,
     "precision": null, "required": true, "default": null, "notes": "自動採番",
     "changed": false, "source_range": "B9:AD9"}
  ],
  "events": [
    {"trigger": "検索ボタン押下", "action": "条件で検索し一覧を再表示", "transition": null, "source_range": "B20:AD20"}
  ],
  "messages": [{"id": "MSG001", "text": "該当データがありません", "source_range": "B30:AD30"}],
  "flow": [{"from": "検索実行", "to": "一覧表示", "label": null, "source": "drawing"}]
}
```

## table_spec

典型構成: テーブル物理名/論理名のヘッダ、カラム定義表（No/論理名/物理名/型/桁/NOT NULL/PK/FK/デフォルト/備考）、
インデックス定義、1シート複数テーブルのことも多い（`regions` で分割）。

```json
{
  "document_type": "table_spec",
  "tables": [
    {"logical_name": "受注", "physical_name": "orders", "description": null, "source_range": "B2:T30",
     "columns": [
       {"no": 1, "logical_name": "受注番号", "physical_name": "order_no", "type": "varchar", "length": 10,
        "precision": null, "nullable": false, "pk": true, "fk": null, "default": null, "notes": null,
        "source_range": "B6:T6"}
     ],
     "indexes": [{"name": "idx_orders_customer", "columns": ["customer_id"], "unique": false}]}
  ]
}
```

注意: PK/FK 列は ○ か数字（複合キー順）で書かれる。数字なら `pk_order` も残す。

## interface_spec

典型構成: IF-ID、方向（送信/受信）、プロトコル、エンドポイント、リクエスト項目表、レスポンス項目表、
エラーコード表。階層構造（`items[].children`）はインデント（先頭スペース）や列位置のズレで表現される
ことがあるので、ダンプの `col` の違いと画像で階層を読む。

```json
{
  "document_type": "interface_spec",
  "interface": {"id": "IF-001", "name": "受注登録API", "direction": "受信", "protocol": "HTTPS/JSON",
                "endpoint": "POST /api/orders", "source_range": "B2:L8"},
  "request": [{"name": "orderNo", "type": "string", "length": 10, "required": true, "level": 1,
               "notes": null, "source_range": "B12:T12", "children": []}],
  "response": [],
  "errors": [{"code": "E001", "message": "必須項目未入力", "http_status": 400}]
}
```

## test_spec

典型構成: テストID、大分類/中分類/小分類（結合セルで階層化）、前提条件、手順、期待結果、
実施結果（OK/NG）、実施日、担当。**結合セルで階層化された分類は、結合の先頭セルにしか値が無い**ので、
`merged` を使って各行に分類を埋める。

```json
{
  "document_type": "test_spec",
  "cases": [
    {"id": "T-001", "category": ["受注", "登録", "正常系"], "precondition": "ログイン済み",
     "steps": ["受注画面を開く", "必須項目を入力して登録"], "expected": "一覧に追加される",
     "result": "OK", "executed_on": "2026-10-01", "assignee": "山田", "source_range": "B5:Z5"}
  ]
}
```

## flow

典型構成: スイムレーン（横または縦の結合セルで部門名）、図形（開始/終了/処理/判断）、矢印。
`drawings` の `kind == connector` と `connect_from_text/connect_to_text` が主役。
判断（ひし形 `diamond`）から出る矢印はラベル（Yes/No）が近くの小さな図形やセルに書かれる。

```json
{
  "document_type": "flow",
  "lanes": [{"name": "営業", "source_range": "B3:B20"}],
  "nodes": [{"id": "n1", "text": "受注受付", "shape": "rect", "lane": "営業", "anchor": "C4:F6"}],
  "edges": [{"from": "n1", "to": "n2", "label": "Yes", "source": "drawing"}],
  "notes": []
}
```

接続が解決できない矢印: `anchor.from`/`anchor.to` のセル座標に最も近い図形を候補にし、
画像で確認する。それでも曖昧なら `unresolved` へ。

## batch_spec

```json
{
  "document_type": "batch_spec",
  "job": {"id": "JOB-001", "name": "日次受注集計", "schedule": "毎日 02:00", "trigger": "cron",
          "inputs": ["orders"], "outputs": ["daily_summary"], "source_range": "B2:L10"},
  "steps": [{"no": 1, "description": "前日分の受注を抽出", "error_handling": "異常終了・再実行可"}],
  "exit_codes": [{"code": 0, "meaning": "正常終了"}]
}
```

## change_history

```json
{
  "document_type": "change_history",
  "revisions": [{"version": "1.0", "date": "2026-09-01", "author": "山田", "description": "初版",
                 "source_range": "A2:C2"}]
}
```

本文シートの `changed: true` と、最新の改訂内容を突き合わせると、変更箇所の裏取りになる。

## generic

種別が決まらないとき。領域ごとに「表」か「キー・値」か「自由記述」かを判断して並べる。

```json
{
  "document_type": "generic",
  "title": "...",
  "sections": [
    {"kind": "key_value", "title": "基本情報", "source_range": "B2:H6",
     "data": {"画面ID": "SCR-001", "作成者": "山田"}},
    {"kind": "table", "title": "項目一覧", "source_range": "B8:Z20",
     "columns": ["No", "項目名", "型"], "rows": [[1, "受注番号", "文字"]]},
    {"kind": "text", "title": "備考", "source_range": "B22:Z25", "text": "..."}
  ],
  "flow": []
}
```
