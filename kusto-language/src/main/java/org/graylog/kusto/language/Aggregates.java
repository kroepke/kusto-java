// Ported from: src/Kusto.Language/Aggregates.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language;

import static org.graylog.kusto.language.FunctionHelpers.*; // PORT: §3.5 using static FunctionHelpers

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import org.graylog.kusto.language.binding.Binder;
import org.graylog.kusto.language.symbols.ArgumentKind;
import org.graylog.kusto.language.symbols.ColumnSymbol;
import org.graylog.kusto.language.symbols.CustomReturnType;
import org.graylog.kusto.language.symbols.CustomReturnTypeContext;
import org.graylog.kusto.language.symbols.FunctionSymbol;
import org.graylog.kusto.language.symbols.P;
import org.graylog.kusto.language.symbols.Parameter;
import org.graylog.kusto.language.symbols.ParameterTypeKind;
import org.graylog.kusto.language.symbols.ResultNameKind;
import org.graylog.kusto.language.symbols.ReturnTypeKind;
import org.graylog.kusto.language.symbols.ScalarTypes;
import org.graylog.kusto.language.symbols.Signature;
import org.graylog.kusto.language.symbols.Tabularity;
import org.graylog.kusto.language.symbols.TupleSymbol;
import org.graylog.kusto.language.symbols.TypeSymbol;
import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.syntax.SimpleNamedExpression;
import org.graylog.kusto.language.syntax.StarExpression;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Linq;

/// <summary>
/// Well known aggregates
/// </summary>
public final class Aggregates // PORT: §3.5 static class → final class with a private constructor
{
    private Aggregates() { }

    public static final FunctionSymbol Sum =
        new FunctionSymbol("sum", ReturnTypeKind.Parameter0Promoted,
            new Parameter("expr", ParameterTypeKind.Summable))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("sum");

    public static final FunctionSymbol SumIf =
        new FunctionSymbol("sumif", ReturnTypeKind.Parameter0Promoted,
            new Parameter("expr", ParameterTypeKind.Summable),
            new Parameter("predicate", ScalarTypes.Bool))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("sumif");

    public static final FunctionSymbol Cnt =
        new FunctionSymbol("cnt", ScalarTypes.Long)
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("cnt")
        .obsolete("count")
        .hide(); // legacy

    public static final FunctionSymbol Count =
        new FunctionSymbol("count",
             new Signature(ScalarTypes.Long),
             new Signature(ScalarTypes.Long,
                new Parameter("predicate", ScalarTypes.Bool))
                .hide()
                .obsolete("countif"))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("count");

    public static final FunctionSymbol CountIf =
        new FunctionSymbol("countif", ScalarTypes.Long,
            new Parameter("predicate", ScalarTypes.Bool))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("countif");

    public static final FunctionSymbol DCount =
        new FunctionSymbol("dcount", ScalarTypes.Long,
            new Parameter("expr", ParameterTypeKind.Scalar),
            new Parameter("accuracy", ParameterTypeKind.NotDynamic, P.minOccurring(0))) // PORT: §3.12
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("dcount");

    public static final FunctionSymbol DCountIf =
        new FunctionSymbol("dcountif", ScalarTypes.Long,
            new Parameter("expr", ParameterTypeKind.Scalar),
            new Parameter("predicate", ScalarTypes.Bool),
            new Parameter("accuracy", ParameterTypeKind.NotDynamic, P.minOccurring(0))) // PORT: §3.12
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("dcountif");

    public static final FunctionSymbol TDigest =
        new FunctionSymbol("tdigest",
            ScalarTypes.DynamicArray,
            new Parameter("expr", ParameterTypeKind.Summable),
            new Parameter("weight", ParameterTypeKind.Integer, P.minOccurring(0))) // PORT: §3.12
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("tdigest");

    public static final FunctionSymbol TDigestMerge =
        new FunctionSymbol("tdigest_merge",
            ScalarTypes.DynamicArray,
            new Parameter("tdigest", ParameterTypeKind.DynamicArray))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("merge_tdigests");

    public static final FunctionSymbol MergeTDigest =
        new FunctionSymbol("merge_tdigest",
            ScalarTypes.DynamicArray,
            new Parameter("tdigest", ParameterTypeKind.DynamicArray))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("merge_tdigests");

    public static final FunctionSymbol Hll =
        new FunctionSymbol("hll",
            ScalarTypes.DynamicArray,
            new Parameter("expr", ParameterTypeKind.NotRealOrBool),
            new Parameter("accuracy", ParameterTypeKind.Integer, P.minOccurring(0))) // PORT: §3.12
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("hll");

