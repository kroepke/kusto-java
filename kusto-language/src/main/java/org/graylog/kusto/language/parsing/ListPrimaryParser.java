// Ported from: src/Kusto.Language/Parser/Combinators/Parsers/ListPrimaryParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.utils.ObjectPool;

/// <summary>
/// A parser with result based parsing implemented over output-list based parsing.
/// </summary>
public abstract class ListPrimaryParser<TInput, TOutput> extends Parser2<TInput, TOutput>
{
    /// <summary>
    /// Common pool of output lists: don't allocate temporary lists!
    /// </summary>
    // PORT: §3.9 static field of a generic class: one pool shared by all instantiations
    private static final ObjectPool<List<Object>> s_outputListPool =
        new ObjectPool<List<Object>>(() -> new ArrayList<Object>(), list -> list.clear(), 50);

    @Override
    @SuppressWarnings("unchecked")
    public ParseResult<TOutput> parse(Source<TInput> source, int start)
    {
        var list = s_outputListPool.allocateFromPool();
        try
        {
            var n = this.parse(source, start, list, 0);

            return new ParseResult<TOutput>(n,
                n >= 0 && list.size() > 0
                    ? (TOutput) list.get(0) // PORT: §3.10 unchecked cast
                    : null); // PORT: §3.10 default(TOutput)
        }
        finally
        {
            s_outputListPool.returnToPool(list);
        }
    }
}
