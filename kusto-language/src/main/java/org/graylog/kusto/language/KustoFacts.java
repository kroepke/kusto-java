// Ported from: src/Kusto.Language/Parser/KustoFacts.cs
// Ported from: src/Kusto.Language/Parser/KustoFacts_Keywords.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.graylog.kusto.language.parsing.TextFacts;
import org.graylog.kusto.language.parsing.TokenParser;
import org.graylog.kusto.language.symbols.ScalarSymbol;
import org.graylog.kusto.language.symbols.ScalarTypes;
import org.graylog.kusto.language.symbols.TableSymbol;
import org.graylog.kusto.language.symbols.Tabularity;
import org.graylog.kusto.language.syntax.BetweenExpression;
import org.graylog.kusto.language.syntax.BinaryExpression;
import org.graylog.kusto.language.syntax.BracketedExpression;
import org.graylog.kusto.language.syntax.Command;
import org.graylog.kusto.language.syntax.CompoundStringLiteralExpression;
import org.graylog.kusto.language.syntax.DataTableExpression;
import org.graylog.kusto.language.syntax.DynamicExpression;
import org.graylog.kusto.language.syntax.ElementExpression;
import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.syntax.ExpressionStatement;
import org.graylog.kusto.language.syntax.FacetWithExpressionClause;
import org.graylog.kusto.language.syntax.FacetWithOperatorClause;
import org.graylog.kusto.language.syntax.ForkExpression;
import org.graylog.kusto.language.syntax.FunctionCallExpression;
import org.graylog.kusto.language.syntax.HasAllExpression;
import org.graylog.kusto.language.syntax.HasAnyExpression;
import org.graylog.kusto.language.syntax.InExpression;
import org.graylog.kusto.language.syntax.LiteralExpression;
import org.graylog.kusto.language.syntax.MakeGraphPartitionedByClause;
import org.graylog.kusto.language.syntax.MaterializedViewCombineClause;
import org.graylog.kusto.language.syntax.MaterializedViewCombineExpression;
import org.graylog.kusto.language.syntax.MvApplySubqueryExpression;
import org.graylog.kusto.language.syntax.PartitionByOperator;
import org.graylog.kusto.language.syntax.PartitionSubquery;
import org.graylog.kusto.language.syntax.PipeExpression;
import org.graylog.kusto.language.syntax.PrefixUnaryExpression;
import org.graylog.kusto.language.syntax.QueryOperator;
import org.graylog.kusto.language.syntax.SeparatedElement1;
import org.graylog.kusto.language.syntax.Statement;
import org.graylog.kusto.language.syntax.SyntaxFacts;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.graylog.kusto.language.syntax.SyntaxList1;
import org.graylog.kusto.language.syntax.ToScalarExpression;
import org.graylog.kusto.language.syntax.ToTableExpression;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetChars;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.IntRef;
import org.graylog.kusto.language.utils.dotnet.Linq;
import org.graylog.kusto.language.utils.dotnet.Out;

public final class KustoFacts
{
    private KustoFacts() // PORT: §3.5 static class
    {
    }

    // ===== upstream part: KustoFacts.cs =====

