// Ported from: src/Kusto.Language/Symbols/SymbolMatch.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

// [Flags]
public final class SymbolMatch // PORT: §3.17 [Flags] enum → int constants holder (D23)
{
    private SymbolMatch() // PORT: §3.17
    {
    }

    public static final int None = 0;

    /// <summary>
    /// Any column
    /// </summary>
    public static final int Column = 1;

    /// <summary>
    /// Any table (not external)
    /// </summary>
    public static final int Table = Column << 1;

    /// <summary>
    /// Any external table
    /// </summary>
    public static final int ExternalTable = Table << 1;

    /// <summary>
    /// Any function
    /// </summary>
    public static final int Function = ExternalTable << 1;

    /// <summary>
    /// Any local view
    /// </summary>
    public static final int View = Function << 1;

    /// <summary>
    /// Any local variable or parameter
    /// </summary>
    public static final int Local = View << 1;

    /// <summary>
    /// Any database
    /// </summary>
    public static final int Database = Local << 1;

    /// <summary>
    /// Any cluster
    /// </summary>
    public static final int Cluster = Database << 1;

    /// <summary>
    /// Any entity group
    /// </summary>
    public static final int EntityGroup = Cluster << 1;

    /// <summary>
    /// Any entity group element
    /// </summary>
    public static final int EntityGroupElement = EntityGroup << 1;

    /// <summary>
    /// Include scalar items (applies to variables and functions)
    /// </summary>
    public static final int Scalar = EntityGroupElement << 1;

    /// <summary>
    /// Include tabular items (applies to variables and functions)
    /// </summary>
    public static final int Tabular = Scalar << 1;

    /// <summary>
    /// Include non-scalar items (applies to variable and functions)
    /// </summary>
    public static final int NonScalar = Tabular << 1;

    /// <summary>
    /// Any materialized view
    /// </summary>
    public static final int MaterializedView = NonScalar << 1;

    /// <summary>
    /// Any query option
    /// </summary>
    public static final int Option = MaterializedView << 1;

    /// <summary>
    /// Any graph
    /// </summary>
    public static final int Graph = Option << 1;

    /// <summary>
    /// Any stored query result
    /// </summary>
    public static final int StoredQueryResult = Graph << 1;

    /// <summary>
    /// Any graph model
    /// </summary>
    public static final int GraphModel = StoredQueryResult << 1;

    /// <summary>
    /// Any graph snapshot
    /// </summary>
    public static final int GraphSnapshot = GraphModel << 1;

    /// <summary>
    /// Any column, table, function or local, scalar or tabular, database or cluster
    /// </summary>
    public static final int Any = Column | Table | Function | View | Local | Database | Cluster | MaterializedView | EntityGroup | EntityGroupElement | Graph | GraphModel | GraphSnapshot;

    /// <summary>
    /// Any column, table, function or local, scalar or tabular
    /// </summary>
    public static final int Default = Column | Table | Function | View | Local | MaterializedView | EntityGroup | EntityGroupElement | Graph | ExternalTable;
}
