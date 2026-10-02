// Ported from: src/Kusto.Language/Editor/CompletionPriority.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.editor;

/// <summary>
/// The rank that control the ordering of completion items.
/// </summary>
public enum CompletionRank {
    // PORT: §3.17 explicit enum values live in a value field; (int)e is value(), never ordinal()
    Literal(1),
    Aggregate(2),
    Column(3),
    Table(4),
    Entity(5),  // other entities not tables or functions
    Variable(6),
    Function(7),
    Keyword(8),
    StringOperator(9),
    MathOperator(10),
    Other(11),
    Default(12); // based on completion kind

    private final int value;

    CompletionRank(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
