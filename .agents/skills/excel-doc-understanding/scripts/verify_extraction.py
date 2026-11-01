#!/usr/bin/env python3
"""
LLM が作った構造化 JSON の各値が、元の Excel に本当に存在するかを機械的に突き合わせる。

画像読解では ID・桁数・型名などを「それらしく」誤読しやすい。
ここでは構造化 JSON のすべての葉（文字列/数値）について、
ダンプ(JSON)内のセル値・図形テキスト・コメントのどこかに出現するかを調べ、
出現しないものを「要確認」として列挙する。

使い方:
  python verify_extraction.py <structured.json> <dump_dir> [--sheet NAME] [--min-len 2]
終了コード: 0=すべて確認済み, 1=要確認あり
"""
import argparse
import glob
import json
import os
import re
import sys
import unicodedata

# 構造上の語（スキーマのキー/固定語）で、元文書に存在しなくてよいもの
# 自由記述・メタ情報のトップレベルキー（元文書に無くてよい）
SKIP_TOP = {"_meta", "unresolved", "source_file", "hidden_notes_summary"}
# セル座標・範囲（A1, B2:F10）は構造化側が付与する参照なので検証しない
CELL_REF = re.compile(r"^[A-Za-z]{1,3}\d+(:[A-Za-z]{1,3}\d+)?$")
IGNORE_VALUES = {"true", "false", "null", "yes", "no", "string", "number", "integer", "boolean",
                 "date", "datetime", "array", "object", "必須", "任意", "high", "medium", "low"}


def norm(s):
    """全角/半角・空白・大小を無視した比較用に正規化。"""
    s = unicodedata.normalize("NFKC", str(s))
    s = re.sub(r"\s+", "", s)
    return s.lower()


def corpus_from_dump(dump_dir, sheet=None):
    texts = []
    for p in glob.glob(os.path.join(dump_dir, "*.json")):
        if os.path.basename(p) == "index.json":
            continue
        with open(p, encoding="utf-8") as f:
            d = json.load(f)
        if sheet and d.get("sheet") != sheet:
            continue
        for c in d.get("cells", []):
            if c.get("value") is not None:
                texts.append(str(c["value"]))
            if c.get("comment"):
                texts.append(c["comment"])
            if c.get("comment_author"):
                texts.append(c["comment_author"])
        for s in d.get("drawings", []):
            if s.get("text"):
                texts.append(s["text"])
            if s.get("name"):
                texts.append(s["name"])
        texts.append(d.get("sheet", ""))
    joined = "\n".join(texts)
    return set(norm(t) for t in texts), norm(joined)


def leaves(obj, path=""):
    if isinstance(obj, dict):
        for k, v in obj.items():
            yield from leaves(v, f"{path}.{k}" if path else k)
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            yield from leaves(v, f"{path}[{i}]")
    else:
        yield path, obj


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("structured")
    ap.add_argument("dump_dir")
    ap.add_argument("--sheet")
    ap.add_argument("--min-len", type=int, default=2)
    ap.add_argument("--skip-keys", default="confidence,notes,source,summary,_meta,document_type,template,sheet,source_range,source_cells,unresolved",
                    help="LLMが自由記述する想定のキー（カンマ区切り）。検証対象から外す")
    args = ap.parse_args()

    with open(args.structured, encoding="utf-8") as f:
        data = json.load(f)
    exact, joined = corpus_from_dump(args.dump_dir, args.sheet)
    skip = set(k.strip() for k in args.skip_keys.split(",") if k.strip())

    checked = 0
    unverified = []
    for path, v in leaves(data):
        key = re.split(r"[.\[]", path)[-1] if "." in path or "[" in path else path
        last_key = path.split(".")[-1].split("[")[0]
        top_key = path.split(".")[0].split("[")[0]
        if last_key in skip or top_key in SKIP_TOP or v is None or isinstance(v, bool):
            continue
        s = str(v)
        if isinstance(v, float) and v.is_integer():
            s = str(int(v))
        if len(s) < args.min_len or s.lower() in IGNORE_VALUES or CELL_REF.match(s):
            continue
        n = norm(s)
        checked += 1
        if n in exact or n in joined:
            continue
        # 長い文は部分一致で許容（文を要約した場合など）: 先頭/末尾10文字が出現すれば弱い一致
        if len(n) >= 20 and (n[:10] in joined and n[-10:] in joined):
            continue
        unverified.append({"path": path, "value": s})

    report = {"checked": checked, "unverified_count": len(unverified), "unverified": unverified}
    print(json.dumps(report, ensure_ascii=False, indent=1))
    sys.exit(1 if unverified else 0)


if __name__ == "__main__":
    main()
