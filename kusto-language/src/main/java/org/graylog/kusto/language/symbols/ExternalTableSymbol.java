// Ported from: src/Kusto.Language/Symbols/TableSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

import java.util.Arrays;

import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// A table declared external to Kusto
/// </summary>
public class ExternalTableSymbol extends TableSymbol
{
    private ExternalTableSymbol(String name, int state, Iterable<ColumnSymbol> columns, String description) // PORT: §3.17 TableState → int
    {
        super(name, state, columns, description);
    }

    @Internal
    public ExternalTableSymbol(TableSymbol sourceTable)
    {
        super(sourceTable);
    }

    public ExternalTableSymbol(String name, Iterable<ColumnSymbol> columns, String description)
    {
        this(name, TableState.None, columns, description);
    }

    public ExternalTableSymbol(String name, Iterable<ColumnSymbol> columns) // PORT: §3.12 description = null
    {
        this(name, columns, (String) null);
    }

    public ExternalTableSymbol(String name, ColumnSymbol... columns)
    {
        this(name, (Iterable<ColumnSymbol>) (columns != null ? Arrays.asList(columns) : null)); // PORT: §3.10 params array → List
    }

    public ExternalTableSymbol(String name, String columns, String description)
    {
        this(name, TableSymbol.from(columns).columns(), description);
    }

    public ExternalTableSymbol(String name, String columns) // PORT: §3.12 description = null
    {
        this(name, columns, (String) null);
    }

    @Override
    protected TableSymbol create(String name, int state, Iterable<ColumnSymbol> columns, String description) // PORT: §3.17 TableState → int
    {
        return new ExternalTableSymbol(name, state, columns, description);
    }
}
