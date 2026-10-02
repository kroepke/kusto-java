// Ported from: src/Kusto.Language/Parser/Combinators/ParseResult.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

// PORT: §3.2 struct -> record (§3.18); default(ParseResult<T>) is never used, Java null is never a result
public record ParseResult<TOutput>(
    /// <summary>
    /// The number of input items consumed by the parser or a negative number if the parsing failed.
    /// </summary>
    int length,

    /// <summary>
    /// The single produced result of the parser.
    /// </summary>
    TOutput value)
{
    /// <summary>
    /// True if the parse was successful.
    /// </summary>
    public boolean succeeded()
    {
        return length() >= 0;
    }
}
