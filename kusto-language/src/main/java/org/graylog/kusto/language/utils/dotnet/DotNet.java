// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: .NET object.ToString, Convert.ChangeType and BCL throw helpers (PORTING.md 3.14, 3.16, 5.2).

package org.graylog.kusto.language.utils.dotnet;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

/** Miscellaneous .NET runtime behaviour that upstream relies on implicitly. */
public final class DotNet {
    private static final double TWO_POW_63 = 9.223372036854775808E18;
    /** Smallest double whose {@code (decimal)} conversion overflows (.NET {@code VarDecFromR8}). */
    private static final double DECIMAL_OVERFLOW = 7.9228162514264338E28;

    private DotNet() {
    }

    /**
     * .NET {@code object.ToString()} under the invariant culture, with string interpolation's null
     * rule: null gives {@code ""} (PORTING.md 3.14). {@code bool} is {@code True}/{@code False},
     * {@code double}/{@code float} use the .NET Core 3.0+ shortest round-trip format,
     * {@code decimal} keeps its scale, {@code TimeSpan} is "c", {@code DateTime} is
     * {@code MM/dd/yyyy HH:mm:ss}, {@code Guid} is "D" lower case.
     */
    public static String str(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof String s) {
            return s;
        }
        if (o instanceof Boolean b) {
            return DotNetBoolean.toString(b);
        }
        if (o instanceof Double d) {
            return DotNetNumber.toString(d.doubleValue());
        }
        if (o instanceof Float f) {
            return DotNetNumber.toString(f.floatValue());
        }
        if (o instanceof BigDecimal d) {
            return DotNetDecimal.toString(d);
        }
        if (o instanceof UUID g) {
            return DotNetGuid.toString(g);
        }
        return o.toString();
    }

    /**
     * .NET {@code Convert.ChangeType(value, targetType)} for the types upstream compares
     * ({@code ValueComparer.cs:26-27}): {@link String}, {@link Long}, {@link Integer}, {@link Double},
     * {@link Float}, {@link BigDecimal}, {@link Boolean}. A value already of the target type is returned
     * as is.
     *
     * @throws ClassCastException (.NET {@code InvalidCastException}) for a non-{@code IConvertible} source
     *     ({@code TimeSpan}, {@code Guid}) or an unsupported conversion
     * @throws ArithmeticException (.NET {@code OverflowException}) when the value is out of range
     * @throws IllegalArgumentException (.NET {@code FormatException}) when a string does not parse
     */
    public static Object changeType(Object value, Class<?> target) {
        if (value == null) {
            if (target.isPrimitive()) {
                throw new ClassCastException("Null object cannot be converted to a value type.");
            }
            return null;
        }
        if (target.isInstance(value)) {
            return value;
        }
        if (value instanceof TimeSpan || value instanceof UUID) {
            throw new ClassCastException("Object must implement IConvertible.");
        }
        if (target == String.class) {
            return str(value);
        }
        if (target == Long.class || target == long.class) {
            return toLong(value);
        }
        if (target == Integer.class || target == int.class) {
            long v = toLong(value);
            if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
                throw new ArithmeticException("Value was either too large or too small for an Int32.");
            }
            return (int) v;
        }
        if (target == Double.class || target == double.class) {
            return toDouble(value);
        }
        if (target == Float.class || target == float.class) {
            if (value instanceof String s) {
                Float f = DotNetNumber.tryParseFloat(s);
                if (f == null) {
                    throw formatException();
                }
                return f;
            }
            return (float) toDouble(value);
        }
        if (target == BigDecimal.class) {
            return toDecimal(value);
        }
        if (target == Boolean.class || target == boolean.class) {
            return toBoolean(value);
        }
        throw new ClassCastException("Invalid cast from '" + value.getClass().getSimpleName() + "' to '"
                + target.getSimpleName() + "'.");
    }

    /** .NET {@code Convert.ToInt64}: doubles round half to even; NaN and out-of-range throw. */
    private static long toLong(Object value) {
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Boolean b) {
            return b ? 1L : 0L;
        }
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            double r = Math.rint(d);
            if (!(r >= -TWO_POW_63 && r < TWO_POW_63)) {
                throw new ArithmeticException("Value was either too large or too small for an Int64.");
            }
            return (long) r;
        }
        if (value instanceof BigDecimal d) {
            BigDecimal r = d.setScale(0, RoundingMode.HALF_EVEN);
            try {
                return r.longValueExact();
            } catch (ArithmeticException e) {
                throw new ArithmeticException("Value was either too large or too small for an Int64.");
            }
        }
        if (value instanceof String s) {
            Long v = DotNetNumber.tryParseLong(s);
            if (v == null) {
                if (DotNetNumber.isIntegerOverflow(s)) {
                    throw new ArithmeticException("Value was either too large or too small for an Int64.");
                }
                throw formatException();
            }
            return v;
        }
        throw invalidCast(value, "Int64");
    }

    private static double toDouble(Object value) {
        if (value instanceof Double d) {
            return d;
        }
        if (value instanceof Float f) {
            return f;
        }
        if (value instanceof Long l) {
            return l;
        }
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Boolean b) {
            return b ? 1.0 : 0.0;
        }
        if (value instanceof BigDecimal d) {
            return d.doubleValue();
        }
        if (value instanceof String s) {
            Double v = DotNetNumber.tryParseDouble(s);
            if (v == null) {
                throw formatException();
            }
            return v;
        }
        throw invalidCast(value, "Double");
    }

    /** .NET {@code Convert.ToDecimal}: a double is rounded to 15 significant digits ({@code VarDecFromR8}). */
    private static BigDecimal toDecimal(Object value) {
        if (value instanceof Long l) {
            return BigDecimal.valueOf(l);
        }
        if (value instanceof Integer i) {
            return BigDecimal.valueOf(i);
        }
        if (value instanceof Boolean b) {
            return b ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Math.abs(d) >= DECIMAL_OVERFLOW) {
                throw new ArithmeticException("Value was either too large or too small for a Decimal.");
            }
            if (d == 0) {
                return BigDecimal.ZERO;
            }
            BigDecimal r = new BigDecimal(d).round(new MathContext(15, RoundingMode.HALF_EVEN)).stripTrailingZeros();
            if (r.scale() > 28) {
                r = r.setScale(28, RoundingMode.HALF_EVEN).stripTrailingZeros();
            }
            if (r.scale() < 0) {
                r = r.setScale(0);
            }
            return r.signum() == 0 ? BigDecimal.ZERO : r;
        }
        if (value instanceof String s) {
            BigDecimal v = DotNetDecimal.tryParse(s);
            if (v == null) {
                throw formatException();
            }
            return v;
        }
        throw invalidCast(value, "Decimal");
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Long l) {
            return l != 0;
        }
        if (value instanceof Integer i) {
            return i != 0;
        }
        if (value instanceof Double d) {
            return d != 0;
        }
        if (value instanceof Float f) {
            return f != 0;
        }
        if (value instanceof BigDecimal d) {
            return d.signum() != 0;
        }
        if (value instanceof String s) {
            Boolean b = DotNetBoolean.tryParse(s);
            if (b == null) {
                throw new IllegalArgumentException("String '" + s + "' was not recognized as a valid Boolean.");
            }
            return b;
        }
        throw invalidCast(value, "Boolean");
    }

    private static IllegalArgumentException formatException() {
        return new IllegalArgumentException("The input string was not in a correct format.");
    }

    private static ClassCastException invalidCast(Object value, String target) {
        return new ClassCastException("Invalid cast from '" + value.getClass().getSimpleName() + "' to '" + target + "'.");
    }

    /**
     * .NET {@code Dictionary.Add}: throws on an existing key.
     *
     * @throws IllegalArgumentException (.NET {@code ArgumentException}) when {@code key} is already present
     */
    public static <K, V> void dictionaryAdd(Map<K, V> map, K key, V value) {
        if (map.containsKey(key)) {
            throw new IllegalArgumentException("An item with the same key has already been added. Key: " + str(key));
        }
        map.put(key, value);
    }

    /** {@code Activator.CreateInstance<T>()} / {@code new T()}: invokes the no-arg constructor. */
    public static <T> T newInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("Cannot create an instance of " + type.getName(), cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create an instance of " + type.getName(), e);
        }
    }

    /**
     * C# {@code (long)d} on .NET 9+: truncates toward zero, saturates at the long range, NaN gives 0.
     * Java's narrowing conversion has exactly these semantics.
     */
    public static long longCast(double d) {
        return (long) d;
    }
}
