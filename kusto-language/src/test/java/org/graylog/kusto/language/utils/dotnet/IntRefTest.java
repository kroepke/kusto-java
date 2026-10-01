// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for IntRef.

package org.graylog.kusto.language.utils.dotnet;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IntRefTest {
    @Test
    void holdsValue() {
        IntRef r = new IntRef();
        assertEquals(0, r.value);
        r.value += 5;
        assertEquals(5, r.value);
        assertEquals(7, new IntRef(7).value);
    }
}
