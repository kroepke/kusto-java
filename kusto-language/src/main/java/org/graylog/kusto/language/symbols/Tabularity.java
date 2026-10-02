// Ported from: src/Kusto.Language/Symbols/Tabularity.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

public enum Tabularity
{
    /// <summary>
    /// The symbol is not scalar or tabular; void, error
    /// </summary>
    None,

    /// <summary>
    /// The symbol is scalar; scalar types, columns, tuples and some functions.
    /// </summary>
    Scalar,

    /// <summary>
    /// The symbol is tabular or related; tables, some functions and patterns.
    /// </summary>
    Tabular,

    /// <summary>
    /// Not scalar or tabular entity; cluster, database, entity_group, graph, etc.
    /// </summary>
    Other,

    /// <summary>
    /// The tabularity is not known.
    /// </summary>
    Unknown,

    /// <summary>
    /// The tabularity was unspecified and should be determined based on other state.
    /// </summary>
    Unspecified
}
