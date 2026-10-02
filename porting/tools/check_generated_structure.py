#!/usr/bin/env python3
# Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
# Copyright (c) 2026 Graylog, Inc. Purpose: structural diff of generated syntax nodes, C# reference vs Java (PORTING.md 4.5).
"""Compare the structure of the generated syntax nodes against the C# reference.

Extracts, from porting/reference/GeneratedSyntaxNodes.cs and from the generated Java files under
kusto-language/src/main/java/org/graylog/kusto/language/syntax/, one tuple per node:

    (name, base, abstract/sealed, kind, [(childName, optional, completionHint)...],
     [(paramType, paramName)...] of the full constructor)

plus the Visit* method order of the four visitor classes, and diffs them. C# types are mapped to
their Java spelling first (PORTING.md 2.4, 3.17: SyntaxList<T> -> SyntaxList1<T>,
SeparatedElement<T> -> SeparatedElement1<T>, string -> String, IReadOnlyList<T> -> List<T>,
the [Flags] enums CompletionHint and SymbolMatch -> int); "@operator" is "operator".
Hand-written region contents are ignored.

Usage: python3 porting/tools/check_generated_structure.py [--reference X.cs] [--java DIR]
Exit status 1 on any difference. No third-party dependencies.
"""
import os
import re
import sys

REPO = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
REFERENCE = os.path.join(REPO, "porting", "reference", "GeneratedSyntaxNodes.cs")
JAVA_DIR = os.path.join(REPO, "kusto-language", "src", "main", "java", "org", "graylog", "kusto", "language", "syntax")
TEMPLATE = "// Ported from: src/Kusto.Language/Syntax/CodeGen/GeneratedSyntaxNodes.tt ("
VISITORS = ["SyntaxVisitor", "DefaultSyntaxVisitor", "SyntaxVisitor1", "DefaultSyntaxVisitor1"]


def split_params(text):
    """Split a parameter list at top-level commas (generic arguments may nest)."""
    out, depth, cur = [], 0, ""
    for ch in text:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur.strip())
            cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur.strip())
    return out


def java_type(cs):
    cs = cs.strip()
    if cs in ("CompletionHint", "Kusto.Language.Symbols.SymbolMatch"):
        return "int"
    cs = re.sub(r"\bIReadOnlyList<", "List<", cs)
    cs = re.sub(r"\bstring\b", "String", cs)
    cs = re.sub(r"\b(SyntaxList|SeparatedElement)<", r"\g<1>1<", cs)
    return cs


def params_of(text, csharp):
    out = []
    for p in split_params(text):
        if csharp:
            p = p.split(" = ")[0]
        typ, name = p.rsplit(" ", 1)
        out.append((java_type(typ) if csharp else typ, name.lstrip("@")))
    return out


def make_node(name, base, modifier):
    return {"name": name, "base": base, "modifier": modifier, "kind": None, "children": [],
            "optional": set(), "hints": {}, "params": None}


def finish(node):
    children = [(c, i in node["optional"], node["hints"].get(i)) for i, c in enumerate(node["children"])]
    return (node["name"], node["base"], node["modifier"], node["kind"], tuple(children), tuple(node["params"] or ()))


# ---------------------------------------------------------------------------------------------- C#

CS_DECL = re.compile(r"public (?:(abstract|sealed) )?partial class (\w+) : (\w+)$")
CS_KIND_CONST = re.compile(r"public override SyntaxKind Kind => SyntaxKind\.(\w+);$")
CS_CTOR = re.compile(r"internal (\w+)\((.*)\) : base\((.*)\)$")
CS_METHOD = re.compile(r"(public|protected) override \S+ (\w+)")
CS_CASE_NAME = re.compile(r"case (\d+): return nameof\((\w+)\);$")
CS_CASE_LABEL = re.compile(r"case (\d+):$")
CS_CASE_HINT = re.compile(r"case (\d+): return CompletionHint\.(\w+);$")
CS_VISITOR_CLASS = re.compile(r"public partial class (\w+)(<TResult>)?(?: : .*)?$")
CS_VISIT = re.compile(r"public (?:abstract|override) (?:void|TResult) Visit(\w+)\(")


