// Ported from: src/Kusto.Language/Parser/CommandFacts.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.utils.dotnet.IntList;

public final class CommandFacts
{
    private CommandFacts() // PORT: §3.5 static class
    {
    }

    // PORT: §3.17 IReadOnlyList<int> -> IntList (see ScriptFacts)

    /// <summary>
    /// True if the line at the given line start appears to be the start of a Kusto command.
    /// </summary>
    public static boolean isCommandStartLine(String text, int lineStart)
    {
        var lineLength = TextFacts.getLineLength(text, lineStart);
        var indentationLength = TextFacts.getIndentationLength(text, lineStart);
        if (lineLength > indentationLength && text.charAt(lineStart + indentationLength) == '.')
        {
            return true;
        }
        return false;
    }

    /// <summary>
    /// Produces a list of kusto command block starts, where each block starts with a new command
    /// </summary>
    public static IntList getCommandBlockStarts(String text)
    {
        return getCommandBlockStarts(text, TextFacts.getLineStarts(text));
    }

    /// <summary>
    /// Produces a list of kusto command block starts, where each block starts with a new command
    /// </summary>
    public static IntList getCommandBlockStarts(String text, IntList lineStarts)
    {
        var commandStarts = new IntList(0);
        int commentStart = -1;

        for (int i = 0; i < lineStarts.size(); i++) // PORT: §3.17 foreach over IntList
        {
            var lineStart = lineStarts.get(i);
            if (isCommandStartLine(text, lineStart))
            {
                // start command from first leading comment line
                var start = commentStart != -1 ? commentStart : lineStart;
                if (commandStarts.size() == 0)
                    start = 0; // if first block, then always start at 0
                commandStarts.add(start);
                commentStart = -1;
            }
            else if (CommentFacts.isCommentLine(text, lineStart))
            {
                if (commentStart == -1)
                    commentStart = lineStart;
            }
            else
            {
                // comment sequence is broken.. start over
                commentStart = -1;
            }
        }

        if (commandStarts.size() == 0)
        {
            // if no commands found, then always have one block starting at 0.
            commandStarts.add(0);
        }

        return commandStarts;
    }

    /// <summary>
    /// Produces a list of command block texts from a script text of multiple commands.
    /// </summary>
    public static List<String> getCommandBlockTexts(String text) // PORT: §3.17 IReadOnlyList<string> -> List<String>
    {
        var lineStarts = TextFacts.getLineStarts(text);
        IntList blockStarts = getCommandBlockStarts(text, lineStarts);
        blockStarts = ScriptFacts.removeDuplicateBlockStarts(blockStarts);
        blockStarts = ScriptFacts.removeInvalidKustoBlockStarts(blockStarts, text);

        var blockTexts = new ArrayList<String>();
        for (int i = 0; i < blockStarts.size(); i++)
        {
            var start = blockStarts.get(i);
            var end = i < blockStarts.size() - 1 ? blockStarts.get(i + 1) : text.length();
            var length = end - start;
            var blockText = text.substring(start, start + length); // PORT: §5.4 Substring(start, length)
            blockTexts.add(blockText);
        }

        return blockTexts;
    }

    /// <summary>
    /// Produce a list of command texts from a script texts of multiple commands,
    /// where commands have leading and trailing comment and whitespace lines removed.
    /// </summary>
    public static List<String> getCommandTexts(String text) // PORT: §3.17 IReadOnlyList<string> -> List<String>
    {
        var commandTexts = new ArrayList<String>();

        var blockTexts = getCommandBlockTexts(text);
        for (var blockText : blockTexts)
        {
            var trimmed = CommentFacts.trimCommentAndWhitespaceLines(blockText);
            if (isCommandStartLine(trimmed, 0))
            {
                commandTexts.add(trimmed);
            }
        }

        return commandTexts;
    }
}
