// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: StackSafeParser (Combinators/SafeParse.cs) is heap-based and result-equivalent to the direct parse.

package org.graylog.kusto.language.parsing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.junit.jupiter.api.Test;

class StackSafeParserTest {
    static final int DEEP = 5_000;
    static final long SMALL_STACK = 512L << 10;

    static final Parser2<Character, Character> OPEN = Parsers.match((Character c) -> c == '(', c -> c).withTag("'('");
    static final Parser2<Character, Character> CLOSE = Parsers.match((Character c) -> c == ')', c -> c).withTag("')'");
    static final Parser2<Character, Integer> LEAF = Parsers.match((Character c) -> c == 'x', c -> 0).withTag("x");

    /** expr := '(' expr ')' | 'x', producing the nesting depth; recursive through Parsers.forward. */
    @SuppressWarnings("unchecked")
    static Parser2<Character, Integer> recursiveGrammar() {
        Parser2<Character, Integer>[] expr = new Parser2[1];
        var forward = Parsers.forward(() -> expr[0]);
        expr[0] = Parsers.first(Parsers.rule(OPEN, forward, CLOSE, (o, e, c) -> e + 1), LEAF);
        return forward;
    }

    /** An acyclic grammar that is exactly {@code depth} rules deep: no ForwardParser guard, so the direct parse recurses. */
    static Parser2<Character, Integer> deepAcyclicGrammar(int depth) {
        Parser2<Character, Integer> p = LEAF;
        for (int i = 0; i < depth; i++) {
            p = Parsers.rule(OPEN, p, CLOSE, (o, e, c) -> e + 1);
        }
        return p;
    }

    static String nested(int depth) {
        return "(".repeat(depth) + "x" + ")".repeat(depth);
    }

    static <T> T onSmallStack(java.util.function.Supplier<T> body) throws InterruptedException {
        var result = new AtomicReference<Object>();
        var t = new Thread(null, () -> {
            try {
                result.set(body.get());
            } catch (Throwable e) {
                result.set(e);
            }
        }, "t", SMALL_STACK);
        t.start();
        t.join();
        @SuppressWarnings("unchecked")
        T r = (T) result.get();
        return r;
    }

    @Test
    void stackSafeParserHandlesDeepNestingOnSmallStackWhereDirectParseOverflows() throws Exception {
        var grammar = deepAcyclicGrammar(DEEP);
        var text = nested(DEEP);

        Object direct = onSmallStack(() -> grammar.parse(new TextSource(text), 0, new ArrayList<Object>(), 0));
        assertInstanceOf(StackOverflowError.class, direct, "the direct recursive parse should overflow a 512 KB stack");

        Object safe = onSmallStack(() -> {
            var output = new ArrayList<Object>();
            int len = SafeParser.parseSafe(grammar, new TextSource(text), 0, output, 0);
            return List.of(len, output);
        });
        assertFalse(safe instanceof Throwable, () -> "safe parse threw " + safe);
        var pair = (List<?>) safe;
        assertEquals(text.length(), pair.get(0));
        assertEquals(List.of(DEEP), pair.get(1));
    }

    @Test
    void forwardGrammarFallsBackToStackSafeParserOnSmallStack() throws Exception {
        var grammar = recursiveGrammar();
        var text = nested(DEEP);

        Object result = onSmallStack(() -> grammar.parse(new TextSource(text), 0));
        assertFalse(result instanceof Throwable, () -> "parse threw " + result);
        @SuppressWarnings("unchecked")
        var r = (ParseResult<Integer>) result;
        assertEquals(text.length(), r.length());
        assertEquals(DEEP, r.value());

        Object listResult = onSmallStack(() -> {
            var output = new ArrayList<Object>();
            int len = SafeParser.parseSafe(grammar, new TextSource(text), 0, output, 0);
            return List.of(len, output);
        });
        assertFalse(listResult instanceof Throwable, () -> "safe parse threw " + listResult);
        assertEquals(List.of(text.length(), List.of(DEEP)), listResult);
    }