def parse_csharp(path):
    with open(path, encoding="utf-8-sig") as f:
        lines = [l.strip() for l in f.read().split("\n")]
    nodes, visitors = {}, {}
    order = []
    node, method, visitor, in_class = None, None, None, False
    for line in lines:
        m = re.match(r"#region class (\w+)$", line)
        if m:
            node, method, in_class = None, None, True
            continue
        if line.startswith("#endregion /* class "):
            if node:
                nodes[node["name"]] = finish(node)
                order.append(node["name"])
            node, in_class = None, False
            continue
        m = CS_DECL.match(line)
        if m and in_class:
            node = make_node(m.group(2), m.group(3), m.group(1) or "")
            continue
        m = CS_VISITOR_CLASS.match(line)
        if m and not in_class:
            visitor = m.group(1) + ("1" if m.group(2) else "")
            visitors[visitor] = []
            continue
        if visitor and node is None:
            m = CS_VISIT.match(line)
            if m:
                visitors[visitor].append(m.group(1))
            continue
        if node is None:
            continue
        if line == "public override SyntaxKind Kind => this.kind;":
            node["kind"] = "<field>"
            continue
        m = CS_KIND_CONST.match(line)
        if m:
            node["kind"] = m.group(1)
            continue
        m = CS_CTOR.match(line)
        if m:
            node["params"] = params_of(m.group(2), True)
            continue
        m = CS_METHOD.match(line)
        if m:
            method = m.group(2)
            continue
        if method == "GetName":
            m = CS_CASE_NAME.match(line)
            if m:
                assert int(m.group(1)) == len(node["children"]), (node["name"], line)
                node["children"].append(m.group(2))
        elif method == "IsOptional":
            m = CS_CASE_LABEL.match(line)
            if m:
                node["optional"].add(int(m.group(1)))
        elif method == "GetCompletionHintCore":
            m = CS_CASE_HINT.match(line)
            if m:
                node["hints"][int(m.group(1))] = m.group(2)
    return nodes, order, visitors


# -------------------------------------------------------------------------------------------- Java

J_DECL = re.compile(r"public (?:(abstract|final) )?class (\w+)(?:<TResult>)? extends (\w+)(?:<TResult>)? \{$")
J_VISITOR_DECL = re.compile(r"public abstract class (\w+)(?:<TResult>)?(?: extends \w+(?:<TResult>)?)? \{$")
J_KIND_CONST = re.compile(r"@Override public SyntaxKind kind\(\) \{ return SyntaxKind\.(\w+); \}$")
J_KIND_FIELD = "@Override public SyntaxKind kind() { return this.kind; }"
J_VISIT = re.compile(r"(?:public abstract|@Override public) (?:void|TResult) visit(\w+)\(")


def strip_regions(text):
    out, inside = [], False
    for line in text.split("\n"):
        s = line.strip()
        if s.startswith("// <hand-written"):
            inside = True
            continue
        if s.startswith("// </hand-written"):
            inside = False
            continue
        if not inside:
            out.append(s)
    return out


