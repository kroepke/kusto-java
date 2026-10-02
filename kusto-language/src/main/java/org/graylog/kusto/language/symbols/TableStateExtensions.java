// Ported from: src/Kusto.Language/Symbols/TableState.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

public final class TableStateExtensions
{
    private TableStateExtensions() // PORT: §3.5 static class
    {
    }

    public static boolean has(int state, int test) // PORT: §3.17 TableState is an int holder (D23)
    {
        return (state & test) != 0;
    }

    public static int with(int state, int stateToAdd) // PORT: §3.17 TableState is an int holder (D23)
    {
        return state | stateToAdd;
    }

    public static int without(int state, int stateToRemove) // PORT: §3.17 TableState is an int holder (D23)
    {
        return state & ~stateToRemove;
    }

    /// <summary>
    /// The table is new, but inherits any global state from the current state.
    /// </summary>
    public static int new_(int state) // PORT: §2.3 reserved word; §3.17 TableState is an int holder (D23)
    {
        // new tables still inherit global states like serialized
        return with(state, TableState.Serialized);
    }

    /// <summary>
    /// The table is open, and may have more columns than specified.
    /// </summary>
    public static int open(int state) // PORT: §3.17 TableState is an int holder (D23)
    {
        return with(state, TableState.Open);
    }

    /// <summary>
    /// The table is not sorted.
    /// </summary>
    public static int unsorted(int state) // PORT: §3.17 TableState is an int holder (D23)
    {
        return without(state, TableState.Sorted);
    }
}
