// Ported from: src/Kusto.Language/Binder/FunctionCallResult.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.binding;

import org.graylog.kusto.language.symbols.TypeSymbol;
import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// Represents the result information for a function or operator invocation.
/// </summary>
// PORT: §3.2 struct -> record; components are the lowercased properties Type and Info
@Internal
public record FunctionCallResult(
    /// <summary>
    /// The result type of this signature.
    /// </summary>
    TypeSymbol type,

    /// <summary>
    /// The extended semantic info for the function call.
    /// </summary>
    FunctionCallInfo info)
{
    public FunctionCallResult(TypeSymbol type) // PORT: §3.12 info = null
    {
        this(type, null);
    }

    // PORT: §3.8 implicit operator FunctionCallResult(TypeSymbol) -> explicit factory
    public static FunctionCallResult of(TypeSymbol type)
    {
        return new FunctionCallResult(type, null);
    }
}
