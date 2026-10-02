// Ported from: src/Kusto.Language/Symbols/Conversion.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

/// <summary>
/// The kinds of conversions allowed between values of two different types.
/// </summary>
public enum Conversion
{
    // the enum values are in order of subsumption, a greater value
    // subsumes all other choices (exception None)

    /// <summary>
    /// No conversion allowed between different scalar types (strict)
    /// </summary>
    None,

    /// <summary>
    /// Type promotion (widening) allowed.
    /// </summary>
    Promotable,

    /// <summary>
    /// Conversions to dynamic allowed.
    /// </summary>
    Dynamic,

    /// <summary>
    /// Conversions between compatible types allowed (widening or narrowing)
    /// </summary>
    Compatible,

    /// <summary>
    /// All conversions allowed (no checking)
    /// </summary>
    Any
}
