// Ported from: src/Kusto.Language/Parser/Combinators/ParserExtensions.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.List;

import org.graylog.kusto.language.utils.dotnet.Out;

public final class ParserExtensions
{
    private ParserExtensions() // PORT: §3.5 static class
    {
    }

    /// <summary>
    /// Parses the text into the output list.
    /// </summary>
    public static int parse(Parser<Character> parser, String text, List<Object> output) // PORT: §3.5 extension method
    {
        return parser.parse(new TextSource(text), 0, output, 0);
    }

    /// <summary>
    /// Parses the text and returns a single output value in <see cref="ParseResult{TOutput}"/>.
    /// </summary>
    public static <TOutput> ParseResult<TOutput> parse(Parser2<Character, TOutput> parser, String text) // PORT: §3.5 extension method; §2.4 Parser<char,TOutput> -> Parser2
    {
        return parser.parse(new TextSource(text), 0);
    }

    /// <summary>
    /// Returns true if the parser successfully parses the text. 
    /// Returns the produced value as an out parameter.
    /// </summary>
    public static <TOutput> boolean tryparse(Parser2<Character, TOutput> parser, String text, Out<TOutput> value) // PORT: §3.5 extension method; §3.3 out
    {
        var result = parse(parser, text);
        value.value = result.value();
        return result.length() > 0;
    }
}
