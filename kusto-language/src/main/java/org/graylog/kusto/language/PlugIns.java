// Ported from: src/Kusto.Language/PlugIns.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language;

import static org.graylog.kusto.language.FunctionHelpers.*; // PORT: §3.5 using static FunctionHelpers

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import org.graylog.kusto.language.symbols.ArgumentKind;
import org.graylog.kusto.language.symbols.ColumnSymbol;
import org.graylog.kusto.language.symbols.CombineKind;
import org.graylog.kusto.language.symbols.CustomReturnTypeContext;
import org.graylog.kusto.language.symbols.FunctionSymbol;
import org.graylog.kusto.language.symbols.GroupSymbol;
import org.graylog.kusto.language.symbols.P;
import org.graylog.kusto.language.symbols.Parameter;
import org.graylog.kusto.language.symbols.ParameterTypeKind;
import org.graylog.kusto.language.symbols.ScalarTypes;
import org.graylog.kusto.language.symbols.Signature;
import org.graylog.kusto.language.symbols.TableSymbol;
import org.graylog.kusto.language.symbols.Tabularity;
import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.syntax.FunctionCallExpression;
import org.graylog.kusto.language.syntax.LiteralExpression;
import org.graylog.kusto.language.syntax.SimpleNamedExpression;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetBoolean;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Linq;

/// <summary>
/// Well known plugins.
/// </summary>
public final class PlugIns // PORT: §3.5 static class → final class with a private constructor
{
    private PlugIns() { }

    public static final FunctionSymbol ActiveUseCounts =
        new FunctionSymbol("active_users_count",
            context -> // PORT: §3.8
            {
                var cols = new ArrayList<ColumnSymbol>();
                addReferencedColumn(cols, context, "TimelineColumn"); // timeline
                addReferencedColumns(cols, context, "Dimension"); // dimensions
                cols.add(new ColumnSymbol("dcount", ScalarTypes.Long));
                return new TableSymbol(cols);
            },
            Tabularity.Tabular,
            new Parameter("IdColumn", ParameterTypeKind.NotDynamic, ArgumentKind.Column),
            new Parameter("TimelineColumn", ParameterTypeKind.Summable, ArgumentKind.Column),
            new Parameter("Start", ParameterTypeKind.Summable, ArgumentKind.Constant),
            new Parameter("End", ParameterTypeKind.Summable, ArgumentKind.Constant),
            new Parameter("LookbackWindow", ParameterTypeKind.Summable, ArgumentKind.Constant),
            new Parameter("Period", ParameterTypeKind.Summable, ArgumentKind.Constant),
            new Parameter("ActivePeriods", ParameterTypeKind.Summable, ArgumentKind.Constant),
            new Parameter("Step", ParameterTypeKind.Summable, ArgumentKind.Constant),
            new Parameter("Dimension", ParameterTypeKind.NotDynamic, ArgumentKind.Column, P.minOccurring(0), P.maxOccurring(MaxRepeat)) // PORT: §3.12
            );

    public static final FunctionSymbol ActivityCountsMetrics =
        new FunctionSymbol("activity_counts_metrics",
            context -> // PORT: §3.8
            {
                var cols = new ArrayList<ColumnSymbol>();
                addReferencedColumn(cols, context, "TimelineColumn"); // timeline column
                addReferencedColumns(cols, context, "Dimension"); // dimension columns
                cols.add(new ColumnSymbol("count", ScalarTypes.Long));
                cols.add(new ColumnSymbol("dcount", ScalarTypes.Long));
                cols.add(new ColumnSymbol("new_dcount", ScalarTypes.Long));
                cols.add(new ColumnSymbol("aggregated_dcount", ScalarTypes.Long));
                return new TableSymbol(cols);
            },
            Tabularity.Tabular,
            new Parameter("IdColumn", ParameterTypeKind.NotDynamic, ArgumentKind.Column),
            new Parameter("TimelineColumn", ParameterTypeKind.Summable, ArgumentKind.Column),
            new Parameter("Start", ParameterTypeKind.Summable, ArgumentKind.Constant),
            new Parameter("End", ParameterTypeKind.Summable, ArgumentKind.Constant),
            new Parameter("Step", ParameterTypeKind.Summable, ArgumentKind.Constant),
            new Parameter("Dimension", ParameterTypeKind.NotDynamic, ArgumentKind.Column, P.minOccurring(0), P.maxOccurring(MaxRepeat)) // PORT: §3.12
            );

    public static final FunctionSymbol ActivityEngagement =
        new FunctionSymbol("activity_engagement",
            new Signature(
                context -> // PORT: §3.8
                {
                    var cols = new ArrayList<ColumnSymbol>();
                    addReferencedColumn(cols, context, "TimelineColumn"); // timeline column
                    cols.add(new ColumnSymbol("dcount_activities_inner", ScalarTypes.Long)); // inner activity
                    cols.add(new ColumnSymbol("dcount_activities_outer", ScalarTypes.Long)); // outer activity
                    cols.add(new ColumnSymbol("activity_ratio", ScalarTypes.Real));
                    return new TableSymbol(cols);
                },
                Tabularity.Tabular,
                new Parameter("IdColumn", ParameterTypeKind.NotDynamic, ArgumentKind.Column),
                new Parameter("TimelineColumn", ParameterTypeKind.Summable, ArgumentKind.Column),
                new Parameter("InnerActivityWindow", ParameterTypeKind.Summable, ArgumentKind.Constant),
                new Parameter("OuterActivityWindow", ParameterTypeKind.Summable, ArgumentKind.Constant)),
            new Signature(
                context -> // PORT: §3.8
                {
                    var cols = new ArrayList<ColumnSymbol>();
                    addReferencedColumn(cols, context, "TimelineColumn"); // timeline column
                    addReferencedColumns(cols, context, "Dimension"); // dimension columns
                    cols.add(new ColumnSymbol("dcount_activities_inner", ScalarTypes.Long)); // inner activity
                    cols.add(new ColumnSymbol("dcount_activities_outer", ScalarTypes.Long)); // outer activity
                    cols.add(new ColumnSymbol("activity_ratio", ScalarTypes.Real));
                    return new TableSymbol(cols);
                },
                Tabularity.Tabular,
                new Parameter("IdColumn", ParameterTypeKind.NotDynamic, ArgumentKind.Column),
                new Parameter("TimelineColumn", ParameterTypeKind.Summable, ArgumentKind.Column),
                new Parameter("Start", ParameterTypeKind.Summable, ArgumentKind.Constant),
                new Parameter("End", ParameterTypeKind.Summable, ArgumentKind.Constant),
                new Parameter("InnerActivityWindow", ParameterTypeKind.Summable, ArgumentKind.Constant),
                new Parameter("OuterActivityWindow", ParameterTypeKind.Summable, ArgumentKind.Constant),
                new Parameter("Dimension", ParameterTypeKind.NotDynamic, ArgumentKind.Column, P.minOccurring(0), P.maxOccurring(MaxRepeat))) // PORT: §3.12
            );

    public static final FunctionSymbol ActivityMetrics =
        new FunctionSymbol("activity_metrics",
            new Signature(
                context -> // PORT: §3.8
                {
                    var cols = new ArrayList<ColumnSymbol>();
                    addReferencedColumn(cols, context, "TimelineColumn"); // timeline columns
                    addReferencedColumns(cols, context, "Dimension"); // dimension columns
                    cols.add(new ColumnSymbol("dcount_values", ScalarTypes.Long));
                    cols.add(new ColumnSymbol("dcount_newvalues", ScalarTypes.Long));
                    cols.add(new ColumnSymbol("retention_rate", ScalarTypes.Real));
                    cols.add(new ColumnSymbol("churn_rate", ScalarTypes.Real));
                    return new TableSymbol(cols);
                },
                Tabularity.Tabular,
                new Parameter("IdColumn", ParameterTypeKind.Scalar, ArgumentKind.Column),
                new Parameter("TimelineColumn", ParameterTypeKind.Summable, ArgumentKind.Column),
                new Parameter("Start", ParameterTypeKind.Summable, ArgumentKind.Constant),
                new Parameter("End", ParameterTypeKind.Summable, ArgumentKind.Constant),
                new Parameter("Step", ParameterTypeKind.Summable, ArgumentKind.Constant),
                new Parameter("Dimension", ParameterTypeKind.NotDynamic, ArgumentKind.Column, P.minOccurring(0), P.maxOccurring(MaxRepeat))), // PORT: §3.12
            new Signature(
                context -> // PORT: §3.8
                {
                    var cols = new ArrayList<ColumnSymbol>();
                    addReferencedColumn(cols, context, "TimelineColumn"); // timeline columns
                    cols.add(new ColumnSymbol("dcount_values", ScalarTypes.Long));
                    cols.add(new ColumnSymbol("dcount_newvalues", ScalarTypes.Long));
                    cols.add(new ColumnSymbol("retention_rate", ScalarTypes.Real));
                    cols.add(new ColumnSymbol("churn_rate", ScalarTypes.Real));
                    return new TableSymbol(cols);
                },
                Tabularity.Tabular,
                new Parameter("IdColumn", ParameterTypeKind.Scalar, ArgumentKind.Column),
                new Parameter("TimelineColumn", ParameterTypeKind.Summable, ArgumentKind.Column),
                new Parameter("Start", ParameterTypeKind.Summable, ArgumentKind.Constant, P.minOccurring(0)), // PORT: §3.12
                new Parameter("End", ParameterTypeKind.Summable, ArgumentKind.Constant, P.minOccurring(0))) // PORT: §3.12
            );

