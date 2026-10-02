#!/usr/bin/env python3
"""Review gate: member order of each ported Java class equals upstream C# member order.

Usage: check_member_order.py [--wave N] [--status ported,partial] [--only TypeName]
                             [--verbose] [paths...]
Exit 1 iff any ORDER violation. Heuristic token-level extractor (see README).
"""
import argparse
import json
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
UPSTREAM = os.path.join(ROOT, "upstream", "kusto-query-language")

# ---------------------------------------------------------------- lexing

IDENT_START = re.compile(r"[A-Za-z_$]")


def preprocess_cs(src):
    """Resolve #if BRIDGE / #else / #endif keeping the !BRIDGE branch; drop other directives."""
    out = []
    stack = []  # (active_before, cond_value, in_else)
    active = True
    for line in src.split("\n"):
        s = line.strip()
        if s.startswith("#"):
            m = re.match(r"#\s*(if|elif|else|endif)\b(.*)", s)
            if m:
                d, rest = m.group(1), m.group(2).strip()
                if d == "if":
                    v = eval_cond(rest)
                    stack.append((active, v, False))
                    active = active and v
                elif d == "elif":
                    pa, v, _ = stack[-1]
                    v2 = eval_cond(rest)
                    stack[-1] = (pa, v or v2, False)
                    active = pa and v2 and not v
                elif d == "else":
                    pa, v, _ = stack[-1]
                    stack[-1] = (pa, v, True)
                    active = pa and not v
                elif d == "endif":
                    pa, v, _ = stack.pop()
                    active = pa
            out.append("")
            continue
        out.append(line if active else "")
    return "\n".join(out)


def eval_cond(c):
    c = c.split("//")[0].strip()
    if c == "BRIDGE":
        return False
    if c == "!BRIDGE":
        return True
    return True


def tokenize(src, lang):
    """Return list of tokens: identifiers, numbers ('0'), single punctuation, '=>' ('->' for java),
    strings as '"'. Comments dropped."""
    toks = []
    i, n = 0, len(src)
    cs = lang == "cs"

    def skip_interp_braces(i):
        # at '{' inside interpolated string; return index after matching '}'
        depth = 0
        while i < n:
            c = src[i]
            if c == '"':
                i = skip_string(i)
                continue
            if c == "{":
                depth += 1
            elif c == "}":
                depth -= 1
                if depth == 0:
                    return i + 1
            i += 1
        return i

    def skip_string(i, verbatim=False, interp=False):
        # src[i] == '"'
        if src.startswith('"""', i):
            q = 3
            while src.startswith('"', i + q):
                q += 1
            end = src.find('"' * q, i + q)
            return n if end < 0 else end + q
        i += 1
        while i < n:
            c = src[i]
            if verbatim:
                if c == '"':
                    if src.startswith('""', i):
                        i += 2
                        continue
                    return i + 1
            else:
                if c == "\\":
                    i += 2
                    continue
                if c == '"':
                    return i + 1
                if c == "\n" and not verbatim:
                    return i  # unterminated; bail
            if interp and c == "{":
                if src.startswith("{{", i):
                    i += 2
                    continue
                i = skip_interp_braces(i)
                continue
            i += 1
        return i

    while i < n:
        c = src[i]
        if c in " \t\r\n":
            i += 1
        elif src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
        elif c == '"':
            i = skip_string(i)
            toks.append('"')
        elif cs and c in "@$" and i + 1 < n and (src[i + 1] == '"' or (src[i + 1] in "@$" and src[i + 2:i + 3] == '"')):
            j = i
            verb = interp = False
            while src[j] in "@$":
                verb |= src[j] == "@"
                interp |= src[j] == "$"
                j += 1
            i = skip_string(j, verb, interp)
            toks.append('"')
        elif c == "'":
            j = i + 1
            while j < n and src[j] != "'":
                j += 2 if src[j] == "\\" else 1
            i = j + 1
            toks.append("'")
        elif IDENT_START.match(c) or (cs and c == "@" and i + 1 < n and IDENT_START.match(src[i + 1])):
            j = i + 1
            while j < n and (src[j].isalnum() or src[j] in "_$"):
                j += 1
            w = src[i:j]
            toks.append(w[1:] if w.startswith("@") and cs else w)
            i = j
        elif c.isdigit():
            j = i + 1
            while j < n and (src[j].isalnum() or src[j] in "._"):
                j += 1
            toks.append("0")
            i = j
        elif src.startswith("=>", i):
            toks.append("=>")
            i += 2
        elif src.startswith("->", i) and not cs:
            toks.append("=>")
            i += 2
        else:
            toks.append(c)
            i += 1
    return toks


