// Ported from: src/Kusto.Language/Parser/Combinators/Source.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.lang.invoke.VarHandle;

import org.graylog.kusto.language.utils.Interlocked;

/// <summary>
/// An abstraction over a sequence of input items.
/// </summary>
public abstract class Source<T>
{
    // PORT: §3.13 stands in for `ref _cache`
    private static final VarHandle CACHE = Interlocked.handle(Source.class, "_cache", SourceCache.class);

    /// <summary>
    /// Gets the nth item from the current position in the source.
    /// </summary>
    public abstract T peek(int n);

    public T peek() // PORT: §3.12 optional parameter n = 0
    {
        return peek(0);
    }

    /// <summary>
    /// Returns true if the position is beyond the last element.
    /// </summary>
    public abstract boolean isEnd(int n);

    public boolean isEnd() // PORT: §3.12 optional parameter n = 0
    {
        return isEnd(0);
    }

    private volatile SourceCache _cache; // PORT: §3.13 CAS-published field is volatile
    public SourceCache cache()
    {
        if (_cache == null)
        {
            Interlocked.compareExchange(CACHE, this, new SourceCache(), null); // PORT: §3.13
        }
        return _cache;
    }
}