    private static Parameter nam_IdColumn = new Parameter("IdColumn", ParameterTypeKind.NotDynamic, ArgumentKind.Column);
    private static Parameter nam_TimelineColumn = new Parameter("TimelineColumn", ParameterTypeKind.Summable, ArgumentKind.Column);
    private static Parameter nam_Start = new Parameter("Start", ParameterTypeKind.Summable, ArgumentKind.Constant);
    private static Parameter nam_End = new Parameter("End", ParameterTypeKind.Summable, ArgumentKind.Constant);
    private static Parameter nam_Window = new Parameter("Window", ParameterTypeKind.Scalar);
    private static Parameter nam_Cohort = new Parameter("Cohort", ParameterTypeKind.Scalar, ArgumentKind.Constant, P.minOccurring(0)); // PORT: §3.12
    private static Parameter nam_Dimension = new Parameter("Dimension", ParameterTypeKind.NotDynamic, ArgumentKind.Column, P.minOccurring(0), P.maxOccurring(MaxRepeat)); // PORT: §3.12
    private static Parameter nam_Lookback = new Parameter("lookback", ParameterTypeKind.Tabular, P.minOccurring(0)); // PORT: §3.12

    public static final FunctionSymbol NewActivityMetrics =
        new FunctionSymbol("new_activity_metrics",
             new Signature(
                 context -> // PORT: §3.8
                 {
                     var cols = new ArrayList<ColumnSymbol>();

                     var timelineArg = context.getArgument("TimelineColumn"); // timeline column
                     if (timelineArg != null)
                     {
                         var timelineArgName = context.getResultName(timelineArg);
                         cols.add(new ColumnSymbol(makeColumnName("from", timelineArgName), timelineArg.resultType()));
                         cols.add(new ColumnSymbol(makeColumnName("to", timelineArgName), timelineArg.resultType()));
                     }

                     addReferencedColumns(cols, context, "Dimension"); // dimension columns

                     cols.add(new ColumnSymbol("dcount_new_values", ScalarTypes.Long));
                     cols.add(new ColumnSymbol("dcount_retained_values", ScalarTypes.Long));
                     cols.add(new ColumnSymbol("dcount_churn_values", ScalarTypes.Long));
                     cols.add(new ColumnSymbol("retention_rate", ScalarTypes.Real));
                     cols.add(new ColumnSymbol("churn_rate", ScalarTypes.Real));
                     return new TableSymbol(cols);
                 },
                 Tabularity.Tabular,
                 nam_IdColumn,
                 nam_TimelineColumn,
                 nam_Start,
                 nam_End,
                 nam_Window,
                 nam_Cohort,
                 nam_Dimension,
                 nam_Lookback)
            .withLayout((sig, args, list) ->
            {
                // add these even if not that many arguments supplied.. should not cause a problem
                list.add(nam_IdColumn);
                list.add(nam_TimelineColumn);
                list.add(nam_Start);
                list.add(nam_End);
                list.add(nam_Window);

                // if the next argument is a constant (or not a column == dimension), then it must be the optional cohort argument
                if (args.size() > 5 && (args.get(5).isConstant() || !(args.get(5).referencedSymbol() instanceof ColumnSymbol)))
                    list.add(nam_Cohort);

                // after cohort, all non-tabular arguments are dimension arguments
                int i = list.size();
                for (; i < args.size() && !(args.get(i).resultType() instanceof TableSymbol); i++)
                {
                    list.add(nam_Dimension);
                }

                // this last argument should be the lookback table
                if (i < args.size())
                    list.add(nam_Lookback);
            }));

    public static final List<ColumnSymbol> AutoClusterColumns = Arrays.asList(new ColumnSymbol[] { // PORT: §3.17 array as IReadOnlyList
        new ColumnSymbol("SegmentId", ScalarTypes.Long),
        new ColumnSymbol("Count", ScalarTypes.Long),
        new ColumnSymbol("Percent", ScalarTypes.Real)
    });

    public static final FunctionSymbol AutoCluster =
        new FunctionSymbol("autocluster",
            context -> new TableSymbol(Linq.concat(AutoClusterColumns, context.rowScope().columns())) // PORT: §3.8; §3.6
                           .withInheritableProperties(context.rowScope()),
            Tabularity.Tabular,
            new Parameter("SizeWeight", ScalarTypes.Real, P.defaultValueIndicator("~"), P.minOccurring(0)), // PORT: §3.12
            new Parameter("WeightColumn", ParameterTypeKind.Scalar, ArgumentKind.Column, P.defaultValueIndicator("~"), P.minOccurring(0)), // PORT: §3.12
            new Parameter("NumSeeds", ParameterTypeKind.Integer, P.defaultValueIndicator("~"), P.minOccurring(0)), // PORT: §3.12
            new Parameter("CustomWildcard", ParameterTypeKind.Scalar, P.minOccurring(0), P.maxOccurring(MaxRepeat))); // PORT: §3.12

    public static final FunctionSymbol BagUnpack =
         new FunctionSymbol("bag_unpack",
             context -> new TableSymbol(Linq.where(context.rowScope().columns(), c -> context.arguments().size() == 0 || c != context.arguments().get(0).referencedSymbol())) // PORT: §3.8; §3.6
                            .withInheritableProperties(context.rowScope())
                            .withIsOpen(true),
             Tabularity.Tabular,
             new Parameter("column", ParameterTypeKind.Scalar, ArgumentKind.Column),
             new Parameter("column_prefix", ScalarTypes.String, ArgumentKind.LiteralNotEmpty, P.minOccurring(0))); // PORT: §3.12

    public static final List<ColumnSymbol> BasketColumns = Arrays.asList(new ColumnSymbol[] { // PORT: §3.17 array as IReadOnlyList
        new ColumnSymbol("SegmentId", ScalarTypes.Long),
        new ColumnSymbol("Count", ScalarTypes.Long),
        new ColumnSymbol("Percent", ScalarTypes.Real)
    });

    public static final FunctionSymbol Basket =
         new FunctionSymbol("basket",
             context -> new TableSymbol(Linq.concat(BasketColumns, context.rowScope().columns())) // PORT: §3.8; §3.6
                            .withInheritableProperties(context.rowScope()),
             Tabularity.Tabular,
             new Parameter("Threshold", ScalarTypes.Real, P.defaultValueIndicator("~"), P.minOccurring(0)), // PORT: §3.12
             new Parameter("WeightColumn", ParameterTypeKind.Scalar, ArgumentKind.Column, P.defaultValueIndicator("~"), P.minOccurring(0)), // PORT: §3.12
             new Parameter("MaxDimensions", ParameterTypeKind.Integer, P.defaultValueIndicator("~"), P.minOccurring(0)), // PORT: §3.12
             new Parameter("CustomWildcard", ParameterTypeKind.Scalar, P.minOccurring(0), P.maxOccurring(MaxRepeat))); // PORT: §3.12

    public static final FunctionSymbol DCountIntersect =
         new FunctionSymbol("dcount_intersect",
             context -> new TableSymbol(Linq.concat(context.rowScope().columns(), Linq.selectIndexed(context.arguments(), (a, i) -> new ColumnSymbol("s" + i, ScalarTypes.Long)))) // PORT: §3.8; §3.6
                            .withInheritableProperties(context.rowScope()),
             Tabularity.Tabular,
             new Parameter("hll", ParameterTypeKind.DynamicArray, P.minOccurring(2), P.maxOccurring(MaxRepeat))); // PORT: §3.12

