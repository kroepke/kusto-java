// Ported from: src/Kusto.Language/Utils/ListExtensions.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.EqualityComparer;
import org.graylog.kusto.language.utils.dotnet.Internal;
import org.graylog.kusto.language.utils.dotnet.Linq;
import org.graylog.kusto.language.utils.dotnet.ReadOnlyList;

public final class ListExtensions {
    private ListExtensions() {
    }

    /// <summary>
    /// Converts the sequence to a read only list.
    /// </summary>
    // PORT: §3.17 IReadOnlyList<T> is java.util.List<T>; the result is an identity-equality view over a SafeList (D26)
    public static <T> List<T> toReadOnly(Iterable<T> list) {
        if (list == null
            || list == EmptyReadOnlyList.Instance
            || (list instanceof List<?> rol && rol.size() == 0) // PORT: §3.15 `list is IReadOnlyList<T> rol && rol.Count == 0`
            || (list instanceof ReadOnlyList<?> rol2 && rol2.size() == 0)) { // PORT: §3.10 SafeList is a ReadOnlyList, not a List
            // empty read only list is immutable.
            return EmptyReadOnlyList.instance(); // PORT: §3.9
        } else if (list instanceof SafeList<T> safeList) {
            // SafeList is immutable
            return safeList.asList(); // PORT: §3.10
        } else if (list instanceof SafeList.ListView<T> view) {
            // already the immutable view of a SafeList
            return view; // PORT: §3.10
        } else {
            // convert to a SafeList to make it immutable.
            return new SafeList<T>(list).asList(); // PORT: §3.10
        }
    }

    /// <summary>
    /// Converts the sequence to a <see cref="SafeList{T}"/>
    /// </summary>
    public static <T> SafeList<T> toSafeList(Iterable<T> list) {
        if (list == null
            || list == EmptyReadOnlyList.Instance
            || (list instanceof List<?> rol && rol.size() == 0) // PORT: §3.15 `list is IReadOnlyList<T> rol && rol.Count == 0`
            || (list instanceof ReadOnlyList<?> rol2 && rol2.size() == 0)) { // PORT: §3.10
            // use an sharable empty SafeList
            return SafeList.empty(); // PORT: §3.9
        } else if (list instanceof SafeList<T> safeList) {
            // already a SafeList
            return safeList;
        } else if (list instanceof SafeList.ListView<T> view) {
            // PORT: §3.10 the view of a SafeList is the SafeList
            return view.source();
        } else {
            // make a new SafeList
            return new SafeList<T>(list);
        }
    }

    /// <summary>
    /// Converts a sequence to a <see cref="HashSet{T}"/>
    /// This function is provided because it does not exist for some runtimes (Bridge.Net included)
    /// </summary>
    public static <T> LinkedHashSet<T> toHashSetEx(Iterable<T> list) { // PORT: §3.17 HashSet -> LinkedHashSet
        var hs = new LinkedHashSet<T>();

        for (var item : list) {
            hs.add(item);
        }

        return hs;
    }

    /// <summary>
    /// Constructs a dictionary from a list that may have duplicate keys.
    /// In case of duplicates, the last matching item is kept.
    /// </summary>
    public static <TKey, TValue> LinkedHashMap<TKey, TValue> toDictionaryLast(Iterable<TValue> values, Function<TValue, TKey> keySelector) {
        return toDictionaryLast(values, keySelector, v -> v);
    }

    /// <summary>
    /// Constructs a dictionary from a list that may have duplicate keys.
    /// In case of duplicates, the last matching item is kept.
    /// </summary>
    public static <TItem, TKey, TValue> LinkedHashMap<TKey, TValue> toDictionaryLast(Iterable<TItem> items, Function<TItem, TKey> keySelector, Function<TItem, TValue> valueSelector) {
        var map = new LinkedHashMap<TKey, TValue>(); // PORT: §3.17
        for (var item : items) {
            var key = keySelector.apply(item);
            var value = valueSelector.apply(item);
            map.put(key, value);
        }

        return map;
    }

