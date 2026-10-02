// Ported from: src/Kusto.Language/Symbols/TypeFacts.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Predicate;

import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.syntax.SeparatedElement1;
import org.graylog.kusto.language.syntax.SyntaxList1;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.ObjectPool;
import org.graylog.kusto.language.utils.dotnet.Out;

public final class TypeFacts
{
    private TypeFacts() // PORT: §3.5 static class
    {
    }

    /// <summary>
    /// Gets the element type of an array or null if the type is not an array or dynamic.
    /// </summary>
    public static TypeSymbol getElementType(TypeSymbol type)
    {
        if (type instanceof DynamicArraySymbol array)
        {
            return ScalarTypes.getDynamic(array.elementType());
        }
        else if (type == ScalarTypes.Dynamic)
        {
            return ScalarTypes.Dynamic;
        }
        else
        {
            return null;
        }
    }

    /// <summary>
    /// Returns the widest numeric type from the set of types
    /// The widest type is the one that can contain the values of all the other types:
    /// </summary>
    public static ScalarSymbol getWidestScalarType(TypeSymbol... scalarTypes)
    {
        return getWidestScalarType(scalarTypes != null ? Arrays.asList(scalarTypes) : (List<TypeSymbol>)null); // PORT: §3.17 array as IReadOnlyList
    }

    /// <summary>
    /// Returns the widest scalar type from the set of types, or null if there are no scalar types.
    /// The widest type is the one that can contain the values of all the other types:
    /// </summary>
    public static ScalarSymbol getWidestScalarType(List<? extends TypeSymbol> scalarTypes) // PORT: §3.10 covariance
    {
        ScalarSymbol widestType = null;

        if (scalarTypes != null)
        {
            for (int i = 0; i < scalarTypes.size(); i++)
            {
                var type = scalarTypes.get(i);

                if (type instanceof ScalarSymbol s && s.isNumeric() && s != widestType)
                {
                    if (widestType == null || s.isWiderThan(widestType))
                    {
                        widestType = s;
                    }
                }
            }
        }

        return widestType;
    }

    /// <summary>
    /// Gets the common scalar type amongst a set of types.
    /// </summary>
    public static TypeSymbol getCommonType(
        List<? extends TypeSymbol> types, // PORT: §3.10 covariance
        Predicate<TypeSymbol> fnInclude,
        Conversion allowedConversion,
        TypeSymbol defaultType)
    {
        TypeSymbol commonType = null;

        if (types != null)
        {
            for (var type : types)
            {
                if (type != null && (fnInclude == null || fnInclude.test(type)))
                {
                    Out<TypeSymbol> outCommonType = new Out<>(); // PORT: §3.3 out aliased with an input argument
                    var ok = tryGetCommonType(commonType, type, allowedConversion, outCommonType);
                    commonType = outCommonType.value;
                    if (!ok)
                        return defaultType;
                }
            }
        }

        return commonType != null ? commonType : defaultType; // PORT: §3.14
    }

    public static TypeSymbol getCommonType(List<? extends TypeSymbol> types, Predicate<TypeSymbol> fnInclude, Conversion allowedConversion) // PORT: §3.12 optional parameter defaultType = null
    {
        return getCommonType(types, fnInclude, allowedConversion, null);
    }

    public static TypeSymbol getCommonType(List<? extends TypeSymbol> types, Predicate<TypeSymbol> fnInclude) // PORT: §3.12 optional parameter allowedConversion = Conversion.Promotable
    {
        return getCommonType(types, fnInclude, Conversion.Promotable);
    }

    public static TypeSymbol getCommonType(List<? extends TypeSymbol> types) // PORT: §3.12 optional parameter fnInclude = null
    {
        return getCommonType(types, null);
    }

    /// <summary>
    /// Gets the common scalar type amongst a set of types.
    /// </summary>
    public static TypeSymbol getCommonScalarType(
        List<? extends TypeSymbol> types, // PORT: §3.10 covariance
        Conversion allowedConversions)
    {
        return getCommonType(types, t -> t.isScalar(), allowedConversions);
    }

