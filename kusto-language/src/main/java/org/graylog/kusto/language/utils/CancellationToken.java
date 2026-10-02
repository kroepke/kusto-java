// Ported from: src/Kusto.Language/Utils/Cancellation.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.util.function.BooleanSupplier;

import org.graylog.kusto.language.utils.dotnet.OperationCanceledException;

// PORT: D22 the BRIDGE shape is ported (a wrapped cancellation test); Java has no System.Threading.CancellationToken.
public final class CancellationToken {
    // PORT: §3.2 default(CancellationToken) is a token that is never cancelled
    public static final CancellationToken NONE = new CancellationToken();

    private final BooleanSupplier _fnCanceled; // PORT: §3.8 Func<bool>

    public CancellationToken(BooleanSupplier fnCanceled) {
        _fnCanceled = fnCanceled;
    }

    public CancellationToken() { // PORT: §3.12 optional parameter fnCanceled = null
        this(null);
    }

    // PORT: §3.8 replaces the implicit conversion from System.Threading.CancellationToken (Cancellation.cs:30)
    public static CancellationToken of(BooleanSupplier fnCanceled) {
        return new CancellationToken(fnCanceled);
    }

    public void throwIfCancellationRequested() {
        if (_fnCanceled != null && _fnCanceled.getAsBoolean())
            throw new OperationCanceledException();
    }
}
