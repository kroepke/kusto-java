// Ported from: src/Kusto.Language/Utils/StringTable.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.util.Iterator;

import org.graylog.kusto.language.utils.dotnet.Internal;
import org.graylog.kusto.language.utils.dotnet.Out;

/// <summary>
/// A table of strings accessible via lookup of string ranges
/// </summary>
/// <remarks>
/// You can use this class to look up strings in the table using a range of characters from another string
/// without having to first get a substring to use as a key. This avoids unnecessary alloctions.
/// </remarks>
@Internal
public class StringTable implements Iterable<String> {
    /// <summary/>
    private final TextKeyedDictionary<String> map
        = new TextKeyedDictionary<String>();

    /// <summary>
    /// Construct a new empty <see cref="StringTable"/>
    /// </summary>
    public StringTable() {
    }

    /// <summary>
    /// Add a new string to the table.
    /// </summary>
    /// <returns>The instance of the value added or already found in the table.</returns>
    public String add(String text) {
        Out<String> result = new Out<String>(); // PORT: §3.3 out parameter

        if (!this.map.tryGetValue(text, 0, text.length(), result)) {
            result.value = this.map.getOrAddValue(text, 0, text.length(), (_t, _s, _l) -> (_s > 0 || _l < _t.length()) ? _t.substring(_s, _s + _l) : _t); // PORT: §5.4 Substring(_s, _l)
        }

        return result.value;
    }

    /// <summary>
    /// Add a set of strings to the table.
    /// </summary>
    public void add(Iterable<String> strings) {
        for (var s : strings) {
            this.add(s);
        }
    }

    /// <summary>
    /// Add a new string sub range to the table.
    /// </summary>
    /// <returns>The instance of the value added or already found in the table.</returns>
    public String add(String text, int start, int length) {
        Out<String> result = new Out<String>(); // PORT: §3.3 out parameter

        if (!this.map.tryGetValue(text, start, length, result)) {
            result.value = this.map.getOrAddValue(text, start, length, (_t, _s, _l) -> _t.substring(_s, _s + _l)); // PORT: §5.4 Substring(_s, _l)
        }

        return result.value;
    }

    /// <summary>
    /// True if the table contains the string value.
    /// </summary>
    public boolean contains(String text) {
        return this.map.containsKey(text);
    }

    /// <summary>
    /// True if the table contains the sub string value.
    /// </summary>
    public boolean containsKey(String text, int start, int length) {
        return this.map.containsKey(text, start, length);
    }

    /// <summary/>
    @Override
    public Iterator<String> iterator() { // PORT: §3.17 GetEnumerator
        return this.map.iterator();
    }
}