    /** A grammar exercising rule, sequence, first, best, optional, required, zeroOrMore, oneOrMore, produce, if, apply. */
    static Parser<Character> mixedGrammar() {
        var a = Parsers.match((Character c) -> c == 'a', c -> "a");
        var b = Parsers.match((Character c) -> c == 'b', c -> "b");
        var c = Parsers.match((Character ch) -> ch == 'c', ch -> "c");
        var comma = Parsers.match((Character ch) -> ch == ',', ch -> ",");
        var item = Parsers.first(
            Parsers.rule(a, b, (x, y) -> x + y),
            Parsers.applyZeroOrMore(a, left -> Parsers.rule(left, c, (l, r) -> l + r)),
            Parsers.best(b, Parsers.rule(b, b, (x, y) -> x + y)));
        var list = Parsers.produce(
            Parsers.sequence(
                Parsers.required(item, () -> "<missing>"),
                Parsers.zeroOrMore(Parsers.sequence(comma, Parsers.required(item, () -> "<missing>")))),
            (List<Object> elements) -> DotNetStrings.join("|", elements));
        var tail = Parsers.optional(Parsers.rule(Parsers.match((Character ch) -> ch == ';', ch -> ";"), s -> s), () -> "<none>");
        var guarded = Parsers.if_(Parsers.match((Character ch) -> ch != '!'), list);
        return Parsers.sequence(guarded, tail, Parsers.oneOrMore(Parsers.match((Character ch) -> ch == '.', ch -> ".")));
    }

    @Test
    void stackSafeParserMatchesDirectParseOnShallowInput() {
        var grammar = mixedGrammar();
        for (var text : List.of("ab.", "a,b;..", "abb.", "a,,b.", "a,b", "!a.", "", "b,bb,ab;.", "x", "a;x", "acc,a.", "acb.")) {
            var directOut = new ArrayList<Object>();
            int direct = grammar.parse(new TextSource(text), 0, directOut, 0);
            var safeOut = new ArrayList<Object>();
            int safe = SafeParser.parseSafe(grammar, new TextSource(text), 0, safeOut, 0);
            assertEquals(direct, safe, () -> "length for '" + text + "'");
            assertEquals(directOut, safeOut, () -> "output for '" + text + "'");
        }

        var recursive = recursiveGrammar();
        for (var text : List.of("x", "(x)", "((x))", "((x)", "(y)", "", "(((x)))")) {
            var directOut = new ArrayList<Object>();
            int direct = recursive.parse(new TextSource(text), 0, directOut, 0);
            var safeOut = new ArrayList<Object>();
            int safe = SafeParser.parseSafe(recursive, new TextSource(text), 0, safeOut, 0);
            assertEquals(direct, safe, () -> "length for '" + text + "'");
            assertEquals(directOut, safeOut, () -> "output for '" + text + "'");
        }
    }

    @Test
    void notAndFailsAndUntypedMatchAreNotSupportedByStackSafeParser() {
        // upstream throws NotImplementedException for these visits (SafeParse.cs:332-335, 513-516, 524-527)
        var x = Parsers.match((Character c) -> c == 'x');
        assertThrows(UnsupportedOperationException.class, () -> SafeParser.parseSafe(x, new TextSource("x"), 0, new ArrayList<Object>(), 0));
        assertThrows(UnsupportedOperationException.class, () -> SafeParser.parseSafe(Parsers.not(x), new TextSource("y"), 0, new ArrayList<Object>(), 0));
        assertThrows(UnsupportedOperationException.class, () -> SafeParser.parseSafe(Parsers.fails(x), new TextSource("y"), 0, new ArrayList<Object>(), 0));
    }

    @Test
    void zeroOrOneSuccessIsDroppedByStackSafeParser() {
        // PORT-BUG mirror (SafeParse.cs:684-698): the item is produced but its length is not added.
        var x = Parsers.zeroOrOne(Parsers.match((Character c) -> c == 'x', c -> "x"));
        var directOut = new ArrayList<Object>();
        assertEquals(1, x.parse(new TextSource("x"), 0, directOut, 0));
        assertEquals(List.of("x"), directOut);
        var safeOut = new ArrayList<Object>();
        assertEquals(0, SafeParser.parseSafe(x, new TextSource("x"), 0, safeOut, 0));
        assertEquals(List.of("x"), safeOut);
    }
}
