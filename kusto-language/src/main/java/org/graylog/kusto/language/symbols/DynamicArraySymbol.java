// Ported from: src/Kusto.Language/Symbols/DynamicSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// A symbol representing an array of values stored in a dynamic column.
/// </summary>
public final class DynamicArraySymbol extends DynamicSymbol
{
    private final TypeSymbol elementType;
    public TypeSymbol elementType() { return elementType; }

    @Override
    public SymbolKind kind() { return SymbolKind.Array; }

    @Internal
    public DynamicArraySymbol(TypeSymbol elementType)
    {
        super("dynamic");
        this.elementType = elementType;
    }
}
