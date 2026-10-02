// Ported from: src/Kusto.Language/Utils/ListExtensions.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.util.List;
import java.util.Objects;

import org.graylog.kusto.language.utils.dotnet.EqualityComparer;
import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// Compares <see cref="IReadOnlyList{T}"/> instances for structural equality (all elements are equal in order)
/// </summary>
/// <remarks>Assumes list instances are actually immutable.</remarks>
// PORT: §2.2 second top-level type of ListExtensions.cs; IEqualityComparer<IReadOnlyList<T>> -> EqualityComparer<List<T>> (§3.10, §3.17)
@Internal
public class ReadOnlyListComparer<T> implements EqualityComparer<List<T>> {
    /// <summary>
    /// A <see cref="ReadOnlyListComparer{T}"/> that compares elements using their default comparer.
    /// </summary>
    // PORT: §3.9 static field of a generic class: one shared raw static, typed access through defaultComparer()
    @SuppressWarnings("rawtypes")
    public static final ReadOnlyListComparer Default = new ReadOnlyListComparer<Object>(EqualityComparer.DEFAULT);

    // PORT: §3.9 typed access to the shared Default instance
    @SuppressWarnings("unchecked")
    public static <T> ReadOnlyListComparer<T> defaultComparer() {
        return (ReadOnlyListComparer<T>) Default;
    }

    private final EqualityComparer<T> comparer;

    public ReadOnlyListComparer(EqualityComparer<T> comparer) {
        this.comparer = Objects.requireNonNull(comparer, "comparer"); // PORT: §3.16 ArgumentNullException
    }

    @Override
    public boolean equals(List<T> x, List<T> y) {
        if (x == y)
            return true;

        if (x == null || y == null)
            return false;

        if (x.size() != y.size())
            return false;

        for (int i = 0; i < x.size(); i++) {
            if (!comparer.equals(x.get(i), y.get(i)))
                return false;
        }

        return true;
    }

    @Override
    public int hashCode(List<T> list) {
        if (list == null)
            return 0;
        if (list.size() == 1)
            return list.get(0).hashCode();

        int hc = 0;
        for (var table : list) {
            hc = hc + table.hashCode(); // unchecked int addition wraps as in C#
        }

        return hc;
    }
}