# ---------------------------------------------------------------- body parsing

OPEN = {"(": ")", "[": "]", "{": "}"}
CLOSE = set(OPEN.values())
TYPE_KW = {"class", "struct", "enum", "interface", "record"}


def match_close(toks, i):
    """toks[i] is an opener; return index of matching closer."""
    d = 0
    n = len(toks)
    while i < n:
        t = toks[i]
        if t in OPEN:
            d += 1
        elif t in CLOSE:
            d -= 1
            if d == 0:
                return i
        i += 1
    return n - 1


def split_members(toks, lo, hi, lang):
    """Split toks[lo:hi] (a class body) into member token ranges (a, b, body_range or None)."""
    out = []
    i = lo
    while i < hi:
        if toks[i] == ";":
            i += 1
            continue
        start = i
        seen_eq = False
        angle = 0
        body = None
        j = i
        while j < hi:
            t = toks[j]
            if t in ("(", "["):
                j = match_close(toks, j) + 1
                continue
            if t == "{":
                e = match_close(toks, j)
                if seen_eq or angle < 0:
                    j = e + 1
                    continue
                body = (j + 1, e)
                j = e + 1
                # property initializer:  { get; } = x;
                if j < hi and toks[j] == "=":
                    seen_eq = True
                    continue
                break
            if t == ";":
                j += 1
                break
            if t in ("=", "=>"):
                seen_eq = True
            j += 1
        out.append((start, j, body))
        i = j
    return out


def strip_annotations(toks, lang):
    if lang == "cs":
        i = 0
        while i < len(toks) and toks[i] == "[":
            i = match_close(toks, i) + 1
        return toks[i:]
    out = []
    i = 0
    while i < len(toks):
        if toks[i] == "@" and i + 1 < len(toks) and toks[i + 1] != "interface":
            i += 2
            while i + 1 < len(toks) and toks[i] == "." and toks[i + 1].isidentifier():
                i += 2
            if i < len(toks) and toks[i] == "(":
                i = match_close(toks, i) + 1
            continue
        out.append(toks[i])
        i += 1
    return out


def last_ident(hdr):
    for t in reversed(hdr):
        if re.match(r"[A-Za-z_$]", t):
            return t
    return None


def header_prefix(hdr):
    """tokens before first '(' '{' '=' '=>' 'where' ':' at angle depth 0."""
    out = []
    angle = 0
    for t in hdr:
        if t == "<":
            angle += 1
        elif t == ">":
            angle -= 1
        elif angle == 0 and t in ("(", "{", "=", "=>", "where", ":", ";"):
            break
        out.append(t)
    return out


def cut_name_before_generic(hdr):
    """For method/ctor name: last ident before the first top-level '(' ."""
    cut = hdr.index("(")
    h = hdr[:cut]
    # drop trailing generic args  Name<T>
    if h and h[-1] == ">":
        d = 0
        k = len(h) - 1
        while k >= 0:
            if h[k] == ">":
                d += 1
            elif h[k] == "<":
                d -= 1
                if d == 0:
                    break
            k -= 1
        h = h[:k]
    return h


