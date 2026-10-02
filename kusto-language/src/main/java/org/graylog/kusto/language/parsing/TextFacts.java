// Ported from: src/Kusto.Language/Parser/TextFacts.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.utils.ObjectPool;
import org.graylog.kusto.language.utils.dotnet.IntList;
import org.graylog.kusto.language.utils.dotnet.IntRef;

/// <summary>
/// Facts about text.
/// </summary>
public final class TextFacts
{
    private TextFacts() // PORT: §3.5 static class
    {
    }

    public static boolean isWhitespace(char ch)
    {
        switch (ch)
        {
            case '\t':     // tab
            case ' ':      // space
            case '\r':     // carriage return
            case '\n':     // line feed
            case '\u000c': // form feed
            case '\u00a0': // no break space
            case '\u1680': // ogham space mark
            case '\u180e': // mongolian vowel separator
            case '\u2000': // en quad
            case '\u2001': // em quad
            case '\u2002': // en space
            case '\u2003': // em space
            case '\u2004': // three-per-em space
            case '\u2005': // four-per-em space
            case '\u2006': // six-per-em space
            case '\u2007': // figure space
            case '\u2008': // punctuation space
            case '\u2009': // thin space
            case '\u200a': // hair space
            case '\u200b': // zero width space
            case '\u202f': // narrow no break space
            case '\u205f': // medium mathematical space
            case '\u3000': // ideograph space
            case '\uFEFF': // byte order mark
                return true;
            default:
                return false;
        }
    }

    /// <summary>
    /// True if the text is all whitespace.
    /// </summary>
    public static boolean isWhitespaceOnly(String text)
    {
        return isWhitespaceOnly(text, 0, text.length());
    }

    /// <summary>
    /// True if the range of text is all whitespace.
    /// </summary>
    public static boolean isWhitespaceOnly(String text, int start, int length)
    {
        for (int i = start, n = Math.min(text.length(), start + length); i < n; i++)
        {
            if (!isWhitespace(text.charAt(i)))
                return false;
        }

        return true;
    }

    /// <summary>
    /// True if the line starting at start position is only whitespace.
    /// </summary>
    public static boolean isWhitespaceLine(String text, int start)
    {
        var length = getLineLength(text, start);
        return isWhitespaceOnly(text, start, length);
    }

    /// <summary>
    /// Gets the contiguous whitespace in the text from the starting position.
    /// </summary>
    public static String getWhitespace(String text, int start)
    {
        var count = getWhitespaceCount(text, start);
        return count > 0
            ? text.substring(start, start + count) // PORT: §5.4 Substring(start, length)
            : "";
    }

    /// <summary>
    /// Gets the count of contiguous whitespace in the text after the starting position.
    /// </summary>
    public static int getWhitespaceCount(String text, int start)
    {
        var end = start;

        while (end < text.length() && TextFacts.isWhitespace(text.charAt(end)))
        {
            end++;
        }

        return end - start;
    }

    /// <summary>
    /// Gets the count of continguous whitespace before the starting position.
    /// </summary>
    public static int getWhitespaceCountBefore(String text, int start)
    {
        var pos = start - 1;
        while (pos >= 0 && TextFacts.isWhitespace(text.charAt(pos)))
        {
            pos--;
        }

        return start - pos;
    }

    public static boolean isLineBreakStart(char ch)
    {
        switch (ch)
        {
            case '\r':      // Carriage Return
            case '\n':      // Line Feed
            case '\u2028':  // Line Separator.
            case '\u2029':  // Paragraph Separator
                return true;

            default:
                return false;
        }
    }

    public static int getLineBreakLength(String text, int position)
    {
        if (position < text.length())
        {
            var ch = text.charAt(position);
            switch (ch)
            {
                case '\r':
                    if (position + 1 < text.length() && text.charAt(position + 1) == '\n')
                    {
                        return 2;
                    }
                    return 1;
                case '\n':      // Line Feed
                case '\u2028':  // Line Separator.
                case '\u2029':  // Paragraph Separator
                    return 1;
            }
        }

        return 0;
    }

    public static boolean hasLineBreaks(String text)
    {
        for (int i = 0; i < text.length(); i++) // PORT: §3.18 foreach (var c in text)
        {
            var c = text.charAt(i);
            if (isLineBreakStart(c))
                return true;
        }

        return false;
    }

    /// <summary>
    /// Gets the index of the start of the next line break or -1.
    /// </summary>
    public static int getNextLineBreakStart(String text, int start)
    {
        for (int i = start; i < text.length(); i++)
        {
            if (isLineBreakStart(text.charAt(i)))
                return i;
        }

        return -1;
    }

