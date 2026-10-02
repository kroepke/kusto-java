// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: compares the emitted Java node structure with the C# reference output (PORTING.md 4.5).
package org.graylog.kusto.language.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Parses {@code porting/reference/GeneratedSyntaxNodes.cs} (byte-identical to the pinned upstream
 * generator's output) and the in-memory Java output, extracts per node (name, base, abstract/sealed,
 * kind, ordered children with optional flag and completion hint, constructor parameters) and the
 * visitor method order, and compares them. C# types are mapped to Java spellings first.
 */
class GeneratorStructureTest {
    record Child(String name, boolean optional, String hint) {
    }

    record Node(String name, String base, String modifier, String kind, List<Child> children, List<String> parameters) {
    }

    record Structure(Map<String, Node> nodes, List<String> order, Map<String, List<String>> visitors) {
    }

    private static Structure csharp;
    private static Structure java;
    private static Map<String, String> files;

    @BeforeAll
    static void parse() throws IOException {
        csharp = parseCSharp(Files.readString(RepoPaths.reference(), StandardCharsets.UTF_8));
        files = SyntaxNodeGenerator.generate(SyntaxNodeInfos.All, SyntaxNodeInfos.KnownTypes);
        java = parseJava(files);
    }

    @Test
    void counts() {
        assertEquals(225, csharp.nodes().size());
        assertEquals(225, java.nodes().size());
        assertEquals(229, files.size());
        assertEquals(16, java.nodes().values().stream().filter(n -> n.modifier().equals("abstract")).count());
        assertEquals(SyntaxNodeGenerator.VISITORS, List.copyOf(java.visitors().keySet()));
    }

    @Test
    void node_order_matches_reference() {
        assertEquals(csharp.order(), java.order());
    }

    @Test
    void every_node_matches_reference() {
        var mismatches = new ArrayList<String>();
        for (String name : csharp.order()) {
            Node expected = csharp.nodes().get(name);
            Node actual = java.nodes().get(name);
            if (!expected.equals(actual)) {
                mismatches.add("C#:   " + expected + "\nJava: " + actual);
            }
        }
        assertTrue(mismatches.isEmpty(), mismatches.size() + " node(s) differ:\n" + String.join("\n", mismatches));
    }

    @Test
    void visitor_method_order_matches_reference() {
        for (String visitor : SyntaxNodeGenerator.VISITORS) {
            assertEquals(csharp.visitors().get(visitor), java.visitors().get(visitor), visitor);
            assertEquals(209, java.visitors().get(visitor).size(), visitor);
        }
    }

    @Test
    void reference_totals() {
        int children = 0;
        int optional = 0;
        int hints = 0;
        for (Node n : csharp.nodes().values()) {
            children += n.children().size();
            for (Child c : n.children()) {
                optional += c.optional() ? 1 : 0;
                hints += c.hint() != null ? 1 : 0;
            }
        }
        assertEquals(683, children);
        assertEquals(92, optional);
        assertEquals(654, hints);
    }

    // ------------------------------------------------------------------------------------------ C#

    private static final Pattern CS_DECL = Pattern.compile("public (?:(abstract|sealed) )?partial class (\\w+) : (\\w+)");
    private static final Pattern CS_KIND = Pattern.compile("public override SyntaxKind Kind => SyntaxKind\\.(\\w+);");
    private static final Pattern CS_CTOR = Pattern.compile("internal (\\w+)\\((.*)\\) : base\\((.*)\\)");
    private static final Pattern CS_METHOD = Pattern.compile("(?:public|protected) override \\S+ (\\w+).*");
    private static final Pattern CS_NAME = Pattern.compile("case (\\d+): return nameof\\((\\w+)\\);");
    private static final Pattern CS_LABEL = Pattern.compile("case (\\d+):");
    private static final Pattern CS_HINT = Pattern.compile("case (\\d+): return CompletionHint\\.(\\w+);");
    private static final Pattern CS_VISITOR = Pattern.compile("public partial class (\\w+)(<TResult>)?(?: : .*)?");
    private static final Pattern CS_VISIT = Pattern.compile("public (?:abstract|override) (?:void|TResult) Visit(\\w+)\\(.*");

