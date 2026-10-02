// Ported from: src/Kusto.Language/Parser/SyntaxParsers.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BiFunction;

import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.DiagnosticFacts;
import org.graylog.kusto.language.ParseOptions;
import org.graylog.kusto.language.editor.CompletionItem;
import org.graylog.kusto.language.editor.CompletionKind;
import org.graylog.kusto.language.editor.CompletionPriority;
import org.graylog.kusto.language.editor.CompletionRank;
import org.graylog.kusto.language.syntax.SeparatedElement1;
import org.graylog.kusto.language.syntax.SyntaxCategory;
import org.graylog.kusto.language.syntax.SyntaxElement;
import org.graylog.kusto.language.syntax.SyntaxFacts;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.graylog.kusto.language.syntax.SyntaxList1;
import org.graylog.kusto.language.syntax.SyntaxToken;
import org.graylog.kusto.language.utils.Ensure;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Linq;
import org.graylog.kusto.language.utils.dotnet.Out;

// PORT: §3.10 D7 `using static Parsers<LexicalToken>`: calls go through the static generic Parsers methods,
// with explicitly typed lambda parameters where inference needs TInput = LexicalToken.

/// <summary>
/// Parser and Scanners for working with Kusto syntax.
/// </summary>
public final class SyntaxParsers
{
    private SyntaxParsers() // PORT: §3.5 static class
    {
    }

    /// <summary>
    /// Creates a missing <see cref="SyntaxToken"/> for a token that was expected to have the specified <see cref="SyntaxKind"/>.
    /// </summary>
    public static SyntaxToken createMissingToken(SyntaxKind kind, Diagnostic diagnostic)
    {
        var dx = diagnostic != null ? diagnostic : // PORT: §3.14 ??
            (kind == SyntaxKind.IdentifierToken ? DiagnosticFacts.getMissingName() : DiagnosticFacts.getTokenExpected(kind));
        return SyntaxToken.missing("", kind, Arrays.asList(new Diagnostic[] { dx })); // PORT: §3.17 array as IReadOnlyList
    }

    public static SyntaxToken createMissingToken(SyntaxKind kind) // PORT: §3.12 optional parameter diagnostic = null
    {
        return createMissingToken(kind, (Diagnostic) null);
    }

    /// <summary>
    /// Creates a missing <see cref="SyntaxToken"/> for a token that was expected to have the specified <see cref="SyntaxKind"/>.
    /// </summary>
    public static SyntaxToken createMissingToken(List<SyntaxKind> kinds) // PORT: §2.5
    {
        return SyntaxToken.missing("", kinds.get(0), Arrays.asList(new Diagnostic[] { DiagnosticFacts.getTokenExpected(kinds) })); // PORT: §3.17 array as IReadOnlyList
    }

    /// <summary>
    /// Creates a missing <see cref="SyntaxToken"/> for a token that was expected to have the specified text.
    /// </summary>
    public static SyntaxToken createMissingToken(String text, Diagnostic diagnostic)
    {
        var kindOut = new Out<SyntaxKind>(); // PORT: §3.3
        SyntaxKind kind;
        if (!SyntaxFacts.tryGetKind(text, kindOut))
        {
            kind = SyntaxKind.IdentifierToken;
        }
        else
        {
            kind = kindOut.value; // PORT: §3.3
        }

        return SyntaxToken.missing("", kind, Arrays.asList(new Diagnostic[] { diagnostic != null ? diagnostic : DiagnosticFacts.getTokenExpected(new String[] { text }) })); // PORT: §3.17 array as IReadOnlyList, §3.14 ??
    }

    public static SyntaxToken createMissingToken(String text) // PORT: §3.12 optional parameter diagnostic = null
    {
        return createMissingToken(text, (Diagnostic) null);
    }

    /// <summary>
    /// Creates a missing <see cref="SyntaxToken"/> for a token that was expected to have one of the specified texts.
    /// </summary>
    public static SyntaxToken createMissingTokenText(List<String> texts) // PORT: §2.5
    {
        return SyntaxToken.missing("", SyntaxKind.IdentifierToken, Arrays.asList(new Diagnostic[] { DiagnosticFacts.getTokenExpected(texts) })); // PORT: §3.17 array as IReadOnlyList
    }

    /// <summary>
    /// A parser that consumes the next <see cref="LexicalToken"/>, producing the corresponding <see cref="SyntaxToken"/>.
    /// </summary>
    public static final Parser2<LexicalToken, SyntaxToken> AnyToken =
        Parsers.match((LexicalToken t) -> true, t -> SyntaxToken.from(t)).withTag("<any>");

    /// <summary>
    /// A parser that consumes the next <see cref="LexicalToken"/> as long as it does not have the kind <see cref="SyntaxKind.EndOfTextToken"/>, producing the corresponding <see cref="SyntaxToken"/>.
    /// </summary>
    public static final Parser2<LexicalToken, SyntaxToken> AnyTokenButEnd =
        Parsers.match((LexicalToken t) -> t.kind() != SyntaxKind.EndOfTextToken, t -> SyntaxToken.from(t)).withTag("<any!end>");

    /// <summary>
    /// A parser that consumes only the end of text token.
    /// </summary>
    public static final Parser<LexicalToken> EndOfText =
        Parsers.match((LexicalToken t) -> t.kind() == SyntaxKind.EndOfTextToken);

    /// <summary>
    /// Gets the default tag to assign a token parser, based on the token's text.
    /// </summary>
    private static String getDefaultTag(String text)
    {
        var kind = new Out<SyntaxKind>(); // PORT: §3.3
        if (SyntaxFacts.tryGetKind(text, kind))
        {
            return getDefaultTag(kind.value);
        }
        else
        {
            return text;
        }
    }

    /// <summary>
    /// Gets the default tag to assign a token parser, based on the token's kind.
    /// </summary>
    private static String getDefaultTag(SyntaxKind kind)
    {
        var text = SyntaxFacts.getText(kind); // PORT: §3.5
        return SyntaxFacts.getCategory(kind) == SyntaxCategory.Punctuation // PORT: §3.5
                ? "'" + DotNet.str(text) + "'" // PORT: §3.14
                : text != null ? text : DotNetStrings.toLowerInvariant(kind.toString()); // PORT: §3.14 ??, §5.1 ToLower()
    }

