// Ported from: src/Kusto.Language/Symbols/TableSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

import org.graylog.kusto.language.utils.dotnet.Internal;

public class MaterializedViewSymbol extends TableSymbol
{
    /// <summary>
    /// The query that that is the source of the materialized view.
    /// </summary>
    private String materializedViewQuery;
    public String materializedViewQuery() { return this.materializedViewQuery; }

    // TODO: find better solution for storing this data for analyzer
    private MaterializedViewKind materializedViewKind = MaterializedViewKind.Unknown; // PORT: §3.9 default(enum)
    @Internal
    public MaterializedViewKind materializedViewKind() { return this.materializedViewKind; }
    @Internal
    public void setMaterializedViewKind(MaterializedViewKind value) { this.materializedViewKind = value; }

    private MaterializedViewSymbol(String name, int state, Iterable<ColumnSymbol> columns, String description, String query) // PORT: §3.17 TableState → int
    {
        super(name, state, columns, description);
        this.materializedViewQuery = query;
    }

    @Internal
    public MaterializedViewSymbol(TableSymbol sourceTable, String query)
    {
        super(sourceTable);
        this.materializedViewQuery = query;
    }

    @Internal
    public MaterializedViewSymbol(TableSymbol sourceTable) // PORT: §3.12 query = null
    {
        this(sourceTable, (String) null);
    }

    public MaterializedViewSymbol(String name, Iterable<ColumnSymbol> columns, String query, String description)
    {
        this(name, TableState.None, columns, description, query);
    }

    public MaterializedViewSymbol(String name, Iterable<ColumnSymbol> columns, String query) // PORT: §3.12 description = null
    {
        this(name, columns, query, (String) null);
    }

    public MaterializedViewSymbol(String name, String columns, String query, String description)
    {
        this(name, TableSymbol.from(columns).columns(), query, description);
    }

    public MaterializedViewSymbol(String name, String columns, String query) // PORT: §3.12 description = null
    {
        this(name, columns, query, (String) null);
    }

    @Override
    protected TableSymbol create(String name, int state, Iterable<ColumnSymbol> columns, String description) // PORT: §3.17 TableState → int
    {
        return new MaterializedViewSymbol(name, state, columns, description, this.materializedViewQuery());
    }

    @Override
    public SymbolKind kind() { return SymbolKind.MaterializedView; }
}
