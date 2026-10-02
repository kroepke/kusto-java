// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for PartialParser (Combinators/PartialParser.cs) on a small token grammar.

package org.graylog.kusto.language.parsing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graylog.kusto.language.syntax.SyntaxKind;
import org.graylog.kusto.language.syntax.SyntaxToken;
import org.junit.jupiter.api.Test;

class PartialParserTest {
    static ArraySource<LexicalToken> lex(String text) {
        return new ArraySource<LexicalToken>(Arrays.asList(TokenParser.parseTokens(text)));
    }

    static Parser<LexicalToken> command(String name) {
        return Parsers.sequence(
            SyntaxParsers.token(SyntaxKind.DotToken),
            SyntaxParsers.token("show"),
            SyntaxParsers.token(name)).withTag(name);
    }

    final Parser<LexicalToken> showTables = command("tables");
    final Parser<LexicalToken> showFunctions = command("functions");
    final List<Parser<LexicalToken>> commands = List.of(showTables, showFunctions);

    @Test
    void fullMatchPicksTheMatchingParser() {
        var source = lex(".show tables");
        assertEquals(3, PartialParser.scanPartialBest(commands, source, 0));

        var output = new ArrayList<Object>();
        var best = new ArrayList<Parser<LexicalToken>>();
        var failures = new ArrayList<Parser<LexicalToken>>();
        int len = PartialParser.parsePartialBest(commands, source, 0, output, 0, best, (p, o) -> failures.add(p));
        assertEquals(3, len);
        assertEquals(List.of(showTables), best);
        assertEquals(3, output.size());
        assertEquals("tables", ((SyntaxToken) output.get(2)).text());
        assertTrue(failures.isEmpty());
    }

    @Test
    void partialMatchReportsCombinedFailure() {
        var source = lex(".show foo");
        assertEquals(2, PartialParser.scanPartialBest(commands, source, 0));

        var output = new ArrayList<Object>();
        var best = new ArrayList<Parser<LexicalToken>>();
        var failures = new ArrayList<Parser<LexicalToken>>();
        int len = PartialParser.parsePartialBest(commands, source, 0, output, 0, best, (p, o) -> failures.add(p));
        assertEquals(2, len);
        // both commands scan the same amount, so both are best (Distinct keeps order)
        assertEquals(List.of(showTables, showFunctions), best);
        assertEquals(2, output.size());
        assertEquals("show", ((SyntaxToken) output.get(1)).text());

        // all best paths end in an error: the errors are combined into one BestParser
        assertEquals(1, failures.size());
        var combined = assertInstanceOf(BestParser.class, failures.get(0));
        assertEquals(2, combined.childParserCount());
        assertEquals("tables", combined.getChildParser(0).tag());
        assertEquals("functions", combined.getChildParser(1).tag());
    }

    @Test
    void scanAndParsePartialOfOneParser() {
        assertEquals(3, PartialParser.scanPartial(showTables, lex(".show tables"), 0));
        assertEquals(2, PartialParser.scanPartial(showTables, lex(".show foo"), 0));
        assertEquals(-1, PartialParser.scanPartial(showTables, lex("foo"), 0));

        var output = new ArrayList<Object>();
        assertEquals(2, PartialParser.parsePartial(showTables, lex(".show foo"), 0, output, 0));
        assertEquals(2, output.size());
        assertEquals(-1, PartialParser.parsePartial(showTables, lex("foo"), 0, new ArrayList<Object>(), 0));
    }

    @Test
    void bestPathIsMemoizedPerSource() {
        var source = lex(".show foo");
        assertEquals(2, PartialParser.scanPartialBest(commands, source, 0));
        // a second call with an equal key (same parsers, same start) hits the cache
        assertEquals(2, PartialParser.scanPartialBest(new ArrayList<>(commands), source, 0));
        var best = new ArrayList<Parser<LexicalToken>>();
        PartialParser.parsePartialBest(commands, source, 0, new ArrayList<Object>(), 0, best);
        assertEquals(2, best.size());
    }

    @Test
    void emptyParserListThrowsLikeEnumerableMax() {
        // Enumerable.Max on an empty sequence throws InvalidOperationException upstream (PartialParser.cs:365)
        var source = lex(".foo");
        assertThrows(IllegalStateException.class, () -> PartialParser.scanPartialBest(List.<Parser<LexicalToken>>of(), source, 0));
        assertThrows(IllegalStateException.class,
            () -> PartialParser.parsePartialBest(List.<Parser<LexicalToken>>of(), lex(".foo"), 0, new ArrayList<Object>(), 0, new ArrayList<Parser<LexicalToken>>()));
    }

    @Test
    void optionalAndRepeatedPartsArePartiallyScanned() {
        var name = SyntaxParsers.token(SyntaxKind.IdentifierToken);
        var comma = SyntaxParsers.token(SyntaxKind.CommaToken);
        var grammar = Parsers.sequence(
            SyntaxParsers.token(SyntaxKind.DotToken),
            Parsers.zeroOrMore(Parsers.sequence(name, comma)),
            SyntaxParsers.token(SyntaxKind.SemicolonToken));
        // ".a, b, c" scans the dot, two (name, comma) pairs and the partial pair "c" (whose comma is missing)
        assertEquals(6, PartialParser.scanPartial(grammar, lex(".a, b, c"), 0));
        var failures = new ArrayList<Parser<LexicalToken>>();
        var output = new ArrayList<Object>();
        assertEquals(6, PartialParser.parsePartial(grammar, lex(".a, b, c"), 0, output, 0, (p, o) -> failures.add(p)));
        assertFalse(failures.isEmpty());

        failures.clear();
        assertEquals(6, PartialParser.parsePartial(grammar, lex(".a, b, ;"), 0, new ArrayList<Object>(), 0, (p, o) -> failures.add(p)));
        assertTrue(failures.isEmpty());
    }
}
