// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: eager LINQ-named helpers over Iterable, no streams (PORTING.md 3.6).

package org.graylog.kusto.language.utils.dotnet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/** Eager LINQ-style helpers returning {@code ArrayList}-backed lists. Never uses streams. */
public final class Linq {
    private Linq() {
    }

    private static <T> Iterable<T> nn(Iterable<T> source) {
        return Objects.requireNonNull(source, "source");
    }

    public static <T, R> List<R> select(Iterable<T> source, Function<? super T, ? extends R> f) {
        Objects.requireNonNull(f, "selector");
        ArrayList<R> r = new ArrayList<>();
        for (T t : nn(source)) {
            r.add(f.apply(t));
        }
        return r;
    }

    public static <T, R> List<R> selectIndexed(Iterable<T> source, BiFunction<? super T, Integer, ? extends R> f) {
        Objects.requireNonNull(f, "selector");
        ArrayList<R> r = new ArrayList<>();
        int i = 0;
        for (T t : nn(source)) {
            r.add(f.apply(t, i++));
        }
        return r;
    }

    public static <T> List<T> where(Iterable<T> source, Predicate<? super T> p) {
        Objects.requireNonNull(p, "predicate");
        ArrayList<T> r = new ArrayList<>();
        for (T t : nn(source)) {
            if (p.test(t)) {
                r.add(t);
            }
        }
        return r;
    }

    public static <T> boolean any(Iterable<T> source) {
        return nn(source).iterator().hasNext();
    }

    public static <T> boolean any(Iterable<T> source, Predicate<? super T> p) {
        Objects.requireNonNull(p, "predicate");
        for (T t : nn(source)) {
            if (p.test(t)) {
                return true;
            }
        }
        return false;
    }

    public static <T> boolean all(Iterable<T> source, Predicate<? super T> p) {
        Objects.requireNonNull(p, "predicate");
        for (T t : nn(source)) {
            if (!p.test(t)) {
                return false;
            }
        }
        return true;
    }

    public static <T> T first(Iterable<T> source) {
        Iterator<T> it = nn(source).iterator();
        if (!it.hasNext()) {
            throw new IllegalStateException("Sequence contains no elements");
        }
        return it.next();
    }

    public static <T> T first(Iterable<T> source, Predicate<? super T> p) {
        Objects.requireNonNull(p, "predicate");
        for (T t : nn(source)) {
            if (p.test(t)) {
                return t;
            }
        }
        throw new IllegalStateException("Sequence contains no matching element");
    }

    public static <T> T firstOrDefault(Iterable<T> source) {
        Iterator<T> it = nn(source).iterator();
        return it.hasNext() ? it.next() : null;
    }

    public static <T> T firstOrDefault(Iterable<T> source, Predicate<? super T> p) {
        Objects.requireNonNull(p, "predicate");
        for (T t : nn(source)) {
            if (p.test(t)) {
                return t;
            }
        }
        return null;
    }

    public static <T> T last(Iterable<T> source) {
        boolean found = false;
        T last = null;
        for (T t : nn(source)) {
            last = t;
            found = true;
        }
        if (!found) {
            throw new IllegalStateException("Sequence contains no elements");
        }
        return last;
    }

    public static <T> T last(Iterable<T> source, Predicate<? super T> p) {
        Objects.requireNonNull(p, "predicate");
        boolean found = false;
        T last = null;
        for (T t : nn(source)) {
            if (p.test(t)) {
                last = t;
                found = true;
            }
        }
        if (!found) {
            throw new IllegalStateException("Sequence contains no matching element");
        }
        return last;
    }

    public static <T> T lastOrDefault(Iterable<T> source) {
        T last = null;
        for (T t : nn(source)) {
            last = t;
        }
        return last;
    }

    public static <T> T lastOrDefault(Iterable<T> source, Predicate<? super T> p) {
        Objects.requireNonNull(p, "predicate");
        T last = null;
        for (T t : nn(source)) {
            if (p.test(t)) {
                last = t;
            }
        }
        return last;
    }

    public static <T> List<T> concat(Iterable<? extends T> a, Iterable<? extends T> b) {
        Objects.requireNonNull(a, "first");
        Objects.requireNonNull(b, "second");
        ArrayList<T> r = new ArrayList<>();
        for (T t : a) {
            r.add(t);
        }
        for (T t : b) {
            r.add(t);
        }
        return r;
    }

    public static <T> List<T> append(Iterable<? extends T> source, T item) {
        Objects.requireNonNull(source, "source");
        ArrayList<T> r = new ArrayList<>();
        for (T t : source) {
            r.add(t);
        }
        r.add(item);
        return r;
    }

