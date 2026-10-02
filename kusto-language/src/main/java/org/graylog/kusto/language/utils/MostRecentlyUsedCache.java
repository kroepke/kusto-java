// Ported from: src/Kusto.Language/Utils/MostRecentlyUsedCache.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.graylog.kusto.language.utils.dotnet.Internal;
import org.graylog.kusto.language.utils.dotnet.Out;

/// <summary>
/// A map that limits the number of entries,
/// retaining only the most recently accessed ones.
/// </summary>
// PORT: §3.10 where TKey : IEquatable<TKey> has no Java counterpart; keys are compared with equals
@Internal
public class MostRecentlyUsedCache<TKey, TValue> {
    private List<Map.Entry<TKey, TValue>> _pairs; // PORT: §3.2 KeyValuePair<K,V> -> Map.Entry
    private final int _maxSize;

    public MostRecentlyUsedCache(int maxSize) {
        _pairs = new ArrayList<Map.Entry<TKey, TValue>>();
        _maxSize = maxSize;
    }

    public MostRecentlyUsedCache() { // PORT: §3.12 optional parameter maxSize = 10
        this(10);
    }

    /// <summary>
    /// The current number of entries in the cache.
    /// </summary>
    public int count() {
        return _pairs.size();
    }

    /// <summary>
    /// Gets the value for the key and marks the key as recently used.
    /// </summary>
    public boolean tryGetValue(TKey key, Out<TValue> value) { // PORT: §3.3 out parameter
        synchronized (this) { // PORT: §3.13 lock
            for (int i = 0; i < _pairs.size(); i++) {
                var pair = _pairs.get(i);
                if (pair.getKey().equals(key)) {
                    value.value = pair.getValue();

                    // move most recently accessed pair to front of list
                    _pairs.remove(i);
                    _pairs.add(0, pair);

                    return true;
                }
            }

            value.value = null; // PORT: §3.10 default(TValue)
            return false;
        }
    }

    /// <summary>
    /// Adds or updates the value for the key and removes any items beyond the max size.
    /// </summary>
    public void addOrUpdate(TKey key, TValue value) {
        synchronized (this) { // PORT: §3.13 lock
            for (int i = 0; i < _pairs.size(); i++) {
                var pair = _pairs.get(i);
                if (pair.getKey().equals(key)) {
                    // PORT-BUG: overwrites the NEW value with the OLD one, so an update never changes the stored value (D18)
                    value = pair.getValue();

                    // update and move to front
                    _pairs.remove(i);
                    _pairs.add(0, new AbstractMap.SimpleImmutableEntry<TKey, TValue>(key, value));
                    return;
                }
            }

            // did not find, so add
            _pairs.add(0, new AbstractMap.SimpleImmutableEntry<TKey, TValue>(key, value));

            while (_pairs.size() > _maxSize)
                _pairs.remove(_pairs.size() - 1);
        }
    }
}
