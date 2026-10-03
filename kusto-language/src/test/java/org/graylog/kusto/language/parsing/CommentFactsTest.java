// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: smoke test for the ported CommentFacts.
package org.graylog.kusto.language.parsing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CommentFactsTest
{
    @Test
    void findsCommentLines()
    {
        assertTrue(CommentFacts.isCommentLine("  // hello"));
        assertFalse(CommentFacts.isCommentLine("T | take 1 // trailing"));
        assertEquals("hello", CommentFacts.getCommentLineText("  //  hello  "));
    }

    @Test
    void getCommentTexts()
    {
        assertEquals(List.of("a", "b"), CommentFacts.getCommentTexts("// a\nT\n  // b\n"));
    }

    @Test
    void trimsCommentAndWhitespaceLines()
    {
        assertEquals("T | take 1", CommentFacts.trimCommentAndWhitespaceLines("// c\n\nT | take 1\n\n// d"));
    }
}