    /// <summary>
    /// A parser that consumes the next next <see cref="LexicalToken"/> if it has the specified <see cref="SyntaxKind"/>, producing a corresponding <see cref="SyntaxToken"/>.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxToken> token(SyntaxKind kind, CompletionKind ckind, CompletionPriority priority, String ctext)
    {
        var item = createCompletionItem(kind, ckind != null ? ckind : getCompletionKind(kind), priority, ctext); // PORT: §3.14 ??
        return token(kind, item);
    }

    public static Parser2<LexicalToken, SyntaxToken> token(SyntaxKind kind, CompletionKind ckind, CompletionPriority priority) // PORT: §3.12 optional parameter ctext = null
    {
        return token(kind, ckind, priority, (String) null);
    }

    public static Parser2<LexicalToken, SyntaxToken> token(SyntaxKind kind, CompletionKind ckind) // PORT: §3.12 optional parameter priority = Normal
    {
        return token(kind, ckind, CompletionPriority.Normal, (String) null);
    }

    public static Parser2<LexicalToken, SyntaxToken> token(SyntaxKind kind) // PORT: §3.12 optional parameter ckind = null
    {
        return token(kind, (CompletionKind) null, CompletionPriority.Normal, (String) null);
    }

    /// <summary>
    /// A parser that consumes the next next <see cref="LexicalToken"/> if it has the specified <see cref="SyntaxKind"/>, producing a corresponding <see cref="SyntaxToken"/>.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxToken> token(SyntaxKind kind, CompletionItem... items)
    {
        var rule = Parsers.match((LexicalToken t) -> t.kind() == kind, lt -> SyntaxToken.from(lt)).withTag(getDefaultTag(kind));

        if (items != null && items.length == 0)
        {
            items = new CompletionItem[] { createCompletionItem(kind, getCompletionKind(kind), CompletionPriority.Normal, null) };
        }

        if (items != null)
        {
            rule = rule.withAnnotations(Arrays.asList(items)); // PORT: §3.17 array as IEnumerable<object>
        }

        return rule;
    }

    /// <summary>
    /// Creates a new version of the parser with the ComplationItem annotation set.
    /// </summary>
    public static <TElement> Parser2<LexicalToken, TElement> withCompletion(Parser2<LexicalToken, TElement> parser, CompletionItem... items) // PORT: §3.5 extension method
    {
        return parser.withAnnotations(Linq.<Object>concat(Linq.where(parser.annotations(), a -> !(a instanceof CompletionItem)), Arrays.asList(items))); // PORT: §3.6
    }

    /// <summary>
    /// Creates a new version of the parser with the CompletionHint annotation set.
    /// </summary>
    // PORT: §3.17 D23 CompletionHint is an int-constant holder: the boxed hint is an Integer, so `a is CompletionHint` is `a instanceof Integer`
    public static <TElement> Parser2<LexicalToken, TElement> withCompletionHint(Parser2<LexicalToken, TElement> parser, int hint) // PORT: §3.5 extension method
    {
        return parser.withAnnotations(Linq.<Object>concat(Linq.where(parser.annotations(), a -> !(a instanceof Integer)), Arrays.asList(new Object[] { (Object) hint }))); // PORT: §3.6
    }

    /// <summary>
    /// A parser that consumes the next next <see cref="LexicalToken"/> if it has one of the specified <see cref="SyntaxKind"/>s, producing a corresponding <see cref="SyntaxToken"/>.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxToken> token(List<SyntaxKind> kinds, CompletionKind defaultKind, CompletionPriority priority) // PORT: §2.5
    {
        Ensure.argumentNotNull(kinds, "kinds" /* nameof */);
        var set = new LinkedHashSet<SyntaxKind>(kinds); // PORT: §3.17

        var rule = Parsers.match((LexicalToken t) -> set.contains(t.kind()), lt -> SyntaxToken.from(lt)).withTag(DotNetStrings.join(" | ", Linq.select(kinds, k -> getDefaultTag(k)))); // PORT: §3.6, §3.14

        var items = getCompletionItems(set, defaultKind, priority);
        if (items.size() > 0)
        {
            rule = rule.withAnnotations(items); // PORT: §3.10 covariance: List<CompletionItem> as IEnumerable<object>
        }

        return rule;
    }

    public static Parser2<LexicalToken, SyntaxToken> token(List<SyntaxKind> kinds, CompletionKind defaultKind) // PORT: §3.12 optional parameter priority = Normal
    {
        return token(kinds, defaultKind, CompletionPriority.Normal);
    }

    public static Parser2<LexicalToken, SyntaxToken> token(List<SyntaxKind> kinds) // PORT: §3.12 optional parameter defaultKind = null
    {
        return token(kinds, (CompletionKind) null, CompletionPriority.Normal);
    }

    /// <summary>
    /// Matches one or more lexical tokens to the corresponding text.
    /// </summary>
    public static int matchesText(Source<LexicalToken> source, int start, String text)
    {
        // consume all lexical tokens with combined text that matches the specified text
        int textOffset = 0;
        int i = 0;

        for (; !source.isEnd(start + i); i++)
        {
            var token = source.peek(start + i);

            // only first token can have trivia
            if (i > 0 && token.trivia().length() > 0)
                break;

            // token has more text than remaining
            if (token.text().length() > text.length() - textOffset)
                break;

            // text parts must match exactly
            if (DotNetStrings.compare(token.text(), 0, text, textOffset, token.text().length()) != 0) // PORT: §5.4 D12 ordinal
                break;

            textOffset += token.text().length();

            if (textOffset == text.length())
                return (i + 1);
        }

        return -(i + 1);
    }

