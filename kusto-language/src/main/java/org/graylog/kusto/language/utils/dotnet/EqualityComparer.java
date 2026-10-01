// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: mirror of .NET IEqualityComparer<T> and map-key wrapper (PORTING.md 3.10).

package org.graylog.kusto.language.utils.dotnet;

import java.util.Objects;

/** Mirror of {@code IEqualityComparer<T>}, with a {@link Key} wrapper for use as map keys. */
public interface EqualityComparer<T> {
    boolean equals(T a, T b);

    int hashCode(T a);

    EqualityComparer<Object> DEFAULT = new EqualityComparer<Object>() {
        @Override
        public boolean equals(Object a, Object b) {
            return Objects.equals(a, b);
        }

        @Override
        public int hashCode(Object a) {
            return Objects.hashCode(a);
        }
    };

    /** Comparer using {@code Objects.equals} / {@code Objects.hashCode}. */
    @SuppressWarnings("unchecked")
    static <T> EqualityComparer<T> defaultComparer() {
        return (EqualityComparer<T>) DEFAULT;
    }

    /** Ordinal (UTF-16 code unit) string equality. */
    EqualityComparer<String> ORDINAL = new EqualityComparer<String>() {
        @Override
        public boolean equals(String a, String b) {
            return Objects.equals(a, b);
        }

        @Override
        public int hashCode(String a) {
            return a == null ? 0 : a.hashCode();
        }
    };

    /**
     * Mirrors .NET {@code OrdinalIgnoreCase}: compares per UTF-16 char after
     * {@code Character.toUpperCase(char)}. Never lower-cases.
     */
    EqualityComparer<String> ORDINAL_IGNORE_CASE = new EqualityComparer<String>() {
        @Override
        public boolean equals(String a, String b) {
            if (a == b) {
                return true;
            }
            if (a == null || b == null || a.length() != b.length()) {
                return false;
            }
            for (int i = 0; i < a.length(); i++) {
                char x = a.charAt(i);
                char y = b.charAt(i);
                if (x != y && Character.toUpperCase(x) != Character.toUpperCase(y)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode(String a) {
            if (a == null) {
                return 0;
            }
            int h = 0;
            for (int i = 0; i < a.length(); i++) {
                h = 31 * h + Character.toUpperCase(a.charAt(i));
            }
            return h;
        }
    };

    static <T> Key<T> of(T value, EqualityComparer<T> comparer) {
        return new Key<>(value, comparer);
    }

    /** Wraps a value so that {@code equals}/{@code hashCode} delegate to a comparer. */
    final class Key<T> {
        private final T value;
        private final EqualityComparer<T> comparer;

        public Key(T value, EqualityComparer<T> comparer) {
            this.value = value;
            this.comparer = Objects.requireNonNull(comparer, "comparer");
        }

        public T value() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }
            @SuppressWarnings("unchecked")
            Key<T> other = (Key<T>) o;
            return comparer.equals(value, other.value);
        }

        @Override
        public int hashCode() {
            return comparer.hashCode(value);
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }
}
