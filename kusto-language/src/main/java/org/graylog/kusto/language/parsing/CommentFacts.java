// Ported from: src/Kusto.Language/Parser/CommentFacts.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;

public final class CommentFacts
{
    private CommentFacts() // PORT: §3.5 static class
    {
    }

    /// <summary>
    /// Gets the text of the comments in the text.
    /// </summary>
    public static List<String> getCommentTexts(String text) // PORT: §3.17 IReadOnlyList<string> -> List<String>
    {
        if (!text.contains("/"))
            return EmptyReadOnlyList.<String>instance();

        var commentTexts = new ArrayList<String>();
        var lines = TextFacts.getLineTexts(text);

        for (var line : lines)
        {
            if (isCommentLine(line))
            {
                commentTexts.add(getCommentLineText(line));
            }
        }

        return commentTexts;
    }

    /// <summary>
    /// True if the line at the given line start appears to be a comment line.
    /// </summary>
    public static boolean isCommentLine(String text, int lineStart)
    {
        var lineLength = TextFacts.getLineLength(text, lineStart);
        var indentationLength = TextFacts.getIndentationLength(text, lineStart);
        return indentationLength < lineLength && isCommentStart(text, lineStart + indentationLength);
    }

    public static boolean isCommentLine(String text) // PORT: §3.12 optional parameter lineStart = 0
    {
        return isCommentLine(text, 0);
    }

    /// <summary>
    /// Gets the text of the comment for a comment line.
    /// </summary>
    public static String getCommentLineText(String text, int lineStart)
    {
        var indentation = TextFacts.getIndentationLength(text, lineStart);
        if (isCommentStart(text, lineStart + indentation))
        {
            var lineEnd = TextFacts.getLineEnd(text, lineStart);
            var commentStart = lineStart + indentation + 2;
            var commentLength = lineEnd - commentStart;
            return DotNetStrings.trim(text.substring(commentStart, commentStart + commentLength)); // PORT: §5.4 Substring(start, length)
        }

        return "";
    }

    public static String getCommentLineText(String text) // PORT: §3.12 optional parameter lineStart = 0
    {
        return getCommentLineText(text, 0);
    }

    /// <summary>
    /// True if the text at the given position appears to be the start of a Kusto comment.
    /// </summary>
    private static boolean isCommentStart(String text, int position)
    {
        return position < text.length() - 1
            && text.charAt(position) == '/'
            && text.charAt(position + 1) == '/';
    }

    /// <summary>
    /// Trims the leading and trailing comment and whitespace lines from the text.
    /// </summary>
    public static String trimCommentAndWhitespaceLines(String text)
    {
        var lineStarts = TextFacts.getLineStarts(text);

        // skip comments and whitespace at start
        int startLine = 0;
        while (startLine < lineStarts.size() &&
            (isCommentLine(text, lineStarts.get(startLine)) || TextFacts.isWhitespaceLine(text, lineStarts.get(startLine))))
        {
            startLine++;
        }

        // skip comments and whitespace at end
        int endLine = lineStarts.size() - 1;
        while (endLine >= 0
            && (isCommentLine(text, lineStarts.get(endLine)) || TextFacts.isWhitespaceLine(text, lineStarts.get(endLine))))
        {
            endLine--;
        }

        if (endLine < startLine)
        {
            // there is nothing that is not a comment or whitespace line
            return "";
        }
        else
        {
            var start = lineStarts.get(startLine);
            var end = lineStarts.get(endLine) + TextFacts.getLineLength(text, lineStarts.get(endLine));
            var length = end - start;
            return text.substring(start, start + length); // PORT: §5.4 Substring(start, length)
        }
    }
}
