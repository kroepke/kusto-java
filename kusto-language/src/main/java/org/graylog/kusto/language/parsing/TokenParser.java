// Ported from: src/Kusto.Language/Parser/TokenParser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
// PORT-SKELETON: W2

package org.graylog.kusto.language.parsing;

/// <summary>
/// Parses <see cref="LexicalToken"/> for Kusto query language
/// </summary>
public class TokenParser
{
    public static int scanWhitespace(String text, int start) // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    public static int scanTrivia(String text, int start) // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    public static int scanIdentifier(String text, int start) // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    public static int scanIdentifier(String text) // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    public static int scanLongLiteral(String text, int start) // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    public static int scanLongLiteral(String text) // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    public static int scanRealLiteral(String text, int start) // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    public static int scanRealLiteral(String text) // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    public static int scanStringLiteral(String text, int start, boolean failWhenMissingEndQuote) // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    public static int scanStringLiteral(String text, int start) // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    public static int scanStringLiteral(String text) // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }
}