    public static final FunctionSymbol HllIf =
        new FunctionSymbol("hll_if",
            ScalarTypes.DynamicArray,
            new Parameter("expr", ParameterTypeKind.NotRealOrBool),
            new Parameter("predicate", ScalarTypes.Bool),
            new Parameter("accuracy", ParameterTypeKind.Integer, P.minOccurring(0))) // PORT: §3.12
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("hll_if");

    public static final FunctionSymbol HllMerge =
        new FunctionSymbol("hll_merge",
            ScalarTypes.DynamicArray,
            new Parameter("hll", ParameterTypeKind.DynamicArray))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("hll_merge");

    public static final FunctionSymbol Min =
        new FunctionSymbol("min", ReturnTypeKind.Parameter0,
            new Parameter("expr", ParameterTypeKind.Orderable))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("min");

    public static final FunctionSymbol MinIf =
        new FunctionSymbol("minif", ReturnTypeKind.Parameter0,
            new Parameter("expr", ParameterTypeKind.Orderable),
            new Parameter("predicate", ScalarTypes.Bool))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("minif");

    public static final FunctionSymbol Max =
        new FunctionSymbol("max", ReturnTypeKind.Parameter0,
           new Parameter("expr", ParameterTypeKind.Orderable))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("max");

    public static final FunctionSymbol MaxIf =
        new FunctionSymbol("maxif", ReturnTypeKind.Parameter0,
            new Parameter("expr", ParameterTypeKind.Orderable),
            new Parameter("predicate", ScalarTypes.Bool))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("maxif");

    public static final FunctionSymbol Avg =
        new FunctionSymbol("avg",
            new Signature(ScalarTypes.Real,
                new Parameter("expr", ParameterTypeKind.Integer)),
            new Signature(ScalarTypes.Real,
                new Parameter("expr", ScalarTypes.Real)),
            new Signature(ScalarTypes.Decimal,
                new Parameter("expr", ScalarTypes.Decimal)),
            new Signature(ScalarTypes.TimeSpan,
                new Parameter("expr", ScalarTypes.TimeSpan)),
            new Signature(ScalarTypes.DateTime,
                new Parameter("expr", ScalarTypes.DateTime)))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("avg");

    public static final FunctionSymbol AvgIf =
        new FunctionSymbol("avgif",
            new Signature(ScalarTypes.Real,
                new Parameter("expr", ParameterTypeKind.Integer),
                new Parameter("predicate", ScalarTypes.Bool)),
            new Signature(ScalarTypes.Real,
                new Parameter("expr", ScalarTypes.Real),
                new Parameter("predicate", ScalarTypes.Bool)),
            new Signature(ScalarTypes.Decimal,
                new Parameter("expr", ScalarTypes.Decimal),
                new Parameter("predicate", ScalarTypes.Bool)),
            new Signature(ScalarTypes.TimeSpan,
                new Parameter("expr", ScalarTypes.TimeSpan),
                new Parameter("predicate", ScalarTypes.Bool)),
            new Signature(ScalarTypes.DateTime,
                new Parameter("expr", ScalarTypes.DateTime),
                new Parameter("predicate", ScalarTypes.Bool)))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("avgif");

    public static final FunctionSymbol MakeList_Deprecated =
        new FunctionSymbol("makelist",
            ReturnTypeKind.Parameter0Array,
            new Parameter("expr", ParameterTypeKind.Scalar),
            new Parameter("maxSize", ParameterTypeKind.Integer, P.minOccurring(0))) // PORT: §3.12
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("list")
        .obsolete("make_list")
        .hide();

    public static final FunctionSymbol MakeList =
        new FunctionSymbol("make_list",
            ReturnTypeKind.Parameter0Array,
            new Parameter("expr", ParameterTypeKind.Scalar),
            new Parameter("maxSize", ParameterTypeKind.Integer, P.minOccurring(0))) // PORT: §3.12
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("list");

    public static final FunctionSymbol MakeListIf =
        new FunctionSymbol("make_list_if",
            ReturnTypeKind.Parameter0Array,
            new Parameter("expr", ParameterTypeKind.Scalar),
            new Parameter("predicate", ScalarTypes.Bool),
            new Parameter("maxSize", ParameterTypeKind.Integer, P.minOccurring(0))) // PORT: §3.12
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("list");

