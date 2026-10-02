// Ported from: src/Kusto.Language/Utils/StringAndNumberComparer.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.util.Comparator;

import org.graylog.kusto.language.utils.dotnet.DotNetChars;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// Compares strings with embedded numbers using the numbers numeric order, not the text order.
/// </summary>
@Internal
public class StringAndNumberComparer implements Comparator<String> { // PORT: §3.10 IComparer<string>
    // PORT: §5.4 StringComparison.Ordinal / OrdinalIgnoreCase is carried as an ignore-case flag
    private final boolean _ignoreCase;

    private StringAndNumberComparer(boolean ignoreCase) {
        _ignoreCase = ignoreCase;
    }

    public static final StringAndNumberComparer Ordinal =
        new StringAndNumberComparer(false); // StringComparison.Ordinal

    public static final StringAndNumberComparer OrdinalIgnoreCase =
        new StringAndNumberComparer(true); // StringComparison.OrdinalIgnoreCase

    @Override
    public int compare(String xText, String yText) {
        int xStart = 0;
        int yStart = 0;

        while (xStart < xText.length() && yStart < yText.length()) {
            var xSegmentIsNumber = DotNetChars.isDigit(xText.charAt(xStart)); // PORT: §5.1 char.IsDigit
            var ySegmentIsNumber = DotNetChars.isDigit(yText.charAt(yStart)); // PORT: §5.1 char.IsDigit

            if (xSegmentIsNumber && ySegmentIsNumber) {
                // both segments are numbers
                int xNumberLength = lengthOfNumber(xText, xStart);
                int yNumberLength = lengthOfNumber(yText, yStart);

                var comp = compareNumberSegment(xText, xStart, xNumberLength, yText, yStart, yNumberLength);
                if (comp != 0)
                    return comp;

                xStart += xNumberLength;
                yStart += yNumberLength;
            } else if (!xSegmentIsNumber && !ySegmentIsNumber) {
                // neither segments are numbers
                var commonLength = getCommonLengthWithoutDigits(xText, xStart, yText, yStart);

                var comp = DotNetStrings.compare(xText, xStart, yText, yStart, commonLength, _ignoreCase); // PORT: §5.4 string.Compare with StringComparison
                if (comp != 0)
                    return comp;

                xStart += commonLength;
                yStart += commonLength;
            } else {
                // one segment is a number and not the other, so the result is just comparison of first character
                // since text characters can either sort before or after numbers
                return xText.charAt(xStart) - yText.charAt(yStart);
            }
        }

        // everything was identical until one ran out of characters
        // therefore the one without remaining text orders first
        if (xText.length() - xStart > 0) {
            // x has more characters, so x orders after y
            return 1;
        } else if (yText.length() - yStart > 0) {
            // y has more characters, so x orders before y
            return -1;
        } else {
            // both are the same
            return 0;
        }
    }

    /// <summary>
    /// Gets the largest length the two strings share without digits.
    /// </summary>
    private int getCommonLengthWithoutDigits(String xText, int xStart, String yText, int yStart) {
        int length = 0;

        while (xStart + length < xText.length() && !DotNetChars.isDigit(xText.charAt(xStart + length)) // PORT: §5.1 char.IsDigit
            && yStart + length < yText.length() && !DotNetChars.isDigit(yText.charAt(yStart + length))) { // PORT: §5.1 char.IsDigit
            length++;
        }

        return length;
    }

    // PORT-BUG: the leading-zero skips below never shorten xLength/yLength, so for numbers of unequal length the result is
    // always the difference of the ORIGINAL lengths ("007" sorts after "8"); only equal-length numbers compare by text. Mirrored verbatim.
    private int compareNumberSegment(String xText, int xStart, int xLength, String yText, int yStart, int yLength) {
        // if x is longer, skip leading zeros up to length of y
        while (xStart < xText.length() && xLength > yLength && xText.charAt(xStart) == '0')
            xStart++;

        // if y is longer, skip leading zeros up to length of x
        while (yStart < yText.length() && yLength > xLength && yText.charAt(yStart) == '0')
            yStart++;

        // if both numbers have same length use text comparsison
        if (xLength > 0 && xLength == yLength)
            return DotNetStrings.compare(xText, xStart, yText, yStart, xLength); // PORT: §5.4 culture-sensitive upstream, ordinal here (D12)

        // longer wins because it has more digits
        return xLength - yLength;
    }

    private static int lengthOfNumber(String text, int startIndex) {
        int index = startIndex;
        for (; index < text.length(); index++) {
            if (!DotNetChars.isDigit(text.charAt(index))) // PORT: §5.1 char.IsDigit
                break;
        }
        return index - startIndex;
    }
}
