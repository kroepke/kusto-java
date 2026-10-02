#!/usr/bin/env python3
# Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
# Copyright (c) 2026 Graylog, Inc. Purpose: converts upstream SyntaxNodeInfos.cs into the Java data mirror SyntaxNodeInfos.java (PORTING.md 4.1, 4.2).
"""Convert Kusto.Language.Generators/SyntaxNodeInfos.cs into SyntaxNodeInfos.java.

The C# file is a single static array of object initializers. It is regular enough to convert
line by line, so every upstream line from the class declaration to the class's closing brace maps
to exactly one Java line, at the same line number when the upstream preamble allows it:

    new SyntaxNodeInfo                    -> node()
    {               (object initializer)  -> // {
    Name = "X",                           -> .name("X")
    Properties = new []                   -> .properties(new SyntaxNodeProperty[]
    {               (array initializer)   -> {
    new SyntaxNodeProperty { A = v, ... },  -> prop().a(v)...,
    }               (closes Properties)   -> })
    },  /  }        (closes the node)     -> .end(),  /  .end()
    // comment, blank line                -> unchanged
    #region X / #endregion / #if ...      -> // #region X ...

Anything else is an error: the converter fails with the upstream line number instead of guessing,
so the output never needs a hand edit. Output is deterministic (no timestamps, LF line endings).

Usage:
    python3 porting/tools/convert_syntax_node_infos.py           # writes the Java file
    python3 porting/tools/convert_syntax_node_infos.py --check   # exit 1 if the Java file differs
    python3 porting/tools/convert_syntax_node_infos.py --input X.cs --output Y.java
No third-party dependencies.
"""
import json
import os
import re
import sys

REPO = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
UPSTREAM_REL = "src/Kusto.Language.Generators/SyntaxNodeInfos.cs"
DEFAULT_INPUT = os.path.join(REPO, "upstream", "kusto-query-language", UPSTREAM_REL)
DEFAULT_OUTPUT = os.path.join(
    REPO, "kusto-language-generator", "src", "main", "java", "org", "graylog", "kusto", "language",
    "generator", "SyntaxNodeInfos.java")

# C# field name -> Java fluent builder method, per initialised type.
FIELDS = {
    "SyntaxNodeInfo": {
        "Name": "name", "Doc": "doc", "Remarks": "remarks", "Base": "base", "Abstract": "abstract_",
        "Sealed": "sealed_", "ConstructionOptions": "constructionOptions",
        "CloneOptions": "cloneOptions", "Kind": "kind",
        # "Properties" is handled structurally (multi-line array initializer).
    },
    "SyntaxNodeProperty": {
        "Name": "name", "Type": "type", "Doc": "doc", "PublicSetter": "publicSetter",
        "Optional": "optional", "DefaultValue": "defaultValue", "IsSyntax": "isSyntax",
        "Completion": "completion",
    },
    "KnownTypeInfo": {
        "Name": "name", "Namespace": "namespace", "Immutable": "immutable",
        "CopyByValue": "copyByValue",
    },
}
# Builder entry points (static methods imported in the preamble).
FACTORY = {"SyntaxNodeInfo": "node()", "SyntaxNodeProperty": "prop()", "KnownTypeInfo": "known()"}
# Enum-typed fields whose values are dotted enum member names.
ENUM_VALUE = re.compile(r"(ConstructorGenerationOptions|SyntaxNodeCloneOptions)\.[A-Za-z_]\w*\Z")


class ConvertError(Exception):
    pass


def upstream_commit():
    with open(os.path.join(REPO, "porting", "manifest.json"), encoding="utf-8") as f:
        return json.load(f)["upstreamCommit"]


def split_comment(text):
    """Split 'code // comment' at the first // outside a string literal. Returns (code, comment)."""
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == '@' and i + 1 < n and text[i + 1] == '"':
            j = i + 2
            while j < n:
                if text[j] == '"':
                    if j + 1 < n and text[j + 1] == '"':
                        j += 2
                        continue
                    break
                j += 1
            i = j + 1
        elif c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == '\\' else 1
            i = j + 1
        elif text.startswith("//", i):
            return text[:i].rstrip(), text[i:]
        else:
            i += 1
    return text.rstrip(), None


def java_string(lit, lineno):
    """Convert a C# string literal (regular or verbatim) to a Java string literal."""
    if lit.startswith('@"'):
        body = lit[2:-1].replace('""', '"')
        out = []
        for ch in body:
            if ch == '\\':
                out.append('\\\\')
            elif ch == '"':
                out.append('\\"')
            elif ch == '\n':
                out.append('\\n')
            else:
                out.append(ch)
        return '"' + "".join(out) + '"'
    body = lit[1:-1]
    # C# and Java share \" \\ \n \t \r \0-less escapes used here; reject the ones that differ.
    for m in re.finditer(r"\\(.)", body):
        if m.group(1) not in '"\\ntr\'':
            raise ConvertError("line %d: unsupported C# escape '\\%s'" % (lineno, m.group(1)))
    return '"' + body + '"'


