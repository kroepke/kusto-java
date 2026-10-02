// Ported from: src/Kusto.Language/Symbols/PatternSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

import java.util.List;

import org.graylog.kusto.language.utils.ArgumentCheckers;
import org.graylog.kusto.language.utils.EmptyReadOnlyList;

/// <summary>
/// The symbol for a pattern.
/// </summary>
public final class PatternSymbol extends TypeSymbol
{
    @Override
    public SymbolKind kind() { return SymbolKind.Pattern; }

    private final List<Parameter> parameters;
    public List<Parameter> parameters() { return this.parameters; }

    private final Parameter pathParameter;
    public Parameter pathParameter() { return this.pathParameter; }

    private final List<PatternSignature> signatures;
    public List<PatternSignature> signatures() { return this.signatures; }

    public PatternSymbol(
        String name,
        List<Parameter> parameters,
        Parameter pathParameter,
        List<PatternSignature> signatures)
    {
        super(name);
        this.parameters = ArgumentCheckers.checkArgumentNullOrElementNull(parameters != null ? parameters : EmptyReadOnlyList.<Parameter>instance(), "parameters"); // PORT: §3.14 ??; §3.9; §3.5
        this.pathParameter = pathParameter;
        this.signatures = ArgumentCheckers.checkArgumentNullOrElementNull(signatures != null ? signatures : EmptyReadOnlyList.<PatternSignature>instance(), "signatures"); // PORT: §3.14 ??; §3.9; §3.5

        for (var sig : this.signatures())
        {
            sig.setSymbol(this);
        }
    }

    public PatternSymbol(String name, List<Parameter> parameters, Parameter pathParameter) // PORT: §3.12 signatures = null
    {
        this(name, parameters, pathParameter, null);
    }

    public PatternSymbol(String name, List<Parameter> parameters) // PORT: §3.12 pathParameter = null, signatures = null
    {
        this(name, parameters, null, null);
    }

    public PatternSymbol(String name)
    {
        this(name, null, null, null);
    }

    @Override
    public Tabularity tabularity() { return Tabularity.Tabular; }
}
