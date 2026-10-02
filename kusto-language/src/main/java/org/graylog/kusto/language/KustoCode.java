// Ported from: src/Kusto.Language/KustoCode.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
// PORT-SKELETON: W4

package org.graylog.kusto.language;

import org.graylog.kusto.language.syntax.SyntaxNode;

/// <summary>
/// The code for a single query or command.
/// </summary>
public final class KustoCode
{
    /// <summary>
    /// The root <see cref="SyntaxNode"/> of the syntax tree.
    /// </summary>
    public SyntaxNode syntax() // PORT-PENDING: W4
    {
        throw new UnsupportedOperationException("PORT-PENDING: W4");
    }

    /// <summary>
    /// Create a new <see cref="KustoCode"/> instance from the text and globals. Does not perform semantic analysis.
    /// </summary>
    public static KustoCode parse(String text) // PORT-PENDING: W4
    {
        throw new UnsupportedOperationException("PORT-PENDING: W4");
    }
}
