// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: enforces SourceRules on kusto-language sources and proves each rule on synthetic snippets.

package org.graylog.kusto.language.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** {@link SourceRules} on {@code ../kusto-language/src/main/java}, plus self-tests of every rule. */
class SourceRulesTest {
    private static final String SHA = "9d95a2d5bb085d151f14e88e07b703755fd914e1";
    private static final String ORIGINAL = SourceRules.ORIGINAL_1 + "\n// Copyright (c) 2026 Graylog, Inc. Purpose: test.\n\npackage x;\n";
    private static final String PORTED = "// Ported from: src/Kusto.Language/Parser/TokenParser.cs\n"
            + "// Upstream: microsoft/Kusto-Query-Language @ " + SHA + "\n"
            + SourceRules.SPDX + "\n" + SourceRules.UPSTREAM_LICENSE + "\n" + SourceRules.DERIVED + "\npackage x;\n";

    @Test
    void kusto_language_sources_follow_the_rules() {
        Path root = Harness.repoRoot().resolve("kusto-language/src/main/java");
        assertTrue(Files.isDirectory(root), root + " missing");
        SourceRules.Scan scan = SourceRules.scanTree(root, Harness.upstreamCommit(), true);
        Map<String, List<SourceRules.Marker>> byWave = scan.byWave();
        System.out.println("PORT-PENDING/PORT-SKELETON markers in kusto-language: " + scan.markers().size());
        byWave.forEach((wave, ms) -> {
            System.out.println("  " + wave + ": " + ms.size());
            ms.forEach(m -> System.out.println("    " + m.kind() + " " + m.file() + ":" + m.line()));
        });
        if (!scan.violations().isEmpty()) {
            fail(scan.violations().size() + " source rule violations:\n  "
                    + String.join("\n  ", scan.violations().stream().map(Object::toString).toList()));
        }
    }

