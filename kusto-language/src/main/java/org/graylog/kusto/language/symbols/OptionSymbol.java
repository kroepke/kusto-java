// Ported from: src/Kusto.Language/Symbols/OptionSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import java.util.List;

import org.graylog.kusto.language.utils.ArgumentCheckers;
import org.graylog.kusto.language.utils.EmptyReadOnlyList;

/// <summary>
/// A symbol for a query options (assigned via set statement)
/// </summary>
public final class OptionSymbol extends Symbol
{
    private final String description;
    public String description() { return this.description; }

    private final List<ScalarSymbol> types;
    public List<ScalarSymbol> types() { return this.types; }

    private final List<String> examples;
    public List<String> examples() { return this.examples; }

    @Override
    public SymbolKind kind() { return SymbolKind.Option; }

    public OptionSymbol(String name,
        String description,
        List<ScalarSymbol> types,
        List<String> examples)
    {
        super(name);
        this.description = description != null ? description : ""; // PORT: §3.14 ??
        this.types = types != null ? types : EmptyReadOnlyList.<ScalarSymbol>instance(); // PORT: §3.14 ??
        this.examples = examples != null ? examples : EmptyReadOnlyList.<String>instance(); // PORT: §3.14 ??
    }

    // PORT: §3.12 optional parameters description, types, examples = null
    public OptionSymbol(String name)
    {
        this(name, (String) null, (List<ScalarSymbol>) null, (List<String>) null);
    }

    public OptionSymbol(String name, String description)
    {
        this(name, description, (List<ScalarSymbol>) null, (List<String>) null);
    }

    public OptionSymbol(String name, String description, List<ScalarSymbol> types)
    {
        this(name, description, types, (List<String>) null);
    }

    public OptionSymbol(String name,
        String description,
        ScalarSymbol type,
        List<String> examples)
    {
        this(name, description, List.of(ArgumentCheckers.checkArgumentNull(type, "type" /* nameof */)), examples); // PORT: §3.11 new[] { type }
    }

    public OptionSymbol(String name, String description, ScalarSymbol type) // PORT: §3.12 optional parameter examples = null
    {
        this(name, description, type, (List<String>) null);
    }

    @Override
    public Tabularity tabularity() { return Tabularity.Scalar; }
}
