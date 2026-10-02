// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: Parser/Parser2 base members, ParseResult, sources and visitors (Parser.cs, ParseResult.cs, ArraySource.cs, LimitSource.cs, ParserVisitors.cs).

package org.graylog.kusto.language.parsing.combinators;

import static org.graylog.kusto.language.parsing.combinators.CombinatorTestSupport.parse;
import static org.graylog.kusto.language.parsing.combinators.CombinatorTestSupport.src;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.graylog.kusto.language.parsing.ApplyKind;
import org.graylog.kusto.language.parsing.ApplyParser;
import org.graylog.kusto.language.parsing.ArraySource;
import org.graylog.kusto.language.parsing.ElementAndSeparator;
import org.graylog.kusto.language.parsing.IsParentVisitor;
import org.graylog.kusto.language.parsing.LexicalToken;
import org.graylog.kusto.language.parsing.LimitSource;
import org.graylog.kusto.language.parsing.ParseResult;
import org.graylog.kusto.language.parsing.Parser;
import org.graylog.kusto.language.parsing.Parser2;
import org.graylog.kusto.language.parsing.Parsers;
import org.graylog.kusto.language.parsing.Source;
import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.junit.jupiter.api.Test;

class ParserBaseTest {
    @Test
    void withTagClonesOnlyOnChange() {
        Parser<Character> p = Parsers.match('a');
        assertEquals("", p.tag());
        assertSame(p, p.withTag(null));
        assertSame(p, p.withTag(""));
        Parser<Character> tagged = p.withTag("A");
        assertNotSame(p, tagged);
        assertEquals("A", tagged.tag());
        assertEquals("", p.tag());
        assertSame(tagged, tagged.withTag("A"));
        assertEquals(1, tagged.scan(src("a"), 0));
        assertEquals(p.getClass(), tagged.getClass());
    }

    @Test
    void withIsHiddenAndHideKeepTagAndAnnotations() {
        Parser<Character> p = Parsers.match('a').withTag("A").withAnnotations(List.of("note"));
        assertFalse(p.isHidden());
        assertSame(p, p.withIsHidden(false));
        Parser<Character> hidden = p.hide();
        assertTrue(hidden.isHidden());
        assertEquals("A", hidden.tag());
        assertEquals(List.of("note"), hidden.annotations());
        Parser<Character> retagged = hidden.withTag("B");
        assertTrue(retagged.isHidden());
        assertEquals(List.of("note"), retagged.annotations());
    }

    @Test
    void withAnnotationsComparesByReference() {
        Parser<Character> p = Parsers.match('a');
        assertSame(EmptyReadOnlyList.Instance, p.annotations());
        assertSame(p, p.withAnnotations(List.of()));           // ToReadOnly(empty) is the shared empty list
        Parser<Character> a1 = p.withAnnotations(List.of("x"));
        assertNotSame(p, a1);
        assertNotSame(a1, a1.withAnnotations(a1.annotations().subList(0, 1))); // a new list, even if equal
    }

    @Test
    void parser2CovariantWithMembersAndCast() {
        Parser2<Character, String> p = Parsers.match((Character c) -> c == 'a', (Character c) -> "A");
        Parser2<Character, String> t = p.withTag("T").withIsHidden(true).hide().withAnnotations(List.of(1));
        assertEquals("T", t.tag());
        assertEquals("A", parse(t, "a").value());
        Parser2<Character, Object> cast = p.withTag("C").<Object>cast();
        assertEquals("C", cast.tag());
        assertEquals("A", parse(cast, "a").value());
        assertEquals(-1, parse(cast, "b").length());
    }

    @Test
    void descriptionPrefixesTag() {
        Parser<Character> p = Parsers.match('a').withTag("A");
        assertTrue(p.description().startsWith("A: "), p.description());
        assertSame(p.description(), p.description());
    }

