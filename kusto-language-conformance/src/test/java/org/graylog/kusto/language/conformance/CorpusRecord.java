// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: one corpus line (golden-format.md "Files").

package org.graylog.kusto.language.conformance;

import java.util.Objects;

/**
 * One line of {@code corpus/<name>.jsonl}.
 *
 * @param id     {@code <corpus>/<nnnn>}
 * @param text   the KQL text; may contain lone surrogates
 * @param schema schema id ({@code schemas/<id>.json}) or {@code null} for {@code GlobalState.Default}
 * @param source provenance
 * @param crlf   {@code true} when the text keeps CRLF line ends (informational)
 */
public record CorpusRecord(String id, String text, String schema, String source, boolean crlf) {
    public CorpusRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
    }

    /** The corpus name, the part of {@link #id} before the first {@code /}. */
    public String corpus() {
        int slash = id.indexOf('/');
        return slash < 0 ? id : id.substring(0, slash);
    }
}
