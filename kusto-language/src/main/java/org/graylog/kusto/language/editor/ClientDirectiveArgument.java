// Ported from: src/Kusto.Language/Editor/ClientDirectiveArgument.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.editor;

/// <summary>
/// A client directive argument info.
/// </summary>
public class ClientDirectiveArgument {
    private final String name;
    private final String text;
    private final Object value;

    /// <summary>
    /// An optional name assigned to the argument: name = value
    /// </summary>
    public String name() {
        return name;
    }

    /// <summary>
    /// The unparsed text of the argument value.
    /// </summary>
    public String text() {
        return text;
    }

    /// <summary>
    /// The parsed value of the argument: double, long or string.
    /// </summary>
    public Object value() { // PORT: §3.7 object holding a boxed Double, Long or String (or null)
        return value;
    }

    public ClientDirectiveArgument(String name, String text, Object value) {
        this.name = name;
        this.text = text;
        this.value = value;
    }
}
