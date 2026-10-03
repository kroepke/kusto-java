// Ported from: src/Kusto.Language/Parser/ScriptFacts.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graylog.kusto.language.editor.TextRange;
import org.graylog.kusto.language.utils.dotnet.IntList;

public final class ScriptFacts
{
    private ScriptFacts() // PORT: §3.5 static class
    {
    }

    // PORT: §3.17 IReadOnlyList<int> / List<int> / IEnumerable<int> -> IntList throughout (same as TextFacts.getLineStarts)

    /// <summary>
    /// Produces a list of block starts for a Kusto multi-block document.
    /// </summary>
    public static IntList getKustoBlockStarts(String text, IntList lineStarts)
    {
        var blockStarts = getLineSeparatedBlockStarts(text, lineStarts);
        blockStarts = removeDuplicateBlockStarts(blockStarts);
        blockStarts = removeInvalidKustoBlockStarts(blockStarts, text);
        return blockStarts;
    }

    /// <summary>
    /// Produces a list of block starts by separating blocks by blank lines.
    /// </summary>
    public static IntList getLineSeparatedBlockStarts(String text, IntList lineStarts)
    {
        var blockStarts = new IntList(0);
        blockStarts.add(0); // first block starts at 0

        boolean newBlockNextWhitespaceLine = false; // no prior block
        boolean newBlockNextNonWhitespaceLine = false; // already added first block

        for (int i = 0; i < lineStarts.size(); i++)
        {
            var lineStart = lineStarts.get(i);
            var lineEnd = i + 1 < lineStarts.size() ? lineStarts.get(i + 1) : text.length();
            var allWhitespace = TextFacts.isWhitespaceOnly(text, lineStart, lineEnd - lineStart);

            if (allWhitespace)
            {
                if (newBlockNextWhitespaceLine)
                {
                    blockStarts.add(lineStart);
                }
                newBlockNextWhitespaceLine = true;
                newBlockNextNonWhitespaceLine = true;
            }
            else if (newBlockNextNonWhitespaceLine)
            {
                blockStarts.add(lineStart);
                newBlockNextWhitespaceLine = false;
                newBlockNextNonWhitespaceLine = false;
            }
        }

        return blockStarts;
    }

    /// <summary>
    /// Removes any duplicates from list of block starts.
    /// </summary>
    public static IntList removeDuplicateBlockStarts(IntList blockStarts)
    {
        var starts = orderedCopy(blockStarts); // PORT: §3.6 OrderBy(x => x).ToList()

        for (int i = 0; i < starts.size() - 1;)
        {
            if (starts.get(i) == starts.get(i + 1))
            {
                starts.removeRange(i, 1); // PORT: §3.17 RemoveAt(i)
            }
            else
            {
                i++;
            }
        }

        return starts;
    }

    // PORT: §3.6 helper for OrderBy(x => x).ToList() over ints; not in upstream
    private static IntList orderedCopy(IntList values)
    {
        var array = values.toArray();
        Arrays.sort(array);
        var result = new IntList(array.length);
        for (var value : array)
        {
            result.add(value);
        }
        return result;
    }

    /// <summary>
    /// Removes block starts that cannot appear in a kusto multi-block document.
    /// </summary>
    public static IntList removeInvalidKustoBlockStarts(IntList blockStarts, String text)
    {
        return removeInvalidBlockStarts(blockStarts, getInvalidKustoBlockStartRanges(text));
    }

    /// <summary>
    /// Removes block starts that occur in invalid ranges.
    /// </summary>
    public static IntList removeInvalidBlockStarts(IntList blockStarts, List<TextRange> invalidRanges)
    {
        var starts = orderedCopy(blockStarts); // PORT: §3.6 OrderBy(x => x).ToList()

        for (int iRange = 0, iBlock = 0; iRange < invalidRanges.size() && iBlock < starts.size();)
        {
            var range = invalidRanges.get(iRange);
            var blockStart = starts.get(iBlock);
            if (range.end() <= blockStart)
            {
                // range is before block
                iRange++;
            }
            else if (range.start() >= blockStart)
            {
                // range is after block
                iBlock++;
            }
            else
            {
                // range overlaps block start
                starts.removeRange(iBlock, 1); // PORT: §3.17 RemoveAt(iBlock)
            }
        }

        return starts;
    }

    /// <summary>
    /// Produces a list of ordered invalid ranges where blocks may not start in a Kusto multi-block document.
    /// </summary>
    public static List<TextRange> getInvalidKustoBlockStartRanges(String text)
    {
        var invalidRanges = new ArrayList<TextRange>();

        // look for invalid places for blocks to start
        for (int i = 0, n = text.length(); i < n;)
        {
            // skip over strings in case they contain blank lines
            // or they may contain characters that would otherwise appear to be the start
            // of multi-line string
            int strlen = TokenParser.scanStringLiteral(text, i);
            if (strlen > 0)
            {
                invalidRanges.add(new TextRange(i, strlen));
                i += strlen;
                continue;
            }
            else
            {
                // skip over comments as they may contain characters that appear to be the start
                // of a multi-line string
                int commentLen = TokenParser.scanComment(text, i);
                if (commentLen > 0)
                {
                    invalidRanges.add(new TextRange(i, commentLen));
                    i += commentLen;
                    continue;
                }
            }

            i += 1;
        }

        return invalidRanges;
    }

}
