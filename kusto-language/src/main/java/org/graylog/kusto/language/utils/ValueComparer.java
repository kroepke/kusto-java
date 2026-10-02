// Ported from: src/Kusto.Language/Utils/ValueComparer.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.math.BigDecimal;

import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Internal;
import org.graylog.kusto.language.utils.dotnet.Out;

@Internal
public final class ValueComparer {
    private ValueComparer() {
    }

    /// <summary>
    /// Returns true if the values are equivalent.
    /// The values do not have to be the same type.
    /// </summary>
    public static boolean areEquivalent(Object x, Object y, boolean caseSensitive) {
        if (x == null && y == null)
            return true;

        if (x == null || y == null)
            return false;

        var xType = x.getClass();
        var yType = y.getClass();

        // make both values have the same type for comparison
        Out<Class<?>> comparingType = new Out<Class<?>>(); // PORT: §3.3 out parameter
        if (!tryGetBestComparingType(xType, yType, comparingType))
            return false;

        var convertedX = comparingType.value == xType ? x : DotNet.changeType(x, comparingType.value); // PORT: §5.2 Convert.ChangeType
        var convertedY = comparingType.value == yType ? y : DotNet.changeType(y, comparingType.value); // PORT: §5.2 Convert.ChangeType

        if (comparingType.value == String.class) {
            return DotNetStrings.compare((String) convertedX, (String) convertedY, !caseSensitive) == 0;
        } else {
            return objectEquals(convertedX, convertedY);
        }
    }

    public static boolean areEquivalent(Object x, Object y) { // PORT: §3.12 optional parameter caseSensitive = false
        return areEquivalent(x, y, false);
    }

    // PORT: §5.2 object.Equals on boxed values: doubles/floats compare with == (0.0 equals -0.0) and NaN equals NaN, decimals ignore scale
    private static boolean objectEquals(Object a, Object b) {
        if (a instanceof Double da && b instanceof Double db) {
            return da.doubleValue() == db.doubleValue() || (da.isNaN() && db.isNaN());
        }
        if (a instanceof Float fa && b instanceof Float fb) {
            return fa.floatValue() == fb.floatValue() || (fa.isNaN() && fb.isNaN());
        }
        if (a instanceof BigDecimal ba && b instanceof BigDecimal bb) {
            return ba.compareTo(bb) == 0;
        }
        return a.equals(b);
    }

    /// <summary>
    /// Gets the best type for comparing between the two specified types,
    /// such that ChangeType will not throw.
    /// </summary>
    private static boolean tryGetBestComparingType(Class<?> typeA, Class<?> typeB, Out<Class<?>> comparingType) {
        if (typeA == typeB) {
            comparingType.value = typeA;
            return true;
        } else if (typeA == String.class || typeB == String.class) {
            comparingType.value = String.class;
            return true;
        } else if (isNumeric(typeA) && isNumeric(typeB)) {
            if (typeA == Double.class || typeB == Double.class) {
                comparingType.value = Double.class;
            } else if (typeA == BigDecimal.class || typeB == BigDecimal.class) {
                comparingType.value = BigDecimal.class;
            } else if (typeA == Float.class || typeB == Float.class) {
                comparingType.value = Float.class;
            }
            // PORT: §3.7 the UInt64 branch (typeA == typeof(UInt64) || typeB == typeof(UInt64)) is dead: no Java value maps to UInt64
            else {
                comparingType.value = Long.class;
            }

            return true;
        } else {
            comparingType.value = null;
            return false;
        }
    }

    // PORT: §3.7 Type.GetTypeCode switch over SByte/Byte/Int16/UInt16/Int32/UInt32/Int64/UInt64/Single/Double/Decimal:
    // only the boxed types the port produces (Integer, Long, Float, Double, BigDecimal) can occur
    private static boolean isNumeric(Class<?> type) {
        return type == Integer.class
            || type == Long.class
            || type == Float.class
            || type == Double.class
            || type == BigDecimal.class;
    }
}