    public static final FunctionSymbol MakeListWithNulls =
        new FunctionSymbol("make_list_with_nulls",
            ReturnTypeKind.Parameter0Array,
            new Parameter("expr", ParameterTypeKind.Scalar))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("list");

    public static final FunctionSymbol MakeSet_Deprecated =
        new FunctionSymbol("makeset",
            ReturnTypeKind.Parameter0Array,
            new Parameter("expr", ParameterTypeKind.Scalar),
            new Parameter("maxSize", ParameterTypeKind.Integer, P.minOccurring(0))) // PORT: §3.12
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("set")
        .obsolete("make_set")
        .hide();

    public static final FunctionSymbol MakeSet =
        new FunctionSymbol("make_set",
            ReturnTypeKind.Parameter0Array,
            new Parameter("expr", ParameterTypeKind.Scalar),
            new Parameter("maxSize", ParameterTypeKind.Integer, P.minOccurring(0))) // PORT: §3.12
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("set");

    public static final FunctionSymbol MakeSetIf =
        new FunctionSymbol("make_set_if",
            ReturnTypeKind.Parameter0Array,
            new Parameter("expr", ParameterTypeKind.Scalar),
            new Parameter("predicate", ScalarTypes.Bool),
            new Parameter("maxSize", ParameterTypeKind.Integer, P.minOccurring(0))) // PORT: §3.12
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("set");

    public static final FunctionSymbol Passthrough =
       new FunctionSymbol("passthrough", ReturnTypeKind.Parameter0,
           new Parameter("expr", ParameterTypeKind.Scalar))
       .withResultNameKind(ResultNameKind.FirstArgument)
       .hide();

    public static final FunctionSymbol MakeDictionary =
        new FunctionSymbol("make_dictionary",
            ScalarTypes.DynamicBag,
            new Parameter("expr", ParameterTypeKind.DynamicBag),
            new Parameter("maxSize", ParameterTypeKind.Integer, P.minOccurring(0))) // PORT: §3.12
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("dictionary")
        .hide();

    public static final FunctionSymbol MakeBag =
        new FunctionSymbol("make_bag",
            ScalarTypes.DynamicBag,
            new Parameter("expr", ParameterTypeKind.DynamicBag),
            new Parameter("maxSize", ParameterTypeKind.Integer, P.minOccurring(0))) // PORT: §3.12
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("bag");

    public static final FunctionSymbol MakeBagIf =
        new FunctionSymbol("make_bag_if",
            ScalarTypes.DynamicBag,
            new Parameter("expr", ParameterTypeKind.DynamicBag),
            new Parameter("predicate", ScalarTypes.Bool),
            new Parameter("maxSize", ParameterTypeKind.Integer, P.minOccurring(0))) // PORT: §3.12
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("bag");

    public static final FunctionSymbol BuildSchema =
        new FunctionSymbol("buildschema",
            ScalarTypes.DynamicBag,
            new Parameter("expr", ParameterTypeKind.DynamicBag))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("schema");

    public static final FunctionSymbol BinaryAllOr =
       new FunctionSymbol("binary_all_or",
           new Signature(ReturnTypeKind.Parameter0,
               new Parameter("expr", ParameterTypeKind.Summable)))
        .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol BinaryAllAnd =
      new FunctionSymbol("binary_all_and",
          new Signature(ReturnTypeKind.Parameter0,
              new Parameter("expr", ParameterTypeKind.Summable)))
      .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol BinaryAllXor =
      new FunctionSymbol("binary_all_xor",
          new Signature(ReturnTypeKind.Parameter0,
              new Parameter("expr", ParameterTypeKind.Summable)))
      .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol CountDistinct =
        new FunctionSymbol("count_distinct", ScalarTypes.Long,
            new Parameter("expr", ParameterTypeKind.NotDynamic))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("count_distinct")
        .withOptimizedAlternative("dcount");

    public static final FunctionSymbol CountDistinctIf =
        new FunctionSymbol("count_distinctif", ScalarTypes.Long,
            new Parameter("expr", ParameterTypeKind.NotDynamic),
            new Parameter("predicate", ScalarTypes.Bool))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("count_distinctif")
        .withOptimizedAlternative("dcountif");

