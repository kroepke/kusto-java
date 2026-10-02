// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: the harness's only view of the port, so it compiles before the port exists.

package org.graylog.kusto.language.conformance;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Adapter between the harness and the Java port. Implementations need a public no-argument
 * constructor; {@link PortAdapters#get()} loads them by name from {@code -Dkusto.port}.
 */
public interface PortAdapter {
    /** {@code false} when there is no port behind this adapter (the harness then reports 0%). */
    boolean available();

    /**
     * Parses and analyses {@code rec.text()} under {@code schema} ({@code null} means
     * {@code GlobalState.Default}) and renders the Java side of the golden record: same keys, same
     * order and same rendering rules as the oracle (golden-format.md, oracle/README.md);
     * {@code timing} is optional. Must not throw for a port exception: those go into
     * {@code outcome}. Called on a thread with a 16 MB stack.
     */
    ObjectNode write(CorpusRecord rec, SchemaFile schema);

    /** Start offsets of the lexical tokens of {@code text} (fuzz mutator), or {@code null}. */
    int[] tokenStarts(String text);
}
