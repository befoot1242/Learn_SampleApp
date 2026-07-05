"""Excelファイル(.xlsx/.xlsm/.xls)をPDFに変換する。AIにExcel設計書を読ませる前処理用。

Microsoft Excel のCOM操作(ExportAsFixedFormat)でPDF化するため、
シートごとの印刷設定(印刷範囲・用紙・向き)がそのまま反映される。

必要環境: Windows + Microsoft Excel + pywin32 (pip install pywin32)

Excelが無いマシンでは LibreOffice で代替可能:
    soffice --headless --convert-to pdf --outdir <出力先> <入力.xlsx>

使い方:
    python convert_excel_to_pdf.py "C:\\docs\\設計書.xlsx"
    python convert_excel_to_pdf.py "C:\\docs" --out-dir "C:\\docs\\pdf"
    python convert_excel_to_pdf.py "C:\\docs\\設計書.xlsx" --per-sheet
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

import pythoncom
import win32com.client

EXCEL_EXTENSIONS = {".xlsx", ".xlsm", ".xls"}
XL_TYPE_PDF = 0
XL_SHEET_VISIBLE = -1


def sanitize(name: str) -> str:
    """シート名をファイル名に使えない文字を置換して返す。"""
    return re.sub(r'[\\/:*?"<>|]', "_", name).strip()


def collect_targets(path: Path) -> list[Path]:
    if path.is_dir():
        targets = sorted(
            p for p in path.iterdir()
            if p.suffix.lower() in EXCEL_EXTENSIONS and not p.name.startswith("~$")
        )
        if not targets:
            sys.exit(f"変換対象のExcelファイルが見つかりません: {path}")
        return targets
    if not path.exists():
        sys.exit(f"ファイルが存在しません: {path}")
    return [path]


def export_workbook(excel, file: Path, out_dir: Path, per_sheet: bool) -> None:
    print(f"変換中: {file.name}")
    # UpdateLinks=0(外部リンク更新しない), ReadOnly=True(元ファイルを変更しない)
    workbook = excel.Workbooks.Open(str(file), 0, True)
    try:
        base_name = file.stem
        if per_sheet:
            for index, sheet in enumerate(workbook.Worksheets, start=1):
                if sheet.Visible != XL_SHEET_VISIBLE:
                    print(f"  スキップ(非表示): {sheet.Name}")
                    continue
                out_path = out_dir / f"{base_name}_{index:02d}_{sanitize(sheet.Name)}.pdf"
                sheet.ExportAsFixedFormat(XL_TYPE_PDF, str(out_path))
                print(f"  -> {out_path}")
        else:
            out_path = out_dir / f"{base_name}.pdf"
            workbook.ExportAsFixedFormat(XL_TYPE_PDF, str(out_path))
            print(f"  -> {out_path}")
    finally:
        workbook.Close(False)


def main() -> None:
    # リダイレクト/パイプ経由でも日本語が化けないようUTF-8に固定
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(
        description="ExcelファイルをPDFに変換する(Excel COM使用)",
    )
    parser.add_argument("path", help="変換対象のExcelファイル、またはフォルダ")
    parser.add_argument(
        "--out-dir", "-o",
        help="出力先フォルダ(省略時は入力ファイルと同じ場所)",
    )
    parser.add_argument(
        "--per-sheet", "-s", action="store_true",
        help="1シート=1PDFで出力する(省略時はブック全体を1PDF)",
    )
    args = parser.parse_args()

    targets = collect_targets(Path(args.path))

    pythoncom.CoInitialize()
    excel = None
    try:
        excel = win32com.client.DispatchEx("Excel.Application")
        excel.Visible = False
        excel.DisplayAlerts = False
        excel.AskToUpdateLinks = False

        for file in targets:
            if args.out_dir:
                out_dir = Path(args.out_dir)
                out_dir.mkdir(parents=True, exist_ok=True)
            else:
                out_dir = file.parent
            export_workbook(excel, file, out_dir, args.per_sheet)
    finally:
        if excel is not None:
            excel.Quit()
        pythoncom.CoUninitialize()

    print("完了")


if __name__ == "__main__":
    main()
