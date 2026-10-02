// Ported from: src/Kusto.Language/Editor/CodeKinds.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.editor;

/// <summary>
/// Known code kinds.
/// </summary>
public final class CodeKinds {
    private CodeKinds() {
    }

    /// <summary>
    /// The code is a Kusto Query.
    /// </summary>
    public static final String Query = "Query" /* nameof */; // PORT: §2.3 nameof

    /// <summary>
    /// The code is a Kusto Commmand.
    /// </summary>
    public static final String Command = "Command" /* nameof */; // PORT: §2.3 nameof

    /// <summary>
    /// The code is a Kusto Directive
    /// </summary>
    public static final String Directive = "Directive" /* nameof */; // PORT: §2.3 nameof

    /// <summary>
    /// The code kind is not known.
    /// </summary>
    public static final String Unknown = "Unknown" /* nameof */; // PORT: §2.3 nameof
}
