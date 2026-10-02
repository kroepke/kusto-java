// Ported from: src/Kusto.Language/Parser/LexicalToken.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
// PORT-SKELETON: W2

package org.graylog.kusto.language.parsing;

import java.util.List;

import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.syntax.SyntaxKind;

/// <summary>
/// A piece of source text that represents simple identifiers, keywords, literals or punctuation.
/// <see cref="LexicalToken"/>'s are produced by <see cref="TokenParser"/>
/// </summary>
public class LexicalToken
{
    /// <summary>
    /// The kind of the token
    /// </summary>
    public SyntaxKind kind() // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    /// <summary>
    /// The trivia (whitespace, etc) that preceeds the proper text of the token.
    /// </summary>
    public String trivia() // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    public String text() // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    public List<Diagnostic> diagnostics() // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }
}