    private static void addPercentileColumns(
        List<ColumnSymbol> columns, CustomReturnTypeContext context, String valueParameterName, String percentileParameterName)
    {
        if (context.getArgument(valueParameterName) instanceof Expression valueArg
            && context.getResultName(valueArg) instanceof String valueArgName)
        {
            var resultType = valueArg.resultType();
            if (resultType == ScalarTypes.Int)
                resultType = ScalarTypes.Long;
            else if (resultType == ScalarTypes.Decimal)
                resultType = ScalarTypes.Real;

            for (var percentileArg : context.getArguments(percentileParameterName))
            {
                var percentileFragment = makeValidNameFragment(getConstantValue(percentileArg));
                var name = percentileParameterName + "_" + valueArgName + "_" + percentileFragment;
                columns.add(new ColumnSymbol(name, resultType, null, null, valueArg)); // PORT: §3.12 source:
            }
        }
    }

    private static CustomReturnType PercentileReturn = context -> // PORT: §3.8
    {
        var cols = new ArrayList<ColumnSymbol>();
        addPercentileColumns(cols, context, "expr", "percentile");
        return new TupleSymbol(cols);
    };

    private static CustomReturnType PercentileArrayReturn = context -> // PORT: §3.8
    {
        var cols = new ArrayList<ColumnSymbol>();

        if (context.getArgument("expr") instanceof Expression valueArg
            && context.getResultName(valueArg) instanceof String valueArgName)
        {
            cols.add(new ColumnSymbol("percentiles_" + valueArgName, ScalarTypes.DynamicArray, null, null, valueArg)); // PORT: §3.12 source:
        }

        return new TupleSymbol(cols);
    };

    public static final FunctionSymbol Percentile =
        new FunctionSymbol("percentile",
            PercentileReturn,
            Tabularity.Scalar,
            new Parameter("expr", ParameterTypeKind.Scalar),
            new Parameter("percentile", ParameterTypeKind.Number));

    public static final FunctionSymbol Percentiles =
        new FunctionSymbol("percentiles",
            PercentileReturn,
            Tabularity.Scalar,
            new Parameter("expr", ParameterTypeKind.Scalar),
            new Parameter("percentile", ParameterTypeKind.Number, P.minOccurring(1), P.maxOccurring(MaxRepeat))); // PORT: §3.12

    public static final FunctionSymbol PercentilesArray =
        new FunctionSymbol("percentiles_array",
            new Signature(
                PercentileArrayReturn,
                Tabularity.Scalar,
                new Parameter("expr", ParameterTypeKind.Scalar),
                new Parameter("percentile", ParameterTypeKind.Number, P.minOccurring(1), P.maxOccurring(MaxRepeat))), // PORT: §3.12
            new Signature(
                PercentileArrayReturn,
                Tabularity.Scalar,
                new Parameter("expr", ParameterTypeKind.Scalar),
                new Parameter("percentiles", ParameterTypeKind.DynamicArray)));

    public static final FunctionSymbol PercentileW =
        new FunctionSymbol("percentilew", PercentileReturn, Tabularity.Scalar,
            new Parameter("expr", ParameterTypeKind.Scalar),
            new Parameter("weight", ParameterTypeKind.Integer),
            new Parameter("percentile", ParameterTypeKind.Number));

    public static final FunctionSymbol PercentilesW =
        new FunctionSymbol("percentilesw", PercentileReturn, Tabularity.Scalar,
            new Parameter("expr", ParameterTypeKind.Scalar),
            new Parameter("weight", ParameterTypeKind.Integer),
            new Parameter("percentile", ParameterTypeKind.Number, P.minOccurring(1), P.maxOccurring(MaxRepeat))); // PORT: §3.12

    public static final FunctionSymbol PercentilesWArray =
        new FunctionSymbol("percentilesw_array",
            new Signature(PercentileArrayReturn, Tabularity.Scalar,
                new Parameter("expr", ParameterTypeKind.Scalar),
                new Parameter("weight", ParameterTypeKind.Integer),
                new Parameter("percentile", ParameterTypeKind.Number, P.minOccurring(1), P.maxOccurring(MaxRepeat))), // PORT: §3.12
            new Signature(PercentileArrayReturn, Tabularity.Scalar,
                new Parameter("expr", ParameterTypeKind.Scalar),
                new Parameter("weight", ParameterTypeKind.Integer),
                new Parameter("percentiles", ParameterTypeKind.DynamicArray)));

    public static final FunctionSymbol Stdev =
        new FunctionSymbol("stdev", ScalarTypes.Real,
            new Parameter("expr", ParameterTypeKind.Summable))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("stdev");

