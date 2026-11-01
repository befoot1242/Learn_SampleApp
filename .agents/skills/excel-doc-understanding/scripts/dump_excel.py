#!/usr/bin/env python3
"""
Excel 文書を「レイアウト情報つき中間表現」にダンプする。

人間向けに作られた Excel（方眼紙、結合セル、罫線で描いた表、色による凡例、
図形・矢印）から、AI が意味を復元するのに必要な情報を取り出す。

出力（<outdir>/ 配下）:
  index.json            ワークブック全体の目次（シート一覧・サイズ・非表示・図形数・テンプレート推定）
  <sheet>.json          セル・結合・罫線領域・図形・コメント・凡例候補の完全ダンプ
  <sheet>.md            人間/LLM が読みやすい Markdown グリッド（結合・色・領域つき）

使い方:
  python dump_excel.py <input.xlsx> <outdir> [--max-cells 20000] [--sheets "A,B"]
"""
import argparse
import json
import os
import re
import sys
import zipfile
import xml.etree.ElementTree as ET
from collections import defaultdict

import openpyxl
from openpyxl.utils import get_column_letter, range_boundaries

NS = {
    "xdr": "http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing",
    "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
    "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
    "rel": "http://schemas.openxmlformats.org/package/2006/relationships",
    "m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
}

# よくある SIer 系テンプレートのキーワード（シート名/先頭領域の文字列で判定）
TEMPLATE_HINTS = {
    "screen_spec": ["画面設計", "画面仕様", "画面定義", "画面項目", "項目定義", "画面レイアウト"],
    "table_spec": ["テーブル定義", "テーブル設計", "エンティティ", "カラム", "物理名", "論理名", "DB定義"],
    "interface_spec": ["IF仕様", "I/F仕様", "インタフェース", "インターフェース", "API仕様", "電文", "リクエスト", "レスポンス"],
    "test_spec": ["試験項目", "テスト項目", "テスト仕様", "テストケース", "期待結果", "試験仕様", "確認観点"],
    "flow": ["業務フロー", "処理フロー", "フロー図", "シーケンス"],
    "batch_spec": ["バッチ", "ジョブ", "JOB", "処理概要", "起動条件"],
    "change_history": ["変更履歴", "改訂履歴", "更新履歴"],
}


# ---------------------------------------------------------------- utilities

def color_of(c):
    """openpyxl の Color を '#RRGGBB' か None に。theme/indexed は 'theme:n' として残す。"""
    if c is None:
        return None
    try:
        if c.type == "rgb" and c.rgb and isinstance(c.rgb, str):
            rgb = c.rgb[-6:]
            if rgb.upper() in ("000000", "FFFFFF") and c.rgb[:2] in ("00",):
                return None
            return "#" + rgb.upper()
        if c.type == "theme":
            return f"theme:{c.theme}" + (f"/tint{round(c.tint, 2)}" if c.tint else "")
        if c.type == "indexed":
            if c.indexed in (64, 65):  # system fg/bg
                return None
            return f"indexed:{c.indexed}"
    except Exception:
        return None
    return None


def border_sides(cell):
    b = cell.border
    out = {}
    for side in ("top", "bottom", "left", "right"):
        s = getattr(b, side)
        if s is not None and s.style and s.style != "none":
            out[side] = s.style
    return out


def norm_val(v):
    if v is None:
        return None
    if isinstance(v, (int, float, bool)):
        return v
    if hasattr(v, "isoformat"):
        return v.isoformat()
    return str(v)


def sanitize_name(name):
    return re.sub(r'[\\/:*?"<>|\s]+', "_", name).strip("_") or "sheet"


# ---------------------------------------------------------------- drawings

