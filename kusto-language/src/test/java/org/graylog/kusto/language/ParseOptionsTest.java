// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: pins ParseOptions defaults, With* copy semantics and equality.

package org.graylog.kusto.language;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ParseOptionsTest {
    @Test
    void defaultValues() {
        ParseOptions d = ParseOptions.Default;
        assertTrue(d.alwaysProduceEndToken());
        assertFalse(d.allowLiteralsWithLineBreaks());
        assertFalse(d.allowNonAdjacentWildcardParts());
        assertEquals(ParserKind.Default, d.parserKind());
    }

    @Test
    void parserKindDeclarationOrder() {
        assertEquals(0, ParserKind.Grammar.ordinal());
        assertEquals(1, ParserKind.Default.ordinal());
    }

    @Test
    void withReturnsSameInstanceWhenUnchanged() {
        ParseOptions d = ParseOptions.Default;
        assertSame(d, d.withAlwaysProduceEndTokens(true));
        assertSame(d, d.withAllowLiteralsWithLineBreaks(false));
        assertSame(d, d.withAllowNonAdjacentWildcardParts(false));
        assertSame(d, d.withParserKind(ParserKind.Default));
    }

    @Test
    void withChangesOnlyOneProperty() {
        ParseOptions d = ParseOptions.Default;
        ParseOptions a = d.withAlwaysProduceEndTokens(false);
        assertNotSame(d, a);
        assertFalse(a.alwaysProduceEndToken());
        assertEquals(d.allowLiteralsWithLineBreaks(), a.allowLiteralsWithLineBreaks());
        assertEquals(d.parserKind(), a.parserKind());

        ParseOptions b = d.withAllowLiteralsWithLineBreaks(true);
        assertTrue(b.allowLiteralsWithLineBreaks());
        assertTrue(b.alwaysProduceEndToken());

        ParseOptions c = d.withAllowNonAdjacentWildcardParts(true);
        assertTrue(c.allowNonAdjacentWildcardParts());

        ParseOptions g = d.withParserKind(ParserKind.Grammar);
        assertEquals(ParserKind.Grammar, g.parserKind());
        assertTrue(g.alwaysProduceEndToken());
    }

    @Test
    void equalsAndHashCode() {
        ParseOptions d = ParseOptions.Default;
        ParseOptions x = d.withAlwaysProduceEndTokens(false).withAlwaysProduceEndTokens(true);
        assertNotSame(d, x);
        assertTrue(d.equals(x));
        assertEquals(d, x);
        assertEquals(d.hashCode(), x.hashCode());
        assertNotEquals(d, d.withAllowLiteralsWithLineBreaks(true));
        assertNotEquals(d, d.withAllowNonAdjacentWildcardParts(true));
        assertNotEquals(d, d.withParserKind(ParserKind.Grammar));
        assertNotEquals(d, d.withAlwaysProduceEndTokens(false));
        assertFalse(d.equals((Object) null));
        assertFalse(d.equals((Object) "Default"));
    }

    @Test
    void equalExceptForParseKindIgnoresParserKind() {
        ParseOptions d = ParseOptions.Default;
        ParseOptions g = d.withParserKind(ParserKind.Grammar);
        assertTrue(d.equalExceptForParseKind(g));
        assertFalse(d.equals(g));
        assertFalse(d.equalExceptForParseKind(d.withAllowLiteralsWithLineBreaks(true)));
        assertFalse(d.equalExceptForParseKind(d.withAlwaysProduceEndTokens(false)));
        assertFalse(d.equalExceptForParseKind(d.withAllowNonAdjacentWildcardParts(true)));
    }
}