    /// <summary>
    /// Create a <see cref="SyntaxToken"/> from one or more adjacent <see cref="LexicalToken"/>.
    /// </summary>
    public static SyntaxToken produceSyntaxToken(Source<LexicalToken> source, int start, int length, SyntaxKind asKind) // PORT: §3.7 SyntaxKind? -> nullable SyntaxKind
    {
        var token = source.peek(start);
        if (token != null)
        {
            String text = token.text();

            for (int i = 1; i < length; i++)
            {
                token = source.peek(start + i);
                if (token == null || token.trivia().length() > 0)
                    return null;
                text += token.text(); // PORT: §3.14 Text is never null
            }

            return produceSyntaxToken(source, start, length, text, asKind);
        }

        return null;
    }

    public static SyntaxToken produceSyntaxToken(Source<LexicalToken> source, int start, int length) // PORT: §3.12 optional parameter asKind = null
    {
        return produceSyntaxToken(source, start, length, (SyntaxKind) null);
    }

    /// <summary>
    /// Create a <see cref="SyntaxToken"/> from one or more adjacent <see cref="LexicalToken"/>.
    /// </summary>
    public static SyntaxToken produceSyntaxToken(Source<LexicalToken> source, int start, int length, String text, SyntaxKind asKind) // PORT: §3.7 SyntaxKind? -> nullable SyntaxKind
    {
        var firstToken = source.peek(start);

        if (length == 1 && (asKind == null || asKind == firstToken.kind()))
        {
            return SyntaxToken.from(firstToken);
        }
        else if (asKind != null) // PORT: §3.15 `asKind is SyntaxKind kind` on SyntaxKind? is a has-value test
        {
            SyntaxKind kind = asKind;
            // assigning kinds only works for token categories that hold onto their text
            switch (SyntaxFacts.getCategory(kind)) // PORT: §3.5
            {
                case Identifier:
                    return SyntaxToken.identifier(firstToken.trivia(), text);
                case Other:
                    return SyntaxToken.other(firstToken.trivia(), text, kind);
                default:
                    throw new IllegalStateException("Cannot produce syntax token for kind: " + DotNet.str(kind)); // PORT: §3.16, §3.14
            }
        }
        else
        {
            return SyntaxToken.identifier(firstToken.trivia(), text);
        }
    }

    public static SyntaxToken produceSyntaxToken(Source<LexicalToken> source, int start, int length, String text) // PORT: §3.12 optional parameter asKind = null
    {
        return produceSyntaxToken(source, start, length, text, (SyntaxKind) null);
    }

    /// <summary>
    /// Gets the text of a series of tokens.
    /// </summary>
    public static String getCombinedTokenText(Source<LexicalToken> source, int start, int length, boolean includeInnerTrivia)
    {
        if (length == 1)
        {
            return source.peek(start).text();
        }

        var builder = new StringBuilder();
        for (int i = 0; i < length; i++)
        {
            var token = source.peek(start + i);
            if (i > 0 && includeInnerTrivia)
                builder.append(DotNet.str(token.trivia())); // PORT: §3.14
            builder.append(DotNet.str(token.text())); // PORT: §3.14
        }

        return builder.toString();
    }

    public static String getCombinedTokenText(Source<LexicalToken> source, int start, int length) // PORT: §3.12 optional parameter includeInnerTrivia = true
    {
        return getCombinedTokenText(source, start, length, true);
    }

    /// <summary>
    /// A parser that consumes the next <see cref="LexicalToken"/> (or series of adjacent tokens) that combined has the specified text, producing a single <see cref="SyntaxToken"/>.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxToken> matchText(String text, SyntaxKind asKind)
    {
        return Parsers.match(
            (Source<LexicalToken> source, int start) -> matchesText(source, start, text), 
            (Source<LexicalToken> source, int start, int length) -> produceSyntaxToken(source, start, length, text, asKind));
    }

    public static Parser2<LexicalToken, SyntaxToken> matchText(String text) // PORT: §3.12 optional parameter asKind = null
    {
        return matchText(text, (SyntaxKind) null);
    }

    /// <summary>
    /// A parser that consumes the next <see cref="LexicalToken"/> (or series of adjacent tokens) if it has the specified text, producing a single <see cref="SyntaxToken"/>.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxToken> token(String text, SyntaxKind asKind, CompletionKind ckind, CompletionPriority priority, String ctext)
    {
        var rule = matchText(text, asKind).withTag(getDefaultTag(text));

        var item = createCompletionItem(text, ckind != null ? ckind : getCompletionKind(text), priority, ctext); // PORT: §3.14 ??
        return withCompletion(rule, item); // PORT: §3.5
    }

    public static Parser2<LexicalToken, SyntaxToken> token(String text, SyntaxKind asKind, CompletionKind ckind, CompletionPriority priority) // PORT: §3.12 optional parameter ctext = null
    {
        return token(text, asKind, ckind, priority, (String) null);
    }

    public static Parser2<LexicalToken, SyntaxToken> token(String text, SyntaxKind asKind, CompletionKind ckind) // PORT: §3.12 optional parameter priority = Normal
    {
        return token(text, asKind, ckind, CompletionPriority.Normal, (String) null);
    }

    public static Parser2<LexicalToken, SyntaxToken> token(String text, SyntaxKind asKind) // PORT: §3.12 optional parameter ckind = null
    {
        return token(text, asKind, (CompletionKind) null, CompletionPriority.Normal, (String) null);
    }

    public static Parser2<LexicalToken, SyntaxToken> token(String text) // PORT: §3.12 optional parameter asKind = null
    {
        return token(text, (SyntaxKind) null, (CompletionKind) null, CompletionPriority.Normal, (String) null);
    }

    /// <summary>
    /// A parser that consumes the next <see cref="LexicalToken"/> (or series of adjacent tokens) if it has the specified text, producing a single <see cref="SyntaxToken"/>.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxToken> token(String text, CompletionKind ckind, CompletionPriority priority, String ctext)
    {
        var rule = matchText(text).withTag(getDefaultTag(text));

        var item = createCompletionItem(text, ckind, priority, ctext);
        return withCompletion(rule, item); // PORT: §3.5
    }

    public static Parser2<LexicalToken, SyntaxToken> token(String text, CompletionKind ckind, CompletionPriority priority) // PORT: §3.12 optional parameter ctext = null
    {
        return token(text, ckind, priority, (String) null);
    }