def parse_class_body(toks, lo, hi, cname, lang, prefix, members):
    """Append (kind, name) members in order."""
    for (a, b, body) in split_members(toks, lo, hi, lang):
        hdr_all = strip_annotations(toks[a:b], lang)
        if not hdr_all:
            continue
        # header = tokens up to body start
        if body is not None:
            # header excludes the body: recompute by slicing original tokens before '{'
            raw = toks[a:b]
            # find the body '{' index inside raw at depth 0
            k, d = 0, 0
            while k < len(raw):
                if raw[k] in ("(", "["):
                    k = match_close(raw, k)
                elif raw[k] == "{":
                    break
                k += 1
            hdr = strip_annotations(raw[:k], lang)
        else:
            hdr = hdr_all
        pre = header_prefix(hdr)
        words = set(pre)
        # --- initializer blocks (java) / no header
        if not hdr:
            if lang == "java":
                members.append(("m", prefix + "<init>")) if False else None
            continue
        if hdr[0] == "using" and lang == "cs":
            continue
        if lang == "cs" and "namespace" in pre:
            parse_class_body(toks, body[0], body[1], None, lang, prefix, members)
            continue
        if lang == "java" and hdr == ["static"] and body is not None:
            members.append(("m", prefix + "<cctor>"))
            continue
        tkw = [t for t in pre if t in TYPE_KW or t == "@" ]
        if "interface" in pre and "@" in pre:
            tkw = ["interface"]
        if "delegate" in pre and lang == "cs":
            cut = cut_name_before_generic(hdr)
            members.append(("t", prefix + (last_ident(cut) or "?")))
            continue
        if tkw:
            kw = tkw[0]
            idx = pre.index(kw)
            name = next((t for t in pre[idx + 1:] if IDENT_START.match(t)), "?")
            members.append(("t", prefix + name))
            if body is not None:
                if kw == "enum":
                    parse_enum(toks, body[0], body[1], name, lang, prefix + name + ".", members)
                else:
                    parse_class_body(toks, body[0], body[1], name, lang, prefix + name + ".", members)
            continue
        if lang == "cs" and "event" in pre:
            members.append(("f", prefix + (last_ident(pre) or "?")))
            continue
        # indexer
        if lang == "cs" and "this" in pre and "[" in hdr and "(" not in hdr[:hdr.index("[")]:
            members.append(("m", prefix + "this[]"))
            continue
        if "operator" in hdr and lang == "cs":
            k = hdr.index("operator")
            cut = hdr.index("(") if "(" in hdr else len(hdr)
            op = "".join(hdr[k + 1:cut])
            members.append(("m", prefix + "operator" + op))
            continue
        eq_at = min([hdr.index(x) for x in ("=", "=>") if x in hdr] or [10 ** 9])
        if "(" in hdr and hdr.index("(") < eq_at:
            h = cut_name_before_generic(hdr)
            if not h:
                continue
            name = h[-1]
            if len(h) >= 2 and h[-2] == "~":
                continue
            if name == cname and (lang == "java" or True) and not (len(h) >= 2 and h[-2] in (".",) and lang == "cs" and False):
                static = "static" in h
                members.append(("m", prefix + ("<cctor>" if static and lang == "cs" else "<ctor>")))
            else:
                # explicit interface impl  IFoo.Bar -> Bar (last ident already)
                members.append(("m", prefix + name))
            continue
        # property / field
        # split declarators for fields: int a, b = 1;
        angle = 0
        names = []
        cur = []
        stopped = False
        for t in hdr:
            if t == "<":
                angle += 1
            elif t == ">":
                angle -= 1
            if angle == 0 and t in ("=", "=>"):
                stopped = True
                break
            if angle == 0 and t == ",":
                names.append(last_ident(cur))
                cur = []
                continue
            cur.append(t)
        names.append(last_ident(cur))
        kind = "p" if (body is not None and lang == "cs") else "f"
        for nm in names:
            if nm and nm not in ("get", "set"):
                members.append((kind, prefix + nm))


def parse_enum(toks, lo, hi, name, lang, prefix, members):
    i = lo
    # java: constants until top-level ';'
    if lang == "java":
        j = lo
        while j < hi and toks[j] != ";":
            if toks[j] in OPEN:
                j = match_close(toks, j)
            j += 1
        const_hi = j
        rest = j + 1
    else:
        const_hi = hi
        rest = hi
    i = lo
    cur_start = i
    while i <= const_hi:
        if i == const_hi or toks[i] == ",":
            seg = strip_annotations(toks[cur_start:i], lang)
            if seg and IDENT_START.match(seg[0]):
                members.append(("e", prefix + seg[0]))
            cur_start = i + 1
            i += 1
            continue
        if toks[i] in OPEN:
            i = match_close(toks, i)
        i += 1
    if lang == "java" and rest < hi:
        parse_class_body(toks, rest, hi, name, lang, prefix, members)


# ---------------------------------------------------------------- file level

