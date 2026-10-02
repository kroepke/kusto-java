// Ported from: src/Kusto.Language/Diagnostics/Diagnostic.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language;

public final class DiagnosticSeverity
{
    private DiagnosticSeverity() // PORT: §3.5 static class
    {
    }

    /// <summary>
    /// A diagnostic that represents code that will fail to execute.
    /// </summary>
    public static final String Error = "Error" /* nameof */;

    /// <summary>
    /// A diagnostic that represents code that will execute but with possible unintended consequence.
    /// </summary>
    public static final String Warning = "Warning" /* nameof */;

    /// <summary>
    /// A diagnostic that represents a suggestion to improve the code.
    /// </summary>
    public static final String Suggestion = "Suggestion" /* nameof */;

    /// <summary>
    /// A diagnostic that represents information about the code.
    /// </summary>
    public static final String Information = "Information" /* nameof */;

    /// <summary>
    /// A diagnostic that is not meant to be relayed to the user.
    /// </summary>
    public static final String Hidden = "Hidden" /* nameof */;
}