    @Test
    void flagsAndChildren() {
        Parser<Character> a = Parsers.match('a');
        Parser<Character> b = Parsers.match('b');
        Parser<Character> seq = Parsers.sequence(a, b);
        assertTrue(seq.isSequence());
        assertEquals(2, seq.childParserCount());
        assertSame(b, seq.getChildParser(1));
        assertNull(seq.getChildParser(2));
        assertEquals(1, seq.getChildParserIndex(b));
        assertEquals(-1, seq.getChildParserIndex(Parsers.match('b')));
        assertTrue(Parsers.first(a, b).isAlternation());
        assertTrue(Parsers.best(a, b).isAlternation());
        assertTrue(Parsers.zeroOrMore(a).isOptional());
        assertTrue(Parsers.zeroOrMore(a).isRepetition());
        assertTrue(Parsers.oneOrMore(a).isRepetition());
        assertTrue(a.isMatch());
        assertTrue(Parsers.not(a).isNegation());
        assertTrue(Parsers.fails(a).isNegation());
        assertTrue(Parsers.if_(a, b).isConditional());
        Parser2<Character, String> s = Parsers.match((Character c) -> true, (Character c) -> "s");
        assertTrue(Parsers.forward(s).isForward());
        assertSame(s, Parsers.forward(s).getChildParser(0));
        assertTrue(Parsers.optional(s).isOptional());
        assertTrue(Parsers.limit(a, s).isConditional());
        assertFalse(s.isRequired());
        ApplyParser<Character, String, String> apply = (ApplyParser<Character, String, String>) Parsers.applyZeroOrMore(s, left -> Parsers.rule(left, x -> x));
        assertEquals(ApplyKind.ZeroOrMore, apply.applyKind());
        assertSame(s, apply.leftParser());
    }

    @Test
    void parseResultIsAValueRecord() {
        ParseResult<String> r = new ParseResult<>(3, "x");
        assertEquals(3, r.length());
        assertEquals("x", r.value());
        assertTrue(r.succeeded());
        assertTrue(new ParseResult<>(0, null).succeeded());
        assertFalse(new ParseResult<>(-1, null).succeeded());
        assertEquals(new ParseResult<>(3, "x"), r);
        assertEquals(new ParseResult<>(3, "x").hashCode(), r.hashCode());
        assertNotEquals(new ParseResult<>(2, "x"), r);
    }

    @Test
    void elementAndSeparatorRejectsNullElement() {
        assertThrows(NullPointerException.class, () -> new ElementAndSeparator<String, String>(null, ","));
        assertNull(new ElementAndSeparator<String, String>("e").separator());
    }

    @Test
    void arraySourceClampsAndYieldsDefaultPastEnd() {
        List<Character> chars = Arrays.asList('a', 'b', 'c', 'd');
        Source<Character> s = new ArraySource<>(chars, 1, 2);
        assertEquals('b', s.peek(0));
        assertEquals('c', s.peek(1));
        assertNull(s.peek(2));
        assertFalse(s.isEnd(1));
        assertTrue(s.isEnd(2));
        Source<Character> clamped = new ArraySource<>(chars, 2, 100);
        assertTrue(clamped.isEnd(2));
        assertFalse(clamped.isEnd(1));
        assertTrue(new ArraySource<>(chars, 1).isEnd(3));
        assertFalse(new ArraySource<>(chars).isEnd());
    }

    @Test
    void limitSourceEndsAtLimit() {
        Source<Character> s = new LimitSource<>(src("abcd"), 2);
        assertFalse(s.isEnd(1));
        assertTrue(s.isEnd(2));
        assertEquals('c', s.peek(2));   // Peek is not limited
        assertTrue(new LimitSource<>(src("a"), 5).isEnd(1));
    }

    @Test
    void isParentVisitorMirrorsUpstreamIncludingFailsBug() {
        Parser<LexicalToken> a = Parsers.<LexicalToken>match((LexicalToken t) -> true);
        Parser<LexicalToken> b = Parsers.<LexicalToken>match((LexicalToken t) -> false);
        assertTrue(Parsers.sequence(a, b).accept(IsParentVisitor.Instance, b));
        assertFalse(Parsers.sequence(a).accept(IsParentVisitor.Instance, b));
        assertTrue(Parsers.not(a).accept(IsParentVisitor.Instance, a));
        // PORT-BUG mirror (D20): VisitFails compares with !=
        assertFalse(Parsers.fails(a).accept(IsParentVisitor.Instance, a));
        assertTrue(Parsers.fails(a).accept(IsParentVisitor.Instance, b));
        assertFalse(a.accept(IsParentVisitor.Instance, a));
    }
}
