// Ported from: src/Kusto.Language/Symbols/SymbolMatch.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import org.graylog.kusto.language.utils.dotnet.DotNetStrings;

public final class SymbolMatchExtensions
{
    private SymbolMatchExtensions() // PORT: §3.5 static class
    {
    }

    public static boolean matches(Symbol symbol, String name, int match, boolean ignoreCase) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        if (name != null)
        {
            if (ignoreCase)
            {
                if (DotNetStrings.compare(symbol.name(), name, ignoreCase) != 0) // PORT: §5.4 culture compare is ordinal (D12)
                    return false;
            }
            else 
            {
                // compare first character before calling string.Compare (perf)
                var sn = symbol.name();
                if (name.length() == 0 
                    || sn.length() == 0 
                    || name.charAt(0) != sn.charAt(0)
                    || DotNetStrings.compare(sn, name) != 0) // PORT: §5.4 culture compare is ordinal (D12)
                    return false;
            }
        }

        if ((match & SymbolMatch.Column) != 0 && symbol instanceof ColumnSymbol)
            return true;

        if ((match & SymbolMatch.Table) != 0 && symbol instanceof TableSymbol ts && !ts.isExternal() && !ts.isMaterializedView() && !ts.isStoredQueryResult())
            return true;

        if ((match & SymbolMatch.ExternalTable) != 0 && symbol instanceof TableSymbol ets && ets.isExternal())
            return true;

        if ((match & SymbolMatch.MaterializedView) != 0 && symbol instanceof TableSymbol mv && mv.isMaterializedView())
            return true;

        if ((match & SymbolMatch.Database) != 0 && symbol instanceof DatabaseSymbol)
            return true;

        if ((match & SymbolMatch.Cluster) != 0 && symbol instanceof ClusterSymbol)
            return true;

        if ((match & SymbolMatch.EntityGroup) != 0 && isTypeOrVariable(EntityGroupSymbol.class, symbol)) // PORT: §3.10
            return true;

        if ((match & SymbolMatch.EntityGroupElement) != 0 && isTypeOrVariable(EntityGroupElementSymbol.class, symbol)) // PORT: §3.10
            return true;

        if ((match & SymbolMatch.Graph) != 0 && isTypeOrVariable(GraphSymbol.class, symbol)) // PORT: §3.10
            return true;

        if ((match & SymbolMatch.GraphModel) != 0 && symbol instanceof GraphModelSymbol)
            return true;

        if ((match & SymbolMatch.GraphSnapshot) != 0 && symbol instanceof GraphSnapshotSymbol)
            return true;

        if ((match & SymbolMatch.StoredQueryResult) != 0 && isTypeOrVariable(StoredQueryResultSymbol.class, symbol)) // PORT: §3.10
            return true;

        // Tabularity Filters

        // if symbol is only allowed to be scalar but it is not 
        if ((match & SymbolMatch.Scalar) != 0 
            && (match & SymbolMatch.NonScalar) == 0  // not claimed to allow non-scalar
            && (match & SymbolMatch.Tabular) == 0    // tabular not allowed
            && !symbol.isScalar())
            return false;

        // if symbol is not allowed to be scalar but it is
        if ((match & SymbolMatch.NonScalar) != 0 
            && (match & SymbolMatch.Scalar) == 0 // did not also claim it could be scalar
            && symbol.tabularity() == Tabularity.Scalar)
            return false;

        // if symbol is only allowed to be tabular but it is not
        if ((match & SymbolMatch.Tabular) != 0 
            && (match & SymbolMatch.Scalar) == 0 
            && (match & SymbolMatch.NonScalar) == 0
            && !symbol.isTabular())
            return false;

        // The remaining symbols are affected by tabularity filters above
        if ((match & SymbolMatch.Function) != 0 && (symbol instanceof FunctionSymbol || symbol instanceof PatternSymbol))
            return true;

        if ((match & SymbolMatch.View) != 0 && (symbol instanceof FunctionSymbol fs2 && fs2.isView()))
            return true;

        if ((match & SymbolMatch.Local) != 0 && (symbol instanceof VariableSymbol || symbol instanceof ParameterSymbol))
            return true;

        return false;
    }

    public static boolean matches(Symbol symbol, String name, int match) // PORT: §3.12 optional parameter ignoreCase = false
    {
        return matches(symbol, name, match, false);
    }

    public static boolean matches(Symbol symbol, int match)
    {
        return matches(symbol, null, match);
    }

    /// <summary>
    /// True if the symbol matches the type or is a variable with a value that matches the type.
    /// </summary>
    private static <T> boolean isTypeOrVariable(Class<T> type, Symbol symbol) // PORT: §3.10 reified type test → Class<T>
    {
        return type.isInstance(symbol)
            || (symbol instanceof VariableSymbol vs && type.isInstance(vs.type()));
    }
}