    public static Parser2<LexicalToken, SyntaxToken> token(String text, CompletionKind ckind) // PORT: §3.12 optional parameter priority = Normal
    {
        return token(text, ckind, CompletionPriority.Normal, (String) null);
    }

    /// <summary>
    /// A parser that consumes the next <see cref="LexicalToken"/> (or series of adjacent tokens) if it has one of the specified texts, producing a single <see cref="SyntaxToken"/>.
    /// </summary>
    @SuppressWarnings("unchecked")
    public static Parser2<LexicalToken, SyntaxToken> tokenText(List<String> texts, CompletionKind defaultKind, CompletionPriority priority) // PORT: §2.5
    {
        Ensure.argumentNotNull(texts, "texts" /* nameof */);

        Parser2<LexicalToken, SyntaxToken>[] parsers = Linq.toArray(Linq.select(texts, t -> matchText(t)), n -> (Parser2<LexicalToken, SyntaxToken>[]) new Parser2[n]); // PORT: §3.6, §3.10 generic array; a typed local picks the First<TOutput> overload
        var rule = Parsers.first(parsers)
                .withTag(DotNetStrings.join(" | ", Linq.select(texts, t -> getDefaultTag(t)))); // PORT: §3.6, §3.14

        var items = getCompletionItemsText(texts, defaultKind, priority);
        if (items.size() > 0)
        {
            rule = rule.withAnnotations(items); // PORT: §3.10 covariance: List<CompletionItem> as IEnumerable<object>
        }

        return rule;
    }

    public static Parser2<LexicalToken, SyntaxToken> tokenText(List<String> texts, CompletionKind defaultKind) // PORT: §3.12 optional parameter priority = Normal
    {
        return tokenText(texts, defaultKind, CompletionPriority.Normal);
    }

    public static Parser2<LexicalToken, SyntaxToken> tokenText(List<String> texts) // PORT: §3.12 optional parameter defaultKind = null
    {
        return tokenText(texts, (CompletionKind) null, CompletionPriority.Normal);
    }

    /// <summary>
    /// A parser that consumes the next <see cref="LexicalToken"/> (or series of adjacent tokens) if it has the specified text, producing a single <see cref="SyntaxToken"/>.
    /// It does not show up in intellisense completion lists.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxToken> hiddenToken(String text, SyntaxKind asKind)
    {
        return matchText(text, asKind).withTag(getDefaultTag(text));
    }

    public static Parser2<LexicalToken, SyntaxToken> hiddenToken(String text) // PORT: §3.12 optional parameter asKind = null
    {
        return hiddenToken(text, (SyntaxKind) null);
    }

    /// <summary>
    /// A parser that consumes the next next <see cref="LexicalToken"/> if it has the specified <see cref="SyntaxKind"/>, producing a corresponding <see cref="SyntaxToken"/>.
    /// It does not show up in intellisense completion lists.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxToken> hiddenToken(SyntaxKind tokenKind)
    {
        return Parsers.match((LexicalToken t) -> t.kind() == tokenKind, lt -> SyntaxToken.from(lt)).withTag(getDefaultTag(tokenKind));
    }

    /// <summary>
    /// A parser that consumes the next <see cref="LexicalToken"/> (or series of adjacent tokens) if it has one of the specified texts, producing a single <see cref="SyntaxToken"/>.
    /// It does not show up in intellisense completion lists.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxToken> hiddenToken(List<String> texts)
    {
        Ensure.argumentNotNull(texts, "texts" /* nameof */);
        var set = new LinkedHashSet<String>(texts); // PORT: §3.17

        var rule = Parsers.match((LexicalToken t) -> set.contains(t.text()), lt -> SyntaxToken.from(lt)).withTag(DotNetStrings.join(" | ", Linq.select(texts, t -> getDefaultTag(t)))); // PORT: §3.6, §3.14

        return rule;
    }

    /// <summary>
    /// A parser that consumes the next <see cref="LexicalToken"/> if it has the specified <see cref="SyntaxKind"/>, producing a corresponding <see cref="SyntaxToken"/> or an equivalent missing token otherwise.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxToken> requiredToken(SyntaxKind kind, CompletionKind ckind, CompletionPriority priority, String ctext)
    {
        return Parsers.required(token(kind, ckind, priority, ctext), () -> createMissingToken(kind));
    }

    public static Parser2<LexicalToken, SyntaxToken> requiredToken(SyntaxKind kind, CompletionKind ckind, CompletionPriority priority) // PORT: §3.12 optional parameter ctext = null
    {
        return requiredToken(kind, ckind, priority, (String) null);
    }

    public static Parser2<LexicalToken, SyntaxToken> requiredToken(SyntaxKind kind, CompletionKind ckind) // PORT: §3.12 optional parameter priority = Normal
    {
        return requiredToken(kind, ckind, CompletionPriority.Normal, (String) null);
    }

    public static Parser2<LexicalToken, SyntaxToken> requiredToken(SyntaxKind kind) // PORT: §3.12 optional parameter ckind = null
    {
        return requiredToken(kind, (CompletionKind) null, CompletionPriority.Normal, (String) null);
    }

    /// <summary>
    /// A parser that consumes the next <see cref="LexicalToken"/> if it has the specified <see cref="SyntaxKind"/>, producing a corresponding <see cref="SyntaxToken"/> or an equivalent missing token otherwise.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxToken> requiredToken(SyntaxKind kind, CompletionItem item)
    {
        return Parsers.required(token(kind, item), () -> createMissingToken(kind));
    }

    /// <summary>
    /// A parser that consumes the next <see cref="LexicalToken"/> if it has one of the specified <see cref="SyntaxKind"/>, producing a corresponding <see cref="SyntaxToken"/> or an equivalent missing token otherwise.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxToken> requiredToken(List<SyntaxKind> kinds, CompletionKind ckind, CompletionPriority priority) // PORT: §2.5
    {
        return Parsers.required(token(kinds, ckind, priority), () -> createMissingToken(kinds));
    }