def load_drawings(xlsx_path):
    """xl/drawings/*.xml を直接読んで、シート名 -> 図形リストを返す。
    openpyxl は図形を読まないので zip を直接パースする。"""
    result = defaultdict(list)
    try:
        z = zipfile.ZipFile(xlsx_path)
    except Exception:
        return result
    names = set(z.namelist())

    # workbook.xml: sheet name -> rId -> worksheet path
    try:
        wb_xml = ET.fromstring(z.read("xl/workbook.xml"))
        wb_rels = ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))
    except Exception:
        return result
    rid_to_target = {r.get("Id"): r.get("Target") for r in wb_rels.findall("rel:Relationship", NS)}
    sheet_paths = {}
    for s in wb_xml.findall("m:sheets/m:sheet", NS):
        rid = s.get(f"{{{NS['r']}}}id")
        target = rid_to_target.get(rid, "")
        target = target.lstrip("/")
        if not target.startswith("xl/"):
            target = "xl/" + target
        sheet_paths[s.get("name")] = target

    for sheet_name, ws_path in sheet_paths.items():
        rels_path = ws_path.replace("worksheets/", "worksheets/_rels/") + ".rels"
        if rels_path not in names:
            continue
        rels = ET.fromstring(z.read(rels_path))
        for r in rels.findall("rel:Relationship", NS):
            if not r.get("Type", "").endswith("/drawing"):
                continue
            target = r.get("Target")
            dpath = os.path.normpath(os.path.join(os.path.dirname(ws_path), target)).replace("\\", "/")
            if dpath not in names:
                continue
            try:
                droot = ET.fromstring(z.read(dpath))
            except Exception:
                continue
            result[sheet_name].extend(parse_drawing(droot))
    return result


def _anchor(el):
    def rc(tag):
        n = el.find(f"xdr:{tag}", NS)
        if n is None:
            return None
        col = n.find("xdr:col", NS)
        row = n.find("xdr:row", NS)
        if col is None or row is None:
            return None
        return f"{get_column_letter(int(col.text) + 1)}{int(row.text) + 1}"
    return {"from": rc("from"), "to": rc("to")}


def _texts(sp):
    parts = []
    for p in sp.findall(".//a:p", NS):
        t = "".join((r.text or "") for r in p.findall(".//a:t", NS))
        if t.strip():
            parts.append(t.strip())
    return "\n".join(parts)


def _shape_meta(sp, kind):
    cnv = sp.find(f"xdr:nv{'Cxn' if kind == 'connector' else ''}SpPr/xdr:cNvPr", NS)
    sid = cnv.get("id") if cnv is not None else None
    name = cnv.get("name") if cnv is not None else None
    geom = sp.find(".//a:prstGeom", NS)
    preset = geom.get("prst") if geom is not None else None
    fill = sp.find("xdr:spPr/a:solidFill/a:srgbClr", NS)
    fill_color = "#" + fill.get("val") if fill is not None else None
    # 線の先端（矢印の向き）
    tail = sp.find("xdr:spPr/a:ln/a:tailEnd", NS)
    head = sp.find("xdr:spPr/a:ln/a:headEnd", NS)
    arrows = {}
    if head is not None and head.get("type", "none") != "none":
        arrows["head"] = head.get("type")
    if tail is not None and tail.get("type", "none") != "none":
        arrows["tail"] = tail.get("type")
    meta = {"id": sid, "name": name, "kind": kind, "preset": preset, "fill": fill_color}
    if arrows:
        meta["arrows"] = arrows
    return meta


def parse_drawing(root):
    shapes = []

    def walk(container, anchor):
        for child in container:
            tag = child.tag.split("}")[-1]
            if tag in ("twoCellAnchor", "oneCellAnchor", "absoluteAnchor"):
                walk(child, _anchor(child))
            elif tag == "grpSp":
                walk(child, anchor)
            elif tag == "sp":
                m = _shape_meta(child, "shape")
                m["text"] = _texts(child)
                m["anchor"] = anchor
                shapes.append(m)
            elif tag == "cxnSp":
                m = _shape_meta(child, "connector")
                st = child.find("xdr:nvCxnSpPr/xdr:cNvCxnSpPr/a:stCxn", NS)
                en = child.find("xdr:nvCxnSpPr/xdr:cNvCxnSpPr/a:endCxn", NS)
                m["connect_from_id"] = st.get("id") if st is not None else None
                m["connect_to_id"] = en.get("id") if en is not None else None
                m["anchor"] = anchor
                shapes.append(m)
            elif tag == "pic":
                cnv = child.find("xdr:nvPicPr/xdr:cNvPr", NS)
                shapes.append({"id": cnv.get("id") if cnv is not None else None,
                               "name": cnv.get("name") if cnv is not None else None,
                               "kind": "picture", "anchor": anchor})
            elif tag == "graphicFrame":
                cnv = child.find("xdr:nvGraphicFramePr/xdr:cNvPr", NS)
                shapes.append({"id": cnv.get("id") if cnv is not None else None,
                               "name": cnv.get("name") if cnv is not None else None,
                               "kind": "chart_or_smartart", "anchor": anchor})

    walk(root, None)
    # コネクタの接続先を図形名/テキストに解決
    by_id = {s["id"]: s for s in shapes if s.get("id")}
    for s in shapes:
        if s["kind"] == "connector":
            for key in ("connect_from_id", "connect_to_id"):
                t = by_id.get(s.get(key))
                if t:
                    s[key.replace("_id", "_text")] = t.get("text") or t.get("name")
    return shapes


