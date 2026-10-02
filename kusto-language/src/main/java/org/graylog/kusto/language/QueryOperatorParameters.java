// Ported from: src/Kusto.Language/QueryOperatorParameters.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language;

import java.util.Arrays;
import java.util.List;

import org.graylog.kusto.language.utils.ListExtensions;

/// <summary>
/// Known parameters for specific query operators or expressions
/// </summary>
public final class QueryOperatorParameters // PORT: §3.5 static class → final class with a private constructor
{
    private QueryOperatorParameters() { }

    public static final QueryOperatorParameter BagExpansion =
        new QueryOperatorParameter("bagexpansion", QueryOperatorParameterValueKind.Word, true, KustoFacts.MvExpandKinds).hide(); // PORT: §3.12 named arguments; skipped optionals pass the upstream default

    public static final QueryOperatorParameter BinLegacy =
        new QueryOperatorParameter("bin_legacy", QueryOperatorParameterValueKind.Any);

    public static final QueryOperatorParameter CrossCluster =
        new QueryOperatorParameter("__crossCluster", QueryOperatorParameterValueKind.Any);

    public static final QueryOperatorParameter CrossDB =
        new QueryOperatorParameter("__crossDB", QueryOperatorParameterValueKind.Any);

    public static final QueryOperatorParameter DecodeBlocks =
        new QueryOperatorParameter("decodeblocks", QueryOperatorParameterValueKind.BoolLiteral, true, null, false); // PORT: §3.12 named arguments; skipped optionals pass the upstream default

    public static final QueryOperatorParameter ExpandOutput =
        new QueryOperatorParameter("expandoutput", QueryOperatorParameterValueKind.Any);

    public static final QueryOperatorParameter Flags =
        new QueryOperatorParameter("flags", QueryOperatorParameterValueKind.Word);

    public static final QueryOperatorParameter HintDotConcurrency =
        new QueryOperatorParameter("hint.concurrency", QueryOperatorParameterValueKind.WordOrNumber, false, KustoFacts.HintConcurrencies); // PORT: §3.12 named arguments; skipped optionals pass the upstream default

    public static final QueryOperatorParameter HintDotDistribution =
        new QueryOperatorParameter("hint.distribution", QueryOperatorParameterValueKind.Word, false, KustoFacts.HintDistributions); // PORT: §3.12 named arguments; skipped optionals pass the upstream default

    public static final QueryOperatorParameter HintDotMaterialized =
        new QueryOperatorParameter("hint.materialized", QueryOperatorParameterValueKind.BoolLiteral);

    public static final QueryOperatorParameter HintDotNumPartitions =
        new QueryOperatorParameter("hint.num_partitions", QueryOperatorParameterValueKind.IntegerLiteral, true, null, false); // PORT: §3.12 named arguments; skipped optionals pass the upstream default

    public static final QueryOperatorParameter HintDotPassFilters =
        new QueryOperatorParameter("hint.pass_filters", QueryOperatorParameterValueKind.Any);

    public static final QueryOperatorParameter HintDotPassFiltersColumn =
        new QueryOperatorParameter("hint.pass_filters_column", QueryOperatorParameterValueKind.Column);

    public static final QueryOperatorParameter HintDotProgressiveTop =
        new QueryOperatorParameter("hint.progressive_top", QueryOperatorParameterValueKind.BoolLiteral);

    public static final QueryOperatorParameter HintDotRemote =
        new QueryOperatorParameter("hint.remote", QueryOperatorParameterValueKind.Word, false, KustoFacts.HintRemotes); // PORT: §3.12 named arguments; skipped optionals pass the upstream default

    public static final QueryOperatorParameter HintDotShuffleKey =
        new QueryOperatorParameter("hint.shufflekey", QueryOperatorParameterValueKind.Column, true, null, true); // PORT: §3.12 named arguments; skipped optionals pass the upstream default

    public static final QueryOperatorParameter HintDotSpread =
        new QueryOperatorParameter("hint.spread", QueryOperatorParameterValueKind.WordOrNumber, false, KustoFacts.HintSpreads); // PORT: §3.12 named arguments; skipped optionals pass the upstream default

