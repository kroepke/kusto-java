// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: mutable holder for C# out/ref of reference types (PORTING.md 3.3).

package org.graylog.kusto.language.utils.dotnet;

/** Mutable holder standing in for a C# {@code out} or {@code ref} parameter. */
public final class Out<T> {
    public T value;

    public Out() {
    }

    public Out(T initial) {
        this.value = initial;
    }
}
