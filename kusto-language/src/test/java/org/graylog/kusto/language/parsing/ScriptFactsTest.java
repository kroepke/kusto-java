// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: smoke test for the ported ScriptFacts.
package org.graylog.kusto.language.parsing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class ScriptFactsTest
{
    @Test
    void splitsAtBlankLines()
    {
        var text = "T1\n\nT2\n";
        var starts = ScriptFacts.getKustoBlockStarts(text, TextFacts.getLineStarts(text));
        assertArrayEquals(new int[] { 0, 4 }, starts.toArray());
    }

    @Test
    void blankLineInsideStringDoesNotSplit()
    {
        var text = "print ```a\n\nb```";
        var starts = ScriptFacts.getKustoBlockStarts(text, TextFacts.getLineStarts(text));
        assertArrayEquals(new int[] { 0 }, starts.toArray());
    }
}
