// Ported from: src/Kusto.Language/Symbols/TableSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import org.graylog.kusto.language.KustoFacts;
import org.graylog.kusto.language.binding.Binder;
import org.graylog.kusto.language.parsing.QueryParser;
import org.graylog.kusto.language.syntax.SyntaxNode;
import org.graylog.kusto.language.utils.ArgumentCheckers;
import org.graylog.kusto.language.utils.Interlocked;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Internal;
import org.graylog.kusto.language.utils.dotnet.Linq;
import org.graylog.kusto.language.utils.dotnet.Out;

/// <summary>
/// A symbol representing a table
/// </summary>
public class TableSymbol extends TypeSymbol
{
    /// <summary>
    /// The columns of the table.
    /// </summary>
    private final List<ColumnSymbol> columns;
    public List<ColumnSymbol> columns() { return this.columns; }

    /// <summary>
    /// The description of the table.
    /// </summary>
    private final String description;
    public String description() { return this.description; }

    // [Flags]
    protected static final class TableState // PORT: §3.17 [Flags] enum (ushort) → int constants holder (D23); shadows the top-level TableState as upstream does
    {
        private TableState() // PORT: §3.17
        {
        }

        public static final int None =              0b0000_0000;
        public static final int Serialized =        0b0000_0001;
        public static final int Sorted =            0b0000_0010;
        public static final int Open =              0b0000_0100;
    }

    /// <summary>
    /// The state of the table as bit flags
    /// </summary>
    private final int _state; // PORT: §3.17 TableState

    protected TableSymbol(String name, int state, Iterable<ColumnSymbol> columns, String description) // PORT: §3.17 TableState → int
    {
        super(name);
        _state = state;
        this.columns = ArgumentCheckers.checkArgumentNullOrElementNull(ListExtensions.toReadOnly(columns), "columns"); // PORT: §3.5
        this.description = description != null ? description : ""; // PORT: §3.14 ??
    }

    private TableSymbol(int state, Iterable<ColumnSymbol> columns, String description) // PORT: §3.17 TableState → int
    {
        this("", state, columns, description);
    }

    @Internal
    public TableSymbol(TableSymbol sourceTable)
    {
        this(sourceTable.name(), sourceTable._state, sourceTable.columns(), sourceTable.description());
    }

    public TableSymbol(String name, Iterable<ColumnSymbol> columns, String description)
    {
        this(name, TableState.None, columns, description);
    }

    public TableSymbol(String name, Iterable<ColumnSymbol> columns) // PORT: §3.12 description = null
    {
        this(name, columns, (String) null);
    }

    public TableSymbol(String name, String schema, String description)
    {
        this(name, TableSymbol.from(schema).columns(), description);
    }

    public TableSymbol(String name, String schema) // PORT: §3.12 description = null
    {
        this(name, schema, (String) null);
    }

    public TableSymbol(String name, ColumnSymbol... columns)
    {
        this(name, TableState.None, asList(columns), null); // PORT: §3.10
    }

    public TableSymbol(Iterable<ColumnSymbol> columns)
    {
        this("", TableState.None, columns, null);
    }

    public TableSymbol(ColumnSymbol... columns)
    {
        this((Iterable<ColumnSymbol>) asList(columns)); // PORT: §3.10
    }

    // PORT: §3.10 params ColumnSymbol[] → ColumnSymbol...; a null array reaches ToReadOnly as null (→ empty), as upstream.
    private static List<ColumnSymbol> asList(ColumnSymbol[] columns)
    {
        return columns != null ? Arrays.asList(columns) : null;
    }

    /// <summary>
    /// Gets a <see cref="TableSymbol"/> for the schema: (name:type, ...)
    /// </summary>
    public static TableSymbol from(String schema)
    {
        if (schema == null)
        {
            throw new NullPointerException("schema"); // PORT: §3.16 ArgumentNullException
        }

        schema = DotNetStrings.trim(schema); // PORT: §5.1

        if (schema.length() > 0 && schema.charAt(0) != '(')
            schema = "(" + schema;

        if (schema.length() > 0 && schema.charAt(schema.length() - 1) != ')')
            schema = schema + ")";

        var rowSchema = QueryParser.parseRowSchema(schema);
        if (rowSchema == null)
        {
            throw new IllegalStateException("Invalid schema: " + DotNet.str(schema)); // PORT: §3.16 InvalidOperationException; §3.14
        }

        var columns = new ArrayList<ColumnSymbol>();
        Binder.createColumnsFromRowSchema(rowSchema.columns(), columns);
        return new TableSymbol(columns);
    }

    @Override
    public SymbolKind kind() { return SymbolKind.Table; }