    public static final List<ColumnSymbol> DiffPatternsColumns = Arrays.asList(new ColumnSymbol[] { // PORT: §3.17 array as IReadOnlyList
        new ColumnSymbol("SegmentId", ScalarTypes.Long),
        new ColumnSymbol("CountA", ScalarTypes.Long),
        new ColumnSymbol("CountB", ScalarTypes.Long),
        new ColumnSymbol("PercentA", ScalarTypes.Real),
        new ColumnSymbol("PercentB", ScalarTypes.Real),
        new ColumnSymbol("PercentDiffAB", ScalarTypes.Real)
    });

    public static final FunctionSymbol DiffPatterns =
         new FunctionSymbol("diffpatterns",
             context -> new TableSymbol(Linq.concat(DiffPatternsColumns, context.rowScope().columns())) // PORT: §3.8; §3.6
                            .withInheritableProperties(context.rowScope()),
             Tabularity.Tabular,
             new Parameter("SplitColumn", ParameterTypeKind.Scalar, ArgumentKind.Column),
             new Parameter("SplitValueA", ScalarTypes.String),
             new Parameter("SplitValueB", ScalarTypes.String),
             new Parameter("WeightColumn", ParameterTypeKind.Scalar, ArgumentKind.Column, P.defaultValueIndicator("~"), P.minOccurring(0)), // PORT: §3.12
             new Parameter("Threshold", ScalarTypes.Real, P.defaultValueIndicator("~"), P.minOccurring(0)), // PORT: §3.12
             new Parameter("MaxDimensions", ParameterTypeKind.Integer, P.defaultValueIndicator("~"), P.minOccurring(0)), // PORT: §3.12
             new Parameter("CustomWildcard", ParameterTypeKind.Scalar, P.minOccurring(0), P.maxOccurring(MaxRepeat))); // PORT: §3.12

    public static final FunctionSymbol EstimateRowsCount =
         new FunctionSymbol("estimate_rows_count",
             new Signature(
                context -> new TableSymbol(new ColumnSymbol("EstimatedRowsCount", ScalarTypes.Long)), // PORT: §3.8
                Tabularity.Tabular));

    public static final FunctionSymbol ExecuteShowCommand =
         new FunctionSymbol("execute_show_command",
             context -> new TableSymbol().withIsOpen(true), // depends on contents of command string // PORT: §3.8
             Tabularity.Tabular,
             new Parameter("connection_string", ScalarTypes.String),
             new Parameter("command", ScalarTypes.String));

    public static final FunctionSymbol ExecuteQuery =
         new FunctionSymbol("execute_query",
             context -> new TableSymbol().withIsOpen(true), // depends on contents of command string // PORT: §3.8
             Tabularity.Tabular,
             new Parameter("connection_string", ScalarTypes.String),
             new Parameter("query", ScalarTypes.String));

    public static final FunctionSymbol ExternalDatatable =
         new FunctionSymbol("external_datatable",
             new Signature(
                context -> new TableSymbol().withIsOpen(true), // depends on the data sent from the client // PORT: §3.8
                Tabularity.Tabular));

// PORT: §3.20 dead `#if false` block kept as a comment
// #if false   // problem with multiple repeating parameters
//         new FunctionSymbol("funnel_analysis",
//              (table, args) => table,
//              Tabularity.Tabular,
//              new Parameter("IdColumn", ParameterTypeKind.Scalar) // needs to be numeric or dynamic?
//              ),
// #endif
    public static final FunctionSymbol FunnelSequence =
        new FunctionSymbol("funnel_sequence",
            context -> // PORT: §3.8
            {
                // only declare first table, as additional schema is not useful to intellisense
                var cols = new ArrayList<ColumnSymbol>();
                addReferencedColumn(cols, context, "TimelineColumn");

                var stateArg = context.getArgument("StateColumn");
                if (stateArg != null)
                {
                    cols.add(new ColumnSymbol("prev", stateArg.resultType(), null, null, stateArg)); // PORT: §3.12 source:
                    cols.add(new ColumnSymbol("next", stateArg.resultType(), null, null, stateArg)); // PORT: §3.12 source:
                }

                cols.add(new ColumnSymbol("dcount", ScalarTypes.Long));
                cols.add(new ColumnSymbol("samples", ScalarTypes.DynamicArray));
                return new TableSymbol(cols);
            },
            Tabularity.Tabular,
            new Parameter("IdColumn", ParameterTypeKind.NotDynamic, ArgumentKind.Column),
            new Parameter("TimelineColumn", ParameterTypeKind.Summable, ArgumentKind.Column),
            new Parameter("Start", ParameterTypeKind.Summable, ArgumentKind.Constant),
            new Parameter("End", ParameterTypeKind.Summable, ArgumentKind.Constant),
            new Parameter("MaxWindowSizeBetweenSteps", ParameterTypeKind.Summable, ArgumentKind.Constant),
            new Parameter("Step", ParameterTypeKind.Summable, ArgumentKind.Constant),
            new Parameter("StateColumn", ParameterTypeKind.NotDynamic, ArgumentKind.Column),
            new Parameter("Sequence", ParameterTypeKind.DynamicArray, ArgumentKind.Constant));

    public static final FunctionSymbol FunnelSequenceCompletion =
        new FunctionSymbol("funnel_sequence_completion",
            context -> // PORT: §3.8
            {
                var cols = new ArrayList<ColumnSymbol>();
                addReferencedColumn(cols, context, "TimelineColumn");

                var stateArg = context.getArgument("StateColumn");
                if (stateArg != null)
                {
                    cols.add(new ColumnSymbol(context.getResultName(stateArg), ScalarTypes.String, null, null, stateArg)); // PORT: §3.12 source:
                }

                cols.add(new ColumnSymbol("Period", ScalarTypes.TimeSpan));
                cols.add(new ColumnSymbol("dcount", ScalarTypes.Long));
                return new TableSymbol(cols);
            },
            Tabularity.Tabular,
            new Parameter("IdColumn", ParameterTypeKind.NotDynamic, ArgumentKind.Column),
            new Parameter("TimelineColumn", ParameterTypeKind.Summable, ArgumentKind.Column),
            new Parameter("Start", ParameterTypeKind.Summable, ArgumentKind.Constant),
            new Parameter("End", ParameterTypeKind.Summable, ArgumentKind.Constant),
            new Parameter("BinSize", ParameterTypeKind.Summable, ArgumentKind.Constant),
            new Parameter("StateColumn", ParameterTypeKind.NotDynamic, ArgumentKind.Column),
            new Parameter("Sequence", ParameterTypeKind.DynamicArray, ArgumentKind.Constant),
            new Parameter("MaxSequencePeriods", ParameterTypeKind.DynamicArray, ArgumentKind.Constant));

    public static final List<ColumnSymbol> HttpRequestColumns = Arrays.asList(new ColumnSymbol[] { // PORT: §3.17 array as IReadOnlyList
        new ColumnSymbol("ResponseHeaders", ScalarTypes.DynamicBag),
        new ColumnSymbol("ResponseBody", ScalarTypes.Dynamic)
    });

    public static final FunctionSymbol HttpRequest =
         new FunctionSymbol("http_request",
             new TableSymbol(HttpRequestColumns),
             new Parameter("Uri", ScalarTypes.String, ArgumentKind.Constant),
             new Parameter("RequestHeaders", ParameterTypeKind.DynamicBag, P.minOccurring(0)), // PORT: §3.12
             new Parameter("Options", ParameterTypeKind.DynamicBag, P.minOccurring(0))); // PORT: §3.12

    public static final FunctionSymbol HttpRequestPost =
         new FunctionSymbol("http_request_post",
             new TableSymbol(HttpRequestColumns),
             new Parameter("Uri", ScalarTypes.String, ArgumentKind.Constant),
             new Parameter("RequestHeaders", ParameterTypeKind.DynamicBag, P.minOccurring(0)), // PORT: §3.12
             new Parameter("Options", ParameterTypeKind.DynamicBag, P.minOccurring(0)), // PORT: §3.12
             new Parameter("Content", ScalarTypes.String, ArgumentKind.Constant, P.minOccurring(0))); // PORT: §3.12