    /** First occurrence wins; insertion order kept. */
    public static <T> List<T> distinct(Iterable<T> source) {
        LinkedHashSet<T> set = new LinkedHashSet<>();
        for (T t : nn(source)) {
            set.add(t);
        }
        return new ArrayList<>(set);
    }

    public static <T, K> List<T> distinctBy(Iterable<T> source, Function<? super T, ? extends K> key) {
        Objects.requireNonNull(key, "keySelector");
        LinkedHashSet<K> seen = new LinkedHashSet<>();
        ArrayList<T> r = new ArrayList<>();
        for (T t : nn(source)) {
            if (seen.add(key.apply(t))) {
                r.add(t);
            }
        }
        return r;
    }

    /** Distinct elements of {@code a} not in {@code b}, in first-occurrence order. */
    public static <T> List<T> except(Iterable<T> a, Iterable<? extends T> b) {
        Objects.requireNonNull(a, "first");
        Objects.requireNonNull(b, "second");
        LinkedHashSet<T> exclude = new LinkedHashSet<>();
        for (T t : b) {
            exclude.add(t);
        }
        LinkedHashSet<T> seen = new LinkedHashSet<>();
        ArrayList<T> r = new ArrayList<>();
        for (T t : a) {
            if (!exclude.contains(t) && seen.add(t)) {
                r.add(t);
            }
        }
        return r;
    }

    public static <T> List<T> ofType(Iterable<?> source, Class<T> type) {
        Objects.requireNonNull(type, "type");
        ArrayList<T> r = new ArrayList<>();
        for (Object o : nn(source)) {
            if (type.isInstance(o)) {
                r.add(type.cast(o));
            }
        }
        return r;
    }

    public static <T> List<T> cast(Iterable<?> source, Class<T> type) {
        Objects.requireNonNull(type, "type");
        ArrayList<T> r = new ArrayList<>();
        for (Object o : nn(source)) {
            r.add(type.cast(o));
        }
        return r;
    }

    public static <T> List<T> toList(Iterable<T> source) {
        ArrayList<T> r = new ArrayList<>();
        for (T t : nn(source)) {
            r.add(t);
        }
        return r;
    }

    public static <T> T[] toArray(Iterable<T> source, IntFunction<T[]> generator) {
        List<T> list = toList(source);
        return list.toArray(generator.apply(list.size()));
    }

    public static <T, K> LinkedHashMap<K, T> toDictionary(Iterable<T> source, Function<? super T, ? extends K> key) {
        return toDictionary(source, key, t -> t);
    }

    public static <T, K, V> LinkedHashMap<K, V> toDictionary(
            Iterable<T> source, Function<? super T, ? extends K> key, Function<? super T, ? extends V> value) {
        Objects.requireNonNull(key, "keySelector");
        Objects.requireNonNull(value, "elementSelector");
        LinkedHashMap<K, V> map = new LinkedHashMap<>();
        for (T t : nn(source)) {
            K k = key.apply(t);
            if (map.containsKey(k)) {
                throw new IllegalArgumentException("An item with the same key has already been added. Key: " + k);
            }
            map.put(k, value.apply(t));
        }
        return map;
    }

    public static <T extends Comparable<? super T>> T max(Iterable<T> source) {
        return max(source, t -> t);
    }

    public static <T, R extends Comparable<? super R>> R max(Iterable<T> source, Function<? super T, ? extends R> f) {
        Objects.requireNonNull(f, "selector");
        boolean found = false;
        R best = null;
        for (T t : nn(source)) {
            R v = f.apply(t);
            if (!found || v.compareTo(best) > 0) {
                best = v;
                found = true;
            }
        }
        if (!found) {
            throw new IllegalStateException("Sequence contains no elements");
        }
        return best;
    }

    public static <T extends Comparable<? super T>> T min(Iterable<T> source) {
        return min(source, t -> t);
    }

    public static <T, R extends Comparable<? super R>> R min(Iterable<T> source, Function<? super T, ? extends R> f) {
        Objects.requireNonNull(f, "selector");
        boolean found = false;
        R best = null;
        for (T t : nn(source)) {
            R v = f.apply(t);
            if (!found || v.compareTo(best) < 0) {
                best = v;
                found = true;
            }
        }
        if (!found) {
            throw new IllegalStateException("Sequence contains no elements");
        }
        return best;
    }

    /** Stable ascending sort by key. */
    public static <T, K extends Comparable<? super K>> List<T> orderBy(
            Iterable<T> source, Function<? super T, ? extends K> key) {
        return orderBy(source, key, Comparator.<K>naturalOrder());
    }

    public static <T, K> List<T> orderBy(
            Iterable<T> source, Function<? super T, ? extends K> key, Comparator<? super K> cmp) {
        Objects.requireNonNull(key, "keySelector");
        Objects.requireNonNull(cmp, "comparer");
        List<T> r = toList(source);
        Collections.sort(r, (x, y) -> cmp.compare(key.apply(x), key.apply(y)));
        return r;
    }