    public static final QueryOperatorParameter HintDotStrategy =
        new QueryOperatorParameter("hint.strategy", QueryOperatorParameterValueKind.Word, false, KustoFacts.HintStrategies); // PORT: §3.12 named arguments; skipped optionals pass the upstream default

    public static final QueryOperatorParameter Id =
        new QueryOperatorParameter("__id", QueryOperatorParameterValueKind.Any);

    public static final QueryOperatorParameter IsFuzzy =
        new QueryOperatorParameter("isfuzzy", QueryOperatorParameterValueKind.BoolLiteral, true, null, false, Arrays.asList(new String[] { "__isFuzzy" })); // PORT: §3.12 named arguments; skipped optionals pass the upstream default

    public static final QueryOperatorParameter BestEffort =
        new QueryOperatorParameter("best_effort", QueryOperatorParameterValueKind.BoolLiteral);

    public static final QueryOperatorParameter Kind =
        new QueryOperatorParameter("kind", QueryOperatorParameterValueKind.Word);

    public static final QueryOperatorParameter Output =
        new QueryOperatorParameter("output", QueryOperatorParameterValueKind.Word);

    public static final QueryOperatorParameter WithComponentId =
        new QueryOperatorParameter("with_component_id", QueryOperatorParameterValueKind.NameDeclaration);

    public static final QueryOperatorParameter NoWithSource =
        new QueryOperatorParameter("__noWithSource", QueryOperatorParameterValueKind.Any);

    public static final QueryOperatorParameter PackedColumn =
        new QueryOperatorParameter("__packedColumn", QueryOperatorParameterValueKind.Column);

    public static final QueryOperatorParameter SourceColumnIndex =
        new QueryOperatorParameter("__sourceColumnIndex", QueryOperatorParameterValueKind.Any);

    public static final QueryOperatorParameter WithMatchId =
        new QueryOperatorParameter("with_match_id", QueryOperatorParameterValueKind.NameDeclaration);

    public static final QueryOperatorParameter WithItemIndex =
        new QueryOperatorParameter("with_itemindex", QueryOperatorParameterValueKind.NameDeclaration);

    public static final QueryOperatorParameter WithSource =
        new QueryOperatorParameter("withsource", QueryOperatorParameterValueKind.NameDeclaration, true, null, false, Arrays.asList(new String[] { "with_source" })); // PORT: §3.12 named arguments; skipped optionals pass the upstream default

    public static final QueryOperatorParameter WithStepName =
        new QueryOperatorParameter("with_step_name", QueryOperatorParameterValueKind.NameDeclaration);

    public static final QueryOperatorParameter ForceRemote =
        new QueryOperatorParameter("force_remote", QueryOperatorParameterValueKind.BoolLiteral);

    public static final QueryOperatorParameter Cycles =
        new QueryOperatorParameter("cycles", QueryOperatorParameterValueKind.Word, true, KustoFacts.CyclesKinds); // PORT: §3.12 named arguments; skipped optionals pass the upstream default

    public static final QueryOperatorParameter ShortestPathsOutputs =
        new QueryOperatorParameter("output", QueryOperatorParameterValueKind.Word, true, KustoFacts.ShortestPathsOutputs); // PORT: §3.12 named arguments; skipped optionals pass the upstream default

    public static final QueryOperatorParameter ManagedIdentityAuthEnabled =
        new QueryOperatorParameter("AllowManagedIdentityAuthentication", QueryOperatorParameterValueKind.BoolLiteral, true, null, false); // PORT: §3.12 named arguments; skipped optionals pass the upstream default

    /// <summary>
    /// All query operator parameters.
    /// Does not include parameters used for other syntax clauses (like render with properties)
    /// </summary>
    public static final List<QueryOperatorParameter> AllParameters = Arrays.asList(new QueryOperatorParameter[] // PORT: §3.17 array as IReadOnlyList
    {
        BagExpansion.hide(),
        BinLegacy.hide(),
        CrossCluster.hide(),
        CrossDB.hide(),
        DecodeBlocks.hide(),
        ExpandOutput.hide(),
        //Flags.hide(),
        HintDotConcurrency.hide(),
        HintDotDistribution.hide(),
        HintDotMaterialized.hide(),
        HintDotNumPartitions.hide(),
        HintDotPassFilters.hide(),
        HintDotPassFiltersColumn.hide(),
        HintDotProgressiveTop.hide(),
        HintDotRemote.hide(),
        HintDotShuffleKey.hide(),
        HintDotSpread.hide(),
        HintDotStrategy.hide(),
        Id.hide(),
        IsFuzzy.hide(),
        Kind.hide(),
        NoWithSource.hide(),
        PackedColumn.hide(),
        SourceColumnIndex.hide(),
        Cycles.hide(),
        WithMatchId.hide(),
        WithItemIndex.hide(),
        WithSource.hide(),
        WithStepName.hide(),
        ForceRemote.hide(),
    });