    public static final FunctionSymbol StdevIf =
        new FunctionSymbol("stdevif", ScalarTypes.Real,
            new Parameter("expr", ParameterTypeKind.Summable),
            new Parameter("predicate", ScalarTypes.Bool))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("stdevif");

    public static final FunctionSymbol Stdevp =
        new FunctionSymbol("stdevp", ScalarTypes.Real,
            new Parameter("expr", ParameterTypeKind.Summable))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("stdevp");

    public static final FunctionSymbol Variance =
        new FunctionSymbol("variance", ScalarTypes.Real,
            new Parameter("expr", ParameterTypeKind.Summable))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("variance");

    public static final FunctionSymbol VarianceIf =
        new FunctionSymbol("varianceif", ScalarTypes.Real,
            new Parameter("expr", ParameterTypeKind.Summable),
            new Parameter("predicate", ScalarTypes.Bool))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("varianceif");

    public static final FunctionSymbol Variancep =
        new FunctionSymbol("variancep", ScalarTypes.Real,
            new Parameter("expr", ParameterTypeKind.Summable))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("variancep");

    public static final FunctionSymbol VariancepIf =
        new FunctionSymbol("variancepif", ScalarTypes.Real,
            new Parameter("expr", ParameterTypeKind.Summable),
            new Parameter("predicate", ScalarTypes.Bool))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("variancepif");

    public static final FunctionSymbol Covariance =
        new FunctionSymbol("covariance", ScalarTypes.Real,
            new Parameter("expr", ParameterTypeKind.Summable),
            new Parameter("expr", ParameterTypeKind.Summable))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("covariance");

    public static final FunctionSymbol CovarianceIf =
        new FunctionSymbol("covarianceif", ScalarTypes.Real,
            new Parameter("expr", ParameterTypeKind.Summable),
            new Parameter("expr", ParameterTypeKind.Summable),
            new Parameter("predicate", ScalarTypes.Bool))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("covarianceif");

    public static final FunctionSymbol Covariancep =
        new FunctionSymbol("covariancep", ScalarTypes.Real,
            new Parameter("expr", ParameterTypeKind.Summable),
            new Parameter("expr", ParameterTypeKind.Summable))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("covariancep");

    public static final FunctionSymbol CovariancepIf =
        new FunctionSymbol("covariancepif", ScalarTypes.Real,
            new Parameter("expr", ParameterTypeKind.Summable),
            new Parameter("expr", ParameterTypeKind.Summable),
            new Parameter("predicate", ScalarTypes.Bool))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("covariancepif");

    public static final FunctionSymbol Any =
        new FunctionSymbol("any",
            new Signature(
                ReturnTypeKind.Parameter0,
                new Parameter("expr", ParameterTypeKind.Scalar)),
            new Signature(
                context -> getAnyResult(context, /*unnamedExpressionPrefix:*/ null), // PORT: §3.8; §3.12 named argument
                Tabularity.Scalar,
                new Parameter("expr", ParameterTypeKind.Scalar, P.minOccurring(2), P.maxOccurring(MaxRepeat))), // PORT: §3.12
            new Signature(
                context -> getAnyResult(context, /*unnamedExpressionPrefix:*/ null), // PORT: §3.8; §3.12 named argument
                Tabularity.Scalar,
                new Parameter("expr", ParameterTypeKind.Scalar, ArgumentKind.StarOnly)))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("any")
        .obsolete("take_any");

    public static final FunctionSymbol TakeAny =
       new FunctionSymbol("take_any",
           new Signature(
               context -> getAnyResult(context, /*unnamedExpressionPrefix:*/ "any_"), // PORT: §3.8; §3.12 named argument
               Tabularity.Scalar,
               new Parameter("expr", ParameterTypeKind.Scalar, ArgumentKind.StarAllowed, P.minOccurring(1), P.maxOccurring(MaxRepeat)))); // PORT: §3.12

    public static final FunctionSymbol AnyIf =
        new FunctionSymbol("anyif",
            new Signature(
                ReturnTypeKind.Parameter0,
                new Parameter("expr", ParameterTypeKind.Scalar),
                new Parameter("predicate", ScalarTypes.Bool)))
        .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
        .withResultNamePrefix("anyif")
        .obsolete("take_anyif");

    public static final FunctionSymbol TakeAnyIf =
       new FunctionSymbol("take_anyif",
           new Signature(
               ReturnTypeKind.Parameter0,
               new Parameter("expr", ParameterTypeKind.Scalar),
               new Parameter("predicate", ScalarTypes.Bool)))
        .withResultNameKind(ResultNameKind.FirstArgument);

