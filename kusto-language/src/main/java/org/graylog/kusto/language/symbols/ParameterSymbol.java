// Ported from: src/Kusto.Language/Symbols/ParameterSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import org.graylog.kusto.language.utils.ArgumentCheckers;

/// <summary>
/// A symbol for a declared function's parameter
/// </summary>
public final class ParameterSymbol extends Symbol
{
    private final TypeSymbol type;
    public TypeSymbol type() { return this.type; }

    private final String description;
    public String description() { return this.description; }

    @Override
    public SymbolKind kind() { return SymbolKind.Parameter; }

    public ParameterSymbol(String name, TypeSymbol type, String description)
    {
        super(name);
        this.type = ArgumentCheckers.checkArgumentNull(type, "type" /* nameof */);
        this.description = description != null ? description : ""; // PORT: §3.14 ??
    }

    public ParameterSymbol(String name, TypeSymbol type) // PORT: §3.12 optional parameter description = null
    {
        this(name, type, (String) null);
    }

    @Override
    public Tabularity tabularity() { return this.type().tabularity(); }
}