    /** Stable descending sort by key. */
    public static <T, K extends Comparable<? super K>> List<T> orderByDescending(
            Iterable<T> source, Function<? super T, ? extends K> key) {
        return orderByDescending(source, key, Comparator.<K>naturalOrder());
    }

    public static <T, K> List<T> orderByDescending(
            Iterable<T> source, Function<? super T, ? extends K> key, Comparator<? super K> cmp) {
        Objects.requireNonNull(key, "keySelector");
        Objects.requireNonNull(cmp, "comparer");
        List<T> r = toList(source);
        Comparator<T> c = (x, y) -> cmp.compare(key.apply(x), key.apply(y));
        r.sort(c.reversed());
        return r;
    }

    public static <T> boolean sequenceEqual(Iterable<T> a, Iterable<T> b) {
        Iterator<T> i = nn(a).iterator();
        Iterator<T> j = nn(b).iterator();
        while (i.hasNext() && j.hasNext()) {
            if (!Objects.equals(i.next(), j.next())) {
                return false;
            }
        }
        return !i.hasNext() && !j.hasNext();
    }

    /** Pairs elements until the shorter sequence ends. */
    public static <A, B, R> List<R> zip(Iterable<A> a, Iterable<B> b, BiFunction<? super A, ? super B, ? extends R> f) {
        Objects.requireNonNull(f, "resultSelector");
        Iterator<A> i = nn(a).iterator();
        Iterator<B> j = nn(b).iterator();
        ArrayList<R> r = new ArrayList<>();
        while (i.hasNext() && j.hasNext()) {
            r.add(f.apply(i.next(), j.next()));
        }
        return r;
    }

    public static <T, R> List<R> selectMany(Iterable<T> source, Function<? super T, ? extends Iterable<? extends R>> f) {
        Objects.requireNonNull(f, "selector");
        ArrayList<R> r = new ArrayList<>();
        for (T t : nn(source)) {
            for (R x : f.apply(t)) {
                r.add(x);
            }
        }
        return r;
    }

    public static <T> List<T> take(Iterable<T> source, int count) {
        ArrayList<T> r = new ArrayList<>();
        if (count <= 0) {
            nn(source);
            return r;
        }
        for (T t : nn(source)) {
            r.add(t);
            if (r.size() >= count) {
                break;
            }
        }
        return r;
    }

    public static <T> List<T> skip(Iterable<T> source, int count) {
        ArrayList<T> r = new ArrayList<>();
        int i = 0;
        for (T t : nn(source)) {
            if (i++ >= count) {
                r.add(t);
            }
        }
        return r;
    }

    public static <T> int count(Iterable<T> source) {
        if (nn(source) instanceof java.util.Collection) {
            return ((java.util.Collection<?>) source).size();
        }
        int n = 0;
        for (T ignored : source) {
            n++;
        }
        return n;
    }

    public static <T> int count(Iterable<T> source, Predicate<? super T> p) {
        Objects.requireNonNull(p, "predicate");
        int n = 0;
        for (T t : nn(source)) {
            if (p.test(t)) {
                n++;
            }
        }
        return n;
    }

    public static List<Integer> range(int start, int count) {
        if (count < 0 || (long) start + count - 1 > Integer.MAX_VALUE) {
            throw new IndexOutOfBoundsException("count");
        }
        ArrayList<Integer> r = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            r.add(start + i);
        }
        return r;
    }

    public static <T> List<T> reverse(Iterable<T> source) {
        List<T> r = toList(source);
        Collections.reverse(r);
        return r;
    }

    public static <T> boolean contains(Iterable<T> source, T item) {
        for (T t : nn(source)) {
            if (Objects.equals(t, item)) {
                return true;
            }
        }
        return false;
    }

    public static int sum(Iterable<Integer> source) {
        int s = 0;
        for (Integer i : nn(source)) {
            s = Math.addExact(s, i);
        }
        return s;
    }

    public static <T, A> A aggregate(Iterable<T> source, A seed, BiFunction<A, ? super T, A> f) {
        Objects.requireNonNull(f, "func");
        A acc = seed;
        for (T t : nn(source)) {
            acc = f.apply(acc, t);
        }
        return acc;
    }

    /** Seedless aggregate; throws {@link IllegalStateException} on empty input. */
    public static <T> T aggregate(Iterable<T> source, BiFunction<T, T, T> f) {
        Objects.requireNonNull(f, "func");
        Iterator<T> it = nn(source).iterator();
        if (!it.hasNext()) {
            throw new IllegalStateException("Sequence contains no elements");
        }
        T acc = it.next();
        while (it.hasNext()) {
            acc = f.apply(acc, it.next());
        }
        return acc;
    }
}