    public static TypeSymbol getCommonScalarType(List<? extends TypeSymbol> types) // PORT: §3.12 optional parameter allowedConversions = Conversion.Promotable
    {
        return getCommonScalarType(types, Conversion.Promotable);
    }

    /// <summary>
    /// Gets the common scalar type amongst a set of types.
    /// </summary>
    public static TypeSymbol getCommonScalarType(TypeSymbol... types)
    {
        return getCommonScalarType(types != null ? Arrays.asList(types) : (List<TypeSymbol>)null); // PORT: §3.17 array as IReadOnlyList
    }

    /// <summary>
    /// Gets the common scalar result type amongst a set of expressions.
    /// </summary>
    public static TypeSymbol getCommonResultType(
        List<? extends Expression> expressions, // PORT: §3.10 covariance
        Conversion allowedConversions,
        TypeSymbol defaultType)
    {
        TypeSymbol commonType = null;

        if (expressions != null)
        {
            for (var expr : expressions)
            {
                if (expr != null)
                {
                    Out<TypeSymbol> outCommonType = new Out<>(); // PORT: §3.3 out aliased with an input argument
                    var ok = tryGetCommonType(commonType, expr.resultType(), allowedConversions, outCommonType);
                    commonType = outCommonType.value;
                    if (!ok)
                        return defaultType;
                }
            }
        }

        return commonType != null ? commonType : defaultType; // PORT: §3.14
    }

    public static TypeSymbol getCommonResultType(List<? extends Expression> expressions, Conversion allowedConversions) // PORT: §3.12 optional parameter defaultType = null
    {
        return getCommonResultType(expressions, allowedConversions, (TypeSymbol)null);
    }

    public static TypeSymbol getCommonResultType(List<? extends Expression> expressions) // PORT: §3.12 optional parameter allowedConversions = Conversion.Promotable
    {
        return getCommonResultType(expressions, Conversion.Promotable);
    }

    /// <summary>
    /// Gets the common scalar result type amongst a set of expressions.
    /// </summary>
    public static TypeSymbol getCommonResultType(Expression... expressions)
    {
        return getCommonResultType(expressions != null ? Arrays.asList(expressions) : (List<Expression>)null); // PORT: §3.17 array as IReadOnlyList
    }

    /// <summary>
    /// Gets the common scalar result type amongst a set of expressions.
    /// </summary>
    public static TypeSymbol getCommonResultType(
        SyntaxList1<SeparatedElement1<Expression>> expressions, // PORT: §2.4
        Conversion allowedConversions,
        TypeSymbol defaultType,
        boolean ignoreDynamic)
    {
        TypeSymbol commonType = null;

        if (expressions != null)
        {
            for (int i = 0; i < expressions.size(); i++)
            {
                var expr = expressions.get(i).element();
                if (ignoreDynamic && expr.resultType() instanceof DynamicSymbol)
                    continue;
                Out<TypeSymbol> outCommonType = new Out<>(); // PORT: §3.3 out aliased with an input argument
                var ok = tryGetCommonType(commonType, expr.resultType(), allowedConversions, outCommonType);
                commonType = outCommonType.value;
                if (!ok)
                    return defaultType;
            }
        }

        if (commonType == null && ignoreDynamic)
            return getCommonResultType(expressions, allowedConversions, defaultType, false);

        return commonType != null ? commonType : defaultType; // PORT: §3.14
    }

    public static TypeSymbol getCommonResultType(SyntaxList1<SeparatedElement1<Expression>> expressions, Conversion allowedConversions, TypeSymbol defaultType) // PORT: §3.12 optional parameter ignoreDynamic = false
    {
        return getCommonResultType(expressions, allowedConversions, defaultType, false);
    }

