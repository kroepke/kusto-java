// Ported from: src/Kusto.Language/Symbols/ScalarTypes.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import org.graylog.kusto.language.utils.dotnet.DotNet;

// PORT: §2.3 the fields String, Type, Long, Decimal, DateTime, TimeSpan and Guid keep the upstream spelling;
// inside this class the java.lang.String type is always written fully qualified.
// PORT: §3.9 static initialisation runs in C# textual order: every field initialiser below in place, then the
// explicit static constructor as a static {} block after s_typeMap. Forward references would observe null.
public final class ScalarTypes
{
    private ScalarTypes() // PORT: §3.5 static class
    {
    }

    /// <summary>
    /// The dynamic type.
    /// </summary>
    public static final ScalarSymbol Dynamic = 
        DynamicAnySymbol.Instance;

    /// <summary>
    /// The bool type.
    /// </summary>
    public static final ScalarSymbol Bool = 
        new PrimitiveSymbol("bool", new java.lang.String[] { "boolean" }, ScalarFlags.Orderable);

    /// <summary>
    /// The int type.
    /// </summary>
    public static final ScalarSymbol Int = 
        new PrimitiveSymbol("int", new java.lang.String[] { "int32", "uint", "uint32", "int8", "uint8", "int16", "uint16" }, ScalarFlags.Integer | ScalarFlags.Numeric | ScalarFlags.Interval | ScalarFlags.Summable | ScalarFlags.Orderable);

    /// <summary>
    /// The long type.
    /// </summary>
    public static final ScalarSymbol Long = 
        new PrimitiveSymbol("long", new java.lang.String[] { "int64", "ulong", "uint64" }, ScalarFlags.Integer | ScalarFlags.Numeric | ScalarFlags.Interval | ScalarFlags.Summable | ScalarFlags.Orderable, new ScalarSymbol[] { Int });

    /// <summary>
    /// The real type.
    /// </summary>
    public static final ScalarSymbol Real = 
        new PrimitiveSymbol("real", new java.lang.String[] { "double", "float" }, ScalarFlags.Numeric | ScalarFlags.Interval | ScalarFlags.Summable | ScalarFlags.Orderable, new ScalarSymbol[] { Int, Long });

    /// <summary>
    /// The decimal type.
    /// </summary>
    public static final ScalarSymbol Decimal = 
        new PrimitiveSymbol("decimal", null, ScalarFlags.Numeric | ScalarFlags.Interval | ScalarFlags.Summable | ScalarFlags.Orderable, new ScalarSymbol[] { Int, Long, Real });

    /// <summary>
    /// The datetime type.
    /// </summary>
    public static final ScalarSymbol DateTime = 
        new PrimitiveSymbol("datetime", new java.lang.String[] { "date" }, ScalarFlags.Interval | ScalarFlags.Summable | ScalarFlags.Orderable);

    /// <summary>
    /// The timespan type.
    /// </summary>
    public static final ScalarSymbol TimeSpan = 
        new PrimitiveSymbol("timespan", new java.lang.String[] { "time" }, ScalarFlags.Interval | ScalarFlags.Summable | ScalarFlags.Orderable);

    /// <summary>
    /// The guid type.
    /// </summary>
    public static final ScalarSymbol Guid = 
        new PrimitiveSymbol("guid", new java.lang.String[] { "uuid", "uniqueid" });

    /// <summary>
    /// The type of a type literal.
    /// </summary>
    public static final ScalarSymbol Type = 
        new PrimitiveSymbol("type");

    /// <summary>
    /// The string type.
    /// </summary>
    public static final ScalarSymbol String = 
        new PrimitiveSymbol("string", null, ScalarFlags.Orderable, new ScalarSymbol[] { Dynamic }); // PORT: §3.12 named argument → positional

    /// <summary>
    /// The type used for null in dynamic expressions.
    /// </summary>
    public static final ScalarSymbol Null = 
        new PrimitiveSymbol("null", null, ScalarFlags.None);