    public static final FunctionSymbol AIEmbedText_Deprecated =
         new FunctionSymbol("ai_embed_text",
             context -> // PORT: §3.8
             {
                 var sourceColumns = context.rowScope().columns();
                 var columnPrefix = context.getResultName(context.getArgument("Text"));

                 var embeddingColumnName = makeColumnName(columnPrefix, "embedding");
                 var addedColumns = new ArrayList<ColumnSymbol>(Arrays.asList(new ColumnSymbol[] { new ColumnSymbol(embeddingColumnName, ScalarTypes.Dynamic) })); // PORT: §3.17 collection initializer

                 Boolean includeErrorMessages; // PORT: §3.3 out var; §5.2 bool.TryParse → DotNetBoolean.tryParse (null = false result)
                 if (context.getArgument("IncludeErrorMessages") != null &&
                    (includeErrorMessages = DotNetBoolean.tryParse(getConstantValue(context.getArgument("IncludeErrorMessages")))) != null)
                 {
                     if (includeErrorMessages)
                     {
                         var errorColumnName = makeColumnName(columnPrefix, "embedding", "error");
                         addedColumns.add(new ColumnSymbol(errorColumnName, ScalarTypes.String));
                     }
                 }

                 var resultColumns = Linq.concat(sourceColumns, addedColumns); // PORT: §3.6

                 return new TableSymbol(resultColumns);
             },
             Tabularity.Tabular,
             new Parameter("Text", ParameterTypeKind.Scalar, ArgumentKind.Expression /* Column | Literal */), // PORT-BUG (D32): ArgumentKind is not [Flags]; Column | Literal is (ArgumentKind)13, no declared member, and every core comparison/switch on it falls through like Expression; Java enums cannot hold 13
             new Parameter("ConnectionString", ScalarTypes.String),
             new Parameter("Options", ParameterTypeKind.DynamicBag, P.minOccurring(0)), // PORT: §3.12
             new Parameter("IncludeErrorMessages", ScalarTypes.Bool, P.minOccurring(0))) // PORT: §3.12
        .obsolete("ai_embeddings");

    public static final FunctionSymbol AIEmbeddings =
        new FunctionSymbol("ai_embeddings",
            context -> // PORT: §3.8
            {
                var sourceColumns = context.rowScope().columns();
                var columnPrefix = context.getResultName(context.getArgument("Text"));

                var embeddingColumnName = makeColumnName(columnPrefix, "embeddings");
                var addedColumns = new ArrayList<ColumnSymbol>(Arrays.asList(new ColumnSymbol[] { new ColumnSymbol(embeddingColumnName, ScalarTypes.Dynamic) })); // PORT: §3.17 collection initializer

                Boolean includeErrorMessages; // PORT: §3.3 out var; §5.2 bool.TryParse → DotNetBoolean.tryParse (null = false result)
                if (context.getArgument("IncludeErrorMessages") != null &&
                   (includeErrorMessages = DotNetBoolean.tryParse(getConstantValue(context.getArgument("IncludeErrorMessages")))) != null)
                {
                    if (includeErrorMessages)
                    {
                        var errorColumnName = makeColumnName(columnPrefix, "embeddings", "error");
                        addedColumns.add(new ColumnSymbol(errorColumnName, ScalarTypes.String));
                    }
                }

                var resultColumns = Linq.concat(sourceColumns, addedColumns); // PORT: §3.6

                return new TableSymbol(resultColumns);
            },
            Tabularity.Tabular,
            new Parameter("Text", ParameterTypeKind.Scalar, ArgumentKind.Expression /* Column | Literal */), // PORT-BUG (D32): ArgumentKind is not [Flags]; Column | Literal is (ArgumentKind)13, no declared member, and every core comparison/switch on it falls through like Expression; Java enums cannot hold 13
            new Parameter("ConnectionString", ScalarTypes.String),
            new Parameter("Options", ParameterTypeKind.DynamicBag, P.minOccurring(0)), // PORT: §3.12
            new Parameter("IncludeErrorMessages", ScalarTypes.Bool, P.minOccurring(0))); // PORT: §3.12


    public static final FunctionSymbol AIChatCompletion =
         new FunctionSymbol("ai_chat_completion",
             context -> // PORT: §3.8
             {
                 var sourceColumns = context.rowScope().columns();
                 var columnPrefix = context.getResultName(context.getArgument("Prompt"));

                 var completionColumnName = makeColumnName(columnPrefix, "completion");
                 var addedColumns = new ArrayList<ColumnSymbol>(Arrays.asList(new ColumnSymbol[] { new ColumnSymbol(completionColumnName, ScalarTypes.String) })); // PORT: §3.17 collection initializer

                 Boolean includeErrorMessages; // PORT: §3.3 out var; §5.2 bool.TryParse → DotNetBoolean.tryParse (null = false result)
                 if (context.getArgument("IncludeErrorMessages") != null &&
                    (includeErrorMessages = DotNetBoolean.tryParse(getConstantValue(context.getArgument("IncludeErrorMessages")))) != null)
                 {
                     if (includeErrorMessages)
                     {
                         var errorColumnName = makeColumnName(columnPrefix, "completion", "error");
                         addedColumns.add(new ColumnSymbol(errorColumnName, ScalarTypes.String));
                     }
                 }

                 var resultColumns = Linq.concat(sourceColumns, addedColumns); // PORT: §3.6

                 return new TableSymbol(resultColumns);
             },
             Tabularity.Tabular,
             new Parameter("Prompt", ParameterTypeKind.DynamicArray, ArgumentKind.Expression /* Column | Literal */), // PORT-BUG (D32): ArgumentKind is not [Flags]; Column | Literal is (ArgumentKind)13, no declared member, and every core comparison/switch on it falls through like Expression; Java enums cannot hold 13
             new Parameter("ConnectionString", ScalarTypes.String),
             new Parameter("Options", ParameterTypeKind.DynamicBag, P.minOccurring(0)), // PORT: §3.12
             new Parameter("IncludeErrorMessages", ScalarTypes.Bool, P.minOccurring(0))); // PORT: §3.12

    public static final FunctionSymbol AIChatCompletionPrompt =
         new FunctionSymbol("ai_chat_completion_prompt",
             context -> // PORT: §3.8
             {
                 var sourceColumns = context.rowScope().columns();
                 var columnPrefix = context.getResultName(context.getArgument("Prompt"));

                 var completionColumnName = makeColumnName(columnPrefix, "completion");
                 var addedColumns = new ArrayList<ColumnSymbol>(Arrays.asList(new ColumnSymbol[] { new ColumnSymbol(completionColumnName, ScalarTypes.String) })); // PORT: §3.17 collection initializer

                 Boolean includeErrorMessages; // PORT: §3.3 out var; §5.2 bool.TryParse → DotNetBoolean.tryParse (null = false result)
                 if (context.getArgument("IncludeErrorMessages") != null &&
                    (includeErrorMessages = DotNetBoolean.tryParse(getConstantValue(context.getArgument("IncludeErrorMessages")))) != null)
                 {
                     if (includeErrorMessages)
                     {
                         var errorColumnName = makeColumnName(columnPrefix, "completion", "error");
                         addedColumns.add(new ColumnSymbol(errorColumnName, ScalarTypes.String));
                     }
                 }

                 var resultColumns = Linq.concat(sourceColumns, addedColumns); // PORT: §3.6

                 return new TableSymbol(resultColumns);
             },
             Tabularity.Tabular,
             new Parameter("Prompt", ScalarTypes.String, ArgumentKind.Expression /* Column | Literal */), // PORT-BUG (D32): ArgumentKind is not [Flags]; Column | Literal is (ArgumentKind)13, no declared member, and every core comparison/switch on it falls through like Expression; Java enums cannot hold 13
             new Parameter("ConnectionString", ScalarTypes.String),
             new Parameter("Options", ParameterTypeKind.DynamicBag, P.minOccurring(0)), // PORT: §3.12
             new Parameter("IncludeErrorMessages", ScalarTypes.Bool, P.minOccurring(0))); // PORT: §3.12


    public static final FunctionSymbol Identity =
         new FunctionSymbol("identity",
             new Signature(
                 context -> context.rowScope(), // PORT: §3.8
                 Tabularity.Tabular));

    public static final FunctionSymbol IdentityV3 =
         new FunctionSymbol("identity_v3",
            context -> context.rowScope(), // PORT: §3.8
            Tabularity.Tabular,
            new Parameter("mode", ScalarTypes.String, ArgumentKind.Constant),
            new Parameter("exceptionText", ScalarTypes.String, ArgumentKind.Constant));

    public static final FunctionSymbol InferStorageSchema =
         new FunctionSymbol("infer_storage_schema",
             new TableSymbol(new ColumnSymbol("CslSchema", ScalarTypes.String)),
             new Parameter("Options", ParameterTypeKind.DynamicBag));

    public static final FunctionSymbol InferStorageSchemaWithSuggestions =
         new FunctionSymbol("infer_storage_schema_with_suggestions",
            new TableSymbol(new ColumnSymbol("SuggestedTableSchema", ScalarTypes.String)),
            new Parameter("Options", ParameterTypeKind.DynamicBag));