_cache = {}


def parse_file(path, lang):
    key = (path, lang)
    if key in _cache:
        return _cache[key]
    with open(path, encoding="utf-8-sig", errors="replace") as f:
        src = f.read()
    if lang == "cs":
        src = preprocess_cs(src)
    toks = tokenize(src, lang)
    # collect top-level + namespace-level types (and nested anywhere for java lookup)
    types = {}  # name -> (members list), first definition per name; also nested flat map

    def walk(lo, hi):
        for (a, b, body) in split_members(toks, lo, hi, lang):
            raw = toks[a:b]
            k = 0
            while k < len(raw):
                if raw[k] in ("(", "["):
                    k = match_close(raw, k)
                elif raw[k] == "{":
                    break
                k += 1
            hdr = strip_annotations(raw[:k], lang)
            pre = header_prefix(hdr)
            if lang == "cs" and "namespace" in pre and body is not None:
                walk(*body)
                continue
            tkw = [t for t in pre if t in TYPE_KW]
            if not tkw or body is None or "delegate" in pre:
                continue
            idx = pre.index(tkw[0])
            name = next((t for t in pre[idx + 1:] if IDENT_START.match(t)), None)
            if not name:
                continue
            members = []
            if tkw[0] == "enum":
                parse_enum(toks, body[0], body[1], name, lang, "", members)
            else:
                parse_class_body(toks, body[0], body[1], name, lang, "", members)
            types.setdefault(name, []).append(members)
            # java nested types resolvable by simple name too
            if lang == "java":
                walk_nested(body[0], body[1])

    def walk_nested(lo, hi):
        # register nested java types by simple name (for hoisting tolerance)
        for (a, b, body) in split_members(toks, lo, hi, lang):
            raw = toks[a:b]
            k = 0
            while k < len(raw):
                if raw[k] in ("(", "["):
                    k = match_close(raw, k)
                elif raw[k] == "{":
                    break
                k += 1
            hdr = strip_annotations(raw[:k], lang)
            pre = header_prefix(hdr)
            tkw = [t for t in pre if t in TYPE_KW]
            if tkw and body is not None:
                idx = pre.index(tkw[0])
                name = next((t for t in pre[idx + 1:] if IDENT_START.match(t)), None)
                if name:
                    members = []
                    if tkw[0] == "enum":
                        parse_enum(toks, body[0], body[1], name, lang, "", members)
                    else:
                        parse_class_body(toks, body[0], body[1], name, lang, "", members)
                    types.setdefault("::nested::" + name, []).append(members)
                    walk_nested(body[0], body[1])

    walk(0, len(toks))
    _cache[key] = types
    return types


# ---------------------------------------------------------------- normalisation

CS_SPECIAL = {"gethashcode": "hashcode", "equals": "equals", "tostring": "tostring"}


def norm_name(full, lang):
    """full is 'A.B.name' ; normalise each segment."""
    segs = full.split(".")
    out = []
    for idx, s in enumerate(segs):
        s = s.rstrip("_") or s
        lo = s.lower()
        if idx == len(segs) - 1 and lo in CS_SPECIAL and lang == "cs":
            lo = CS_SPECIAL[lo]
        out.append(lo)
    return ".".join(out)


def strip_type_digits(key, kinds_t):
    return key


def to_keys(members, lang):
    """members: list of (kind, name). Return list of (key, display)."""
    res = []
    for kind, name in members:
        k = norm_name(name, lang)
        if kind == "t":
            # nested type: strip arity suffix digits (Foo1 -> foo)
            k = re.sub(r"\d+$", "", k) or k
        res.append((k, name))
    return res


def collapse(lst):
    out = []
    for item in lst:
        if out and out[-1][0] == item[0]:
            continue
        out.append(item)
    return out