    /// <summary>
    /// The type used when the type is unknown.
    /// </summary>
    public static final ScalarSymbol Unknown = 
        new PrimitiveSymbol("unknown", null, ScalarFlags.All);

    /// <summary>
    /// A dynamic type that is known to contain a boolean.
    /// </summary>
    public static final ScalarSymbol DynamicBool = 
        new DynamicPrimitiveSymbol(ScalarTypes.Bool);

    /// <summary>
    /// A dynamic type that is known to contain a long.
    /// </summary>
    public static final ScalarSymbol DynamicLong = 
        new DynamicPrimitiveSymbol(ScalarTypes.Long);

    /// <summary>
    /// A dynamic type that is known to contail a real.
    /// </summary>
    public static final ScalarSymbol DynamicReal = 
        new DynamicPrimitiveSymbol(ScalarTypes.Real);

    /// <summary>
    /// A dynamic type that is known to contain a datetime.
    /// </summary>
    public static final ScalarSymbol DynamicDateTime = 
        new DynamicPrimitiveSymbol(ScalarTypes.DateTime);

    /// <summary>
    /// A dynamic type that is known to contain a timespan.
    /// </summary>
    public static final ScalarSymbol DynamicTimeSpan = 
        new DynamicPrimitiveSymbol(ScalarTypes.TimeSpan);

    /// <summary>
    /// A dynamic type that is known to contain a guid.
    /// </summary>
    public static final ScalarSymbol DynamicGuid = 
        new DynamicPrimitiveSymbol(ScalarTypes.Guid);

    /// <summary>
    /// A dynamic type that is known to contain a string.
    /// </summary>
    public static final ScalarSymbol DynamicString = 
        new DynamicPrimitiveSymbol(ScalarTypes.String);

    /// <summary>
    /// A dynamic type that is known to contain a bag of properties (JSON object).
    /// </summary>
    public static final DynamicBagSymbol DynamicBag = 
        DynamicBagSymbol.Empty;

    /// <summary>
    /// A dynamic type that is known to contain an array.
    /// </summary>
    public static final DynamicArraySymbol DynamicArray = 
        new DynamicArraySymbol(ScalarTypes.Dynamic);

    /// <summary>
    /// A dynamic type that is known to contain an array of bool.
    /// </summary>
    public static final DynamicArraySymbol DynamicArrayOfBool = 
        new DynamicArraySymbol(ScalarTypes.Bool);

    /// <summary>
    /// A dynamic type that is known to contain an array of long.
    /// </summary>
    public static final DynamicArraySymbol DynamicArrayOfLong = 
        new DynamicArraySymbol(ScalarTypes.Long);

    /// <summary>
    /// A dynamic type that is known to contain an array of real.
    /// </summary>
    public static final DynamicArraySymbol DynamicArrayOfReal = 
        new DynamicArraySymbol(ScalarTypes.Real);

    /// <summary>
    /// A dynamic type that is known to contain an array of datetime.
    /// </summary>
    public static final DynamicArraySymbol DynamicArrayOfDateTime = 
        new DynamicArraySymbol(ScalarTypes.DateTime);

    /// <summary>
    /// A dynamic type that is known to contain an array of timespan.
    /// </summary>
    public static final DynamicArraySymbol DynamicArrayOfTimeSpan = 
        new DynamicArraySymbol(ScalarTypes.TimeSpan);

    /// <summary>
    /// A dynamic type that is known to contain an array of guid.
    /// </summary>
    public static final DynamicArraySymbol DynamicArrayOfGuid = 
        new DynamicArraySymbol(ScalarTypes.Guid);

    /// <summary>
    /// A dynamic type that is known to contain an array of string.
    /// </summary>
    public static final DynamicArraySymbol DynamicArrayOfString = 
        new DynamicArraySymbol(ScalarTypes.String);

    /// <summary>
    /// A dynamic type that is known to contain an array of arrays.
    /// </summary>
    public static final DynamicArraySymbol DynamicArrayOfArray = 
        new DynamicArraySymbol(ScalarTypes.DynamicArray);

