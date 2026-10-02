// Ported from: src/Kusto.Language/Symbols/ErrorSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

/// <summary>
/// A symbol representing an unknown type due to a semantic error.
/// </summary>
public final class ErrorSymbol extends TypeSymbol
{
    @Override
    public SymbolKind kind() { return SymbolKind.Error; }

    private ErrorSymbol()
    {
        super("error");
    }

    @Override
    public boolean isError() { return true; }

    @Override
    public Tabularity tabularity() { return Tabularity.None; }

    public static final ErrorSymbol Instance = new ErrorSymbol();
}
