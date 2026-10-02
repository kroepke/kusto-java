// Ported from: src/Kusto.Language/Symbols/ClusterSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import org.graylog.kusto.language.utils.ArgumentCheckers;
import org.graylog.kusto.language.utils.Interlocked;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.SafeList;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Linq;

/// <summary>
/// A symbol representing a cluster.
/// </summary>
public final class ClusterSymbol extends TypeSymbol
{
    // PORT: §3.13 stands in for `ref _nameToDatabaseMap`
    private static final VarHandle NAME_TO_DATABASE_MAP = Interlocked.handle(ClusterSymbol.class, "_nameToDatabaseMap", LinkedHashMap.class);

    private final SafeList<Symbol> _members;

    /// <summary>
    /// If true, then the definition of the cluster is not fully known.
    /// </summary>
    private final boolean isOpen;
    public boolean isOpen() { return this.isOpen; }

    /// <summary>
    /// Creates a new instance of a <see cref="ClusterSymbol"/>.
    /// </summary>
    // PORT: §2.5 the private (string, IEnumerable<Symbol>, bool) overload takes List<Symbol> so it does not clash after erasure with the public (string, IEnumerable<DatabaseSymbol>, bool)
    private ClusterSymbol(String name, List<Symbol> members, boolean isOpen)
    {
        super(name);
        _members = ListExtensions.toSafeList(members);
        ArgumentCheckers.checkArgumentNullOrElementNull(_members.asList(), "members" /* nameof */); // PORT: §3.10 SafeList is not a List
        this.isOpen = isOpen;
    }

    /// <summary>
    /// Creates a new instance of a <see cref="ClusterSymbol"/>.
    /// </summary>
    public ClusterSymbol(String name, Iterable<? extends DatabaseSymbol> databases, boolean isOpen)
    {
        // PORT: §3.10 `(IReadOnlyList<Symbol>)databases` covariance cast; a non-list enumerable is copied instead of throwing InvalidCastException
        this(name, databases != null ? Linq.<Symbol>cast(databases, Symbol.class) : null, isOpen);
    }

    public ClusterSymbol(String name, Iterable<? extends DatabaseSymbol> databases) // PORT: §3.12 optional parameter isOpen = false
    {
        this(name, databases, false);
    }

    /// <summary>
    /// Creates a new instance of a <see cref="ClusterSymbol"/>.
    /// </summary>
    public ClusterSymbol(String name, DatabaseSymbol... databases)
    {
        this(name, (Iterable<? extends DatabaseSymbol>) (databases != null ? Arrays.asList(databases) : null), false); // PORT: §3.10 (IEnumerable<DatabaseSymbol>)databases
    }

    @Override
    public List<Symbol> members() { return _members.asList(); } // PORT: §3.10 SafeList is not a List

    @Override
    public SymbolKind kind() { return SymbolKind.Cluster; }

    @Override
    public Tabularity tabularity() { return Tabularity.Tabular; }

    /// <summary>
    /// Creates a new <see cref="ClusterSymbol"/> with the specified members.
    /// </summary>
    public ClusterSymbol addMembers(Iterable<? extends Symbol> members)
    {
        List<Symbol> newMembers = Linq.<Symbol>concat(_members, members); // PORT: §3.6
        return new ClusterSymbol(this.name(), newMembers, this.isOpen());
    }

    /// <summary>
    /// Creates a new <see cref="ClusterSymbol"/> with the specified members.
    /// </summary>
    public ClusterSymbol addMembers(Symbol... members)
    {
        return addMembers(Arrays.asList(members)); // PORT: §3.10 (IReadOnlyList<Symbol>)members
    }

    /// <summary>
    /// The databases associated with this cluster.
    /// </summary>
    public List<DatabaseSymbol> databases()
    {
        if (_databases == null)
        {
            _databases = ListExtensions.toReadOnly(Linq.ofType(_members, DatabaseSymbol.class)); // PORT: §3.6
        }

        return _databases;
    }

