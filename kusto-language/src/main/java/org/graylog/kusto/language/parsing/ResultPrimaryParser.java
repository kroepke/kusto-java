// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/ResultPrimaryParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.List;

/// <summary>
/// A parser with output list based parsing implemented over result based parsing.
/// These parsers should *not* wrap other parsers.
/// </summary>
public abstract class ResultPrimaryParser<TInput, TOutput> extends Parser2<TInput, TOutput>
{
    @Override
    public int parse(Source<TInput> source, int inputStart, List<Object> output, int outputStart)
    {
        var result = this.parse(source, inputStart);

        if (result.length() >= 0)
        {
            output.add(result.value());
        }

        return result.length();
    }
}