    public static TypeSymbol getCommonResultType(SyntaxList1<SeparatedElement1<Expression>> expressions, Conversion allowedConversions) // PORT: §3.12 optional parameter defaultType = null
    {
        return getCommonResultType(expressions, allowedConversions, (TypeSymbol)null);
    }

    public static TypeSymbol getCommonResultType(SyntaxList1<SeparatedElement1<Expression>> expressions) // PORT: §3.12 optional parameter allowedConversions = Conversion.Promotable
    {
        return getCommonResultType(expressions, Conversion.Promotable);
    }

    /// <summary>
    /// Gets the common type amongst a set of columns.
    /// </summary>
    public static TypeSymbol getCommonColumnType(
        List<ColumnSymbol> columns,
        Conversion allowedConversions,
        TypeSymbol defaultType)
    {
        TypeSymbol commonType = null;

        if (columns != null)
        {
            for (var col : columns)
            {
                Out<TypeSymbol> outCommonType = new Out<>(); // PORT: §3.3 out aliased with an input argument
                var ok = tryGetCommonType(commonType, col.type(), allowedConversions, outCommonType);
                commonType = outCommonType.value;
                if (!ok)
                    return defaultType;
            }
        }

        return commonType != null ? commonType : defaultType; // PORT: §3.14
    }

    public static TypeSymbol getCommonColumnType(List<ColumnSymbol> columns, Conversion allowedConversions) // PORT: §3.12 optional parameter defaultType = null
    {
        return getCommonColumnType(columns, allowedConversions, null);
    }

    public static TypeSymbol getCommonColumnType(List<ColumnSymbol> columns) // PORT: §3.12 optional parameter allowedConversions = Conversion.Promotable
    {
        return getCommonColumnType(columns, Conversion.Promotable);
    }

    /// <summary>
    /// Gets the type that is wider/more-general for each specified type.
    /// </summary>
    private static boolean tryGetCommonType(
        TypeSymbol typeA, 
        TypeSymbol typeB, 
        Conversion allowedConversions,
        Out<TypeSymbol> commonType) // PORT: §3.3
    {
        Out<TypeSymbol> dut = new Out<>(); // PORT: §3.3 `out var dut`, shared by the three DynamicPrimitiveSymbol branches as upstream

        if (typeA == null && typeB == null)
        {
            commonType.value = null;
            return false;
        }
        else if (typeB == null)
        {
            commonType.value = typeA;
            return true;
        }
        else if (typeA == null)
        {
            commonType.value = typeB;
            return true;
        }
        else if (typeA == typeB)
        {
            commonType.value = typeA;
            return true;
        }
        else if (typeA == ScalarTypes.Null)
        {
            commonType.value = typeB;
            return true;
        }
        else if (typeB == ScalarTypes.Null)
        {
            commonType.value = typeA;
            return true;
        }
        else if (typeA == ScalarTypes.Unknown
            || typeB == ScalarTypes.Unknown)
        {
            commonType.value = ScalarTypes.Unknown;
            return true;
        }
        else if (typeA == ScalarTypes.Dynamic
            || typeB == ScalarTypes.Dynamic)
        {
            commonType.value = ScalarTypes.Dynamic;
            return true;
        }
        else if (isPromotableTo(typeA, typeB) // PORT: §3.5
            && isConversionAllowed(Conversion.Promotable, allowedConversions))
        {
            commonType.value = typeB;
            return true;
        }
        else if (isPromotableTo(typeB, typeA) // PORT: §3.5
            && isConversionAllowed(Conversion.Promotable, allowedConversions))
        {
            commonType.value = typeA;
            return true;
        }
        else if (typeA instanceof DynamicArraySymbol arrayA && typeB instanceof DynamicArraySymbol arrayB)
        {
            // PORT-BUG: equal element types yield the element type, not an array type (TypeFacts.cs:255-257); mirrored verbatim
            commonType.value = (arrayA.elementType() == arrayB.elementType())
                ? arrayA.elementType()
                : ScalarTypes.DynamicArray;
            return true;
        }
        else if (typeA instanceof DynamicBagSymbol bagA && typeB instanceof DynamicBagSymbol bagB)
        {
            commonType.value = ScalarTypes.getDynamicBag(intersect(bagA.properties(), bagB.properties()));
            return true;
        }
        else if (typeA instanceof DynamicPrimitiveSymbol dpa
            && typeB instanceof DynamicPrimitiveSymbol dpb
            && tryGetCommonType(dpa.underlyingType(), dpb.underlyingType(), allowedConversions, dut))
        {
            commonType.value = ScalarTypes.getDynamic(dut.value);
            return true;
        }
        else if (typeA instanceof DynamicPrimitiveSymbol dpa1
            && typeB instanceof ScalarSymbol
            && tryGetCommonType(dpa1.underlyingType(), typeB, allowedConversions, dut))
        {
            commonType.value = ScalarTypes.getDynamic(dut.value);
            return true;
        }
        else if (typeA instanceof ScalarSymbol
            && typeB instanceof DynamicPrimitiveSymbol dpb1
            && tryGetCommonType(typeA, dpb1.underlyingType(), allowedConversions, dut))
        {
            commonType.value = ScalarTypes.getDynamic(dut.value);
            return true;
        }
        else if (typeA instanceof DynamicSymbol 
            && typeB instanceof DynamicSymbol)
        {
            commonType.value = ScalarTypes.Dynamic;
            return true;
        }
        else if (typeA instanceof TableSymbol tableA && typeB instanceof TableSymbol tableB)
        {
            commonType.value = new TableSymbol(intersect(tableA.columns(), tableB.columns()));
            return true;
        }
        else if (typeA.isScalar() && typeB.isScalar() 
            && isConversionAllowed(Conversion.Dynamic, allowedConversions))
        {
            commonType.value = ScalarTypes.Dynamic;
            return true;
        }
        else
        {
            commonType.value = null;
            return false;
        }
    }

