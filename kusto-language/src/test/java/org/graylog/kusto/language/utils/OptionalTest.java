// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for Optional, the null-carrying struct port (Utils/Optional.cs).
package org.graylog.kusto.language.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OptionalTest {
    @Test
    void noneHasNoValue() {
        Optional<String> none = Optional.NONE();
        assertFalse(none.hasValue());
        assertNull(none.value());
        assertSame(Optional.<String>NONE(), Optional.<Integer>NONE());
    }

    @Test
    void ofCarriesAValue() {
        var o = Optional.of("x");
        assertTrue(o.hasValue());
        assertEquals("x", o.value());
        assertTrue(new Optional<>(5).hasValue());
    }

    @Test
    void ofNullMeansSetToNull() {
        // GlobalState.cs:256-271 distinguishes "not specified" from "set to null"
        Optional<String> setToNull = Optional.of(null);
        assertTrue(setToNull.hasValue());
        assertNull(setToNull.value());
        assertFalse(Optional.<String>NONE().hasValue());
    }
}