    public static Parser2<LexicalToken, SyntaxToken> requiredToken(List<SyntaxKind> kinds, CompletionKind ckind) // PORT: §3.12 optional parameter priority = Normal
    {
        return requiredToken(kinds, ckind, CompletionPriority.Normal);
    }

    public static Parser2<LexicalToken, SyntaxToken> requiredToken(List<SyntaxKind> kinds) // PORT: §3.12 optional parameter ckind = null
    {
        return requiredToken(kinds, (CompletionKind) null, CompletionPriority.Normal);
    }

    /// <summary>
    /// A parser that consumes the next <see cref="LexicalToken"/> (or series of adjacent tokens) if it has the specified text, producing a corresponding <see cref="SyntaxToken"/> or an equivalent missing token otherwise.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxToken> requiredToken(String text, SyntaxKind asKind, CompletionKind ckind, CompletionPriority priority, String ctext)
    {
        return Parsers.required(token(text, asKind, ckind, priority, ctext), () -> createMissingToken(text));
    }

    public static Parser2<LexicalToken, SyntaxToken> requiredToken(String text, SyntaxKind asKind, CompletionKind ckind, CompletionPriority priority) // PORT: §3.12 optional parameter ctext = null
    {
        return requiredToken(text, asKind, ckind, priority, (String) null);
    }

    public static Parser2<LexicalToken, SyntaxToken> requiredToken(String text, SyntaxKind asKind, CompletionKind ckind) // PORT: §3.12 optional parameter priority = Normal
    {
        return requiredToken(text, asKind, ckind, CompletionPriority.Normal, (String) null);
    }

    public static Parser2<LexicalToken, SyntaxToken> requiredToken(String text, SyntaxKind asKind) // PORT: §3.12 optional parameter ckind = null
    {
        return requiredToken(text, asKind, (CompletionKind) null, CompletionPriority.Normal, (String) null);
    }

    public static Parser2<LexicalToken, SyntaxToken> requiredToken(String text) // PORT: §3.12 optional parameter asKind = null
    {
        return requiredToken(text, (SyntaxKind) null, (CompletionKind) null, CompletionPriority.Normal, (String) null);
    }

    /// <summary>
    /// A parser that consumes the next <see cref="LexicalToken"/> (or series of adjacent tokens) if it has one of the specified texts, producing a corresponding <see cref="SyntaxToken"/> or an equivalent missing token otherwise.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxToken> requiredTokenText(List<String> texts, CompletionKind ckind, CompletionPriority priority) // PORT: §2.5
    {
        return Parsers.required(tokenText(texts, ckind, priority), () -> createMissingTokenText(texts));
    }

    public static Parser2<LexicalToken, SyntaxToken> requiredTokenText(List<String> texts, CompletionKind ckind) // PORT: §3.12 optional parameter priority = Normal
    {
        return requiredTokenText(texts, ckind, CompletionPriority.Normal);
    }

    public static Parser2<LexicalToken, SyntaxToken> requiredTokenText(List<String> texts) // PORT: §3.12 optional parameter ckind = null
    {
        return requiredTokenText(texts, (CompletionKind) null, CompletionPriority.Normal);
    }

    /// <summary>
    /// Gets the default <see cref="CompletionItem"/> for a token with the specified <see cref="SyntaxKind"/>.
    /// </summary>
    public static CompletionItem createCompletionItem(SyntaxKind kind, CompletionKind ckind, CompletionPriority priority, String ctext)
    {
        var text = SyntaxFacts.getText(kind);
        return createCompletionItem(text, ckind, priority, ctext);
    }

    public static CompletionItem createCompletionItem(SyntaxKind kind, CompletionKind ckind, CompletionPriority priority) // PORT: §3.12 optional parameter ctext = null
    {
        return createCompletionItem(kind, ckind, priority, (String) null);
    }

    /// <summary>
    /// Gets the default <see cref="CompletionItem"/> for a token with the specified text.
    /// </summary>
    public static CompletionItem createCompletionItem(String text, CompletionKind ckind, CompletionPriority priority, String ctext, String matchText)
    {
        // no text is not going to work
        if (DotNetStrings.isNullOrWhiteSpace(text))
            return null;

        // hide any syntax that starts with _ from completion
        if (text.startsWith("_")) // PORT: §5.4 StringComparison.Ordinal
            return null;

        var item = new CompletionItem(ckind, text, null, null, matchText, null, CompletionRank.Default, priority); // PORT: §3.12 named arguments matchText, priority

        if (ctext != null)
        {
            item = item.withApplyTexts(ctext);
        }

        return item;
    }

    public static CompletionItem createCompletionItem(String text, CompletionKind ckind, CompletionPriority priority, String ctext) // PORT: §3.12 optional parameter matchText = null
    {
        return createCompletionItem(text, ckind, priority, ctext, (String) null);
    }

    public static CompletionItem createCompletionItem(String text, CompletionKind ckind, CompletionPriority priority) // PORT: §3.12 optional parameter ctext = null
    {
        return createCompletionItem(text, ckind, priority, (String) null, (String) null);
    }

    /// <summary>
    /// Gets the <see cref="CompletionKind"/> for the token text.
    /// </summary>
    public static CompletionKind getCompletionKind(String text, CompletionKind defaultKind) // PORT: §3.7 CompletionKind? -> nullable
    {
        var kind = new Out<SyntaxKind>(); // PORT: §3.3
        return SyntaxFacts.tryGetKind(text, kind)
            ? getCompletionKind(kind.value, defaultKind)
            : defaultKind != null ? defaultKind : CompletionKind.Syntax; // PORT: §3.14 ??
    }

    public static CompletionKind getCompletionKind(String text) // PORT: §3.12 optional parameter defaultKind = null
    {
        return getCompletionKind(text, (CompletionKind) null);
    }

    /// <summary>
    /// Gets the default <see cref="CompletionKind"/> for the token kind.
    /// </summary>
    public static CompletionKind getCompletionKind(SyntaxKind kind, CompletionKind defaultKind) // PORT: §3.7 CompletionKind? -> nullable
    {
        switch (SyntaxFacts.getCategory(kind)) // PORT: §3.5
        {
            case Keyword:
                return CompletionKind.Keyword;
            case Operator:
                return CompletionKind.ScalarInfix;
            case Punctuation:
                return CompletionKind.Punctuation;
            default:
                return defaultKind != null ? defaultKind : CompletionKind.Syntax; // PORT: §3.14 ??
        }
    }

