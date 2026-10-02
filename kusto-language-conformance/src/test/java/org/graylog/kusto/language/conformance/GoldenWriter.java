// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: Java side of golden-format.md; reserved name, implemented in W2.

package org.graylog.kusto.language.conformance;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Renders the port's output as golden records ({@code -Dkusto.port=org.graylog.kusto.language.conformance.GoldenWriter}).
 * Normative: golden-format.md plus oracle/README.md "Rendering decisions".
 */
// PORT-PENDING: W2
public final class GoldenWriter implements PortAdapter {
    @Override
    public boolean available() {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    @Override
    public ObjectNode write(CorpusRecord rec, SchemaFile schema) {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }

    @Override
    public int[] tokenStarts(String text) {
        throw new UnsupportedOperationException("PORT-PENDING: W2");
    }
}
