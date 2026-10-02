// Ported from: src/Kusto.Language/Symbols/DynamicSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// A symbol that represents any scalar primitive stored in a dynamic column.
/// </summary>
public final class DynamicPrimitiveSymbol extends DynamicSymbol
{
    private final ScalarSymbol underlyingType;
    public ScalarSymbol underlyingType() { return underlyingType; }

    @Override
    public SymbolKind kind() { return SymbolKind.Primitive; }

    @Internal
    public DynamicPrimitiveSymbol(ScalarSymbol underlyingType)
    {
        super("dynamic");
        this.underlyingType = underlyingType;
    }
}