    /// <summary>
    /// Types allowed for function parameters and columns.
    /// </summary>
    public static final List<String> ParamTypes = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "bool",
        "boolean",
        "date",
        "datetime",
        "decimal",
        "double",
        "dynamic",
        "guid",
        "int",
        "int64",
        "int8",
        "long",
        "real",
        "string",
        "time",
        "timespan",
        "uniqueid"
    });

    /// <summary>
    /// Extended type allowed in some schema declarations.
    /// </summary>
    public static final List<String> ExtendedParamTypes = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "bool",
        "boolean",
        "date",
        "datetime",
        "decimal",
        "double",
        "dynamic",
        "float",
        "guid",
        "int",
        "int16",
        "int32",
        "int64",
        "int8",
        "long",
        "real",
        "decimal",
        "string",
        "time",
        "timespan",
        "uint",
        "uint16",
        "uint32",
        "uint64",
        "uint8",
        "ulong",
        "uniqueid"
    });

    public static final List<String> StorageTypes =
        ExtendedParamTypes;


    private static List<String> _knownQueryOperatorParameterNames;

    // PORT: §3.9 stays lazy: the only static-init cycle (KustoFacts ⇄ QueryOperatorParameters); class init never touches W3 types
    public static List<String> knownQueryOperatorParameterNames()
    {
        // defer calculation of this list to avoid cycle with QueryOperatorParameters
        if (_knownQueryOperatorParameterNames == null)
        {
            // PORT: §3.6, D12 (OrderBy(n => n) is culture-sensitive upstream, ordinal here; order not observable, traps.md)
            _knownQueryOperatorParameterNames =
                Linq.orderBy(
                    Linq.selectMany(QueryOperatorParameters.AllParameters, p -> Linq.concat(Arrays.asList(new String[] { p.name() }), p.aliases())),
                    n -> n);
        }

        return _knownQueryOperatorParameterNames;
    }

    public static final List<String> ChartTypes = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "table", "list", "barchart", "piechart", "ladderchart", "timechart", "linechart", "anomalychart", "pivotchart", "areachart",
        "stackedareachart", "scatterchart", "timepivot", "columnchart", "timeline", "3Dchart", "card", "treemap", "plotly", "graph", "sankey"
    });

    /// <summary>
    /// Chart types not shown in intellisense
    /// </summary>
    public static final List<String> HiddenChartTypes = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "3Dchart",
        "sankey", // TODO: unhide it once service-side supports this
        "graph" // TODO: unhide it once service-side supports this
    });

    /// <summary>
    /// Char types shown in intellisense
    /// </summary>
    public static final List<String> VisibleChartTypes =
        Linq.where(ChartTypes, c -> !HiddenChartTypes.contains(c)); // PORT: §3.6

    public static final List<String> ChartProperties = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "title", "xcolumn", "series", "ycolumns", "anomalycolumns", "kind", "xtitle", "ytitle", "xaxis", "yaxis", "legend", "ysplit", "accumulate", "ymin", "ymax", "xmax", "xmin"
    });

    public static final List<String> ChartKinds = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "default", "unstacked", "stacked", "stacked100", "map"
    });

    public static final List<String> ChartLegends = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "visible", "hidden"
    });

    public static final List<String> ChartAxis = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "linear", "log"
    });

    public static final List<String> ChartYSplit = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "none", "axes", "panels"
    });

    public static final List<String> HintDistributions = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "single", "per_node", "per_shard", "default"
    });

    public static final List<String> HintRemotes = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "auto", "local"
    });

    public static final List<String> HintStrategies = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "auto", "broadcast", "centralized", "shuffle"
    });

    public static final List<String> HintSpreads = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "auto", "local"
    });

    public static final List<String> HintConcurrencies = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "auto", "left", "local", "right", "unresolved"
    });

    public static final List<String> DistinctHintStrategies = HintStrategies;

    public static final List<String> EvaluateHintDistributions = HintDistributions;
    public static final List<String> EvaluateHintRemotes = HintRemotes;

    public static final List<String> JoinKinds = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "inner", "fullouter", "innerunique", "leftanti", "leftantisemi", "anti",
        "leftouter", "leftsemi", "rightanti", "rightantisemi", "rightouter", "rightsemi"
    });

    public static final List<String> GraphMarkComponentsKinds = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
       "weak", "strong"
    });

    public static final List<String> JoinHintRemotes = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "auto", "left", "local", "right", "unresolved"
    });

    public static final List<String> JoinHintStrategies = HintStrategies;

    public static final List<String> LookupKinds = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "inner", "leftouter"
    });

    public static final List<String> MakeSeriesKinds = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "nonempty"
    });

    public static final List<String> MvExpandKinds = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "bag", "array"
    });

    public static final List<String> InlineExternalTableKinds = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "storage", "delta"
    });

    public static final List<String> InlineExternalTableDataFormats = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "parquet", "avro", "csv", "tsv", "json", "orc", "txt"
    });

    public static final List<String> InlineExternalTablePartitionColumnFunctions = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "hash", "bin", "startofday", "startofweek", "startofmonth", "startofyear"
    });

    public static final List<String> PartitionHintConcurrencies = HintConcurrencies;
    public static final List<String> PartitionHintSpreads = HintSpreads;

    public static final List<String> PartitionHintStrategies = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "shuffle", "native", "legacy"
    });

    public static final List<String> PartitionedGraphMakeHintStrategies = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "shuffle", "native"
    });

    public static final List<String> ReduceByKinds = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "source"
    });

    public static final List<String> SearchKinds = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "default", "case_insensitive", "case_sensitive"
    });

    public static final List<String> SortHintStrategies = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "splitBlock", "multipleBlocks"
    });

    public static final List<String> SummarizeHintStrategies = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "shuffle"
    });

    public static final List<String> UnionWithSourceProperties = Arrays.asList(new String[] { // PORT: §3.17 array as IReadOnlyList
        "withsource", "with_source"
    });

    public static String unionIsFuzzyProperty() { return "isfuzzy"; }

    public static final List<String> UnionKinds = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "inner", "outer"
    });

    public static final List<String> CyclesKinds = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "none", "all", "unique_edges"
    });

    public static final List<String> ShortestPathsOutputs = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "any", "all"
    });

    public static final List<String> UnionHintConcurrencies = HintConcurrencies;
    public static final List<String> UnionHintSpreads = HintSpreads;

    public static final List<String> ParseKinds = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "simple", "regex", "relaxed"
    });

    public static final List<String> DataScopeValues = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "all", "hotcache"
    });

    public static final List<String> ScanKinds = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "partial",
        "full"
    });

    public static final List<String> ScanStepOutputValues = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "all", "last", "none"
    });

    public static final List<String> ToScalarKinds = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "nooptimization"
    });

    public static final List<String> ToTableKinds = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "nooptimization"
    });

    public static final List<String> LimitExamples = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "10", "100", "1000"
    });

    public static final List<String> TopExamples = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "10", "100", "1000"
    });

    public static final List<String> AgoExamples = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "30min", "1h", "1d"
    });

    public static final List<String> KnownInternalFunctionNames = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "__blackbox",
        "__box",
        "__cast",
        "__columnifexists",
        "__const_cast",
        "__fetch_contextual_scalar_value",
        "__geo_line_validate",
        "__geo_polygon_validate",
        "__getobject",
        "__getobjectex",
        "__has_ipv4",
        "__has_ipv4_prefix",
        "__hash_crc32",
        "__hash_djb2",
        "__hash_many_crc32",
        "__hash_xxxh64",
        "__invoke",
        "__is_scalar",
        "__lz4_compress_dynamic_array_to_base64_string",
        "__make_const",
        "__null",
        "__null_as",
        "__object",
        "__query_parameter",
        "__regex_complexity",
        "__row_offset",
        "__rowstore_ref",
        "__search_wildcard",
        "__search_wildcard_explicit_cols",
        "__search_wildcard_explicit_cols_v35",
        "__shard_record_position",
        "__sql_add",
        "__sql_divide",
        "__sql_modulo",
        "__sql_multiply",
        "__sql_subtract",
        "__trace_information",
        "__var",
        "__warning",
        "__get_scalar",
    });

    public static final List<String> DateTimeParts = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "year",
        "quarter",
        "month",
        "week_of_year",
        "day",
        "dayofyear",
        "hour",
        "minute",
        "second",
        "millisecond",
        "microsecond",
        "nanosecond"
    });

    public static final List<String> DateDiffParts = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "year",
        "quarter",
        "month",
        "week",
        "day",
        "hour",
        "minute",
        "second",
        "millisecond",
        "microsecond",
        "nanosecond"
    });

    /// <summary>
    /// Directive names possibly supported by the client.
    /// </summary>
    public static final List<String> Directives = Arrays.asList(new String[] // PORT: §3.17 array as IReadOnlyList
    {
        "automate",
        "browse",       // open browser to link
        "checkconnectivity", // check connectivity to a cluster
        "connect",      // set connection for script or query
        "connectivitycheck", // check connectivity to a cluster
        "crp",          // set client request properties for script or query
        "database",     // set default database (and cluster) for query
        "download",
        "qp",           // set query parameters for script or query
        "query",
        "run",          // execute expression (via print operator)
        "save",         // run query & save results to local file
        "sqr",          // run query & save results to server
        "truesight",
        "upload",
        "welcome"       // show welcome message
    });

    /// <summary>
    /// Keywords that can be used as identifiers everywhere.
    /// </summary>
    public static final List<SyntaxKind> KeywordsAsIdentifiers =
        Linq.where(SyntaxFacts.getKindsWithFixedText(), k -> SyntaxFacts.isKeyword(k) && SyntaxFacts.canBeIdentifier(k)); // PORT: §3.6, §3.5

    /// <summary>
    /// Keywords that can be used as identifiers in some additional locations in queries.
    /// </summary>
    public static final List<SyntaxKind> ExtendedKeywordsAsIdentifiers =
        Linq.concat(KeywordsAsIdentifiers, // PORT: §3.6
            Arrays.asList(new SyntaxKind[] // PORT: §3.17 array as IReadOnlyList
            {
                SyntaxKind.AccumulateKeyword,
                SyntaxKind.AsKeyword,
                SyntaxKind.ByKeyword,
                SyntaxKind.ContainsKeyword,
                SyntaxKind.ConsumeKeyword,
                SyntaxKind.CountKeyword,
                SyntaxKind.DataTableKeyword,
                SyntaxKind.DistinctKeyword,
                SyntaxKind.EarliestKeyword,
                SyntaxKind.ExtendKeyword,
                SyntaxKind.ExternalDataKeyword,
                SyntaxKind.InlineExternalTableKeyword,
                SyntaxKind.DataFormatKeyword,
                SyntaxKind.DateTimePatternKeyword,
                // FALSE?? How can this be a keyword it is already a literal
                SyntaxKind.FindKeyword,
                SyntaxKind.FilterKeyword,
                SyntaxKind.HasKeyword,
                SyntaxKind.InKeyword,
                SyntaxKind.InvokeKeyword,
                SyntaxKind.LatestKeyword,
                SyntaxKind.LimitKeyword,
                SyntaxKind.MaterializeKeyword,
                SyntaxKind.MdmKeyword,
                SyntaxKind.OfKeyword,
                SyntaxKind.ParseKeyword,
                SyntaxKind.PlotlyKeyword,
                SyntaxKind.PrintKeyword,
                SyntaxKind.SampleKeyword,
                SyntaxKind.SampleDistinctKeyword,
                SyntaxKind.ScanKeyword,
                SyntaxKind.SearchKeyword,
                SyntaxKind.SerializeKeyword,
                SyntaxKind.SetKeyword,
                SyntaxKind.SortKeyword,
                SyntaxKind.SqlKeyword,
                SyntaxKind.SummarizeKeyword,
                SyntaxKind.TakeKeyword,
                SyntaxKind.TitleKeyword,
                SyntaxKind.ToKeyword,
                SyntaxKind.TopKeyword,
                SyntaxKind.ToScalarKeyword,
                SyntaxKind.ToTableKeyword,
                SyntaxKind.TopNestedKeyword,
                SyntaxKind.TopHittersKeyword,
                SyntaxKind.VerboseKeyword,
                SyntaxKind.ViewersKeyword,
                SyntaxKind.WhereKeyword
            }));

    /// <summary>
    /// Keywords that can be used as identifiers in some distinct locations in queries.
    /// </summary>
    public static final List<SyntaxKind> SpecialKeywordsAsIdentifiers = Arrays.asList(new SyntaxKind[] // PORT: §3.17 array as IReadOnlyList
    {
        SyntaxKind.KindKeyword,
        SyntaxKind.WithSourceKeyword,
        SyntaxKind.With_SourceKeyword
    });

    public static final List<SyntaxKind> ForkOperatorKinds = Arrays.asList(new SyntaxKind[] // PORT: §3.17 array as IReadOnlyList
    {
        SyntaxKind.AsOperator,
        SyntaxKind.CountOperator,
        SyntaxKind.DistinctOperator,
        SyntaxKind.ExecuteAndCacheOperator,
        SyntaxKind.ExtendOperator,
        SyntaxKind.FilterOperator,
        SyntaxKind.InvokeOperator,
        SyntaxKind.MvExpandOperator,
        SyntaxKind.ParseOperator,
        SyntaxKind.ParseWhereOperator,
        SyntaxKind.ParseKvOperator,
        SyntaxKind.ProjectOperator,
        SyntaxKind.ProjectAwayOperator,
        SyntaxKind.ProjectByNamesOperator,
        SyntaxKind.ProjectKeepOperator,
        SyntaxKind.ProjectRenameOperator,
        SyntaxKind.ProjectReorderOperator,
        SyntaxKind.ReduceByOperator,
        SyntaxKind.SampleDistinctOperator,
        SyntaxKind.SampleOperator,
        SyntaxKind.SortOperator,
        SyntaxKind.SummarizeOperator,
        SyntaxKind.TakeOperator,
        SyntaxKind.TopHittersOperator,
        SyntaxKind.TopNestedOperator,
        SyntaxKind.TopOperator
    });

    /// <summary>
    /// Query operators that can come after a pipe
    /// </summary>
    public static final List<SyntaxKind> PostPipeOperatorKinds = Arrays.asList(new SyntaxKind[] // PORT: §3.17 array as IReadOnlyList
    {
        SyntaxKind.AsOperator,
        SyntaxKind.ConsumeOperator,
        SyntaxKind.CountOperator,
        SyntaxKind.DistinctOperator,
        SyntaxKind.EvaluateOperator,
        SyntaxKind.ExecuteAndCacheOperator,
        SyntaxKind.ExtendOperator,
        SyntaxKind.FacetOperator,
        SyntaxKind.FilterOperator,
        SyntaxKind.FindOperator,
        SyntaxKind.ForkOperator,
        SyntaxKind.GetSchemaOperator,
        SyntaxKind.GraphMatchOperator,
        SyntaxKind.GraphToTableOperator,
        SyntaxKind.InvokeOperator,
        SyntaxKind.JoinOperator,
        SyntaxKind.LookupOperator,
        SyntaxKind.MakeSeriesOperator,
        SyntaxKind.MakeGraphOperator,
        SyntaxKind.MvApplyOperator,
        SyntaxKind.MvExpandOperator,
        SyntaxKind.ParseOperator,
        SyntaxKind.ParseWhereOperator,
        SyntaxKind.ParseKvOperator,
        SyntaxKind.PartitionOperator,
        SyntaxKind.PartitionByOperator,
        SyntaxKind.ProjectAwayOperator,
        SyntaxKind.ProjectByNamesOperator,
        SyntaxKind.ProjectKeepOperator,
        SyntaxKind.ProjectOperator,
        SyntaxKind.ProjectRenameOperator,
        SyntaxKind.ProjectReorderOperator,
        SyntaxKind.ReduceByOperator,
        SyntaxKind.RenderOperator,
        SyntaxKind.SampleDistinctOperator,
        SyntaxKind.SampleOperator,
        SyntaxKind.ScanOperator,
        SyntaxKind.SearchOperator,
        SyntaxKind.SerializeOperator,
        SyntaxKind.SummarizeOperator,
        SyntaxKind.TakeOperator,
        SyntaxKind.SortOperator,
        SyntaxKind.TopHittersOperator,
        SyntaxKind.TopOperator,
        SyntaxKind.TopNestedOperator,
        SyntaxKind.UnionOperator
    });

    /// <summary>
    /// True if the query operator is on the right side of a pipe expression
    /// or is in a context that allows operators that would normally only appear
    /// on the right side of a pipe expression.
    /// </summary>
    public static boolean hasPipedInput(QueryOperator queryOp)
    {
        return (queryOp.parent() instanceof PipeExpression pe && pe.operator() == queryOp)
            || isChildOfPipeStartingExpression(queryOp);
    }

    private static boolean isChildOfPipeStartingExpression(Expression expr)
    {
        return (expr.parent() instanceof ForkExpression fce && fce.expression() == expr)
            || (expr.parent() instanceof PartitionSubquery ps && ps.subquery() == expr)
            || (expr.parent() instanceof MvApplySubqueryExpression mvas && mvas.expression() == expr)
            || (expr.parent() instanceof PartitionByOperator pbo && pbo.subquery() == expr)
            || (expr.parent() instanceof MakeGraphPartitionedByClause mgpb && mgpb.subquery() == expr)
            || (expr.parent() instanceof FacetWithExpressionClause fwce && fwce.expression() == expr)
            || (expr.parent() instanceof FacetWithOperatorClause fwoc && fwoc.operator() == expr)
            || (expr.parent() instanceof Expression pe && isChildOfPipeStartingExpression(pe))
            || (expr.parent() instanceof MaterializedViewCombineClause mvc && mvc.parent() instanceof MaterializedViewCombineExpression mve && mve.aggregationsClause() == mvc);
    }

    /// <summary>
    /// True if the text can be used as an identifier everywhere in the specified language dialect.
    /// </summary>
    public static boolean canBeIdentifier(String text, KustoDialect dialect)
    {
        // bad input is bad
        if (DotNetStrings.isNullOrEmpty(text))
            return false;

        // disallow some otherwise legal identifiers that start with numbers
        if (TextFacts.isDigit(text.charAt(0)))
            return false;

        // does it even look like an identifier?
        if (TokenParser.scanIdentifier(text) != text.length())
            return false;

        // is it actually a boolean literal?
        if (TokenParser.scanBooleanLiteral(text) == text.length())
            return false;

        // depends on the dialect
        switch (dialect)
        {
            case ClusterManagerCommand:
            case DataManagerCommand:
                // always require brackets to avoid needing to maintain the correct list of keywords
                return false;

            case EngineCommand:
                return !s_engineCommandKeywordsThatNeedBrackets.contains(text);

            case Query:
                var kind = new Out<SyntaxKind>(); // PORT: §3.3
                if (SyntaxFacts.tryGetKind(text, kind)
                    && SyntaxFacts.isKeyword(kind.value) // PORT: §3.5
                    && !SyntaxFacts.canBeIdentifier(kind.value))
                {
                    return false;
                }
                break;
        }

        return true;
    }

    /// <summary>
    /// True if the text can be used as an identifier everywhere in Kusto queries.
    /// </summary>
    public static boolean canBeIdentifier(String text) {
        return canBeIdentifier(text, KustoDialect.Query);
    }

    /// <summary>
    /// Adds bracketting and quoting to the name if it cannot be an identifier in the specified language dialect.
    /// </summary>
    public static String bracketNameIfNecessary(String name, KustoDialect dialect)
    {
        if (!canBeIdentifier(name, dialect))
        {
            return getBracketedName(name);
        }

        return name;
    }

    /// <summary>
    /// Adds bracketting and quoting to the name if it cannot be an identifier everywhere in Kusto queries.
    /// </summary>
    public static String bracketNameIfNecessary(String name) {
        return bracketNameIfNecessary(name, KustoDialect.Query);
    }

    /// <summary>
    /// Convert name to bracketed form: name -> ['name']
    /// </summary>
    public static String getBracketedName(String name)
    {
        return "[" + getStringLiteral(name) + "]";
    }

    /// <summary>
    /// Gets the single-quoted escaped string literal for the text,
    /// unless it contains a single quote, and then get the double-quoted escaped string literal.
    /// </summary>
    public static String getStringLiteral(String text)
    {
        if (text.contains("'"))
        {
            return getDoubleQuotedStringLiteral(text);
        }
        else
        {
            return getSingleQuotedStringLiteral(text);
        }
    }

    /// <summary>
    /// Gets the single-quoted escaped string literal for the text.
    /// </summary>
    public static String getSingleQuotedStringLiteral(String text)
    {
        return "'" + getEscapedString(text, singleQuoteStringEscapes) + "'";
    }

    /// <summary>
    /// Gets the double-quoted escaped string literal for the text.
    /// </summary>
    public static String getDoubleQuotedStringLiteral(String text)
    {
        return "\"" + getEscapedString(text, doubleQuoteStringEscapes) + "\"";
    }

    /// <summary>
    /// Gets the multi-line quoted string literal for the text.
    /// </summary>
    public static String getMultiLineStringLiteral(String text)
    {
        return MultiLineStringQuote + DotNet.str(text) + MultiLineStringQuote; // PORT: §3.14
    }

    private static LinkedHashMap<Character, String> singleQuoteStringEscapes =
        new LinkedHashMap<Character, String>(); // PORT: §3.17
    static { // PORT: §3.9 collection initializer (Dictionary.Add per entry)
        DotNet.dictionaryAdd(singleQuoteStringEscapes, '\'', "\\'");
        DotNet.dictionaryAdd(singleQuoteStringEscapes, '\\', "\\\\");
        DotNet.dictionaryAdd(singleQuoteStringEscapes, '\u0007', "\\a"); // PORT: §5.1 C# '\a'
        DotNet.dictionaryAdd(singleQuoteStringEscapes, '\b', "\\b");
        DotNet.dictionaryAdd(singleQuoteStringEscapes, '\f', "\\f");
        DotNet.dictionaryAdd(singleQuoteStringEscapes, '\n', "\\n");
        DotNet.dictionaryAdd(singleQuoteStringEscapes, '\r', "\\r");
        DotNet.dictionaryAdd(singleQuoteStringEscapes, '\t', "\\t"); }

    private static LinkedHashMap<Character, String> doubleQuoteStringEscapes =
        new LinkedHashMap<Character, String>(); // PORT: §3.17
    static { // PORT: §3.9 collection initializer (Dictionary.Add per entry)
        DotNet.dictionaryAdd(doubleQuoteStringEscapes, '"', "\\\"");
        DotNet.dictionaryAdd(doubleQuoteStringEscapes, '\\', "\\\\");
        DotNet.dictionaryAdd(doubleQuoteStringEscapes, '\u0007', "\\a"); // PORT: §5.1 C# '\a'
        DotNet.dictionaryAdd(doubleQuoteStringEscapes, '\b', "\\b");
        DotNet.dictionaryAdd(doubleQuoteStringEscapes, '\f', "\\f");
        DotNet.dictionaryAdd(doubleQuoteStringEscapes, '\n', "\\n");
        DotNet.dictionaryAdd(doubleQuoteStringEscapes, '\r', "\\r");
        DotNet.dictionaryAdd(doubleQuoteStringEscapes, '\t', "\\t"); }

    private static String getEscapedString(String text, Map<Character, String> escapes)
    {
        var builder = new StringBuilder();

        for (var ch : text.toCharArray()) // PORT: §3.18 foreach over the UTF-16 chars
        {
            var escape = escapes.get(ch); // PORT: §3.3 TryGetValue (values never null)
            if (escape != null)
            {
                builder.append(escape);
            }
            else
            {
                builder.append(ch);
            }
        }

        return builder.toString();
    }

    /// <summary>
    /// Gets the column name used for an expression in a projection.
    /// </summary>
    public static String getExpressionResultName(Expression expr, String defaultName, TableSymbol rowScope) // PORT-PENDING: W6 (Binder.getExpressionResultName)
    {
        // return Binder.getExpressionResultName(expr, defaultName, rowScope);
        throw new UnsupportedOperationException("PORT-PENDING: W6");
    }

    public static String getExpressionResultName(Expression expr, String defaultName) // PORT: §3.12 optional parameter rowScope = null
    {
        return getExpressionResultName(expr, defaultName, null);
    }

    public static String getExpressionResultName(Expression expr) // PORT: §3.12 optional parameters defaultName = "", rowScope = null
    {
        return getExpressionResultName(expr, "", null);
    }

    /// <summary>
    /// Gets the content value of a string literal
    /// </summary>
    public static String getStringLiteralValue(String literal)
    {
        int start = 0;
        int end = literal.length();
        var isVerbatim = false;

        if (end == 0)
        {
            return "";
        }

        // do not include H prefix in value
        if (literal.charAt(0) == 'h' || literal.charAt(0) == 'H')
        {
            start++;
        }

        if (start < literal.length() && literal.charAt(start) == '@')
        {
            start++; // do not include @ prefix in value
            isVerbatim = true;
        }

        // check for multi-line string
        var multiLineLiteral = new Out<String>(); // PORT: §3.3
        if (tryParseMultiLineStringLiteral(start, literal, multiLineLiteral))
        {
            return multiLineLiteral.value;
        }

        if (start >= literal.length())
            return "";

        var startQuote = literal.charAt(start);
        var bracketed = startQuote == '[';
        var endQuote = bracketed ? ']' : startQuote;

        start++; // do not include quote in value

        if (end > 0 && literal.charAt(end - 1) == endQuote)
            end--; // do not include end quote in value

        if (end <= start)
        {
            return "";
        }
        else if (!isVerbatim && !bracketed && literal.indexOf('\\', start) >= start)
        {
            return decodeEscapes(literal, start, end - start);
        }
        else if (isVerbatim && !bracketed && hasInteriorQuote(literal, start, end, endQuote))
        {
            return decodeDoubleQuotes(literal, start, end - start, endQuote);
        }
        else if (start > 0 || end < literal.length())
        {
            return literal.substring(start, start + (end - start)); // PORT: §5.4 Substring(start, length)
        }
        else
        {
            return literal;
        }
    }

    /// <summary>
    /// The quote text at the start and end of a multi-line string.
    /// </summary>
    public static final String MultiLineStringQuote = "```";

    /// <summary>
    /// Alternate quote for multi-line strings, to be depreciated.
    /// </summary>
    public static final String AlternateMultiLineStringQuote = "~~~";

    private static boolean tryParseMultiLineStringLiteral(int start, String text, Out<String> literal) // PORT: §3.3
    {
        return tryParseMultiLineStringLiteral(start, text, MultiLineStringQuote, literal)
            || tryParseMultiLineStringLiteral(start, text, AlternateMultiLineStringQuote, literal);
    }

    private static boolean tryParseMultiLineStringLiteral(int start, String text, String quote, Out<String> literal) // PORT: §3.3
    {
        // check for multi-line string
        if (start + quote.length() < text.length()
            && DotNetStrings.compare(text, start, quote, 0, quote.length()) == 0) // PORT: §5.4 StringComparison.Ordinal
        {
            var twiceQuoteLen = quote.length() << 1;
            if (text.length() - start >= twiceQuoteLen && text.endsWith(quote)) // PORT: §5.4 StringComparison.Ordinal
            {
                literal.value = text.substring(start + quote.length(), start + quote.length() + (text.length() - twiceQuoteLen - start)); // PORT: §5.4 Substring(start, length)
                return true;
            }
            else
            {
                literal.value = text.substring(start + quote.length());
                return true;
            }
        }

        literal.value = null;
        return false;
    }

    private static boolean hasInteriorQuote(String text, int start, int end, char quote)
    {
        var position = text.indexOf(quote); // PORT-BUG: ignores start, so the opening quote always counts (D19)
        return position >= 0 && position < end;
    }

    private static String decodeEscapes(String text, int start, int length)
    {
        var builder = new StringBuilder();

        int i = start;
        int end = start + length;
        while (i < end)
        {
            var ch = text.charAt(i);
            if (ch == '\\' && i + 1 < end)
            {
                var ch2 = text.charAt(i + 1);
                switch (ch2)
                {
                    case '\'':
                    case '"':
                    case '\\':
                        builder.append(ch2);
                        i += 2;
                        break;
                    case 'a':
                        builder.append('\u0007'); // PORT: §5.1 C# '\a'
                        i += 2;
                        break;
                    case 'b':
                        builder.append('\b');
                        i += 2;
                        break;
                    case 'f':
                        builder.append('\f');
                        i += 2;
                        break;
                    case 'n':
                        builder.append('\n');
                        i += 2;
                        break;
                    case 'r':
                        builder.append('\r');
                        i += 2;
                        break;
                    case 't':
                        builder.append('\t');
                        i += 2;
                        break;
                    case 'v':
                        builder.append('\u000B'); // PORT: §5.1 C# '\v'
                        i += 2;
                        break;
                    case 'u':
                    {
                        // 4 char hex encoded character
                        i += 2;
                        var ri = new IntRef(i); // PORT: §3.3 ref int
                        builder.append((char)decodeHex(text, 4, ri));
                        i = ri.value;
                        break;
                    }
                    case 'U':
                    {
                        // 8 char hex encoded character (two surrogate pairs)
                        i += 2;
                        var ri = new IntRef(i); // PORT: §3.3 ref int
                        var hex = decodeHex(text, 8, ri);
                        i = ri.value;
                        var converted = DotNetChars.convertFromUtf32(hex); // PORT: §5.1 char.ConvertFromUtf32 (throws on surrogates and > U+10FFFF)
                        builder.append(converted);
                        break;
                    }
                    case 'x':
                    {
                        // 2 char hex encoded character (not same as C#)
                        i += 2;
                        var ri = new IntRef(i); // PORT: §3.3 ref int
                        builder.append((char)decodeHex(text, 2, ri));
                        i = ri.value;
                        break;
                    }
                    default:
                        if (DotNetChars.isDigit(ch2)) // PORT: §5.1 char.IsDigit (Unicode Nd, accepts 8 and 9 unlike the lexer; mirrored)
                        {
                            // octal encoded character
                            i++;
                            var ri = new IntRef(i); // PORT: §3.3 ref int
                            builder.append((char)decodeOctal(text, 3, ri));
                            i = ri.value;
                        }
                        else
                        {
                            // just this character?
                            i += 2;
                            builder.append(ch2);
                        }
                        break;
                }
            }
            else
            {
                builder.append(ch);
                i++;
            }
        }

        return builder.toString();
    }

    private static int decodeOctal(String text, int length, IntRef index) // PORT: §3.3 ref int
    {
        int value = 0;
        int count = 0;

        for (; count < 3 && index.value < text.length() && DotNetChars.isDigit(text.charAt(index.value)); count++, index.value++) // PORT: §5.1 char.IsDigit
        {
            value = (value << 3) + (text.charAt(index.value) - '0');
        }

        return value;
    }

    private static int decodeHex(String text, int length, IntRef index) // PORT: §3.3 ref int
    {
        int value = 0;
        int count = 0;

        for (; count < length && index.value < text.length(); count++, index.value++)
        {
            var ch = text.charAt(index.value);

            if (ch >= 'a' && ch <= 'f')
            {
                value = (value << 4) + (ch - 'a' + 10);
            }
            else if (ch >= 'A' && ch <= 'F')
            {
                value = (value << 4) + (ch - 'A' + 10);
            }
            else if (ch >= '0' && ch <= '9')
            {
                value = (value << 4) + (ch - '0');
            }
            else
            {
                break;
            }
        }

        return value;
    }

    private static String decodeDoubleQuotes(String text, int start, int length, char quote)
    {
        var builder = new StringBuilder();

        for (int i = start, end = start + length; i < end; i++)
        {
            var ch = text.charAt(i);

            if (ch == quote && i + 1 < end && text.charAt(i + 1) == quote)
            {
                i++;
            }

            builder.append(ch);
        }

        return builder.toString();
    }

    private static final String HostNamePrefix = "://";
    public static final String KustoWindowsNet = ".kusto.windows.net";

    /// <summary>
    /// Returns true if the name matches the host name.
    /// </summary>
    public static boolean isHostName(String name, String hostName)
    {
        return DotNetStrings.compareOrdinalIgnoreCase(name, hostName) == 0; // PORT: §5.4 StringComparison.OrdinalIgnoreCase
    }

    /// <summary>
    /// Gets the host name from a possible uri
    /// </summary>
    public static String getHostName(String clusterUriOrName)
    {
        var hostname = new Out<String>(); // PORT: §3.3
        getUriParts(
            clusterUriOrName,
            new Out<String>(),  // scheme
            hostname,
            new Out<String>(), // port
            new Out<String>(), // path
            new Out<String>()  // query
            );
        return hostname.value;
    }

    /// <summary>
    /// Gets the host and path names
    /// </summary>
    public static void getHostAndPath(String clusterUriOrName, Out<String> host, Out<String> path) // PORT: §3.3
    {
        getUriParts(
            clusterUriOrName,
            new Out<String>(), // scheme
            host,
            new Out<String>(), // port
            path,
            new Out<String>()  // query
            );
    }

    private static void getUriParts( // PORT: §3.3
        String uri,
        Out<String> scheme,
        Out<String> hostname,
        Out<String> port,
        Out<String> path,
        Out<String> query)
    {
        scheme.value = null;
        port.value = null;
        path.value = null;
        query.value = null;

        int hostnameStart = 0;

        int hostNamePrefixStart = uri.indexOf(HostNamePrefix); // PORT: §5.4, D12 culture IndexOf(string) → ordinal
        if (hostNamePrefixStart > 0)
        {
            scheme.value = uri.substring(0, hostNamePrefixStart);
            hostnameStart = hostNamePrefixStart + HostNamePrefix.length();
        }

        // extract only permitted host name or IP value characters
        var pos = hostnameStart;
        while (pos < uri.length() &&
            isHostNameChar(uri.charAt(pos)))
        {
            pos++;
        }

        if (hostnameStart == 0 && pos == uri.length())
        {
            hostname.value = uri;
        }
        else
        {
            hostname.value = uri.substring(hostnameStart, hostnameStart + (pos - hostnameStart)); // PORT: §5.4 Substring(start, length)
        }

        if (pos < uri.length() && uri.charAt(pos) == ':')
        {
            pos++;
            var portStart = pos;
            while (pos < uri.length() && DotNetChars.isDigit(uri.charAt(pos))) // PORT: §5.1 char.IsDigit
            {
                pos++;
            }

            if (pos > portStart)
            {
                port.value = uri.substring(portStart, portStart + (pos - portStart)); // PORT: §5.4 Substring(start, length)
            }
        }

        if (pos < uri.length() && uri.charAt(pos) == '/')
        {
            pos++;
            var pathStart = pos;
            while (pos < uri.length() && uri.charAt(pos) != '?')
            {
                pos++;
            }

            if (pos > pathStart)
            {
                path.value = uri.substring(pathStart, pathStart + (pos - pathStart)); // PORT: §5.4 Substring(start, length)
            }
        }

        if (pos < uri.length() && uri.charAt(pos) == '?')
        {
            query.value = uri.substring(pos);
        }
    }

    /// <summary>
    /// Returns true if the character is a legal part of a host name or IP address.
    /// </summary>
    private static boolean isHostNameChar(char ch)
    {
        return DotNetChars.isLetter(ch) || DotNetChars.isDigit(ch) || ch == '-' || ch == '.' || ch == '_'; // PORT: §5.1 char.IsLetter / char.IsDigit
    }

    /// <summary>
    /// Gets the full name of a host given the short or full name.
    /// </summary>
    public static String getFullHostName(String hostname, String defaultDomainSuffix)
    {
        if (isPossibleShortHostName(hostname)
            && !DotNetStrings.isNullOrEmpty(defaultDomainSuffix)
            && defaultDomainSuffix.charAt(0) == '.')
        {
            return hostname + defaultDomainSuffix;
        }
        else
        {
            return hostname;
        }
    }

    /// <summary>
    /// Returns true if the hostname has a short name
    /// </summary>
    public static boolean hasShortHostName(String hostname, String defaultDomainSuffix)
    {
        // the full name has a short name, if it is in the default domain and the remaining prefix qualifies as a short name.
        return !DotNetStrings.isNullOrEmpty(defaultDomainSuffix)
            && defaultDomainSuffix.charAt(0) == '.'
            && hostname.length() >= defaultDomainSuffix.length() // PORT: §5.4 EndsWith(OrdinalIgnoreCase) as a clamped region compare
            && DotNetStrings.compare(hostname, hostname.length() - defaultDomainSuffix.length(), defaultDomainSuffix, 0, defaultDomainSuffix.length(), true) == 0
            && hostname.length() > defaultDomainSuffix.length()
            && isPossibleShortHostName(hostname, 0, hostname.length() - defaultDomainSuffix.length());
    }

    /// <summary>
    /// Returns true if the name is the short name of the host name.
    /// </summary>
    public static boolean isShortHostName(String name, String hostName, String defaultDomainSuffix)
    {
        // supplied name is the first part of xxx.YYY.ZZZ or xxx.xxx.YYY.ZZZ
        if (!hasShortHostName(hostName, defaultDomainSuffix))
            return false;

        // the hostname should be the combination of the short name and the default domain suffix
        if (name.length() + defaultDomainSuffix.length() != hostName.length())
            return false;

        return DotNetStrings.compare(hostName, 0, name, 0, name.length(), true) == 0; // PORT: §5.4 StartsWith(OrdinalIgnoreCase); name is shorter than hostName here
    }

    /// <summary>
    /// Returns true if the name is a possible short host name
    /// </summary>
    public static boolean isPossibleShortHostName(String name)
    {
        return isPossibleShortHostName(name, 0, name.length());
    }

    private static boolean isPossibleShortHostName(String name, int start, int len)
    {
        // short names can have zero or one dots:  name or name.region
        return !DotNetStrings.isNullOrEmpty(name)
            && name.charAt(name.length() - 1) != '.'
            && countDots(name, start, len) < 2;
    }

    private static int countDots(String text, int start, int len)
    {
        int count = 0;

        for (int i = start, end = start + len; i < end; i++)
        {
            if (text.charAt(i) == '.')
                count++;
        }

        return count;
    }

    /// <summary>
    /// Gets the short name given the full host name, if one exists or null if no short name exists
    /// </summary>
    public static String getShortHostName(String hostName, String defaultDomainSuffix)
    {
        if (hostName != null && hasShortHostName(hostName, defaultDomainSuffix))
        {
            return hostName.substring(0, hostName.length() - defaultDomainSuffix.length());
        }

        return null;
    }

    /// <summary>
    /// True if the text matches the pattern (*, xxx*, *xxx, *xxx*, xxx*yyy, *xxx*yyy*, ...)
    /// The * represents any zero-or-more characters.
    /// </summary>
    public static boolean matches(String pattern, String text, boolean ignoreCase)
    {
        if (pattern == null)
            throw new NullPointerException("pattern"); // PORT: §3.16 ArgumentNullException(nameof(pattern))

        if (text == null)
            throw new NullPointerException("text"); // PORT: §3.16 ArgumentNullException(nameof(text))

        // empty pattern does not match anything
        if (pattern.length() == 0)
            return false;

        return matches(pattern, 0, text, 0, ignoreCase);
    }

    public static boolean matches(String pattern, String text) // PORT: §3.12 optional parameter ignoreCase = false
    {
        return matches(pattern, text, false);
    }

    private static boolean matches(String pattern, int patternSegmentStart, String text, int textPosition, boolean ignoreCase)
    {
        var asteriskPosition = pattern.indexOf('*', patternSegmentStart);
        var sawAsterisk = asteriskPosition >= 0;

        var patternSegmentEnd = sawAsterisk ? asteriskPosition : pattern.length();

        // skip over adjacent asterisks
        while (sawAsterisk && asteriskPosition + 1 < pattern.length() && pattern.charAt(asteriskPosition + 1) == '*')
        {
            asteriskPosition++;
        }

        var nextPatternStart = sawAsterisk ? asteriskPosition + 1 : pattern.length();
        var patternSegmentLength = patternSegmentEnd - patternSegmentStart;

        if (patternSegmentLength == 0)
        {
            if (patternSegmentStart >= pattern.length())
            {
                // no more pattern segments to match
                return true;
            }
            else
            {
                // this is the nothing pattern before the first asterisk
                return matches(pattern, nextPatternStart, text, textPosition, ignoreCase);
            }
        }
        else if (patternSegmentStart == 0)
        {
            if (!sawAsterisk)
            {
                // this is fixed-pattern (no asterisks before or after), so must be exact match
                return text.length() == patternSegmentLength
                    && DotNetStrings.compare(text, 0, pattern, 0, patternSegmentLength, ignoreCase) == 0; // PORT: §5.4, D12
            }
            else
            {
                // this is the first segment (no asterisk before) so its a starts-with pattern segment
                if (patternSegmentLength > text.length()
                    || DotNetStrings.compare(text, 0, pattern, 0, patternSegmentLength, ignoreCase) != 0) // PORT: §5.4, D12
                {
                    return false;
                }

                return matches(pattern, nextPatternStart, text, patternSegmentLength, ignoreCase);
            }
        }
        else if (!sawAsterisk)
        {
            // no asterisk after, so it is an ends-with pattern segment
            return (patternSegmentLength <= text.length() - textPosition
                    && DotNetStrings.compare(text, text.length() - patternSegmentLength, pattern, patternSegmentStart, patternSegmentLength, ignoreCase) == 0); // PORT: §5.4, D12
        }
        else
        {
            // between asterisks, so this is a contains segment
            var matchesPosition = indexOf(text, textPosition, pattern, patternSegmentStart, patternSegmentLength);
            if (matchesPosition == -1)
                return false;

            return matches(pattern, nextPatternStart, text, matchesPosition + patternSegmentLength, ignoreCase);
        }
    }

    private static int indexOf(String text, int textStart, String value, int valueStart, int valueLength)
    {
        var firstChar = value.charAt(valueStart);

        while (true)
        {
            int firstCharPosition = text.indexOf(firstChar, textStart);
            if (firstCharPosition < textStart || firstCharPosition + valueLength > text.length())
                return -1;

            if (DotNetStrings.compare(text, firstCharPosition, value, valueStart, valueLength) == 0) // PORT: §5.4, D12
                return firstCharPosition;

            textStart = firstCharPosition + 1;
        }
    }

    /// <summary>
    /// Attempts to determine the tabularity of an expression (scalar, tabular, none, unknown) given syntax.
    /// </summary>
    public static Tabularity getSyntaxTabularity(Expression expression, GlobalState globals)
    {
        globals = globals != null ? globals : GlobalState.default_(); // PORT: §3.14 ??; §2.3 Default → default_()

        // PORT: §3.15 case Type _: fall-through stacks → instanceof chains
        if (expression instanceof QueryOperator  // querys operator are always tabular
            || expression instanceof PipeExpression // pipes are always queries
            || expression instanceof Command        // commands always have tabular output
            || expression instanceof DataTableExpression
            || expression instanceof ToTableExpression)
        {
            return Tabularity.Tabular;
        }
        else if (expression instanceof BinaryExpression
            || expression instanceof PrefixUnaryExpression
            || expression instanceof InExpression
            || expression instanceof HasAnyExpression
            || expression instanceof HasAllExpression
            || expression instanceof BetweenExpression
            || expression instanceof ElementExpression
            || expression instanceof BracketedExpression
            || expression instanceof LiteralExpression
            || expression instanceof CompoundStringLiteralExpression
            || expression instanceof DynamicExpression
            || expression instanceof ToScalarExpression)
        {
            return Tabularity.Scalar;
        }
        else if (expression instanceof FunctionCallExpression fc)
        {
            var fn = globals.getFunction(fc.name().simpleName());
            if (fn != null)
                return fn.tabularity();
            var db = globals.database(); // PORT: §3.14 ?.
            var dbFn = db != null ? db.getFunction(fc.name().simpleName()) : null;
            if (dbFn != null)
                return dbFn.tabularity();
            return Tabularity.Unknown;
        }
        else // case Expression _: default:
        {
            return Tabularity.Unknown;
        }
    }

    public static Tabularity getSyntaxTabularity(Expression expression) // PORT: §3.12 optional parameter globals = null
    {
        return getSyntaxTabularity(expression, null);
    }

    /// <summary>
    /// Attempts to determine the tabularity of a statement (scalar, tabular, none, unknown) given syntax.
    /// </summary>
    public static Tabularity getSyntaxTabularity(Statement statement, GlobalState globals)
    {
        if (statement != null
            && statement instanceof ExpressionStatement es)
        {
            return KustoFacts.getSyntaxTabularity(es.expression(), globals);
        }
        else
        {
            return Tabularity.None;
        }
    }

    public static Tabularity getSyntaxTabularity(Statement statement) // PORT: §3.12 optional parameter globals = null
    {
        return getSyntaxTabularity(statement, null);
    }

    /// <summary>
    /// Attempts to determine the tabularity of a block of statements (scalar, tabular, none, unknown) given syntax.
    /// </summary>
    public static Tabularity getSyntaxTabularity(SyntaxList1<SeparatedElement1<Statement>> statements, GlobalState globals)
    {
        if (statements != null
            && statements.size() > 0
            && statements.get(statements.size() - 1).element() instanceof ExpressionStatement es)
        {
            return KustoFacts.getSyntaxTabularity(es.expression(), globals);
        }
        else
        {
            return Tabularity.None;
        }
    }

    public static Tabularity getSyntaxTabularity(SyntaxList1<SeparatedElement1<Statement>> statements) // PORT: §3.12 optional parameter globals = null
    {
        return getSyntaxTabularity(statements, null);
    }

    /// <summary>
    /// Gets the scalar type for the literal syntax kind.
    /// </summary>
    public static ScalarSymbol getLiteralType(SyntaxKind kind)
    {
        switch (kind)
        {
            case BooleanLiteralExpression:
            case BooleanLiteralToken:
                return ScalarTypes.Bool;

            case StringLiteralExpression:
            case StringLiteralToken:
            case CompoundStringLiteralExpression:
                return ScalarTypes.String;

            case IntLiteralExpression:
            case IntLiteralToken:
                return ScalarTypes.Int;

            case LongLiteralExpression:
            case LongLiteralToken:
                return ScalarTypes.Long;

            case RealLiteralExpression:
            case RealLiteralToken:
                return ScalarTypes.Real;

            case DecimalLiteralExpression:
            case DecimalLiteralToken:
                return ScalarTypes.Decimal;

            case TimespanLiteralExpression:
            case TimespanLiteralToken:
                return ScalarTypes.TimeSpan;

            case DateTimeLiteralExpression:
            case DateTimeLiteralToken:
                return ScalarTypes.DateTime;

            case GuidLiteralExpression:
            case GuidLiteralToken:
            case RawGuidLiteralToken:
                return ScalarTypes.Guid;

            case TypeOfLiteralExpression:
                return ScalarTypes.Type;

            case DynamicExpression:
                return ScalarTypes.Dynamic;

            default:
                return ScalarTypes.Unknown;
        }
    }

    // ===== upstream part: KustoFacts_Keywords.cs =====

    /// <summary>
    /// Known identifier-like keywords from all sources (query and all control commands)
    /// that are not specifically enabled to be identifiers everywhere.
    /// </summary>
    private static final LinkedHashSet<String> s_engineCommandKeywordsThatNeedBrackets = new LinkedHashSet<String>(Arrays.asList(new String[] // PORT: §3.17 HashSet collection initializer (Add ignores duplicates)
    {
        "__unique",
        "accumulate",
        "and",
        "application",
        "as",
        "asc",
        "between",
        "blockedprincipals",
        "boolean",
        "by",
        "byte",
        "bytes",
        "cachingpolicy",
        "callout",
        "callouts",
        "callstacks",
        "cancel",
        "char",
        "clusteradmin",
        "concurrency",
        "configuration",
        "consume",
        "container",
        "containers",
        "contains",
        "count",
        "dataexport",
        "datasize",
        "datastats",
        "datatable",
        "date",
        "datetime",
        "datetime_pattern",
        "days",
        "decimal",
        "desc",
        "dimensions",
        "disabled",
        "distinct",
        "double",
        "dryrun",
        "dynamic",
        "earliest",
        "empty",
        "enabled",
        "encodingpolicy",
        "entity_group",
        "exclude",
        "expired_tables_cleanup",
        "extend",
        "extent_tags_retention",
        "extentsize",
        "external_data",
        "externaldata",
        "filesystem",
        "filter",
        "find",
        "first",
        "flags",
        "float",
        "follower",
        "for",
        "format_datetime",
        "GB",
        "getschema",
        "graph_query",
        "harddelete",
        "hardretention",
        "has",
        "has_all",
        "has_any",
        "hot_window",
        "identity",
        "in",
        "include",
        "int",
        "int16",
        "int32",
        "int64",
        "int8",
        "invoke",
        "journal",
        "kind",
        "last",
        "latest",
        "limit",
        "long",
        "materialize",
        "MB",
        "mdm",
        "missing",
        "mvapply",
        "mvexpand",
        "network",
        "of",
        "or",
        "order",
        "others",
        "parse",
        "pathformat",
        "print",
        "project",
        "queries",
        "query_results",
        "real",
        "recoverability",
        "restricted_view_access",
        "row_level_security",
        "rowstore",
        "rowstore_references",
        "rowstore_sealinfo",
        "rowstorepolicy",
        "rowstores",
        "sample",
        "scan",
        "seal",
        "seals",
        "search",
        "serialize",
        "set",
        "shards",
        "shuffle",
        "snapshot_indexing",
        "softdelete",
        "softretention",
        "sort",
        "sql",
        "startofday",
        "startofmonth",
        "startofweek",
        "startofyear",
        "statistics",
        "stored_query_result",
        "stored_query_results",
        "storedqueryresultcontainers",
        "string",
        "summarize",
        "tablepurge",
        "take",
        "time",
        "timespan",
        "title",
        "to",
        "top",
        "toscalar",
        "totable",
        "transactions",
        "trim",
        "uint",
        "uint16",
        "uint32",
        "uint64",
        "uint8",
        "ulong",
        "union",
        "uniqueid",
        "unrestrictedviewers",
        "until",
        "unused",
        "utilization",
        "verbose",
        "viewers",
        "views",
        "violations",
        "where",
        "writeaheadlog",
        "graph_snapshots_drop_by_retention",
        "graph_snapshots_optimize",
        "graph_shards",
    }));
}
