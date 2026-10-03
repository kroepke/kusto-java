// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: tests known-differences.json validation, the committed config files and adapter discovery.

package org.graylog.kusto.language.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class KnownDifferencesTest {
    static final String SHA = "9d95a2d5bb085d151f14e88e07b703755fd914e1";

    static KnownDifferences parse(String entry) throws Exception {
        return KnownDifferences.parse(Harness.MAPPER.readTree("{\"differences\":[" + entry + "]}"), "t");
    }

    @Test
    void committed_files_load() {
        // KD-001/KD-002 (D31): the one date-dependent sentinel record, on tokens and on bind
        assertEquals(2, KnownDifferences.load().entries().size());
        ConformanceBaseline b = ConformanceBaseline.load();
        assertTrue(b.gatedLayers().containsAll(Set.of(Layer.TOKENS, Layer.FIDELITY, Layer.TOKEN_VALUES)), "W2 gates the lexer layers");
        for (String c : Harness.KNOWN_CORPORA) {
            for (Layer l : Layer.values()) {
                assertTrue(b.expected().get(c).containsKey(l), c + " " + l);
                assertTrue(b.expected(c, l) >= 0, c + " " + l);
                if (b.gatedLayers().contains(l)) {
                    assertTrue(b.expected(c, l) > 0, "gated " + c + " " + l + " has a baseline");
                }
            }
        }
    }

    @Test
    void glob_matching() throws Exception {
        KnownDifferences k = parse("{\"id\":\"KD-1\",\"record\":\"docs/00?1\",\"dRow\":\"D1\",\"reason\":\"r\",\"expires\":\"" + SHA + "\"}");
        assertTrue(k.match("docs/0011", Layer.BIND, "bind[0].type").isPresent());
        assertFalse(k.match("docs/00011", Layer.BIND, "bind").isPresent());
        assertFalse(k.match("docsX0011", Layer.BIND, "bind").isPresent(), "glob quotes regex metacharacters");
        assertEquals("*", k.entries().get(0).layer());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"record\":\"x\",\"dRow\":\"D1\",\"reason\":\"r\",\"expires\":\"" + SHA + "\"}",
        "{\"id\":\"K\",\"record\":\"x\",\"dRow\":\"12\",\"reason\":\"r\",\"expires\":\"" + SHA + "\"}",
        "{\"id\":\"K\",\"record\":\"x\",\"dRow\":\"D1\",\"reason\":\"r\",\"expires\":\"abc\"}",
        "{\"id\":\"K\",\"record\":\"x\",\"dRow\":\"D1\",\"expires\":\"" + SHA + "\"}",
        "{\"id\":\"K\",\"record\":\"x\",\"layer\":\"NOPE\",\"dRow\":\"D1\",\"reason\":\"r\",\"expires\":\"" + SHA + "\"}",
        "{\"id\":\"K\",\"record\":\"^(\",\"dRow\":\"D1\",\"reason\":\"r\",\"expires\":\"" + SHA + "\"}",
        "{\"id\":\"K\",\"record\":\"x\",\"dRow\":\"D1\",\"reason\":\"r\",\"expires\":\"" + SHA + "\"},"
                + "{\"id\":\"K\",\"record\":\"y\",\"dRow\":\"D1\",\"reason\":\"r\",\"expires\":\"" + SHA + "\"}",
    })
    void invalid_entries_are_rejected(String entry) {
        assertThrows(IllegalStateException.class, () -> parse(entry));
    }

    @Test
    void adapter_discovery() {
        assertInstanceOf(GoldenWriter.class, PortAdapters.load(PortAdapters.DEFAULT));
        assertTrue(PortAdapters.available(PortAdapters.load(PortAdapters.DEFAULT)));
        assertFalse(PortAdapters.available(new EmptyPort()));
        assertNull(new EmptyPort().write(new CorpusRecord("x/1", "T", null, null, false), null));
        assertThrows(IllegalStateException.class, () -> PortAdapters.load("java.lang.String"));
        assertThrows(IllegalStateException.class, () -> PortAdapters.load("no.such.Adapter"));
    }

    @Test
    void kql_thread_has_large_stack_and_propagates() throws Exception {
        int depth = KqlThread.call(() -> recurse(0, 60_000), 30_000);
        assertEquals(60_000, depth);
        KqlThread.CallFailed f = assertThrows(KqlThread.CallFailed.class,
                () -> KqlThread.call(() -> { throw new IllegalStateException("boom"); }, 30_000));
        assertInstanceOf(IllegalStateException.class, f.getCause());
        assertThrows(java.util.concurrent.TimeoutException.class, () -> KqlThread.call(() -> {
            Thread.sleep(5_000);
            return null;
        }, 50));
    }

    private static int recurse(int d, int max) {
        return d == max ? d : recurse(d + 1, max);
    }
}
