// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: pins LexicalToken constructor defaults (LexicalToken.cs:37-48).

package org.graylog.kusto.language.parsing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.DiagnosticFacts;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.junit.jupiter.api.Test;

class LexicalTokenTest {
    @Test
    void nullTriviaAndTextBecomeEmpty() {
        LexicalToken t = new LexicalToken(SyntaxKind.IdentifierToken, null, null);
        assertEquals("", t.trivia());
        assertEquals("", t.text());
        assertEquals(0, t.length());
        assertEquals(SyntaxKind.IdentifierToken, t.kind());
    }

    @Test
    void defaultDiagnosticsAreNoDiagnostics() {
        assertSame(Diagnostic.NoDiagnostics, new LexicalToken(SyntaxKind.IdentifierToken, " ", "x").diagnostics());
        assertSame(Diagnostic.NoDiagnostics, new LexicalToken(SyntaxKind.IdentifierToken, " ", "x", (List<Diagnostic>) null).diagnostics());
        assertSame(Diagnostic.NoDiagnostics, new LexicalToken(SyntaxKind.IdentifierToken, " ", "x", (Diagnostic) null).diagnostics());
    }

    @Test
    void singleDiagnosticBecomesOneElementList() {
        Diagnostic d = DiagnosticFacts.getMissingText(")");
        LexicalToken t = new LexicalToken(SyntaxKind.LongLiteralToken, "", "long(1", d);
        assertEquals(1, t.diagnostics().size());
        assertSame(d, t.diagnostics().get(0));
    }

    @Test
    void lengthIsTriviaPlusText() {
        LexicalToken t = new LexicalToken(SyntaxKind.IdentifierToken, "  // c\n", "abc");
        assertEquals(10, t.length());
    }

    @Test
    void identityEquality() {
        LexicalToken a = new LexicalToken(SyntaxKind.IdentifierToken, "", "a");
        LexicalToken b = new LexicalToken(SyntaxKind.IdentifierToken, "", "a");
        assertNotEquals(a, b);
        assertEquals(a, a);
    }
}
