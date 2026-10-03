// Ported from: src/Kusto.Language/Editor/TextRange.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.editor;

/// <summary>
/// A range of text.
/// </summary>
// PORT: §3.2 struct -> record; equality is never used upstream (only ScriptFacts builds and reads ranges). Components are lowerCamel per §2.3.
public record TextRange(int start, int length)
{
    /// <summary>
    /// The starting position of the range in the text.
    /// </summary>
    // PORT: §3.1 Start { get; } is the record accessor start()

    /// <summary>
    /// The length of the range in the text.
    /// </summary>
    // PORT: §3.1 Length { get; } is the record accessor length()

    /// <summary>
    /// The ending of the range in the text.
    /// </summary>
    public int end()
    {
        return this.start + this.length;
    }

    // PORT: §3.2 the (start, length) constructor is the record's canonical constructor

    public static TextRange fromBounds(int start, int end)
    {
        return new TextRange(start, end - start);
    }

    public static final TextRange Empty = new TextRange(0, 0);

    /// <summary>
    /// True if this text range overlaps the other text range.
    /// </summary>
    public boolean overlaps(TextRange other)
    {
        return overlaps(this.start, this.length, other.start, other.length);
    }

    /// <summary>
    /// True if the range A overlaps the range B
    /// </summary>
    public static boolean overlaps(int startA, int lengthA, int startB, int lengthB)
    {
        var endA = startA + lengthA;
        var endB = startB + lengthB;
        return Math.max(startA, startB) <= Math.min(endA, endB);
    }
}
