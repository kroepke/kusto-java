// Ported from: src/Kusto.Language/Editor/CompletionHint.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.editor;

/// <summary>
/// A hint that describes the category of syntax element that belongs in
/// an associated syntax node location, used to determine appropriate
/// completion items.
/// </summary>
// PORT: §3.17 [Flags] enum -> int constants holder with the upstream member names and values (D23); combine with | as in C#
public final class CompletionHint {
    private CompletionHint() {
    }

    /// <summary>
    /// No completion
    /// </summary>
    public static final int None = 0;

    /// <summary>
    /// Inherit hint from parent
    /// </summary>
    public static final int Inherit = 1;

    /// <summary>
    /// An expression (scalar or tabular)
    /// </summary>
    public static final int Expression = Inherit << 1;

    /// <summary>
    /// A scalar expression (non-boolean)
    /// </summary>
    public static final int Scalar = Expression << 1;

    /// <summary>
    /// A tabular expression
    /// </summary>
    public static final int Tabular = Scalar << 1;

    /// <summary>
    /// Any non-scalar expression
    /// </summary>
    public static final int NonScalar = Tabular << 1;

    /// <summary>
    /// A boolean expression
    /// </summary>
    public static final int Boolean = NonScalar << 1;

    /// <summary>
    /// A numeric expression
    /// </summary>
    public static final int Number = Boolean << 1;

    /// <summary>
    /// Literal value
    /// </summary>
    public static final int Literal = Number << 1;

    /// <summary>
    /// aggregate expression
    /// </summary>
    public static final int Aggregate = Literal << 1;

    /// <summary>
    /// A tabular function
    /// </summary>
    public static final int TabularFunction = Aggregate << 1;

    /// <summary>
    /// A scalar function
    /// </summary>
    public static final int ScalarFunction = TabularFunction << 1;

    /// <summary>
    /// A database function
    /// </summary>
    public static final int DatabaseFunction = ScalarFunction << 1;

    /// <summary>
    /// Any function
    /// </summary>
    public static final int Function = DatabaseFunction << 1;

    /// <summary>
    /// A name declaration
    /// </summary>
    public static final int Declaration = Function << 1;

    /// <summary>
    /// A column name reference
    /// </summary>
    public static final int Column = Declaration << 1;

    /// <summary>
    /// A table name reference
    /// </summary>
    public static final int Table = Column << 1;

    /// <summary>
    /// A database expression: database('database')
    /// </summary>
    public static final int Database = Table << 1;

    /// <summary>
    /// A cluster expression: cluster('cluster')
    /// </summary>
    public static final int Cluster = Database << 1;

    /// <summary>
    /// An entity group
    /// </summary>
    public static final int EntityGroup = Cluster << 1;

    /// <summary>
    /// A graph
    /// </summary>
    public static final int Graph = EntityGroup << 1;

    /// <summary>
    /// Syntax completions only
    /// </summary>
    public static final int Syntax = Graph << 1;

    /// <summary>
    /// A query operator
    /// </summary>
    public static final int Query = Syntax << 1;

    /// <summary>
    /// A command name
    /// </summary>
    public static final int Command = Query << 1;

    /// <summary>
    /// A keyword
    /// </summary>
    public static final int Keyword = Command << 1;

    /// <summary>
    /// A clause
    /// </summary>
    public static final int Clause = Keyword << 1;

    /// <summary>
    /// A materialized view
    /// </summary>
    public static final int MaterializedView = Clause << 1;

    /// <summary>
    /// A query option
    /// </summary>
    public static final int Option = MaterializedView << 1;

    /// <summary>
    /// An external table
    /// </summary>
    public static final int ExternalTable = Option << 1;

    /// <summary>
    /// A stored query result
    /// </summary>
    public static final int StoredQueryResult = ExternalTable << 1;

    /// <summary>
    /// A stored database graph model
    /// </summary>
    public static final int GraphModel = StoredQueryResult << 1;

    /// <summary>
    /// A stored database graph snapshot.
    /// </summary>
    public static final int GraphSnapshot = GraphModel << 1;
}
