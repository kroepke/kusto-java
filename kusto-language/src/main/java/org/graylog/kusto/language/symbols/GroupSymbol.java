// Ported from: src/Kusto.Language/Symbols/GroupSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import java.util.Arrays;
import java.util.List;

import org.graylog.kusto.language.utils.ArgumentCheckers;
import org.graylog.kusto.language.utils.ListExtensions;

/// <summary>
/// A symbol corresponding to a group of symbols.
/// This symbol occurs when a name reference is ambigous.
/// </summary>
public final class GroupSymbol extends TypeSymbol
{
    @Override
    public SymbolKind kind() { return SymbolKind.Group; }

    private final List<Symbol> members;

    @Override
    public List<Symbol> members() { return this.members; }

    @SuppressWarnings("unchecked")
    public GroupSymbol(Iterable<? extends Symbol> symbols) // PORT: §3.10 covariance
    {
        super("group");
        this.members = ArgumentCheckers.checkArgumentNullOrElementNull(ListExtensions.toReadOnly((Iterable<Symbol>)symbols), "symbols" /* nameof */); // PORT: §3.5, §3.10 covariance (read-only use)
    }

    public GroupSymbol(Symbol... symbols)
    {
        this(symbols != null ? Arrays.asList(symbols) : (Iterable<Symbol>)null); // PORT: §3.17 array as IEnumerable
    }

    @Override
    public Tabularity tabularity()
    {
        return this.members().get(0).tabularity();
    }
}
