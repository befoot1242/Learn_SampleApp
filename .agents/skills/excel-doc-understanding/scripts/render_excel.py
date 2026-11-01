#!/usr/bin/env python3
"""
Excel の各シートを「人間が見るのと同じ見た目」の PNG に描画する。

ポイント:
  - openpyxl で保存すると図形・矢印が消えるので、xlsx(zip) 内の XML を直接書き換える
  - 各シートについて「他シートを非表示にしたコピー」を作り、1シート=1PDF にする
    （LibreOffice は非表示シートを印刷しないので、ページ→シートの対応が確実になる）
  - 横1ページに収める (fitToWidth=1)、行番号/列記号を印字する (headings=1) ので、
    セルダンプの座標と画像を突き合わせられる

出力（<outdir>/ 配下）:
  <sheet>.pdf, <sheet>-<page>.png, render_index.json

使い方:
  python render_excel.py <input.xlsx> <outdir> [--dpi 110] [--sheets "A,B"] [--no-fit]
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
import xml.etree.ElementTree as ET

M = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
REL = "http://schemas.openxmlformats.org/package/2006/relationships"
ET.register_namespace("", M)
ET.register_namespace("r", R)
# 他の名前空間も壊さないよう、よく出るものを登録しておく
for p, u in {
    "mc": "http://schemas.openxmlformats.org/markup-compatibility/2006",
    "x14ac": "http://schemas.microsoft.com/office/spreadsheetml/2009/9/ac",
    "xr": "http://schemas.microsoft.com/office/spreadsheetml/2014/revision",
    "xr2": "http://schemas.microsoft.com/office/spreadsheetml/2015/revision2",
    "xr3": "http://schemas.microsoft.com/office/spreadsheetml/2016/revision3",
    "x14": "http://schemas.microsoft.com/office/spreadsheetml/2009/9/main",
    "xdr": "http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing",
    "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
}.items():
    ET.register_namespace(p, u)

# CT_Worksheet の子要素順。pageSetup を正しい位置に挿入するために使う
WS_ORDER = ["sheetPr", "dimension", "sheetViews", "sheetFormatPr", "cols", "sheetData",
            "sheetCalcPr", "sheetProtection", "protectedRanges", "scenarios", "autoFilter",
            "sortState", "dataConsolidate", "customSheetViews", "mergeCells", "phoneticPr",
            "conditionalFormatting", "dataValidations", "hyperlinks", "printOptions",
            "pageMargins", "pageSetup", "headerFooter", "rowBreaks", "colBreaks",
            "customProperties", "cellWatches", "ignoredErrors", "smartTags", "drawing",
            "legacyDrawing", "legacyDrawingHF", "drawingHF", "picture", "oleObjects",
            "controls", "webPublishItems", "tableParts", "extLst"]


def q(tag):
    return f"{{{M}}}{tag}"


def local(tag):
    return tag.split("}")[-1]


def insert_ordered(root, el):
    """CT_Worksheet の順序を守って el を挿入（同名要素があれば置換）。"""
    name = local(el.tag)
    for i, child in enumerate(list(root)):
        if local(child.tag) == name:
            root.remove(child)
            root.insert(i, el)
            return
    idx = WS_ORDER.index(name)
    later = set(WS_ORDER[idx + 1:])
    for i, child in enumerate(list(root)):
        if local(child.tag) in later:
            root.insert(i, el)
            return
    root.append(el)


def patch_sheet_xml(xml_bytes, fit):
    root = ET.fromstring(xml_bytes)
    # 行/列見出しを印字
    po = root.find(q("printOptions"))
    if po is None:
        po = ET.Element(q("printOptions"))
    po.set("headings", "1")
    po.set("gridLines", "0")
    insert_ordered(root, po)
    if fit:
        sp = root.find(q("sheetPr"))
        if sp is None:
            sp = ET.Element(q("sheetPr"))
            root.insert(0, sp)
        pup = sp.find(q("pageSetUpPr"))
        if pup is None:
            pup = ET.SubElement(sp, q("pageSetUpPr"))
        pup.set("fitToPage", "1")
        ps = root.find(q("pageSetup"))
        if ps is None:
            ps = ET.Element(q("pageSetup"))
        ps.set("fitToWidth", "1")
        ps.set("fitToHeight", "0")
        ps.set("orientation", "landscape")
        for k in ("scale",):
            ps.attrib.pop(k, None)
        insert_ordered(root, ps)
    return ET.tostring(root, xml_declaration=True, encoding="UTF-8")


def patch_workbook_xml(xml_bytes, visible_sheet):
    root = ET.fromstring(xml_bytes)
    sheets = root.find(q("sheets"))
    for i, s in enumerate(sheets.findall(q("sheet"))):
        if s.get("name") == visible_sheet:
            s.attrib.pop("state", None)
        else:
            s.set("state", "hidden")
    # activeTab を対象シートに
    bv = root.find(q("bookViews"))
    if bv is not None:
        names = [s.get("name") for s in sheets.findall(q("sheet"))]
        for wv in bv.findall(q("workbookView")):
            if visible_sheet in names:
                wv.set("activeTab", str(names.index(visible_sheet)))
            wv.attrib.pop("firstSheet", None)
    return ET.tostring(root, xml_declaration=True, encoding="UTF-8")


def sheet_xml_paths(z):
    wb = ET.fromstring(z.read("xl/workbook.xml"))
    rels = ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))
    rid = {r.get("Id"): r.get("Target") for r in rels.findall(f"{{{REL}}}Relationship")}
    out = {}
    for s in wb.find(q("sheets")).findall(q("sheet")):
        t = rid.get(s.get(f"{{{R}}}id"), "").lstrip("/")
        if not t.startswith("xl/"):
            t = "xl/" + t
        out[s.get("name")] = (t, s.get("state", "visible"))
    return out


def make_single_sheet_copy(src, dst, sheet_name, fit):
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
        paths = sheet_xml_paths(zin)
        target_path = paths[sheet_name][0]
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename == "xl/workbook.xml":
                data = patch_workbook_xml(data, sheet_name)
            elif item.filename == target_path:
                try:
                    data = patch_sheet_xml(data, fit)
                except Exception as e:  # 壊すくらいなら元のまま
                    print(f"  [warn] sheet xml patch failed for {sheet_name}: {e}", file=sys.stderr)
            zout.writestr(item, data)


def sanitize_name(name):
    return re.sub(r'[\\/:*?"<>|\s]+', "_", name).strip("_") or "sheet"


def soffice_pdf(xlsx, outdir):
    env = dict(os.environ)
    env.setdefault("HOME", tempfile.gettempdir())
    cmd = ["soffice", "--headless", "--norestore", "--convert-to", "pdf", "--outdir", outdir, xlsx]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=300, env=env)
    pdf = os.path.join(outdir, os.path.splitext(os.path.basename(xlsx))[0] + ".pdf")
    if not os.path.exists(pdf):
        raise RuntimeError(f"soffice failed: {r.stdout}\n{r.stderr}")
    return pdf


def pdf_to_png(pdf, prefix, dpi):
    subprocess.run(["pdftoppm", "-r", str(dpi), "-png", pdf, prefix], check=True, timeout=300)
    d = os.path.dirname(prefix)
    base = os.path.basename(prefix)
    pngs = sorted(f for f in os.listdir(d) if f.startswith(base + "-") and f.endswith(".png"))
    return [os.path.join(d, f) for f in pngs]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("xlsx")
    ap.add_argument("outdir")
    ap.add_argument("--dpi", type=int, default=110)
    ap.add_argument("--sheets")
    ap.add_argument("--no-fit", action="store_true", help="横1ページ収めを行わない（元の改ページ設定で印刷）")
    ap.add_argument("--include-hidden", action="store_true")
    args = ap.parse_args()
    os.makedirs(args.outdir, exist_ok=True)
    wanted = set(s.strip() for s in args.sheets.split(",")) if args.sheets else None

    with zipfile.ZipFile(args.xlsx) as z:
        paths = sheet_xml_paths(z)
    index = {"file": os.path.basename(args.xlsx), "dpi": args.dpi, "sheets": []}
    tmp = tempfile.mkdtemp(prefix="xlsrender_")
    try:
        for name, (_, state) in paths.items():
            if wanted and name not in wanted:
                continue
            if state != "visible" and not args.include_hidden:
                index["sheets"].append({"name": name, "skipped": "hidden"})
                continue
            base = sanitize_name(name)
            copy = os.path.join(tmp, base + ".xlsx")
            make_single_sheet_copy(args.xlsx, copy, name, fit=not args.no_fit)
            try:
                pdf = soffice_pdf(copy, tmp)
            except Exception as e:
                index["sheets"].append({"name": name, "error": str(e)[:500]})
                continue
            dst_pdf = os.path.join(args.outdir, base + ".pdf")
            shutil.move(pdf, dst_pdf)
            pngs = pdf_to_png(dst_pdf, os.path.join(args.outdir, base), args.dpi)
            index["sheets"].append({"name": name, "file_base": base, "pdf": dst_pdf,
                                    "pages": [os.path.basename(p) for p in pngs]})
            print(f"{name}: {len(pngs)} page(s)")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    with open(os.path.join(args.outdir, "render_index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
