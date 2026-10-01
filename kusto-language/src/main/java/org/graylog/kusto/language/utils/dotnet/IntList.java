// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: growable primitive int list mirroring List<int> (PORTING.md 3.17).

package org.graylog.kusto.language.utils.dotnet;

import java.util.Arrays;

/** Growable {@code int} list mirroring the {@code List<int>} operations the port needs. */
public final class IntList {
    private int[] items;
    private int size;

    public IntList() {
        items = new int[8];
    }

    public IntList(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity");
        }
        items = new int[Math.max(capacity, 4)];
    }

    private void ensure(int needed) {
        if (needed > items.length) {
            long n = items.length;
            while (n < needed) {
                n *= 2;
            }
            items = Arrays.copyOf(items, (int) Math.min(n, Integer.MAX_VALUE - 8));
        }
    }

    private void check(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index " + index + ", size " + size);
        }
    }

    public void add(int value) {
        ensure(size + 1);
        items[size++] = value;
    }

    public int get(int index) {
        check(index);
        return items[index];
    }

    public void set(int index, int value) {
        check(index);
        items[index] = value;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        size = 0;
    }

    /** C# {@code RemoveRange(start, count)}: removes elements [start, start + count). */
    public void removeRange(int start, int count) {
        if (start < 0 || count < 0 || start > size - count) {
            throw new IndexOutOfBoundsException("start " + start + ", count " + count + ", size " + size);
        }
        if (count > 0) {
            System.arraycopy(items, start + count, items, start, size - start - count);
            size -= count;
        }
    }

    /** C# {@code Insert(index, value)}; index may equal size. */
    public void insert(int index, int value) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("index " + index + ", size " + size);
        }
        ensure(size + 1);
        System.arraycopy(items, index, items, index + 1, size - index);
        items[index] = value;
        size++;
    }

    public void addRange(IntList other) {
        int n = other.size;
        ensure(size + n);
        System.arraycopy(other.items, 0, items, size, n);
        size += n;
    }

    public int[] toArray() {
        return Arrays.copyOf(items, size);
    }

    /** .NET {@code List<int>.BinarySearch}: index if found, else {@code ~insertionPoint}. */
    public int binarySearch(int value) {
        int lo = 0;
        int hi = size - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int v = items[mid];
            if (v == value) {
                return mid;
            }
            if (v < value) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ~lo;
    }

    public int indexOf(int value) {
        for (int i = 0; i < size; i++) {
            if (items[i] == value) {
                return i;
            }
        }
        return -1;
    }

    public int lastValue() {
        if (size == 0) {
            throw new IndexOutOfBoundsException("empty");
        }
        return items[size - 1];
    }

    public int removeLast() {
        if (size == 0) {
            throw new IndexOutOfBoundsException("empty");
        }
        return items[--size];
    }
}
