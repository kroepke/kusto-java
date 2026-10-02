// Ported from: src/Kusto.Language/Symbols/CustomAvailabilityContext.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

import org.graylog.kusto.language.syntax.SyntaxNode;

/// <summary>
/// A binding context for a function/operator call.
/// </summary>
public abstract class CustomAvailabilityContext
{
    /// <summary>
    /// The location related to the function/operator call.
    /// </summary>
    public SyntaxNode location() { return null; }
}
