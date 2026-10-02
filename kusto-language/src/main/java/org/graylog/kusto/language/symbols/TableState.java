// Ported from: src/Kusto.Language/Symbols/TableState.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

/// <summary>
/// The state of a <see cref="TableSymbol"/>
/// </summary>
// [Flags]
public final class TableState // PORT: §3.17 [Flags] enum → int constants holder (D23)
{
    private TableState() // PORT: §3.17
    {
    }

    public static final int None = 0;

    /// <summary>
    /// The table rows are serialized
    /// </summary>
    public static final int Serialized = 0b0000_0001;

    /// <summary>
    /// The table rows are sorted
    /// </summary>
    public static final int Sorted = 0b0000_0010;

    /// <summary>
    /// The table is open and may contain more columns than specified
    /// </summary>
    public static final int Open = 0b0000_0100;
}
