// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: behaviour of the Parsers factories and combinator parsers (Parsers.cs, Parsers/*.cs).

package org.graylog.kusto.language.parsing.combinators;

import static org.graylog.kusto.language.parsing.combinators.CombinatorTestSupport.assertAgree;
import static org.graylog.kusto.language.parsing.combinators.CombinatorTestSupport.parse;
import static org.graylog.kusto.language.parsing.combinators.CombinatorTestSupport.parseList;
import static org.graylog.kusto.language.parsing.combinators.CombinatorTestSupport.scan;
import static org.graylog.kusto.language.parsing.combinators.CombinatorTestSupport.src;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.graylog.kusto.language.parsing.ElementAndSeparator;
import org.graylog.kusto.language.parsing.OffsetValue;
import org.graylog.kusto.language.parsing.ParseResult;
import org.graylog.kusto.language.parsing.Parser;
import org.graylog.kusto.language.parsing.Parser2;
import org.graylog.kusto.language.parsing.Parsers;
import org.graylog.kusto.language.parsing.TextSource;
import org.graylog.kusto.language.utils.dotnet.EqualityComparer;
import org.junit.jupiter.api.Test;

class ParsersTest {
    private static Parser2<Character, String> ch(char c) {
        return Parsers.match((Character x) -> x == c, (Character x) -> String.valueOf(x));
    }

    private static Parser2<Character, String> digit() {
        return Parsers.match((Character x) -> Character.isDigit(x), (Character x) -> String.valueOf(x));
    }

    // ---------------------------------------------------------------- Match

    @Test
    void matchCharAndPredicate() {
        Parser<Character> a = Parsers.match('a');
        assertEquals(1, assertAgree(a, "abc"));
        assertEquals(-1, assertAgree(a, "bc"));
        assertEquals(-1, assertAgree(a, ""));
        assertEquals(1, assertAgree(Parsers.match('A', true), "abc"));
        assertEquals(-1, assertAgree(Parsers.match('A'), "abc"));
    }

    @Test
    void matchStringEncodesFailurePositionWithComplement() {
        Parser<Character> abc = Parsers.match("abc");
        assertEquals(3, assertAgree(abc, "abcd"));
        assertEquals(~2, assertAgree(abc, "abd"));   // mismatch at index 2
        assertEquals(~0, assertAgree(abc, "xbc"));
        assertEquals(~2, assertAgree(abc, "ab"));    // past end: default(char) never matches
        assertEquals(3, assertAgree(Parsers.match("aBc", true), "AbC"));
        assertEquals(~1, assertAgree(Parsers.match("aBc", true), "AxC"));
        // TextSource takes the quick path and reports a plain -1
        assertEquals(-1, abc.scan(new TextSource("abd"), 0));
        assertEquals(3, abc.scan(new TextSource("abc"), 0));
    }

    @Test
    void matchItemSequenceAndAny() {
        assertEquals(1, assertAgree(Parsers.match((Character) 'q'), "q"));
        assertEquals(2, assertAgree(Parsers.matchSequence(Arrays.asList('a', 'b')), "abz"));
        assertEquals(~1, assertAgree(Parsers.matchSequence(Arrays.asList('a', 'b')), "az"));
        assertEquals(~1, assertAgree(Parsers.matchSequence(Arrays.asList('a', 'b')), "a"));
        assertEquals(1, assertAgree(Parsers.matchAny('x', 'y'), "y"));
        assertEquals(-1, assertAgree(Parsers.matchAny('x', 'y'), "z"));
        assertEquals(1, assertAgree(Parsers.<Character>any(), "z"));
        assertEquals(-1, assertAgree(Parsers.<Character>any(), ""));
        assertEquals("<any>", Parsers.Any.tag());
    }

