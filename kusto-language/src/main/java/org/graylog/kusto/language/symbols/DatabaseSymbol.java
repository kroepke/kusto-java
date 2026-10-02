// Ported from: src/Kusto.Language/Symbols/DatabaseSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import org.graylog.kusto.language.utils.ArgumentCheckers;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.dotnet.Linq;

/// <summary>
/// A symbol representing a database.
/// </summary>
public final class DatabaseSymbol extends TypeSymbol
{
    private final String _alternateName;
    private final List<Symbol> _members;

    /// <summary>
    /// If true, then the definition of the database is not fully known.
    /// </summary>
    private final boolean isOpen;
    public boolean isOpen() { return this.isOpen; }

    // caches
    // PORT: §3.13 racy-but-benign lazy caches are volatile so a published list is always fully visible
    private volatile List<TableSymbol> _tables;
    private volatile List<ExternalTableSymbol> _externalTables;
    private volatile List<MaterializedViewSymbol> _materializedViews;
    private volatile List<FunctionSymbol> _functions;
    private volatile List<EntityGroupSymbol> _entityGroups;
    private volatile List<StoredQueryResultSymbol> _storedQueryResults;
    private volatile List<GraphModelSymbol> _graphModels;
    private volatile LinkedHashSet<Symbol> _symbolSet; // PORT: §3.17 HashSet -> LinkedHashSet

    /// <summary>
    /// Creates a new instance of a <see cref="DatabaseSymbol"/>.
    /// </summary>
    @SuppressWarnings("unchecked")
    public DatabaseSymbol(String name, String alternateName, Iterable<? extends Symbol> members, boolean isOpen)
    {
        super(name);
        _alternateName = alternateName != null ? alternateName : ""; // PORT: §3.14 ??
        // PORT: §3.10 covariance IEnumerable<Symbol>
        _members = ArgumentCheckers.checkArgumentNullOrElementNull(ListExtensions.toReadOnly((Iterable<Symbol>) members), "members" /* nameof */);
        this.isOpen = isOpen;
    }

    public DatabaseSymbol(String name, String alternateName, Iterable<? extends Symbol> members) // PORT: §3.12 optional parameter isOpen = false
    {
        this(name, alternateName, members, false);
    }

    /// <summary>
    /// Creates a new instance of a <see cref="DatabaseSymbol"/>.
    /// </summary>
    public DatabaseSymbol(String name, Iterable<? extends Symbol> members, boolean isOpen)
    {
        this(name, null, members, isOpen);
    }

    public DatabaseSymbol(String name, Iterable<? extends Symbol> members) // PORT: §3.12 optional parameter isOpen = false
    {
        this(name, null, members, false);
    }

    /// <summary>
    /// Creates a new instance of a <see cref="DatabaseSymbol"/>.
    /// </summary>
    public DatabaseSymbol(String name, String alternateName, Symbol... members)
    {
        this(name, alternateName, members != null ? Arrays.asList(members) : null, false); // PORT: §3.10 (IEnumerable<Symbol>)members
    }

    /// <summary>
    /// Creates a new instance of a <see cref="DatabaseSymbol"/>.
    /// </summary>
    public DatabaseSymbol(String name, Symbol... members)
    {
        this(name, members != null ? Arrays.asList(members) : null, false); // PORT: §3.10 (IEnumerable<Symbol>)members
    }

    @Override
    public String alternateName() { return _alternateName; }

    @Override
    public SymbolKind kind() { return SymbolKind.Database; }

    @Override
    public Tabularity tabularity() { return Tabularity.Other; }

    /// <summary>
    /// All the symbols contained by this symbol.
    /// </summary>
    @Override
    public List<Symbol> members() { return _members; }

    /// <summary>
    /// The tables contained by the database.
    /// </summary>
    public List<TableSymbol> tables()
    {
        if (_tables == null)
        {
            // PORT: §3.6 OfType<TableSymbol>().Where(...).ToReadOnly()
            _tables = ListExtensions.toReadOnly(Linq.where(Linq.ofType(this.members(), TableSymbol.class),
                ts -> !ts.isExternal() && !ts.isMaterializedView() && !ts.isStoredQueryResult()));
        }

        return _tables;
    }

    /// <summary>
    /// The external tables accessible from the database.
    /// </summary>
    public List<ExternalTableSymbol> externalTables()
    {
        if (_externalTables == null)
        {
            _externalTables = ListExtensions.toReadOnly(Linq.ofType(this.members(), ExternalTableSymbol.class)); // PORT: §3.6
        }

        return _externalTables;
    }

    /// <summary>
    /// The materialized views accessible from the database.
    /// </summary>
    public List<MaterializedViewSymbol> materializedViews()
    {
        if (_materializedViews == null)
        {
            _materializedViews = ListExtensions.toReadOnly(Linq.ofType(this.members(), MaterializedViewSymbol.class)); // PORT: §3.6
        }

        return _materializedViews;
    }

    /// <summary>
    /// The functions contained by the database.
    /// </summary>
    public List<FunctionSymbol> functions()
    {
        if (_functions == null)
        {
            _functions = ListExtensions.toReadOnly(Linq.ofType(this.members(), FunctionSymbol.class)); // PORT: §3.6
        }

        return _functions;
    }

    /// <summary>
    /// The entity groups contained by the database.
    /// </summary>
    public List<EntityGroupSymbol> entityGroups()
    {
        if (_entityGroups == null)
        {
            _entityGroups = ListExtensions.toReadOnly(Linq.ofType(this.members(), EntityGroupSymbol.class)); // PORT: §3.6
        }

        return _entityGroups;
    }