    public static <TItem, TKey> List<TItem> addOrUpdate(List<TItem> items, List<TItem> newOrUpdatedItems, Function<TItem, TKey> keySelector) {
        if (newOrUpdatedItems == null || newOrUpdatedItems.size() == 0) {
            // no items to add or update
            return items;
        } else if (newOrUpdatedItems.size() == 1) {
            // single item to add or update
            var item = newOrUpdatedItems.get(0);
            if (item == null)
                return items;

            var key = keySelector.apply(item);
            // PORT: §3.14 EqualityComparer<TKey>.Default.Equals -> Objects.equals

            // PORT: §3.6 items.Where(it => !comparer.Equals(key, keySelector(it))).Concat(new[] { item }).ToReadOnly()
            return toReadOnly(
                Linq.concat(
                    Linq.where(items, it -> !Objects.equals(key, keySelector.apply(it))),
                    List.of(item)));
        } else {
            // multiple items to add or updated
            // PORT: §3.6 items.Concat(newOrUpdatedItems).Where(x => x != null).DistinctLast(keySelector).ToReadOnly()
            return toReadOnly(
                distinctLast(
                    Linq.where(Linq.concat(items, newOrUpdatedItems), x -> x != null),
                    keySelector));
        }
    }

    /// <summary>
    /// Return the sequence of distinct items based on the key, where the first item with a matching key wins.
    /// </summary>
    // PORT: §3.6 IEnumerable result is an eager list; items.Distinct(new KeyComparer<TItem, TKey>(keySelector))
    public static <TItem, TKey> List<TItem> distinctFirst(Iterable<TItem> items, Function<TItem, TKey> keySelector) {
        var comparer = new KeyComparer<TItem, TKey>(keySelector);
        var seen = new LinkedHashSet<EqualityComparer.Key<TItem>>();
        var result = new ArrayList<TItem>();
        for (var item : items) {
            if (seen.add(EqualityComparer.of(item, comparer))) {
                result.add(item);
            }
        }
        return result;
    }

    /// <summary>
    /// Return the sequence of distinct items based on the key, where last item with a matching key wins.
    /// </summary>
    // PORT: §3.6 IEnumerable result is an eager list
    public static <TItem, TKey> List<TItem> distinctLast(Iterable<TItem> items, Function<TItem, TKey> keySelector) {
        return Linq.reverse(distinctFirst(Linq.reverse(items), keySelector));
    }

    private static class KeyComparer<TItem, TKey>
        implements EqualityComparer<TItem> {
        private final Function<TItem, TKey> _keySelector;

        public KeyComparer(Function<TItem, TKey> keySelector) {
            _keySelector = keySelector;
        }

        @Override
        public boolean equals(TItem x, TItem y) {
            return Objects.equals( // PORT: §3.14 EqualityComparer<TKey>.Default.Equals
                _keySelector.apply(x),
                _keySelector.apply(y));
        }

        @Override
        public int hashCode(TItem obj) {
            var key = _keySelector.apply(obj); // PORT: §3.14 ?.GetHashCode() ?? 0
            return key != null ? key.hashCode() : 0;
        }
    }


    /// <summary>
    /// Searches for an item in the ordered array that matches the comparison.
    /// The comparer function returns 0 if it matches the item, -1 if the item comes before and 1 if the item comes after.
    /// Returns the index of the item found or -1 if the item was not found.
    /// </summary>
    public static <T> int binarySearch(List<T> array, ToIntFunction<T> comparer) {
        var left = 0;
        var right = array.size() - 1;

        while (left <= right) {
            int mid = (left + right) / 2;

            var c = comparer.applyAsInt(array.get(mid));

            if (c == 0) {
                return mid;
            } else if (c > 0) {
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }

        // not found
        return -1;
    }

    /// <summary>
    /// Return the index of the item in the list or -1 if not found.
    /// </summary>
    public static <T> int indexOf(List<T> list, T item) {
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(item, list.get(i))) // PORT: §3.14 EqualityComparer<T>.Default.Equals
                return i;
        }

