// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for SyntaxParsers (Parser/SyntaxParsers.cs): tokens, missing tokens, token merging.

package org.graylog.kusto.language.parsing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.DiagnosticFacts;
import org.graylog.kusto.language.editor.CompletionItem;
import org.graylog.kusto.language.editor.CompletionKind;
import org.graylog.kusto.language.editor.CompletionPriority;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.graylog.kusto.language.syntax.SyntaxToken;
import org.junit.jupiter.api.Test;

class SyntaxParsersTest {
    static ArraySource<LexicalToken> lex(String text) {
        return new ArraySource<LexicalToken>(Arrays.asList(TokenParser.parseTokens(text)));
    }

    static List<String> messages(SyntaxToken token) {
        var result = new ArrayList<String>();
        for (Diagnostic d : token.syntaxDiagnostics()) {
            result.add(d.message());
        }
        return result;
    }

    @Test
    void tokenByKind() {
        var comma = SyntaxParsers.token(SyntaxKind.CommaToken);
        var ok = comma.parse(lex(","), 0);
        assertEquals(1, ok.length());
        assertEquals(SyntaxKind.CommaToken, ok.value().kind());
        assertFalse(comma.parse(lex("x"), 0).succeeded());

        assertEquals("','", comma.tag());
        assertEquals("and", SyntaxParsers.token(SyntaxKind.AndKeyword).tag());
        assertEquals("',' | ';'", SyntaxParsers.token(List.of(SyntaxKind.CommaToken, SyntaxKind.SemicolonToken)).tag());
        assertEquals("foo | ','", SyntaxParsers.tokenText(List.of("foo", ",")).tag());
    }

    @Test
    void tokenCompletionAnnotations() {
        var and = (CompletionItem) SyntaxParsers.token(SyntaxKind.AndKeyword).annotations().get(0);
        assertEquals(CompletionKind.Keyword, and.kind());
        assertEquals("and", and.displayText());
        assertEquals(CompletionPriority.Normal, and.priority());

        var comma = (CompletionItem) SyntaxParsers.token(SyntaxKind.CommaToken).annotations().get(0);
        assertEquals(CompletionKind.Punctuation, comma.kind());

        var foo = (CompletionItem) SyntaxParsers.token("foo").annotations().get(0);
        assertEquals(CompletionKind.Syntax, foo.kind());
        assertEquals("foo", foo.displayText());

        // an empty params array gets the default item
        var defaulted = SyntaxParsers.token(SyntaxKind.CommaToken, new CompletionItem[0]).annotations();
        assertEquals(1, defaulted.size());

        // text starting with '_' produces no completion item, but WithCompletion(null) still adds a null annotation (upstream quirk)
        assertEquals(Arrays.asList((Object) null), SyntaxParsers.token("_hidden").annotations());

        // the list forms drop null items
        assertEquals(2, SyntaxParsers.token(List.of(SyntaxKind.CommaToken, SyntaxKind.SemicolonToken)).annotations().size());
        assertEquals(1, SyntaxParsers.tokenText(List.of("foo", "_bar")).annotations().size());
        assertEquals(0, SyntaxParsers.hiddenToken("foo").annotations().size());
    }

