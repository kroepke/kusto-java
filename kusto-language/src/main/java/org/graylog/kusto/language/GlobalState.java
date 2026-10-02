// Ported from: src/Kusto.Language/GlobalState.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
// PORT-SKELETON: W4

package org.graylog.kusto.language;

import org.graylog.kusto.language.symbols.FunctionSymbol;

/// <summary>
/// The global state that a kusto query is associated with.
/// </summary>
public final class GlobalState
{
    /// <summary>
    /// Returns true if the function is a built-in function.
    /// </summary>
    public boolean isBuiltInFunction(FunctionSymbol fn) // PORT-PENDING: W4
    {
        throw new UnsupportedOperationException("PORT-PENDING: W4");
    }

    /// <summary>
    /// Gets the value for the specified property
    /// </summary>
    public <T> T getProperty(GlobalStateProperty1<T> property) // PORT-PENDING: W4
    {
        throw new UnsupportedOperationException("PORT-PENDING: W4");
    }

    /// <summary>
    /// The default <see cref="GlobalState"/>
    /// </summary>
    // PORT: §2.3 property Default → default_() (keyword); §3.9 W4 makes it a CAS-published lazy singleton
    public static GlobalState default_() // PORT-PENDING: W4
    {
        throw new UnsupportedOperationException("PORT-PENDING: W4");
    }
}
