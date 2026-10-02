// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: proves <hand-written> regions survive regeneration and unexpected regions fail (PORTING.md 4.4).
package org.graylog.kusto.language.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionPreservationTest {
    private static final String EXPRESSION_FROM = "src/Kusto.Language/Syntax/SyntaxNode.cs:107-123";
    private static final String SEMANTICS_FROM = "src/Kusto.Language/Syntax/SyntaxNode_Semantics.cs:233-280";

    @TempDir
    Path dir;

    private int run(boolean check) {
        return GenerateSyntaxNodes.run(dir, check, new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    private String read(String name) throws IOException {
        return Files.readString(dir.resolve(name), StandardCharsets.UTF_8);
    }

    private void write(String name, String text) throws IOException {
        Files.writeString(dir.resolve(name), text, StandardCharsets.UTF_8);
    }

    @Test
    void fresh_directory_gets_every_file_with_empty_regions() throws IOException {
        assertEquals(0, run(false));
        try (var list = Files.list(dir)) {
            assertEquals(229, list.count());
        }
        String expression = read("Expression.java");
        assertTrue(expression.contains("    // <hand-written from=\"" + EXPRESSION_FROM + "\">\n    // </hand-written>\n"
                + "    // <hand-written from=\"" + SEMANTICS_FROM + "\">\n    // </hand-written>\n}\n"), expression);
        assertTrue(expression.contains("// <hand-written-imports>\n// </hand-written-imports>\n"));
        assertTrue(!read("BinaryExpression.java").contains("// <hand-written"));
        assertEquals(0, run(true));
    }

    @Test
    void region_content_survives_regeneration() throws IOException {
        assertEquals(0, run(false));
        String body = "    public boolean isLiteral() {\n        return false;\n    }\n\n    // trailing comment with <angle> \"quotes\"\n";
        String imports = "import org.graylog.kusto.language.symbols.TypeSymbol;\n";
        String original = read("Expression.java");
        String edited = original
                .replace("// <hand-written from=\"" + EXPRESSION_FROM + "\">\n", "// <hand-written from=\"" + EXPRESSION_FROM + "\">\n" + body)
                .replace("// <hand-written-imports>\n", "// <hand-written-imports>\n" + imports);
        String visitorBody = "    public abstract void visitList(SyntaxList list);\n";
        String visitor = read("SyntaxVisitor.java").replace("SyntaxVisitor.cs:10-15\">\n", "SyntaxVisitor.cs:10-15\">\n" + visitorBody);
        write("Expression.java", edited);
        write("SyntaxVisitor.java", visitor);

        assertEquals(0, run(true), "the edited regions are not drift");
        assertEquals(0, run(false));
        assertEquals(edited, read("Expression.java"));
        assertEquals(visitor, read("SyntaxVisitor.java"));

        // Generated text outside the regions is restored.
        write("Expression.java", edited.replace("super(diagnostics);", "super(diagnostics); // edited"));
        assertEquals(1, run(true));
        assertEquals(0, run(false));
        assertEquals(edited, read("Expression.java"));
    }

    @Test
    void missing_region_in_existing_file_is_emitted_empty() throws IOException {
        assertEquals(0, run(false));
        String original = read("NamedParameter.java");
        write("NamedParameter.java", original.replaceAll("(?s)    // <hand-written from=.*?// </hand-written>\n", ""));
        assertEquals(0, run(false));
        assertEquals(original, read("NamedParameter.java"));
    }

    @Test
    void unexpected_region_fails() throws IOException {
        assertEquals(0, run(false));
        String binary = read("BinaryExpression.java");
        write("BinaryExpression.java", binary.replace("\n}\n", "\n    // <hand-written from=\"src/Kusto.Language/Syntax/SyntaxNode.cs:1-2\">\n    // </hand-written>\n}\n"));
        var e = assertThrows(HandWrittenRegions.RegionException.class, () -> run(false));
        assertTrue(e.getMessage().contains("BinaryExpression.java") && e.getMessage().contains("unexpected"), e.getMessage());
        assertThrows(HandWrittenRegions.RegionException.class, () -> run(true));
    }

    @Test
    void wrong_origin_in_expected_class_fails() throws IOException {
        assertEquals(0, run(false));
        write("Expression.java", read("Expression.java").replace(SEMANTICS_FROM, "src/Kusto.Language/Syntax/SyntaxNode_Semantics.cs:233-290"));
        var e = assertThrows(HandWrittenRegions.RegionException.class, () -> run(false));
        assertTrue(e.getMessage().contains("233-290"), e.getMessage());
    }

    @Test
    void imports_region_in_class_without_origins_fails() throws IOException {
        assertEquals(0, run(false));
        write("Clause.java", read("Clause.java").replace("\npublic abstract class", "\n// <hand-written-imports>\n// </hand-written-imports>\npublic abstract class"));
        assertThrows(HandWrittenRegions.RegionException.class, () -> run(false));
    }

    @Test
    void malformed_regions_fail() {
        String open = "    // <hand-written from=\"" + EXPRESSION_FROM + "\">\n";
        String close = "    // </hand-written>\n";
        assertThrows(HandWrittenRegions.RegionException.class,
                () -> HandWrittenRegions.extract("Expression.java", "Expression", open + "x\n"), "unterminated");
        assertThrows(HandWrittenRegions.RegionException.class,
                () -> HandWrittenRegions.extract("Expression.java", "Expression", open + close + open + close), "duplicate");
        assertThrows(HandWrittenRegions.RegionException.class,
                () -> HandWrittenRegions.extract("Expression.java", "Expression", open + open + close), "nested");
        assertThrows(HandWrittenRegions.RegionException.class,
                () -> HandWrittenRegions.extract("Expression.java", "Expression", close), "unmatched end");
        assertThrows(HandWrittenRegions.RegionException.class,
                () -> HandWrittenRegions.extract("Expression.java", "Expression", "    // <hand-written>\n"), "malformed");
        var regions = HandWrittenRegions.extract("Expression.java", "Expression", open + "a\n\n  b\n" + close);
        assertEquals(List.of("a", "", "  b"), regions.body(EXPRESSION_FROM));
        assertEquals(List.of(), regions.body(SEMANTICS_FROM));
        assertEquals(HandWrittenRegions.Regions.EMPTY, HandWrittenRegions.extract("X.java", "X", null));
    }

    /** Each origin range is exactly one upstream {@code partial class} declaration through its closing brace. */
    @Test
    void origin_table_matches_upstream_partials() throws IOException {
        Path probe = RepoPaths.upstreamSource(HandWrittenRegions.SYNTAX_NODE);
        Assumptions.assumeTrue(Files.isRegularFile(probe), "upstream submodule not checked out");
        Map<String, String> upstreamNames = Map.of(
                "SyntaxVisitor1", "SyntaxVisitor<TResult>", "DefaultSyntaxVisitor1", "DefaultSyntaxVisitor<TResult>");
        assertEquals(19, HandWrittenRegions.ORIGINS.size());
        for (var entry : HandWrittenRegions.ORIGINS.entrySet()) {
            String name = upstreamNames.getOrDefault(entry.getKey(), entry.getKey());
            for (String from : entry.getValue()) {
                int colon = from.lastIndexOf(':');
                String[] range = from.substring(colon + 1).split("-");
                List<String> lines = Files.readAllLines(RepoPaths.upstreamSource(from.substring(0, colon)), StandardCharsets.UTF_8);
                String first = lines.get(Integer.parseInt(range[0]) - 1);
                String last = lines.get(Integer.parseInt(range[1]) - 1);
                assertTrue(first.matches("    public (abstract )?partial class " + java.util.regex.Pattern.quote(name) + "( : .*)?"),
                        from + " starts with: " + first);
                assertEquals("    }", last, from + " ends with: " + last);
                for (int i = Integer.parseInt(range[0]); i < Integer.parseInt(range[1]) - 1; i++) {
                    assertTrue(!lines.get(i).startsWith("    }"), from + " closes early at line " + (i + 1));
                }
            }
        }
    }
}
