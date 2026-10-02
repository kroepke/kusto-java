// Ported from: src/Kusto.Language/Operators.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language;

import java.util.Arrays;
import java.util.List;

import org.graylog.kusto.language.symbols.ArgumentKind;
import org.graylog.kusto.language.symbols.CustomReturnTypeContext;
import org.graylog.kusto.language.symbols.OperatorKind;
import org.graylog.kusto.language.symbols.OperatorSymbol;
import org.graylog.kusto.language.symbols.P;
import org.graylog.kusto.language.symbols.Parameter;
import org.graylog.kusto.language.symbols.ParameterTypeKind;
import org.graylog.kusto.language.symbols.ReturnTypeKind;
import org.graylog.kusto.language.symbols.ScalarTypes;
import org.graylog.kusto.language.symbols.Signature;
import org.graylog.kusto.language.symbols.Tabularity;
import org.graylog.kusto.language.symbols.TypeSymbol;

/// <summary>
/// Built-in scalar math-like operators
/// </summary>
public final class Operators // PORT: §3.5 static class → final class with a private constructor
{
    private Operators() { }

    private static final TypeSymbol[] DateAndTimespan = new TypeSymbol[] // PORT: §3.10 new[] spelled out
    {
        ScalarTypes.DateTime,
        ScalarTypes.TimeSpan
    };

    private static final TypeSymbol[] StringOrDynamic = new TypeSymbol[] // PORT: §3.10 new[] spelled out
    {
        ScalarTypes.String,
        ScalarTypes.Dynamic
    };

    private static final TypeSymbol[] DynamicAddable = new TypeSymbol[] // PORT: §3.10 new[] spelled out
    {
        ScalarTypes.Int,
        ScalarTypes.Long,
        ScalarTypes.Real,
        ScalarTypes.Decimal,
        ScalarTypes.TimeSpan,
        ScalarTypes.DateTime
    };

    private static OperatorSymbol stringBinary(OperatorKind kind, boolean dynamicRHS)
    { return (dynamicRHS) // PORT: §3.1 expression body
            ? new OperatorSymbol(kind,
                new Signature(ScalarTypes.Bool,
                    new Parameter("left", ParameterTypeKind.StringOrDynamic),
                    new Parameter("right", ParameterTypeKind.StringOrDynamic)),
                new Signature(ScalarTypes.Bool,
                    new Parameter("left", ParameterTypeKind.StringOrDynamic, ArgumentKind.StarOnly),
                    new Parameter("right", ParameterTypeKind.StringOrDynamic)))
            : new OperatorSymbol(kind,
                new Signature(ScalarTypes.Bool,
                    new Parameter("left", ParameterTypeKind.StringOrDynamic),
                    new Parameter("right", ScalarTypes.String)),
                new Signature(ScalarTypes.Bool,
                    new Parameter("left", ParameterTypeKind.StringOrDynamic, ArgumentKind.StarOnly),
                    new Parameter("right", ScalarTypes.String))); }

    private static OperatorSymbol stringBinary(OperatorKind kind) // PORT: §3.12 dynamicRHS = true
    {
        return stringBinary(kind, true);
    }

    public static final OperatorSymbol UnaryMinus =
        new OperatorSymbol(OperatorKind.UnaryMinus,
                new Signature(Operators::unaryReturnType, Tabularity.Scalar, new Parameter("operand", ParameterTypeKind.Summable)), // PORT: §3.8 method group
                new Signature(ScalarTypes.Dynamic, new Parameter("operand", ScalarTypes.Dynamic)));

    public static final OperatorSymbol UnaryPlus =
        new OperatorSymbol(OperatorKind.UnaryPlus,
            new Signature(Operators::unaryReturnType, Tabularity.Scalar, new Parameter("operand", ParameterTypeKind.Summable)), // PORT: §3.8 method group
            new Signature(ScalarTypes.Dynamic, new Parameter("operand", ScalarTypes.Dynamic)));