    public static CompletionKind getCompletionKind(SyntaxKind kind) // PORT: §3.12 optional parameter defaultKind = null
    {
        return getCompletionKind(kind, (CompletionKind) null);
    }

    /// <summary>
    /// Gets the default <see cref="CompletionItem"/> for tokens with any of the specified <see cref="SyntaxKind"/>.
    /// </summary>
    private static List<CompletionItem> getCompletionItems(Iterable<SyntaxKind> kinds, CompletionKind defaultKind, CompletionPriority priority) // PORT: §2.5; §3.6 eager list
    {
        return Linq.where(Linq.select(kinds, k -> createCompletionItem(k, getCompletionKind(k, defaultKind), priority)), i -> i != null);
    }

    /// <summary>
    /// Gets the default <see cref="CompletionItem"/> for tokens with any of the specified texts.
    /// </summary>
    private static List<CompletionItem> getCompletionItemsText(Iterable<String> texts, CompletionKind defaultKind, CompletionPriority priority) // PORT: §2.5; §3.6 eager list
    {
        return Linq.where(Linq.select(texts, t -> createCompletionItem(t, getCompletionKind(t, defaultKind), priority)), i -> i != null);
    }

    /// <summary>
    /// A parser that parses a <see cref="SyntaxList"/> of elements.
    /// </summary>
    @SuppressWarnings("unchecked")
    public static <TElement extends SyntaxElement> Parser2<LexicalToken, SyntaxList1<TElement>> list(
        Parser2<LexicalToken, TElement> elementParser,
        BiFunction<Source<LexicalToken>, Integer, TElement> fnMissingElement,
        boolean oneOrMore)
    {
        return Parsers.list(
            elementParser,
            fnMissingElement,
            oneOrMore,
            elements ->
                new SyntaxList1<TElement>((TElement[]) elements.toArray(new SyntaxElement[0]))); // PORT: §3.10 elements.ToArray() into a TElement[] (erasure SyntaxElement[])
    }

    public static <TElement extends SyntaxElement> Parser2<LexicalToken, SyntaxList1<TElement>> list( // PORT: §3.12 optional parameter oneOrMore = false
        Parser2<LexicalToken, TElement> elementParser,
        BiFunction<Source<LexicalToken>, Integer, TElement> fnMissingElement)
    {
        return list(elementParser, fnMissingElement, false);
    }

    public static <TElement extends SyntaxElement> Parser2<LexicalToken, SyntaxList1<TElement>> list( // PORT: §3.12 optional parameter fnMissingElement = null
        Parser2<LexicalToken, TElement> elementParser)
    {
        return list(elementParser, null, false);
    }

    /// <summary>
    /// A parser that parses a <see cref="SyntaxList"/> of <see cref="SeparatedElement{TElement}"/>'s
    /// </summary>
    public static <TElement extends SyntaxElement> Parser2<LexicalToken, SyntaxList1<SeparatedElement1<TElement>>> separatedList(
        Parser2<LexicalToken, TElement> primaryElementParser,
        SyntaxKind separatorKind,
        BiFunction<Source<LexicalToken>, Integer, TElement> fnMissingElement,
        Parser<LexicalToken> endOfList,
        boolean oneOrMore,
        boolean allowTrailingSeparator)
    {
        return separatedList(
            primaryElementParser,
            separatorKind,
            primaryElementParser.withTag("..."),
            fnMissingElement,
            endOfList,
            oneOrMore,
            allowTrailingSeparator);
    }

    public static <TElement extends SyntaxElement> Parser2<LexicalToken, SyntaxList1<SeparatedElement1<TElement>>> separatedList( // PORT: §3.12 optional parameter allowTrailingSeparator = false
        Parser2<LexicalToken, TElement> primaryElementParser,
        SyntaxKind separatorKind,
        BiFunction<Source<LexicalToken>, Integer, TElement> fnMissingElement,
        Parser<LexicalToken> endOfList,
        boolean oneOrMore)
    {
        return separatedList(primaryElementParser, separatorKind, fnMissingElement, endOfList, oneOrMore, false);
    }

    public static <TElement extends SyntaxElement> Parser2<LexicalToken, SyntaxList1<SeparatedElement1<TElement>>> separatedList( // PORT: §3.12 optional parameter oneOrMore = false
        Parser2<LexicalToken, TElement> primaryElementParser,
        SyntaxKind separatorKind,
        BiFunction<Source<LexicalToken>, Integer, TElement> fnMissingElement,
        Parser<LexicalToken> endOfList)
    {
        return separatedList(primaryElementParser, separatorKind, fnMissingElement, endOfList, false, false);
    }

    public static <TElement extends SyntaxElement> Parser2<LexicalToken, SyntaxList1<SeparatedElement1<TElement>>> separatedList( // PORT: §3.12 optional parameter endOfList = null
        Parser2<LexicalToken, TElement> primaryElementParser,
        SyntaxKind separatorKind,
        BiFunction<Source<LexicalToken>, Integer, TElement> fnMissingElement)
    {
        return separatedList(primaryElementParser, separatorKind, fnMissingElement, (Parser<LexicalToken>) null, false, false);
    }

    /// <summary>
    /// A parser that parses a <see cref="SyntaxList"/> of <see cref="SeparatedElement"/>'s.
    /// </summary>
    public static <TElement extends SyntaxElement> Parser2<LexicalToken, SyntaxList1<SeparatedElement1<TElement>>> separatedList(
        Parser2<LexicalToken, TElement> primaryElementParser,
        SyntaxKind separatorKind,
        Parser2<LexicalToken, TElement> secondaryElementParser,
        BiFunction<Source<LexicalToken>, Integer, TElement> fnMissingElement,
        Parser<LexicalToken> endOfList,
        boolean oneOrMore,
        boolean allowTrailingSeparator)
    {
        return Parsers.oList(
            primaryElementParser,
            token(separatorKind),
            secondaryElementParser,
            fnMissingElement,
            (source, start) -> createMissingToken(separatorKind),
            fnMissingElement,
            endOfList,
            oneOrMore,
            allowTrailingSeparator,
            (List<Object> elements) -> SyntaxParsers.<TElement>makeSeparatedList(elements)); // PORT: §3.10 method group MakeSeparatedList<TElement>
    }

