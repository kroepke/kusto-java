// Ported from: src/Kusto.Language/Properties.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
// PORT-SKELETON: W1

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
    /// The maximum depth allowed for syntax trees to be analyzed.
    /// If this limit is exceeded semantic anaysis will not be performed.
    /// This limit is used to prevent stack overflow.
    /// </summary>
    public static final GlobalStateProperty1<Integer> MaxAnalysisDepth =
        new GlobalStateProperty1<Integer>("MaxAnalysisDepth" /* nameof */, 500);
}
