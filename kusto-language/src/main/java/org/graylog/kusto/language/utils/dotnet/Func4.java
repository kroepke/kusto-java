// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: C# Func with 4 inputs (PORTING.md 3.8).

package org.graylog.kusto.language.utils.dotnet;

/** C# {@code Func<T1..T4,R>}. */
@FunctionalInterface
public interface Func4<T1, T2, T3, T4, R> {
    R apply(T1 t1, T2 t2, T3 t3, T4 t4);
}