        return -1;
    }

    @Internal
    public static <T> void removeAll(List<T> list, Predicate<T> selector) {
        for (int i = list.size() - 1; i >= 0; i--) {
            if (selector.test(list.get(i))) {
                list.remove(i); // RemoveAt(i)
            }
        }
    }

    @Internal
    public static <T> void setCount(List<T> list, int count) {
        if (list.size() > count) {
            list.subList(count, list.size()).clear(); // PORT: §5.4 RemoveRange(count, list.Count - count)
        }
    }

    @Internal
    public static String join(List<String> items, String separator, String finalSeparator) {
        var builder = new StringBuilder();

        for (int i = 0, n = items.size(); i < n; i++) {
            if (i > 0)
                builder.append(DotNet.str(i < n - 1 ? separator : finalSeparator)); // PORT: §3.14 Append(null) appends nothing

            builder.append(DotNet.str(items.get(i))); // PORT: §3.14
        }

        return builder.toString();
    }

    @Internal
    public static String join(List<String> items, String separator) { // PORT: §3.12 optional parameter finalSeparator = null
        return join(items, separator, null);
    }

    @Internal
    public static <K, V> boolean isEquivalentTo(Map<K, V> a, Map<K, V> b) {
        if (a == b)
            return true;

        if (a == null || b == null)
            return false;

        if (a.size() != b.size())
            return false;

        for (var kvp : a.entrySet()) {
            if (!b.containsKey(kvp.getKey())) // PORT: §3.3 TryGetValue
                return false;
            var bValue = b.get(kvp.getKey());

            if (kvp.getValue() != null && bValue != null && !kvp.getValue().equals(bValue))
                return false;
        }

        return true;
    }

    /// <summary>
    /// Returns the index of the first match or -1 if no matches.
    /// </summary>
    public static <T> int firstIndex(List<T> list, Predicate<T> predicate) {
        for (int i = 0; i < list.size(); i++) {
            if (predicate.test(list.get(i)))
                return i;
        }

        return -1;
    }

    /// <summary>
    /// Returns the index of the last match or -1 if no matches.
    /// </summary>
    public static <T> int lastIndex(List<T> list, Predicate<T> predicate) {
        for (int i = list.size() - 1; i >= 0; i--) {
            if (predicate.test(list.get(i)))
                return i;
        }

        return -1;
    }

    /// <summary>
    /// Returns a new list with the item added.
    /// </summary>
    public static <T> List<T> append(List<T> list, T newItem) {
        return replaceRange(list, list.size(), 0, newItem);
    }

    /// <summary>
    /// Returns a new list with the items added.
    /// </summary>
    public static <T> List<T> append(List<T> list, List<T> newItems) {
        return replaceRange(list, list.size(), 0, newItems);
    }

    /// <summary>
    /// Returns a new list with the item at the specified index replaced with the new item.
    /// </summary>
    public static <T> List<T> replace(List<T> list, int index, T newItem) {
        return replaceRange(list, index, 1, newItem);
    }

    /// <summary>
    /// Returns a new list with the items in the specified range replaced with the new item.
    /// </summary>
    public static <T> List<T> replaceRange(List<T> list, int start, int length, T newItem) {
        var newList = new ArrayList<T>(list); // PORT: §3.17 list.ToList()

        if (length > 0 && start >= 0 && start < newList.size()) {
            var removed = Math.min(newList.size() - start, length);
            if (removed > 0)
                newList.subList(start, start + removed).clear(); // PORT: §5.4 RemoveRange(start, removed)
        }

        if (newItem != null && start >= 0 && start <= newList.size()) {
            newList.add(start, newItem); // Insert(start, newItem)
        }

        return newList;
    }

    /// <summary>
    /// Returns a new list with the items in the specified range replaced with the new item.
    /// </summary>
    public static <T> List<T> replaceRange(List<T> list, int start, int length, List<T> newItems) {
        var newList = new ArrayList<T>(list); // PORT: §3.17 list.ToList()

        if (length > 0 && start >= 0 && start < newList.size()) {
            var removed = Math.min(newList.size() - start, length);
            if (removed > 0)
                newList.subList(start, start + removed).clear(); // PORT: §5.4 RemoveRange(start, removed)
        }

        if (newItems != null && newItems.size() > 0 && start >= 0 && start <= newList.size()) {
            newList.addAll(start, newItems); // InsertRange(start, newItems)
        }

        return newList;
    }
}
