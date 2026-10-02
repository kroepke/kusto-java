// Ported from: src/Kusto.Language/Utils/TextKeyedDictionary.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.util.function.Function;

import org.graylog.kusto.language.utils.dotnet.Internal;

// PORT: §2.2 second top-level type of TextKeyedDictionary.cs
@Internal
public final class TextKeyedDictionaryExtensions {
    private TextKeyedDictionaryExtensions() {
    }

    public static <TSource, TValue> TextKeyedDictionary<TValue> toTextKeyedDictionary(
        Iterable<TSource> source,
        Function<TSource, String> keySelector,
        Function<TSource, TValue> valueSelector) {
        var tkd = new TextKeyedDictionary<TValue>();

        for (var item : source) {
            tkd.getOrAddValue(keySelector.apply(item), valueSelector.apply(item));
        }

        return tkd;
    }
}
