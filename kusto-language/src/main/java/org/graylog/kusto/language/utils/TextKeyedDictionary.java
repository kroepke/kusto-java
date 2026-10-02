// Ported from: src/Kusto.Language/Utils/TextKeyedDictionary.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.util.Iterator;
import java.util.LinkedHashMap;

import org.graylog.kusto.language.utils.dotnet.Func3;
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
public class TextKeyedDictionary<TValue> implements Iterable<TValue> {
    /// <summary/>
    private final LinkedHashMap<Key, TValue> map // PORT: §3.17 Dictionary -> LinkedHashMap
        = new LinkedHashMap<Key, TValue>();

    /// <summary>
    /// Construct a new empty <see cref="TextKeyedDictionary{TValue}"/>
    /// </summary>
    public TextKeyedDictionary() {
    }

    /// <summary>
    /// Gets the corresponding value. Returns true if the value is found.
    /// </summary>
    public boolean tryGetValue(String text, int start, int length, Out<TValue> value) { // PORT: §3.3 out parameter
        Key key = new Key(text, start, length);
        return this.tryGet(key, value);
    }

    // PORT: §3.3 Dictionary.TryGetValue(key, out value); a null value still counts as found
    private boolean tryGet(Key key, Out<TValue> value) {
        TValue v = this.map.get(key);
        if (v != null || this.map.containsKey(key)) {
            value.value = v;
            return true;
        }

        value.value = null; // PORT: §3.10 default(TValue)
        return false;
    }

    /// <summary>
    /// Gets the corresponding value or adds it.
    /// </summary>
    public TValue getOrAddValue(String text, TValue newValue) {
        return this.getOrAddValue(text, 0, text.length(), newValue);
    }

    /// <summary>
    /// Gets the corresponding value or adds it.
    /// </summary>
    public TValue getOrAddValue(String text, int start, int length, TValue newValue) {
        Out<TValue> value = new Out<TValue>(); // PORT: §3.3
        Key key = new Key(text, start, length);

        if (!this.tryGet(key, value)) {
            value.value = newValue;
            this.map.put(key, value.value); // PORT: §3.17 Dictionary.Add; the key was just found absent
        }

        return value.value;
    }

    /// <summary>
    /// Gets the corresponding value or adds it.
    /// </summary>
    public TValue getOrAddValue(String text, Func3<String, Integer, Integer, TValue> evaluator) { // PORT: §3.8 Func<string,int,int,TValue>
        return this.getOrAddValue(text, 0, text.length(), evaluator);
    }

    /// <summary>
    /// Gets the corresponding value or adds it.
    /// </summary>
    public TValue getOrAddValue(String text, int start, int length, Func3<String, Integer, Integer, TValue> evaluator) { // PORT: §3.8 Func<string,int,int,TValue>
        Out<TValue> value = new Out<TValue>(); // PORT: §3.3
        Key key = new Key(text, start, length);

        if (!this.tryGet(key, value)) {
            value.value = evaluator.apply(text, start, length);
            this.map.put(key, value.value); // PORT: §3.17 Dictionary.Add; the key was just found absent
        }

        return value.value;
    }

    /// <summary>
    /// True if the table contains the string value.
    /// </summary>
    public boolean containsKey(String text) {
        return this.containsKey(text, 0, text.length());
    }

    /// <summary>
    /// True if the table contains the sub string value.
    /// </summary>
    public boolean containsKey(String text, int start, int length) {
        if (start < 0 || length < 1 || start + length > text.length()) {
            return false;
        }

        return this.map.containsKey(new Key(text, start, length));
    }

    /// <summary/>
    @Override
    public Iterator<TValue> iterator() { // PORT: §3.17 GetEnumerator over map.Values
        return this.map.values().iterator();
    }

    /// <summary/>
    // PORT: §3.2 struct Key : IEquatable<Key> -> final class with the upstream equality (never a record), D24
    private static final class Key {
        /// <summary/>
        private final String text;

        /// <summary/>
        private final int start;

        /// <summary/>
        private final int length;

        /// <summary/>
        private final int hash;

        /// <summary/>
        public Key(String text) {
            this(text, 0, text.length());
        }

        /// <summary/>
        public Key(String text, int start, int length) {
            this.text = text;
            this.start = start;
            this.length = length;
            this.hash = getFNVHashCode(text, start, length);
        }

        /// <summary>
        /// The offset bias value used in the FNV-1a algorithm
        /// See http://en.wikipedia.org/wiki/Fowler%E2%80%93Noll%E2%80%93Vo_hash_function
        /// </summary>
        private static final int FnvOffsetBias = (int) 2166136261L; // unchecked((int)2166136261)

        /// <summary>
        /// The generative factor used in the FNV-1a algorithm
        /// See http://en.wikipedia.org/wiki/Fowler%E2%80%93Noll%E2%80%93Vo_hash_function
        /// </summary>
        private static final int FnvPrime = 16777619;

        /// <summary>
        /// Compute the hashcode of a sub-string using FNV-1a
        /// See http://en.wikipedia.org/wiki/Fowler%E2%80%93Noll%E2%80%93Vo_hash_function
        /// </summary>
        private static int getFNVHashCode(String text, int start, int length) {
            int hashCode = FnvOffsetBias;
            int end = start + length;

            for (int i = start; i < end; i++) {
                hashCode = (hashCode ^ text.charAt(i)) * FnvPrime; // int arithmetic wraps like unchecked
            }

            return hashCode;
        }

        /// <summary/>
        public boolean equals(Key other) {
            if (this.hash != other.hash
                || this.length != other.length) {
                return false;
            }

            for (int i = 0; i < this.length; i++) {
                if (this.text.charAt(this.start + i) != other.text.charAt(other.start + i)) {
                    return false;
                }
            }

            return true;
        }

        // PORT: D24 equals(Object) delegating to the typed equals
        @Override
        public boolean equals(Object obj) {
            return obj instanceof Key other && this.equals(other);
        }

        /// <summary/>
        @Override
        public int hashCode() {
            return this.hash;
        }
    }
}
