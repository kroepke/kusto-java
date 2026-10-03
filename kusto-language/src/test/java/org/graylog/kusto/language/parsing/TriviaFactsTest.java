// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: smoke test for the ported TriviaFacts.
package org.graylog.kusto.language.parsing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.graylog.kusto.language.utils.dotnet.IntRef;
import org.junit.jupiter.api.Test;

class TriviaFactsTest
{
    @Test
    void findsCommentSpan()
    {
        var start = new IntRef();
        var length = new IntRef();
        assertTrue(TriviaFacts.tryGetCommentSpan("  // hi\n", 4, start, length));
        assertEquals(2, start.value);
        assertEquals(6, length.value);
    }

    @Test
    void noCommentInWhitespace()
    {
        assertFalse(TriviaFacts.tryGetCommentSpan("   ", 1, new IntRef(), new IntRef()));
    }
}
