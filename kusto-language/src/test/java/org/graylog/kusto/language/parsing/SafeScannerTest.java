// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: StackSafeScanner (Combinators/SafeScan.cs) is heap-based and result-equivalent to the direct scan.

package org.graylog.kusto.language.parsing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class SafeScannerTest {
    @Test
    void stackSafeScannerHandlesDeepNestingOnSmallStackWhereDirectScanOverflows() throws Exception {
        var grammar = StackSafeParserTest.deepAcyclicGrammar(StackSafeParserTest.DEEP);
        var text = StackSafeParserTest.nested(StackSafeParserTest.DEEP);

        Object direct = StackSafeParserTest.onSmallStack(() -> grammar.scan(new TextSource(text), 0));
        assertInstanceOf(StackOverflowError.class, direct, "the direct recursive scan should overflow a 512 KB stack");

        Object safe = StackSafeParserTest.onSmallStack(() -> SafeScanner.scanSafe(grammar, new TextSource(text), 0));
        assertEquals(text.length(), safe);
    }

    @Test
    void forwardGrammarScanFallsBackToStackSafeScannerOnSmallStack() throws Exception {
        var grammar = StackSafeParserTest.recursiveGrammar();
        var text = StackSafeParserTest.nested(StackSafeParserTest.DEEP);

        Object result = StackSafeParserTest.onSmallStack(() -> grammar.scan(new TextSource(text), 0));
        assertEquals(text.length(), result);

        // unbalanced: fails with the negative length encoding
        var bad = text.substring(0, text.length() - 1);
        Object badResult = StackSafeParserTest.onSmallStack(() -> grammar.scan(new TextSource(bad), 0));
        Object badSafe = StackSafeParserTest.onSmallStack(() -> SafeScanner.scanSafe(grammar, new TextSource(bad), 0));
        assertInstanceOf(Integer.class, badResult);
        assertTrue((Integer) badResult < 0);
        assertEquals(badResult, badSafe);
    }

    /** A grammar exercising not, fails and the untyped match, which only the scanner supports. */
    static Parser<Character> negationGrammar() {
        var x = Parsers.match((Character c) -> c == 'x');
        var y = Parsers.match((Character c) -> c == 'y');
        return Parsers.sequence(
            Parsers.zeroOrMore(Parsers.not(y)),
            Parsers.first(y, Parsers.fails(x)),
            Parsers.zeroOrOne(x));
    }

    @Test
    void stackSafeScannerMatchesDirectScanOnShallowInput() {
        var grammars = List.of(StackSafeParserTest.mixedGrammar(), StackSafeParserTest.recursiveGrammar(), negationGrammar());
        var inputs = List.of("ab.", "a,b;..", "abb.", "a,,b.", "a,b", "!a.", "", "b,bb,ab;.", "x", "a;x", "acc,a.", "acb.",
            "(x)", "((x)", "(y)", "abcy", "xy", "abc");
        for (var grammar : grammars) {
            for (var text : inputs) {
                int direct = grammar.scan(new TextSource(text), 0);
                int safe = SafeScanner.scanSafe(grammar, new TextSource(text), 0);
                assertEquals(direct, safe, () -> "scan length for '" + text + "' with " + grammar.description());
            }
        }
    }

    @Test
    void zeroOrOneSuccessIsDroppedByStackSafeScanner() {
        // PORT-BUG mirror (SafeScan.cs:680-693): the stop branch of VisitZeroOrMore does not add the last result,
        // so a successful ZeroOrOne item counts 0 on the safe path.
        var grammar = negationGrammar();
        assertEquals(5, grammar.scan(new TextSource("abcyx"), 0));
        assertEquals(4, SafeScanner.scanSafe(grammar, new TextSource("abcyx"), 0));

        var x = Parsers.match((Character c) -> c == 'x');
        assertEquals(1, Parsers.zeroOrOne(x).scan(new TextSource("x"), 0));
        assertEquals(0, SafeScanner.scanSafe(Parsers.zeroOrOne(x), new TextSource("x"), 0));
    }
}