    static Structure parseCSharp(String text) {
        var nodes = new LinkedHashMap<String, Node>();
        var order = new ArrayList<String>();
        var visitors = new LinkedHashMap<String, List<String>>();
        boolean inClass = false;
        Builder node = null;
        String method = null;
        String visitor = null;
        for (String raw : text.replace("﻿", "").split("\n", -1)) {
            String line = raw.strip();
            Matcher m;
            if (line.startsWith("#region class ")) {
                inClass = true;
                node = null;
                method = null;
            } else if (line.startsWith("#endregion /* class ")) {
                if (node != null) {
                    nodes.put(node.name, node.build());
                    order.add(node.name);
                }
                inClass = false;
                node = null;
            } else if (inClass && (m = CS_DECL.matcher(line)).matches()) {
                node = new Builder(m.group(2), m.group(3), m.group(1) == null ? "" : m.group(1));
            } else if (!inClass && (m = CS_VISITOR.matcher(line)).matches()) {
                visitor = m.group(1) + (m.group(2) != null ? "1" : "");
                visitors.put(visitor, new ArrayList<>());
            } else if (!inClass && visitor != null) {
                if ((m = CS_VISIT.matcher(line)).matches()) {
                    visitors.get(visitor).add(m.group(1));
                }
            } else if (node != null) {
                if (line.equals("public override SyntaxKind Kind => this.kind;")) {
                    node.kind = "<field>";
                } else if ((m = CS_KIND.matcher(line)).matches()) {
                    node.kind = m.group(1);
                } else if ((m = CS_CTOR.matcher(line)).matches()) {
                    node.parameters = new ArrayList<>();
                    for (String p : splitParameters(m.group(2))) {
                        p = p.split(" = ")[0];
                        int space = p.lastIndexOf(' ');
                        node.parameters.add(javaType(p.substring(0, space)) + " " + p.substring(space + 1).replace("@", ""));
                    }
                } else if ((m = CS_METHOD.matcher(line)).matches()) {
                    method = m.group(1);
                } else if ("GetName".equals(method) && (m = CS_NAME.matcher(line)).matches()) {
                    assertEquals(node.children.size(), Integer.parseInt(m.group(1)));
                    node.children.add(m.group(2));
                } else if ("IsOptional".equals(method) && (m = CS_LABEL.matcher(line)).matches()) {
                    node.optional.add(Integer.parseInt(m.group(1)));
                } else if ("GetCompletionHintCore".equals(method) && (m = CS_HINT.matcher(line)).matches()) {
                    node.hints.put(Integer.parseInt(m.group(1)), m.group(2));
                }
            }
        }
        return new Structure(nodes, order, visitors);
    }

    /** Java spelling of a C# parameter type (PORTING.md 2.4, 3.17). Independent of the generator's map. */
    static String javaType(String cs) {
        if (cs.equals("CompletionHint") || cs.equals("Kusto.Language.Symbols.SymbolMatch")) {
            return "int";
        }
        return cs.replace("IReadOnlyList<", "List<")
                .replaceAll("\\bstring\\b", "String")
                .replaceAll("\\bSyntaxList<", "SyntaxList1<")
                .replaceAll("\\bSeparatedElement<", "SeparatedElement1<");
    }

    // ---------------------------------------------------------------------------------------- Java

    private static final Pattern J_DECL = Pattern.compile("public (?:(abstract|final) )?class (\\w+) extends (\\w+) \\{");
    private static final Pattern J_KIND = Pattern.compile("@Override public SyntaxKind kind\\(\\) \\{ return SyntaxKind\\.(\\w+); \\}");
    private static final Pattern J_CASE_NAME = Pattern.compile("case (\\d+): return \"(\\w+)\";");
    private static final Pattern J_CASE = Pattern.compile("case (\\d+):");
    private static final Pattern J_CASE_HINT = Pattern.compile("case (\\d+): return CompletionHint\\.(\\w+);");
    private static final Pattern J_VISIT = Pattern.compile("(?:public abstract|@Override public) (?:void|TResult) visit(\\w+)\\(.*");

