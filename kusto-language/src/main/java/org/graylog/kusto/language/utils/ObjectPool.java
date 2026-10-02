// Ported from: src/Kusto.Language/Utils/ObjectPool.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graylog.kusto.language.utils.dotnet.Internal;

@Internal
public class ObjectPool<T> { // PORT: §3.10 where T : class has no Java counterpart
    private final Supplier<T> creator; // PORT: §3.8 Func<T>
    private final Consumer<T> resetter; // PORT: §3.8 Action<T>
    private final AtomicReferenceArray<T> items; // PORT: §3.13 T[] with Interlocked on elements

    public ObjectPool(Supplier<T> creator, Consumer<T> resetter, int size) {
        this.creator = creator;
        this.resetter = resetter;
        this.items = new AtomicReferenceArray<T>(size);
    }

    public ObjectPool(Supplier<T> creator, Consumer<T> resetter) { // PORT: §3.12 optional parameter size = 10
        this(creator, resetter, 10);
    }

    public T allocateFromPool() {
        // look for item returned to pool
        for (int i = 0; i < this.items.length(); i++) {
            if (this.items.get(i) != null) {
                var item = Interlocked.exchange(this.items, i, null); // PORT: §3.13
                if (item != null) {
                    return item;
                }
            }
        }

        // make a new one
        return this.creator.get();
    }

    public void returnToPool(T item) {
        // clear item
        this.resetter.accept(item);

        // look for open space to place in pool
        // if no space is found, let GC have it.
        for (int i = 0; i < this.items.length(); i++) {
            if (this.items.get(i) == null) {
                var result = Interlocked.compareExchange(this.items, i, item, null); // PORT: §3.13
                if (result == null) {
                    break;
                }
            }
        }
    }
}
