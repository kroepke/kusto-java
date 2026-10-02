// Ported from: src/Kusto.Language/Symbols/SymbolKind.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

public enum SymbolKind
{
    /// <summary>
    /// Not a symbol
    /// </summary>
    None, // PORT: §3.17 explicit '= 0' equals the ordinal

    /// <summary>
    /// A primitive scalar type
    /// </summary>
    Primitive,

    /// <summary>
    /// Declared local variable
    /// </summary>
    Variable,

    /// <summary>
    /// Function (local, external or built-in)
    /// </summary>
    Function,

    /// <summary>
    /// Function parameter
    /// </summary>
    Parameter,

    /// <summary>
    /// Pattern
    /// </summary>
    Pattern,

    /// <summary>
    /// A tuple type (a set of multiple columns)
    /// </summary>
    Tuple,

    /// <summary>
    /// A bag of properties (dynamic object)
    /// </summary>
    Bag,

    /// <summary>
    /// An array of values (dynamic array)
    /// </summary>
    Array,

    /// <summary>
    /// Column (of table or tuple)
    /// </summary>
    Column,

    /// <summary>
    /// A table (has columns)
    /// </summary>
    Table,

    /// <summary>
    /// A graph symbol (has nodes and edges)
    /// </summary>
    Graph, 

    /// <summary>
    /// A database (contains tables, functions, etc)
    /// </summary>
    Database,

    /// <summary>
    /// A cluster (contains databases)
    /// </summary>
    Cluster,

    /// <summary>
    /// Built-in language operator  (+, ==, etc)
    /// </summary>
    Operator,

    /// <summary>
    /// A symbol corresponding to a group of symbols
    /// </summary>
    Group,

    /// <summary>
    /// The void type (when there is no type)
    /// </summary>
    Void,

    /// <summary>
    /// The error type (when a type is unknown due to an error)
    /// </summary>
    Error,

    /// <summary>
    /// Command statements
    /// </summary>
    Command,

    /// <summary>
    /// Materialized view
    /// </summary>
    MaterializedView,

    /// <summary>
    /// A query option (assigned via set statement)
    /// </summary>
    Option,

    /// <summary>
    /// A query operator parameter
    /// </summary>
    QueryOperatorParameter,

    /// <summary>
    /// An named entity group
    /// </summary>
    EntityGroup,

    /// <summary>
    /// A named entity group element (via macro-expand)
    /// </summary>
    EntityGroupElement,

    /// <summary>
    /// A stored query result element.
    /// </summary>
    StoredQueryResult,

    /// <summary>
    /// A model of a graph that contains one or more snapshots
    /// </summary>
    GraphModel,

    /// <summary>
    /// A snapshot of a graph model
    /// </summary>
    GraphSnapshot
}
