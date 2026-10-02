// Ported from: src/Kusto.Language/Utils/EmptyReadOnlyList.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// PORT: §3.9 a static field of a generic class is one shared raw static; EmptyReadOnlyList<T> is not generic in Java.
// ListExtensions.toReadOnly compares against Instance by reference, which a single shared instance keeps working.
public final class EmptyReadOnlyList {
    private EmptyReadOnlyList() {
    }

    @SuppressWarnings("rawtypes")
    public static final List Instance = Collections.unmodifiableList(new ArrayList<Object>()); // PORT: §3.9 raw shared static

    // PORT: §3.9 typed access to the shared instance, replaces EmptyReadOnlyList<T>.Instance
    @SuppressWarnings("unchecked")
    public static <T> List<T> instance() {
        return (List<T>) Instance;
    }
}
