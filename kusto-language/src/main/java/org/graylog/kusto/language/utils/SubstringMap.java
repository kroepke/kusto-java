// Ported from: src/Kusto.Language/Utils/SubstringMap.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.graylog.kusto.language.utils.dotnet.DotNet;

/// <summary>
/// Represents a map between a set of string keys and values
/// that can be used to quickly find the longest match in a region of another string.
/// </summary>
public class SubstringMap<TValue> {
    private final Node<TValue> _root;

    public SubstringMap(Iterable<Map.Entry<String, TValue>> keyValuePairs) { // PORT: §3.2 KeyValuePair -> Map.Entry
        _root = Node.from(keyValuePairs);
    }

    /// <summary>
    /// Finds the 
    /// </summary>
    public Map.Entry<String, TValue> getLongestMatch(String text, int start) {
        Node<TValue> bestNode = null;

        var node = _root;
        for (var pos = start; pos < text.length(); pos++) {
            node = node.getSubNode(text.charAt(pos));
            if (node == null)
                break;
            if (node.hasValue()) {
                bestNode = node;
            }
        }

        if (bestNode != null)
            return bestNode.value();

        return noValue();
    }

    // PORT: §3.9 static field of a generic class: one shared raw static, typed access through noValue()
    private static final Map.Entry<String, Object> NoValue =
        new AbstractMap.SimpleImmutableEntry<String, Object>("", null); // default(TValue) is null

    @SuppressWarnings("unchecked")
    private static <TValue> Map.Entry<String, TValue> noValue() { // PORT: §3.9
        return (Map.Entry<String, TValue>) (Map.Entry<String, ?>) NoValue;
    }

    // PORT: §3.10 a nested class of a generic class does not see TValue in a static Java nested class, so Node carries its own TValue
    private static final class Node<TValue> {
        /// <summary>
        /// The <see cref="KeyValuePair{TKey, TValue}"/> resolvable at this node.
        /// </summary>
        private Map.Entry<String, TValue> value; // PORT: §3.1 { get; private set; }
        private boolean hasValue;

        public Map.Entry<String, TValue> value() {
            return value;
        }

        public boolean hasValue() {
            return hasValue;
        }

        /// <summary>
        /// 
        /// </summary>
        private CharMap<TValue> map;

        private Node() {
        }

        public static <TValue> Node<TValue> from(Iterable<Map.Entry<String, TValue>> keyValuePairs) {
            var node = new Node<TValue>();

            for (var pair : keyValuePairs) {
                node.add(pair);
            }

            return node;
        }

        private void add(Map.Entry<String, TValue> pair) {
            var node = this;
            Node<TValue> subNode;

            var key = pair.getKey();
            for (int i = 0; i < key.length(); i++) { // PORT: §3.18 foreach (var ch in pair.Key)
                var ch = key.charAt(i);
                if (node.map == null) {
                    subNode = new Node<TValue>();
                    node.map = new SingleCharMap<TValue>(ch, subNode);
                } else {
                    subNode = node.map.get(ch);

                    if (subNode == null) {
                        subNode = new Node<TValue>();
                        node.map = node.map.add(ch, subNode);
                    }
                }

                node = subNode;
            }

            node.value = pair;
            node.hasValue = true;
        }

        /// <summary>
        /// Gets the <see cref="Node"/> corresponding to the key.
        /// </summary>
        public Node<TValue> getSubNode(char key) {
            return this.map != null ? this.map.get(key) : null; // PORT: §3.14 ?.
        }

        private abstract static class CharMap<TValue> {
            public abstract CharMap<TValue> add(char key, Node<TValue> node);
            public abstract Node<TValue> get(char key);
        }

        private static final class SingleCharMap<TValue> extends CharMap<TValue> {
            private final char _key;
            private final Node<TValue> _node;

            public SingleCharMap(char key, Node<TValue> node) {
                _key = key;
                _node = node;
            }

            @Override
            public CharMap<TValue> add(char key, Node<TValue> node) {
                return new ArrayCharMap<TValue>(3).add(_key, _node).add(key, node);
            }

            @Override
            public Node<TValue> get(char key) {
                return _key == key ? _node : null;
            }
        }

        private static final class ArrayCharMap<TValue> extends CharMap<TValue> {
            // PORT: §3.2 KeyValuePair<char, Node>[] is an array of entries; unset slots hold default(KeyValuePair) = ('\0', null)
            private final Map.Entry<Character, Node<TValue>>[] _pairs;
            private int _length;

            @SuppressWarnings({"unchecked", "rawtypes"})
            public ArrayCharMap(int count) {
                _pairs = (Map.Entry<Character, Node<TValue>>[]) new Map.Entry[count];
                for (int i = 0; i < count; i++) {
                    _pairs[i] = new AbstractMap.SimpleImmutableEntry<Character, Node<TValue>>('\0', null);
                }
                _length = 0;
            }

            @Override
            public CharMap<TValue> add(char key, Node<TValue> node) {
                if (_length < _pairs.length - 1) {
                    _pairs[_length] = new AbstractMap.SimpleImmutableEntry<Character, Node<TValue>>(key, node);
                    _length++;
                    return this;
                } else {
                    return new DictionaryMap<TValue>(_pairs).add(key, node);
                }
            }

            @Override
            public Node<TValue> get(char key) {
                for (int i = 0; i < _length; i++) {
                    var kvp = _pairs[i];
                    if (kvp.getKey() == key)
                        return kvp.getValue();
                }

                return null;
            }
        }

        private static final class DictionaryMap<TValue> extends CharMap<TValue> {
            private final LinkedHashMap<Character, Node<TValue>> _map; // PORT: §3.17 Dictionary -> LinkedHashMap

            public DictionaryMap(Map.Entry<Character, Node<TValue>>[] kvps) {
                _map = new LinkedHashMap<Character, Node<TValue>>();

                if (kvps != null) {
                    // PORT-BUG: copies every slot of the 3-slot array, including the unused default ('\0', null) slot, so key '\0' maps to null
                    for (var kvp : kvps) {
                        DotNet.dictionaryAdd(_map, kvp.getKey(), kvp.getValue()); // PORT: §3.17 Dictionary.Add throws on a duplicate key
                    }
                }
            }

            public DictionaryMap() { // PORT: §3.12 optional parameter kvps = null
                this(null);
            }

            @Override
            public CharMap<TValue> add(char key, Node<TValue> node) {
                DotNet.dictionaryAdd(_map, key, node); // PORT: §3.17 Dictionary.Add throws on a duplicate key
                return this;
            }

            @Override
            public Node<TValue> get(char key) {
                return _map.get(key); // PORT: §3.3 TryGetValue; value is null when absent
            }
        }
    }
}
