// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: checks the CodeGenerator mirror against the C# reference output's preamble.
package org.graylog.kusto.language.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;

class CodeGeneratorTest {
    /** Upstream SyntaxNodeGenerator.WriteFileStart + namespace + first region reproduce reference lines 4-25. */
    @Test
    void csharp_preamble_matches_reference() throws IOException {
        var cg = new CodeGenerator();
        cg.writeHeader(".tt", "CslTreeGenerator.t4");
        cg.writeUsingBlock(List.of("System", "System.Collections.Generic",
                "CompletionKind=Kusto.Language.Editor.CompletionKind",
                "CompletionHint=Kusto.Language.Editor.CompletionHint"), false, false);
        cg.writeNamespaceDeclarationBegin("Kusto.Language.Syntax");
        cg.writeRegionBegin("SyntaxNodes");
        List<String> reference = Files.readAllLines(RepoPaths.reference(), StandardCharsets.UTF_8);
        String expected = String.join("\n", reference.subList(3, 25)) + "\n";
        assertEquals(expected, cg.getText());
    }

    @Test
    void indentation_and_empty_lines() {
        var cg = new CodeGenerator();
        cg.writeScope("class A", () -> {
            cg.writeEmptyLineIfNeeded();         // suppressed: the line above ends with "{"
            cg.writeLine("int x;");
            cg.writeEmptyLineIfNeeded();         // written, without indentation
            cg.writeEmptyLineIfNeeded();         // suppressed: already empty above
            cg.writeLine("int y; // {0}", "z");
        });
        assertEquals("class A\n{\n    int x;\n\n    int y; // z\n}\n", cg.getText());
    }

    @Test
    void java_scope_and_region() {
        var cg = new CodeGenerator();
        cg.writeJavaScope("class A", () -> cg.writeHandWrittenRegion("f.cs:1-2", List.of("  raw {", "")));
        assertEquals("class A {\n    // <hand-written from=\"f.cs:1-2\">\n  raw {\n\n    // </hand-written>\n}\n", cg.getText());
    }

    @Test
    void names_and_format() {
        assertEquals("@operator", CodeGenerator.getCamelCase("Operator"));
        assertEquals("operator", CodeGenerator.getJavaCamelCase("Operator"));
        assertEquals("default_", CodeGenerator.getJavaCamelCase("Default"));
        assertEquals("NameDeclaration", CodeGenerator.getPascalCase("nameDeclaration"));
        assertEquals("m_left", CodeGenerator.getClassMemberName("Left"));
        assertEquals("a {b} 1 x", CodeGenerator.format("a {{b}} {1} {0}", "x", 1));
        assertThrows(IllegalArgumentException.class, () -> CodeGenerator.format("{2}", "x"));
        assertEquals("a &lt;b&gt; &#64;c *&#47;", CodeGenerator.escapeJavadoc("a <b> @c */"));
    }

    @Test
    void java_types() {
        assertEquals("SyntaxList1<SeparatedElement1<Expression>>", SyntaxNodeGenerator.getJavaType("SyntaxList<SeparatedElement<Expression>>"));
        assertEquals("SyntaxList", SyntaxNodeGenerator.getJavaType("SyntaxList"));
        assertEquals("SeparatedElement", SyntaxNodeGenerator.getJavaType("SeparatedElement"));
        assertEquals("String", SyntaxNodeGenerator.getJavaType("string"));
        assertEquals("List<String>", SyntaxNodeGenerator.getJavaType("IReadOnlyList<string>"));
        assertEquals("int", SyntaxNodeGenerator.getJavaType("CompletionHint"));
        assertEquals("int", SyntaxNodeGenerator.getJavaType("Kusto.Language.Symbols.SymbolMatch"));
        assertEquals("NameReference", SyntaxNodeGenerator.getJavaType("NameReference"));
    }
}
