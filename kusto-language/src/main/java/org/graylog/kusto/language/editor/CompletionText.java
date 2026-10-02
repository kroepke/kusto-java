// Ported from: src/Kusto.Language/Editor/CompletionText.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.editor;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.utils.ListExtensions;

/// <summary>
/// An individual text segment for a <see cref="CompletionItem"/>
/// </summary>
// PORT: §3.20 [DebuggerDisplay] dropped
public class CompletionText {
    private final String text;
    private final boolean caret;
    private final boolean select;

    /// <summary>
    /// The text to apply to insert.
    /// </summary>
    public String text() {
        return text;
    }

    /// <summary>
    /// True if the caret should be moved to start of this text after being inserted.
    /// </summary>
    public boolean caret() {
        return caret;
    }

    /// <summary>
    /// True if the text should be selected after being inserted. 
    /// </summary>
    public boolean select() {
        return select;
    }

    protected CompletionText(String text, boolean caret, boolean select) {
        this.text = text;
        this.caret = caret;
        this.select = select;
    }

    /// <summary>
    /// Creates a new <see cref="CompletionText"/>.
    /// </summary>
    public static CompletionText create(String text, boolean caret, boolean select) {
        return new CompletionText(text, caret, select);
    }

    // PORT: §3.12 optional parameters caret = false, select = false
    public static CompletionText create(String text, boolean caret) {
        return create(text, caret, false);
    }

    // PORT: §3.12 optional parameters caret = false, select = false
    public static CompletionText create(String text) {
        return create(text, false, false);
    }

    /// <summary>
    /// Creates a new <see cref="CompletionText"/> that is intended to be editted.
    /// </summary>
    public static CompletionText createEdit(String text) {
        return new CompletionText(text, true, true); // PORT: §3.12 named arguments caret: true, select: true
    }


    private static final String DefaultCursorMarker = "|";
    private static final String DefaultStartMarker = "[[";
    private static final String DefaultEndMarker = "]]";

    /// <summary>
    /// Parses the text into a list of <see cref="CompletionText"/>.
    /// </summary>
    public static List<CompletionText> parse(
        String textWithMarkers,
        String cursorMarker,
        String startMarker,
        String endMarker) {
        var list = new ArrayList<CompletionText>();

        var cursorPos = textWithMarkers.indexOf(cursorMarker); // PORT: §5.4 culture-sensitive IndexOf(string), ordinal here (D12)
        if (cursorPos >= 0) {
            var beforeCursorText = cursorPos > 0 ? textWithMarkers.substring(0, cursorPos) : "";
            var afterCursorText = textWithMarkers.substring(cursorPos + cursorMarker.length());
            list.add(CompletionText.create(beforeCursorText));
            list.add(CompletionText.create(afterCursorText, true)); // PORT: §3.12 named argument caret: true
        } else {
            var pos = 0;
            while (pos < textWithMarkers.length()) {
                var startPos = textWithMarkers.indexOf(startMarker, pos); // PORT: §5.4 culture-sensitive IndexOf(string, int), ordinal here (D12)
                if (startPos >= 0) {
                    var endPos = textWithMarkers.indexOf(endMarker, startPos + startMarker.length()); // PORT: §5.4 culture-sensitive IndexOf(string, int), ordinal here (D12)
                    if (endPos >= 0) {
                        if (startPos > pos) {
                            list.add(CompletionText.create(textWithMarkers.substring(pos, pos + (startPos - pos)))); // PORT: §5.4 start + length
                        }

                        list.add(CompletionText.createEdit(textWithMarkers.substring(startPos + startMarker.length(), (startPos + startMarker.length()) + (endPos - (startPos + startMarker.length()))))); // PORT: §5.4 start + length
                        pos = endPos + endMarker.length();
                        continue;
                    }
                }

                list.add(CompletionText.create(textWithMarkers.substring(pos)));
                pos = textWithMarkers.length();
            }
        }

        return ListExtensions.toReadOnly(list); // PORT: §3.5
    }

    // PORT: §3.12 optional parameters endMarker = DefaultEndMarker
    public static List<CompletionText> parse(String textWithMarkers, String cursorMarker, String startMarker) {
        return parse(textWithMarkers, cursorMarker, startMarker, DefaultEndMarker);
    }

    // PORT: §3.12 optional parameters startMarker, endMarker = defaults
    public static List<CompletionText> parse(String textWithMarkers, String cursorMarker) {
        return parse(textWithMarkers, cursorMarker, DefaultStartMarker, DefaultEndMarker);
    }

    // PORT: §3.12 optional parameters cursorMarker, startMarker, endMarker = defaults
    public static List<CompletionText> parse(String textWithMarkers) {
        return parse(textWithMarkers, DefaultCursorMarker, DefaultStartMarker, DefaultEndMarker);
    }
}