    /// <summary>
    /// The stored query results contained by the database.
    /// </summary>
    public List<StoredQueryResultSymbol> storedQueryResults()
    {
        if (_storedQueryResults == null)
        {
            _storedQueryResults = ListExtensions.toReadOnly(Linq.ofType(this.members(), StoredQueryResultSymbol.class)); // PORT: §3.6
        }
        return _storedQueryResults;
    }

    /// <summary>
    /// The graph models contained by the database.
    /// </summary>
    public List<GraphModelSymbol> graphModels()
    {
        if (_graphModels == null)
        {
            _graphModels = ListExtensions.toReadOnly(Linq.ofType(this.members(), GraphModelSymbol.class)); // PORT: §3.6
        }
        return _graphModels;
    }

    /// <summary>
    /// Gets the member with the specified name or returns null.
    /// </summary>
    public Symbol getMember(String name)
    {
        return Linq.firstOrDefault(_members, m -> Objects.equals(m.name(), name)); // PORT: §3.14 string ==
    }

    /// <summary>
    /// Gets the table with the specified name or returns null.
    /// </summary>
    public TableSymbol getTable(String name)
    {
        return Linq.firstOrDefault(this.tables(), t -> Objects.equals(t.name(), name)); // PORT: §3.14 string ==
    }

    /// <summary>
    /// Gets the table, external table or materialized view with the specified name.
    /// </summary>
    public TableSymbol getAnyTable(String name)
    {
        // PORT: §3.14 ?? chain
        TableSymbol result = getTable(name);
        if (result == null)
            result = getExternalTable(name);
        if (result == null)
            result = getMaterializedView(name);
        return result;
    }

    /// <summary>
    /// Gets the external table with the specified name or returns null.
    /// </summary>
    public TableSymbol getExternalTable(String name)
    {
        return Linq.firstOrDefault(this.externalTables(), t -> Objects.equals(t.name(), name)); // PORT: §3.14 string ==
    }

    /// <summary>
    /// Gets the materialized view with the specified name or returns null.
    /// </summary>
    public MaterializedViewSymbol getMaterializedView(String name)
    {
        return Linq.firstOrDefault(this.materializedViews(), t -> Objects.equals(t.name(), name)); // PORT: §3.14 string ==
    }

    /// <summary>
    /// Gets the function with the specified name or returns null.
    /// </summary>
    public FunctionSymbol getFunction(String name)
    {
        return Linq.firstOrDefault(this.functions(), f -> Objects.equals(f.name(), name)); // PORT: §3.14 string ==
    }

    /// <summary>
    /// Gets the entitiy group with the specified name or retuns null.
    /// </summary>
    public EntityGroupSymbol getEntityGroup(String name)
    {
        return Linq.firstOrDefault(this.entityGroups(), eg -> Objects.equals(eg.name(), name)); // PORT: §3.14 string ==
    }

    /// <summary>
    /// Gets the stored query result with the specified name or returns null.
    /// </summary>
    public StoredQueryResultSymbol getStoredQueryResult(String name)
    {
        return Linq.firstOrDefault(this.storedQueryResults(), sqr -> Objects.equals(sqr.name(), name)); // PORT: §3.14 string ==
    }

    /// <summary>
    /// Gets the graph model with the specified name or returns null.
    /// </summary>
    public GraphModelSymbol getGraphModel(String name)
    {
        return Linq.firstOrDefault(this.graphModels(), gm -> Objects.equals(gm.name(), name)); // PORT: §3.14 string ==
    }

    /// <summary>
    /// Returns a new <see cref="DatabaseSymbol"/> with the specified <see cref="AlternateName"/>.
    /// </summary>
    public DatabaseSymbol withAlternateName(String alternateName)
    {
        if (Objects.equals(this.alternateName(), alternateName)) // PORT: §3.14 string ==
            return this;
        return new DatabaseSymbol(this.name(), alternateName, this.members(), this.isOpen());
    }

    /// <summary>
    /// Returns a new <see cref="DatabaseSymbol"/> with the specified members.
    /// </summary>
    public DatabaseSymbol withMembers(Iterable<? extends Symbol> members)
    {
        return new DatabaseSymbol(this.name(), this.alternateName(), members, this.isOpen());
    }

    /// <summary>
    /// Returns a new <see cref="DatabaseSymbol"/> with the specified members added.
    /// </summary>
    public DatabaseSymbol addMembers(Iterable<? extends Symbol> symbols)
    {
        return new DatabaseSymbol(this.name(), this.alternateName(), Linq.concat(this.members(), symbols), this.isOpen()); // PORT: §3.6
    }

    /// <summary>
    /// Returns a new <see cref="DatabaseSymbol"/> with the specified members added.
    /// </summary>
    public DatabaseSymbol addMembers(Symbol... symbols)
    {
        return addMembers(Arrays.asList(symbols)); // PORT: §3.10 (IEnumerable<Symbol>)symbols
    }

    /// <summary>
    /// Returns true if the symbol is contained by the database.
    /// </summary>
    public boolean contains(Symbol symbol)
    {
        if (this._symbolSet == null)
        {
            this._symbolSet = new LinkedHashSet<Symbol>(); // PORT: §3.17

            for (Symbol member : this.members())
            {
                // PORT-BUG: upstream adds the argument `symbol`, not `member` (DatabaseSymbol.cs:307); mirrored verbatim
                this._symbolSet.add(symbol);
            }
        }

        return this._symbolSet.contains(symbol);
    }

    public static final DatabaseSymbol Unknown =
        new DatabaseSymbol("", null, true); // PORT: §3.12 named arguments members: null, isOpen: true
}