    public static <TElement extends SyntaxElement> Parser2<LexicalToken, SyntaxList1<SeparatedElement1<TElement>>> separatedList( // PORT: §3.12 optional parameter allowTrailingSeparator = false
        Parser2<LexicalToken, TElement> primaryElementParser,
        SyntaxKind separatorKind,
        Parser2<LexicalToken, TElement> secondaryElementParser,
        BiFunction<Source<LexicalToken>, Integer, TElement> fnMissingElement,
        Parser<LexicalToken> endOfList,
        boolean oneOrMore)
    {
        return separatedList(primaryElementParser, separatorKind, secondaryElementParser, fnMissingElement, endOfList, oneOrMore, false);
    }

    public static <TElement extends SyntaxElement> Parser2<LexicalToken, SyntaxList1<SeparatedElement1<TElement>>> separatedList( // PORT: §3.12 optional parameter oneOrMore = false
        Parser2<LexicalToken, TElement> primaryElementParser,
        SyntaxKind separatorKind,
        Parser2<LexicalToken, TElement> secondaryElementParser,
        BiFunction<Source<LexicalToken>, Integer, TElement> fnMissingElement,
        Parser<LexicalToken> endOfList)
    {
        return separatedList(primaryElementParser, separatorKind, secondaryElementParser, fnMissingElement, endOfList, false, false);
    }

    public static <TElement extends SyntaxElement> Parser2<LexicalToken, SyntaxList1<SeparatedElement1<TElement>>> separatedList( // PORT: §3.12 optional parameter endOfList = null
        Parser2<LexicalToken, TElement> primaryElementParser,
        SyntaxKind separatorKind,
        Parser2<LexicalToken, TElement> secondaryElementParser,
        BiFunction<Source<LexicalToken>, Integer, TElement> fnMissingElement)
    {
        return separatedList(primaryElementParser, separatorKind, secondaryElementParser, fnMissingElement, (Parser<LexicalToken>) null, false, false);
    }

    /// <summary>
    /// Determines if a typical comma separated list has ended
    /// </summary>
    public static Parser<LexicalToken> EndOfCommaList = Parsers.match((LexicalToken t) ->
        t.kind() == SyntaxKind.EndOfTextToken
        || t.kind() == SyntaxKind.CloseParenToken
        || t.kind() == SyntaxKind.CloseBracketToken
        || t.kind() == SyntaxKind.CloseBraceToken
        || t.kind() == SyntaxKind.BarToken
        || t.kind() == SyntaxKind.SemicolonToken);

    /// <summary>
    /// A parser that parses a typical comma separated <see cref="SyntaxList"/> of <see cref="SeparatedElement"/>'s.
    /// </summary>
    @SuppressWarnings("unchecked")
    public static <TElement extends SyntaxElement> Parser2<LexicalToken, SyntaxList1<SeparatedElement1<TElement>>> commaList(
        Parser2<LexicalToken, TElement> elementParser,
        BiFunction<Source<LexicalToken>, Integer, TElement> fnMissingElement,
        boolean oneOrMore,
        boolean allowTrailingComma,
        Iterable<SyntaxKind> endKinds)
    {
        Parser<LexicalToken> endOfList = EndOfCommaList;

        if (endKinds != null)
        {
            var hash = new LinkedHashSet<SyntaxKind>(); // PORT: §3.17
            for (var k : endKinds) // PORT: §3.17 new HashSet<T>(IEnumerable<T>)
            {
                hash.add(k);
            }
            endOfList = Parsers.first(EndOfCommaList, Parsers.match((LexicalToken t) -> hash.contains(t.kind())));
        }

        return separatedList(elementParser, SyntaxKind.CommaToken, fnMissingElement, endOfList, oneOrMore, allowTrailingComma);
    }

    public static <TElement extends SyntaxElement> Parser2<LexicalToken, SyntaxList1<SeparatedElement1<TElement>>> commaList( // PORT: §3.12 optional parameter endKinds = null
        Parser2<LexicalToken, TElement> elementParser,
        BiFunction<Source<LexicalToken>, Integer, TElement> fnMissingElement,
        boolean oneOrMore,
        boolean allowTrailingComma)
    {
        return commaList(elementParser, fnMissingElement, oneOrMore, allowTrailingComma, null);
    }

    public static <TElement extends SyntaxElement> Parser2<LexicalToken, SyntaxList1<SeparatedElement1<TElement>>> commaList( // PORT: §3.12 optional parameter allowTrailingComma = false
        Parser2<LexicalToken, TElement> elementParser,
        BiFunction<Source<LexicalToken>, Integer, TElement> fnMissingElement,
        boolean oneOrMore)
    {
        return commaList(elementParser, fnMissingElement, oneOrMore, false, null);
    }

    public static <TElement extends SyntaxElement> Parser2<LexicalToken, SyntaxList1<SeparatedElement1<TElement>>> commaList( // PORT: §3.12 optional parameter oneOrMore = false
        Parser2<LexicalToken, TElement> elementParser,
        BiFunction<Source<LexicalToken>, Integer, TElement> fnMissingElement)
    {
        return commaList(elementParser, fnMissingElement, false, false, null);
    }

    /// <summary>
    /// A parser that parses a typical comma separated <see cref="SyntaxList"/> of <see cref="SeparatedElement"/>'s.
    /// </summary>
    @SuppressWarnings("unchecked")
    public static <TElement extends SyntaxElement> Parser2<LexicalToken, SyntaxList1<SeparatedElement1<TElement>>> oneOrMoreCommaList(
        Parser2<LexicalToken, TElement> elementParser,
        BiFunction<Source<LexicalToken>, Integer, TElement> fnMissingElement)
    {
        return Parsers.produce(
            Parsers.sequence(
                Parsers.required(elementParser.<SyntaxElement>cast(), (source, start) -> (SyntaxElement) fnMissingElement.apply(source, start)),
                Parsers.zeroOrMore(
                    Parsers.sequence(
                        Parsers.rule(token(SyntaxKind.CommaToken), t -> (SyntaxElement) t),
                        Parsers.rule(elementParser, l -> (SyntaxElement) l)))),
            (List<Object> elements) -> SyntaxParsers.<TElement>makeSeparatedList(elements));
    }