# ---------------------------------------------------------------- regions

def bordered_regions(cells):
    """罫線を持つセルの連結成分を「領域」として切り出す（表・枠の検出）。
    方眼紙 Excel では罫線で囲まれた塊がほぼ意味単位に対応する。"""
    bordered = {(c["row"], c["col"]) for c in cells if c.get("border")}
    seen = set()
    regions = []
    for start in sorted(bordered):
        if start in seen:
            continue
        stack = [start]
        comp = []
        seen.add(start)
        while stack:
            r, c = stack.pop()
            comp.append((r, c))
            for nr, nc in ((r + 1, c), (r - 1, c), (r, c + 1), (r, c - 1)):
                if (nr, nc) in bordered and (nr, nc) not in seen:
                    seen.add((nr, nc))
                    stack.append((nr, nc))
        rows = [p[0] for p in comp]
        cols = [p[1] for p in comp]
        if len(comp) < 2:
            continue
        regions.append({
            "range": f"{get_column_letter(min(cols))}{min(rows)}:{get_column_letter(max(cols))}{max(rows)}",
            "min_row": min(rows), "max_row": max(rows), "min_col": min(cols), "max_col": max(cols),
            "cell_count": len(comp),
        })
    return regions


# ---------------------------------------------------------------- sheet dump

def dump_sheet(ws, ws_values, drawings, max_cells):
    merged = []
    merged_lookup = {}  # (row,col) -> top-left coord
    for mr in ws.merged_cells.ranges:
        min_col, min_row, max_col, max_row = range_boundaries(str(mr))
        tl = f"{get_column_letter(min_col)}{min_row}"
        merged.append({"range": str(mr), "top_left": tl,
                       "rows": max_row - min_row + 1, "cols": max_col - min_col + 1})
        for r in range(min_row, max_row + 1):
            for c in range(min_col, max_col + 1):
                merged_lookup[(r, c)] = tl

    cells = []
    truncated = False
    count = 0
    for row in ws.iter_rows():
        for cell in row:
            v = cell.value
            fill = color_of(cell.fill.fgColor) if cell.fill and cell.fill.fill_type == "solid" else None
            borders = border_sides(cell)
            comment = cell.comment.text.strip() if cell.comment else None
            key = (cell.row, cell.column)
            in_merge = merged_lookup.get(key)
            if v is None and not fill and not borders and not comment:
                continue
            if in_merge and in_merge != cell.coordinate and v is None and not comment:
                # 結合領域の非先頭セルは罫線/色だけ持つので省略（領域検出用に border は残す）
                if borders:
                    cells.append({"coord": cell.coordinate, "row": cell.row, "col": cell.column,
                                  "merged_into": in_merge, "border": borders})
                continue
            count += 1
            if count > max_cells:
                truncated = True
                break
            entry = {"coord": cell.coordinate, "row": cell.row, "col": cell.column}
            cached = ws_values[cell.coordinate].value if ws_values is not None else None
            if isinstance(v, str) and v.startswith("="):
                entry["formula"] = v
                entry["value"] = norm_val(cached)
            else:
                entry["value"] = norm_val(v)
            if fill:
                entry["fill"] = fill
            f = cell.font
            if f is not None:
                if f.bold:
                    entry["bold"] = True
                if f.strike:
                    entry["strike"] = True
                fc = color_of(f.color)
                if fc and fc not in ("#000000", "theme:1"):
                    entry["font_color"] = fc
            if borders:
                entry["border"] = borders
            if comment:
                entry["comment"] = comment
                if cell.comment.author:
                    entry["comment_author"] = cell.comment.author
            if cell.hyperlink is not None:
                entry["hyperlink"] = cell.hyperlink.target or cell.hyperlink.location
            if in_merge and in_merge == cell.coordinate:
                entry["merged"] = next(m["range"] for m in merged if m["top_left"] == cell.coordinate)
            al = cell.alignment
            if al is not None and al.wrap_text:
                entry["wrap"] = True
            cells.append(entry)
        if truncated:
            break

    regions = bordered_regions(cells)

    # 凡例候補：色つきで短い文字列を持つセル（「黄色＝変更」など）
    legend = [{"coord": c["coord"], "fill": c.get("fill"), "font_color": c.get("font_color"),
               "text": c.get("value")}
              for c in cells
              if (c.get("fill") or c.get("font_color")) and isinstance(c.get("value"), str) and len(c["value"]) <= 20]
    # 使われている塗り色の分布（凡例推定の材料）
    fill_counts = defaultdict(int)
    for c in cells:
        if c.get("fill"):
            fill_counts[c["fill"]] += 1

    hidden_rows = [r for r, d in ws.row_dimensions.items() if d.hidden]
    hidden_cols = [k for k, d in ws.column_dimensions.items() if d.hidden]

    validations = []
    try:
        for dv in ws.data_validations.dataValidation:
            validations.append({"range": str(dv.sqref), "type": dv.type, "formula1": dv.formula1})
    except Exception:
        pass

    return {
        "sheet": ws.title,
        "state": ws.sheet_state,
        "dimension": ws.dimensions,
        "max_row": ws.max_row, "max_col": ws.max_column,
        "print_area": ws.print_area,
        "freeze_panes": ws.freeze_panes,
        "hidden_rows": hidden_rows[:200],
        "hidden_cols": hidden_cols[:50],
        "merged": merged,
        "regions": regions,
        "fill_colors": dict(sorted(fill_counts.items(), key=lambda kv: -kv[1])),
        "legend_candidates": legend[:50],
        "data_validations": validations,
        "drawings": drawings,
        "cells": cells,
        "truncated": truncated,
    }


