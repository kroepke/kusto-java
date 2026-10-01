#!/usr/bin/env python3
"""Extract KQL strings from the Kusto.Language readme into corpus/readme.jsonl.

Sources (FunctionSymbol bodies are included as extra KQL fragments because the readme
has only four distinct queries): C# string literals passed as the first argument of KustoCode.Parse*(...) or assigned
to `query`/`var query` inside ```csharp fences, plus the body of any ```kusto fence.
Handles regular "..." escapes and verbatim @"..." (with "" for a quote).
"""
import json
import subprocess, re, sys, datetime
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
from corpuslib import ROOT, CORPUS, SCHEMAS, write_jsonl, update_provenance

README = ROOT / "upstream/kusto-query-language/src/Kusto.Language/readme.md"
LIT = r'(@"(?:[^"]|"")*"|"(?:[^"\\\n]|\\.)*")'
PAT = re.compile(r'KustoCode\.Parse\w*\(\s*' + LIT + r'|\bquery\s*=\s*' + LIT
                 + r'|new FunctionSymbol\(\s*"[^"]*"\s*,\s*(?:"[^"]*"\s*,\s*)?' + LIT + r'\s*\)')
SIMPLE = {'n': '\n', 'r': '\r', 't': '\t', '0': '\0', '\\': '\\', '"': '"', "'": "'",
          'a': '\a', 'b': '\b', 'f': '\f', 'v': '\v'}


def unescape(lit):
    if lit.startswith('@'):
        return lit[2:-1].replace('""', '"')
    s, out, i = lit[1:-1], [], 0
    while i < len(s):
        c = s[i]
        if c != '\\':
            out.append(c); i += 1; continue
        n = s[i + 1]
        if n in SIMPLE:
            out.append(SIMPLE[n]); i += 2
        elif n == 'u':
            out.append(chr(int(s[i + 2:i + 6], 16))); i += 6
        elif n == 'x':
            m = re.match(r'[0-9a-fA-F]{1,4}', s[i + 2:])
            out.append(chr(int(m.group(0), 16))); i += 2 + len(m.group(0))
        else:
            raise ValueError("bad escape " + lit)
    return ''.join(out)


def main():
    schema = json.loads((SCHEMAS / "readme-v1.json").read_text(encoding="utf-8"))
    tables = {t["name"] for t in schema["tables"]} | {f["name"] for f in schema["functions"]}
    ident = re.compile(r'(?<![\w.])(' + '|'.join(map(re.escape, sorted(tables))) + r')(?!\w)')
    lines = README.read_text(encoding="utf-8").split('\n')
    found, fence, start = [], None, 0
    for n, line in enumerate(lines, 1):
        m = re.match(r'\s*```(\w*)', line)
        if m and fence is None:
            fence, start, body = m.group(1).lower(), n, []
            continue
        if m and fence is not None:
            text = '\n'.join(body)
            if fence == 'kusto':
                found.append((start + 1, text))
            elif fence in ('csharp', 'chsarp'):
                for mm in PAT.finditer(text):
                    lit = mm.group(1) or mm.group(2) or mm.group(3)
                    off = text.count('\n', 0, mm.start())
                    found.append((start + 1 + off, unescape(lit)))
            fence = None
            continue
        if fence is not None:
            body.append(line)
    seen, recs = set(), []
    for ln, q in found:
        if q in seen:
            continue
        seen.add(q)
        recs.append({"id": f"readme/{len(recs)+1:04d}", "text": q,
                     "schema": "readme-v1" if ident.search(q) else None,
                     "source": f"readme.md:{ln}"})
    write_jsonl(CORPUS / "readme.jsonl", recs)
    update_provenance("readme", {
        "repo": "microsoft/Kusto-Query-Language (upstream submodule)",
        "path": "src/Kusto.Language/readme.md", "commit": subprocess.check_output(["git","-C",str(ROOT/"upstream/kusto-query-language"),"rev-parse","HEAD"],text=True).strip(), "license": "Apache-2.0",
        "count": len(recs), "extractionScript": "porting/tools/extract_readme_corpus.py",
        "extractedAt": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")})
    print(f"readme: {len(recs)} records")


if __name__ == "__main__":
    main()
