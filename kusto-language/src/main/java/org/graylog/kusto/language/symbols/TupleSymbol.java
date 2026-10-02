// Ported from: src/Kusto.Language/Symbols/TupleSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graylog.kusto.language.syntax.SyntaxNode;
import org.graylog.kusto.language.utils.ArgumentCheckers;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.dotnet.Linq;

/// <summary>
/// A symbol for a tuple of one or more name/value pairs.
/// </summary>
public final class TupleSymbol extends ScalarSymbol
{
    private final List<ColumnSymbol> columns;
    public List<ColumnSymbol> columns() { return columns; }

    @Override
    public List<Symbol> members() { return Collections.unmodifiableList(this.columns()); } // PORT: §3.10 covariance

    @Override
    public SymbolKind kind() { return SymbolKind.Tuple; }

    private final TableSymbol relatedTable;
    public TableSymbol relatedTable() { return relatedTable; }

    public TupleSymbol(Iterable<ColumnSymbol> columns, TableSymbol relatedTable)
    {
        super("tuple");
        this.columns = ArgumentCheckers.checkArgumentNullOrElementNull(ListExtensions.toReadOnly(columns), "columns" /* nameof */); // PORT: §3.5
        this.relatedTable = relatedTable;
    }

    public TupleSymbol(Iterable<ColumnSymbol> columns) // PORT: §3.12 optional parameter relatedTable = null
    {
        this(columns, null);
    }

    public TupleSymbol(ColumnSymbol... columns)
    {
        this(columns != null ? Arrays.asList(columns) : (Iterable<ColumnSymbol>)null); // PORT: §3.17 array as IEnumerable
    }

    /// <summary>
    /// Create a <see cref="TupleSymbol"/> instance from a schema description: (col: type, ...)
    /// </summary>
    public static TupleSymbol from(String schema) // PORT: §3.10 `new` static hiding of ScalarSymbol.From
    {
        return ScalarTypes.getTuple(schema);
    }

    /// <summary>
    /// If true, then a single column tuple can be reduced to the scalar value of that column.
    /// </summary>
    public boolean isReducibleToScalar() { return this.columns().size() == 1 && this.relatedTable() == null; }

    /// <summary>
    /// Returns a new <see cref="TupleSymbol"/> instance with the specified columns. 
    /// </summary>
    public TupleSymbol withColumns(Iterable<ColumnSymbol> columns)
    {
        return new TupleSymbol(columns, this.relatedTable());
    }

    /// <summary>
    /// Returns a new <see cref="TupleSymbol"/> instance with the columns updated to have the specified source. 
    /// </summary>
    public TupleSymbol withSource(SyntaxNode source)
    {
        return this.withColumns(Linq.select(this.columns(), c -> c.withSource(source))); // PORT: §3.6
    }

    // PORT: §3.10 params T[]: `new TupleSymbol(null)` binds the params ColumnSymbol[] constructor in C# (array is more specific); the cast reproduces that choice
    public static final TupleSymbol Empty = new TupleSymbol((ColumnSymbol[])null);
}