    /// <summary>
    /// Gets the index of the end of the next line break or -1
    /// </summary>
    public static int getNextLineBreakEnd(String text, int start)
    {
        var result = getNextLineBreakStart(text, start);
        return result >= 0 ? result + getLineBreakLength(text, result) : -1;
    }

    /// <summary>
    /// Returns the position of end of the line containing the specified position.
    /// Does not include any line break characters.
    /// </summary>
    public static int getLineEnd(String text, int position)
    {
        var lengthToEnd = getLineLength(text, position);
        return position + lengthToEnd;
    }

    /// <summary>
    /// Returns the position of the start of the line containing the specified position.
    /// </summary>
    public static int getLineStart(String text, int position)
    {
        var previousLineEnd = new IntRef(); // PORT: §3.3 out int
        if (tryGetPreviousLineEnd(text, position, previousLineEnd))
        {
            var lineBreakLength = getLineBreakLength(text, previousLineEnd.value);
            return previousLineEnd.value + lineBreakLength;
        }

        return 0;
    }

    /// <summary>
    /// Gets the position of the end of the previous line given a position on the current line.
    /// </summary>
    public static boolean tryGetPreviousLineEnd(String text, int position, IntRef previousLineEnd) // PORT: §3.3 out int
    {
        for (; position >= 0; position--)
        {
            var lineBreakLength = getLineBreakLength(text, position);

            if (lineBreakLength == 0)
                continue;

            if (lineBreakLength == 1
                && position > 0
                && getLineBreakLength(text, position - 1) == lineBreakLength + 1) // PORT: §3.15 `is int longerLineBreakLength` on a non-nullable int always matches; inlined
            {
                previousLineEnd.value = position - 1;
            }
            else
            {
                previousLineEnd.value = position;
            }

            return true;
        }

        previousLineEnd.value = 0;
        return false;
    }

    /// <summary>
    /// Gets the position of the start of the next line given a position on the current line.
    /// </summary>
    public static boolean tryGetNextLineStart(String text, int position, IntRef nextLineStart) // PORT: §3.3 out int
    {
        var lineEnd = getLineEnd(text, position);
        var lineBreakLength = getLineBreakLength(text, lineEnd);
        if (lineBreakLength > 0)
        {
            nextLineStart.value = lineEnd + lineBreakLength;
            return true;
        }
        else
        {
            nextLineStart.value = 0;
            return false;
        }
    }

    /// <summary>
    /// Gets the position of the start of the first line break or -1
    /// </summary>
    public static int getFirstLineBreakStart(String text)
    {
        return getNextLineBreakStart(text, 0);
    }

    /// <summary>
    /// Gets the index of the end of hte first line break or -1
    /// </summary>
    public static int getFirstLineBreakEnd(String text)
    {
        return getNextLineBreakEnd(text, 0);
    }

    /// <summary>
    /// Gets the index of the start of the last line break or -1
    /// </summary>
    public static int getLastLineBreakStart(String text, int start)
    {
        var result = -1;
        var lastLbEnd = start;

        while (start >= 0)
        {
            var nextLbStart = getNextLineBreakStart(text, lastLbEnd);
            if (nextLbStart >= 0)
            {
                lastLbEnd = nextLbStart + getLineBreakLength(text, lastLbEnd);
                result = start = nextLbStart;
                continue;
            }
            else
            {
                break;
            }
        }

        return result;
    }

    public static int getLastLineBreakStart(String text) // PORT: §3.12 optional parameter start = 0
    {
        return getLastLineBreakStart(text, 0);
    }

    /// <summary>
    /// Gets the index of the start of the last line break or -1
    /// </summary>
    public static int getLastLineBreakEnd(String text, int start)
    {
        var result = getLastLineBreakStart(text, start);
        return result >= 0 ? result + getLineBreakLength(text, result) : -1;
    }

    public static int getLastLineBreakEnd(String text) // PORT: §3.12 optional parameter start = 0
    {
        return getLastLineBreakEnd(text, 0);
    }

    /// <summary>
    /// Returns true if the line is empty or whitespace.
    /// </summary>
    public static boolean isBlankLine(String text, int lineStart)
    {
        var pos = getPositionAfterLeadingWhitespace(text, lineStart);
        return pos == text.length() || isLineBreakStart(text.charAt(pos));
    }

    /// <summary>
    /// Gets the text position after the leading whitespace on the line.
    /// </summary>
    public static int getPositionAfterLeadingWhitespace(String text, int lineStart)
    {
        var pos = lineStart;

        while (pos < text.length() && isWhitespace(text.charAt(pos)) && !isLineBreakStart(text.charAt(pos)))
            pos++;

        return pos;
    }

