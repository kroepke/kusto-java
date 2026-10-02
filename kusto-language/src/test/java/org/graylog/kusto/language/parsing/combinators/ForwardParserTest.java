// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: ForwardParser recursion and the MaxCallDepth = 30 fallback to SafeParser/SafeScanner (ForwardParser.cs:47-106, PORTING D9).

package org.graylog.kusto.language.parsing.combinators;

import static org.graylog.kusto.language.parsing.combinators.CombinatorTestSupport.assertAgree;
import static org.graylog.kusto.language.parsing.combinators.CombinatorTestSupport.parse;
import static org.graylog.kusto.language.parsing.combinators.CombinatorTestSupport.parseList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.parsing.ParseResult;
import org.graylog.kusto.language.parsing.Parser2;
import org.graylog.kusto.language.parsing.Parsers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ForwardParserTest {
    /** nested := '(' nested ')' | 'x'; value = nesting depth. */
    private static Parser2<Character, Integer> nested() {
        @SuppressWarnings("unchecked")
        Parser2<Character, Integer>[] core = new Parser2[1];
        Parser2<Character, Integer> forward = Parsers.forward(() -> core[0]);
        core[0] = Parsers.first(
            Parsers.rule(
                Parsers.match((Character c) -> c == '(', (Character c) -> 0),
                forward,
                Parsers.match((Character c) -> c == ')', (Character c) -> 0),
                (open, depth, close) -> depth + 1),
            Parsers.match((Character c) -> c == 'x', (Character c) -> 0));
        return forward;
    }

    private static String input(int depth) {
        return "(".repeat(depth) + "x" + ")".repeat(depth);
    }

    // Upstream does not throw past MaxCallDepth (30): it switches to the stack-safe engines, which
    // yield the same results. 29..31 straddle the threshold; 31 levels need the fallback.
    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 29, 30, 31, 32, 60, 200 })
    void nestedForwardsParseAndScanAtAndBeyondMaxCallDepth(int depth) {
        Parser2<Character, Integer> p = nested();
        String text = input(depth);
        assertEquals(text.length(), assertAgree(p, text));
        assertEquals(depth, parse(p, text).value());
        List<Object> out = new ArrayList<>();
        assertEquals(text.length(), parseList(p, text, out));
        assertEquals(List.of(depth), out);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 30, 31, 45 })
    void nestedForwardsFailIdenticallyAtAndBeyondMaxCallDepth(int depth) {
        Parser2<Character, Integer> p = nested();
        String text = input(depth);
        String broken = text.substring(0, text.length() - 1) + "]";   // last ')' replaced
        int expected = -(text.length() - 1) - 1;                        // consumed prefix, then fail
        assertEquals(expected, assertAgree(p, broken));
        ParseResult<Integer> r = parse(p, broken);
        assertEquals(expected, r.length());
        assertEquals(null, r.value());
    }

    @Test
    void fallbackBoundsTheJavaStackPastMaxCallDepth() {
        // The innermost 'x' is matched with the Java stack depth recorded. On the direct path the depth grows
        // with the nesting; past MaxCallDepth the stack-safe engines take over, so 300 levels need far fewer
        // frames than the direct path would (it measures 215 frames at both 30 and 300 levels).
        int[] frames = new int[1];
        @SuppressWarnings("unchecked")
        Parser2<Character, Integer>[] core = new Parser2[1];
        Parser2<Character, Integer> forward = Parsers.forward(() -> core[0]);
        core[0] = Parsers.first(
            Parsers.rule(
                Parsers.match((Character c) -> c == '(', (Character c) -> 0),
                forward,
                Parsers.match((Character c) -> c == ')', (Character c) -> 0),
                (open, depth, close) -> depth + 1),
            Parsers.match((Character c) -> {
                if (c == 'x') {
                    frames[0] = Thread.currentThread().getStackTrace().length;
                }
                return c == 'x';
            }, (Character c) -> 0));

        assertEquals(30, parse(forward, input(30)).value());
        int at30 = frames[0];
        assertEquals(300, parse(forward, input(300)).value());
        int at300 = frames[0];
        assertTrue(at300 <= at30 + 50, "frames at 30: " + at30 + ", at 300: " + at300);
    }

    @ParameterizedTest
    @ValueSource(ints = { 5, 31, 40 })
    void callDepthCounterIsRestoredAfterEachCall(int depth) {
        Parser2<Character, Integer> p = nested();
        // repeated calls on the same thread behave identically (the ThreadLocal counter returns to zero)
        for (int i = 0; i < 3; i++) {
            assertEquals(depth, parse(p, input(depth)).value());
        }
    }
}