    /// <summary>
    /// A dynamic type that is known to contain an array of property bags (JSON objects).
    /// </summary>
    public static final DynamicArraySymbol DynamicArrayOfBag = 
        new DynamicArraySymbol(ScalarTypes.DynamicBag);

    /// <summary>
    /// A dynamic type that is known to contain an array of arrays of real.
    /// </summary>
    public static final DynamicArraySymbol DynamicArrayOfArrayOfReal = 
        new DynamicArraySymbol(DynamicArrayOfReal);

    /// <summary>
    /// A dynamic type that is known to contain an array of arrays of string.
    /// </summary>
    public static final DynamicArraySymbol DynamicArrayOfArrayOfString = 
        new DynamicArraySymbol(DynamicArrayOfString);

    /// <summary>
    /// The general shape of a geometry returned by a geometric function.
    /// </summary>
    public static final DynamicBagSymbol GeoShape = 
        new DynamicBagSymbol(
            new ColumnSymbol("type", ScalarTypes.String),
            new ColumnSymbol("coordinates", ScalarTypes.DynamicArray));
    /// <summary>
    /// Get the dynamic type for the specified underlying type.
    /// </summary>
    public static TypeSymbol getDynamic(TypeSymbol underlyingType)
    {
        if (underlyingType == null
            || underlyingType == ScalarTypes.Dynamic
            || underlyingType == ScalarTypes.Null
            || underlyingType == ScalarTypes.Unknown)
            return ScalarTypes.Dynamic;
        else if (underlyingType instanceof DynamicSymbol)
            return underlyingType;
        else if (underlyingType == ScalarTypes.Bool)
            return DynamicBool;
        else if (underlyingType == ScalarTypes.Int)
            return DynamicLong;
        else if (underlyingType == ScalarTypes.Long)
            return DynamicLong;
        else if (underlyingType == ScalarTypes.Real)
            return DynamicReal;
        else if (underlyingType == ScalarTypes.DateTime)
            return DynamicDateTime;
        else if (underlyingType == ScalarTypes.TimeSpan)
            return DynamicTimeSpan;
        else if (underlyingType == ScalarTypes.Guid)
            return DynamicGuid;
        else if (underlyingType == ScalarTypes.String)
            return DynamicString;
        else
            return Dynamic;
    }

    /// <summary>
    /// Returns the <see cref="DynamicArraySymbol"/> for a dynamic array of the specified element type.
    /// </summary>
    public static DynamicArraySymbol getDynamicArray(TypeSymbol elementType)
    {
        // dont make arrays of dynamic primivtes
        if (elementType instanceof DynamicPrimitiveSymbol dp)
            elementType = dp.underlyingType();

        if (elementType == null)
            return DynamicArray;
        else if (elementType == ScalarTypes.Bool)
            return DynamicArrayOfBool;
        else if (elementType == ScalarTypes.Int)
            return DynamicArrayOfLong;
        else if (elementType == ScalarTypes.Long)
            return DynamicArrayOfLong;
        else if (elementType == ScalarTypes.Real)
            return DynamicArrayOfReal;
        else if (elementType == ScalarTypes.DateTime)
            return DynamicArrayOfDateTime;
        else if (elementType == ScalarTypes.TimeSpan)
            return DynamicArrayOfTimeSpan;
        else if (elementType == ScalarTypes.Guid)
            return DynamicArrayOfGuid;
        else if (elementType == ScalarTypes.Dynamic)
            return DynamicArray;
        else if (elementType == ScalarTypes.String)
            return DynamicArrayOfString;
        else if (elementType == ScalarTypes.DynamicArray)
            return DynamicArrayOfArray;
        else if (elementType == ScalarTypes.DynamicBag)
            return DynamicArrayOfBag;
        else if (elementType == ScalarTypes.Null)
            return DynamicArray;
        else if (elementType == ScalarTypes.Unknown)
            return DynamicArray;
        else if (elementType == ScalarTypes.DynamicArrayOfReal)
            return DynamicArrayOfArrayOfReal;
        else if (elementType == ScalarTypes.DynamicArrayOfString)
            return DynamicArrayOfArrayOfString;
        else if (elementType instanceof DynamicArraySymbol
            || elementType instanceof DynamicBagSymbol)
            return new DynamicArraySymbol(elementType);
        else
            return DynamicArray;
    }