    @Test
    void matchAnyIgnoresComparerAsUpstream() {
        // PORT-BUG mirror: MatchAny builds a HashSet with the comparer but tests items.Contains (default equality)
        EqualityComparer<Character> everythingEqual = new EqualityComparer<>() {
            @Override
            public boolean equals(Character a, Character b) {
                return true;
            }

            @Override
            public int hashCode(Character a) {
                return 0;
            }
        };
        assertEquals(-1, scan(Parsers.matchAny(Arrays.asList('x'), everythingEqual), "z"));
        assertEquals(1, scan(Parsers.match((Character) 'x', everythingEqual), "z"));
    }

    @Test
    void matchProducerListVsResultQuirk() {
        // MatchParser<TInput,TOutput>.Parse(source, start) treats length > 0 as success; list Parse uses length >= 0
        Parser2<Character, Integer> v = Parsers.value(() -> 42);
        ParseResult<Integer> r = parse(v, "abc");
        assertEquals(0, r.length());
        assertNull(r.value());
        List<Object> out = new ArrayList<>();
        assertEquals(0, parseList(v, "abc", out));
        assertEquals(List.of(42), out);
    }

    // ---------------------------------------------------------------- Sequence / Rule / First / Best

    @Test
    void sequenceFailureLengthIncludesConsumedPrefix() {
        Parser<Character> seq = Parsers.sequence(Parsers.match('a'), Parsers.match('b'), Parsers.match('c'));
        assertEquals(3, assertAgree(seq, "abc"));
        assertEquals(-1 - 2, assertAgree(seq, "abx"));   // n - len
        assertEquals(-1, assertAgree(seq, "x"));
        assertEquals(2, assertAgree(Parsers.and(Parsers.match('a'), Parsers.match('b')), "ab"));
        assertEquals(1, assertAgree(Parsers.sequence(List.of(Parsers.match('a'))), "a"));
    }

    @Test
    void sequenceRestoresOutputOnFailure() {
        Parser<Character> seq = Parsers.sequence(ch('a'), ch('b'));
        List<Object> out = new ArrayList<>(List.of("keep"));
        assertEquals(-2, seq.parse(src("ax"), 0, out, 1));
        assertEquals(List.of("keep"), out);
        assertEquals(2, seq.parse(src("ab"), 0, out, 1));
        assertEquals(List.of("keep", "a", "b"), out);
    }

    @Test
    void ruleProducesFromChildValues() {
        Parser2<Character, String> r = Parsers.rule(ch('a'), ch('b'), ch('c'), (x, y, z) -> x + y + z);
        assertEquals(3, assertAgree(r, "abc"));
        assertEquals("abc", parse(r, "abc").value());
        List<Object> out = new ArrayList<>();
        assertEquals(3, parseList(r, "abc", out));
        assertEquals(List.of("abc"), out);

        ParseResult<String> failed = parse(r, "abx");
        assertEquals(-3, failed.length());
        assertNull(failed.value());
        assertFalse(failed.succeeded());
        assertEquals(-3, assertAgree(r, "abx"));

        Parser2<Character, String> r1 = Parsers.rule(ch('a'), x -> x.toUpperCase());
        assertEquals("A", parse(r1, "a").value());
        Parser2<Character, String> r9 = Parsers.rule(digit(), digit(), digit(), digit(), digit(), digit(), digit(), digit(), digit(),
            (a, b, c, d, e, f, g, h, i) -> a + b + c + d + e + f + g + h + i);
        assertEquals("123456789", parse(r9, "123456789").value());
        assertEquals(-9, assertAgree(r9, "12345678x"));
    }

    @Test
    void firstTakesFirstSuccessAndLongestFailure() {
        Parser<Character> f = Parsers.first(Parsers.match('x'), Parsers.match('a'));
        assertEquals(1, assertAgree(f, "a"));
        Parser<Character> g = Parsers.first(Parsers.match("xy"), Parsers.match("abc"));
        assertEquals(~2, assertAgree(g, "abz"));     // min of ~0 and ~2

        Parser2<Character, String> f2 = Parsers.first(
            Parsers.rule(ch('a'), ch('b'), (x, y) -> "ab"),
            Parsers.rule(ch('a'), x -> "a"));
        assertEquals("ab", parse(f2, "ab").value());
        assertEquals("a", parse(f2, "ac").value());
        List<Object> out = new ArrayList<>();
        assertEquals(1, parseList(f2, "ac", out));
        assertEquals(List.of("a"), out);
        assertEquals(-1, assertAgree(f2, "z"));
    }