TOKEN = re.compile(r"""\s*(?:
    (?P<ident>[A-Za-z_][\w.]*)
  | (?P<str>@"(?:[^"]|"")*"|"(?:[^"\\]|\\.)*")
  | (?P<num>-?\d+)
  | (?P<punct>[=,{}])
)""", re.VERBOSE)


def tokenize(code, lineno):
    out, i = [], 0
    code = code.rstrip()
    while i < len(code):
        m = TOKEN.match(code, i)
        if not m or m.end() == i:
            raise ConvertError("line %d: cannot tokenize %r" % (lineno, code[i:]))
        kind = m.lastgroup
        out.append((kind, m.group(kind)))
        i = m.end()
    return out


def convert_value(kind, text, lineno):
    if kind == "str":
        return java_string(text, lineno)
    if kind == "num":
        return text
    if kind == "ident":
        if text in ("true", "false", "null"):
            return text
        if ENUM_VALUE.match(text):
            return text
    raise ConvertError("line %d: unsupported value %r" % (lineno, text))


def convert_assignments(tokens, type_name, lineno):
    """tokens: Ident = value (, Ident = value)* [,]. Returns (chain, trailing_comma)."""
    fields = FIELDS[type_name]
    chain, i, trailing = [], 0, False
    while i < len(tokens):
        if i + 2 >= len(tokens) or tokens[i][0] != "ident" or tokens[i + 1] != ("punct", "="):
            raise ConvertError("line %d: expected 'Field = value' in %s" % (lineno, type_name))
        name = tokens[i][1]
        if name not in fields:
            raise ConvertError("line %d: unknown %s field %r" % (lineno, type_name, name))
        value = convert_value(tokens[i + 2][0], tokens[i + 2][1], lineno)
        chain.append(".%s(%s)" % (fields[name], value))
        i += 3
        if i < len(tokens):
            if tokens[i] != ("punct", ","):
                raise ConvertError("line %d: expected ',' after %s" % (lineno, name))
            i += 1
            if i == len(tokens):
                trailing = True
    return "".join(chain), trailing


SINGLE_LINE_OBJECT = re.compile(r"new (SyntaxNodeProperty|KnownTypeInfo)\s*\{(.*)\}\s*(,?)\Z")
ARRAY_DECL = re.compile(r"public static (SyntaxNodeInfo|KnownTypeInfo)\[\] (\w+) = new \1\[\]\Z")
CLASS_DECL = "public static class SyntaxNodeInfos"
DEDENT = "    "  # upstream nests everything in `namespace Kusto.Language.Generator { ... }`


