// Ported from: src/Kusto.Language/Symbols/ScalarSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import java.util.Arrays;
import java.util.List;

/// <summary>
/// A base type for all scalar types.
/// </summary>
public abstract class ScalarSymbol extends TypeSymbol
{
    protected ScalarSymbol(String name)
    {
        super(name);
    }

    @Override
    public Tabularity tabularity() { return Tabularity.Scalar; }

    /// <summary>
    ///  Other names for this type.
    /// </summary>
    public List<String> aliases() { return None; }

    private static final List<String> None = Arrays.asList(new String[] { }); // PORT: §3.17 array as IReadOnlyList

    public boolean isInteger() { return false; }
    public boolean isNumeric() { return false; }
    public boolean isInterval() { return false; }
    public boolean isSummable() { return false; }
    public boolean isOrderable() { return false; }
    public boolean isMultiValue() { return false; }

    /// <summary>
    /// True if this symbol is wider than the specified symbol.
    /// </summary>
    public boolean isWiderThan(ScalarSymbol scalar) { return false; }

    /// <summary>
    /// Gets the <see cref="ScalarSymbol"/> for the type name.
    /// </summary>
    public static ScalarSymbol from(String typeName)
    {
        return ScalarTypes.getSymbol(typeName);
    }
}
