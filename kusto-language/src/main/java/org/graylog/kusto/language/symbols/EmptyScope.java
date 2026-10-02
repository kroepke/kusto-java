// Ported from: src/Kusto.Language/Symbols/EmptyScope.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import java.util.List;

public class EmptyScope extends Scope
{
    public static final EmptyScope Instance = new EmptyScope();

    @Override
    public void getSymbols(String name, int match, List<Symbol> symbols) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        // do nothing
    }
}
