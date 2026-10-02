// Ported from: src/Kusto.Language/Diagnostics/Diagnostic.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language;

public enum DiagnosticLocationKind
{
    /// <summary>
    /// The diagnostic location is known with absolute start and length values.
    /// </summary>
    Absolute,

    /// <summary>
    /// The diagnostic location is unknown, but relative to the syntax item it is associated with.
    /// </summary>
    Relative,

    /// <summary>
    /// The diagnostic location is unknown, but after the end of the syntax item it is associated with.
    /// </summary>
    RelativeEnd
}
