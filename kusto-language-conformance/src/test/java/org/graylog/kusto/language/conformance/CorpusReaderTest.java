// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: tests corpus and schema reading, lone-surrogate preservation and JSON escaping.

package org.graylog.kusto.language.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorpusReaderTest {
    @Test
    void lone_surrogates_survive_as_single_utf16_units() {
        CorpusRecord r = CorpusReader.parseLine(
                "{\"id\": \"traps/0001\", \"text\": \"a\\uD800b\\uDC00c\\uD83D\\uDE00\", \"schema\": null, \"source\": \"x\"}");
        String t = r.text();
        assertEquals(7, t.length());
        assertEquals('\uD800', t.charAt(1));
        assertEquals('\uDC00', t.charAt(3));
        assertEquals(0x1F600, t.codePointAt(5));
        assertNull(r.schema());
        assertEquals("traps", r.corpus());
        assertFalse(r.crlf());
    }

    @Test
    void quote_matches_oracle_escaping_and_round_trips() {
        String s = "a\"\\\n\u0001\u007F\u2028\uD800x\uDC00\uD83D\uDE00é";
        String q = JsonText.quote(s);
        assertEquals("\"a\\\"\\\\\\u000A\\u0001\u007F\u2028\\uD800x\\uDC00\uD83D\uDE00é\"", q);
        assertEquals(s, CorpusReader.parseLine("{\"id\": \"x/1\", \"text\": " + q + "}").text());
        assertEquals("a\\uD800b", JsonText.escapeLoneSurrogates("a\uD800b"));
        assertEquals("\uD83D\uDE00", JsonText.escapeLoneSurrogates("\uD83D\uDE00"));
    }

    @Test
    void crlf_flag_and_missing_fields() {
        CorpusRecord r = CorpusReader.parseLine("{\"id\": \"docs/1\", \"text\": \"a\\r\\nb\", \"schema\": \"s\", \"source\": \"f\", \"crlf\": true}");
        assertTrue(r.crlf());
        assertEquals("a\r\nb", r.text());
        assertThrows(IllegalStateException.class, () -> CorpusReader.parseLine("{\"id\": \"x\"}"));
        assertThrows(IllegalStateException.class, () -> CorpusReader.parseLine("not json"));
    }

    @Test
    void reads_committed_corpora() {
        List<CorpusRecord> readme = CorpusReader.read("readme");
        assertEquals(7, readme.size());
        assertEquals("readme/0001", readme.get(0).id());
        assertEquals("readme-v1", readme.get(0).schema());
        assertTrue(CorpusReader.read("docs").stream().anyMatch(CorpusRecord::crlf));
        assertTrue(Harness.corpusNames().containsAll(List.of("readme", "docs", "sentinel")));
        assertEquals("readme", Harness.corpusNames().get(0));
    }

    @Test
    void reads_schemas() {
        SchemaFile s = SchemaReader.get("readme-v1");
        assertEquals("db", s.database());
        assertEquals("T", s.tables().get(0).name());
        assertEquals("real", s.tables().get(0).columns().get(0).type());
        assertEquals("(maxHeight: real)", s.functions().get(1).parameters());
        assertTrue(s.notes().isTextual());
        assertTrue(SchemaReader.get("sentinel-v1").notes().isObject());
        assertTrue(SchemaReader.get("sentinel-v1").tables().size() > 10);
        assertNull(SchemaReader.get(null));
    }

    @Test
    void manifest_upstream_commit() {
        assertTrue(Harness.upstreamCommit().matches("[0-9a-f]{40}"));
    }
}