    /// <summary>
    /// Returns true if the specified conversion is allowed given the allowed conversions.
    /// </summary>
    public static boolean isConversionAllowed(Conversion conversion, Conversion allowedConversions)
    {
        return conversion.compareTo(allowedConversions) <= 0; // PORT: §3.17 enum `<=` → compareTo (declaration order)
    }

    /// <summary>
    /// Gets the common argument type for arguments corresponding to parameters constrained to specific <see cref="ParameterTypeKind"/>.CommonXXX values.
    /// </summary>
    public static TypeSymbol getCommonArgumentType(
        List<Parameter> argumentParameters, 
        List<? extends TypeSymbol> argumentTypes, // PORT: §3.10 covariance
        TypeSymbol defaultType,
        boolean ignoreDynamic)
    {
        TypeSymbol commonType = null;

        for (int i = 0; i < argumentTypes.size(); i++)
        {
            var parameter = argumentParameters.get(i);
            if (parameter != null)
            {
                var argType = argumentTypes.get(i);

                if (ignoreDynamic && argType instanceof DynamicSymbol)
                    continue;

                if ((parameter.typeKind() == ParameterTypeKind.CommonScalar && argType.isScalar())
                    || (parameter.typeKind() == ParameterTypeKind.CommonNumber && isNumeric(argType))
                    || (parameter.typeKind() == ParameterTypeKind.CommonSummable && isSummable(argType))
                    || (parameter.typeKind() == ParameterTypeKind.CommonOrderable && isOrderable(argType)))
                {
                    Out<TypeSymbol> outCommonType = new Out<>(); // PORT: §3.3 out aliased with an input argument
                    var ok = tryGetCommonType(commonType, argType, Conversion.Promotable, outCommonType);
                    commonType = outCommonType.value;
                    if (!ok)
                        return defaultType;
                }
                else if (parameter.typeKind() == ParameterTypeKind.CommonScalarOrDynamic && argType.isScalar())
                {
                    Out<TypeSymbol> outCommonType = new Out<>(); // PORT: §3.3 out aliased with an input argument
                    var ok = tryGetCommonType(commonType, argType, Conversion.Dynamic, outCommonType);
                    commonType = outCommonType.value;
                    if (!ok)
                        return defaultType;
                }
            }
        }

        if (commonType == null && ignoreDynamic)
            return getCommonArgumentType(argumentParameters, argumentTypes, defaultType, false);

        return commonType != null ? commonType : defaultType; // PORT: §3.14
    }