# ---------------------------------------------------------------- markdown

def _short(v, limit=40):
    s = "" if v is None else str(v).replace("\n", "⏎").replace("|", "｜")
    return s if len(s) <= limit else s[: limit - 1] + "…"


def sheet_markdown(d):
    lines = [f"# シート: {d['sheet']}", ""]
    meta = [f"範囲 {d['dimension']}", f"状態 {d['state']}"]
    if d["print_area"]:
        meta.append(f"印刷範囲 {d['print_area']}")
    if d["hidden_rows"]:
        meta.append(f"非表示行 {len(d['hidden_rows'])}件")
    if d["hidden_cols"]:
        meta.append(f"非表示列 {d['hidden_cols']}")
    if d["truncated"]:
        meta.append("※ セル数上限で打ち切り")
    lines.append("- " + " / ".join(meta))
    if d["fill_colors"]:
        lines.append("- 塗り色の使用数: " + ", ".join(f"{k}×{v}" for k, v in list(d["fill_colors"].items())[:8]))
    if d["legend_candidates"]:
        lines.append("- 凡例候補（色つき短文セル）: " +
                     ", ".join(f"{c['coord']}『{_short(c['text'], 12)}』({c.get('fill') or c.get('font_color')})"
                               for c in d["legend_candidates"][:10]))
    lines.append("")

    if d["drawings"]:
        lines.append("## 図形・矢印")
        for s in d["drawings"]:
            a = s.get("anchor") or {}
            pos = f"{a.get('from')}→{a.get('to')}" if a else "?"
            if s["kind"] == "connector":
                lines.append(f"- 矢印/線 `{s.get('preset')}` [{pos}] "
                             f"{s.get('connect_from_text') or s.get('connect_from_id') or '?'} ⇒ "
                             f"{s.get('connect_to_text') or s.get('connect_to_id') or '?'}"
                             + (f" arrows={s['arrows']}" if s.get("arrows") else ""))
            else:
                lines.append(f"- {s['kind']} `{s.get('preset') or s.get('name')}` [{pos}]"
                             + (f" fill={s['fill']}" if s.get("fill") else "")
                             + (f": {_short(s.get('text'), 80)}" if s.get("text") else ""))
        lines.append("")

    # グリッド：値かコメントを持つセルだけ、空の行列を落として表に
    cell_map = {(c["row"], c["col"]): c for c in d["cells"] if "value" in c}
    if not cell_map:
        lines.append("（値を持つセルなし）")
        return "\n".join(lines)
    rows = sorted({r for r, _ in cell_map})
    cols = sorted({c for _, c in cell_map})
    # 結合セルの範囲を列に反映させるため、結合先頭セルの span を記録
    merge_span = {m["top_left"]: m for m in d["merged"]}

    lines.append("## グリッド（空行・空列は省略。`[黄]`=塗り色, `**`=太字, `⟶n列結合` `⇣n行結合`）")
    lines.append("")
    header = "| 行 | " + " | ".join(get_column_letter(c) for c in cols) + " |"
    lines.append(header)
    lines.append("|" + "---|" * (len(cols) + 1))
    for r in rows:
        out = [str(r)]
        for c in cols:
            cell = cell_map.get((r, c))
            if not cell:
                out.append("")
                continue
            s = _short(cell.get("value"))
            if cell.get("formula"):
                s += f" `{_short(cell['formula'], 20)}`"
            if cell.get("bold"):
                s = f"**{s}**" if s else s
            if cell.get("strike"):
                s = f"~~{s}~~"
            tags = []
            if cell.get("fill"):
                tags.append(cell["fill"])
            if cell.get("font_color"):
                tags.append("文字" + cell["font_color"])
            m = merge_span.get(cell["coord"])
            if m:
                if m["cols"] > 1:
                    tags.append(f"⟶{m['cols']}列結合")
                if m["rows"] > 1:
                    tags.append(f"⇣{m['rows']}行結合")
            if cell.get("comment"):
                tags.append("💬" + _short(cell["comment"], 30))
            if tags:
                s += " [" + " ".join(tags) + "]"
            out.append(s)
        lines.append("| " + " | ".join(out) + " |")
    lines.append("")

    if d["regions"]:
        lines.append("## 罫線で囲まれた領域（表・枠の候補）")
        for i, rg in enumerate(d["regions"], 1):
            # 領域の先頭行のテキストを見出し候補として添える
            head = [c for c in d["cells"] if c.get("row") == rg["min_row"]
                    and rg["min_col"] <= c.get("col", 0) <= rg["max_col"] and c.get("value") is not None]
            head_txt = " / ".join(_short(c["value"], 15) for c in head[:6])
            lines.append(f"- 領域{i} `{rg['range']}` ({rg['cell_count']}セル) 先頭行: {head_txt}")
        lines.append("")
    return "\n".join(lines)


