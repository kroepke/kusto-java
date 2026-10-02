// Ported from: src/Kusto.Language/Symbols/ScalarSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

// [Flags]
public final class ScalarFlags // PORT: §3.17 [Flags] enum → int constants holder (D23)
{
    private ScalarFlags() // PORT: §3.17
    {
    }

    public static final int None     = 0b0000_0000;

    /// <summary>
    /// Is an integer type
    /// </summary>
    public static final int Integer  = 0b0000_0001;

    /// <summary>
    /// Is a numeric type
    /// </summary>
    public static final int Numeric  = 0b0000_0010;

    /// <summary>
    /// Is an interval type (typically add/subtract operator is defined for this)
    /// </summary>
    public static final int Interval = 0b0000_0100;

    /// <summary>
    /// Can be used in the sum aggregate
    /// </summary>
    public static final int Summable = 0b0000_1000;

    /// <summary>
    /// Can be used in order by or arg_max aggregate
    /// </summary>
    public static final int Orderable = 0b0001_0000;

    /// <summary>
    /// All flags
    /// </summary>
    public static final int All = Integer | Numeric | Interval | Summable | Orderable;
}
