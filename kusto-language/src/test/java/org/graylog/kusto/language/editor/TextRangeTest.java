// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: smoke test for the ported TextRange record.
package org.graylog.kusto.language.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextRangeTest
{
    @Test
    void endAndBounds()
    {
        var r = new TextRange(3, 4);
        assertEquals(7, r.end());
        assertEquals(r, TextRange.fromBounds(3, 7));
    }

    @Test
    void overlaps()
    {
        assertTrue(new TextRange(0, 5).overlaps(new TextRange(5, 2)));
        assertFalse(new TextRange(0, 4).overlaps(new TextRange(5, 2)));
    }
}