    // parameter sets for specific operators

    public static final List<QueryOperatorParameter> AsParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        HintDotMaterialized
    }));

    public static final List<QueryOperatorParameter> ConsumeParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        DecodeBlocks,
    }));

    public static final List<QueryOperatorParameter> DataTableParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        // no known parameters
    }));

    public static final List<QueryOperatorParameter> DistinctParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        HintDotShuffleKey,
        HintDotStrategy.withValues(KustoFacts.DistinctHintStrategies),
        HintDotNumPartitions
    }));

    public static final List<QueryOperatorParameter> EvaluateParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        HintDotDistribution.withValues(KustoFacts.EvaluateHintDistributions),
        HintDotRemote.withValues(KustoFacts.EvaluateHintRemotes)
    }));

    public static final List<QueryOperatorParameter> ExternalDataWithClauseProperties = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        // no known parameters
    }));

    public static final List<QueryOperatorParameter> InlineExternalTableProperties = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        // no known parameters
    }));

    public static final List<QueryOperatorParameter> FilterParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        // no known parameters
    }));

    public static final List<QueryOperatorParameter> FindParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        WithSource
    }));

    public static final List<QueryOperatorParameter> GraphMatchParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        Cycles.withValues(KustoFacts.CyclesKinds),
    }));

    public static final List<QueryOperatorParameter> GraphShortestPathsParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        Output.withValues(KustoFacts.ShortestPathsOutputs),
    }));

    public static final List<QueryOperatorParameter> RestrictStatementParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        ManagedIdentityAuthEnabled
    }));


    public static final List<QueryOperatorParameter> JoinParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        Kind.withValues(KustoFacts.JoinKinds),
        HintDotRemote.withValues(KustoFacts.JoinHintRemotes),
        HintDotShuffleKey,
        HintDotStrategy.withValues(KustoFacts.JoinHintStrategies),
        HintDotNumPartitions
    }));

    public static final List<QueryOperatorParameter> GraphMarkComponentsParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        Kind.withValues(KustoFacts.GraphMarkComponentsKinds),
        WithComponentId
    }));

    public static final List<QueryOperatorParameter> LookupParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        Kind.withValues(KustoFacts.LookupKinds)
    }));

    public static final List<QueryOperatorParameter> MakeSeriesParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        HintDotShuffleKey,
        Kind.withValues(KustoFacts.MakeSeriesKinds)
    }));

    public static final List<QueryOperatorParameter> MvApplyParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        WithItemIndex,
    }));

    public static final List<QueryOperatorParameter> MvExpandParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        Kind.withValues(KustoFacts.MvExpandKinds),
        BagExpansion.withValues(KustoFacts.MvExpandKinds).hide(),
        WithItemIndex,
    }));

    public static final List<QueryOperatorParameter> ParseParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        Kind.withValues(KustoFacts.ParseKinds),
        Flags,
    }));

    public static final List<QueryOperatorParameter> ParseKvWithProperties = Arrays.asList(new QueryOperatorParameter[] // PORT: §3.17 array as IReadOnlyList
    {
        new QueryOperatorParameter("pair_delimiter", QueryOperatorParameterValueKind.StringLiteral),
        new QueryOperatorParameter("kv_delimiter", QueryOperatorParameterValueKind.StringLiteral),
        new QueryOperatorParameter("regex", QueryOperatorParameterValueKind.StringLiteral),
        new QueryOperatorParameter("quote", QueryOperatorParameterValueKind.StringLiteral, true, null, true), // PORT: §3.12 named arguments; skipped optionals pass the upstream default
        new QueryOperatorParameter("escape", QueryOperatorParameterValueKind.StringLiteral, true, null, true), // PORT: §3.12 named arguments; skipped optionals pass the upstream default
        new QueryOperatorParameter("greedy", QueryOperatorParameterValueKind.BoolLiteral),
    });

    public static final List<QueryOperatorParameter> PartitionByParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        HintDotConcurrency.withValues(KustoFacts.PartitionHintConcurrencies),
        HintDotSpread.withValues(KustoFacts.PartitionHintSpreads),
        HintDotMaterialized,
        HintDotShuffleKey,
        HintDotStrategy.withValues(KustoFacts.PartitionHintStrategies)
    }));

    public static final List<QueryOperatorParameter> PartitionParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        HintDotConcurrency.withValues(KustoFacts.PartitionHintConcurrencies),
        HintDotSpread.withValues(KustoFacts.PartitionHintSpreads),
        HintDotMaterialized,
        HintDotShuffleKey,
        HintDotStrategy.withValues(KustoFacts.PartitionHintStrategies)
    }));

    public static final List<QueryOperatorParameter> ReduceParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList (C# new[] infers QueryOperatorParameter[])
    {
        Kind.withValues(KustoFacts.ReduceByKinds)
    }));

    public static final List<QueryOperatorParameter> ReduceWithParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList (C# new[] infers QueryOperatorParameter[])
    {
        new QueryOperatorParameter("threshold", QueryOperatorParameterValueKind.NumericLiteral),
        new QueryOperatorParameter("characters", QueryOperatorParameterValueKind.StringLiteral)
    }));

    public static final QueryOperatorParameter WithSourceId = new QueryOperatorParameter("with_source_id", QueryOperatorParameterValueKind.NameDeclaration);
    public static final QueryOperatorParameter WithTargetId = new QueryOperatorParameter("with_target_id", QueryOperatorParameterValueKind.NameDeclaration);
    public static final QueryOperatorParameter WithNodeId = new QueryOperatorParameter("with_node_id", QueryOperatorParameterValueKind.NameDeclaration);

    public static final List<QueryOperatorParameter> GraphToTableEdgesParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList (C# new[] infers QueryOperatorParameter[])
    {
        WithSourceId,
        WithTargetId
    }));

    public static final List<QueryOperatorParameter> GraphToTableNodesParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList (C# new[] infers QueryOperatorParameter[])
    {
        WithNodeId
    }));

    public static final List<QueryOperatorParameter> GraphMakeParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList (C# new[] infers QueryOperatorParameter[])
    {
        HintDotStrategy.withValues(KustoFacts.PartitionedGraphMakeHintStrategies)
    }));

    public static final QueryOperatorParameter RenderKind =
        new QueryOperatorParameter("kind", QueryOperatorParameterValueKind.Word, true, KustoFacts.ChartKinds); // PORT: §3.12 named arguments; skipped optionals pass the upstream default

    public static final QueryOperatorParameter RenderTitle =
        new QueryOperatorParameter("title", QueryOperatorParameterValueKind.String);

    public static final QueryOperatorParameter RenderAccumulate =
        new QueryOperatorParameter("accumulate", QueryOperatorParameterValueKind.BoolLiteral);

    public static final QueryOperatorParameter RenderWithDeprecated =
        new QueryOperatorParameter("with", QueryOperatorParameterValueKind.StringLiteral).withHasNoEquals(true);

    public static final QueryOperatorParameter RenderByDeprecated =
        new QueryOperatorParameter("by", QueryOperatorParameterValueKind.ColumnList).withHasNoEquals(true);

    public static final List<QueryOperatorParameter> RenderParameters = Arrays.asList(new QueryOperatorParameter[] // PORT: §3.17 array as IReadOnlyList
    {
        RenderKind.hide(),
        RenderTitle.hide(),
        RenderAccumulate.hide(),
        RenderWithDeprecated.hide(),
        RenderByDeprecated.hide()
    });

    public static final List<QueryOperatorParameter> RenderWithProperties = Arrays.asList(new QueryOperatorParameter[] // PORT: §3.17 array as IReadOnlyList
    {
        RenderKind,
        RenderTitle,
        RenderAccumulate,
        new QueryOperatorParameter("xcolumn", QueryOperatorParameterValueKind.Column),
        new QueryOperatorParameter("ycolumns", QueryOperatorParameterValueKind.ColumnList),
        new QueryOperatorParameter("anomalycolumns", QueryOperatorParameterValueKind.ColumnList),
        new QueryOperatorParameter("series", QueryOperatorParameterValueKind.ColumnList),
        new QueryOperatorParameter("xtitle", QueryOperatorParameterValueKind.String),
        new QueryOperatorParameter("ytitle", QueryOperatorParameterValueKind.String),
        new QueryOperatorParameter("xaxis", QueryOperatorParameterValueKind.Word, true, KustoFacts.ChartAxis), // PORT: §3.12 named arguments; skipped optionals pass the upstream default
        new QueryOperatorParameter("yaxis", QueryOperatorParameterValueKind.Word, true, KustoFacts.ChartAxis), // PORT: §3.12 named arguments; skipped optionals pass the upstream default
        new QueryOperatorParameter("legend", QueryOperatorParameterValueKind.Word, true, KustoFacts.ChartLegends), // PORT: §3.12 named arguments; skipped optionals pass the upstream default
        new QueryOperatorParameter("ysplit", QueryOperatorParameterValueKind.Word, true, KustoFacts.ChartYSplit), // PORT: §3.12 named arguments; skipped optionals pass the upstream default
        new QueryOperatorParameter("ymin", QueryOperatorParameterValueKind.ForcedRealLiteral),
        new QueryOperatorParameter("ymax", QueryOperatorParameterValueKind.ForcedRealLiteral),
        new QueryOperatorParameter("xmin", QueryOperatorParameterValueKind.ScalarLiteral),
        new QueryOperatorParameter("xmax", QueryOperatorParameterValueKind.ScalarLiteral),
    });

    public static final List<QueryOperatorParameter> SampleParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        // no known parameters
    }));

    public static final List<QueryOperatorParameter> SampleDistinctParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        // no known parameters
    }));

    public static final List<QueryOperatorParameter> ScanParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        Kind.withValues(KustoFacts.ScanKinds).hide(),
        WithMatchId,
    }));

    public static final List<QueryOperatorParameter> SearchParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        Kind.withValues(KustoFacts.SearchKinds)
    }));

    public static final List<QueryOperatorParameter> SerializedParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        // no known parameters
    }));

    public static final List<QueryOperatorParameter> SortParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        HintDotStrategy.withValues(KustoFacts.SortHintStrategies)
    }));

    public static final List<QueryOperatorParameter> SummarizeParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        HintDotShuffleKey,
        HintDotStrategy.withValues(KustoFacts.SummarizeHintStrategies),
        HintDotNumPartitions
    }));

    public static final List<QueryOperatorParameter> TakeParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        // no known parameters
    }));

    public static final List<QueryOperatorParameter> TopParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        HintDotProgressiveTop,
    }));

    public static final QueryOperatorParameter ToScalarKindParameter =
        Kind.withValues(KustoFacts.ToScalarKinds);

    public static final QueryOperatorParameter ToTableKindParameter =
        Kind.withValues(KustoFacts.ToTableKinds);

    public static final List<QueryOperatorParameter> UnionParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
    {
        Kind.withValues(KustoFacts.UnionKinds),
        WithSource,
        IsFuzzy,
        BestEffort,
        HintDotConcurrency.withValues(KustoFacts.UnionHintConcurrencies),
        HintDotSpread.withValues(KustoFacts.UnionHintSpreads)
    }));

    public static final List<QueryOperatorParameter> MacroExpandParameters = ListExtensions.toReadOnly(Arrays.asList(new QueryOperatorParameter[] // PORT: §3.5; §3.17 array as IReadOnlyList
     {
        ForceRemote,
        IsFuzzy,
        BestEffort
     }));

    public static final QueryOperatorParameter GetSchemaKind =
        new QueryOperatorParameter("kind", QueryOperatorParameterValueKind.Word, true, Arrays.asList(new String[] {"csl"})); // PORT: §3.12 named arguments; skipped optionals pass the upstream default
}