    static Structure parseJava(Map<String, String> files) {
        var nodes = new LinkedHashMap<String, Node>();
        var order = new ArrayList<String>();
        var visitors = new LinkedHashMap<String, List<String>>();
        for (var entry : files.entrySet()) {
            String className = entry.getKey().replace(".java", "");
            List<String> lines = entry.getValue().lines().map(String::strip).toList();
            if (SyntaxNodeGenerator.VISITORS.contains(className)) {
                var methods = new ArrayList<String>();
                for (String line : lines) {
                    Matcher m = J_VISIT.matcher(line);
                    if (m.matches()) {
                        methods.add(m.group(1));
                    }
                }
                visitors.put(className, methods);
                continue;
            }
            Builder node = null;
            for (String line : lines) {
                Matcher m;
                if (node == null) {
                    if ((m = J_DECL.matcher(line)).matches()) {
                        String mod = m.group(1) == null ? "" : m.group(1).equals("final") ? "sealed" : "abstract";
                        node = new Builder(m.group(2), m.group(3), mod);
                    }
                } else if (line.equals("@Override public SyntaxKind kind() { return this.kind; }")) {
                    node.kind = "<field>";
                } else if ((m = J_KIND.matcher(line)).matches()) {
                    node.kind = m.group(1);
                } else if (node.parameters == null && line.startsWith("public " + node.name + "(") && line.endsWith(") {")) {
                    node.parameters = splitParameters(line.substring(("public " + node.name + "(").length(), line.length() - ") {".length()));
                } else if (line.startsWith("@Override public String getName(int index)")) {
                    m = J_CASE_NAME.matcher(line);
                    while (m.find()) {
                        assertEquals(node.children.size(), Integer.parseInt(m.group(1)));
                        node.children.add(m.group(2));
                    }
                } else if (line.startsWith("@Override public boolean isOptional(int index)")) {
                    m = J_CASE.matcher(line);
                    while (m.find()) {
                        node.optional.add(Integer.parseInt(m.group(1)));
                    }
                } else if (line.startsWith("@Override protected int getCompletionHintCore(int index)")) {
                    m = J_CASE_HINT.matcher(line);
                    while (m.find()) {
                        node.hints.put(Integer.parseInt(m.group(1)), m.group(2));
                    }
                }
            }
            assertTrue(node != null && node.name.equals(className), entry.getKey());
            nodes.put(className, node.build());
            order.add(className);
        }
        return new Structure(nodes, order, visitors);
    }

    static List<String> splitParameters(String text) {
        var out = new ArrayList<String>();
        int depth = 0;
        var current = new StringBuilder();
        for (char ch : text.toCharArray()) {
            if (ch == '<') {
                depth++;
            } else if (ch == '>') {
                depth--;
            }
            if (ch == ',' && depth == 0) {
                out.add(current.toString().strip());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (!current.toString().isBlank()) {
            out.add(current.toString().strip());
        }
        return out;
    }

    private static final class Builder {
        final String name;
        final String base;
        final String modifier;
        String kind;
        final List<String> children = new ArrayList<>();
        final TreeSet<Integer> optional = new TreeSet<>();
        final TreeMap<Integer, String> hints = new TreeMap<>();
        List<String> parameters;

        Builder(String name, String base, String modifier) {
            this.name = name;
            this.base = base;
            this.modifier = modifier;
        }

        Node build() {
            var list = new ArrayList<Child>();
            for (int i = 0; i < children.size(); i++) {
                list.add(new Child(children.get(i), optional.contains(i), hints.get(i)));
            }
            return new Node(name, base, modifier, kind, List.copyOf(list), parameters == null ? List.of() : List.copyOf(parameters));
        }
    }
}
