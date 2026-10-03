// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: smoke test for the ported CommandFacts.
package org.graylog.kusto.language.parsing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CommandFactsTest
{
    @Test
    void commandStartLine()
    {
        assertTrue(CommandFacts.isCommandStartLine("  .show tables", 0));
        assertFalse(CommandFacts.isCommandStartLine("T | take 1", 0));
    }

    @Test
    void commandTexts()
    {
        var text = ".show tables\n// c\n.show databases\n";
        assertEquals(List.of(".show tables", ".show databases"), CommandFacts.getCommandTexts(text));
        assertEquals(2, CommandFacts.getCommandBlockStarts(text).size());
    }
}
