// Ported from: src/Kusto.Language/Symbols/Scope.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import java.util.List;

public abstract class Scope
{
    /// <summary>
    /// Gets all symbols in the scope with the specified name and kind.
    /// </summary>
    public abstract void getSymbols(String name, int match, List<Symbol> symbols); // PORT: §3.17 SymbolMatch is an int holder (D23)

    /// <summary>
    /// Gets all the symbols in the scope with the specified kind.
    /// </summary>
    public void getSymbols(int match, List<Symbol> symbols) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        this.getSymbols(null, match, symbols);
    }
}
