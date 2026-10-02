// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: Describer (Combinators/Describer.cs) text forms, derived from the upstream writer rules.

package org.graylog.kusto.language.parsing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DescriberTest {
    static final Parser<Character> a = Parsers.match((Character ch) -> ch == 'a').withTag("a");
    static final Parser<Character> b = Parsers.match((Character ch) -> ch == 'b').withTag("b");
    static final Parser<Character> c = Parsers.match((Character ch) -> ch == 'c').withTag("c");
    static final Parser2<Character, String> a2 = Parsers.match((Character ch) -> ch == 'a', ch -> "a").withTag("a");
    static final Parser2<Character, String> b2 = Parsers.match((Character ch) -> ch == 'b', ch -> "b").withTag("b");

    @Test
    void sequencesAndAlternations() {
        assertEquals("a b", Describer.describe(Parsers.sequence(a, b)));
        assertEquals("a | b", Describer.describe(Parsers.first(a, b)));
        assertEquals("a | b", Describer.describe(Parsers.best(a, b)));
        // nested items with a different separator get parentheses
        assertEquals("a (b | c)", Describer.describe(Parsers.sequence(a, Parsers.first(b, c))));
        assertEquals("a | (b c)", Describer.describe(Parsers.first(a, Parsers.sequence(b, c))));
        // the same separator flattens
        assertEquals("a b c", Describer.describe(Parsers.sequence(a, Parsers.sequence(b, c))));
        assertEquals("a | b | c", Describer.describe(Parsers.first(a, Parsers.first(b, c))));
        assertEquals("a b", Describer.describe(Parsers.rule(a2, b2, (x, y) -> x + y)));
    }

    @Test
    void repetitionsAndOptionals() {
        assertEquals("[a]", Describer.describe(Parsers.optional(a)));
        assertEquals("[a]", Describer.describe(Parsers.optional(a2)));
        assertEquals("[a]", Describer.describe(Parsers.zeroOrOne(a)));
        assertEquals("{a}", Describer.describe(Parsers.zeroOrMore(a)));
        assertEquals("{a}+", Describer.describe(Parsers.oneOrMore(a)));
        assertEquals("{a b}", Describer.describe(Parsers.zeroOrMore(Parsers.sequence(a, b))));
        assertEquals("a {b | c}", Describer.describe(Parsers.sequence(a, Parsers.zeroOrMore(Parsers.first(b, c)))));
    }

    @Test
    void required() {
        var single = Parsers.required(Parsers.rule(a2, x -> x), () -> "missing");
        assertEquals("a!", Describer.describe(single));
        var pair = Parsers.required(Parsers.rule(a2, b2, (x, y) -> x + y), () -> "missing");
        assertEquals("(a b)!", Describer.describe(pair));
        assertEquals("a b", Describer.describe(pair, false));
        assertEquals("c (a b)!", Describer.describe(Parsers.sequence(c, pair)));
    }

    @Test
    void terms() {
        assertEquals("not(a)", Describer.describe(Parsers.not(a)));
        assertEquals("fails(a)", Describer.describe(Parsers.fails(a)));
        assertEquals("forward()", Describer.describe(Parsers.forward(() -> a2)));
        assertEquals("match()", Describer.describe(Parsers.match((Character ch) -> true)));
        assertEquals("match()", Describer.describe(Parsers.match((Character ch) -> true, ch -> ch)));
        assertEquals("map()", Describer.describe(Parsers.<Character, String>map(List.of(Map.entry(List.of('a'), "A")))));
        assertEquals("a b", Describer.describe(Parsers.convert(Parsers.sequence(a, b), "ab")));
        assertEquals("b", Describer.describe(Parsers.if_(a, b2)));
        assertEquals("a", Describer.describe(Parsers.limit(b, a2)));
        assertEquals("not(a) | fails(b)", Describer.describe(Parsers.first(Parsers.not(a), Parsers.fails(b))));
    }

    @Test
    void apply() {
        assertEquals("a b", Describer.describe(Parsers.apply(a2, left -> Parsers.rule(left, b2, (l, r) -> l + r))));
        assertEquals("a [b]", Describer.describe(Parsers.applyOptional(a2, left -> Parsers.rule(left, b2, (l, r) -> l + r))));
        assertEquals("a {b}", Describer.describe(Parsers.applyZeroOrMore(a2, left -> Parsers.rule(left, b2, (l, r) -> l + r))));
    }

    @Test
    void hiddenAndTagged() {
        assertEquals("a c", Describer.describe(Parsers.sequence(a, b.hide(), c)));
        assertEquals("a", Describer.describe(Parsers.sequence(a, b.hide())));
        assertEquals("", Describer.describe(Parsers.sequence(a, b).hide()));
        assertEquals("", Describer.describe(Parsers.optional(b.hide())));
        // a tag replaces the structure when describing a parent
        assertEquals("x c", Describer.describe(Parsers.sequence(Parsers.sequence(a, b).withTag("x"), c)));
        // Parser.description prefixes the tag
        assertEquals("seq: a b", Parsers.sequence(a, b).withTag("seq").description());
        assertEquals("a | b", Parsers.first(a, b).description());
    }
}
