// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: reflective discovery of the port adapter named by -Dkusto.port.

package org.graylog.kusto.language.conformance;

/** Loads the adapter class named by {@code kusto.port} (default {@link GoldenWriter}). */
public final class PortAdapters {
    public static final String DEFAULT = "org.graylog.kusto.language.conformance.GoldenWriter";

    private static volatile PortAdapter cached;

    private PortAdapters() {
    }

    public static PortAdapter get() {
        PortAdapter a = cached;
        if (a == null) {
            String name = Harness.property("kusto.port");
            a = load(name == null ? DEFAULT : name);
            cached = a;
        }
        return a;
    }

    public static PortAdapter load(String className) {
        try {
            Class<?> c = Class.forName(className);
            if (!PortAdapter.class.isAssignableFrom(c)) {
                throw new IllegalStateException("kusto.port class " + className + " does not implement PortAdapter");
            }
            return (PortAdapter) c.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot instantiate kusto.port class " + className + ": " + e, e);
        }
    }

    /** {@code a.available()}; {@code false} for an adapter with no port behind it (e.g. {@link EmptyPort}). */
    public static boolean available(PortAdapter a) {
        return a.available();
    }
}
