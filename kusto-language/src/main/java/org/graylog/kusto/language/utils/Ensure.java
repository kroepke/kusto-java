// Ported from: src/Kusto.Language/Utils/Ensure.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.util.List;
import java.util.Objects;

import org.graylog.kusto.language.utils.dotnet.DotNet;

public final class Ensure {
    private Ensure() {
    }

    // PORT: §3.20 [Conditional("DEBUG")] becomes a runtime flag, default off to match the Release oracle (D29)
    public static final boolean ENABLED = Boolean.getBoolean("kusto.ensure");

    // PORT: §3.20 [Conditional("DEBUG")]
    public static void isTrue(boolean value, String message) {
        if (!ENABLED) return; // PORT: §3.20
        if (!value) {
            throw new IllegalStateException(message != null ? message : "Expected true"); // PORT: §3.16 InvalidOperationException
        }
    }

    public static void isTrue(boolean value) { // PORT: §3.12 optional parameter message = null
        isTrue(value, null);
    }

    // PORT: §3.20 [Conditional("DEBUG")]
    public static <T> void areEqual(T expected, T actual, String message) {
        if (!ENABLED) return; // PORT: §3.20
        if (!Objects.equals(expected, actual)) { // PORT: §3.14 object.Equals
            throw new IllegalStateException(message != null ? message : "Expected: " + DotNet.str(expected) + " actual: " + DotNet.str(actual)); // PORT: §3.16 InvalidOperationException
        }
    }

    public static <T> void areEqual(T expected, T actual) { // PORT: §3.12 optional parameter message = null
        areEqual(expected, actual, null);
    }

    // PORT: §3.20 [Conditional("DEBUG")]
    public static void notNull(Object value, String message) {
        if (!ENABLED) return; // PORT: §3.20
        if (value == null) {
            throw new IllegalStateException(message != null ? message : "Expected not null"); // PORT: §3.16 InvalidOperationException
        }
    }

    public static void notNull(Object value) { // PORT: §3.12 optional parameter message = null
        notNull(value, null);
    }

    // PORT: §3.20 [Conditional("DEBUG")]
    public static void isNull(Object value, String message) {
        if (!ENABLED) return; // PORT: §3.20
        if (value != null) {
            throw new IllegalStateException(message != null ? message : "Expected null"); // PORT: §3.16 InvalidOperationException
        }
    }

    public static void isNull(Object value) { // PORT: §3.12 optional parameter message = null
        isNull(value, null);
    }

    // PORT: §3.20 [Conditional("DEBUG")]
    public static void argumentNotNull(Object value, String paramName) {
        if (!ENABLED) return; // PORT: §3.20
        if (value == null) {
            throw new NullPointerException(paramName); // PORT: §3.16 ArgumentNullException
        }
    }

    // PORT: §3.20 [Conditional("DEBUG")]
    public static <T> void elementsNotNull(List<T> list, String listName) {
        if (!ENABLED) return; // PORT: §3.20
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == null) {
                throw new IllegalStateException(DotNet.str(listName) + "[" + i + "] is null"); // PORT: §3.16 InvalidOperationException
            }
        }
    }
}
