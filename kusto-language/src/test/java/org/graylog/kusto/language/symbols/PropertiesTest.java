// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for Properties (Properties.cs).
package org.graylog.kusto.language.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.graylog.kusto.language.Properties;
import org.junit.jupiter.api.Test;

class PropertiesTest {
    @Test
    void namesMatchTheMemberNames() {
        assertEquals("AllowClientParameters", Properties.AllowClientParameters.name());
        assertEquals("MaxParseTextSize", Properties.MaxParseTextSize.name());
        assertEquals("MaxAnalysisDepth", Properties.MaxAnalysisDepth.name());
        assertEquals("MaxCachedExpansions", Properties.MaxCachedExpansions.name());
        assertEquals("MaxCachedResultTypes", Properties.MaxCachedResultTypes.name());
    }

    @Test
    void defaultsMatchUpstream() {
        assertEquals(Boolean.FALSE, Properties.AllowClientParameters.defaultValue());
        assertEquals(4 * 1024 * 1024, Properties.MaxParseTextSize.defaultValue());
        assertEquals(500, Properties.MaxAnalysisDepth.defaultValue());
        assertEquals(10, Properties.MaxCachedExpansions.defaultValue());
        assertEquals(50, Properties.MaxCachedResultTypes.defaultValue());
    }
}