    private static final Parameter Geo_lookup_LookupTable = new Parameter("LookupTable", ParameterTypeKind.Tabular);

    private static final Parameter Geo_lookup_LookupPolygonKey = new Parameter("LookupPolygonKey", ParameterTypeKind.DynamicBag, ArgumentKind.Column_Parameter0);
    private static final Parameter Geo_lookup_LookupLineKey = new Parameter("LookupLineKey", ParameterTypeKind.DynamicBag, ArgumentKind.Column_Parameter0);

    private static final Parameter Geo_lookup_SourceLongitudeKey = new Parameter("SourceLongitude", ParameterTypeKind.Scalar, ArgumentKind.Column);
    private static final Parameter Geo_lookup_SourceLatitudeKey = new Parameter("SourceLatitude", ParameterTypeKind.Scalar, ArgumentKind.Column);
    private static final Parameter Geo_lookup_PolygonRadius = new Parameter("radius", ScalarTypes.Real, ArgumentKind.Literal, P.minOccurring(0)); // Can be either Literal or Column_Parameter0 // PORT: §3.12
    private static final Parameter Geo_lookup_LineRadius = new Parameter("radius", ScalarTypes.Real, ArgumentKind.Literal); // Can be either Literal or Column_Parameter0

    private static final Parameter Geo_lookup_return_unmatched = new Parameter("return_unmatched", ScalarTypes.Bool, ArgumentKind.Literal, P.minOccurring(0)); // PORT: §3.12
    private static final Parameter Geo_lookup_area_radius = new Parameter("lookup_area_radius", ScalarTypes.Real, ArgumentKind.Literal, P.minOccurring(0)); // PORT: §3.12
    private static final Parameter Geo_lookup_return_key = new Parameter("return_lookup_key", ScalarTypes.Bool, ArgumentKind.Literal, P.minOccurring(0)); // PORT: §3.12

    public static final FunctionSymbol Geo_Polygon_Lookup = new FunctionSymbol(
        "geo_polygon_lookup",
        new Signature(
            context -> // PORT: §3.8
            {
                var lookupTable = context.getArgument(Geo_lookup_LookupTable.name()) instanceof Expression lookupArg && lookupArg.resultType() instanceof TableSymbol lt ? lt : null; // PORT: §3.14 ?.; §3.15 as
                if (lookupTable != null)
                {
                    var cols = new ArrayList<ColumnSymbol>();
                    cols.addAll(context.rowScope().columns());

                    if (isGeoLookupShouldReturnLookupKey(context, Geo_lookup_LookupPolygonKey.name()))
                    {
                        cols.addAll(lookupTable.columns());
                    }
                    else
                    {
                        // Remove return_lookup_key
                        var lookupkeyName = context.getArgument(Geo_lookup_LookupPolygonKey.name()).referencedSymbol().name();
                        cols.addAll(Linq.where(lookupTable.columns(), c -> !DotNetStrings.equalsOrdinalIgnoreCase(c.name(), lookupkeyName))); // PORT: §3.6; §5.4
                    }

                    var combinedColumns = ColumnSymbol.combine(CombineKind.UniqueNames, cols);
                    return new TableSymbol(combinedColumns);
                }
                else
                {
                    // lookup table unknown, so default to input table
                    return context.rowScope();
                }
            },
            Tabularity.Tabular,
            Geo_lookup_LookupTable,
            Geo_lookup_LookupPolygonKey,
            Geo_lookup_SourceLongitudeKey,
            Geo_lookup_SourceLatitudeKey,
            Geo_lookup_PolygonRadius,
            Geo_lookup_return_unmatched,
            Geo_lookup_area_radius,
            Geo_lookup_return_key)
                .withLayout((signature, args, parameters) ->
                {
                    parameters.add(Geo_lookup_LookupTable);
                    parameters.add(Geo_lookup_LookupPolygonKey);
                    parameters.add(Geo_lookup_SourceLongitudeKey);
                    parameters.add(Geo_lookup_SourceLatitudeKey);

                    for (int i = 4; i < args.size(); i++)
                    {
                        if (args.get(i) instanceof SimpleNamedExpression sne)
                        {
                            switch (DotNetStrings.toLowerInvariant(sne.name().simpleName())) // PORT: §5.1 ToLower under invariant globalization
                            {
                                case "radius":
                                    parameters.add(Geo_lookup_PolygonRadius);
                                    continue;
                                case "return_unmatched":
                                    parameters.add(Geo_lookup_return_unmatched);
                                    continue;
                                case "lookup_area_radius":
                                    parameters.add(Geo_lookup_area_radius);
                                    continue;
                                case "return_lookup_key":
                                    parameters.add(Geo_lookup_return_key);
                                    continue;
                            }
                        }
                        else
                        {
                            switch (i)
                            {
                                case 4:
                                    parameters.add(Geo_lookup_PolygonRadius);
                                    continue;
                                case 5:
                                    parameters.add(Geo_lookup_return_unmatched);
                                    continue;
                                case 6:
                                    parameters.add(Geo_lookup_area_radius);
                                    continue;
                                case 7:
                                    parameters.add(Geo_lookup_return_key);
                                    continue;
                            }
                        }
                    }
                }));

    public static final FunctionSymbol Geo_Line_Lookup = new FunctionSymbol(
        "geo_line_lookup",
        new Signature(
            context -> // PORT: §3.8
            {
                var lookupTable = context.getArgument(Geo_lookup_LookupTable.name()) instanceof Expression lookupArg && lookupArg.resultType() instanceof TableSymbol lt ? lt : null; // PORT: §3.14 ?.; §3.15 as
                if (lookupTable != null)
                {
                    var cols = new ArrayList<ColumnSymbol>();
                    cols.addAll(context.rowScope().columns());

                    if (isGeoLookupShouldReturnLookupKey(context, Geo_lookup_LookupLineKey.name()))
                    {
                        cols.addAll(lookupTable.columns());
                    }
                    else
                    {
                        // Remove return_lookup_key
                        var lookupkeyName = context.getArgument(Geo_lookup_LookupLineKey.name()).referencedSymbol().name();
                        cols.addAll(Linq.where(lookupTable.columns(), c -> !DotNetStrings.equalsOrdinalIgnoreCase(c.name(), lookupkeyName))); // PORT: §3.6; §5.4
                    }

                    var combinedColumns = ColumnSymbol.combine(CombineKind.UniqueNames, cols);
                    return new TableSymbol(combinedColumns);
                }
                else
                {
                    // lookup table unknown, so default to input table
                    return context.rowScope();
                }
            },
            Tabularity.Tabular,
            Geo_lookup_LookupTable,
            Geo_lookup_LookupLineKey,
            Geo_lookup_SourceLongitudeKey,
            Geo_lookup_SourceLatitudeKey,
            Geo_lookup_LineRadius,
            Geo_lookup_return_unmatched,
            Geo_lookup_area_radius,
            Geo_lookup_return_key)
                .withLayout((signature, args, parameters) ->
                {
                    parameters.add(Geo_lookup_LookupTable);
                    parameters.add(Geo_lookup_LookupLineKey);
                    parameters.add(Geo_lookup_SourceLongitudeKey);
                    parameters.add(Geo_lookup_SourceLatitudeKey);
                    parameters.add(Geo_lookup_LineRadius);

                    for (int i = 5; i < args.size(); i++)
                    {
                        if (args.get(i) instanceof SimpleNamedExpression sne)
                        {
                            switch (DotNetStrings.toLowerInvariant(sne.name().simpleName())) // PORT: §5.1 ToLower under invariant globalization
                            {
                                case "return_unmatched":
                                    parameters.add(Geo_lookup_return_unmatched);
                                    continue;
                                case "lookup_area_radius":
                                    parameters.add(Geo_lookup_area_radius);
                                    continue;
                                case "return_lookup_key":
                                    parameters.add(Geo_lookup_return_key);
                                    continue;
                            }
                        }
                        else
                        {
                            switch (i)
                            {
                                case 5:
                                    parameters.add(Geo_lookup_return_unmatched);
                                    continue;
                                case 6:
                                    parameters.add(Geo_lookup_area_radius);
                                    continue;
                                case 7:
                                    parameters.add(Geo_lookup_return_key);
                                    continue;
                            }
                        }
                    }
                }));

