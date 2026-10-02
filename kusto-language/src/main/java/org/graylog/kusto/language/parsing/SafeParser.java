// Ported from: src/Kusto.Language/Parser/Combinators/SafeParse.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.List;

import org.graylog.kusto.language.utils.ObjectPool;

public final class SafeParser
{
    private SafeParser() // PORT: §3.5 static class
    {
    }

    @SuppressWarnings("unchecked")
    public static <TInput> int parseSafe(Parser<TInput> parser, Source<TInput> source, int inputStart, List<Object> output, int outputStart) // PORT: §3.5 extension method
    {
        var ssp = (StackSafeParser<TInput>) ParserPool.Pool.allocateFromPool(); // PORT: §3.9 shared raw pool
        try
        {
            ssp.initialize(source, output);
            return ssp.parse(parser, inputStart, outputStart);
        }
        finally
        {
            ParserPool.Pool.returnToPool(ssp);
        }
    }

    // PORT: §3.9 static field of a generic class (ParserPool<TInput>): one shared raw pool for all TInput.
    // A pooled StackSafeParser holds no TInput state after clear(), so sharing is not observable.
    private static final class ParserPool
    {
        @SuppressWarnings("rawtypes")
        public static final ObjectPool<StackSafeParser> Pool =
            new ObjectPool<StackSafeParser>(() -> new StackSafeParser<Object>(null, null), p -> p.clear());
    }
}