    public static TypeSymbol getAnyResult(CustomReturnTypeContext context, String unnamedExpressionPrefix)
    {
        var columns = new ArrayList<ColumnSymbol>();
        var prefix = unnamedExpressionPrefix != null ? unnamedExpressionPrefix : ""; // PORT: §3.14 ?? string.Empty

        var doNotRepeat = new LinkedHashSet<ColumnSymbol>(getSummarizeByColumns(context.arguments())); // PORT: §3.17 HashSet; ColumnSymbol equality is identity
        var anyStar = Linq.any(context.arguments(), a -> a instanceof StarExpression); // PORT: §3.6

        for (int i = 0; i < context.arguments().size(); i++)
        {
            var arg = context.arguments().get(i);

            if (arg instanceof StarExpression)
            {
                for (var c : context.rowScope().columns())
                {                       
                    if (canAddAnyResultColumn(c, doNotRepeat, anyStar))
                    {
                        doNotRepeat.add(c);
                        columns.add(c);
                    }
                }
            }
            else if (arg instanceof SimpleNamedExpression snx
                && getResultColumn(snx.expression()) instanceof ColumnSymbol vc)
            {
                if (canAddAnyResultColumn(vc, doNotRepeat, anyStar))
                {
                    doNotRepeat.add(vc);
                    columns.add(new ColumnSymbol(snx.name().simpleName(), vc.type(), null, Arrays.asList(new ColumnSymbol[] { vc }))); // PORT: §3.12 originalColumns:
                }
            }
            else if (getResultColumn(arg) instanceof ColumnSymbol c)
            {
                // this is explicitly referenced column (not assigned)
                if (canAddAnyResultColumn(c, doNotRepeat, anyStar))
                {
                    if (doNotRepeat.contains(c))
                    {
                        // change identity of explicitly referenced columns so won't match same columns already in projection list
                        // this will get renamed by project builder
                        columns.add(new ColumnSymbol(c.name(), c.type(), null, Arrays.asList(new ColumnSymbol[] { c }))); // PORT: §3.12 originalColumns:
                    }
                    else
                    {
                        doNotRepeat.add(c);
                        columns.add(c);
                    }
                }
            }
            else
            {
                var expName = Binder.getExpressionResultName(arg, "");
                if (DotNetStrings.isNullOrEmpty(expName))
                {
                    expName = prefix + "arg" + i;
                }

                var col = new ColumnSymbol(expName, arg.resultType(), null, null, arg); // PORT: §3.12 source:
                columns.add(col);
            }
        }

        return new TupleSymbol(columns);
    }

    private static boolean canAddAnyResultColumn(ColumnSymbol column, LinkedHashSet<ColumnSymbol> doNotRepeat, boolean anyStar)
    {
        if (!anyStar)
            return true;

        return !doNotRepeat.contains(column);
    }

    public static final FunctionSymbol ArgMin =
        new FunctionSymbol("arg_min",
            new Signature(
                context -> getArgMinMaxResult(context, "min"), // PORT: §3.8
                Tabularity.Scalar,
                new Parameter("minimized", ParameterTypeKind.Orderable),
                new Parameter("returned", ParameterTypeKind.Scalar, ArgumentKind.StarAllowed, P.minOccurring(0), P.maxOccurring(MaxRepeat)))); // PORT: §3.12

    public static final FunctionSymbol ArgMax =
        new FunctionSymbol("arg_max",
            new Signature(
                context -> getArgMinMaxResult(context, "max"), // PORT: §3.8
                Tabularity.Scalar,
                new Parameter("maximized", ParameterTypeKind.Orderable),
                new Parameter("returned", ParameterTypeKind.Scalar, ArgumentKind.StarAllowed, P.minOccurring(0), P.maxOccurring(MaxRepeat)))); // PORT: §3.12

