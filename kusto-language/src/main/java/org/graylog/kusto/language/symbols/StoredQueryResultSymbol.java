// Ported from: src/Kusto.Language/Symbols/TableSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

import org.graylog.kusto.language.utils.EmptyReadOnlyList;

// Upstream declares no Empty here: StoredQueryResultSymbol.Empty (Signature.cs:507,621) is the inherited static
// TableSymbol.Empty in C#, and Java static member inheritance binds it the same way.
public final class StoredQueryResultSymbol extends TableSymbol
{
    public StoredQueryResultSymbol(String name)
    {
        super(name, EmptyReadOnlyList.<ColumnSymbol>instance()); // PORT: §3.9
    }

    public StoredQueryResultSymbol(String name, Iterable<ColumnSymbol> columns)
    {
        super(name, columns);
    }

    @Override
    public SymbolKind kind() { return SymbolKind.StoredQueryResult; }
}