    /// <summary>
    /// Gets the line length (including line break characters)
    /// </summary>
    public static int getLineLength(String text, int lineStart, boolean includeLineBreak)
    {
        var pos = lineStart;

        while (pos < text.length() && !isLineBreakStart(text.charAt(pos)))
            pos++;

        if (includeLineBreak && pos < text.length())
            pos += getLineBreakLength(text, pos);

        return pos - lineStart;
    }

    public static int getLineLength(String text, int lineStart) // PORT: §3.12 optional parameter includeLineBreak = false
    {
        return getLineLength(text, lineStart, false);
    }

    /// <summary>
    /// Gets the index of the start of the next line or -1;
    /// </summary>
    public static int getNextLineStart(String text, int start)
    {
        var nextStart = getNextLineBreakStart(text, start);
        return nextStart >= 0 ? nextStart + getLineBreakLength(text, nextStart) : nextStart;
    }

    public static boolean isLetter(char ch)
    {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
    }

    public static boolean isDigit(char ch)
    {
        return (ch >= '0' && ch <= '9');
    }

    public static boolean isLetterOrDigit(char ch)
    {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9');
    }

    public static boolean isHexDigit(char ch)
    {
        return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
    }

    /// <summary>
    /// Gets the starting offset of all the lines.
    /// </summary>
    public static void getLineStarts(String text, IntList lineStarts) // PORT: §3.17 List<int> -> IntList
    {
        lineStarts.add(0);

        int lineStart = 0;

        for (int i = 0, n = text.length(); i < n;)
        {
            var lb = getLineBreakLength(text, i);
            if (lb > 0)
            {
                i += lb;
                lineStart = i;
                lineStarts.add(lineStart);
            }
            else
            {
                i++;
            }
        }
    }

    /// <summary>
    /// Gets the starting offset of all the lines.
    /// </summary>
    public static IntList getLineStarts(String text) // PORT: §3.17 IReadOnlyList<int> -> IntList
    {
        var starts = new IntList();
        getLineStarts(text, starts);
        return starts;
    }

    /// <summary>
    /// Gets the starting position of the 1-based line number.
    /// </summary>
    public static boolean tryGetLineStart(String text, int line, IntRef lineStart) // PORT: §3.3 out int
    {
        if (line < 1)
        {
            lineStart.value = 0;
            return false;
        }
        else if (line == 1)
        {
            lineStart.value = 0;
            return true;
        }

        for (int i = 0, n = text.length(), count = 1; i < n;)
        {
            var lb = getLineBreakLength(text, i);
            if (lb > 0)
            {
                i += lb;

                count++;
                if (count == line)
                {
                    lineStart.value = i;
                    return true;
                }
            }
            else
            {
                i++;
            }
        }

        // line beyond the end
        lineStart.value = 0;
        return false;
    }

    private static final ObjectPool<IntList> s_lineStarts =
        new ObjectPool<IntList>(() -> new IntList(), list -> list.clear()); // PORT: §3.17 List<int> -> IntList

    /// <summary>
    /// Gets the 1-based line and lineOffset for a position.
    /// </summary>
    public static boolean tryGetLineAndOffset(String text, int position, IntRef line, IntRef lineOffset) // PORT: §3.3 out int
    {
        var lineStarts = s_lineStarts.allocateFromPool();
        try
        {
            getLineStarts(text, lineStarts);
            return tryGetLineAndOffset(lineStarts, position, line, lineOffset);
        }
        finally
        {
            s_lineStarts.returnToPool(lineStarts);
        }
    }

    /// <summary>
    /// Gets the position corresponding to the 1-based line and lineOffset.
    /// </summary>
    public static boolean tryGetPosition(String text, int line, int lineOffset, IntRef position) // PORT: §3.3 out int
    {
        var lineStart = new IntRef(); // PORT: §3.3 out var
        if (line >= 1 && lineOffset >= 1
            && tryGetLineStart(text, line, lineStart))
        {
            position.value = lineStart.value + (lineOffset - 1);
            return position.value <= text.length();
        }

        position.value = 0;
        return false;
    }

    /// <summary>
    /// Gets the 1-based line and lineOffset for a position.
    /// </summary>
    public static boolean tryGetLineAndOffset(IntList lineStarts, int position, IntRef line, IntRef lineOffset) // PORT: §3.17 List<int> -> IntList; §3.3 out int
    {
        line.value = lineStarts.binarySearch(position);
        line.value = line.value >= 0 ? line.value : ~line.value - 1;

        if (line.value < 0 || line.value >= lineStarts.size())
        {
            line.value = 0;
            lineOffset.value = 0;
            return false;
        }

        lineOffset.value = position - lineStarts.get(line.value) + 1; // 1 based
        line.value++; // 1 based

        return true;
    }

