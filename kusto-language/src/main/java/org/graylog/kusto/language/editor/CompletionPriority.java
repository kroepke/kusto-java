// Ported from: src/Kusto.Language/Editor/CompletionPriority.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.editor;

/// <summary>
/// The priority that controls the ordering of completion items within their rank.
/// </summary>
public enum CompletionPriority {
    // PORT: §3.17 explicit enum values live in a value field; (int)e is value(), never ordinal()
    Top(1),
    High(2),
    Normal(3),
    Low(4);

    // PORT: §3.17 enum alias (Default = Normal)
    public static final CompletionPriority Default = Normal;

    private final int value;

    CompletionPriority(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