# ---------------------------------------------------------------- template guess

def guess_template(sheet_name, cells):
    text = sheet_name + " " + " ".join(str(c.get("value")) for c in cells[:200] if c.get("value") is not None)
    scores = {}
    for tpl, kws in TEMPLATE_HINTS.items():
        s = sum(text.count(k) for k in kws)
        if s:
            scores[tpl] = s
    if not scores:
        return "unknown", {}
    best = max(scores, key=scores.get)
    return best, scores


# ---------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("xlsx")
    ap.add_argument("outdir")
    ap.add_argument("--max-cells", type=int, default=20000)
    ap.add_argument("--sheets", help="カンマ区切りのシート名（指定時はそれだけ）")
    args = ap.parse_args()

    os.makedirs(args.outdir, exist_ok=True)
    wb = openpyxl.load_workbook(args.xlsx, data_only=False)
    try:
        wb_vals = openpyxl.load_workbook(args.xlsx, data_only=True)
    except Exception:
        wb_vals = None
    drawings = load_drawings(args.xlsx)

    wanted = set(s.strip() for s in args.sheets.split(",")) if args.sheets else None
    index = {"file": os.path.basename(args.xlsx), "sheets": []}
    for ws in wb.worksheets:
        if wanted and ws.title not in wanted:
            continue
        ws_vals = wb_vals[ws.title] if wb_vals is not None else None
        d = dump_sheet(ws, ws_vals, drawings.get(ws.title, []), args.max_cells)
        tpl, scores = guess_template(ws.title, d["cells"])
        d["template_guess"] = tpl
        d["template_scores"] = scores
        base = sanitize_name(ws.title)
        with open(os.path.join(args.outdir, base + ".json"), "w", encoding="utf-8") as f:
            json.dump(d, f, ensure_ascii=False, indent=1)
        with open(os.path.join(args.outdir, base + ".md"), "w", encoding="utf-8") as f:
            f.write(sheet_markdown(d))
        index["sheets"].append({
            "name": ws.title, "file_base": base, "state": ws.sheet_state,
            "dimension": d["dimension"], "value_cells": sum(1 for c in d["cells"] if "value" in c),
            "merged": len(d["merged"]), "regions": len(d["regions"]), "drawings": len(d["drawings"]),
            "comments": sum(1 for c in d["cells"] if c.get("comment")),
            "template_guess": tpl, "truncated": d["truncated"],
        })
    with open(os.path.join(args.outdir, "index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=1)
    print(json.dumps(index, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
