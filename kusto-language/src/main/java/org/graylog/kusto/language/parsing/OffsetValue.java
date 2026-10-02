// Ported from: src/Kusto.Language/Parser/Combinators/OffsetValue.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

/// <summary>
/// A parsed value and its source offset.
/// </summary>
/// <param name="offset">The text offset of the value in the source.</param>
/// <param name="value">The value located at the offset.</param>
// PORT: §3.2 struct -> record; the public readonly fields Offset/Value become the components offset()/value() (§2.3)
public record OffsetValue<TValue>(
    /// <summary>
    /// The text offset of the value in the source.
    /// </summary>
    int offset,

    /// <summary>
    /// The value located at the offset.
    /// </summary>
    TValue value)
{
}
