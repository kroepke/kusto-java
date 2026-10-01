// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for Out.

package org.graylog.kusto.language.utils.dotnet;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OutTest {
    @Test
    void holdsValue() {
        Out<String> o = new Out<>();
        assertNull(o.value);
        o.value = "x";
        assertEquals("x", o.value);
        assertEquals("y", new Out<>("y").value);
    }
}