    /// <summary>
    /// Gets the position corresponding to the 1-based line and lineOffset.
    /// </summary>
    public static boolean tryGetPosition(IntList lineStarts, int line, int lineOffset, IntRef position) // PORT: §3.17 IReadOnlyList<int> -> IntList; §3.3 out int
    {
        if (line >= 1 && line <= lineStarts.size() && lineOffset >= 1)
        {
            var lineStart = lineStarts.get(line - 1);
            position.value = lineStart + (lineOffset - 1);
            return true;
        }

        position.value = 0;
        return false;
    }

    /// <summary>
    /// Trims the whitespace off the end of the text range.
    /// Returns the new length with the whitespace removed.
    /// </summary>
    public static int trimEnd(String text)
    {
        return trimEnd(text, 0, text.length());
    }

    /// <summary>
    /// Trims the whitespace off the end of the text range.
    /// Returns the new length with the whitespace removed.
    /// </summary>
    public static int trimEnd(String text, int start, int length)
    {
        for (var ln = length - 1; ln >= 0; ln--)
        {
            if (!isWhitespace(text.charAt(start + ln)))
                return ln + 1;
        }

        return 0;
    }

    /// <summary>
    /// Trims the whitespace from the start of the range.
    /// </summary>
    public static void trimRangeStart(String text, IntRef start, IntRef length) // PORT: §3.3 ref int
    {
        var count = getWhitespaceCount(text, start.value);
        start.value += count;
        length.value -= count;
    }

    /// <summary>
    /// Trims the whitespace from the end of the range.
    /// </summary>
    public static void trimRangeEnd(String text, int start, IntRef length) // PORT: §3.3 ref int
    {
        length.value = trimEnd(text, start, length.value);
    }

    /// <summary>
    /// Trims the whitespace from the start and end of the range.
    /// </summary>
    public static void trimRange(String text, IntRef start, IntRef length) // PORT: §3.3 ref int
    {
        trimRangeStart(text, start, length);
        trimRangeEnd(text, start.value, length);
    }

    /// <summary>
    /// Expands the range to include the start of the first line.
    /// </summary>
    public static void expandRangeToStartOfFirstLine(String text, IntRef start, IntRef length) // PORT: §3.3 ref int
    {
        var newStart = getLineStart(text, start.value);
        start.value = newStart;
        // PORT-BUG: start is overwritten before the delta is taken, so length never grows
        length.value += start.value - newStart;
    }

    /// <summary>
    /// Expands range to include the end of the last line.
    /// </summary>
    public static void expandRangeToEndOfLastLine(String text, int start, IntRef length) // PORT: §3.3 ref int
    {
        var newEnd = getLineEnd(text, start + length.value);
        length.value = newEnd - start;
    }

    /// <summary>
    /// Expands the range to include the start of the first line and the end of the last line.
    /// </summary>
    public static void expandRangeToStartAndEndOfLines(String text, IntRef start, IntRef length) // PORT: §3.3 ref int
    {
        expandRangeToStartOfFirstLine(text, start, length);
        expandRangeToEndOfLastLine(text, start.value, length);
    }

    /// <summary>
    /// Gets the length of the whitespace indentation for the line containing the specified position.
    /// </summary>
    public static int getIndentationLength(String text, int position)
    {
        var startOfLine = getLineStart(text, position);
        return getWhitespaceCount(text, startOfLine);
    }

    /// <summary>
    /// Gets the indentation text for the line containing the position.
    /// </summary>
    public static String getIndentationText(String text, int position)
    {
        var startOfLine = getLineStart(text, position);
        return getWhitespace(text, startOfLine);
    }

    /// <summary>
    /// Gets the text of the line for the specified 1-based line number.
    /// </summary>
    public static String getLineText(String text, int line)
    {
        var lineStart = new IntRef(); // PORT: §3.3 out var
        if (tryGetLineStart(text, line, lineStart))
        {
            var lineEnd = getLineEnd(text, lineStart.value);
            return text.substring(lineStart.value, lineStart.value + (lineEnd - lineStart.value)); // PORT: §5.4 Substring(start, length)
        }
        return "";
    }

    /// <summary>
    /// Gets the text of the lines for each line in the text.
    /// </summary>
    public static List<String> getLineTexts(String text)
    {
        var lineStarts = getLineStarts(text);
        var lines = new ArrayList<String>(lineStarts.size());
        for (int i = 0; i < lineStarts.size(); i++)
        {
            var lineStart = lineStarts.get(i);
            var lineLength = getLineLength(text, lineStart);
            lines.add(text.substring(lineStart, lineStart + lineLength)); // PORT: §5.4 Substring(start, length)
        }
        return lines;
    }
}
