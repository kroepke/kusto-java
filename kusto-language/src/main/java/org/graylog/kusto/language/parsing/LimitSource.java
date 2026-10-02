// Ported from: src/Kusto.Language/Parser/Combinators/LimitSource.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

/// <summary>
/// An input source with an artificially constrained index limit.
/// </summary>
public final class LimitSource<TInput> extends Source<TInput>
{
    private Source<TInput> _source;
    private int _limit;

    public LimitSource(Source<TInput> source, int limit)
    {
        _source = source;
        _limit = limit;
    }

    @Override
    public boolean isEnd(int n) // PORT: §3.12 optional parameter n = 0; isEnd() is inherited from Source
    {
        return n >= _limit || _source.isEnd(n);
    }

    @Override
    public TInput peek(int n) // PORT: §3.12 optional parameter n = 0; peek() is inherited from Source
    {
        return _source.peek(n);
    }
}
