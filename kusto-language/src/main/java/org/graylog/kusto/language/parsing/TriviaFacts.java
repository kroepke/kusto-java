// Ported from: src/Kusto.Language/Parser/TriviaFacts.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import org.graylog.kusto.language.utils.dotnet.IntRef;

public final class TriviaFacts
{
    private TriviaFacts() // PORT: §3.5 static class
    {
    }

    /// <summary>
    /// Gets the span of the comment at the position.
    /// </summary>
    public static boolean tryGetCommentSpan(String trivia, int position, IntRef start, IntRef length) // PORT: §3.3 out int
    {
        start.value = 0;
        length.value = 0;

        for (int p = 0; p < trivia.length() && p <= position; p++)
        {
            length.value = getCommentLength(trivia, p);
            if (length.value > 0)
            {
                // is position inside or adjacent to this comment?
                if (position >= p && position <= p + length.value)
                {
                    start.value = p;
                    return true;
                }
                else
                {
                    p += length.value - 1;
                }
            }
        }

        return false;
    }

    private static int getCommentLength(String trivia, int start)
    {
        var end = start;

        // comment starts with non-whitespace
        if (!TextFacts.isWhitespace(trivia.charAt(start)))
        {
            for (; end < trivia.length(); end++)
            {
                var lbLen = TextFacts.getLineBreakLength(trivia, end);
                if (lbLen > 0)
                {
                    end += lbLen;
                    break;
                }
            }
        }

        return end - start;
    }
}