    private static boolean isGeoLookupShouldReturnLookupKey(CustomReturnTypeContext context, String lookupKeyName)
    {
        var lookupKeyArg = context.getArgument(lookupKeyName); // PORT: §3.14 ?.
        return
            // Lookup key isn't known yet
            (lookupKeyArg != null ? lookupKeyArg.referencedSymbol() : null) == null
            || (context.arguments() != null &&
                // Boolean value of 'return_lookup_key' is set to true
                ((context.arguments().size() == 8 && context.arguments().get(7).kind() == SyntaxKind.BooleanLiteralExpression && context.arguments().get(7).constantValue() != null && (Boolean)context.arguments().get(7).constantValue())
                // Named expression value of 'return_lookup_key' is set to true
                || Linq.any(context.arguments(), arg -> arg instanceof SimpleNamedExpression sne // PORT: §3.6
                    && DotNetStrings.equalsOrdinalIgnoreCase(sne.name().simpleName(), Geo_lookup_return_key.name()) // PORT: §5.4
                    && sne.expression().kind() == SyntaxKind.BooleanLiteralExpression
                    && sne.expression().constantValue() != null
                    && (Boolean)sne.expression().constantValue())));
    }

    private static final Parameter Ipv4_lookup_LookupTable = new Parameter("LookupTable", ParameterTypeKind.Tabular);
    private static final Parameter Ipv4_lookup_SourceIPv4Key = new Parameter("SourceIPv4Key", ParameterTypeKind.Scalar, ArgumentKind.Column);
    private static final Parameter Ipv4_lookup_IPv4LookupKey = new Parameter("IPv4LookupKey", ParameterTypeKind.Scalar, ArgumentKind.Column_Parameter0);
    private static final Parameter IPv4_lookup_ExtraKey = new Parameter("ExtraKey", ParameterTypeKind.Scalar, ArgumentKind.Column_Parameter0_Common, P.minOccurring(0), P.maxOccurring(MaxRepeat)); // PORT: §3.12
    private static final Parameter IPv4_lookup_return_unmatched = new Parameter("return_unmatched", ScalarTypes.Bool, ArgumentKind.Literal, P.minOccurring(0)); // PORT: §3.12

    public static final FunctionSymbol Ipv4_Lookup =
        new FunctionSymbol("ipv4_lookup",
            new Signature(
                context -> { // PORT: §3.8
                    var lookupTable = context.getArgument(Ipv4_lookup_LookupTable.name()) instanceof Expression lookupArg && lookupArg.resultType() instanceof TableSymbol lt ? lt : null; // PORT: §3.14 ?.; §3.15 as
                    if (lookupTable != null)
                    {
                        var keyColumns = Linq.toList(Linq.where(Linq.select(context.getArguments(IPv4_lookup_ExtraKey.name()), e -> e.referencedSymbol() instanceof ColumnSymbol ecs ? ecs : null), c -> c != null)); // PORT: §3.6; §3.15 as
                        var cols = new ArrayList<ColumnSymbol>();
                        // add all left side columns
                        cols.addAll(context.rowScope().columns());
                        // add all right side columns except those used as join keys from both tables
                        cols.addAll(Linq.where(lookupTable.columns(), c -> !Linq.any(keyColumns, kc -> Objects.equals(kc.name(), c.name())))); // PORT: §3.6; §3.14 string ==
                        // make final set of columns have unique names
                        var combinedColumns = ColumnSymbol.combine(CombineKind.UniqueNames, cols);
                        return new TableSymbol(combinedColumns);
                    }
                    else
                    {
                        // lookup table unknown, so default to input table
                        return context.rowScope();
                    }
                },
                Tabularity.Tabular,
                Ipv4_lookup_LookupTable,
                Ipv4_lookup_SourceIPv4Key,
                Ipv4_lookup_IPv4LookupKey,
                IPv4_lookup_ExtraKey,
                IPv4_lookup_return_unmatched)
                .withLayout((signature, args, parameters) ->
                {
                    parameters.add(Ipv4_lookup_LookupTable);
                    parameters.add(Ipv4_lookup_SourceIPv4Key);
                    parameters.add(Ipv4_lookup_IPv4LookupKey);

                    for (int i = 3; i < args.size(); i++)
                    {
                        if (i == args.size() - 1
                            && ((args.get(i) instanceof SimpleNamedExpression sne
                                 && Objects.equals(sne.name().simpleName(), IPv4_lookup_return_unmatched.name())) // PORT: §3.14 string ==
                                || (args.get(i) instanceof LiteralExpression lit && lit.kind() == SyntaxKind.BooleanLiteralExpression)))
                        {
                            parameters.add(IPv4_lookup_return_unmatched);
                        }
                        else
                        {
                            parameters.add(IPv4_lookup_ExtraKey);
                        }
                    }
                }));

    private static final Parameter Ipv6_lookup_LookupTable = new Parameter("LookupTable", ParameterTypeKind.Tabular);
    private static final Parameter Ipv6_lookup_SourceIPv6Key = new Parameter("SourceIPv6Key", ParameterTypeKind.Scalar, ArgumentKind.Column);
    private static final Parameter Ipv6_lookup_IPv6LookupKey = new Parameter("IPv6LookupKey", ParameterTypeKind.Scalar, ArgumentKind.Column_Parameter0);
    private static final Parameter IPv6_lookup_return_unmatched = new Parameter("return_unmatched", ScalarTypes.Bool, ArgumentKind.Literal, P.minOccurring(0)); // PORT: §3.12

    public static final FunctionSymbol Ipv6_Lookup =
        new FunctionSymbol("ipv6_lookup",
            new Signature(
                context -> { // PORT: §3.8
                    var lookupTable = context.getArgument(Ipv6_lookup_LookupTable.name()) instanceof Expression lookupArg && lookupArg.resultType() instanceof TableSymbol lt ? lt : null; // PORT: §3.14 ?.; §3.15 as
                    if (lookupTable != null)
                    {
                        var cols = new ArrayList<ColumnSymbol>();
                        cols.addAll(context.rowScope().columns());
                        cols.addAll(lookupTable.columns());
                        var combinedColumns = ColumnSymbol.combine(CombineKind.UniqueNames, cols);
                        return new TableSymbol(combinedColumns);
                    }
                    else
                    {
                        // lookup table unknown, so default to input table
                        return context.rowScope();
                    }
                },
                Tabularity.Tabular,
                Ipv6_lookup_LookupTable,
                Ipv6_lookup_SourceIPv6Key,
                Ipv6_lookup_IPv6LookupKey,
                IPv6_lookup_return_unmatched)
                .withLayout((signature, args, parameters) ->
                {
                    parameters.add(Ipv6_lookup_LookupTable);
                    parameters.add(Ipv6_lookup_SourceIPv6Key);
                    parameters.add(Ipv6_lookup_IPv6LookupKey);
                    parameters.add(IPv6_lookup_return_unmatched);
                }));

    public static final FunctionSymbol SchemaMerge =
         new FunctionSymbol("schema_merge",
             new TableSymbol(new ColumnSymbol[] { // PORT: §3.10 new[] spelled out
                new ColumnSymbol("ColumnName", ScalarTypes.String),
                new ColumnSymbol("ColumnOrdinal", ScalarTypes.Int),
                new ColumnSymbol("DataType", ScalarTypes.String),
                new ColumnSymbol("ColumnType", ScalarTypes.String),
             }),
             new Parameter("PreserveOrder", ScalarTypes.Bool));

    public static final List<ColumnSymbol> NarrowColumns = Arrays.asList(new ColumnSymbol[] // PORT: §3.17 array as IReadOnlyList
    {
        new ColumnSymbol("Row", ScalarTypes.Long),
        new ColumnSymbol("Column", ScalarTypes.String),
        new ColumnSymbol("Value", ScalarTypes.String)
    });

    public static final FunctionSymbol Narrow =
        new FunctionSymbol("narrow",
             new TableSymbol(NarrowColumns));

    public static final FunctionSymbol Pivot =
         new FunctionSymbol("pivot",
             context -> // PORT: §3.8
             {
                 var pivotColumn = context.arguments().size() > 0 ? (context.arguments().get(0).referencedSymbol() instanceof ColumnSymbol pcs ? pcs : null) : null; // PORT: §3.15 as

                 var aggregateColumn =
                    context.arguments().size() > 1
                    && context.arguments().get(1) instanceof FunctionCallExpression fc
                    && fc.argumentList().expressions().size() > 0
                        ? (fc.argumentList().expressions().get(0).element().referencedSymbol() instanceof ColumnSymbol acs ? acs : null) // PORT: §3.15 as
                        : null;

                 // columns specified
                 var columns = Linq.toList(Linq.where(Linq.select(Linq.skip(context.arguments(), 2), a -> a.referencedSymbol() instanceof ColumnSymbol ccs ? ccs : null), c -> c != null)); // PORT: §3.6; §3.15 as

                 if (columns.size() == 0)
                 {
                     // all columns exept explicity mentioned pivot and aggregate column
                     columns.addAll(Linq.where(context.rowScope().columns(), c -> c != pivotColumn && c != aggregateColumn)); // PORT: §3.6
                 }

                 // pivot table is open because it has additional columns based on data values
                 return new TableSymbol(columns).withIsOpen(true);
             },
             Tabularity.Tabular,
             new Parameter("pivotColumn", ParameterTypeKind.NotDynamic, ArgumentKind.Column),
             new Parameter("aggregateFunction", ParameterTypeKind.Scalar, ArgumentKind.Aggregate, P.minOccurring(0)), // PORT: §3.12
             new Parameter("columnName", ParameterTypeKind.Scalar, ArgumentKind.Column, P.minOccurring(0), P.maxOccurring(MaxRepeat))); // PORT: §3.12

