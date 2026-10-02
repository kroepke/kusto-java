// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: the default adapter while no port exists; reports nothing.

package org.graylog.kusto.language.conformance;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** No port: {@link #available()} is false and every layer reports cause {@code no-port}. */
public final class EmptyPort implements PortAdapter {
    @Override
    public boolean available() {
        return false;
    }

    @Override
    public ObjectNode write(CorpusRecord rec, SchemaFile schema) {
        return null;
    }

    @Override
    public int[] tokenStarts(String text) {
        return null;
    }
}
