// Ported from: src/Kusto.Language/Properties.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language;

/// <summary>
/// The set of predefined <see cref="GlobalStateProperty"/>.
/// </summary>
public final class Properties
{
    private Properties() // PORT: §3.5 static class
    {
    }

    /// <summary>
    /// This property is true client parameters are allowed.
    /// </summary>
    // PORT: §3.10 GlobalStateProperty<bool> -> GlobalStateProperty1<Boolean>; the omitted default is default(bool) == false
    public static final GlobalStateProperty1<Boolean> AllowClientParameters =
        new GlobalStateProperty1<Boolean>("AllowClientParameters" /* nameof */, Boolean.FALSE);

    /// <summary>
    ///  The maximum text size allowed by the parser.
    ///  If this text size is exceeded the text will not be parsed.
    ///  Intellisense only.
    ///  This limit is used to improve typing speed for large queries.
    /// </summary>
    public static final GlobalStateProperty1<Integer> MaxParseTextSize =
        new GlobalStateProperty1<Integer>("MaxParseTextSize" /* nameof */, 4 * 1024 * 1024);

    /// <summary>
    /// The maximum depth allowed for syntax trees to be analyzed.
    /// If this limit is exceeded semantic anaysis will not be performed.
    /// This limit is used to prevent stack overflow.
    /// </summary>
    public static final GlobalStateProperty1<Integer> MaxAnalysisDepth =
        new GlobalStateProperty1<Integer>("MaxAnalysisDepth" /* nameof */, 500);

    /// <summary>
    /// The maxmimum number of cached expansions per database function.
    /// </summary>
    public static final GlobalStateProperty1<Integer> MaxCachedExpansions =
        new GlobalStateProperty1<Integer>("MaxCachedExpansions" /* nameof */, 10);

    /// <summary>
    /// The maximum number of cache result types per database function.
    /// </summary>
    public static final GlobalStateProperty1<Integer> MaxCachedResultTypes =
        new GlobalStateProperty1<Integer>("MaxCachedResultTypes" /* nameof */, 50);
}