    public static final FunctionSymbol Preview =
         new FunctionSymbol("preview",
             context -> new GroupSymbol( // multiple result tables // PORT: §3.8
                 context.rowScope(),
                 new TableSymbol(new ColumnSymbol("Count", ScalarTypes.Long))),
             Tabularity.Tabular,
             new Parameter("NumberOfRows", ParameterTypeKind.Integer));

    private static TableSymbol getOutputSchema(CustomReturnTypeContext context)
    {
        if (context.arguments().size() > 0 && context.arguments().get(0).referencedSymbol() instanceof TableSymbol schema)
        {
            return schema;
        }
        else
        {
            return new TableSymbol().withIsOpen(true);
        }
    }

    public static final FunctionSymbol CSharp =
         new FunctionSymbol("csharp",
             PlugIns::getOutputSchema, // PORT: §3.8 method group
             Tabularity.Tabular,
             new Parameter("OutputSchema", ScalarTypes.Type),
             new Parameter("Script", ScalarTypes.String),
             new Parameter("Arguments", ParameterTypeKind.DynamicBag, P.minOccurring(0))); // PORT: §3.12

    public static final FunctionSymbol Python =
         new FunctionSymbol("python",
             PlugIns::getOutputSchema, // PORT: §3.8 method group
             Tabularity.Tabular,
             new Parameter("OutputSchema", ScalarTypes.Type),
             new Parameter("Script", ScalarTypes.String),
             new Parameter("Arguments", ParameterTypeKind.DynamicBag, P.minOccurring(0)), // PORT: §3.12
             new Parameter("Artifacts", ParameterTypeKind.DynamicBag, P.minOccurring(0))); // PORT: §3.12

    public static final FunctionSymbol R =
         new FunctionSymbol("r",
             PlugIns::getOutputSchema, // PORT: §3.8 method group
             Tabularity.Tabular,
             new Parameter("OutputSchema", ScalarTypes.Type),
             new Parameter("Script", ScalarTypes.String),
             new Parameter("Arguments", ParameterTypeKind.DynamicBag, P.minOccurring(0))); // PORT: §3.12

    public static final FunctionSymbol RollingPercentile =
         new FunctionSymbol("rolling_percentile",
             context -> // PORT: §3.8
             {
                 var cols = new ArrayList<ColumnSymbol>();
                 addReferencedColumn(cols, context, "IndexColumn");
                 addReferencedColumns(cols, context, "Dimension");
                 var binsPerWindow = context.getArgument("BinsPerWindow") instanceof Expression bpw && bpw.literalValue() != null ? DotNet.str(bpw.literalValue()) : "0"; // PORT: §3.14 ?. ??; §5.2 ToString
                 var percentile = context.getArgument("Percentile") instanceof Expression pa && pa.literalValue() != null ? DotNet.str(pa.literalValue()) : "0"; // PORT: §3.14 ?. ??; §5.2 ToString
                 var valueColumn = context.getArgument("ValueColumn") instanceof Expression va && va.referencedSymbol() instanceof ColumnSymbol vcs ? vcs : null; // PORT: §3.14 ?.; §3.15 as
                 cols.add(new ColumnSymbol("rolling_" + binsPerWindow + "_percentile_" + (valueColumn != null && valueColumn.name() != null ? valueColumn.name() : "value") + "_" + percentile, valueColumn != null && valueColumn.type() != null ? valueColumn.type() : ScalarTypes.Long)); // PORT: §3.14 interpolation, ?. ??
                 return new TableSymbol(cols);
             },
             Tabularity.Tabular,
             new Parameter("ValueColumn", ParameterTypeKind.Summable, ArgumentKind.Column),
             new Parameter("Percentile", ParameterTypeKind.Number, ArgumentKind.Constant),
             new Parameter("IndexColumn", ParameterTypeKind.Summable, ArgumentKind.Column),
             new Parameter("BinSize", ParameterTypeKind.Summable),
             new Parameter("BinsPerWindow", ParameterTypeKind.Integer, ArgumentKind.Constant),
             new Parameter("Dimension", ParameterTypeKind.NotDynamic, ArgumentKind.Column, P.minOccurring(0), P.maxOccurring(MaxRepeat)) // PORT: §3.12
             );

    public static final FunctionSymbol RowsNear =
         new FunctionSymbol("rows_near",
            context -> context.rowScope(), // PORT: §3.8
            Tabularity.Tabular,
            new Parameter("Condition", ScalarTypes.Bool, ArgumentKind.Expression),
            new Parameter("NumRows", ParameterTypeKind.Integer, ArgumentKind.Constant),
            new Parameter("NumRowsAfter", ParameterTypeKind.Integer, ArgumentKind.Constant, P.minOccurring(0))); // PORT: §3.12

    public static final FunctionSymbol SessionCount =
         new FunctionSymbol("session_count",
             context -> // PORT: §3.8
             {
                 var cols = new ArrayList<ColumnSymbol>();
                 addReferencedColumn(cols, context, "TimelineColumn");
                 addReferencedColumns(cols, context, "Dimension");
                 cols.add(new ColumnSymbol("count_sessions", ScalarTypes.Long));
                 return new TableSymbol(cols);
             },
             Tabularity.Tabular,
             new Parameter("IdColumn", ParameterTypeKind.NotDynamic, ArgumentKind.Column),
             new Parameter("TimelineColumn", ParameterTypeKind.Summable, ArgumentKind.Column),
             new Parameter("Start", ParameterTypeKind.Summable, ArgumentKind.Constant),
             new Parameter("End", ParameterTypeKind.Summable, ArgumentKind.Constant),
             new Parameter("Bin", ParameterTypeKind.Summable, ArgumentKind.Constant),
             new Parameter("LookBackWindow", ParameterTypeKind.Summable, ArgumentKind.Constant),
             new Parameter("Dimension", ParameterTypeKind.NotDynamic, ArgumentKind.Column, P.minOccurring(0), P.maxOccurring(MaxRepeat))); // PORT: §3.12


    private static final Parameter SD_TimelineColumn = new Parameter("TimelineColumn", ParameterTypeKind.Summable, ArgumentKind.Column);
    private static final Parameter SD_MaxSequenceStepWindows = new Parameter("MaxSeqeunceStepWindow", ParameterTypeKind.Summable, ArgumentKind.Constant);
    private static final Parameter SD_MaxSequenceSpan = new Parameter("MaxSequenceSpan", ParameterTypeKind.Summable, ArgumentKind.Constant);
    private static final Parameter SD_Expr = new Parameter("Expr", ScalarTypes.Bool, ArgumentKind.Expression, P.minOccurring(1), P.maxOccurring(MaxRepeat)); // PORT: §3.12
    private static final Parameter SD_Dimension = new Parameter("Dimension", ParameterTypeKind.NotDynamic, ArgumentKind.Column, P.minOccurring(0), P.maxOccurring(MaxRepeat)); // PORT: §3.12

    public static final FunctionSymbol SequenceDetect =
         new FunctionSymbol("sequence_detect",
             new Signature(
                 context -> // PORT: §3.8
                 {
                     var cols = new ArrayList<ColumnSymbol>();

                     addReferencedColumns(cols, context, SD_Dimension.name());

                     var timelineArg = context.getArgument(SD_TimelineColumn.name());
                     if (timelineArg != null)
                     {
                         var timelineArgName = context.getResultName(timelineArg);

                         cols.addAll(Linq.select(context.getArguments(SD_Expr.name()), a -> // PORT: §3.6
                            new ColumnSymbol(makeColumnName(context.getResultName(a), timelineArgName), timelineArg.resultType())));
                     }

                     cols.add(new ColumnSymbol("Duration", ScalarTypes.TimeSpan));
                     return new TableSymbol(cols);
                 },
                 Tabularity.Tabular,
                 SD_TimelineColumn,
                 SD_MaxSequenceStepWindows,
                 SD_MaxSequenceSpan,
                 SD_Expr,
                 SD_Dimension)
             .withLayout((sig, args, list) ->
             {
                 list.add(SD_TimelineColumn);
                 list.add(SD_MaxSequenceStepWindows);
                 list.add(SD_MaxSequenceSpan);
                 list.add(SD_Expr); // first expr required

                 int i = list.size();

                 // any following bool args are also expr args
                 for (; i < args.size() && isBoolean(args.get(i)); i++)
                 {
                     list.add(SD_Expr);
                 }

                 // all remaining args are dimensions
                 for (; i < args.size(); i++)
                 {
                     list.add(SD_Dimension);
                 }
             }));

