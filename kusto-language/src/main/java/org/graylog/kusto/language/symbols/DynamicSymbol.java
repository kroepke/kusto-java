// Ported from: src/Kusto.Language/Symbols/DynamicSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

/// <summary>
/// A symbol representing a type that is any scalar type, 
/// and is stored dynamic/json column.
/// </summary>
public abstract class DynamicSymbol extends ScalarSymbol
{
    protected DynamicSymbol(String name)
    {
        super(name);
    }
}
