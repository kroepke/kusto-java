// Ported from: src/Kusto.Language/Symbols/GraphModelSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import org.graylog.kusto.language.utils.Interlocked;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.dotnet.Internal;
import org.graylog.kusto.language.utils.dotnet.Out;

/// <summary>
/// A symbol representing a graph model.
/// </summary>
public final class GraphModelSymbol extends TypeSymbol
{
    // PORT: §3.13 stands in for `ref _snapshotMap`
    private static final VarHandle SNAPSHOT_MAP = Interlocked.handle(GraphModelSymbol.class, "_snapshotMap", LinkedHashMap.class);

    /// <summary>
    /// All queries defining edge tables.
    /// </summary>
    private final List<Signature> edges;
    public List<Signature> edges() { return this.edges; }

    /// <summary>
    /// All queries defining node tables.
    /// </summary>
    private final List<Signature> nodes;
    public List<Signature> nodes() { return this.nodes; }

    /// <summary>
    /// All named snapshots.
    /// </summary>
    private final List<GraphSnapshotSymbol> snapshots;
    public List<GraphSnapshotSymbol> snapshots() { return this.snapshots; }

    public GraphModelSymbol(
        String name,
        Iterable<Signature> edges,
        Iterable<Signature> nodes,
        Iterable<GraphSnapshotSymbol> snapshots)
    {
        super(name);
        this.snapshots = ListExtensions.toReadOnly(snapshots);
        this.edges = ListExtensions.toReadOnly(edges);
        this.nodes = ListExtensions.toReadOnly(nodes);

        for (Signature edge : this.edges())
        {
            edge.setSymbol(this);
        }

        for (Signature node : this.nodes())
        {
            node.setSymbol(this);
        }

        for (GraphSnapshotSymbol snapshot : this.snapshots())
        {
            snapshot.setModel(this);
        }
    }

    // PORT: §2.5 the (string, IEnumerable<string>, IEnumerable<string>, IEnumerable<string>) overload takes List<string> for edges and nodes so it does not clash after erasure with the IEnumerable<Signature> overload
    // PORT: §3.11 the computed this(...) arguments go through private static helpers
    public GraphModelSymbol(
        String name,
        List<String> edges,
        List<String> nodes,
        Iterable<String> snapshots)
    {
        this(
              name,
              toSignatures(edges),
              toSignatures(nodes),
              toSnapshotSymbols(snapshots)
              );
    }

    public GraphModelSymbol(String name) // PORT: §3.12 optional parameters edges, nodes, snapshots = null
    {
        this(name, (List<String>) null, (List<String>) null, (Iterable<String>) null);
    }

    public GraphModelSymbol(String name, List<String> edges) // PORT: §3.12 optional parameters nodes, snapshots = null
    {
        this(name, edges, (List<String>) null, (Iterable<String>) null);
    }

    public GraphModelSymbol(String name, List<String> edges, List<String> nodes) // PORT: §3.12 optional parameter snapshots = null
    {
        this(name, edges, nodes, (Iterable<String>) null);
    }

    public GraphModelSymbol(
        String name,
        String edge,
        String node,
        Iterable<String> snapshots)
    {
        this(
              name,
              edge != null ? Collections.singletonList(edge) : null,
              node != null ? Collections.singletonList(node) : null,
              snapshots
              );
    }

    public GraphModelSymbol(String name, String edge) // PORT: §3.12 optional parameters node, snapshots = null
    {
        this(name, edge, (String) null, (Iterable<String>) null);
    }

    public GraphModelSymbol(String name, String edge, String node) // PORT: §3.12 optional parameter snapshots = null
    {
        this(name, edge, node, (Iterable<String>) null);
    }

    // edges != null ? edges.Select(e => new Signature(e, Tabularity.Unknown)) : null
    private static List<Signature> toSignatures(List<String> queries)
    {
        if (queries == null)
            return null;

        List<Signature> result = new ArrayList<Signature>();
        for (String query : queries)
        {
            result.add(new Signature(query, Tabularity.Unknown));
        }
        return result;
    }

    // snapshots != null ? snapshots.Select(sn => new GraphSnapshotSymbol(sn)) : null
    private static List<GraphSnapshotSymbol> toSnapshotSymbols(Iterable<String> names)
    {
        if (names == null)
            return null;

        List<GraphSnapshotSymbol> result = new ArrayList<GraphSnapshotSymbol>();
        for (String name : names)
        {
            result.add(new GraphSnapshotSymbol(name));
        }
        return result;
    }

    @Override
    public Tabularity tabularity() { return Tabularity.None; }

    @Override
    public SymbolKind kind() { return SymbolKind.GraphModel; }

    @Override
    public List<Symbol> members() { return Collections.<Symbol>unmodifiableList(this.snapshots()); } // PORT: §3.10 IReadOnlyList<Symbol> => Snapshots

    private volatile LinkedHashMap<String, GraphSnapshotSymbol> _snapshotMap; // PORT: §3.13 CAS-published field is volatile; §3.17

    /// <summary>
    /// Returns the snapshot of the specified name or null if it does not exist.
    /// </summary>
    public boolean tryGetSnapshot(String name, Out<GraphSnapshotSymbol> snapshot) // PORT: §3.3 out parameter
    {
        if (_snapshotMap == null)
        {
            var tmp = ListExtensions.toDictionaryLast(this.snapshots(), sn -> sn.name());
            Interlocked.compareExchange(SNAPSHOT_MAP, this, tmp, null); // PORT: §3.13
        }

        Objects.requireNonNull(name, "key"); // PORT: §3.16 Dictionary.TryGetValue throws ArgumentNullException on a null key
        snapshot.value = _snapshotMap.get(name); // PORT: §3.3 TryGetValue; values are never null
        return snapshot.value != null;
    }

    private GraphSymbol computedGraphSymbol;

    @Internal
    public GraphSymbol computedGraphSymbol() { return this.computedGraphSymbol; }

    @Internal
    public void setComputedGraphSymbol(GraphSymbol value) { this.computedGraphSymbol = value; }
}