    private static TypeSymbol getArgMinMaxResult(CustomReturnTypeContext context, String prefix)
    {
        var columns = new ArrayList<ColumnSymbol>();

        if (context.arguments().size() > 0)
        {
            var byClauseColumns = new LinkedHashSet<ColumnSymbol>(getSummarizeByColumns(context.arguments())); // PORT: §3.17 HashSet; ColumnSymbol equality is identity
            var doNotRepeat = new LinkedHashSet<ColumnSymbol>(); // PORT: §3.17

            var primaryArg = context.arguments().get(0);
            var primaryColName = Binder.getExpressionResultName(primaryArg);

            var anyStar = Linq.any(context.arguments(), a -> a instanceof StarExpression); // PORT: §3.6

            for (int i = 0; i < context.arguments().size(); i++)
            {
                var arg = context.arguments().get(i);

                if (arg instanceof StarExpression)
                {
                    for (var c : context.rowScope().columns())
                    {
                        if (canAddArgMinMaxResultColumn(i, c, byClauseColumns, doNotRepeat, anyStar))
                        {
                            doNotRepeat.add(c);
                            columns.add(c);
                        }
                    }
                }
                else if (arg instanceof SimpleNamedExpression snx
                    && getResultColumn(snx.expression()) instanceof ColumnSymbol vc)
                {
                    if (canAddArgMinMaxResultColumn(i, vc, byClauseColumns, doNotRepeat, anyStar))
                    {
                        doNotRepeat.add(vc);
                        columns.add(new ColumnSymbol(snx.name().simpleName(), vc.type(), null, Arrays.asList(new ColumnSymbol[] { vc }))); // PORT: §3.12 originalColumns:
                    }
                }
                else if (getResultColumn(arg) instanceof ColumnSymbol c)
                {
                    // this is explicitly referenced column (not assigned)
                    if (canAddArgMinMaxResultColumn(i, c, byClauseColumns, doNotRepeat, anyStar))
                    {
                        if (doNotRepeat.contains(c)
                            || byClauseColumns.contains(c))
                        {
                            // change identity of explicitly referenced columns so won't match same columns already in projection list
                            // this will get renamed by project builder
                            columns.add(new ColumnSymbol(c.name(), c.type(), null, Arrays.asList(new ColumnSymbol[] { c }))); // PORT: §3.12 originalColumns:
                        }
                        else
                        {
                            doNotRepeat.add(c);
                            columns.add(c);
                        }
                    }
                }
                else
                {
                    var expName = Binder.getExpressionResultName(arg, null);
                    if (expName == null)
                    {
                        if (i == 0)
                        {
                            expName = prefix + "_";
                        }
                        else
                        {
                            expName = prefix + "_" + DotNet.str(primaryColName) + "_arg" + i; // PORT: §3.14 null concatenates as ""
                        }
                    }

                    var col = new ColumnSymbol(expName, arg.resultType(), null, null, arg); // PORT: §3.12 source:
                    columns.add(col);
                }
            }
        }

        return new TupleSymbol(columns);
    }

    private static boolean canAddArgMinMaxResultColumn(int argIndex, ColumnSymbol column, LinkedHashSet<ColumnSymbol> byClauseColumns, LinkedHashSet<ColumnSymbol> doNotRepeat, boolean anyStar)
    {
        if (argIndex == 0)
            return true;

        if (!anyStar)
            return true;

        return !doNotRepeat.contains(column)
            && !byClauseColumns.contains(column);
    }

    private static ColumnSymbol getResultColumn(Expression expr)
    { return Binder.getResultColumn(expr); } // PORT: §3.1 expression body
    public static final FunctionSymbol ArgMin_Deprecated =
        new FunctionSymbol("argmin",
            new Signature(
                Aggregates::getArgMinMaxDepResult, // PORT: §3.8 method group
                Tabularity.Scalar,
                new Parameter("minimized", ParameterTypeKind.Orderable),
                new Parameter("returned", ParameterTypeKind.Scalar, ArgumentKind.StarAllowed, P.minOccurring(0), P.maxOccurring(MaxRepeat)))) // PORT: §3.12
        .withResultNamePrefix("min")
        .obsolete("arg_min")
        .hide();

    public static final FunctionSymbol ArgMax_Deprecated =
        new FunctionSymbol("argmax",
            new Signature(
                Aggregates::getArgMinMaxDepResult, // PORT: §3.8 method group
                Tabularity.Scalar,
                new Parameter("maximized", ParameterTypeKind.Orderable),
                new Parameter("returned", ParameterTypeKind.Scalar, ArgumentKind.StarAllowed, P.minOccurring(0), P.maxOccurring(MaxRepeat)))) // PORT: §3.12
        .withResultNamePrefix("max")
        .obsolete("arg_max")
        .hide();