    @Test
    void bestPicksLongestAndFirstOnTie() {
        Parser2<Character, String> first = Parsers.rule(ch('a'), x -> "first");
        Parser2<Character, String> second = Parsers.rule(ch('a'), x -> "second");
        Parser2<Character, String> longer = Parsers.rule(ch('a'), ch('b'), (x, y) -> "longer");

        assertEquals("first", parse(Parsers.best(first, second), "ab").value());
        assertEquals("longer", parse(Parsers.best(first, longer, second), "ab").value());
        assertEquals(2, assertAgree(Parsers.best(first, longer, second), "ab"));

        List<Object> out = new ArrayList<>();
        assertEquals(1, parseList(Parsers.best(first, second), "a", out));
        assertEquals(List.of("first"), out);

        // fnBetter breaks ties among equally long candidates
        @SuppressWarnings("unchecked")
        Parser2<Character, String>[] arr = new Parser2[] { first, second };
        Parser2<Character, String> better = Parsers.best(arr, (other, best) -> other.equals("second"));
        assertEquals("second", parse(better, "a").value());
        out.clear();
        assertEquals(1, parseList(better, "a", out));
        assertEquals(List.of("second"), out);
        // PORT-BUG mirror: the clone drops fnIsBetter
        assertEquals("first", parse(better.withTag("t"), "a").value());

        Parser<Character> b = Parsers.best(Parsers.match("xy"), Parsers.match("abc"));
        assertEquals(3, assertAgree(b, "abc"));
        assertEquals(Parsers.or(Parsers.match("xy"), Parsers.match("abc")).scan(src("ab"), 0), b.scan(src("ab"), 0));
    }

    @Test
    void bestFailureLengthsAsUpstream() {
        Parser<Character> b = Parsers.best(Parsers.match("xy"), Parsers.match("abc"));
        assertEquals(~2, scan(b, "abz"));
        // PORT-BUG mirror: the list Parse returns maxLength (-1) on failure while Scan returns the min
        assertEquals(-1, parseList(b, "abz", new ArrayList<>()));

        Parser2<Character, String> b2 = Parsers.best(
            Parsers.convert(Parsers.match("xy"), "xy"),
            Parsers.convert(Parsers.match("abc"), "abc"));
        assertEquals(~2, scan(b2, "abz"));
        assertEquals(~2, parse(b2, "abz").length());
        assertEquals(-1, parseList(b2, "abz", new ArrayList<>()));
    }

    // ---------------------------------------------------------------- repetition / optional / required

    @Test
    void zeroOrMoreOneOrMoreOptional() {
        assertEquals(2, assertAgree(Parsers.zeroOrMore(Parsers.match('a')), "aab"));
        assertEquals(0, assertAgree(Parsers.zeroOrMore(Parsers.match('a')), "b"));
        assertEquals(2, assertAgree(Parsers.oneOrMore(Parsers.match('a')), "aab"));
        assertEquals(-1, assertAgree(Parsers.oneOrMore(Parsers.match('a')), "b"));
        assertEquals(1, assertAgree(Parsers.optional(Parsers.match('a')), "aab"));
        assertEquals(0, assertAgree(Parsers.optional(Parsers.match('a')), "b"));
        assertEquals(1, assertAgree(Parsers.zeroOrOne(Parsers.match('a')), "aa"));
        // zero-length progress terminates the loop
        assertEquals(0, assertAgree(Parsers.zeroOrMore(Parsers.optional(Parsers.match('a'))), "b"));
        assertEquals(2, assertAgree(Parsers.oneOrMore(Parsers.optional(Parsers.match('a'))), "aab"));
    }

