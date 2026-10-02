// Ported from: src/Kusto.Language/Syntax/SyntaxCategory.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.syntax;

public enum SyntaxCategory
{
    None, // PORT: §3.17 explicit '= 0' equals the ordinal

    /// <summary>
    /// A language keyword
    /// </summary>
    Keyword,

    /// <summary>
    /// A language identifier (column name, etc)
    /// </summary>
    Identifier,

    /// <summary>
    /// Arbitrary punctuation like (, ), [, ], :, etc
    /// </summary>
    Punctuation,

    /// <summary>
    /// Scalar math operators like +, -, *, /, etc
    /// </summary>
    Operator, 

    /// <summary>
    /// Literal values like 10, 1.5, 'a string'
    /// </summary>
    Literal,

    /// <summary>
    /// Special case for list nodes
    /// </summary>
    List,

    /// <summary>
    /// All other non-terminals
    /// </summary>
    Node,

    /// <summary>
    /// Non-typical syntax nodes: EOF, BadXXX, Directives
    /// </summary>
    Other
}
