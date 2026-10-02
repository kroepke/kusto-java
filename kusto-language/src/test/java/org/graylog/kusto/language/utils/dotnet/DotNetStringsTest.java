// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for DotNetStrings and DotNetChars beyond dotnet-facts.json.

package org.graylog.kusto.language.utils.dotnet;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DotNetStringsTest {
    @Test
    void splitKeepsEmpties() {
        assertEquals(List.of("", "a", "", ""), DotNetStrings.split("|a||", '|'));
        assertEquals(List.of("a"), DotNetStrings.split("|a||", '|', true));
        assertEquals(List.of(), DotNetStrings.split("", ',', true));
    }

    @Test
    void joinAndConcatRenderNullAsEmpty() {
        assertEquals("a,,True,1.5", DotNetStrings.join(",", Arrays.asList("a", null, true, 1.5)));
        assertEquals("a--b", DotNetStrings.join("-", "a", null, "b"));
        assertEquals("ab", DotNetStrings.join(null, "a", "b"));
        assertEquals("xFalse1E+21", DotNetStrings.concat("x", null, false, 1e21));
    }

    @Test
    void compareNullsAndRegions() {
        assertEquals(0, DotNetStrings.compare(null, null));
        assertTrue(DotNetStrings.compare(null, "") < 0);
        assertTrue(DotNetStrings.compare("", null) > 0);
        assertTrue(DotNetStrings.compare(null, 0, "a", 0, 1) < 0);
        assertEquals(0, DotNetStrings.compare("xnull", 1, "null", 0, 10));
        assertTrue(DotNetStrings.compare("ab", 0, "abc", 0, 3) < 0);
        assertEquals(0, DotNetStrings.compare("abc", 3, "", 0, 5));
        assertEquals(0, DotNetStrings.compare("A", "a", true));
        assertTrue(DotNetStrings.compare("a", "B", true) < 0);
        assertThrows(IndexOutOfBoundsException.class, () -> DotNetStrings.compare("abc", 0, "abc", -1, 1));
    }

    @Test
    void ordinalIgnoreCaseUpperCasesOnly() {
        assertFalse(DotNetStrings.equalsOrdinalIgnoreCase("ı", "I"));
        assertFalse(DotNetStrings.equalsOrdinalIgnoreCase("ſ", "S"));
        assertTrue(DotNetStrings.equalsOrdinalIgnoreCase("é", "É"));
        assertTrue(DotNetStrings.equalsOrdinalIgnoreCase(null, null));
        assertFalse(DotNetStrings.equalsOrdinalIgnoreCase("a", null));
        assertTrue(DotNetStrings.compareOrdinalIgnoreCase("_", "a") > 0);
    }

    @Test
    void casingKeepsLength() {
        assertEquals("ßıſ", DotNetStrings.toUpperInvariant("ßıſ"));
        assertEquals("İ", DotNetStrings.toLowerInvariant("İ"));
        assertEquals("ABC\ud801", DotNetStrings.toUpperInvariant("abc\ud801"));
        String s = "already";
        assertSame(s, DotNetStrings.toLowerInvariant(s));
    }

    @Test
    void trimAndWhiteSpace() {
        String s = "x";
        assertSame(s, DotNetStrings.trim(s));
        assertEquals("", DotNetStrings.trim(" 　 "));
        assertTrue(DotNetStrings.isNullOrWhiteSpace(null));
        assertTrue(DotNetStrings.isNullOrEmpty(null));
        assertTrue(DotNetStrings.isNullOrEmpty(""));
        assertFalse(DotNetStrings.isNullOrEmpty(" "));
        assertFalse(DotNetChars.isWhiteSpace('​'));
        assertFalse(DotNetChars.isWhiteSpace('﻿'));
    }

    @Test
    void convertFromUtf32() {
        assertEquals("A", DotNetChars.convertFromUtf32(0x41));
        assertEquals("😀", DotNetChars.convertFromUtf32(0x1F600));
        assertEquals("￿", DotNetChars.convertFromUtf32(0xFFFF));
        assertThrows(IndexOutOfBoundsException.class, () -> DotNetChars.convertFromUtf32(0xD800));
        assertThrows(IndexOutOfBoundsException.class, () -> DotNetChars.convertFromUtf32(0xDFFF));
        assertThrows(IndexOutOfBoundsException.class, () -> DotNetChars.convertFromUtf32(0x110000));
        assertThrows(IndexOutOfBoundsException.class, () -> DotNetChars.convertFromUtf32(-1));
    }
}