    @Test
    void requiredTokenProducesMissingTokensWithDiagnostics() {
        var missingComma = SyntaxParsers.requiredToken(SyntaxKind.CommaToken).parse(lex("x"), 0);
        assertEquals(0, missingComma.length());
        assertTrue(missingComma.value().isMissing());
        assertEquals(SyntaxKind.CommaToken, missingComma.value().kind());
        assertEquals(List.of(DiagnosticFacts.getTokenExpected(SyntaxKind.CommaToken).message()), messages(missingComma.value()));
        assertEquals(List.of("Expected: ,"), messages(missingComma.value()));

        var missingName = SyntaxParsers.requiredToken(SyntaxKind.IdentifierToken).parse(lex(","), 0).value();
        assertTrue(missingName.isMissing());
        assertEquals(SyntaxKind.IdentifierToken, missingName.kind());
        assertEquals(List.of(DiagnosticFacts.getMissingName().message()), messages(missingName));

        var missingText = SyntaxParsers.requiredToken("foo").parse(lex(","), 0).value();
        assertEquals(SyntaxKind.IdentifierToken, missingText.kind());
        assertEquals(List.of("Expected: foo"), messages(missingText));

        var missingKnownText = SyntaxParsers.requiredToken(",").parse(lex("x"), 0).value();
        assertEquals(SyntaxKind.CommaToken, missingKnownText.kind());
        assertEquals(List.of("Expected: ,"), messages(missingKnownText));

        var kinds = List.of(SyntaxKind.CommaToken, SyntaxKind.SemicolonToken);
        var missingOneOf = SyntaxParsers.requiredToken(kinds).parse(lex("x"), 0).value();
        assertEquals(SyntaxKind.CommaToken, missingOneOf.kind());
        assertEquals(List.of(DiagnosticFacts.getTokenExpected(kinds).message()), messages(missingOneOf));

        var texts = List.of("foo", "bar");
        var missingOneOfText = SyntaxParsers.requiredTokenText(texts).parse(lex("x"), 0).value();
        assertEquals(SyntaxKind.IdentifierToken, missingOneOfText.kind());
        assertEquals(List.of(DiagnosticFacts.getTokenExpected(texts).message()), messages(missingOneOfText));

        // present tokens are produced as-is
        var present = SyntaxParsers.requiredToken(SyntaxKind.CommaToken).parse(lex(","), 0);
        assertEquals(1, present.length());
        assertFalse(present.value().isMissing());

        // an explicit diagnostic wins
        var dx = DiagnosticFacts.getMissingText("thing");
        assertEquals(List.of(dx.message()), messages(SyntaxParsers.createMissingToken(SyntaxKind.CommaToken, dx)));
        assertEquals(List.of(dx.message()), messages(SyntaxParsers.createMissingToken("foo", dx)));
    }

    @Test
    void matchesText() {
        assertEquals(3, SyntaxParsers.matchesText(lex("a-b"), 0, "a-b"));
        assertEquals(1, SyntaxParsers.matchesText(lex("a-b"), 0, "a"));
        assertEquals(-3, SyntaxParsers.matchesText(lex("a-b"), 0, "a-c"));
        // inner trivia stops the match
        assertEquals(-2, SyntaxParsers.matchesText(lex("a - b"), 0, "a-b"));
        // a token longer than the remaining text stops the match
        assertEquals(-1, SyntaxParsers.matchesText(lex("abc"), 0, "ab"));
        // the first token may have trivia
        assertEquals(3, SyntaxParsers.matchesText(lex(" a-b"), 0, "a-b"));
    }

    @Test
    void produceSyntaxTokenMergesAdjacentTokens() {
        var merged = SyntaxParsers.produceSyntaxToken(lex(" a-b"), 0, 3);
        assertEquals(SyntaxKind.IdentifierToken, merged.kind());
        assertEquals("a-b", merged.text());
        assertEquals(" ", merged.trivia());

        // refuses when an inner token has trivia
        assertNull(SyntaxParsers.produceSyntaxToken(lex("a - b"), 0, 3));
        assertNull(SyntaxParsers.produceSyntaxToken(lex("! ="), 0, 2));

        // a single token is produced from the lexical token, keeping its kind
        assertEquals(SyntaxKind.BangEqualToken, SyntaxParsers.produceSyntaxToken(lex("!="), 0, 1).kind());
        assertEquals(SyntaxKind.BangEqualToken, SyntaxParsers.produceSyntaxToken(lex("!="), 0, 1, SyntaxKind.BangEqualToken).kind());

        // assigning a kind works for Identifier and Other categories only
        var asIdentifier = SyntaxParsers.produceSyntaxToken(lex("!="), 0, 1, SyntaxKind.IdentifierToken);
        assertEquals(SyntaxKind.IdentifierToken, asIdentifier.kind());
        assertEquals("!=", asIdentifier.text());

        var asOther = SyntaxParsers.produceSyntaxToken(lex("a-b"), 0, 3, SyntaxKind.DirectiveToken);
        assertEquals(SyntaxKind.DirectiveToken, asOther.kind());
        assertEquals("a-b", asOther.text());

        var ex = assertThrows(IllegalStateException.class, () -> SyntaxParsers.produceSyntaxToken(lex("a-b"), 0, 3, SyntaxKind.AndKeyword));
        assertEquals("Cannot produce syntax token for kind: AndKeyword", ex.getMessage());

        // the text overload uses the given text, not the tokens' text
        assertEquals("xyz", SyntaxParsers.produceSyntaxToken(lex("a-b"), 0, 3, "xyz").text());

        // past the end: Peek returns null
        assertNull(SyntaxParsers.produceSyntaxToken(new ArraySource<LexicalToken>(List.of()), 0, 1));
    }

