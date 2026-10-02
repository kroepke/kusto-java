// Ported from: src/Kusto.Language/Utils/Optional.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import org.graylog.kusto.language.utils.dotnet.Internal;

// PORT: §3.2 struct -> final class; it must hold null (Optional(null) means "set to null"), so never java.util.Optional
@Internal
public final class Optional<T> {
    // PORT: §3.2 default(Optional<T>) (HasValue == false) is NONE()
    private static final Optional<Object> NONE = new Optional<Object>();

    private final T value;

    private final boolean hasValue;

    public T value() {
        return value;
    }

    public boolean hasValue() {
        return hasValue;
    }

    public Optional(T value) {
        this.value = value;
        this.hasValue = true;
    }

    private Optional() { // PORT: §3.2 default(Optional<T>)
        this.value = null;
        this.hasValue = false;
    }

    // PORT: §3.2 default(Optional<T>)
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> NONE() {
        return (Optional<T>) NONE;
    }

    // PORT: §3.8 replaces the implicit conversion operator (Optional.cs:17)
    public static <T> Optional<T> of(T value) {
        return new Optional<T>(value);
    }
}