    @Test
    void listProducingRepetitions() {
        assertEquals(List.of("a", "a"), parse(Parsers.zeroOrMoreList(ch('a')), "aab").value());
        assertEquals(List.of(), parse(Parsers.zeroOrMoreList(ch('a')), "b").value());
        assertEquals(List.of("a", "a"), parse(Parsers.oneOrMoreList(ch('a')), "aa").value());
        assertEquals(-1, parse(Parsers.oneOrMoreList(ch('a')), "b").length());
        assertEquals("2", parse(Parsers.oneOrMore(ch('a'), l -> String.valueOf(l.size())), "aab").value());
        assertEquals("0", parse(Parsers.zeroOrMore(ch('a'), l -> String.valueOf(l.size())), "b").value());
        assertEquals(List.of("a", "a"), parse(Parsers.list(ch('a'), true, l -> new ArrayList<>(l)), "aa").value());
        assertEquals(-1, parse(Parsers.list(ch('a'), true, l -> new ArrayList<>(l)), "b").length());
        assertEquals(List.of(), parse(Parsers.list(ch('a'), false, l -> new ArrayList<>(l)), "b").value());
        // with a missing-element producer a required first element is synthesised
        Parser2<Character, List<String>> withMissing = Parsers.list(ch('a'), (s, start) -> "<missing@" + start + ">", true, l -> new ArrayList<>(l));
        assertEquals(List.of("<missing@0>"), parse(withMissing, "b").value());
        assertEquals(0, parse(withMissing, "b").length());
    }

    @Test
    void optionalProducesDefaultOrProducerValue() {
        ParseResult<String> r = parse(Parsers.optional(ch('a')), "b");
        assertEquals(0, r.length());
        assertNull(r.value());
        assertEquals("dflt", parse(Parsers.optional(ch('a'), () -> "dflt"), "b").value());
        assertEquals("a", parse(Parsers.optional(ch('a'), () -> "dflt"), "a").value());
        List<Object> out = new ArrayList<>();
        assertEquals(0, parseList(Parsers.optional(ch('a'), () -> "dflt"), "b", out));
        assertEquals(List.of("dflt"), out);
        assertEquals(0, assertAgree(Parsers.optional(ch('a')), "b"));
        assertEquals(1, assertAgree(Parsers.optional(ch('a')), "a"));
    }

    @Test
    void requiredProducesMissingValueAtPosition() {
        Parser2<Character, String> req = Parsers.required(ch('a'), (s, start) -> "missing@" + start);
        ParseResult<String> r = parse(req, "b");
        assertEquals(0, r.length());
        assertEquals("missing@0", r.value());
        List<Object> out = new ArrayList<>();
        assertEquals(0, req.parse(src("xb"), 1, out, 0));
        assertEquals(List.of("missing@1"), out);
        assertEquals(0, assertAgree(req, "b"));
        assertEquals(1, assertAgree(req, "a"));
        assertEquals("a", parse(req, "a").value());
        assertEquals("m", parse(Parsers.required(ch('a'), () -> "m"), "z").value());
        assertTrue(req.isRequired());
    }

    // ---------------------------------------------------------------- Convert / Map / Produce

    @Test
    void convertVariants() {
        assertEquals(3, parse(Parsers.convert(Parsers.match("abc"), (s, start, len) -> len), "abc").value());
        assertEquals("3:[a, b, c]", parse(Parsers.convertList(Parsers.match("abc"), (List<Character> l) -> l.size() + ":" + l), "abc").value());
        assertEquals("AB", parse(Parsers.convertText(Parsers.match("ab"), s -> s.toUpperCase()), "ab").value());
        assertEquals("AB", Parsers.convertText(Parsers.match("ab"), s -> s.toUpperCase()).parse(new TextSource("abz"), 0).value());
        assertEquals("first=a", parse(Parsers.convert(Parsers.match("ab"), (Character c) -> "first=" + c), "ab").value());
        assertEquals("VAL", parse(Parsers.convert(Parsers.match('a'), "VAL"), "a").value());
        assertEquals(3, parse(Parsers.count(Parsers.match("abc")), "abc").value());
        ParseResult<String> failed = parse(Parsers.convert(Parsers.match("abc"), "VAL"), "abx");
        assertEquals(~2, failed.length());
        assertNull(failed.value());
        assertEquals(~2, assertAgree(Parsers.convert(Parsers.match("abc"), "VAL"), "abx"));
    }