def prep(cs_members, java_members):
    cs = collapse(to_keys(cs_members, "cs"))
    jv = to_keys(java_members, "java")
    # fold setters:  foo, setFoo -> foo
    j2 = []
    for k, d in jv:
        last = k.split(".")
        base = last[-1]
        if base.startswith("set") and len(base) > 3 and j2:
            want = ".".join(last[:-1] + [base[3:]])
            if j2[-1][0] == want:
                continue
        j2.append((k, d))
    jv = collapse(j2)
    # overload-group renames: java key starting with a multi-overload C# key
    counts = defaultdict(int)
    for k, _ in to_keys(cs_members, "cs"):
        counts[k] += 1
    multi = sorted((k for k, c in counts.items() if c > 1), key=len, reverse=True)
    cs_keys = set(k for k, _ in cs)
    j3 = []
    for k, d in jv:
        if k not in cs_keys:
            seg = k.split(".")
            pfx = ".".join(seg[:-1])
            for m in multi:
                mseg = m.split(".")
                if ".".join(mseg[:-1]) == pfx and seg[-1].startswith(mseg[-1]) and seg[-1] != mseg[-1]:
                    k = m
                    break
        j3.append((k, d))
    jv = collapse(j3)
    return cs, jv


def lcs_pairs(a, b):
    """a,b lists of keys. Return list of (i,j) pairs of an LCS."""
    n, m = len(a), len(b)
    pre = 0
    while pre < n and pre < m and a[pre] == b[pre]:
        pre += 1
    suf = 0
    while suf < n - pre and suf < m - pre and a[n - 1 - suf] == b[m - 1 - suf]:
        suf += 1
    A, B = a[pre:n - suf], b[pre:m - suf]
    pairs = [(i, i) for i in range(pre)]
    N, M = len(A), len(B)
    if N and M:
        dp = [[0] * (M + 1) for _ in range(N + 1)]
        for i in range(N - 1, -1, -1):
            row, nxt, ai = dp[i], dp[i + 1], A[i]
            for j in range(M - 1, -1, -1):
                if ai == B[j]:
                    row[j] = nxt[j + 1] + 1
                else:
                    x, y = nxt[j], row[j + 1]
                    row[j] = x if x >= y else y
        i = j = 0
        while i < N and j < M:
            if A[i] == B[j]:
                pairs.append((pre + i, pre + j))
                i += 1
                j += 1
            elif dp[i + 1][j] >= dp[i][j + 1]:
                i += 1
            else:
                j += 1
    pairs += [(n - suf + k, m - suf + k) for k in range(suf)]
    return pairs


def compare(cs_members, java_members):
    cs, jv = prep(cs_members, java_members)
    ck = [k for k, _ in cs]
    jk = [k for k, _ in jv]
    pairs = lcs_pairs(ck, jk)
    mc = {i for i, _ in pairs}
    mj = {j for _, j in pairs}
    lc = [i for i in range(len(ck)) if i not in mc]
    lj = [j for j in range(len(jk)) if j not in mj]
    # leftover pairing by key -> out-of-order
    by = defaultdict(list)
    for i in lc:
        by[ck[i]].append(i)
    ooo = []  # (j, i)
    extra = []
    paired_c = set()
    for j in lj:
        if by.get(jk[j]):
            i = by[jk[j]].pop(0)
            paired_c.add(i)
            ooo.append((j, i))
        else:
            extra.append(j)
    missing = [i for i in lc if i not in paired_c]
    # description of order violations
    lcs_j = sorted(mj)
    cs_of_j = {j: i for i, j in pairs}
    viol = []
    for j, i in ooo:
        prev = [x for x in lcs_j if x < j]
        nxt = [x for x in lcs_j if x > j]
        ctx = ""
        # where upstream puts it relative to LCS neighbours
        if prev and i < cs_of_j[prev[-1]]:
            p = prev[-1]
            ctx = f"{jv[j][1]} appears after {jv[p][1]} (upstream order: {cs[i][1]} before {cs[cs_of_j[p]][1]})"
        elif nxt and i > cs_of_j[nxt[0]]:
            nx = nxt[0]
            ctx = f"{jv[j][1]} appears before {jv[nx][1]} (upstream order: {cs[i][1]} after {cs[cs_of_j[nx]][1]})"
        else:
            ctx = f"{jv[j][1]} appears out of sequence (upstream index {i}, java index {j})"
        viol.append(ctx)
    return cs, jv, viol, [cs[i][1] for i in missing], [jv[j][1] for j in extra]


