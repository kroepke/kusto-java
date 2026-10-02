// Ported from: src/Kusto.Language/Symbols/CustomAvailabilty.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

/// <summary>
/// A function that determines if a <see cref="FunctionSymbol"/> is available in a given context.
/// </summary>
@FunctionalInterface
public interface CustomAvailability // PORT: §3.8 custom delegate → @FunctionalInterface; Invoke → invoke
{
    boolean invoke(CustomAvailabilityContext context);
}
