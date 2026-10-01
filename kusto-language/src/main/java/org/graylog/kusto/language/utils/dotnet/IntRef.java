// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unboxed int holder for C# ref int (PORTING.md 3.3).

package org.graylog.kusto.language.utils.dotnet;

/** Mutable int holder standing in for a C# {@code ref int} or {@code out int}. */
public final class IntRef {
    public int value;

    public IntRef() {
    }

    public IntRef(int value) {
        this.value = value;
    }
}