def convert(text, commit):
    if text.startswith("\ufeff"):
        text = text[1:]
    src = text.replace("\r\n", "\n").split("\n")
    try:
        cls = next(i for i, l in enumerate(src) if l.strip() == CLASS_DECL)
    except StopIteration:
        raise ConvertError("class declaration %r not found" % CLASS_DECL)

    out = [
        "// Ported from: " + UPSTREAM_REL,
        "// Upstream: microsoft/Kusto-Query-Language @ " + commit,
        "// SPDX-License-Identifier: Apache-2.0",
        "// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.",
        '// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".',
        "package org.graylog.kusto.language.generator;",
        "",
        "import static org.graylog.kusto.language.generator.KnownTypeInfo.known;",
        "import static org.graylog.kusto.language.generator.SyntaxNodeInfo.node;",
        "import static org.graylog.kusto.language.generator.SyntaxNodeProperty.prop;",
        "",
        None,  # mapping note, filled in below
        "// PORT: §4.2 data mirror: C# object initializers become fluent builder chains, one upstream line per Java line.",
    ]
    if len(out) <= cls:
        note = "Java line N mirrors upstream line N from line %d on" % (cls + 1)
        while len(out) < cls:
            out.append("")
    else:
        note = "Java line = upstream line + %d" % (len(out) - cls)
    out[out.index(None)] = ("// PORT: §4.1 GENERATED by porting/tools/convert_syntax_node_infos.py; do not hand-edit. "
                            + note + ".")

    stack = []        # entries: "class", "array", "node:<Type>", "props"
    pending = None    # what the next lone "{" opens
    done = False
    for idx in range(cls, len(src)):
        lineno = idx + 1
        raw = src[idx].rstrip()
        if done:
            break
        indent_len = len(raw) - len(raw.lstrip())
        indent = raw[:indent_len]
        if indent.startswith(DEDENT):
            indent = indent[len(DEDENT):]
        body = raw.strip()
        code, comment = split_comment(body)
        suffix = ("  " + comment) if comment and code else (comment or "")

        def emit(java):
            line = indent + java
            if suffix:
                line = (line + suffix) if not java else (line + suffix)
            out.append(line.rstrip())

        if not body:
            out.append("")
            continue
        if not code:  # whole-line comment
            out.append(indent + comment)
            continue
        if code.startswith("#"):
            emit("// " + code)
            continue
        top = stack[-1] if stack else None

        if code == CLASS_DECL and not stack:
            emit("public final class SyntaxNodeInfos // PORT: §4.1 C# static class")
            pending = "class"
            continue
        m = ARRAY_DECL.match(code)
        if m and top == "class":
            emit(code)
            pending = "array"
            continue
        if code == "{":
            if pending is None:
                raise ConvertError("line %d: unexpected '{'" % lineno)
            if pending in ("class", "array", "props"):
                emit("{")
            else:
                emit("// {")
            stack.append(pending)
            pending = None
            continue
        if code in ("new SyntaxNodeInfo", "new KnownTypeInfo") and top == "array":
            type_name = code.split()[1]
            emit(FACTORY[type_name])
            pending = "node:" + type_name
            continue
        m = SINGLE_LINE_OBJECT.match(code)
        if m and top in ("props", "array"):
            type_name = m.group(1)
            if (top == "props") != (type_name == "SyntaxNodeProperty"):
                raise ConvertError("line %d: %s not allowed here" % (lineno, type_name))
            chain, _ = convert_assignments(tokenize(m.group(2).strip(), lineno), type_name, lineno)
            emit(FACTORY[type_name] + chain + m.group(3))
            continue
        if code in ("}", "},", "};"):
            if not stack:
                raise ConvertError("line %d: unbalanced '}'" % lineno)
            kind = stack.pop()
            if kind == "props":
                if code != "}":
                    raise ConvertError("line %d: Properties array closed with %r" % (lineno, code))
                emit("})")
            elif kind.startswith("node:"):
                if code == "};":
                    raise ConvertError("line %d: object initializer closed with ';'" % lineno)
                emit(".end()" + ("," if code == "}," else ""))
            elif kind == "array":
                if code != "};":
                    raise ConvertError("line %d: array closed with %r" % (lineno, code))
                emit("};")
            elif kind == "class":
                # PORT: §4.1 C# tolerates `};` after a class body; Java gets a plain `}`.
                emit("}")
                done = True
            continue
        if top and top.startswith("node:"):
            type_name = top[5:]
            if type_name == "SyntaxNodeInfo" and re.match(r"Properties\s*=\s*new\s*(SyntaxNodeProperty)?\s*\[\s*\]\Z", code):
                emit(".properties(new SyntaxNodeProperty[]")
                pending = "props"
                continue
            chain, _ = convert_assignments(tokenize(code, lineno), type_name, lineno)
            emit(chain)
            continue
        raise ConvertError("line %d: cannot convert %r" % (lineno, raw))

    if not done or stack:
        raise ConvertError("unexpected end of input (open: %s)" % stack)
    return "\n".join(out) + "\n"


def main(argv):
    args = argv[1:]
    check = "--check" in args
    args = [a for a in args if a != "--check"]
    inp, outp = DEFAULT_INPUT, DEFAULT_OUTPUT
    while args:
        flag = args.pop(0)
        if flag == "--input" and args:
            inp = args.pop(0)
        elif flag == "--output" and args:
            outp = args.pop(0)
        else:
            print("usage: convert_syntax_node_infos.py [--check] [--input X.cs] [--output Y.java]", file=sys.stderr)
            return 2
    with open(inp, encoding="utf-8") as f:
        text = f.read()
    try:
        java = convert(text, upstream_commit())
    except ConvertError as e:
        print("convert_syntax_node_infos: %s: %s" % (inp, e), file=sys.stderr)
        return 1
    nodes = java.count("node()")
    props = java.count("prop()")
    if check:
        try:
            with open(outp, encoding="utf-8") as f:
                current = f.read()
        except FileNotFoundError:
            current = None
        if current != java:
            print("convert_syntax_node_infos: %s is out of date; re-run without --check" % outp, file=sys.stderr)
            return 1
        print("up to date: %d nodes, %d properties" % (nodes, props))
        return 0
    with open(outp, "w", encoding="utf-8", newline="\n") as f:
        f.write(java)
    print("wrote %s: %d nodes, %d properties, %d lines" % (os.path.relpath(outp, REPO), nodes, props, java.count("\n")))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
