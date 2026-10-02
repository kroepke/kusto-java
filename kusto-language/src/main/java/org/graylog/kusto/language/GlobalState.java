// Ported from: src/Kusto.Language/GlobalState.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
// PORT-SKELETON: W4

package org.graylog.kusto.language;

/// <summary>
/// The global state that a kusto query is associated with.
/// </summary>
public final class GlobalState
{
    /// <summary>
    /// Gets the value for the specified property
    /// </summary>
    public <T> T getProperty(GlobalStateProperty1<T> property) // PORT-PENDING: W4
    {
        throw new UnsupportedOperationException("PORT-PENDING: W4");
    }
}