def parse_java(directory):
    nodes, visitors = {}, {}
    if not os.path.isdir(directory):
        return nodes, visitors
    for fn in sorted(os.listdir(directory)):
        if not fn.endswith(".java"):
            continue
        with open(os.path.join(directory, fn), encoding="utf-8-sig") as f:
            text = f.read()
        if not text.startswith(TEMPLATE):
            continue
        what = text[len(TEMPLATE):text.index("\n")]
        lines = strip_regions(text)
        if what.startswith("visitor "):
            name = what[len("visitor "):-1]
            visitors[name] = [m.group(1) for m in (J_VISIT.match(l) for l in lines) if m]
            continue
        node = None
        for line in lines:
            m = J_DECL.match(line)
            if m and node is None:
                mod = {"abstract": "abstract", "final": "sealed"}.get(m.group(1), "")
                node = make_node(m.group(2), m.group(3), mod)
                continue
            if node is None:
                continue
            if line == J_KIND_FIELD:
                node["kind"] = "<field>"
            elif J_KIND_CONST.match(line):
                node["kind"] = J_KIND_CONST.match(line).group(1)
            elif node["params"] is None and line.startswith("public " + node["name"] + "(") and line.endswith(") {"):
                node["params"] = params_of(line[len("public " + node["name"] + "("):-len(") {")], False)
            elif line.startswith("@Override public String getName(int index)"):
                for i, (idx, child) in enumerate(re.findall(r'case (\d+): return "(\w+)";', line)):
                    assert int(idx) == i, (fn, line)
                    node["children"].append(child)
            elif line.startswith("@Override public boolean isOptional(int index)"):
                node["optional"] = {int(i) for i in re.findall(r"case (\d+):", line)}
            elif line.startswith("@Override protected int getCompletionHintCore(int index)"):
                node["hints"] = {int(i): h for i, h in re.findall(r"case (\d+): return CompletionHint\.(\w+);", line)}
        if node is None:
            raise SystemExit("check_generated_structure: %s: no class declaration" % fn)
        if node["name"] + ".java" != fn:
            raise SystemExit("check_generated_structure: %s declares %s" % (fn, node["name"]))
        nodes[node["name"]] = finish(node)
    return nodes, visitors


def fmt(t):
    name, base, mod, kind, children, params = t
    return ("%s : %s [%s] kind=%s\n      children=%s\n      ctor=%s"
            % (name, base, mod or "-", kind, list(children), ["%s %s" % p for p in params]))


def main(argv):
    ref, jdir = REFERENCE, JAVA_DIR
    args = argv[1:]
    while args:
        flag = args.pop(0)
        if flag == "--reference" and args:
            ref = args.pop(0)
        elif flag == "--java" and args:
            jdir = args.pop(0)
        else:
            print(__doc__, file=sys.stderr)
            return 2
    cs_nodes, cs_order, cs_visitors = parse_csharp(ref)
    j_nodes, j_visitors = parse_java(jdir)

    problems = []
    for name in cs_order:
        if name not in j_nodes:
            problems.append("missing Java node: %s" % name)
        elif cs_nodes[name] != j_nodes[name]:
            problems.append("node %s differs:\n  C#:   %s\n  Java: %s" % (name, fmt(cs_nodes[name]), fmt(j_nodes[name])))
    for name in sorted(set(j_nodes) - set(cs_nodes)):
        problems.append("extra Java node: %s" % name)
    for v in VISITORS:
        if v not in j_visitors:
            problems.append("missing Java visitor: %s" % v)
        elif cs_visitors.get(v) != j_visitors[v]:
            cs_v, j_v = cs_visitors.get(v) or [], j_visitors[v]
            first = next((i for i in range(min(len(cs_v), len(j_v))) if cs_v[i] != j_v[i]), min(len(cs_v), len(j_v)))
            problems.append("visitor %s differs: C# %d methods, Java %d; first difference at index %d"
                            % (v, len(cs_v), len(j_v), first))

    cs_abstract = sum(1 for n in cs_nodes.values() if n[2] == "abstract")
    print("C# reference: %d nodes (%d abstract), %d visitors (%s Visit methods)"
          % (len(cs_nodes), cs_abstract, len(cs_visitors), "/".join(str(len(cs_visitors[v])) for v in VISITORS if v in cs_visitors)))
    j_abstract = sum(1 for n in j_nodes.values() if n[2] == "abstract")
    print("Java:         %d nodes (%d abstract), %d visitors (%s visit methods)"
          % (len(j_nodes), j_abstract, len(j_visitors), "/".join(str(len(j_visitors[v])) for v in VISITORS if v in j_visitors)))
    children = sum(len(n[4]) for n in cs_nodes.values())
    print("compared: %d children, %d optional, %d completion hints, %d constructor parameters"
          % (children, sum(1 for n in cs_nodes.values() for c in n[4] if c[1]),
             sum(1 for n in cs_nodes.values() for c in n[4] if c[2]), sum(len(n[5]) for n in cs_nodes.values())))
    for p in problems:
        print(p)
    if problems:
        print("FAIL: %d difference(s)" % len(problems))
        return 1
    print("OK: structure identical")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