    /// <summary>
    /// Constructs a SyntaxList&lt;SeparatedElement&lt;TElement&gt;&gt; from a list of items and separators.
    /// </summary>
    public static <TElement extends SyntaxElement> SyntaxList1<SeparatedElement1<TElement>> makeSeparatedList(SyntaxElement... elements)
    {
        return SyntaxParsers.<TElement>makeSeparatedList(elements != null ? Arrays.<Object>asList((Object[]) elements) : (List<Object>) null); // PORT: §3.10 (IReadOnlyList<SyntaxElement>)elements as IReadOnlyList<object>
    }

    /// <summary>
    /// Constructs a SyntaxList&lt;SeparatedElement&lt;TElement&gt;&gt; from a list of items and separators.
    /// </summary>
    @SuppressWarnings("unchecked")
    public static <TElement extends SyntaxElement> SyntaxList1<SeparatedElement1<TElement>> makeSeparatedList(List<Object> elements)
    {
        if (elements == null)
            return SyntaxList1.<SeparatedElement1<TElement>>empty();

        var separatedElements = new ArrayList<SeparatedElement1<TElement>>();

        for (int i = 0; i < elements.size(); i += 2)
        {
            var element = (TElement) elements.get(i); // PORT: §3.10 unchecked cast; the runtime check is against the erasure SyntaxElement
            var separator = (i < elements.size() - 1) ? (SyntaxToken) elements.get(i + 1) : null;
            separatedElements.add(new SeparatedElement1<TElement>(element, separator));
        }

        return new SyntaxList1<SeparatedElement1<TElement>>(separatedElements);
    }

    /// <summary>
    /// Repeatedly parses all matching items.
    /// </summary>
    public static <TParser> List<TParser> parseAll(Parser2<LexicalToken, TParser> parser, String text, boolean alwaysProduceEndToken) // PORT: §3.5 extension method; §3.4 eager list
    {
        return parseAll(parser, Arrays.asList(TokenParser.parseTokens(text, ParseOptions.Default.withAlwaysProduceEndTokens(alwaysProduceEndToken)))); // PORT: §3.17 array as IReadOnlyList
    }

    public static <TParser> List<TParser> parseAll(Parser2<LexicalToken, TParser> parser, String text) // PORT: §3.12 optional parameter alwaysProduceEndToken = false
    {
        return parseAll(parser, text, false);
    }

    /// <summary>
    /// Repeatedly parses all matching items.
    /// </summary>
    public static <TParser> List<TParser> parseAll(Parser2<LexicalToken, TParser> parser, List<LexicalToken> tokens) // PORT: §3.5 extension method; §3.4 yield -> eager list
    {
        var results = new ArrayList<TParser>(); // PORT: §3.4
        var source = new ArraySource<LexicalToken>(tokens);
        var start = 0;

        while (!source.isEnd(start))
        {
            var result = parser.parse(source, start);

            if (result.length() <= 0)
                break;

            results.add(result.value()); // PORT: §3.4 yield return
            start += result.length();
        }

        return results; // PORT: §3.4
    }

    /// <summary>
    /// Parses the first matching item.
    /// </summary>
    public static <TParser> TParser parseFirst(Parser2<LexicalToken, TParser> parser, String text, boolean alwaysProduceEOF) // PORT: §3.5 extension method
    {
        return parseFirst(parser, Arrays.asList(TokenParser.parseTokens(text, ParseOptions.Default.withAlwaysProduceEndTokens(alwaysProduceEOF)))); // PORT: §3.17 array as IReadOnlyList
    }

    public static <TParser> TParser parseFirst(Parser2<LexicalToken, TParser> parser, String text) // PORT: §3.12 optional parameter alwaysProduceEOF = false
    {
        return parseFirst(parser, text, false);
    }

    /// <summary>
    /// Parses the first matching item.
    /// </summary>
    public static <TParser> TParser parseFirst(Parser2<LexicalToken, TParser> parser, List<LexicalToken> tokens) // PORT: §3.5 extension method
    {
        var source = new ArraySource<LexicalToken>(tokens);
        var result = parser.parse(source, 0);
        return result.value();
    }

    /// <summary>
    /// Scans the first matching item.
    /// </summary>
    public static int scanFirst(Parser<LexicalToken> parser, String text, boolean alwaysProduceEOF) // PORT: §3.5 extension method
    {
        var source = new ArraySource<LexicalToken>(Arrays.asList(TokenParser.parseTokens(text, ParseOptions.Default.withAlwaysProduceEndTokens(alwaysProduceEOF)))); // PORT: §3.17 array as IReadOnlyList
        return parser.scan(source, 0);
    }

    public static int scanFirst(Parser<LexicalToken> parser, String text) // PORT: §3.12 optional parameter alwaysProduceEOF = false
    {
        return scanFirst(parser, text, false);
    }

    /// <summary>
    /// Adds examples of completions as annotations onto this grammar rule.
    /// </summary>
    public static <TParser> Parser2<LexicalToken, TParser> examples(Parser2<LexicalToken, TParser> parser, List<String> values) // PORT: §3.5 extension method
    {
        return parser.withAnnotations(Linq.<String, Object>select(values, v -> new CompletionItem(CompletionKind.Example, v))); // PORT: §3.6
    }

    /// <summary>
    /// Adds examples of completions as annotations onto this grammar rule.
    /// </summary>
    public static <TParser> Parser2<LexicalToken, TParser> examples(Parser2<LexicalToken, TParser> parser, String... values) // PORT: §3.5 extension method
    {
        return examples(parser, Arrays.asList(values)); // PORT: §3.17 array as IReadOnlyList
    }
}