    /// <summary>
    /// Gets the <see cref="DynamicBagSymbol"/> with the specified properties.
    /// </summary>
    public static DynamicBagSymbol getDynamicBag(
        List<ColumnSymbol> properties)
    {
        if (properties == null || properties.size() == 0)
        {
            return DynamicBag;
        }
        else
        {
            return new DynamicBagSymbol(properties);
        }
    }

    /// <summary>
    /// Gets the <see cref="DynamicBagSymbol"/> with the specified properties.
    /// </summary>
    public static DynamicBagSymbol getDynamicBag(
        ColumnSymbol... properties)
    {
        return getDynamicBag(properties != null ? Arrays.asList(properties) : (List<ColumnSymbol>)null); // PORT: §3.17 array as IReadOnlyList
    }

    /// <summary>
    /// Gets the <see cref="DynamicBagSymbol"/> with the specified properties.
    /// </summary>
    /// <param name="schema">One or more properties specified as: (name: type, ...)</param>
    public static DynamicBagSymbol getDynamicBag(java.lang.String schema)
    {
        return getDynamicBag(TableSymbol.from(schema).columns());
    }

    /// <summary>
    /// Gets the <see cref="TupleSymbol"/> with the specified columns.
    /// </summary>
    public static TupleSymbol getTuple(List<ColumnSymbol> columns)
    {
        return new TupleSymbol(columns);
    }

    /// <summary>
    /// Gets the <see cref="TupleSymbol"/> with the specified columns.
    /// </summary>
    public static TupleSymbol getTuple(ColumnSymbol... columns)
    {
        return new TupleSymbol(columns);
    }

    /// <summary>
    /// Gets the <see cref="TupleSymbol"/> with the specified columns.
    /// </summary>
    /// <param name="schema">One or more columns specified as: (name: type, ...)</param>
    public static TupleSymbol getTuple(java.lang.String schema)
    {
        return getTuple(TableSymbol.from(schema).columns());
    }

    /// <summary>
    /// All known scalar symbols
    /// </summary>
    public static final List<ScalarSymbol> All = Arrays.asList(new ScalarSymbol[] // PORT: §3.17 array as IReadOnlyList
    {
        Bool,
        Int,
        Long,
        Real,
        Decimal,
        String,
        DateTime,
        TimeSpan,
        Guid,
        Type,
        Dynamic,
        Null
    });

    private static final LinkedHashMap<java.lang.String, ScalarSymbol> s_typeMap
        = new LinkedHashMap<java.lang.String, ScalarSymbol>(); // PORT: §3.17 Dictionary → LinkedHashMap

    static // PORT: §3.9 static ctor (ScalarTypes.cs:365)
    {
        for (var type : All)
        {
            DotNet.dictionaryAdd(s_typeMap, type.name(), type); // PORT: §3.17 Dictionary.Add throws on a duplicate key

            for (var alias : type.aliases())
            {
                DotNet.dictionaryAdd(s_typeMap, alias, type); // PORT: §3.17 Dictionary.Add throws on a duplicate key
            }
        }
    }

    /// <summary>
    /// Gets the <see cref="ScalarSymbol"/> associated with the specified type name.
    /// </summary>
    public static ScalarSymbol getSymbol(java.lang.String typeName)
    {
        if (typeName == null)
            throw new NullPointerException("key"); // PORT: §3.16 Dictionary.TryGetValue(null) throws ArgumentNullException
        ScalarSymbol type;
        type = s_typeMap.get(typeName); // PORT: §3.3 TryGetValue; values are never null
        return type;
    }
}
