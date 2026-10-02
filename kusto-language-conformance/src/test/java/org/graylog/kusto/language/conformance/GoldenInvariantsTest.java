// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: checks the oracle goldens satisfy the same invariants (T0b round-trip gate).

package org.graylog.kusto.language.conformance;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Applies {@link Invariants#violations} to the oracle's records (timing ignored), so the invariant
 * definitions are validated against .NET and the T0b gate "{@code fidelity.roundTrip} true for 100%
 * of records" is enforced. Aborted when no golden exists.
 */
@Tag("conformance")
class GoldenInvariantsTest {
    @Test
    void goldensSatisfyInvariants() {
        String upstream = Harness.upstreamCommit();
        List<String> violations = new ArrayList<>();
        int corpora = 0;
        for (String corpus : Harness.corpusNames()) {
            if (!GoldenReader.exists(corpus)) {
                continue;
            }
            corpora++;
            Map<String, String> texts = new java.util.HashMap<>();
            for (CorpusRecord r : CorpusReader.read(corpus)) {
                texts.put(r.id(), r.text());
            }
            GoldenReader.stream(GoldenReader.path(corpus), upstream, g -> {
                String id = g.get("id").textValue();
                String text = texts.get(id);
                if (text == null) {
                    violations.add(id + ": not in corpus (stale golden)");
                    return;
                }
                for (String v : Invariants.violations(text, g, 0)) {
                    violations.add(id + ": " + v);
                }
            });
        }
        if (corpora == 0) {
            Assumptions.abort("no goldens in " + Harness.goldensDir());
        }
        if (!violations.isEmpty()) {
            fail(violations.size() + " golden records violate the invariants:\n  "
                    + String.join("\n  ", violations.subList(0, Math.min(50, violations.size()))));
        }
    }
}
