// Ported from: src/Kusto.Language/Symbols/OperatorSymbol.cs
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
/// A symbol representing a scalar operator.
/// </summary>
public class OperatorSymbol extends Symbol
{
    @Override
    public SymbolKind kind() { return SymbolKind.Operator; }

    private final OperatorKind operatorKind;
    public OperatorKind operatorKind() { return this.operatorKind; }

    // PORT-BUG: `Result` is declared get-only and never assigned upstream (OperatorSymbol.cs:17), so it is always null; mirrored
    private final TypeSymbol result = null;
    public TypeSymbol result() { return this.result; }

    @Override
    public Tabularity tabularity() { return Tabularity.Scalar; }

    private final List<Signature> signatures;
    public List<Signature> signatures() { return this.signatures; }

    public OperatorSymbol(OperatorKind kind, Iterable<Signature> signatures)
    {
        super(kind.toString());
        this.operatorKind = kind;
        this.signatures = ArgumentCheckers.checkArgumentNullOrElementNull(ListExtensions.toReadOnly(signatures), "signatures" /* nameof */);

        for (Signature signature : this.signatures())
        {
            signature.setSymbol(this);
        }
    }

    public OperatorSymbol(OperatorKind kind, Signature... signatures)
    {
        this(kind, signatures != null ? Arrays.asList(signatures) : null); // PORT: §3.10 (IEnumerable<Signature>)signatures
    }

    public OperatorSymbol(OperatorKind kind, TypeSymbol resultType)
    {
        this(kind, new Signature(ArgumentCheckers.checkArgumentNull(resultType, "resultType" /* nameof */)));
    }
}
