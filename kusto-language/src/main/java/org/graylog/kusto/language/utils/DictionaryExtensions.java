// Ported from: src/Kusto.Language/Utils/DictionaryExtensions.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.util.Map;
import java.util.function.Function;
import java.util.function.BiFunction;

import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.Func3;

public final class DictionaryExtensions {
    private DictionaryExtensions() {
    }

    public static <TKey, TValue> TValue getOrAdd(Map<TKey, TValue> dictionary, TKey key, TValue value) {
        // PORT: §3.3 TryGetValue; containsKey keeps null values exact
        if (dictionary.containsKey(key))
            return dictionary.get(key);

        DotNet.dictionaryAdd(dictionary, key, value); // PORT: §3.17 Dictionary.Add
        return value;
    }

    public static <TKey, TValue> TValue getOrAdd(Map<TKey, TValue> dictionary, TKey key, Function<TKey, TValue> valueFactory) {
        // PORT: §3.3 TryGetValue; containsKey keeps null values exact
        if (dictionary.containsKey(key))
            return dictionary.get(key);

        var value = valueFactory.apply(key);
        DotNet.dictionaryAdd(dictionary, key, value); // PORT: §3.17 Dictionary.Add
        return value;
    }

    public static <TKey, TValue> void addOrUpdate(Map<TKey, TValue> dictionary, TKey key, TValue value) {
        dictionary.put(key, value); // PORT: §3.17 indexer set
    }

    public static <TKey, TValue> void addOrUpdate(Map<TKey, TValue> dictionary, TKey key, Function<TKey, TValue> fnAddFactory, BiFunction<TKey, TValue, TValue> fnUpdateFactory) {
        if (dictionary.containsKey(key)) { // PORT: §3.3 TryGetValue
            var oldValue = dictionary.get(key);
            dictionary.put(key, fnUpdateFactory.apply(key, oldValue));
        } else {
            dictionary.put(key, fnAddFactory.apply(key));
        }
    }

    public static <TKey, TValue> void addOrUpdate(Map<TKey, TValue> dictionary, TKey key, BiFunction<TKey, TValue, TValue> fnAddFactory, Func3<TKey, TValue, TValue, TValue> fnUpdateFactory, TValue value) {
        if (dictionary.containsKey(key)) { // PORT: §3.3 TryGetValue
            var oldValue = dictionary.get(key);
            var newValue = fnUpdateFactory.apply(key, oldValue, value);
            dictionary.put(key, newValue);
        } else {
            var addValue = fnAddFactory.apply(key, value);
            DotNet.dictionaryAdd(dictionary, key, addValue); // PORT: §3.17 Dictionary.Add
        }
    }
}