    private static TypeSymbol getArgMinMaxDepResult(CustomReturnTypeContext context)
    {
        var columns = new ArrayList<ColumnSymbol>();

        if (context.arguments().size() > 0)
        {
            // determine columns in by expression
            var byClauseColumns = new LinkedHashSet<ColumnSymbol>(getSummarizeByColumns(context.arguments())); // PORT: §3.17 HashSet; ColumnSymbol equality is identity
            var doNotRepeat = new LinkedHashSet<ColumnSymbol>(); // PORT: §3.17
            var anyStar = Linq.any(context.arguments(), a -> a instanceof StarExpression); // PORT: §3.6

            var primaryArg = context.arguments().get(0);
            String primaryColName;

            if (getResultColumn(primaryArg) instanceof ColumnSymbol pc)
            {
                doNotRepeat.add(pc);
                columns.add(pc);
                primaryColName = pc.name();
            }
            else
            {
                primaryColName = Binder.getExpressionResultName(primaryArg);
                var primaryCol = new ColumnSymbol(primaryColName, primaryArg.resultType(), null, null, primaryArg); // PORT: §3.12 source:
                columns.add(primaryCol);
            }

            for (int i = 1; i < context.arguments().size(); i++)
            {
                var arg = context.arguments().get(i);

                if (arg instanceof StarExpression)
                {
                    for (var c : context.rowScope().columns())
                    {
                        if (c != primaryArg.referencedSymbol()
                            && canAddArgMinMaxResultColumn(i, c, byClauseColumns, doNotRepeat, anyStar))
                        {
                            doNotRepeat.add(c);
                            columns.add(c.withName(DotNet.str(primaryColName) + "_" + c.name()).withOriginalColumns(c)); // PORT: §3.14 null concatenates as ""
                        }
                    }
                }
                else if (arg instanceof SimpleNamedExpression snx
                    && getResultColumn(snx.expression()) instanceof ColumnSymbol vc)
                {
                    if (canAddArgMinMaxResultColumn(i, vc, byClauseColumns, doNotRepeat, anyStar))
                    {
                        doNotRepeat.add(vc);
                        columns.add(new ColumnSymbol(DotNet.str(primaryColName) + "_" + DotNet.str(snx.name().simpleName()), vc.type(), null, Arrays.asList(new ColumnSymbol[] { vc }))); // PORT: §3.12 originalColumns:; §3.14
                    }
                }
                else if (getResultColumn(arg) instanceof ColumnSymbol c)
                {
                    if (canAddArgMinMaxResultColumn(i, c, byClauseColumns, doNotRepeat, anyStar))
                    {
                        doNotRepeat.add(c);
                        columns.add(c.withName(DotNet.str(primaryColName) + "_" + c.name()).withOriginalColumns(c)); // PORT: §3.14 null concatenates as ""
                    }
                }
                else
                {
                    var expName = Binder.getExpressionResultName(arg, null);
                    if (expName == null)
                        expName = "arg" + i;
                    var col = new ColumnSymbol(DotNet.str(primaryColName) + "_" + expName, arg.resultType(), null, null, arg); // PORT: §3.12 source:; §3.14
                    columns.add(col);
                }
            }
        }

        return new TupleSymbol(columns);
    }

    public static final List<FunctionSymbol> All = Arrays.asList(new FunctionSymbol[] // PORT: §3.1 static get-only auto-property { get; } = … → public static final field (no setter, initialised once; matches Functions.All); §3.17 array as IReadOnlyList
    {
        Sum,
        SumIf,
        Cnt,
        Count,
        CountIf,
        DCount,
        DCountIf,
        TDigest,
        TDigestMerge,
        MergeTDigest,
        Hll,
        HllIf,
        HllMerge,
        Min,
        MinIf,
        Max,
        MaxIf,
        Avg,
        AvgIf,
        MakeList_Deprecated,
        MakeList,
        MakeListIf,
        MakeListWithNulls,
        MakeSet_Deprecated,
        MakeSet,
        MakeSetIf,
        MakeDictionary,
        MakeBag,
        MakeBagIf,
        BuildSchema,
        Passthrough,
        Percentile,
        Percentiles,
        PercentilesArray,
        PercentileW,
        PercentilesW,
        PercentilesWArray,
        Stdev,
        StdevIf,
        Stdevp,
        Variance,
        VarianceIf,
        Variancep,
        VariancepIf,
        Covariance,
        CovarianceIf,
        Covariancep,
        CovariancepIf,
        Any,
        TakeAny,
        AnyIf,
        TakeAnyIf,
        ArgMin,
        ArgMax,
        ArgMin_Deprecated,
        ArgMax_Deprecated,
        BinaryAllOr,
        BinaryAllAnd,
        BinaryAllXor,
        CountDistinct,
        CountDistinctIf
    });
}