    @Test
    void textAndOffsetOverTextSource() {
        assertEquals("ab", Parsers.text(Parsers.match("ab")).parse(new TextSource("abc"), 0).value());
        assertEquals("bc", Parsers.text("bc").parse(new TextSource("abc", 1, 2), 0).value());
        assertEquals(new OffsetValue<>(0, "ab"), Parsers.textAndOffset(Parsers.match("ab")).parse(new TextSource("abc"), 0).value());
    }

    @Test
    void mapTakesLongestKeyWithValue() {
        List<Map.Entry<Iterable<Character>, Integer>> entries = new ArrayList<>();
        entries.add(new AbstractMap.SimpleEntry<>(Arrays.asList('a'), 1));
        entries.add(new AbstractMap.SimpleEntry<>(Arrays.asList('a', 'b'), 2));
        entries.add(new AbstractMap.SimpleEntry<>(Arrays.asList('a', 'b', 'c', 'd'), 4));
        Parser2<Character, Integer> map = Parsers.map(entries);
        assertEquals(2, parse(map, "abc").value());
        assertEquals(2, assertAgree(map, "abc"));
        assertEquals(4, parse(map, "abcd").value());
        assertEquals(-1, assertAgree(map, "x"));
        assertNull(parse(map, "x").value());

        List<Map.Entry<Iterable<Character>, Supplier<Integer>>> lazy = new ArrayList<>();
        lazy.add(new AbstractMap.SimpleEntry<>(Arrays.asList('z'), () -> 26));
        assertEquals(26, parse(Parsers.mapList(lazy), "z").value());
    }

    @Test
    void produceListVsObjectList() {
        Parser2<Character, String> joined = Parsers.produce(Parsers.sequence(ch('a'), ch('b')), (List<Object> l) -> l.toString());
        assertEquals("[a, b]", parse(joined, "ab").value());
        assertEquals(-2, assertAgree(joined, "ax"));
        Parser2<Character, Integer> typed = Parsers.produceList(Parsers.sequence(ch('a'), ch('b')), (List<String> l) -> l.size());
        assertEquals(2, parse(typed, "ab").value());
        List<Object> out = new ArrayList<>(List.of("keep"));
        assertEquals(-2, typed.parse(src("ax"), 0, out, 1));
        assertEquals(List.of("keep"), out);
    }

    @Test
    void separatedListProducesElementAndSeparatorPairs() {
        Parser2<Character, List<ElementAndSeparator<String, String>>> list =
            Parsers.list(ch('a'), ch(','), false, l -> new ArrayList<>(l));
        List<ElementAndSeparator<String, String>> v = parse(list, "a,a,a").value();
        assertEquals(List.of(new ElementAndSeparator<>("a", ","), new ElementAndSeparator<>("a", ","), new ElementAndSeparator<>("a")), v);
        assertEquals(5, assertAgree(list, "a,a,a"));
        assertEquals(List.of(), parse(list, "x").value());

        Parser2<Character, List<ElementAndSeparator<String, String>>> withMissing =
            Parsers.list(ch('a'), ch(','), (s, start) -> "?", null, null, false, false, l -> new ArrayList<>(l));
        assertEquals(List.of(new ElementAndSeparator<>("a", ","), new ElementAndSeparator<>("?", ","), new ElementAndSeparator<>("a")),
            parse(withMissing, "a,,a").value());

        Parser2<Character, List<ElementAndSeparator<String, String>>> trailing =
            Parsers.list(ch('a'), ch(','), null, null, null, true, true, l -> new ArrayList<>(l));
        assertEquals(List.of(new ElementAndSeparator<>("a", ","), new ElementAndSeparator<>("a", ",")), parse(trailing, "a,a,").value());
    }