    @Override
    public List<Symbol> members() { return Collections.unmodifiableList(this.columns()); } // PORT: §3.10 covariant IReadOnlyList<ColumnSymbol> → IReadOnlyList<Symbol>

    @Override
    public Tabularity tabularity() { return Tabularity.Tabular; }

    /// <summary>
    /// True if the table is sorted.
    /// </summary>
    public boolean isSorted() { return (_state & TableState.Sorted) != 0; }

    /// <summary>
    /// True if the table is serialized.
    /// </summary>
    public boolean isSerialized() { return (_state & TableState.Serialized) != 0; }

    /// <summary>
    /// True if the table is open.
    /// </summary>
    public boolean isOpen() { return (_state & TableState.Open) != 0; }

    /// <summary>
    /// True if the table is external.
    /// </summary>
    public boolean isExternal() { return this instanceof ExternalTableSymbol; }

    /// <summary>
    /// True if the table is a materialized view.
    /// </summary>
    public boolean isMaterializedView() { return this instanceof MaterializedViewSymbol; }

    /// <summary>
    /// True if the table is a stored query result.
    /// </summary>
    public boolean isStoredQueryResult() { return this instanceof StoredQueryResultSymbol; }

    /// <summary>
    /// Construct a new <see cref="TableSymbol"/> if one of the optional arguments is different that the current values.
    /// </summary>
    protected TableSymbol with(
        String name,
        Integer state, // PORT: §3.7 TableState? → Integer
        Iterable<ColumnSymbol> columns,
        String description)
    {
        var useName = name != null ? name : this.name(); // PORT: §3.14 ??
        var useState = state != null ? state.intValue() : _state;
        var useColumns = columns != null ? columns : this.columns(); // PORT: §3.14 ??
        var useDescription = description != null ? description : this.description(); // PORT: §3.14 ??

        if (!Objects.equals(useName, this.name()) // PORT: §3.14 string ==
            || useState != _state
            || useColumns != this.columns() // reference comparison, as upstream (IEnumerable !=)
            || !Objects.equals(useDescription, this.description())) // PORT: §3.14 string ==
        {
            return create(useName, useState, useColumns, useDescription);
        }
        else
        {
            return this;
        }
    }

    protected TableSymbol with(String name, Integer state, Iterable<ColumnSymbol> columns) // PORT: §3.12 description = null
    {
        return with(name, state, columns, null);
    }

    protected TableSymbol with(String name, Integer state) // PORT: §3.12 columns = null, description = null
    {
        return with(name, state, null, null);
    }

    protected TableSymbol with(String name) // PORT: §3.12 state = null, columns = null, description = null
    {
        return with(name, null, null, null);
    }

    protected TableSymbol with() // PORT: §3.12 name = null, state = null, columns = null, description = null
    {
        return with(null, null, null, null);
    }

    /// <summary>
    /// Constructs a new <see cref="TableSymbol"/> given the specified values.
    /// </summary>
    protected TableSymbol create(String name, int state, Iterable<ColumnSymbol> columns, String description) // PORT: §3.17 TableState → int
    {
        return new TableSymbol(name, state, columns, description);
    }

    /// <summary>
    /// Returns a version of this <see cref="TableSymbol"/> with the specified name.
    /// </summary>
    public TableSymbol withName(String name)
    {
        return with(/*name:*/ name, null, null, null); // PORT: §3.12
    }

    /// <summary>
    /// Returns a version of this <see cref="TableSymbol"/> with the specified description.
    /// </summary>
    public TableSymbol withDescripton(String description)
    {
        return with(null, null, null, /*description:*/ description != null ? description : ""); // PORT: §3.12; §3.14 ??
    }

    /// <summary>
    /// Returns a version of this <see cref="TableSymbol"/> with the specified columns.
    /// </summary>
    public TableSymbol withColumns(Iterable<ColumnSymbol> columns)
    {
        return with(null, null, /*columns:*/ columns, null); // PORT: §3.12
    }

    /// <summary>
    /// Returns a version of this <see cref="TableSymbol"/> with the specified columns.
    /// </summary>
    public TableSymbol withColumns(ColumnSymbol... columns)
    {
        return withColumns((Iterable<ColumnSymbol>) asList(columns)); // PORT: §3.10
    }

    /// <summary>
    /// Returns a version of this <see cref="TableSymbol"/> with additional columns.
    /// </summary>
    public TableSymbol addColumns(Iterable<ColumnSymbol> columns)
    {
        if (columns == null)
            throw new NullPointerException("columns"); // PORT: §3.16 ArgumentNullException

        return with(null, null, /*columns:*/ Linq.concat(this.columns(), columns), null); // PORT: §3.12; §3.6
    }