    public static TypeSymbol getCommonArgumentType(List<Parameter> argumentParameters, List<? extends TypeSymbol> argumentTypes, TypeSymbol defaultType) // PORT: §3.12 optional parameter ignoreDynamic = false
    {
        return getCommonArgumentType(argumentParameters, argumentTypes, defaultType, false);
    }

    public static TypeSymbol getCommonArgumentType(List<Parameter> argumentParameters, List<? extends TypeSymbol> argumentTypes) // PORT: §3.12 optional parameter defaultType = null
    {
        return getCommonArgumentType(argumentParameters, argumentTypes, null);
    }

    /// <summary>
    /// Promotes int to long
    /// </summary>
    public static TypeSymbol promoteToLong(TypeSymbol type) // PORT: §3.5 extension method; null receiver returns null as upstream
    {
        return type == ScalarTypes.Int ? ScalarTypes.Long : type;
    }

    /// <summary>
    /// Returns true if this type can be promoted to the specified type.
    /// </summary>
    public static boolean isPromotableTo(TypeSymbol sourceType, TypeSymbol targetType) // PORT: §3.5
    {
        return (sourceType instanceof ScalarSymbol sourceScalar
             && targetType instanceof ScalarSymbol targetScalar
             && targetScalar.isWiderThan(sourceScalar))
            ||
            ((sourceType.kind() == SymbolKind.Bag 
                || sourceType.kind() == SymbolKind.Array)
             && targetType == ScalarTypes.Dynamic);
    }

    /// <summary>
    /// Returns true if the type is an integer.
    /// </summary>
    public static boolean isInteger(Symbol type) // PORT: §3.5
    {
        return type instanceof ScalarSymbol scalar && scalar.isInteger();
    }

    /// <summary>
    /// Returns true if the type is an interval.
    /// </summary>
    public static boolean isInterval(Symbol type) // PORT: §3.5
    {
        return type instanceof ScalarSymbol scalar && scalar.isInterval();
    }

    /// <summary>
    /// Returns true if the type is numeric.
    /// </summary>
    public static boolean isNumeric(Symbol type) // PORT: §3.5
    {
        return type instanceof ScalarSymbol scalar && scalar.isNumeric();
    }

    /// <summary>
    /// Returns true if the type is summable.
    /// </summary>
    public static boolean isSummable(Symbol type) // PORT: §3.5
    {
        return type instanceof ScalarSymbol scalar && scalar.isSummable();
    }

    /// <summary>
    /// Returns true if the type is orderable.
    /// </summary>
    public static boolean isOrderable(Symbol type) // PORT: §3.5
    {
        return type instanceof ScalarSymbol scalar && scalar.isOrderable();
    }

    /// <summary>
    /// Returns true if the type is any scalar type except any dynamic symbol.
    /// </summary>
    public static boolean isAnyScalarExceptDynamic(Symbol type) // PORT: §3.5
    {
        return type instanceof ScalarSymbol 
            && !(type instanceof DynamicSymbol);
    }

    /// <summary>
    /// Returns true if the type is any scalar type except bool or dynamic(bool)
    /// </summary>
    public static boolean isAnyScalarExceptBool(Symbol type) // PORT: §3.5
    {
        return type instanceof ScalarSymbol 
            && type != ScalarTypes.Bool
            && type != ScalarTypes.DynamicBool;
    }