    // ---------------------------------------------------------------- If / Not / Fails / Limit

    @Test
    void ifNotFailsLimit() {
        Parser<Character> ifp = Parsers.if_(Parsers.match("ab"), Parsers.match('a'));
        assertEquals(1, assertAgree(ifp, "ab"));
        assertEquals(~1, assertAgree(ifp, "ax"));
        Parser2<Character, String> if2 = Parsers.if_(Parsers.match("ab"), ch('a'));
        assertEquals("a", parse(if2, "ab").value());
        assertEquals(~1, assertAgree(if2, "ax"));

        assertEquals(1, assertAgree(Parsers.not(Parsers.match('a')), "b"));
        assertEquals(-1, assertAgree(Parsers.not(Parsers.match('a')), "a"));
        assertEquals(-1, assertAgree(Parsers.not(Parsers.match('a')), ""));

        assertEquals(0, assertAgree(Parsers.fails(Parsers.match('a')), "b"));
        assertEquals(-1, assertAgree(Parsers.fails(Parsers.match('a')), "a"));
        assertEquals(0, assertAgree(Parsers.fails(Parsers.match('a')), ""));

        Parser2<Character, List<Character>> anyList = Parsers.zeroOrMoreList(Parsers.match((Character c) -> true, (Character c) -> c));
        Parser2<Character, List<Character>> limited = Parsers.limit(Parsers.match("ab"), anyList);
        assertEquals(List.of('a', 'b'), parse(limited, "abcd").value());
        assertEquals(2, assertAgree(limited, "abcd"));
        assertEquals(-1, assertAgree(limited, "xbcd"));
        assertNull(parse(limited, "xbcd").value());
    }

    // ---------------------------------------------------------------- Apply

    @Test
    void applyZeroOrMoreIsLeftAssociative() {
        Parser2<Character, String> expr = Parsers.applyZeroOrMore(digit(),
            left -> Parsers.rule(left, ch('+'), digit(), (l, op, r) -> "(" + l + op + r + ")"));
        assertEquals("((1+2)+3)", parse(expr, "1+2+3").value());
        assertEquals(5, assertAgree(expr, "1+2+3"));
        assertEquals("1", parse(expr, "1+").value());
        assertEquals(1, assertAgree(expr, "1+"));
        assertEquals(-1, assertAgree(expr, "+"));
        List<Object> out = new ArrayList<>();
        assertEquals(5, parseList(expr, "1+2+3", out));
        assertEquals(List.of("((1+2)+3)"), out);
    }

    @Test
    void applyOneAndOptional() {
        Parser2<Character, String> once = Parsers.apply(digit(),
            left -> Parsers.rule(left, ch('+'), digit(), (l, op, r) -> l + op + r));
        assertEquals("1+2", parse(once, "1+2+3").value());
        assertEquals(3, assertAgree(once, "1+2+3"));
        assertEquals(-1 - 1, assertAgree(once, "1"));        // -leftLength + rightLength
        assertEquals(-1 - 2, assertAgree(once, "1+x"));      // right rule fails after '+'
        List<Object> out = new ArrayList<>(List.of("keep"));
        assertEquals(-2, once.parse(src("1"), 0, out, 1));
        assertEquals(List.of("keep"), out);

        Parser2<Character, String> opt = Parsers.applyOptional(digit(),
            left -> Parsers.rule(left, ch('+'), digit(), (l, op, r) -> "(" + l + op + r + ")"));
        assertEquals("(1+2)", parse(opt, "1+2+3").value());
        assertEquals("1", parse(opt, "1").value());
        assertEquals(1, assertAgree(opt, "1"));

        // a right parser that only maps the left value consumes nothing
        Parser2<Character, String> mapped = Parsers.apply(digit(), left -> Parsers.rule(left, (String l) -> l + "!"));
        assertEquals("1!", parse(mapped, "1").value());
        assertEquals(1, assertAgree(mapped, "1"));
    }
}
