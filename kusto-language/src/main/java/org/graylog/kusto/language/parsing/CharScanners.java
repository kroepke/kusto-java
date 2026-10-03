// Ported from: src/Kusto.Language/Parser/CharScanners.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import static org.graylog.kusto.language.parsing.Parsers.*; // PORT: §3.5 using static Parsers<char>

/// <summary>
/// A predifined set of common character parsers that produce no output.
/// These are typically used to do look-ahead scanning.
/// </summary>
public final class CharScanners
{
    private CharScanners() // PORT: §3.5 static class
    {
    }

    /// <summary>
    /// A parser that matches a sequence of text characters.
    /// </summary>
    public static Parser<Character> chars(String text, boolean ignoreCase)
    {
        return match(text, ignoreCase);
    }

    public static Parser<Character> chars(String text) // PORT: §3.12 optional parameter ignoreCase = false
    {
        return chars(text, false);
    }

    /// <summary>
    /// A parser that matches a single character.
    /// </summary>
    public static Parser<Character> char_(char ch, boolean ignoreCase) // PORT: §2.3 reserved word Char
    {
        return match(ch, ignoreCase);
    }

    public static Parser<Character> char_(char ch) // PORT: §3.12 optional parameter ignoreCase = false; §2.3 reserved word
    {
        return char_(ch, false);
    }

    /// <summary>
    /// A parser that matches a single letter.
    /// </summary>
    public static final Parser<Character> Letter =
        Parsers.<Character>match((java.util.function.Predicate<Character>) TextFacts::isLetter).withTag("<letter>"); // PORT: §3.8 Func<char,bool>

    /// <summary>
    /// A parser that matches a single digit.
    /// </summary>
    public static final Parser<Character> Digit =
        Parsers.<Character>match((java.util.function.Predicate<Character>) TextFacts::isDigit).withTag("<digit>"); // PORT: §3.8

    /// <summary>
    /// A parser that matches a single hexadecimal digit.
    /// </summary>
    public static final Parser<Character> HexDigit =
        Parsers.<Character>match((java.util.function.Predicate<Character>) TextFacts::isHexDigit).withTag("<hex-digit>"); // PORT: §3.8

    /// <summary>
    /// A parser that matches a single whitespace character.
    /// </summary>
    public static final Parser<Character> Whitespace =
        Parsers.<Character>match((java.util.function.Predicate<Character>) TextFacts::isWhitespace).withTag("<whitespace>"); // PORT: §3.8

    /// <summary>
    /// A parser that matches a line break.
    /// </summary>
    @SuppressWarnings("unchecked") // PORT: §3.10 generic varargs
    public static final Parser<Character> LineBreak =
        Parsers.<Character>or(
            Parsers.<Character>and(char_('\r'), Parsers.<Character>optional(char_('\n'))),
            char_('\n'),
            char_(' '),
            char_(' '))
        .withTag("<line-break>");
}
