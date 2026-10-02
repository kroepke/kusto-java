// Ported from: src/Kusto.Language/Editor/EditString.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.editor;

// PORT: §8 stub. Only the read-only slice ClientDirective needs is ported (status `stubbed`, 12 ClientDirective call sites):
// Empty, the constructor, currentText(), length(), charAt, substring(start, length), substring(start), toString(), of(String).
// The edit-tracking surface (OriginalText, GetChanges, ReplaceAt, Apply, ... EditString.cs:91-825) is not ported.

/// <summary>
/// An immutable string-like type that remembers the changes made to construct its current form.
/// </summary>
public class EditString {
    private final String currentText;

    /// <summary>
    /// The text after all the changes.
    /// </summary>
    public String currentText() {
        return currentText;
    }

    /// <summary>
    /// Create a new <see cref="EditString"/> in a pre-edit state.
    /// </summary>
    public EditString(String text) {
        this.currentText = text != null ? text : ""; // PORT: §3.14 ?? (private ctor: currentText ?? "")
    }

    /// <summary>
    /// An empty <see cref="EditString"/>
    /// </summary>
    public static final EditString Empty = new EditString("");

    /// <summary>
    /// The length of the current text.
    /// </summary>
    public int length() {
        return this.currentText().length();
    }

    /// <summary>
    /// The character at the index of the current text.
    /// </summary>
    public char charAt(int index) { // PORT: §3.1 indexer
        return this.currentText().charAt(index);
    }

    /// <summary>
    /// Converts a string into an <see cref="EditString"/> without any edits.
    /// </summary>
    public static EditString of(String text) { // PORT: §3.8 implicit conversion from string (EditString.cs:80)
        return text != null ? new EditString(text) : null;
    }

    /// <summary>
    /// Returns the current text.
    /// </summary>
    @Override
    public String toString() { // PORT: §3.8 the implicit conversion to string (EditString.cs:72) is toString() at each use
        return this.currentText();
    }

    /// <summary>
    /// Returns a new <see cref="EditString"/> containing only the range of characters.
    /// </summary>
    public EditString substring(int start, int length) {
        // PORT: §8 stub: the edit history is not tracked, only the sliced text
        var newText = this.currentText().substring(start, start + length); // PORT: §5.4 start + length
        return new EditString(newText);
    }

    /// <summary>
    /// Returns a new <see cref="EditString"/> containing only the characters from the start position until the end.
    /// </summary>
    public EditString substring(int start) {
        return substring(start, this.currentText().length() - start);
    }
}