    /// <summary>
    /// Returns true if the type is any scalar except real, bool, dynamic(real) or dynamic(bool)
    /// </summary>
    public static boolean isAnyScalarExceptReadOrBool(Symbol type) // PORT: §3.5
    {
        return type instanceof ScalarSymbol 
            && type != ScalarTypes.Real 
            && type != ScalarTypes.Bool
            && type != ScalarTypes.DynamicReal
            && type != ScalarTypes.DynamicBool;
    }

    /// <summary>
    /// Returns true if the type is numeric or bool
    /// </summary>
    public static boolean isNumericOrBool(Symbol type) // PORT: §3.5
    {
        return type == ScalarTypes.Bool
            || isNumeric(type);
    }

    /// <summary>
    /// Returns true if the type is real or decimal
    /// </summary>
    public static boolean isRealOrDecimal(Symbol type) // PORT: §3.5
    {
        return type == ScalarTypes.Real
            || type == ScalarTypes.Decimal;
    }

    /// <summary>
    /// Returns true if the type is string or any dynamic
    /// </summary>
    public static boolean isStringOrDynamic(Symbol type) // PORT: §3.5
    {
        return type == ScalarTypes.String
            || type instanceof DynamicSymbol;
    }

    /// <summary>
    /// Returns true if the type is string or any array of strings.
    /// </summary>
    public static boolean isStringOrArray(Symbol type) // PORT: §3.5
    {
        return type == ScalarTypes.String
            || type == ScalarTypes.DynamicString
            || type == ScalarTypes.DynamicArrayOfString
            || type == ScalarTypes.DynamicArray     // might be array of strings
            || type == ScalarTypes.Dynamic;  // might be string or array of strings 
    }

    /// <summary>
    /// Returns true if the type is any integer, any dynamic integer, dynamic or dynamic array.
    /// </summary>
    public static boolean isIntegerOrArray(Symbol type) // PORT: §3.5
    {
        return isInteger(type) // PORT: §3.5
            || type == ScalarTypes.DynamicLong
            || type == ScalarTypes.DynamicArrayOfLong
            || type == ScalarTypes.DynamicArray
            || type == ScalarTypes.Dynamic;
    }

    /// <summary>
    /// Returns true if the type is any integer, any integer, dynamic integer or dynamic.
    /// </summary>
    public static boolean isIntegerOrDynamic(Symbol type) // PORT: §3.5
    {
        return isInteger(type) // PORT: §3.5
            || type == ScalarTypes.DynamicLong
            || type == ScalarTypes.Dynamic;
    }

    /// <summary>
    /// Returns true if the type is any dynamic array type or dynamic.
    /// </summary>
    public static boolean isDynamicArray(Symbol type) // PORT: §3.5
    {
        return type instanceof DynamicArraySymbol
            || type == ScalarTypes.Dynamic; // might be an array
    }

    /// <summary>
    /// Returns true if the type is any dynamic bag type or dynamic.
    /// </summary>
    public static boolean isDynamicBag(Symbol type) // PORT: §3.5
    {
        return type instanceof DynamicBagSymbol
            || type == ScalarTypes.Dynamic; // might be a bag
    }

    /// <summary>
    /// Returns true if the type is dynamic, array or bag.
    /// </summary>
    public static boolean isDynamicArrayOrBag(Symbol type) // PORT: §3.5
    {
        return type == ScalarTypes.Dynamic
            || type instanceof DynamicArraySymbol
            || type instanceof DynamicBagSymbol;
    }

    private static final ObjectPool<LinkedHashMap<String, TypeSymbol>> s_nameToTypePool = // PORT: §3.17 Dictionary → LinkedHashMap
        new ObjectPool<LinkedHashMap<String, TypeSymbol>>(
            () -> new LinkedHashMap<String, TypeSymbol>(),
            map -> map.clear());

    private static final ObjectPool<ArrayList<ColumnSymbol>> s_columnListPool = // PORT: §3.17 List → ArrayList
        new ObjectPool<ArrayList<ColumnSymbol>>(
            () -> new ArrayList<ColumnSymbol>(),
            list -> list.clear());