    public static final FunctionSymbol SlidingWindowCounts =
         new FunctionSymbol("sliding_window_counts",
             context -> // PORT: §3.8
             {
                 var cols = new ArrayList<ColumnSymbol>();
                 addReferencedColumn(cols, context, "TimelineColumn");
                 addReferencedColumns(cols, context, "Dimension");
                 cols.add(new ColumnSymbol("Count", ScalarTypes.Long));
                 cols.add(new ColumnSymbol("Dcount", ScalarTypes.Long));
                 return new TableSymbol(cols);
             },
             Tabularity.Tabular,
             new Parameter("IdColumn", ParameterTypeKind.NotDynamic, ArgumentKind.Column),
             new Parameter("TimelineColumn", ParameterTypeKind.Summable, ArgumentKind.Column),
             new Parameter("Start", ParameterTypeKind.Summable, ArgumentKind.Constant),
             new Parameter("End", ParameterTypeKind.Summable, ArgumentKind.Constant),
             new Parameter("LookBackWindow", ParameterTypeKind.Summable, ArgumentKind.Constant),
             new Parameter("Step", ParameterTypeKind.Summable, ArgumentKind.Constant),
             new Parameter("Dimension", ParameterTypeKind.NotDynamic, ArgumentKind.Column, P.minOccurring(0), P.maxOccurring(MaxRepeat))); // PORT: §3.12

    public static final FunctionSymbol SqlRequest =
         new FunctionSymbol("sql_request",
             context -> new TableSymbol().withIsOpen(true), // the schema comes from the database at runtime // PORT: §3.8
             Tabularity.Tabular,
             new Parameter("connection_string", ScalarTypes.String),
             new Parameter("sql_query", ScalarTypes.String),
             new Parameter("sql_parameters", ParameterTypeKind.DynamicBag, P.minOccurring(0)), // PORT: §3.12
             new Parameter("options", ParameterTypeKind.DynamicBag, P.minOccurring(0))); // PORT: §3.12

    public static final FunctionSymbol MySqlRequest =
         new FunctionSymbol("mysql_request",
             context -> new TableSymbol().withIsOpen(true), // the schema comes from the database at runtime // PORT: §3.8
             Tabularity.Tabular,
             new Parameter("connection_string", ScalarTypes.String),
             new Parameter("sql_query", ScalarTypes.String),
             new Parameter("sql_parameters", ParameterTypeKind.DynamicBag, P.minOccurring(0)), // PORT: §3.12
             new Parameter("options", ParameterTypeKind.DynamicBag, P.minOccurring(0))); // PORT: §3.12

    public static final FunctionSymbol PostgreSqlRequest =
       new FunctionSymbol("postgresql_request",
           context -> new TableSymbol().withIsOpen(true), // the schema comes from the database at runtime // PORT: §3.8
           Tabularity.Tabular,
           new Parameter("connection_string", ScalarTypes.String),
           new Parameter("sql_query", ScalarTypes.String),
           new Parameter("sql_parameters", ParameterTypeKind.DynamicBag, P.minOccurring(0)), // PORT: §3.12
           new Parameter("options", ParameterTypeKind.DynamicBag, P.minOccurring(0))) // PORT: §3.12
        .hide(); // Open once service rollout completes

    public static final FunctionSymbol CosmosdbSqlRequest =
         new FunctionSymbol("cosmosdb_sql_request",
             context -> new TableSymbol().withIsOpen(true), // the schema comes from the cosmos database at runtime // PORT: §3.8
             Tabularity.Tabular,
             new Parameter("connection_string", ScalarTypes.String),
             new Parameter("sql_query", ScalarTypes.String),
             new Parameter("sql_parameters", ParameterTypeKind.DynamicBag, P.minOccurring(0)), // PORT: §3.12
             new Parameter("options", ParameterTypeKind.DynamicBag, P.minOccurring(0)) // PORT: §3.12
             );

    public static final FunctionSymbol DaxRequest =
         new FunctionSymbol("dax_request",
             context -> new TableSymbol().withIsOpen(true), // the schema comes from the semantic model at runtime // PORT: §3.8
             Tabularity.Tabular,
             new Parameter("connection_string", ScalarTypes.String),
             new Parameter("dax_query", ScalarTypes.String),
             new Parameter("dax_parameters", ParameterTypeKind.DynamicBag, P.minOccurring(0)), // PORT: §3.12
             new Parameter("options", ParameterTypeKind.DynamicBag, P.minOccurring(0))) // PORT: §3.12
         .hide(); // Open once service rollout completes

    public static final FunctionSymbol GqlRequest =
         new FunctionSymbol("gql_request",
             context -> new TableSymbol().withIsOpen(true), // the schema comes from the graph model at runtime // PORT: §3.8
             Tabularity.Tabular,
             new Parameter("connection_string", ScalarTypes.String),
             new Parameter("gql_query", ScalarTypes.String),
             new Parameter("gql_parameters", ParameterTypeKind.DynamicBag, P.minOccurring(0)), // PORT: §3.12
             new Parameter("options", ParameterTypeKind.DynamicBag, P.minOccurring(0))) // PORT: §3.12
         .hide(); // Open once service rollout completes

    public static final FunctionSymbol AzureDigitalTwinsQueryRequest =
                 new FunctionSymbol("azure_digital_twins_query_request",
                     context -> new TableSymbol().withIsOpen(true), // depends on the SELECT command provided // PORT: §3.8
                     Tabularity.Tabular,
                     new Parameter("endpoint", ScalarTypes.String),
                     new Parameter("sql_query", ScalarTypes.String)
                     );

    public static final List<FunctionSymbol> All = Arrays.asList(new FunctionSymbol[] // PORT: §3.1 static get-only auto-property { get; } = … → public static final field (no setter, initialised once; matches Functions.All); §3.17 array as IReadOnlyList
    {
        ActiveUseCounts,
        ActivityCountsMetrics,
        ActivityEngagement,
        ActivityMetrics,
        AzureDigitalTwinsQueryRequest,
        AutoCluster,
        BagUnpack,
        Basket,
        CosmosdbSqlRequest,
        CSharp,
        DCountIntersect,
        DiffPatterns,
        EstimateRowsCount,
        ExecuteShowCommand,
        ExecuteQuery,
        ExternalDatatable,
        // FunnelAnalysis,
        FunnelSequence,
        FunnelSequenceCompletion,
        HttpRequest,
        HttpRequestPost,
        Identity,
        IdentityV3,
        InferStorageSchema,
        InferStorageSchemaWithSuggestions,
        Geo_Polygon_Lookup,
        Geo_Line_Lookup,
        Ipv4_Lookup,
        Ipv6_Lookup,
        Narrow,
        NewActivityMetrics,
        Pivot,
        Preview,
        Python,
        R,
        RollingPercentile,
        RowsNear,
        SessionCount,
        SequenceDetect,
        SlidingWindowCounts,
        SqlRequest,
        MySqlRequest,
        PostgreSqlRequest,
        DaxRequest,
        GqlRequest,
        AIEmbedText_Deprecated,
        AIChatCompletion,
        AIChatCompletionPrompt,
        AIEmbeddings,
    });

    private static volatile LinkedHashMap<String, FunctionSymbol> s_nameToPlugInMap; // PORT: §3.17 Dictionary; §3.13 volatile so the racy lazy init publishes a fully built map

    /// <summary>
    /// Gets the plug-in function given the name, or null if no plug-in is defined with the specified name.
    /// </summary>
    public static FunctionSymbol getPlugIn(String name)
    {
        if (s_nameToPlugInMap == null)
        {
            s_nameToPlugInMap = Linq.toDictionary(All, f -> f.name()); // PORT: §3.6 (throws on a duplicate name, like ToDictionary)
        }

        var fn = s_nameToPlugInMap.get(Objects.requireNonNull(name, "key")); // PORT: §3.3 TryGetValue; §3.16 a null key throws ArgumentNullException
        return fn;
    }
}
