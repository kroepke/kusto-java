// Ported from: src/Kusto.Language/Utils/ThreadSafeDictionary.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.graylog.kusto.language.utils.dotnet.EqualityComparer;
import org.graylog.kusto.language.utils.dotnet.Func3;
import org.graylog.kusto.language.utils.dotnet.Internal;
import org.graylog.kusto.language.utils.dotnet.Out;

// PORT: §3.13 the !BRIDGE branch over ConcurrentHashMap; get then putIfAbsent, never computeIfAbsent (the factory may run twice, as in .NET).
// ConcurrentHashMap rejects null values; upstream never stores null here.
@Internal
public class ThreadSafeDictionary<TKey, TValue> {
    // PORT: §3.10 a comparer-keyed dictionary wraps its keys in EqualityComparer.Key; without a comparer keys are stored as is
    private final ConcurrentHashMap<Object, TValue> _dictionary;
    private final EqualityComparer<TKey> _comparer;

    public ThreadSafeDictionary() {
        _dictionary = new ConcurrentHashMap<Object, TValue>();
        _comparer = null;
    }

    public ThreadSafeDictionary(EqualityComparer<TKey> comparer) {
        _dictionary = new ConcurrentHashMap<Object, TValue>();
        _comparer = comparer;
    }

    // PORT: §3.10 key wrapper for the comparer-keyed dictionary
    private Object wrap(TKey key) {
        return _comparer != null ? EqualityComparer.of(key, _comparer) : key;
    }

    public int count() {
        return _dictionary.size();
    }

    // PORT: §3.13 ICollection<TKey> live view -> snapshot collection; ordering is unspecified in both
    @SuppressWarnings("unchecked")
    public Collection<TKey> keys() {
        var result = new ArrayList<TKey>();
        for (Object key : _dictionary.keySet()) {
            result.add(_comparer != null ? ((EqualityComparer.Key<TKey>) key).value() : (TKey) key);
        }
        return result;
    }

    // PORT: §3.13 ICollection<TValue> live view -> snapshot collection; ordering is unspecified in both
    public Collection<TValue> values() {
        return new ArrayList<TValue>(_dictionary.values());
    }

    public boolean tryGetValue(TKey key, Out<TValue> value) { // PORT: §3.3 out parameter
        var found = _dictionary.get(wrap(key)); // values are never null
        value.value = found;
        return found != null;
    }

    public boolean tryAdd(TKey key, TValue value) {
        return _dictionary.putIfAbsent(wrap(key), value) == null;
    }

    public TValue getOrAdd(TKey key, TValue value) {
        // PORT: §3.13 ConcurrentDictionary.GetOrAdd: get then putIfAbsent
        var existing = _dictionary.get(wrap(key));
        if (existing != null)
            return existing;
        var previous = _dictionary.putIfAbsent(wrap(key), value);
        return previous != null ? previous : value;
    }

    public TValue getOrAdd(TKey key, Function<TKey, TValue> valueFactory) {
        // PORT: §3.13 ConcurrentDictionary.GetOrAdd: get then putIfAbsent; the factory may run more than once
        var existing = _dictionary.get(wrap(key));
        if (existing != null)
            return existing;
        var value = valueFactory.apply(key);
        var previous = _dictionary.putIfAbsent(wrap(key), value);
        return previous != null ? previous : value;
    }

    public void addOrUpdate(TKey key, TValue value) {
        this.addOrUpdate(key, (k, ev) -> ev, (k, ev, v) -> v, value);
    }

    public void addOrUpdate(TKey key, Function<TKey, TValue> addFactory, BiFunction<TKey, TValue, TValue> updateFactory) {
        // PORT: §3.13 ConcurrentDictionary.AddOrUpdate: retry loop of TryGetValue/TryUpdate or TryAdd
        var wrapped = wrap(key);
        while (true) {
            var old = _dictionary.get(wrapped);
            if (old != null) {
                var updated = updateFactory.apply(key, old);
                if (_dictionary.replace(wrapped, old, updated))
                    return;
            } else {
                var added = addFactory.apply(key);
                if (_dictionary.putIfAbsent(wrapped, added) == null)
                    return;
            }
        }
    }

    // PORT: §3.13 the NET branch of `#if NET472_OR_GREATER || NET || BRIDGE`
    public void addOrUpdate(TKey key, BiFunction<TKey, TValue, TValue> addFactory, Func3<TKey, TValue, TValue, TValue> updateFactory, TValue value) {
        var wrapped = wrap(key);
        while (true) {
            var old = _dictionary.get(wrapped);
            if (old != null) {
                var updated = updateFactory.apply(key, old, value);
                if (_dictionary.replace(wrapped, old, updated))
                    return;
            } else {
                var added = addFactory.apply(key, value);
                if (_dictionary.putIfAbsent(wrapped, added) == null)
                    return;
            }
        }
    }
}