    private static TypeSymbol unaryReturnType(CustomReturnTypeContext context)
    {
        // unary operator folds into constants w/o promotion
        return context.arguments().size() == 1
            && context.argumentTypes().get(0) == ScalarTypes.Int
            && !context.arguments().get(0).isConstant()
            ? ScalarTypes.Long
            : context.argumentTypes().get(0);
    }

    public static final OperatorSymbol And =
        new OperatorSymbol(OperatorKind.And,
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Bool), new Parameter("right", ScalarTypes.Bool)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ScalarTypes.Bool)).hide(),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Bool), new Parameter("right", ScalarTypes.Dynamic)).hide(),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ScalarTypes.Dynamic)).hide()
            );

    public static final OperatorSymbol Or =
        new OperatorSymbol(OperatorKind.Or,
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Bool), new Parameter("right", ScalarTypes.Bool)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ScalarTypes.Bool)).hide(),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Bool), new Parameter("right", ScalarTypes.Dynamic)).hide(),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ScalarTypes.Dynamic)).hide()
            );

    public static final OperatorSymbol Add =
        new OperatorSymbol(OperatorKind.Add,
            new Signature(ReturnTypeKind.Widest, new Parameter("left", ParameterTypeKind.Number), new Parameter("right", ParameterTypeKind.Number)),
            new Signature(ScalarTypes.TimeSpan, new Parameter("left", ScalarTypes.TimeSpan), new Parameter("right", ScalarTypes.TimeSpan)),
            new Signature(ScalarTypes.DateTime, new Parameter("left", DateAndTimespan), new Parameter("right", DateAndTimespan)),
            new Signature(ScalarTypes.Dynamic, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ScalarTypes.Dynamic)),
            new Signature(ScalarTypes.Long, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ScalarTypes.Int)),
            new Signature(ScalarTypes.Long, new Parameter("left", ScalarTypes.Int), new Parameter("right", ScalarTypes.Dynamic)),
            new Signature(ReturnTypeKind.Parameter1, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", DynamicAddable)),
            new Signature(ReturnTypeKind.Parameter0, new Parameter("left", DynamicAddable), new Parameter("right", ScalarTypes.Dynamic)));

    public static final OperatorSymbol Subtract =
        new OperatorSymbol(OperatorKind.Subtract,
            new Signature(ReturnTypeKind.Widest, new Parameter("left", ParameterTypeKind.Number), new Parameter("right", ParameterTypeKind.Number)),
            new Signature(ScalarTypes.TimeSpan, new Parameter("left", ScalarTypes.TimeSpan), new Parameter("right", ScalarTypes.TimeSpan)),
            new Signature(ScalarTypes.TimeSpan, new Parameter("left", ScalarTypes.DateTime), new Parameter("right", ScalarTypes.DateTime)),
            new Signature(ScalarTypes.TimeSpan, new Parameter("left", ScalarTypes.DateTime), new Parameter("right", ScalarTypes.Dynamic)),
            new Signature(ScalarTypes.TimeSpan, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ScalarTypes.DateTime)),
            new Signature(ScalarTypes.DateTime, new Parameter("left", ScalarTypes.DateTime), new Parameter("right", ScalarTypes.TimeSpan)),
            new Signature(ScalarTypes.DateTime, new Parameter("left", ScalarTypes.TimeSpan), new Parameter("right", ScalarTypes.DateTime)),
            new Signature(ScalarTypes.Dynamic, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ScalarTypes.Dynamic)),
            new Signature(ScalarTypes.Long, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ScalarTypes.Int)),
            new Signature(ScalarTypes.Long, new Parameter("left", ScalarTypes.Int), new Parameter("right", ScalarTypes.Dynamic)),
            new Signature(ReturnTypeKind.Parameter1, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", DynamicAddable)),
            new Signature(ReturnTypeKind.Parameter0, new Parameter("left", DynamicAddable), new Parameter("right", ScalarTypes.Dynamic)));

    public static final OperatorSymbol Multiply =
        new OperatorSymbol(OperatorKind.Multiply,
            new Signature(ReturnTypeKind.Widest, new Parameter("left", ParameterTypeKind.Number), new Parameter("right", ParameterTypeKind.Number)),
            new Signature(ScalarTypes.TimeSpan, new Parameter("left", ScalarTypes.TimeSpan), new Parameter("right", ScalarTypes.TimeSpan)),
            new Signature(ScalarTypes.TimeSpan, new Parameter("left", ScalarTypes.TimeSpan), new Parameter("right", ParameterTypeKind.Number)),
            new Signature(ScalarTypes.TimeSpan, new Parameter("left", ParameterTypeKind.Number), new Parameter("right", ScalarTypes.TimeSpan)),
            new Signature(ScalarTypes.DateTime, new Parameter("left", ScalarTypes.DateTime), new Parameter("right", ParameterTypeKind.Number)),
            new Signature(ScalarTypes.DateTime, new Parameter("left", ParameterTypeKind.Number), new Parameter("right", ScalarTypes.DateTime)),
            new Signature(ScalarTypes.Dynamic, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ScalarTypes.Dynamic)),
            new Signature(ScalarTypes.Long, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ScalarTypes.Int)),
            new Signature(ScalarTypes.Long, new Parameter("left", ScalarTypes.Int), new Parameter("right", ScalarTypes.Dynamic)),
            new Signature(ReturnTypeKind.Parameter1, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ParameterTypeKind.Number)),
            new Signature(ReturnTypeKind.Parameter0, new Parameter("left", ParameterTypeKind.Number), new Parameter("right", ScalarTypes.Dynamic)));

    public static final OperatorSymbol Divide =
        new OperatorSymbol(OperatorKind.Divide,
            new Signature(ReturnTypeKind.Widest, new Parameter("left", ParameterTypeKind.Number), new Parameter("right", ParameterTypeKind.Number)),
            new Signature(ScalarTypes.Real, new Parameter("left", ScalarTypes.TimeSpan), new Parameter("right", ScalarTypes.TimeSpan)),
            new Signature(ScalarTypes.TimeSpan, new Parameter("left", ScalarTypes.TimeSpan), new Parameter("right", ParameterTypeKind.Number)),
            new Signature(ScalarTypes.Real, new Parameter("left", ScalarTypes.DateTime), new Parameter("right", ScalarTypes.DateTime)),
            new Signature(ScalarTypes.Real, new Parameter("left", ScalarTypes.DateTime), new Parameter("right", ScalarTypes.TimeSpan)),
            new Signature(ScalarTypes.DateTime, new Parameter("left", ScalarTypes.DateTime), new Parameter("right", ParameterTypeKind.Number)),
            new Signature(ScalarTypes.Dynamic, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ScalarTypes.Dynamic)),
            new Signature(ScalarTypes.Long, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ScalarTypes.Int)),
            new Signature(ScalarTypes.Long, new Parameter("left", ScalarTypes.Int), new Parameter("right", ScalarTypes.Dynamic)),
            new Signature(ReturnTypeKind.Parameter1, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ParameterTypeKind.Number)),
            new Signature(ReturnTypeKind.Parameter0, new Parameter("left", ParameterTypeKind.Number), new Parameter("right", ScalarTypes.Dynamic)));

    public static final OperatorSymbol Modulo =
        new OperatorSymbol(OperatorKind.Modulo,
            new Signature(ReturnTypeKind.Widest, new Parameter("left", ParameterTypeKind.Number), new Parameter("right", ParameterTypeKind.Number)),
            new Signature(ScalarTypes.TimeSpan, new Parameter("left", ScalarTypes.TimeSpan), new Parameter("right", ScalarTypes.TimeSpan)),
            new Signature(ScalarTypes.TimeSpan, new Parameter("left", ScalarTypes.TimeSpan), new Parameter("right", ParameterTypeKind.Number)),
            new Signature(ScalarTypes.TimeSpan, new Parameter("left", ScalarTypes.DateTime), new Parameter("right", ScalarTypes.DateTime)),
            new Signature(ScalarTypes.TimeSpan, new Parameter("left", ScalarTypes.DateTime), new Parameter("right", ScalarTypes.TimeSpan)),
            new Signature(ScalarTypes.DateTime, new Parameter("left", ScalarTypes.DateTime), new Parameter("right", ParameterTypeKind.Number)),
            new Signature(ScalarTypes.Dynamic, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ScalarTypes.Dynamic)),
            new Signature(ScalarTypes.Long, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ScalarTypes.Int)),
            new Signature(ScalarTypes.Long, new Parameter("left", ScalarTypes.Int), new Parameter("right", ScalarTypes.Dynamic)),
            new Signature(ReturnTypeKind.Parameter1, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ParameterTypeKind.Number)),
            new Signature(ReturnTypeKind.Parameter0, new Parameter("left", ParameterTypeKind.Number), new Parameter("right", ScalarTypes.Dynamic)));

    public static final OperatorSymbol LessThan =
        new OperatorSymbol(OperatorKind.LessThan,
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Bool), new Parameter("right", ParameterTypeKind.NumberOrBool)).hide(), // hide bool < ???
            new Signature(ScalarTypes.Bool, new Parameter("left", ParameterTypeKind.Number), new Parameter("right", ParameterTypeKind.NumberOrBool)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.TimeSpan), new Parameter("right", ScalarTypes.TimeSpan)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.DateTime), new Parameter("right", ScalarTypes.DateTime)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ScalarTypes.Dynamic)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ParameterTypeKind.Number)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ParameterTypeKind.Number), new Parameter("right", ScalarTypes.Dynamic)));

    public static final OperatorSymbol LessThanOrEqual =
        new OperatorSymbol(OperatorKind.LessThanOrEqual,
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Bool), new Parameter("right", ParameterTypeKind.NumberOrBool)).hide(), // hide bool <= ???
            new Signature(ScalarTypes.Bool, new Parameter("left", ParameterTypeKind.Number), new Parameter("right", ParameterTypeKind.NumberOrBool)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.TimeSpan), new Parameter("right", ScalarTypes.TimeSpan)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.DateTime), new Parameter("right", ScalarTypes.DateTime)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ScalarTypes.Dynamic)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ParameterTypeKind.Number)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ParameterTypeKind.Number), new Parameter("right", ScalarTypes.Dynamic)));

    public static final OperatorSymbol GreaterThan =
        new OperatorSymbol(OperatorKind.GreaterThan,
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Bool), new Parameter("right", ParameterTypeKind.NumberOrBool)).hide(), // hide bool > ???
            new Signature(ScalarTypes.Bool, new Parameter("left", ParameterTypeKind.Number), new Parameter("right", ParameterTypeKind.NumberOrBool)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.TimeSpan), new Parameter("right", ScalarTypes.TimeSpan)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.DateTime), new Parameter("right", ScalarTypes.DateTime)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ScalarTypes.Dynamic)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ParameterTypeKind.Number)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ParameterTypeKind.Number), new Parameter("right", ScalarTypes.Dynamic)));

    public static final OperatorSymbol GreaterThanOrEqual =
        new OperatorSymbol(OperatorKind.GreaterThanOrEqual,
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Bool), new Parameter("right", ParameterTypeKind.NumberOrBool)).hide(), // hide bool >= ???
            new Signature(ScalarTypes.Bool, new Parameter("left", ParameterTypeKind.Number), new Parameter("right", ParameterTypeKind.NumberOrBool)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.TimeSpan), new Parameter("right", ScalarTypes.TimeSpan)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.DateTime), new Parameter("right", ScalarTypes.DateTime)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ScalarTypes.Dynamic)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Dynamic), new Parameter("right", ParameterTypeKind.Number)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ParameterTypeKind.Number), new Parameter("right", ScalarTypes.Dynamic)));

    public static final OperatorSymbol Equal =
        new OperatorSymbol(OperatorKind.Equal,
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Bool), new Parameter("right", ParameterTypeKind.Scalar)).hide(), // hide bool == ??
            new Signature(ScalarTypes.Bool, new Parameter("left", ParameterTypeKind.NotBool), new Parameter("right", ParameterTypeKind.Scalar)),
            new Signature(ScalarTypes.Bool, new Parameter("left", ParameterTypeKind.NotBool, ArgumentKind.StarOnly), new Parameter("right", ParameterTypeKind.Scalar)));

    public static final OperatorSymbol NotEqual =
        new OperatorSymbol(OperatorKind.NotEqual,
            new Signature(ScalarTypes.Bool, new Parameter("left", ScalarTypes.Bool), new Parameter("right", ParameterTypeKind.Scalar)).hide(), // hide bool != ??
            new Signature(ScalarTypes.Bool, new Parameter("left", ParameterTypeKind.NotBool), new Parameter("right", ParameterTypeKind.Scalar)));

    public static final OperatorSymbol EqualTilde =
        stringBinary(OperatorKind.EqualTilde);

    public static final OperatorSymbol BangTilde =
        stringBinary(OperatorKind.BangTilde);

    public static final OperatorSymbol Has =
        stringBinary(OperatorKind.Has, false); // PORT: §3.12 named argument

    public static final OperatorSymbol HasCs =
        stringBinary(OperatorKind.HasCs, false); // PORT: §3.12 named argument

    public static final OperatorSymbol NotHas =
        stringBinary(OperatorKind.NotHas, false); // PORT: §3.12 named argument

    public static final OperatorSymbol NotHasCs =
        stringBinary(OperatorKind.NotHasCs, false); // PORT: §3.12 named argument

    public static final OperatorSymbol HasPrefix =
        stringBinary(OperatorKind.HasPrefix, false); // PORT: §3.12 named argument

    public static final OperatorSymbol HasPrefixCs =
        stringBinary(OperatorKind.HasPrefixCs, false); // PORT: §3.12 named argument

    public static final OperatorSymbol NotHasPrefix =
        stringBinary(OperatorKind.NotHasPrefix, false); // PORT: §3.12 named argument

    public static final OperatorSymbol NotHasPrefixCs =
        stringBinary(OperatorKind.NotHasPrefixCs, false); // PORT: §3.12 named argument

    public static final OperatorSymbol HasSuffix =
        stringBinary(OperatorKind.HasSuffix, false); // PORT: §3.12 named argument

    public static final OperatorSymbol HasSuffixCs =
        stringBinary(OperatorKind.HasSuffixCs, false); // PORT: §3.12 named argument

    public static final OperatorSymbol NotHasSuffix =
        stringBinary(OperatorKind.NotHasSuffix, false); // PORT: §3.12 named argument

    public static final OperatorSymbol NotHasSuffixCs =
        stringBinary(OperatorKind.NotHasSuffixCs, false); // PORT: §3.12 named argument

    public static final OperatorSymbol Like =
        stringBinary(OperatorKind.Like);

    public static final OperatorSymbol LikeCs =
        stringBinary(OperatorKind.LikeCs);

    public static final OperatorSymbol NotLike =
        stringBinary(OperatorKind.NotLike);

    public static final OperatorSymbol NotLikeCs =
        stringBinary(OperatorKind.NotLikeCs);

    public static final OperatorSymbol Contains =
        stringBinary(OperatorKind.Contains);

    public static final OperatorSymbol ContainsCs =
        stringBinary(OperatorKind.ContainsCs);

    public static final OperatorSymbol NotContains =
        stringBinary(OperatorKind.NotContains);

    public static final OperatorSymbol NotContainsCs =
        stringBinary(OperatorKind.NotContainsCs);

    public static final OperatorSymbol StartsWith =
        stringBinary(OperatorKind.StartsWith);

    public static final OperatorSymbol StartsWithCs =
        stringBinary(OperatorKind.StartsWithCs);

    public static final OperatorSymbol NotStartsWith =
        stringBinary(OperatorKind.NotStartsWith);

    public static final OperatorSymbol NotStartsWithCs =
        stringBinary(OperatorKind.NotStartsWithCs);

    public static final OperatorSymbol EndsWith =
        stringBinary(OperatorKind.EndsWith);

    public static final OperatorSymbol EndsWithCs =
        stringBinary(OperatorKind.EndsWithCs);

    public static final OperatorSymbol NotEndsWith =
        stringBinary(OperatorKind.NotEndsWith);

    public static final OperatorSymbol NotEndsWithCs =
        stringBinary(OperatorKind.NotEndsWithCs);

    public static final OperatorSymbol MatchRegex =
        stringBinary(OperatorKind.MatchRegex);

    public static final OperatorSymbol Search =
        stringBinary(OperatorKind.Search);

    public static final OperatorSymbol In =
        new OperatorSymbol(OperatorKind.In,
                new Signature(ScalarTypes.Bool, new Parameter("value", ScalarTypes.Bool), new Parameter("table", ParameterTypeKind.Tabular)).hide(),
                new Signature(ScalarTypes.Bool, new Parameter("value", ParameterTypeKind.NotBool), new Parameter("table", ParameterTypeKind.Tabular)),
                new Signature(ScalarTypes.Bool, new Parameter("value", ScalarTypes.Bool), new Parameter("value", ParameterTypeKind.Scalar, P.maxOccurring(Short.MAX_VALUE))).hide(), // hide bool in (bools) // PORT: §3.12
                new Signature(ScalarTypes.Bool, new Parameter("value", ParameterTypeKind.NotBool), new Parameter("value", ParameterTypeKind.Scalar, P.maxOccurring(Short.MAX_VALUE)))); // PORT: §3.12

    public static final OperatorSymbol HasAny =
        new OperatorSymbol(OperatorKind.HasAny,
            new Signature(ScalarTypes.Bool, new Parameter("value", StringOrDynamic), new Parameter("table", ParameterTypeKind.Tabular)),
            new Signature(ScalarTypes.Bool, new Parameter("value", StringOrDynamic), new Parameter("value", ParameterTypeKind.Scalar, P.maxOccurring(Short.MAX_VALUE)))); // PORT: §3.12

    public static final OperatorSymbol HasAll =
       new OperatorSymbol(OperatorKind.HasAll,
           new Signature(ScalarTypes.Bool, new Parameter("value", StringOrDynamic), new Parameter("table", ParameterTypeKind.Tabular)),
           new Signature(ScalarTypes.Bool, new Parameter("value", StringOrDynamic), new Parameter("value", ParameterTypeKind.Scalar, P.maxOccurring(Short.MAX_VALUE)))); // PORT: §3.12

    public static final OperatorSymbol InCs =
        new OperatorSymbol(OperatorKind.InCs,
            new Signature(ScalarTypes.Bool, new Parameter("value", StringOrDynamic), new Parameter("table", ParameterTypeKind.Tabular)),
            new Signature(ScalarTypes.Bool, new Parameter("value", StringOrDynamic), new Parameter("value", ParameterTypeKind.Scalar, P.maxOccurring(Short.MAX_VALUE)))); // PORT: §3.12

    public static final OperatorSymbol NotIn =
        new OperatorSymbol(OperatorKind.NotIn,
            new Signature(ScalarTypes.Bool, new Parameter("value", ScalarTypes.Bool), new Parameter("table", ParameterTypeKind.Tabular)).hide(),
            new Signature(ScalarTypes.Bool, new Parameter("value", ParameterTypeKind.NotBool), new Parameter("table", ParameterTypeKind.Tabular)),
            new Signature(ScalarTypes.Bool, new Parameter("value", ScalarTypes.Bool), new Parameter("value", ParameterTypeKind.Scalar, P.maxOccurring(Short.MAX_VALUE))).hide(), // hide bool in (bools) // PORT: §3.12
            new Signature(ScalarTypes.Bool, new Parameter("value", ParameterTypeKind.NotBool), new Parameter("value", ParameterTypeKind.Scalar, P.maxOccurring(Short.MAX_VALUE)))); // PORT: §3.12

    public static final OperatorSymbol NotInCs =
        new OperatorSymbol(OperatorKind.NotInCs,
            new Signature(ScalarTypes.Bool, new Parameter("value", StringOrDynamic), new Parameter("table", ParameterTypeKind.Tabular)),
            new Signature(ScalarTypes.Bool, new Parameter("value", StringOrDynamic), new Parameter("value", ParameterTypeKind.Scalar, P.maxOccurring(Short.MAX_VALUE)))); // PORT: §3.12

    public static final OperatorSymbol Between =
        new OperatorSymbol(OperatorKind.Between,
            new Signature(ScalarTypes.Bool, new Parameter("value", ParameterTypeKind.Number), new Parameter("start", ParameterTypeKind.Number), new Parameter("end", ParameterTypeKind.Number)),
            new Signature(ScalarTypes.Bool, new Parameter("value", ParameterTypeKind.Summable), new Parameter("start", ParameterTypeKind.Parameter0), new Parameter("end", ParameterTypeKind.Parameter0)),
            new Signature(ScalarTypes.Bool, new Parameter("value", ScalarTypes.DateTime), new Parameter("start", ScalarTypes.DateTime), new Parameter("end", ScalarTypes.TimeSpan)),
            new Signature(ScalarTypes.Bool, new Parameter("value", ParameterTypeKind.Number), new Parameter("start", ScalarTypes.Dynamic), new Parameter("end", ParameterTypeKind.Parameter1)),
            new Signature(ScalarTypes.Bool, new Parameter("value", ScalarTypes.Dynamic), new Parameter("start", ParameterTypeKind.Number), new Parameter("end", ParameterTypeKind.Parameter1)));

    public static final OperatorSymbol NotBetween =
        new OperatorSymbol(OperatorKind.NotBetween,
            new Signature(ScalarTypes.Bool, new Parameter("value", ParameterTypeKind.Number), new Parameter("start", ParameterTypeKind.Number), new Parameter("end", ParameterTypeKind.Number)),
            new Signature(ScalarTypes.Bool, new Parameter("value", ParameterTypeKind.Summable), new Parameter("start", ParameterTypeKind.Parameter0), new Parameter("end", ParameterTypeKind.Parameter0)),
            new Signature(ScalarTypes.Bool, new Parameter("value", ScalarTypes.DateTime), new Parameter("start", ScalarTypes.DateTime), new Parameter("end", ScalarTypes.TimeSpan)),
            new Signature(ScalarTypes.Bool, new Parameter("value", ParameterTypeKind.Number), new Parameter("start", ScalarTypes.Dynamic), new Parameter("end", ParameterTypeKind.Parameter1)),
            new Signature(ScalarTypes.Bool, new Parameter("value", ScalarTypes.Dynamic), new Parameter("start", ParameterTypeKind.Number), new Parameter("end", ParameterTypeKind.Parameter1)));

    public static final List<OperatorSymbol> All = Arrays.asList(new OperatorSymbol[] // PORT: §3.1 static get-only auto-property { get; } = … → public static final field (no setter, initialised once; matches Functions.All); §3.17 array as IReadOnlyList
    {
        // unary
        UnaryMinus,
        UnaryPlus,

        // binary
        And,
        Or,
        Add,
        Subtract,
        Multiply,
        Divide,
        Modulo,
        LessThan,
        LessThanOrEqual,
        GreaterThan,
        GreaterThanOrEqual,
        Equal,
        NotEqual,

        // string binary operators
        EqualTilde,
        BangTilde,
        Has,
        HasCs,
        NotHas,
        NotHasCs,
        HasPrefix,
        HasPrefixCs,
        NotHasPrefix,
        NotHasPrefixCs,
        HasSuffix,
        HasSuffixCs,
        NotHasSuffix,
        NotHasSuffixCs,
        Like,
        LikeCs,
        NotLike,
        NotLikeCs,
        Contains,
        ContainsCs,
        NotContains,
        NotContainsCs,
        StartsWith,
        StartsWithCs,
        NotStartsWith,
        NotStartsWithCs,
        EndsWith,
        EndsWithCs,
        NotEndsWith,
        NotEndsWithCs,
        MatchRegex,
        Search,

        // N-ary operators
        In,
        InCs,
        NotIn,
        NotInCs,
        Between,
        NotBetween,
        HasAny,
        HasAll,
    });
}