    /// <summary>
    /// Returns a version of this <see cref="TableSymbol"/> with additional columns.
    /// </summary>
    public TableSymbol addColumns(ColumnSymbol... columns)
    {
        return addColumns((Iterable<ColumnSymbol>) asList(columns)); // PORT: §3.10
    }

    /// <summary>
    /// Returns a version of this <see cref="TableSymbol"/> with the specified state.
    /// </summary>
    private TableSymbol withState(int newState) // PORT: §3.17 TableState → int
    {
        return with(null, /*state:*/ newState, null, null); // PORT: §3.12
    }

    /// <summary>
    /// Returns a version of this <see cref="TableSymbol"/> with the specified <see cref="IsSerialized"/> property.
    /// </summary>
    public TableSymbol withIsSerialized(boolean isSerialized)
    {
        return withState(isSerialized ? (_state | TableState.Serialized) : (_state & ~TableState.Serialized));
    }

    /// <summary>
    /// Returns a version of this <see cref="TableSymbol"/> with the specified <see cref="IsSorted"/> property.
    /// </summary>
    public TableSymbol withIsSorted(boolean isSorted)
    {
        return withState(isSorted ? (_state | TableState.Sorted) : (_state & ~TableState.Sorted));
    }

    /// <summary>
    /// Returns a version of this <see cref="TableSymbol"/> with the specified <see cref="IsOpen"/> property.
    /// </summary>
    public TableSymbol withIsOpen(boolean isOpen)
    {
        return withState(isOpen ? (_state | TableState.Open) : (_state & ~TableState.Open));
    }

    /// <summary>
    /// Returns a version of this <see cref="TableSymbol"/> with the specified <see cref="IsExternal"/> property.
    /// </summary>
    public TableSymbol withIsExternal(boolean isExternal)
    {
        if (this instanceof ExternalTableSymbol == isExternal)
        {
            return this;
        }
        else if (isExternal)
        {
            return new ExternalTableSymbol(this);
        }
        else
        {
            return new TableSymbol(this);
        }
    }

    /// <summary>
    /// Returns a version of this <see cref="TableSymbol"/> with the specified <see cref="IsMaterializedView"/> property.
    /// </summary>
    public TableSymbol withIsMaterializedView(boolean isMaterializedView)
    {
        if (this instanceof MaterializedViewSymbol == isMaterializedView)
        {
            return this;
        }
        else if (isMaterializedView)
        {
            return new MaterializedViewSymbol(this);
        }
        else
        {
            return new TableSymbol(this);
        }
    }

    /// <summary>
    /// The state flags that are inheritable via <see cref="WithInheritableProperties(TableSymbol)"/>
    /// </summary>
    private static final int InheritableState = // PORT: §3.17 TableState → int
        TableState.Serialized | TableState.Sorted | TableState.Open;

    /// <summary>
    /// Returns a version of this <see cref="TableSymbol"/> with the same inheritable state properties as the specified table;
    /// IsSerialized, IsSorted, and IsOpen.
    /// </summary>
    public TableSymbol withInheritableProperties(TableSymbol table)
    {
        if (table == null)
            throw new NullPointerException("table"); // PORT: §3.16 ArgumentNullException

        var newState = (_state & ~InheritableState) | (table._state & InheritableState);
        return withState(newState);
    }

    private volatile LinkedHashMap<String, ColumnSymbol> lazyColumnMap; // PORT: §3.13 CAS-published; §3.17 Dictionary → LinkedHashMap
    private static final VarHandle LAZY_COLUMN_MAP = Interlocked.handle(TableSymbol.class, "lazyColumnMap", LinkedHashMap.class); // PORT: §3.13

    private LinkedHashMap<String, ColumnSymbol> columnMap()
    {
        if (this.lazyColumnMap == null)
        {
            var map = new LinkedHashMap<String, ColumnSymbol>(this.columns().size());

            for (var col : this.columns())
            {
                // do not add duplicate columns to dictionary
                // having duplicate columns is an error that should be caught elsewhere (via diagnostic)
                if (!map.containsKey(col.name()))
                {
                    DotNet.dictionaryAdd(map, col.name(), col); // PORT: §3.17 Dictionary.Add
                }
            }

            Interlocked.compareExchange(LAZY_COLUMN_MAP, this, map, null); // PORT: §3.13
        }

        return this.lazyColumnMap;
    }

    /// <summary>
    /// Gets the column with the specified name.
    /// </summary>
    public ColumnSymbol getColumn(String name)
    {
        var column = new Out<ColumnSymbol>(); // PORT: §3.3
        if (tryGetColumn(name, column))
        {
            return column.value;
        }
        else
        {
            throw new IllegalStateException("The column '" + DotNet.str(name) + "' does not exist."); // PORT: §3.16 InvalidOperationException; §3.14
        }
    }

