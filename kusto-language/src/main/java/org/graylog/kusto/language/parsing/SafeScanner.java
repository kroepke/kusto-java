// Ported from: src/Kusto.Language/Parser/Combinators/SafeScan.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import org.graylog.kusto.language.utils.ObjectPool;

public final class SafeScanner
{
    private SafeScanner() // PORT: §3.5 static class
    {
    }

    @SuppressWarnings("unchecked")
    public static <TInput> int scanSafe(Parser<TInput> parser, Source<TInput> source, int start) // PORT: §3.5 extension method
    {
        var sss = (StackSafeScanner<TInput>) ScannerPool.Pool.allocateFromPool(); // PORT: §3.9 shared raw pool
        try
        {
            sss.initialize(source);
            return sss.scan(parser, start);
        }
        finally
        {
            ScannerPool.Pool.returnToPool(sss);
        }
    }

    // PORT: §3.9 static field of a generic class (ScannerPool<TInput>): one shared raw pool for all TInput.
    // A pooled StackSafeScanner holds no TInput state after clear(), so sharing is not observable.
    private static final class ScannerPool
    {
        @SuppressWarnings("rawtypes")
        public static final ObjectPool<StackSafeScanner> Pool =
            new ObjectPool<StackSafeScanner>(() -> new StackSafeScanner<Object>(null), s -> s.clear());
    }
}
