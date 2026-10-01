// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for ReadOnlyList.

package org.graylog.kusto.language.utils.dotnet;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import java.util.Iterator;
import java.util.List;

class ReadOnlyListTest {
    private static final class Three implements ReadOnlyList<String> {
        @Override
        public int size() {
            return 3;
        }

        @Override
        public String get(int index) {
            return "e" + index;
        }

        @Override
        public Iterator<String> iterator() {
            return toList().iterator();
        }
    }

    @Test
    void toListIsUnmodifiableCopy() {
        Three t = new Three();
        List<String> l = t.toList();
        assertEquals(List.of("e0", "e1", "e2"), l);
        assertThrows(UnsupportedOperationException.class, () -> l.add("x"));
        assertFalse(t.isEmpty());
    }

    @Test
    void identityEquals() {
        assertNotEquals(new Three(), new Three());
        Three t = new Three();
        assertEquals(t, t);
    }
}