# ---------------------------------------------------------------- driver


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--wave", type=int)
    ap.add_argument("--status", default="ported,partial")
    ap.add_argument("--only")
    ap.add_argument("--verbose", action="store_true")
    ap.add_argument("paths", nargs="*")
    a = ap.parse_args()
    statuses = set(a.status.split(","))
    man = json.load(open(os.path.join(ROOT, "porting", "manifest.json")))["entries"]

    def path_ok(e):
        if not a.paths:
            return True
        for p in a.paths:
            p = os.path.relpath(os.path.abspath(p), ROOT) if os.path.exists(p) else p
            if p in e["upstreamPath"] or any(p in jp for jp in e["javaPaths"]):
                return True
        return False

    # selected entries, but partial groups pull in all their parts
    selected = [e for e in man if e["status"] in statuses and e["upstreamPath"]
                and (a.wave is None or e.get("wave") == a.wave) and path_ok(e)]
    groups = defaultdict(list)  # (partialGroup or upstreamPath, typename) -> [(order, upstreamPath, type)]
    seen_entries = set()
    for e in selected:
        for t in e["types"]:
            if not t.get("javaPath") or t.get("stub"):
                continue
            if a.only and t["name"] != a.only:
                continue
            pg = t.get("partialGroup") or (e["partialGroup"] if isinstance(e.get("partialGroup"), str) else None)
            key = (pg, t["name"], t["javaPath"], t["javaName"]) if pg else (e["upstreamPath"], t["name"], t["javaPath"], t["javaName"])
            groups[key] = None
    # fill parts
    for key in list(groups):
        pg, tname, jpath, jname = key
        parts = []
        for e in man:
            if not e["upstreamPath"]:
                continue
            for t in e["types"]:
                if t["name"] != tname or t.get("javaPath") != jpath:
                    continue
                tg = t.get("partialGroup") or (e["partialGroup"] if isinstance(e.get("partialGroup"), str) else None)
                if (tg and tg == pg) or (not tg and e["upstreamPath"] == pg):
                    parts.append((t.get("mergeOrder", 0) or 0, e["upstreamPath"]))
        groups[key] = sorted(set(parts))

    n_classes = n_viol_classes = n_viol = n_missing = n_extra = 0
    problems = 0
    for key in sorted(groups, key=lambda k: (k[2], k[3])):
        pg, tname, jpath, jname = key
        cs_members = []
        found = False
        for _, up in groups[key]:
            fpath = os.path.join(UPSTREAM, up)
            if not os.path.exists(fpath):
                continue
            ts = parse_file(fpath, "cs")
            if tname in ts:
                found = True
                cs_members += ts[tname][0]
        jfile = os.path.join(ROOT, jpath)
        if not os.path.exists(jfile):
            print(f"SKIP {jpath}: java file missing")
            continue
        jt = parse_file(jfile, "java")
        jm = jt.get(jname) or jt.get("::nested::" + jname)
        if not found or not jm:
            print(f"SKIP {jpath}::{jname}: type not found ({'upstream' if not found else 'java'})")
            continue
        cs, jv, viol, missing, extra = compare(cs_members, jm[0])
        n_classes += 1
        n_missing += len(missing)
        n_extra += len(extra)
        label = f"{jpath}::{jname}"
        if viol:
            n_viol_classes += 1
            n_viol += len(viol)
            for v in viol:
                print(f"ORDER: {jpath}: {v}")
        else:
            print(f"OK {label}")
        if missing or extra:
            print(f"  warn: missing {len(missing)}, extra {len(extra)}")
            if True:
                if missing:
                    print("    missing: " + ", ".join(missing[:12]) + (" ..." if len(missing) > 12 else ""))
                if extra:
                    print("    extra:   " + ", ".join(extra[:12]) + (" ..." if len(extra) > 12 else ""))
        if a.verbose:
            print(f"  --- {label}: upstream (left) vs java (right)")
            for r in range(max(len(cs), len(jv))):
                l = cs[r][1] if r < len(cs) else ""
                rr = jv[r][1] if r < len(jv) else ""
                print(f"    {l:50} | {rr}")
    print(f"\nclasses checked: {n_classes}; classes with order violations: {n_viol_classes}; "
          f"order violations: {n_viol}; missing members: {n_missing}; extra members: {n_extra}")
    sys.exit(1 if n_viol else 0)


if __name__ == "__main__":
    main()
