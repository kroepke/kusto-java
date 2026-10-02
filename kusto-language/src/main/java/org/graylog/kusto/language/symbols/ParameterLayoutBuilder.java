// Ported from: src/Kusto.Language/Symbols/ParameterLayouts.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

import java.util.List;

import org.graylog.kusto.language.syntax.Expression;

/// <summary>
/// A function that builds a list of parameters associated with each argument.
/// </summary>
@FunctionalInterface
public interface ParameterLayoutBuilder // PORT: §3.8 custom delegate → @FunctionalInterface; Invoke → invoke
{
    void invoke(Signature signature, List<Expression> arguments, List<Parameter> argumentParameters);
}
