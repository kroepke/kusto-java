// Ported from: src/Kusto.Language/Symbols/CustomReturnType.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

/// <summary>
/// A function that determines the a function's return type given binding info.
/// </summary>
@FunctionalInterface
public interface CustomReturnType // PORT: §3.8 custom delegate → @FunctionalInterface; Invoke → invoke
{
    TypeSymbol invoke(CustomReturnTypeContext info);
}
