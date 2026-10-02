// Ported from: src/Kusto.Language/Symbols/ParameterLayouts.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

/// <summary>
/// The known set of <see cref="ParameterLayout"/>'s
/// </summary>
public final class ParameterLayouts
{
    private ParameterLayouts() // PORT: §3.5 static class
    {
    }

    /// <summary>
    ///  Does not allow parameters to repeat.
    ///  Optional parameters (minOccurring=0) cannot be skipped.
    ///  Named arguments allowed.
    /// </summary>
    public static final ParameterLayout Fixed = new NonRepeatingParameterLayout();

    /// <summary>
    /// Allows for individual repeating parameters, transitioning based on type matching.
    /// Optional parameters (minOccurring=0) cannot be skipped.
    /// Named argument not allowed.
    /// </summary>
    public static final ParameterLayout Repeating = new RepeatingParameterLayout(false);

    /// <summary>
    /// Allows for individual repeating parameters, transitioning based on type matching.
    /// Optional parameters (minOccurring=0) may be skipped.
    /// Named arguments not allowed.
    /// </summary>
    public static final ParameterLayout RepeatingSkipping = new RepeatingParameterLayout(true);

    /// <summary>
    /// Allows for a single group of parameters to repeat together.
    /// Optional parameters (minOccurring=0) cannot be skipped and cannot be part of the repeating block.
    /// Named arguments not allowed.
    /// </summary>
    public static final ParameterLayout BlockRepeating = new BlockRepeatingParameterLayout();

    /// <summary>
    /// A custom layout supplied by a builder function.
    /// </summary>
    public static ParameterLayout custom(ParameterLayoutBuilder builder) { return new CustomParameterLayout(builder); }
}
