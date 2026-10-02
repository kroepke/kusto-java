// Ported from: src/Kusto.Language/Symbols/GraphModelSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import org.graylog.kusto.language.utils.dotnet.Internal;

public class GraphSnapshotSymbol extends Symbol
{
    private GraphModelSymbol model;
    public GraphModelSymbol model() { return this.model; }

    @Internal
    public void setModel(GraphModelSymbol value) { this.model = value; }

    public GraphSnapshotSymbol(String name)
    {
        super(name);
    }

    @Override
    public Tabularity tabularity() { return Tabularity.None; }

    @Override
    public SymbolKind kind() { return SymbolKind.GraphSnapshot; }
}
