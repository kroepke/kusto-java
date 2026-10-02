// Ported from: src/Kusto.Language/Utils/SafeList.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.lang.invoke.VarHandle;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.RandomAccess;

import org.graylog.kusto.language.utils.dotnet.Internal;
import org.graylog.kusto.language.utils.dotnet.ReadOnlyList;

/// <summary>
/// An immutable list over a normal mutable list that only supports adding new items.
/// Only one <see cref="SafeList{T}"/> instance owns the underlying list and can add to it. 
/// When a new <see cref="SafeList{T}"/> is constructed by adding items to the owner, it becomes the new owner.
/// When a new <see cref="SafeList{T}"/> is constructed by adding items to a non-owner, it copies the items it is allowed to see into a new list, making it the owner of the new list.
/// </summary>
// PORT: §3.10 implements ReadOnlyList<T> (IReadOnlyList<T>), not java.util.List, so equals/hashCode stay identity (D26)
public class SafeList<T> implements ReadOnlyList<T> {
    // PORT: §3.13 stands in for `ref _isOwner`; declared first so it is initialised before Empty
    private static final VarHandle IS_OWNER = Interlocked.handle(SafeList.class, "_isOwner", int.class);

    private final List<T> _list;
    private final int _length;
    private volatile int _isOwner; // PORT: §3.13 CAS-published field is volatile

    private SafeList(List<T> list, boolean isOwner) {
        _list = list;
        _length = list.size();
        _isOwner = isOwner ? 1 : 0;
    }

    private SafeList(List<T> list) { // PORT: §3.12 optional parameter isOwner = true
        this(list, true);
    }

    // PORT: §3.11 computed this(...) argument inlined; Iterable -> ArrayList copy goes through a helper
    public SafeList(Iterable<T> items) {
        this(items != null ? copyOf(items) : new ArrayList<T>(0));
    }

    // PORT: §3.11 helper for `new List<T>(items)`
    private static <T> List<T> copyOf(Iterable<T> items) {
        ArrayList<T> list = new ArrayList<T>();
        for (T item : items) {
            list.add(item);
        }
        return list;
    }

    @Override
    public int size() { // PORT: §3.10 Count -> size()
        return _length;
    }

    @Override
    public T get(int index) { // PORT: §3.1 indexer
        if (index < 0 || index >= _length)
            throw new IndexOutOfBoundsException(); // PORT: §3.16 IndexOutOfRangeException

        return _list.get(index);
    }

    @Internal
    public boolean isOwner() {
        return _isOwner != 0;
    }

    /// <summary>
    /// Creates a new list with the same elements as this list plus the item specified.
    /// </summary>
    public SafeList<T> addItem(T item) {
        // take ownership of list if possible
        var wasOwner = Interlocked.compareExchange(IS_OWNER, this, 0, 1); // PORT: §3.13
        if (wasOwner == 1) {
            _list.add(item);
            return new SafeList<T>(_list);
        } else {
            var newList = copy(_list, _length, _length + 1);
            newList.add(item);
            return new SafeList<T>(newList);
        }
    }

    /// <summary>
    /// Creates a new list with the same elements as this list plus the items specified.
    /// </summary>
    public SafeList<T> addItems(Iterable<T> items) {
        // take ownership of list if possible
        var wasOwner = Interlocked.compareExchange(IS_OWNER, this, 0, 1); // PORT: §3.13
        if (wasOwner == 1) {
            for (T item : items) { // PORT: §3.17 AddRange
                _list.add(item);
            }
            return new SafeList<T>(_list);
        } else {
            var newList = copy(_list, _length, _length);
            for (T item : items) { // PORT: §3.17 AddRange
                newList.add(item);
            }
            return new SafeList<T>(newList);
        }
    }

    private static <T> List<T> copy(List<T> source, int length, int newLength) {
        var newList = new ArrayList<T>(newLength);

        for (int i = 0; i < length; i++) {
            newList.add(source.get(i));
        }

        return newList;
    }

    public Enumerator<T> getEnumerator() {
        return new Enumerator<T>(_list, 0, _length);
    }

    @Override
    public Iterator<T> iterator() { // PORT: §3.2 IEnumerable<T>.GetEnumerator
        return this.getEnumerator();
    }

    // PORT: §3.2 the mutable struct Enumerator becomes an Iterator class
    public static final class Enumerator<T> implements Iterator<T> {
        private final List<T> _list;
        private final int _start;
        private final int _end;
        private int _index;

        public Enumerator(List<T> list, int start, int length) {
            _list = list;
            _start = start;
            _end = start + length;
            _index = start - 1;
        }

        public T current() {
            return _index >= _start && _index < _end ? _list.get(_index) : null; // PORT: §3.10 default(T)
        }

        public boolean moveNext() {
            _index++;
            return _index < _end;
        }

        public void dispose() {
        }

        public void reset() {
        }

        // PORT: §3.2 Iterator protocol over MoveNext/Current
        @Override
        public boolean hasNext() {
            return _index + 1 < _end;
        }

        // PORT: §3.2 Iterator protocol over MoveNext/Current
        @Override
        public T next() {
            if (!hasNext())
                throw new NoSuchElementException();
            moveNext();
            return current();
        }
    }

    /// <summary>
    /// An empty list
    /// </summary>
    // PORT: §3.9 static field of a generic class: one shared raw static, typed access through empty()
    @SuppressWarnings("rawtypes")
    public static final SafeList Empty =
        new SafeList<Object>(new ArrayList<Object>(0), false); // don't let anyone add to this global list

    // PORT: §3.9 typed access to the shared Empty instance
    @SuppressWarnings("unchecked")
    public static <T> SafeList<T> empty() {
        return (SafeList<T>) Empty;
    }

    // PORT: §3.10 a java.util.List view with identity equals/hashCode, so toReadOnly can return List<T> (IReadOnlyList<T>) and still be immutable
    public List<T> asList() {
        return new ListView<T>(this);
    }

    // PORT: §3.10 read-only List view of a SafeList; equals/hashCode stay identity as upstream IReadOnlyList (D26)
    public static final class ListView<T> extends AbstractList<T> implements RandomAccess {
        private final SafeList<T> _source;

        private ListView(SafeList<T> source) {
            _source = source;
        }

        public SafeList<T> source() {
            return _source;
        }

        @Override
        public int size() {
            return _source.size();
        }

        @Override
        public T get(int index) {
            return _source.get(index);
        }

        @Override
        public boolean equals(Object o) {
            return this == o;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }
}