    private static List<SourceRules.Violation> scan(String rel, String body) {
        return SourceRules.scanFile(rel, ORIGINAL + body, SHA, true).violations();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "java.util.stream|import java.util.stream.Collectors;",
        "HashMap<|Map<String, Integer> m = foo(); HashMap<String, Integer> h = null;",
        "new HashMap|Object m = new HashMap();",
        "HashSet<|HashSet<String> s = null;",
        "new HashSet|Object s = new HashSet();",
        "EnumMap|Object m = new EnumMap<>(K.class);",
        "CASE_INSENSITIVE_ORDER|var c = String.CASE_INSENSITIVE_ORDER;",
        "equalsIgnoreCase(|boolean b = a.equalsIgnoreCase(c);",
        ".trim()|String t = s.trim();",
        ".strip()|String t = s.strip();",
        ".isBlank()|boolean b = s.isBlank();",
        ".split(|String[] p = s.split(x);",
        "String.join(|String j = String.join(x, y);",
        "Double.parseDouble(|double d = Double.parseDouble(s);",
        "Long.parseLong(|long l = Long.parseLong(s);",
        "Integer.parseInt(|int i = Integer.parseInt(s);",
        "UUID.fromString(|var u = UUID.fromString(s);",
        "ThreadLocalRandom|int r = ThreadLocalRandom.current().nextInt();",
        "assert|    assert x > 0 : y;",
    })
    void each_banned_pattern_is_detected(String label, String line) {
        List<SourceRules.Violation> v = scan("org/x/A.java", line + "\n");
        assertTrue(v.stream().anyMatch(x -> x.detail().equals(label)), label + " not detected in: " + line + " -> " + v);
        assertTrue(scan("org/graylog/kusto/language/utils/dotnet/A.java", line + "\n").isEmpty(), "utils/dotnet must be exempt");
    }

    @Test
    void every_banned_label_has_a_self_test() throws Exception {
        CsvSource src = SourceRulesTest.class.getDeclaredMethod("each_banned_pattern_is_detected", String.class, String.class)
                .getAnnotation(CsvSource.class);
        List<String> tested = java.util.Arrays.stream(src.value()).map(s -> s.substring(0, s.indexOf('|'))).toList();
        assertEquals(List.copyOf(SourceRules.BANNED.keySet()), tested);
    }

    @Test
    void allowed_lookalikes_pass() {
        String body = """
                Map<String, Integer> m = new LinkedHashMap<>();
                LinkedHashMap<String, Integer> l = new LinkedHashMap<String, Integer>();
                Set<String> s = new LinkedHashSet<>(); LinkedHashSet<String> t = null;
                String a = "x.trim() and java.util.stream and HashMap<";
                char c = '"';
                // s.trim() in a comment, new HashMap<>()
                /* block: equalsIgnoreCase( */
                String tb = \"""
                    s.split(",") text block
                    \""";
                int assertion = 1; myassert(x);
                String sub = s.substring(1);
                """;
        assertEquals(List.of(), scan("org/x/A.java", body));
    }

    @Test
    void substring_needs_additive_end_argument() {
        assertTrue(scan("org/x/A.java", "String r = s.substring(a, b);\n").stream()
                .anyMatch(v -> v.detail().startsWith("substring end argument 'b'")));
        assertEquals(List.of(), scan("org/x/A.java", "String r = s.substring(a, a + b);\n"));
        assertEquals(List.of(), scan("org/x/A.java", "String r = s.substring(f(a, c), (start + len));\n"));
        assertEquals(List.of(), scan("org/x/A.java", "String r = s.substring(i,\n    i + g(x, y));\n"));
        assertEquals(List.of(), scan("org/x/A.java", "String r = s.substring(0, len - 1);\n"));
        assertEquals(List.of(), scan("org/x/A.java", "String r = e.substring(a, len); // PORT: \u00a75.4 (start, length)\n"));
        assertEquals(1, scan("org/x/A.java", "String r = s.substring(1, len - 1);\n").size());
        assertEquals(1, scan("org/x/A.java", "String r = s.substring(1, f(a + b));\n").size());
        assertEquals(1, scan("org/x/A.java", "String r = s.substring(1, i++);\n").size());
        assertEquals(1, scan("org/x/A.java", "String r = s.substring(1, +n);\n").size());
        List<SourceRules.Violation> multi = scan("org/x/A.java", "int x;\nString r = s\n    .substring(a, b);\n");
        assertEquals(1, multi.size());
        assertEquals(7, multi.get(0).line(), "line of the .substring call");
    }

    @Test
    void header_forms() {
        assertEquals(List.of(), SourceRules.header("A.java", ORIGINAL, SHA));
        assertEquals(List.of(), SourceRules.header("A.java", PORTED, SHA));
        String merged = "// Ported from: src/Kusto.Language/Syntax/SyntaxNode.cs\n" + PORTED;
        assertEquals(List.of(), SourceRules.header("A.java", merged, SHA));
        String generated = "// Ported from: src/Kusto.Language/Syntax/CodeGen/GeneratedSyntaxNodes.tt (node BinaryExpression)\n"
                + "// Upstream: microsoft/Kusto-Query-Language @ " + SHA + "\n" + SourceRules.SPDX + "\n"
                + "// Copyright (c) Microsoft Corporation. All rights reserved.\n"
                + "// Upstream license: Apache-2.0. This file is GENERATED by kusto-language-generator; do not edit outside <hand-written> regions.\n"
                + "package x;\n";
        assertEquals(List.of(), SourceRules.header("A.java", generated, SHA));

        assertFalse(SourceRules.header("A.java", PORTED.replace(SHA, "0".repeat(40)), SHA).isEmpty(), "stale sha");
        assertFalse(SourceRules.header("A.java", "package x;\n", SHA).isEmpty(), "no header");
        assertFalse(SourceRules.header("A.java", PORTED.replace("TokenParser.cs", "TokenParser.java"), SHA).isEmpty());
        assertFalse(SourceRules.header("A.java", PORTED.replace(SourceRules.DERIVED + "\n", ""), SHA).isEmpty());
        assertFalse(SourceRules.header("A.java", SourceRules.ORIGINAL_1 + "\n// Copyright (c) 2026 Graylog, Inc.\n", SHA).isEmpty());
        assertFalse(SourceRules.header("A.java", "\n" + ORIGINAL, SHA).isEmpty(), "header must be on line 1");
    }

    @Test
    void port_markers_must_cite_rule_or_drow() {
        assertEquals(List.of(), scan("org/x/A.java", "int a; // PORT: §3.6 LINQ\nint b; // PORT: D12 ordinal\n// PORT: §5\n"));
        List<SourceRules.Violation> v = scan("org/x/A.java", "int a; // PORT: because\n");
        assertEquals(1, v.size());
        assertEquals("marker", v.get(0).rule());
        assertEquals(List.of(), scan("org/x/A.java", "String s = \"// PORT: not a comment\";\n"));
        assertEquals(List.of(), SourceRules.scanFile("A.java", PORTED, SHA, true).violations(), "header's quoted // PORT: is not a marker");
    }

    @Test
    void wave_markers_are_collected() {
        SourceRules.Scan s = SourceRules.scanFile("org/x/A.java", ORIGINAL
                + "// PORT-SKELETON: W4\nvoid f() {\n    // PORT-PENDING: W2\n    throw new UnsupportedOperationException(\"PORT-PENDING W2\");\n}\n"
                + "// PORT-PENDING: W3a\n", SHA, true);
        assertEquals(List.of(), s.violations());
        assertEquals(List.of("W2", "W3a", "W4"), List.copyOf(s.byWave().keySet()));
        assertEquals(1, SourceRules.scanFile("org/x/A.java", ORIGINAL + "// PORT-PENDING: later\n", SHA, true).violations().size());
    }
}