    /// <summary>
    /// Returns this list of columns from both lists.
    /// If each contains the same named columns, a column of the common type is used.
    /// </summary>
    public static List<ColumnSymbol> union(List<ColumnSymbol> columnsA, List<ColumnSymbol> columnsB)
    {
        var nameToTypeMap = s_nameToTypePool.allocateFromPool();
        var columnList = s_columnListPool.allocateFromPool();
        try
        {
            for (var col : columnsA)
            {
                nameToTypeMap.put(col.name(), col.type());
            }

            for (var col : columnsB)
            {
                var currentType = nameToTypeMap.get(col.name()); // PORT: §3.3 TryGetValue; values are never null
                if (currentType != null)
                {
                    Out<TypeSymbol> commonType = new Out<>(); // PORT: §3.3
                    if (!tryGetCommonType(currentType, col.type(), Conversion.Dynamic, commonType))
                    {
                        // PORT-BUG: the map is only updated when no common type exists, so a found common type is dropped (TypeFacts.cs:534-537); mirrored verbatim
                        nameToTypeMap.put(col.name(), commonType.value != null ? commonType.value : ScalarTypes.Dynamic); // PORT: §3.14
                    }
                }
                else
                {
                    nameToTypeMap.put(col.name(), col.type());
                }
            }

            for (var col : columnsA)
            {
                var type = nameToTypeMap.get(col.name()); // PORT: §3.3 TryGetValue; values are never null
                if (type != null)
                {
                    if (col.type() == type)
                    {
                        columnList.add(col);
                    }
                    else
                    {
                        columnList.add(new ColumnSymbol(col.name(), type));
                    }

                    nameToTypeMap.remove(col.name());
                }
            }

            for (var col : columnsB)
            {
                var type = nameToTypeMap.get(col.name()); // PORT: §3.3 TryGetValue; values are never null
                if (type != null)
                {
                    if (col.type() == type)
                    {
                        columnList.add(col);
                    }
                    else
                    {
                        columnList.add(new ColumnSymbol(col.name(), type));
                    }

                    nameToTypeMap.remove(col.name());
                }
            }

            return ListExtensions.toReadOnly(columnList); // PORT: §3.5
        }
        finally
        {
            s_nameToTypePool.returnToPool(nameToTypeMap);
            s_columnListPool.returnToPool(columnList);
        }
    }

    /// <summary>
    /// Returns the list of columns that appear in both lists.
    /// The column types are converted to the common type.
    /// </summary>
    public static List<ColumnSymbol> intersect(List<ColumnSymbol> columnsA, List<ColumnSymbol> columnsB)
    {
        var nameToTypeMap = s_nameToTypePool.allocateFromPool();
        var columnList = s_columnListPool.allocateFromPool();
        try
        {
            for (var col : columnsB)
            {
                nameToTypeMap.put(col.name(), col.type());
            }

            for (var col : columnsA)
            {
                var typeB = nameToTypeMap.get(col.name()); // PORT: §3.3 TryGetValue; values are never null
                if (typeB != null)
                {
                    Out<TypeSymbol> commonType = new Out<>(); // PORT: §3.3
                    if (tryGetCommonType(col.type(), typeB, Conversion.Dynamic, commonType))
                    {
                        if (commonType.value == col.type())
                        {
                            columnList.add(col);
                        }
                        else
                        {
                            columnList.add(new ColumnSymbol(col.name(), commonType.value));
                        }
                    }
                    else
                    {
                        columnList.add(new ColumnSymbol(col.name(), ScalarTypes.Dynamic));
                    }
                }
            }

            return ListExtensions.toReadOnly(columnList); // PORT: §3.5
        }
        finally
        {
            s_nameToTypePool.returnToPool(nameToTypeMap);
            s_columnListPool.returnToPool(columnList);
        }
    }
}
