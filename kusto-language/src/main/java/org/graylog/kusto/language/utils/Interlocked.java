// Ported from: src/Kusto.Language/Utils/Interlocked.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicReferenceArray;

/// <summary>
/// Helper functions for interlocked/atomic operations.
/// </summary>
// PORT: §3.13 C# `ref` arguments become a VarHandle (plus owner for instance fields) built with handle(...)/staticHandle(...).
// PORT: D15 Interlocked.CacheLineSeparated and its padding structs (Interlocked.cs:70-114) are dropped, they have no users.
public final class Interlocked {
    private Interlocked() {
    }

    // region class Interlocked

    // PORT: §3.13 builds the VarHandle that stands in for `ref this.field`
    public static VarHandle handle(Class<?> owner, String field, Class<?> type) {
        try {
            return MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).findVarHandle(owner, field, type);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // PORT: §3.13 builds the VarHandle that stands in for `ref staticField`
    public static VarHandle staticHandle(Class<?> owner, String field, Class<?> type) {
        try {
            return MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).findStaticVarHandle(owner, field, type);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /// <summary>
    /// Compares two values, and if they are equal replaces with the new value. 
    /// Returns the original value.
    /// </summary>
    // PORT: §3.13 instance field: ref value -> (VarHandle, owner); reference comparison
    @SuppressWarnings("unchecked")
    public static <T> T compareExchange(VarHandle value, Object owner, T newValue, T comparand) {
        return (T) (Object) value.compareAndExchange(owner, (Object) comparand, (Object) newValue);
    }

    /// <summary>
    /// Compares two values, and if they are equal replaces with the new value. 
    /// Returns the original value.
    /// </summary>
    // PORT: §3.13 static field: ref value -> VarHandle; reference comparison
    @SuppressWarnings("unchecked")
    public static <T> T compareExchange(VarHandle value, T newValue, T comparand) {
        return (T) (Object) value.compareAndExchange((Object) comparand, (Object) newValue);
    }

    // PORT: §3.13 array element: ref items[i] -> (AtomicReferenceArray, index); reference comparison
    public static <T> T compareExchange(AtomicReferenceArray<T> value, int index, T newValue, T comparand) {
        return value.compareAndExchange(index, comparand, newValue);
    }

    /// <summary>
    /// Compares two values, and if they are equal replaces with the new value. 
    /// Returns the original value.
    /// </summary>
    // PORT: §3.13 int instance field: ref int value -> (VarHandle, owner)
    public static int compareExchange(VarHandle value, Object owner, int newValue, int comparand) {
        return (int) value.compareAndExchange(owner, comparand, newValue);
    }

    // PORT: §3.13 int static field: ref int value -> VarHandle
    public static int compareExchange(VarHandle value, int newValue, int comparand) {
        return (int) value.compareAndExchange(comparand, newValue);
    }

    // PORT: §3.13 instance field: ref value -> (VarHandle, owner)
    @SuppressWarnings("unchecked")
    public static <T> T exchange(VarHandle value, Object owner, T newValue) {
        return (T) (Object) value.getAndSet(owner, (Object) newValue);
    }

    // PORT: §3.13 static field: ref value -> VarHandle
    @SuppressWarnings("unchecked")
    public static <T> T exchange(VarHandle value, T newValue) {
        return (T) (Object) value.getAndSet((Object) newValue);
    }

    // PORT: §3.13 array element: ref items[i] -> (AtomicReferenceArray, index), used by ObjectPool
    public static <T> T exchange(AtomicReferenceArray<T> value, int index, T newValue) {
        return value.getAndSet(index, newValue);
    }
    // endregion
}
