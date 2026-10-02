// Ported from: src/Kusto.Language/Parser/TextFacts.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
// PORT-SKELETON: W2

package org.graylog.kusto.language.parsing;

public final class TextFacts
{
    private TextFacts() // PORT: §3.5 static class
    {
    }

    public static boolean isWhitespace(char ch) // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    public static boolean hasLineBreaks(String text) // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    public static int getLineEnd(String text, int position) // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    public static int getNextLineStart(String text, int start) // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    public static boolean isLetter(char ch) // PORT-PENDING: W2
    {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }
}