    private volatile List<DatabaseSymbol> _databases; // PORT: §3.13 benign racy cache, volatile
    private volatile LinkedHashMap<String, DatabaseSymbol> _nameToDatabaseMap; // PORT: §3.13 CAS-published field is volatile; §3.17

    /// <summary>
    /// Gets the database with the specified name or returns null.
    /// </summary>
    public DatabaseSymbol getDatabase(String databaseName)
    {
        if (DotNetStrings.isNullOrEmpty(databaseName))
            return null;

        if (_nameToDatabaseMap == null)
        {
            var tmp = new LinkedHashMap<String, DatabaseSymbol>(); // PORT: §3.17

            for (DatabaseSymbol db : this.databases())
            {
                tmp.put(db.name(), db);
                if (!DotNetStrings.isNullOrEmpty(db.alternateName()))
                    tmp.put(db.alternateName(), db);
            }

            Interlocked.compareExchange(NAME_TO_DATABASE_MAP, this, tmp, null); // PORT: §3.13
        }

        return _nameToDatabaseMap.get(databaseName); // PORT: §3.3 TryGetValue; values are never null
    }

    /// <summary>
    /// Creates a new <see cref="ClusterSymbol"/> with the specified list of databases.
    /// </summary>
    public ClusterSymbol withDatabases(Iterable<? extends DatabaseSymbol> databases)
    {
        return new ClusterSymbol(this.name(), databases, this.isOpen());
    }

    /// <summary>
    /// Creates a new <see cref="ClusterSymbol"/> with the specified database added.
    /// </summary>
    public ClusterSymbol addDatabase(DatabaseSymbol database)
    {
        var newMembers = _members.addItem(database);
        return new ClusterSymbol(this.name(), newMembers.asList(), this.isOpen()); // PORT: §3.10 SafeList is not a List
    }

    /// <summary>
    /// Creates a new <see cref="ClusterSymbol"/> with existing database replaced with the new database.
    /// </summary>
    public ClusterSymbol updateDatabase(DatabaseSymbol existingDatabase, DatabaseSymbol newDatabase)
    {
        // PORT: §3.6 _members.Select(d => d == existingDatabase ? newDatabase : d)
        List<Symbol> newMembers = new ArrayList<Symbol>();
        for (Symbol d : _members)
        {
            newMembers.add(d == existingDatabase ? newDatabase : d);
        }
        return new ClusterSymbol(this.name(), newMembers, this.isOpen());
    }

    /// <summary>
    /// Creates a new <see cref="ClusterSymbol"/> with database added or replacing an existing database with the same name.
    /// </summary>
    public ClusterSymbol addOrUpdateDatabase(DatabaseSymbol newDatabase)
    {
        var existingDatabase = this.getDatabase(newDatabase.name());
        if (existingDatabase != null)
        {
            return this.updateDatabase(existingDatabase, newDatabase);
        }
        else
        {
            return this.addDatabase(newDatabase);
        }
    }

    /// <summary>
    /// Creates a new <see cref="ClusterSymbol"/> with the specified database removed.
    /// </summary>
    public ClusterSymbol removeDatabase(DatabaseSymbol symbolToRemove)
    {
        List<Symbol> newMembers = ListExtensions.toReadOnly(Linq.where(_members, m -> m != symbolToRemove)); // PORT: §3.6
        return new ClusterSymbol(this.name(), newMembers, this.isOpen());
    }

    /// <summary>
    /// Creates a new <see cref="ClusterSymbol"/> with the specified databases removed.
    /// </summary>
    public ClusterSymbol removeDatabases(Iterable<? extends DatabaseSymbol> symbolsToRemove)
    {
        List<Symbol> newMembers = ListExtensions.toReadOnly(Linq.<Symbol>except(_members, symbolsToRemove)); // PORT: §3.6
        return new ClusterSymbol(this.name(), newMembers, this.isOpen());
    }

    /// <summary>
    /// The symbol used to represent unknown clusters.
    /// </summary>
    public static final ClusterSymbol Unknown =
        new ClusterSymbol("", (List<Symbol>) null, true); // PORT: §3.12 named arguments members: null, isOpen: true (selects the private overload)
}