    /// <summary>
    /// Gets the <see cref="ColumnSymbol"/> with the specified name.
    /// Returns true if the column is found, or false if there is no column with the specified name.
    /// </summary>
    public boolean tryGetColumn(String name, Out<ColumnSymbol> column) // PORT: §3.3 out → Out<T>
    {
        if (name == null)
            throw new NullPointerException("key"); // PORT: §3.16 Dictionary.TryGetValue(null) throws ArgumentNullException

        column.value = this.columnMap().get(name); // PORT: §3.3 TryGetValue; values are never null
        return column.value != null;
    }

    /// <summary>
    /// Gets the <see cref="ColumnSymbol"/>s with names that match the pattern.
    /// </summary>
    public void getMatchingColumns(String pattern, List<ColumnSymbol> columns)
    {
        if (!pattern.contains("*"))
        {
            var column = new Out<ColumnSymbol>(); // PORT: §3.3
            if (tryGetColumn(pattern, column))
            {
                columns.add(column.value);
            }
        }
        else
        {
            for (var col : this.columns())
            {
                if (KustoFacts.matches(pattern, col.name()))
                    columns.add(col);
            }
        }
    }

    @Override
    public void getMembers(String name, int match, List<Symbol> symbols, boolean ignoreCase) // PORT: §3.17 SymbolMatch → int
    {
        if (this.columns().size() > 0)
        {
            if (name != null)
            {
                // ColumnMap lookup is ordinal (case-sensitive) even when ignoreCase is true, as upstream.
                var column = this.columnMap().get(name); // PORT: §3.3 TryGetValue
                if (column != null && SymbolMatchExtensions.matches(column, name, match, ignoreCase)) // PORT: §3.5
                {
                    symbols.add(column);
                }
            }
            else
            {
                super.getMembers(name, match, symbols, false); // PORT: §3.12 base.GetMembers(name, match, symbols) drops ignoreCase (default false), as upstream
            }
        }
    }

    /// <summary>
    /// Returns a new <see cref="TableSymbol"/> instance with all columns
    /// modified to reference the specified source.
    /// </summary>
    public TableSymbol withSource(SyntaxNode source)
    {
        return this.withColumns(Linq.select(this.columns(), p -> p.withSource(source))); // PORT: §3.6
    }

    /// <summary>
    /// An empty table.
    /// </summary>
    public static final TableSymbol Empty = new TableSymbol();

    /// <summary>
    /// Combine the columns of multiple tables into a new table.
    /// </summary>
    public static TableSymbol combine(CombineKind kind, Iterable<TableSymbol> tables)
    {
        return new TableSymbol(ColumnSymbol.combine(kind, Linq.select(tables, t -> t.columns()))); // PORT: §3.6
    }

    /// <summary>
    /// Combine the columns of multiple tables into a new table.
    /// </summary>
    public static TableSymbol combine(CombineKind kind, TableSymbol... tables)
    {
        return combine(kind, (Iterable<TableSymbol>) Arrays.asList(tables)); // PORT: §3.10
    }

    /// <summary>
    /// Returns true if the two tables have the same name, columns and properties.
    /// </summary>
    public static boolean areEquivalent(TableSymbol x, TableSymbol y)
    {
        if (x == y)
            return true;
        if (x.getClass() != y.getClass()) // PORT: §3.10 GetType() → getClass()
            return false;
        if (!Objects.equals(x.name(), y.name()) // PORT: §3.14 string ==
            || !Objects.equals(x.alternateName(), y.alternateName())
            || !Objects.equals(x.description(), y.description()))
            return false;
        return areResultEquivalent(x, y);
    }

    /// <summary>
    /// Returns true if the two tables have the same columns and properties,
    /// such that they would be considered the logically equivalent result type.
    /// </summary>
    public static boolean areResultEquivalent(TableSymbol x, TableSymbol y)
    {
        if (x == y)
            return true;
        if (x._state != y._state)
            return false;
        return areColumnsEquivalent(x, y);
    }

    /// <summary>
    /// Returns true if the two tables have the same column names and types.
    /// </summary>
    public static boolean areColumnsEquivalent(TableSymbol x, TableSymbol y)
    {
        if (x == y)
            return true;
        if (x.columns().size() != y.columns().size())
            return false;
        for (int i = 0; i < x.columns().size(); i++)
        {
            var xc = x.columns().get(i);
            var yc = y.columns().get(i);
            if (!Objects.equals(xc.name(), yc.name()) // PORT: §3.14 string ==
                || xc.type() != yc.type())
                return false;
        }
        return true;
    }
}
