// Ported from: src/Kusto.Language/Syntax/ValueInfo.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.syntax;

import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// Represents the associated info of a literal found in syntax.
/// </summary>
public class ValueInfo
{
    /// <summary>
    /// The value as a CLR value.
    /// </summary>
    private final Object value;
    public Object value() { return this.value; }

    /// <summary>
    /// The text of the value.
    /// This is the unescaped value of a string literal or the raw interior text of other literals.
    /// </summary>
    private final String valueText;
    public String valueText() { return this.valueText; }

    /// <summary>
    /// The text of the value as specified in the expression.
    /// </summary>
    private final String rawText;
    public String rawText() { return this.rawText; }

    @Internal
    public ValueInfo(String rawText, String valueText, Object value)
    {
        this.rawText = rawText;
        this.valueText = valueText;
        this.value = value;
    }
}