    @Test
    void combinedTokenText() {
        assertEquals("a - b", SyntaxParsers.getCombinedTokenText(lex("a - b"), 0, 3));
        assertEquals("a-b", SyntaxParsers.getCombinedTokenText(lex("a - b"), 0, 3, false));
        assertEquals("a", SyntaxParsers.getCombinedTokenText(lex(" a"), 0, 1));
    }

    @Test
    void matchTextAndHiddenTokens() {
        var aMinusB = SyntaxParsers.token("a-b");
        var r = aMinusB.parse(lex("a-b"), 0);
        assertEquals(3, r.length());
        assertEquals("a-b", r.value().text());
        assertFalse(aMinusB.parse(lex("a - b"), 0).succeeded());

        var oneOf = SyntaxParsers.tokenText(List.of("foo", "a-b"));
        assertEquals(3, oneOf.parse(lex("a-b"), 0).length());

        var hidden = SyntaxParsers.hiddenToken(List.of("x", "b"));
        assertEquals(1, hidden.parse(lex("b"), 0).length());
        assertFalse(hidden.parse(lex("c"), 0).succeeded());
        assertEquals(1, SyntaxParsers.hiddenToken(SyntaxKind.CommaToken).parse(lex(","), 0).length());
    }

    @Test
    void separatedLists() {
        var name = SyntaxParsers.token(SyntaxKind.IdentifierToken);
        var commaList = SyntaxParsers.commaList(name, (s, i) -> SyntaxParsers.createMissingToken(SyntaxKind.IdentifierToken));
        var list = commaList.parse(lex("a, b, c"), 0).value();
        assertEquals(3, list.size());
        assertEquals("b", list.get(1).element().text());
        assertEquals(SyntaxKind.CommaToken, list.get(0).separator().kind());
        assertNull(list.get(2).separator());

        var oneOrMore = SyntaxParsers.oneOrMoreCommaList(name, (s, i) -> SyntaxParsers.createMissingToken(SyntaxKind.IdentifierToken));
        var two = oneOrMore.parse(lex("a, b"), 0).value();
        assertEquals(2, two.size());
        var none = oneOrMore.parse(lex(""), 0).value();
        assertEquals(1, none.size());
        assertTrue(none.get(0).element().isMissing());

        var made = SyntaxParsers.<SyntaxToken>makeSeparatedList(SyntaxToken.identifier("", "a"), SyntaxToken.punctuation("", SyntaxKind.CommaToken), SyntaxToken.identifier("", "b"));
        assertEquals(2, made.size());
        assertEquals(0, SyntaxParsers.<SyntaxToken>makeSeparatedList((List<Object>) null).size());

        var plain = SyntaxParsers.list(name).parse(lex("a b c"), 0).value();
        assertEquals(3, plain.size());
    }

    @Test
    void parseAllFirstAndScanFirst() {
        var name = SyntaxParsers.token(SyntaxKind.IdentifierToken);
        var all = SyntaxParsers.parseAll(name, "a b c");
        assertEquals(3, all.size());
        assertEquals("c", all.get(2).text());
        assertEquals("a", SyntaxParsers.parseFirst(name, "a b").text());
        assertEquals(1, SyntaxParsers.scanFirst(name, "a b"));
        assertTrue(SyntaxParsers.scanFirst(name, ", b") < 0);

        assertEquals(1, SyntaxParsers.EndOfCommaList.scan(lex(")"), 0));
        assertEquals(1, SyntaxParsers.EndOfText.scan(lex(""), 0));
        assertEquals(1, SyntaxParsers.AnyToken.scan(lex("x"), 0));
        assertTrue(SyntaxParsers.AnyTokenButEnd.scan(lex(""), 0) < 0);
    }
}
