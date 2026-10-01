// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: mirror of IReadOnlyList<T> (PORTING.md 3.10).

package org.graylog.kusto.language.utils.dotnet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Mirror of {@code IReadOnlyList<T>}. Implementers keep identity {@code equals}/{@code hashCode}
 * (PORTING.md D26); do not override them with element-wise versions.
 */
public interface ReadOnlyList<T> extends Iterable<T> {
    int size();

    T get(int index);

    @Override
    Iterator<T> iterator();

    default boolean isEmpty() {
        return size() == 0;
    }

    /** Copies the elements into an unmodifiable list. */
    default List<T> toList() {
        int n = size();
        ArrayList<T> copy = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            copy.add(get(i));
        }
        return Collections.unmodifiableList(copy);
    }
}
