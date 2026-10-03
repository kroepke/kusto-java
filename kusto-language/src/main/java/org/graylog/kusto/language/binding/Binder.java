// Ported from: src/Kusto.Language/Binder/Binder_API.cs
// Ported from: src/Kusto.Language/Binder/Binder_AsContextBuilder.cs
// Ported from: src/Kusto.Language/Binder/Binder_ContextBuilder.cs
// Ported from: src/Kusto.Language/Binder/Binder_FunctionCalls.cs
// Ported from: src/Kusto.Language/Binder/Binder_Misc.cs
// Ported from: src/Kusto.Language/Binder/Binder_Names.cs
// Ported from: src/Kusto.Language/Binder/Binder_NodeBinder.cs
// Ported from: src/Kusto.Language/Binder/Binder_Operators.cs
// Ported from: src/Kusto.Language/Binder/Binder_Projection.cs
// Ported from: src/Kusto.Language/Binder/Binder_SearchPredicateBinder.cs
// Ported from: src/Kusto.Language/Binder/Binder_TablesAndColumns.cs
// Ported from: src/Kusto.Language/Binder/Binder_TreeBinder.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.binding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.DiagnosticFacts;
import org.graylog.kusto.language.DiagnosticSeverity;
import org.graylog.kusto.language.FunctionBodyFacts;
import org.graylog.kusto.language.FunctionCallExpansion;
import org.graylog.kusto.language.Functions;
import org.graylog.kusto.language.GlobalState;
import org.graylog.kusto.language.IncludeFunctionKind;
import org.graylog.kusto.language.KustoFacts;
import org.graylog.kusto.language.PlugIns;
import org.graylog.kusto.language.Properties;
import org.graylog.kusto.language.QueryOperatorParameter;
import org.graylog.kusto.language.QueryOperatorParameterValueKind;
import org.graylog.kusto.language.QueryOperatorParameters;
import org.graylog.kusto.language.binding.Binder;
import org.graylog.kusto.language.binding.ProjectionBuilder;
import org.graylog.kusto.language.binding.SemanticInfo;
import org.graylog.kusto.language.editor.ClientDirectiveArgument;
import org.graylog.kusto.language.editor.CompletionHint;
import org.graylog.kusto.language.parsing.QueryParser;
import org.graylog.kusto.language.parsing.TokenParser;
import org.graylog.kusto.language.symbols.ArgumentKind;
import org.graylog.kusto.language.symbols.ClusterSymbol;
import org.graylog.kusto.language.symbols.ColumnSymbol;
import org.graylog.kusto.language.symbols.CombineKind;
import org.graylog.kusto.language.symbols.CommandSymbol;
import org.graylog.kusto.language.symbols.Conversion;
import org.graylog.kusto.language.symbols.CustomAvailabilityContext;
import org.graylog.kusto.language.symbols.CustomReturnTypeContext;
import org.graylog.kusto.language.symbols.DatabaseSymbol;
import org.graylog.kusto.language.symbols.DynamicArraySymbol;
import org.graylog.kusto.language.symbols.DynamicBagSymbol;
import org.graylog.kusto.language.symbols.DynamicPrimitiveSymbol;
import org.graylog.kusto.language.symbols.DynamicSymbol;
import org.graylog.kusto.language.symbols.EntityGroupElementSymbol;
import org.graylog.kusto.language.symbols.EntityGroupSymbol;
import org.graylog.kusto.language.symbols.ErrorSymbol;
import org.graylog.kusto.language.symbols.FunctionSymbol;
import org.graylog.kusto.language.symbols.GraphSymbol;
import org.graylog.kusto.language.symbols.GroupSymbol;
import org.graylog.kusto.language.symbols.OperatorKind;
import org.graylog.kusto.language.symbols.OptionSymbol;
import org.graylog.kusto.language.symbols.Parameter;
import org.graylog.kusto.language.symbols.ParameterSymbol;
import org.graylog.kusto.language.symbols.ParameterTypeKind;
import org.graylog.kusto.language.symbols.PatternSignature;
import org.graylog.kusto.language.symbols.PatternSymbol;
import org.graylog.kusto.language.symbols.ResultNameKind;
import org.graylog.kusto.language.symbols.ReturnTypeKind;
import org.graylog.kusto.language.symbols.ScalarSymbol;
import org.graylog.kusto.language.symbols.ScalarTypes;
import org.graylog.kusto.language.symbols.Signature;
import org.graylog.kusto.language.symbols.StoredQueryResultSymbol;
import org.graylog.kusto.language.symbols.Symbol;
import org.graylog.kusto.language.symbols.SymbolKind;
import org.graylog.kusto.language.symbols.SymbolMatch;
import org.graylog.kusto.language.symbols.SymbolMatchExtensions;
import org.graylog.kusto.language.symbols.TableSymbol;
import org.graylog.kusto.language.symbols.TupleSymbol;
import org.graylog.kusto.language.symbols.TypeFacts;
import org.graylog.kusto.language.symbols.TypeSymbol;
import org.graylog.kusto.language.symbols.VariableSymbol;
import org.graylog.kusto.language.symbols.VoidSymbol;
import org.graylog.kusto.language.syntax.AliasStatement;
import org.graylog.kusto.language.syntax.AsOperator;
import org.graylog.kusto.language.syntax.AssertSchemaOperator;
import org.graylog.kusto.language.syntax.AtExpression;
import org.graylog.kusto.language.syntax.BadCommand;
import org.graylog.kusto.language.syntax.BadQueryOperator;
import org.graylog.kusto.language.syntax.BetweenExpression;
import org.graylog.kusto.language.syntax.BinaryExpression;
import org.graylog.kusto.language.syntax.BracedName;
import org.graylog.kusto.language.syntax.BracketedExpression;
import org.graylog.kusto.language.syntax.BracketedName;
import org.graylog.kusto.language.syntax.BracketedWildcardedName;
import org.graylog.kusto.language.syntax.Command;
import org.graylog.kusto.language.syntax.CommandAndSkippedTokens;
import org.graylog.kusto.language.syntax.CommandBlock;
import org.graylog.kusto.language.syntax.CommandWithPropertyListClause;
import org.graylog.kusto.language.syntax.CommandWithValueClause;
import org.graylog.kusto.language.syntax.CompoundNamedExpression;
import org.graylog.kusto.language.syntax.CompoundStringLiteralExpression;
import org.graylog.kusto.language.syntax.ConsumeOperator;
import org.graylog.kusto.language.syntax.ContextualDataTableExpression;
import org.graylog.kusto.language.syntax.CountAsIdentifierClause;
import org.graylog.kusto.language.syntax.CountOperator;
import org.graylog.kusto.language.syntax.CustomCommand;
import org.graylog.kusto.language.syntax.CustomNode;
import org.graylog.kusto.language.syntax.DataScopeClause;
import org.graylog.kusto.language.syntax.DataScopeExpression;
import org.graylog.kusto.language.syntax.DataTableExpression;
import org.graylog.kusto.language.syntax.DateTimePattern;
import org.graylog.kusto.language.syntax.DefaultExpressionClause;
import org.graylog.kusto.language.syntax.DefaultSyntaxVisitor;
import org.graylog.kusto.language.syntax.DefaultValueDeclaration;
import org.graylog.kusto.language.syntax.Directive;
import org.graylog.kusto.language.syntax.DirectiveBlock;
import org.graylog.kusto.language.syntax.DistinctOperator;
import org.graylog.kusto.language.syntax.DynamicExpression;
import org.graylog.kusto.language.syntax.ElementExpression;
import org.graylog.kusto.language.syntax.EntityGroup;
import org.graylog.kusto.language.syntax.EvaluateOperator;
import org.graylog.kusto.language.syntax.EvaluateRowSchema;
import org.graylog.kusto.language.syntax.EvaluateSchemaClause;
import org.graylog.kusto.language.syntax.ExecuteAndCacheOperator;
import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.syntax.ExpressionCouple;
import org.graylog.kusto.language.syntax.ExpressionList;
import org.graylog.kusto.language.syntax.ExpressionStatement;
import org.graylog.kusto.language.syntax.ExtendOperator;
import org.graylog.kusto.language.syntax.ExternalDataExpression;
import org.graylog.kusto.language.syntax.ExternalDataWithClause;
import org.graylog.kusto.language.syntax.FacetOperator;
import org.graylog.kusto.language.syntax.FacetWithExpressionClause;
import org.graylog.kusto.language.syntax.FacetWithOperatorClause;
import org.graylog.kusto.language.syntax.FakeExpression;
import org.graylog.kusto.language.syntax.FilterOperator;
import org.graylog.kusto.language.syntax.FindInClause;
import org.graylog.kusto.language.syntax.FindOperator;
import org.graylog.kusto.language.syntax.FindProjectClause;
import org.graylog.kusto.language.syntax.ForkExpression;
import org.graylog.kusto.language.syntax.ForkOperator;
import org.graylog.kusto.language.syntax.FunctionBody;
import org.graylog.kusto.language.syntax.FunctionCallExpression;
import org.graylog.kusto.language.syntax.FunctionDeclaration;
import org.graylog.kusto.language.syntax.FunctionParameter;
import org.graylog.kusto.language.syntax.FunctionParameters;
import org.graylog.kusto.language.syntax.GetSchemaOperator;
import org.graylog.kusto.language.syntax.GraphMarkComponentsOperator;
import org.graylog.kusto.language.syntax.GraphMatchOperator;
import org.graylog.kusto.language.syntax.GraphMatchPattern;
import org.graylog.kusto.language.syntax.GraphMatchPatternEdge;
import org.graylog.kusto.language.syntax.GraphMatchPatternEdgeRange;
import org.graylog.kusto.language.syntax.GraphMatchPatternNode;
import org.graylog.kusto.language.syntax.GraphShortestPathsOperator;
import org.graylog.kusto.language.syntax.GraphToTableAsClause;
import org.graylog.kusto.language.syntax.GraphToTableOperator;
import org.graylog.kusto.language.syntax.GraphToTableOutputClause;
import org.graylog.kusto.language.syntax.GraphWhereEdgesOperator;
import org.graylog.kusto.language.syntax.GraphWhereNodesOperator;
import org.graylog.kusto.language.syntax.HasAllExpression;
import org.graylog.kusto.language.syntax.HasAnyExpression;
import org.graylog.kusto.language.syntax.InExpression;
import org.graylog.kusto.language.syntax.IncludeTrivia;
import org.graylog.kusto.language.syntax.InlineExternalTableConnectionStringsClause;
import org.graylog.kusto.language.syntax.InlineExternalTableDataFormatClause;
import org.graylog.kusto.language.syntax.InlineExternalTableExpression;
import org.graylog.kusto.language.syntax.InlineExternalTableKindClause;
import org.graylog.kusto.language.syntax.InlineExternalTablePartitionClause;
import org.graylog.kusto.language.syntax.InlineExternalTablePathFormatClause;
import org.graylog.kusto.language.syntax.InlineExternalTablePathFormatPartitionColumnReference;
import org.graylog.kusto.language.syntax.InvokeOperator;
import org.graylog.kusto.language.syntax.JoinOnClause;
import org.graylog.kusto.language.syntax.JoinOperator;
import org.graylog.kusto.language.syntax.JoinWhereClause;
import org.graylog.kusto.language.syntax.JsonArrayExpression;
import org.graylog.kusto.language.syntax.JsonObjectExpression;
import org.graylog.kusto.language.syntax.JsonPair;
import org.graylog.kusto.language.syntax.LetStatement;
import org.graylog.kusto.language.syntax.LiteralExpression;
import org.graylog.kusto.language.syntax.LookupOperator;
import org.graylog.kusto.language.syntax.MacroExpandOperator;
import org.graylog.kusto.language.syntax.MacroExpandScopeReferenceName;
import org.graylog.kusto.language.syntax.MakeGraphOperator;
import org.graylog.kusto.language.syntax.MakeGraphPartitionedByClause;
import org.graylog.kusto.language.syntax.MakeGraphTableAndKeyClause;
import org.graylog.kusto.language.syntax.MakeGraphWithImplicitIdClause;
import org.graylog.kusto.language.syntax.MakeGraphWithTablesAndKeysClause;
import org.graylog.kusto.language.syntax.MakeSeriesByClause;
import org.graylog.kusto.language.syntax.MakeSeriesExpression;
import org.graylog.kusto.language.syntax.MakeSeriesFromClause;
import org.graylog.kusto.language.syntax.MakeSeriesFromToStepClause;
import org.graylog.kusto.language.syntax.MakeSeriesInRangeClause;
import org.graylog.kusto.language.syntax.MakeSeriesOnClause;
import org.graylog.kusto.language.syntax.MakeSeriesOperator;
import org.graylog.kusto.language.syntax.MakeSeriesStepClause;
import org.graylog.kusto.language.syntax.MakeSeriesToClause;
import org.graylog.kusto.language.syntax.MaterializeExpression;
import org.graylog.kusto.language.syntax.MaterializedViewCombineClause;
import org.graylog.kusto.language.syntax.MaterializedViewCombineExpression;
import org.graylog.kusto.language.syntax.MaterializedViewCombineNameClause;
import org.graylog.kusto.language.syntax.MvApplyContextIdClause;
import org.graylog.kusto.language.syntax.MvApplyExpression;
import org.graylog.kusto.language.syntax.MvApplyOperator;
import org.graylog.kusto.language.syntax.MvApplyRowLimitClause;
import org.graylog.kusto.language.syntax.MvApplySubqueryExpression;
import org.graylog.kusto.language.syntax.MvExpandExpression;
import org.graylog.kusto.language.syntax.MvExpandOperator;
import org.graylog.kusto.language.syntax.MvExpandRowLimitClause;
import org.graylog.kusto.language.syntax.Name;
import org.graylog.kusto.language.syntax.NameAndTypeDeclaration;
import org.graylog.kusto.language.syntax.NameDeclaration;
import org.graylog.kusto.language.syntax.NameEqualsClause;
import org.graylog.kusto.language.syntax.NameReference;
import org.graylog.kusto.language.syntax.NameReferenceList;
import org.graylog.kusto.language.syntax.NamedExpression;
import org.graylog.kusto.language.syntax.NamedParameter;
import org.graylog.kusto.language.syntax.OptionValueClause;
import org.graylog.kusto.language.syntax.OrderedExpression;
import org.graylog.kusto.language.syntax.OrderingClause;
import org.graylog.kusto.language.syntax.OrderingNullsClause;
import org.graylog.kusto.language.syntax.PackExpression;
import org.graylog.kusto.language.syntax.ParenthesizedExpression;
import org.graylog.kusto.language.syntax.ParseKvOperator;
import org.graylog.kusto.language.syntax.ParseKvWithClause;
import org.graylog.kusto.language.syntax.ParseOperator;
import org.graylog.kusto.language.syntax.ParseWhereOperator;
import org.graylog.kusto.language.syntax.PartialCommand;
import org.graylog.kusto.language.syntax.PartitionByIdClause;
import org.graylog.kusto.language.syntax.PartitionByOperator;
import org.graylog.kusto.language.syntax.PartitionColumnDeclaration;
import org.graylog.kusto.language.syntax.PartitionOperator;
import org.graylog.kusto.language.syntax.PartitionQuery;
import org.graylog.kusto.language.syntax.PartitionScope;
import org.graylog.kusto.language.syntax.PartitionSubquery;
import org.graylog.kusto.language.syntax.PathExpression;
import org.graylog.kusto.language.syntax.PatternDeclaration;
import org.graylog.kusto.language.syntax.PatternMatch;
import org.graylog.kusto.language.syntax.PatternPathParameter;
import org.graylog.kusto.language.syntax.PatternPathValue;
import org.graylog.kusto.language.syntax.PatternStatement;
import org.graylog.kusto.language.syntax.PipeExpression;
import org.graylog.kusto.language.syntax.PrefixUnaryExpression;
import org.graylog.kusto.language.syntax.PrimitiveTypeExpression;
import org.graylog.kusto.language.syntax.PrintOperator;
import org.graylog.kusto.language.syntax.ProjectAwayOperator;
import org.graylog.kusto.language.syntax.ProjectByNamesOperator;
import org.graylog.kusto.language.syntax.ProjectClause;
import org.graylog.kusto.language.syntax.ProjectKeepOperator;
import org.graylog.kusto.language.syntax.ProjectOperator;
import org.graylog.kusto.language.syntax.ProjectRenameOperator;
import org.graylog.kusto.language.syntax.ProjectReorderOperator;
import org.graylog.kusto.language.syntax.QueryBlock;
import org.graylog.kusto.language.syntax.QueryOperator;
import org.graylog.kusto.language.syntax.QueryParametersStatement;
import org.graylog.kusto.language.syntax.RangeOperator;
import org.graylog.kusto.language.syntax.ReduceByOperator;
import org.graylog.kusto.language.syntax.ReduceByWithClause;
import org.graylog.kusto.language.syntax.RenameList;
import org.graylog.kusto.language.syntax.RenderOperator;
import org.graylog.kusto.language.syntax.RenderWithClause;
import org.graylog.kusto.language.syntax.RestrictStatement;
import org.graylog.kusto.language.syntax.RestrictStatementWithClause;
import org.graylog.kusto.language.syntax.RowSchema;
import org.graylog.kusto.language.syntax.SampleDistinctOperator;
import org.graylog.kusto.language.syntax.SampleOperator;
import org.graylog.kusto.language.syntax.ScanAssignment;
import org.graylog.kusto.language.syntax.ScanComputationClause;
import org.graylog.kusto.language.syntax.ScanDeclareClause;
import org.graylog.kusto.language.syntax.ScanOperator;
import org.graylog.kusto.language.syntax.ScanOrderByClause;
import org.graylog.kusto.language.syntax.ScanPartitionByClause;
import org.graylog.kusto.language.syntax.ScanStep;
import org.graylog.kusto.language.syntax.ScanStepOutput;
import org.graylog.kusto.language.syntax.SchemaTypeExpression;
import org.graylog.kusto.language.syntax.SearchOperator;
import org.graylog.kusto.language.syntax.SeparatedElement1;
import org.graylog.kusto.language.syntax.SeparatedElement;
import org.graylog.kusto.language.syntax.SerializeOperator;
import org.graylog.kusto.language.syntax.SetOptionStatement;
import org.graylog.kusto.language.syntax.SimpleNamedExpression;
import org.graylog.kusto.language.syntax.SkippedTokens;
import org.graylog.kusto.language.syntax.SortOperator;
import org.graylog.kusto.language.syntax.StarExpression;
import org.graylog.kusto.language.syntax.Statement;
import org.graylog.kusto.language.syntax.SummarizeByClause;
import org.graylog.kusto.language.syntax.SummarizeOperator;
import org.graylog.kusto.language.syntax.SyntaxElement;
import org.graylog.kusto.language.syntax.SyntaxExtensions;
import org.graylog.kusto.language.syntax.SyntaxFacts;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.graylog.kusto.language.syntax.SyntaxList1;
import org.graylog.kusto.language.syntax.SyntaxList;
import org.graylog.kusto.language.syntax.SyntaxNode;
import org.graylog.kusto.language.syntax.SyntaxNodeExtensions;
import org.graylog.kusto.language.syntax.SyntaxToken;
import org.graylog.kusto.language.syntax.SyntaxTree;
import org.graylog.kusto.language.syntax.SyntaxVisitor1;
import org.graylog.kusto.language.syntax.TakeOperator;
import org.graylog.kusto.language.syntax.ToScalarExpression;
import org.graylog.kusto.language.syntax.ToTableExpression;
import org.graylog.kusto.language.syntax.ToTypeOfClause;
import org.graylog.kusto.language.syntax.TokenName;
import org.graylog.kusto.language.syntax.TopHittersByClause;
import org.graylog.kusto.language.syntax.TopHittersOperator;
import org.graylog.kusto.language.syntax.TopNestedClause;
import org.graylog.kusto.language.syntax.TopNestedOperator;
import org.graylog.kusto.language.syntax.TopNestedWithOthersClause;
import org.graylog.kusto.language.syntax.TopOperator;
import org.graylog.kusto.language.syntax.TypeExpression;
import org.graylog.kusto.language.syntax.TypeOfLiteralExpression;
import org.graylog.kusto.language.syntax.TypedColumnReference;
import org.graylog.kusto.language.syntax.UnionOperator;
import org.graylog.kusto.language.syntax.UnknownCommand;
import org.graylog.kusto.language.syntax.ValueInfo;
import org.graylog.kusto.language.syntax.WhereClause;
import org.graylog.kusto.language.syntax.WildcardedName;
import org.graylog.kusto.language.utils.CancellationToken;
import org.graylog.kusto.language.utils.ConnectionInfo;
import org.graylog.kusto.language.utils.DictionaryExtensions;
import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.MostRecentlyUsedCache;
import org.graylog.kusto.language.utils.ObjectPool;
import org.graylog.kusto.language.utils.StringAndNumberComparer;
import org.graylog.kusto.language.utils.UniqueNameTable;
import org.graylog.kusto.language.utils.ValueComparer;
import org.graylog.kusto.language.utils.dotnet.DateTime;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Func3;
import org.graylog.kusto.language.utils.dotnet.Internal;
import org.graylog.kusto.language.utils.dotnet.Linq;
import org.graylog.kusto.language.utils.dotnet.Out;

@Internal
public final class Binder
{
    // ===== upstream part: Binder_API.cs =====
    /// <summary>
    /// Global state including symbols declared in ambient database.
    /// </summary>
    private final GlobalState _globals;

    /// <summary>
    /// Keeps track of the number of dynamic nodes in the current traversal.
    /// Used to check if the current node is a child of a dynamic node.
    /// </summary>
    private int _dynamicDepth = 0;

    /// <summary>
    /// The cluster assumed when resolveing unqualified calls to database() 
    /// </summary>
    private ClusterSymbol _currentCluster;

    /// <summary>
    /// The database assumed when resolving unqualified references table/function names or calls to table()
    /// </summary>
    private DatabaseSymbol _currentDatabase;

    /// <summary>
    /// The function being declared.
    /// </summary>
    private final FunctionSymbol _currentFunction;

    /// <summary>
    /// The fuzzy entity evaluation is in effect.
    /// </summary>
    private boolean _isFuzzy;

    /// <summary>
    /// All symbol declared locally within the query appear in the local scope.
    /// These are symbols declared by let statements or the as query operator.
    /// Local scopes may be nested within other local scopes.
    /// </summary>
    private LocalScope _localScope;

    /// <summary>
    /// Columns accessible in piped query operators, or from the $left variable in a join on clause.
    /// </summary>
    private TableSymbol _rowScope;

    /// <summary>
    /// Columns accessible from right side of join operator via the $right variable
    /// </summary>
    private TableSymbol _rightRowScope;

    /// <summary>
    /// True if column must match both left and right
    /// </summary>
    private boolean _commonColumnsOnly;

    /// <summary>
    /// Members accessible from left side of path/element expression
    /// </summary>
    private Symbol _pathScope;

    /// <summary>
    /// Implicit argument type used for invoke binding.
    /// </summary>
    private TypeSymbol _implicitArgumentType;

    /// <summary>
    /// The kind of scope in effect.
    /// </summary>
    private ScopeKind _scopeKind = ScopeKind.Normal; // PORT: §3.9 default(enum)

    /// <summary>
    /// The binder for the outer scope
    /// </summary>
    private Binder _outerBinder;

    /// <summary>
    /// Remembered local binding scope state just before a function is declared.
    /// </summary>
    private Map<FunctionDeclaration, LocalScope> _staticScopes; // PORT: §3.17 Dictionary → Map (LinkedHashMap)

    /// <summary>
    /// Any aliased databases.
    /// </summary>
    private final Map<String, DatabaseSymbol> _aliasedDatabases = // PORT: §3.17 Dictionary → Map (LinkedHashMap)
        new LinkedHashMap<String, DatabaseSymbol>();

    /// <summary>
    /// Binding state that is shared across many binders/bindings
    /// </summary>
    private final GlobalBindingCache _globalBindingCache;

    /// <summary>
    /// Binding state that is private to one binding (including analysis of called function bodies)
    /// </summary>
    private final LocalBindingCache _localBindingCache;

    /// <summary>
    /// An optional function that assigns <see cref="SemanticInfo"/> to a <see cref="SyntaxNode"/>
    /// </summary>
    private final BiConsumer<SyntaxNode, SemanticInfo> _semanticInfoSetter;

    /// <summary>
    /// An optional <see cref="CancellationToken"/> specified for use during binding.
    /// </summary>
    private final CancellationToken _cancellationToken;

    private Binder(
        GlobalState globals,
        ClusterSymbol currentCluster,
        DatabaseSymbol currentDatabase,
        FunctionSymbol currentFunction,
        Binder outerBinder,
        LocalScope outerScope,
        GlobalBindingCache globalBindingCache,
        LocalBindingCache localBindingCache,
        BiConsumer<SyntaxNode, SemanticInfo> semanticInfoSetter,
        CancellationToken cancellationToken)
    {
        _globals = globals;
        _currentCluster = currentCluster != null ? currentCluster : globals.cluster(); // PORT: §3.14 ??
        _currentDatabase = currentDatabase != null ? currentDatabase : globals.database(); // PORT: §3.14 ??
        _currentFunction = currentFunction;
        _isFuzzy = outerBinder != null ? outerBinder._isFuzzy : false; // PORT: §3.14 ?. ??
        _outerBinder = outerBinder;
        _globalBindingCache = globalBindingCache != null ? globalBindingCache : new GlobalBindingCache(); // PORT: §3.14 ??
        _localBindingCache = localBindingCache != null ? localBindingCache : new LocalBindingCache(); // PORT: §3.14 ??
        _localScope = new LocalScope(outerScope);
        _semanticInfoSetter = semanticInfoSetter != null ? semanticInfoSetter : Binder::defaultSetSemanticInfo; // PORT: §3.14 ??
        _cancellationToken = cancellationToken;
        _staticScopes = outerBinder != null ? outerBinder._staticScopes : new LinkedHashMap<FunctionDeclaration, LocalScope>(); // PORT: §3.14 ?. ??
    }

    public TableSymbol rowScopeOrEmpty() { return _rowScope != null ? _rowScope : TableSymbol.Empty; } // PORT: §3.1, §3.14 ??
    public TableSymbol rightRowScopeOrEmpty() { return _rightRowScope != null ? _rightRowScope : TableSymbol.Empty; } // PORT: §3.1, §3.14 ??

    /// <summary>
    /// Do semantic analysis over the syntax tree.
    /// </summary>
    public static boolean tryBind(
        SyntaxTree tree,
        GlobalState globals,
        LocalBindingCache localBindingCache,
        BiConsumer<SyntaxNode, SemanticInfo> semanticInfoSetter,
        CancellationToken cancellationToken)
    {
        if (!tree.isSafeToRecurse(globals))
            return false;

        globals = globals.withCache();
        var bindingCache = globals.cache().getOrCreate(GlobalBindingCache.class); // PORT: §3.10 GetOrCreate<T>()
        synchronized (bindingCache) // PORT: §3.13 lock
        {
            var binder = new Binder(
                globals,
                globals.cluster(),
                globals.database(),
                null, // currentFunction
                null, // outer binder
                getDefaultOuterScope(globals),
                bindingCache,
                localBindingCache,
                semanticInfoSetter, // PORT: §3.12 named argument semanticInfoSetter
                cancellationToken); // PORT: §3.12 named argument cancellationToken

            var treeBinder = new TreeBinder(binder);

            tree.root().accept(treeBinder);
            return true;
        }
    }

    public static boolean tryBind(SyntaxTree tree, GlobalState globals, LocalBindingCache localBindingCache, BiConsumer<SyntaxNode, SemanticInfo> semanticInfoSetter) // PORT: §3.12 cancellationToken = default(CancellationToken)
    {
        return tryBind(tree, globals, localBindingCache, semanticInfoSetter, CancellationToken.NONE);
    }

    public static boolean tryBind(SyntaxTree tree, GlobalState globals, LocalBindingCache localBindingCache) // PORT: §3.12 semanticInfoSetter = null
    {
        return tryBind(tree, globals, localBindingCache, null, CancellationToken.NONE);
    }

    public static boolean tryBind(SyntaxTree tree, GlobalState globals) // PORT: §3.12 localBindingCache = null
    {
        return tryBind(tree, globals, null, null, CancellationToken.NONE);
    }

    private static LocalScope getDefaultOuterScope(GlobalState globals)
    {
        LocalScope outerScope = null;

        if (globals.ambientSymbols().size() > 0)
        {
            outerScope = new LocalScope();
            outerScope.addSymbols(globals.ambientSymbols());
        }

        return outerScope;
    }

    @Internal
    public static void defaultSetSemanticInfo(SyntaxNode node, SemanticInfo info)
    {
        if (info != null)
        {
            var data = node.getExtendedData(true); // PORT: §3.12 named argument create
            data.SemanticInfo = info;
        }
    }

    /// <summary>
    /// Do semantic analysis over the body of a called function.
    /// </summary>
    public static boolean tryBindCalledFunctionBody(
        SyntaxTree bodyTree,
        Binder outer,
        ClusterSymbol currentCluster,
        DatabaseSymbol currentDatabase,
        FunctionSymbol currentFunction,
        LocalScope outerScope,
        Iterable<? extends Symbol> locals) // PORT: §3.17 IEnumerable<Symbol> → Iterable
    {
        if (!bodyTree.isSafeToRecurse(outer._globals))
            return false;

        var binder = new Binder(
            outer._globals,
            currentCluster != null ? currentCluster : outer._currentCluster, // PORT: §3.14 ??
            currentDatabase != null ? currentDatabase : outer._currentDatabase, // PORT: §3.14 ??
            currentFunction,
            outer,
            outerScope,
            outer._globalBindingCache,
            outer._localBindingCache,
            outer._semanticInfoSetter,
            outer._cancellationToken);

        if (locals != null)
        {
            binder.setLocals(locals);
        }

        var treeBinder = new TreeBinder(binder);
        bodyTree.root().accept(treeBinder);

        return true;
    }

    /// <summary>
    /// Adds the symbols to the current local scope.
    /// </summary>
    private void setLocals(Iterable<? extends Symbol> locals) // PORT: §3.17 IEnumerable<Symbol> → Iterable
    {
        for (Symbol local : locals)
        {
            _localScope.addSymbol(local);
        }
    }

    /// <summary>
    /// Sets the context of the binder to the specified node and text position.
    /// </summary>
    private void setContext(SyntaxNode contextNode, int position)
    {
        // note: assumes this API is only called at most once after constructor.
        if (contextNode != null)
        {
            var builder = new ContextBuilder(this, position >= 0 ? position : contextNode.textStart());
            contextNode.accept(builder);
        }
    }

    private void setContext(SyntaxNode contextNode) // PORT: §3.12 position = -1
    {
        setContext(contextNode, -1);
    }

    /// <summary>
    /// Gets the computed return type for functions specified with a body or declaration.
    /// </summary>
    public static TypeSymbol getComputedReturnType(
        Signature signature,
        GlobalState globals,
        List<TypeSymbol> argumentTypes)
    {
        globals = globals.withCache();

        var currentDatabase = globals.getDatabase(signature.symbol());
        var currentCluster = globals.getCluster(currentDatabase);

        var bindingCache = globals.cache().getOrCreate(GlobalBindingCache.class); // PORT: §3.10 GetOrCreate<T>()
        synchronized (bindingCache) // PORT: §3.13 lock
        {
            FunctionSymbol currentFunction = signature.symbol() instanceof FunctionSymbol fs ? fs : null; // PORT: §3.15 as
            var binder = new Binder(
                globals,
                currentCluster,
                currentDatabase,
                currentFunction, // currentFunction
                null, // outer binder
                getDefaultOuterScope(globals),
                bindingCache,
                null, // PORT: §3.12 named argument localBindingCache
                null, // PORT: §3.12 named argument semanticInfoSetter
                CancellationToken.NONE); // PORT: §3.12 named argument cancellationToken: default(CancellationToken)

            return binder.getComputedFunctionCallResult(signature, null, argumentTypes).type();
        }
    }

    public static TypeSymbol getComputedReturnType(Signature signature, GlobalState globals) // PORT: §3.12 argumentTypes = null
    {
        return getComputedReturnType(signature, globals, null);
    }

    /// <summary>
    /// Gets the symbol that would be referenced at the specified location.
    /// </summary>
    public static Symbol getReferencedSymbol(SyntaxTree tree, int position, String name, GlobalState globals, int match, CancellationToken cancellationToken) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        if (tree.isSafeToRecurse(globals))
        {
            globals = globals.withCache();
            var bindingCache = globals.cache().getOrCreate(GlobalBindingCache.class); // PORT: §3.10 GetOrCreate<T>()
            synchronized (bindingCache) // PORT: §3.13 lock
            {
                var binder = new Binder(
                    globals,
                    globals.cluster(),
                    globals.database(),
                    null, // currentFunction
                    null, // outer binder
                    getDefaultOuterScope(globals),
                    bindingCache,
                    null, // PORT: §3.12 named argument localBindingCache
                    null, // PORT: §3.12 named argument semanticInfoSetter
                    cancellationToken); // PORT: §3.12 named argument cancellationToken
                var startNode = getStartNode(tree.root(), position);
                if (startNode != null)
                {
                    binder.setContext(startNode, position);
                    var info = binder.bindName(name, match, startNode); // PORT: §3.12 includeRowScope = true, inferColumns = true
                    return info != null ? info.referencedSymbol() : null; // PORT: §3.14 ?.
                }
            }
        }

        return null;
    }

    /// <summary>
    /// Gets the <see cref="TableSymbol"/> that is in scope as the implicit set of columns accessible within a query.
    /// </summary>
    public static TableSymbol getRowScope(SyntaxTree tree, int position, GlobalState globals, CancellationToken cancellationToken)
    {
        if (tree.isSafeToRecurse(globals))
        {
            globals = globals.withCache();
            var bindingCache = globals.cache().getOrCreate(GlobalBindingCache.class); // PORT: §3.10 GetOrCreate<T>()
            synchronized (bindingCache) // PORT: §3.13 lock
            {
                var binder = new Binder(
                    globals,
                    globals.cluster(),
                    globals.database(),
                    null, // currentFunction
                    null, // outer binder
                    getDefaultOuterScope(globals),
                    bindingCache,
                    null, // PORT: §3.12 named argument localBindingCache
                    null, // PORT: §3.12 named argument semanticInfoSetter
                    cancellationToken); // PORT: §3.12 named argument cancellationToken
                var startNode = getStartNode(tree.root(), position);
                if (startNode != null)
                {
                    binder.setContext(startNode, position);
                    return binder._rowScope;
                }
            }
        }

        return TableSymbol.Empty;
    }

    public static TableSymbol getRowScope(SyntaxTree tree, int position, GlobalState globals) // PORT: §3.12 cancellationToken = default(CancellationToken)
    {
        return getRowScope(tree, position, globals, CancellationToken.NONE);
    }

    /// <summary>
    /// Gets all the symbols that are in scope at the text position.
    /// </summary>
    public static void getSymbolsInScope(SyntaxTree tree, int position, GlobalState globals, int match, int include, List<Symbol> list, CancellationToken cancellationToken) // PORT: §3.17 SymbolMatch/IncludeFunctionKind are int holders (D23)
    {
        if (tree.isSafeToRecurse(globals))
        {
            globals = globals.withCache();
            var bindingCache = globals.cache().getOrCreate(GlobalBindingCache.class); // PORT: §3.10 GetOrCreate<T>()
            synchronized (bindingCache) // PORT: §3.13 lock
            {
                var binder = new Binder(
                    globals,
                    globals.cluster(),
                    globals.database(),
                    null, // currentFunction
                    null, // outer binder
                    getDefaultOuterScope(globals),
                    bindingCache,
                    null, // PORT: §3.12 named argument localBindingCache
                    null, // PORT: §3.12 named argument semanticInfoSetter
                    cancellationToken); // PORT: §3.12 named argument cancellationToken
                var startNode = getStartNode(tree.root(), position);
                if (startNode != null)
                {
                    binder.setContext(startNode, position);
                    binder.getSymbolsInContext(startNode, match, include, list);
                }
            }
        }
    }

    private static SyntaxNode getStartNode(SyntaxNode root, int position)
    {
        var token = root.getTokenAt(position);

        if (token != null)
        {
            if (position <= token.textStart())
            {
                var prev = token.getPreviousToken();
                if (prev != null && prev.depth() >= token.depth())
                {
                    return prev.parent();
                }
            }

            return token.parent();
        }

        return null;
    }

    private void getSymbolsInContext(SyntaxNode contextNode, int match, int include, List<Symbol> list) // PORT: §3.17 SymbolMatch/IncludeFunctionKind are int holders (D23)
    {
        if (_pathScope instanceof GroupSymbol g
            && isPassThrough(g))
        {
            var savePathScope = _pathScope;
            for (Symbol s : g.members())
            {
                _pathScope = s;
                getSymbolsInContext(contextNode, match, include, list);
            }
            _pathScope = savePathScope;
        }
        else if (_pathScope != null)
        {
            var isInsideControlCommand = isInsideControlCommandProper(contextNode);
            var memberMatch = match;

            // so far only columns, tables, materialized-views, entity_groups and functions can be dot accessed.
            if (!isInsideControlCommand)
                memberMatch = match & (SymbolMatch.Column | SymbolMatch.Table | SymbolMatch.MaterializedView | SymbolMatch.EntityGroup | SymbolMatch.Function);

            // if this is an entity group element then add special members
            if (getMacroExpandScope(contextNode) != null)
            {
                list.addAll(EntityGroupElementSymbol.SpecialMembers);
            }

            if (_pathScope instanceof DatabaseSymbol)
            {
                // cannot directly get to external tables
                memberMatch &= ~SymbolMatch.ExternalTable;
            }

            // table.column only works in commands
            if (_pathScope instanceof TableSymbol && !isInsideControlCommand)
            {
                memberMatch &= ~SymbolMatch.Column;
            }

            // any columns or tables from left-hand side?
            if ((memberMatch & SymbolMatch.Column) != 0)
            {
                if (_pathScope instanceof TableSymbol table && table.isOpen())
                {
                    list.addAll(getDeclaredAndInferredColumns(table));
                }
                else if (_pathScope instanceof TupleSymbol tuple && tuple.relatedTable() != null && tuple.relatedTable().isOpen())
                {
                    list.addAll(getDeclaredAndInferredColumns(tuple.relatedTable()));
                }
                else
                {
                    getPathMembers(_pathScope, memberMatch, list);
                }
            }
            else if (memberMatch != SymbolMatch.None)
            {
                getPathMembers(_pathScope, memberMatch, list);
            }

            // any special functions from left-hand side?
            if ((match & SymbolMatch.Function) != 0)
            {
                getSpecialFunctions(null, list);
            }
        }
        else
        {
            switch (_scopeKind)
            {
                case Normal:
                    // row scope columns
                    if (_rowScope != null && (match & SymbolMatch.Column) != 0)
                    {
                        if (_rightRowScope != null)
                        {
                            // add $left and $right variables
                            list.add(new VariableSymbol("$left", getTuple(_rowScope)));
                            list.add(new VariableSymbol("$right", getTuple(_rightRowScope)));

                            // common columns
                            getCommonColumns(getDeclaredAndInferredColumns(_rowScope), getDeclaredAndInferredColumns(_rightRowScope), list);
                        }
                        else
                        {
                            _rowScope.getMembers(match, list);
                        }
                    }

                    var localMatch = match;

                    if ((include & IncludeFunctionKind.LocalFunctions) == 0)
                        localMatch &= ~SymbolMatch.Function;

                    if ((include & IncludeFunctionKind.LocalViews) == 0)
                        localMatch &= ~SymbolMatch.View;

                    // local symbols
                    _localScope.getSymbols(localMatch, list);

                    // get any built-in functions
                    if ((match & SymbolMatch.Function) != 0 && (include & IncludeFunctionKind.BuiltInFunctions) != 0)
                    {
                        getFunctionsInScope(contextNode, match, null, IncludeFunctionKind.BuiltInFunctions, list);
                    }

                    // metadata symbols (tables, etc)
                    if (_currentDatabase != null)
                    {
                        var dbMatch = match;

                        if ((include & IncludeFunctionKind.DatabaseFunctions) == 0)
                            dbMatch &= ~SymbolMatch.Function;

                        _currentDatabase.getMembers(dbMatch, list);
                    }

                    if ((match & SymbolMatch.Database) != 0)
                    {
                        _currentCluster.getMembers(match, list);
                    }

                    if ((match & SymbolMatch.Cluster) != 0)
                    {
                        list.addAll(_globals.clusters());
                    }
                    break;

                // aggregate scopes only see aggregate functions
                case Aggregate:
                    if ((match & SymbolMatch.Function) != 0)
                    {
                        getFunctionsInScope(contextNode, match, null, include, list);
                    }
                    break;

                // plug-in scopes only see plug-in functions
                case PlugIn:
                    if ((match & SymbolMatch.Function) != 0)
                    {
                        getFunctionsInScope(contextNode, match, null, include, list);
                    }
                    break;

                case Option:
                    if ((match & SymbolMatch.Option) != 0)
                    {
                        list.addAll(_globals.options());
                    }
                    break;
            }
        }
    }

    private static void getPathMembers(Symbol target, int memberMatch, List<Symbol> result) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        if (target instanceof GroupSymbol g && isPassThrough(g))
        {
            for (Symbol s : g.members())
            {
                getPathMembers(s, memberMatch, result);
            }
        }
        else
        {
            target.getMembers(memberMatch, result);
        }
    }


    private static void getPathMembers(Symbol target, String name, int memberMatch, List<Symbol> result) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        if (target instanceof GroupSymbol g && isPassThrough(g))
        {
            for (Symbol s : g.members())
            {
                getPathMembers(s, name, memberMatch, result);
            }
        }
        else
        {
            target.getMembers(name, memberMatch, result);
        }
    }

    private void getSpecialFunctions(String name, List<Symbol> functions)
    {
        if (_pathScope != null)
        {
            if (_pathScope instanceof GroupSymbol g
                && g.members().size() > 0
                && isPassThrough(g))
            {
                // use info for first symbol in group
                var savePathScope = _pathScope;
                _pathScope = g.members().get(0);
                getSpecialFunctions(name, functions);
                _pathScope = savePathScope;
            }
            else
            {
                // these special methods show up as dot-able methods on their respective types
                switch (_pathScope.kind())
                {
                    case Cluster:
                        if (name == null || Objects.equals(Functions.Database.name(), name)) // PORT: §3.14 string ==
                            functions.add(Functions.Database);
                        break;
                    case Database:
                        if (name == null || Objects.equals(Functions.Database.name(), name)) // PORT: §3.14 string ==
                        {
                            functions.add(Functions.Table);
                            functions.add(Functions.ExternalTable);
                            functions.add(Functions.MaterializedView);
                            functions.add(Functions.EntityGroup);
                            functions.add(Functions.StoredQueryResult);
                            functions.add(Functions.Graph);
                        }
                        break;
                }
            }
        }
    }

    private void getFunctionsInScope(
        SyntaxNode contextNode,
        int match, // PORT: §3.17 SymbolMatch is an int holder (D23)
        String name,
        int include, // PORT: §3.17 IncludeFunctionKind is an int holder (D23)
        List<Symbol> functions)
    {
        var allFunctions = s_symbolListPool.allocateFromPool();
        try
        {
            getFunctionsInScope(
                _scopeKind,
                contextNode,
                name,
                include,
                allFunctions);

            for (Symbol fn : allFunctions)
            {
                if (SymbolMatchExtensions.matches(fn, match)) // PORT: §3.5
                {
                    functions.add(fn);
                }
            }
        }
        finally
        {
            s_symbolListPool.returnToPool(allFunctions);
        }
    }

    private void getFunctionsInScope(
        ScopeKind kind,
        SyntaxNode contextNode,
        String name,
        int include, // PORT: §3.17 IncludeFunctionKind is an int holder (D23)
        List<Symbol> functions)
    {
        switch (kind)
        {
            case Aggregate:
                getAggregateFunctionsInScope(contextNode, name, include, functions);
                break;

            case PlugIn:
                getFunctionsInPlugInScope(contextNode, name, include, functions);
                break;

            case Option:
                break;

            case Normal:
            default:
                getFunctionsInNormalScope(contextNode, name, include, functions);
                break;
        }
    }

    private void getAggregateFunctionsInScope(
        SyntaxNode contextNode,
        String name,
        int include, // PORT: §3.17 IncludeFunctionKind is an int holder (D23)
        List<Symbol> functions)
    {
        if (_pathScope != null)
            return;

        if (name == null)
        {
            if ((include & IncludeFunctionKind.BuiltInFunctions) != 0)
            {
                var context = new BinderAvailabilityContext(contextNode);
                for (FunctionSymbol f : _globals.aggregates()) // PORT: §3.6 hot path: _globals.Aggregates.Where(f => IsAvailable(f, context))
                {
                    if (isAvailable(f, context))
                    {
                        functions.add(f);
                    }
                }
            }

            getFunctionsInNormalScope(contextNode, name, include, functions);
        }
        else
        {
            if ((include & IncludeFunctionKind.BuiltInFunctions) != 0)
            {
                var fn = _globals.getAggregate(name);
                if (fn != null)
                {
                    functions.add(fn);
                }
            }

            if (functions.size() == 0)
            {
                getFunctionsInNormalScope(contextNode, name, include, functions);
            }
        }
    }

    private static class BinderAvailabilityContext extends CustomAvailabilityContext // PORT: §2.2 nested class that does not use the outer instance, static nested class
    {
        public final SyntaxNode _location;

        public BinderAvailabilityContext(SyntaxNode location)
        {
            _location = location;
        }

        @Override
        public SyntaxNode location() { return _location; }
    }

    private static boolean isAvailable(FunctionSymbol function, BinderAvailabilityContext context)
    {
        return function.customAvailability() == null || function.customAvailability().invoke(context); // PORT: §3.8 delegate Invoke → invoke
    }

    private void getFunctionsInPlugInScope(
        SyntaxNode contextNode,
        String name,
        int include, // PORT: §3.17 IncludeFunctionKind is an int holder (D23)
        List<Symbol> functions)
    {
        if (_pathScope != null)
            return;

        if ((include & IncludeFunctionKind.BuiltInFunctions) != 0)
        {
            if (name == null)
            {
                var context = new BinderAvailabilityContext(contextNode);
                for (FunctionSymbol f : _globals.plugIns()) // PORT: §3.6 hot path: _globals.PlugIns.Where(f => IsAvailable(f, context))
                {
                    if (isAvailable(f, context))
                    {
                        functions.add(f);
                    }
                }
            }
            else
            {
                var fn = _globals.getPlugIn(name);
                if (fn != null)
                {
                    functions.add(fn);
                }
            }
        }
    }

    private void getFunctionsInNormalScope(
        SyntaxNode contextNode,
        String name,
        int include, // PORT: §3.17 IncludeFunctionKind is an int holder (D23)
        List<Symbol> functions)
    {
        if (_pathScope != null)
        {
            getSpecialFunctions(name, functions);
        }
        else
        {
            if ((include & IncludeFunctionKind.BuiltInFunctions) != 0)
            {
                var context = new BinderAvailabilityContext(contextNode);

                if (name == null)
                {
                    for (FunctionSymbol f : _globals.functions()) // PORT: §3.6 hot path: _globals.Functions.Where(f => IsAvailable(f, context))
                    {
                        if (isAvailable(f, context))
                        {
                            functions.add(f);
                        }
                    }
                }
                else if (functions.size() == 0)
                {
                    var fn = _globals.getFunction(name);
                    if (fn != null && isAvailable(fn, context))
                    {
                        functions.add(fn);
                    }
                }
            }

            if ((name == null || functions.size() == 0) && (include & IncludeFunctionKind.LocalFunctions) != 0)
            {
                getLocalFunctionsInScope(name, functions);
            }

            if ((name == null || functions.size() == 0) && (include & IncludeFunctionKind.DatabaseFunctions) != 0 && _currentDatabase != null)
            {
                _currentDatabase.getMembers(name, SymbolMatch.Function, functions);
            }
        }
    }

    private void getLocalFunctionsInScope(String name, List<Symbol> functions)
    {
        var locals = s_symbolListPool.allocateFromPool();
        try
        {
            _localScope.getSymbols(name, SymbolMatch.Local | SymbolMatch.Function | SymbolMatch.View, locals);

            for (Symbol local : locals)
            {
                if (local instanceof FunctionSymbol fs)
                {
                    functions.add(fs);
                }
                else if (local instanceof PatternSymbol ps)
                {
                    functions.add(ps);
                }
                else if (local instanceof VariableSymbol vs)
                {
                    var resultType = getResultType(local);
                    if (resultType instanceof FunctionSymbol lfs)
                    {
                        functions.add(lfs);
                    }
                    else if (resultType instanceof PatternSymbol lps)
                    {
                        functions.add(lps);
                    }
                }
            }
        }
        finally
        {
            s_symbolListPool.returnToPool(locals);
        }
    }

    // ===== upstream part: Binder_AsContextBuilder.cs =====
    /// <summary>
    /// A context builder that searches for 'as' operator definitions and adds them to the local scope
    /// </summary>
    @Internal
    public static class AsContextBuilder extends DefaultSyntaxVisitor // PORT: §2.6 internal nested class; §2.2 nested type stays nested
    {
        private final int _position;
        private final Binder _binder;

        @Override
        protected void defaultVisit(SyntaxNode node)
        {
            // visit children
            if (node != null)
            {
                for (int i = 0, n = node.childCount(); i < n; i++)
                {
                    var child = node.getChild(i) instanceof SyntaxNode sn ? sn : null; // PORT: §3.15 as
                    if (child != null)
                    {
                        child.accept(this);
                    }
                }
            }
        }

        public AsContextBuilder(int position, Binder binder)
        {
            _position = position;
            _binder = binder;
        }

        @Override
        public void visitAsOperator(AsOperator node)
        {
            super.visitAsOperator(node);

            var name = node.name().simpleName();
            var type = node.resultType();
            if (!DotNetStrings.isNullOrEmpty(name) && _position > node.end() && type != null) // PORT: §3.14 string.IsNullOrEmpty
            {
                var declaration = new VariableSymbol(node.name().simpleName(), type);
                _binder._localScope.addSymbol(declaration);
            }
        }

        @Override
        public void visitFunctionBody(FunctionBody node)
        {
            // only include as-operators inside function bodies if the position is also within the body
            if (_position > node.textStart() && _position < node.end())
            {
                super.visitFunctionBody(node);
            }
        }
    }

    // ===== upstream part: Binder_ContextBuilder.cs =====
    /// <summary>
    /// The <see cref="ContextBuilder"/> is a <see cref="SyntaxVisitor"/> that puts
    /// a <see cref="Binder"/> into the same state that existed for a given <see cref="SyntaxNode"/> during 
    /// the full semantic analysis.
    /// </summary>
    /// <remarks>
    /// The <see cref="ContextBuilder"/> works by walking the syntax tree upwards from a node at
    /// a given position, recreating the state for the ancestor nodes first and refining that state
    /// on the walk back to down the original node.
    /// </remarks>
    @Internal
    public static class ContextBuilder extends DefaultSyntaxVisitor // PORT: §2.6 internal nested class; §2.2 nested type stays nested
    {
        private final Binder _binder;
        private final int _position;
        private final AsContextBuilder _asBuilder;

        public ContextBuilder(Binder binder, int position)
        {
            _binder = binder;
            _position = position;
            _asBuilder = new AsContextBuilder(position, _binder);
        }

        @Override
        protected void defaultVisit(SyntaxNode node)
        {
            // if you can, ask your parent instead
            for (var parent = node.parent(); parent != null; parent = parent.parent())
            {
                if (parent instanceof SyntaxNode parentNode) // PORT: §3.15 Parent is already a SyntaxNode in Java; the test is always true as upstream
                {
                    parentNode.accept(this);
                    break;  // okay, done now
                }
            }

            // reached the top?  Look for as-operators too
            if (node.parent() == null)
            {
                node.accept(_asBuilder);
            }
        }

        @Override
        public void visitPathExpression(PathExpression node)
        {
            super.visitPathExpression(node);

            // expressions on right-hand side of a dot have path scope
            if (_position >= node.selector().triviaStart())
            {
                _binder._pathScope = node.expression().resultType();
            }
        }

        @Override
        public void visitElementExpression(ElementExpression node)
        {
            super.visitElementExpression(node);

            // expressions within the element selector have path scope?
            if (_position >= node.selector().triviaStart())
            {
                _binder._pathScope = node.expression().resultType();
            }
        }

        @Override
        public void visitParenthesizedExpression(ParenthesizedExpression node)
        {
            super.visitParenthesizedExpression(node);

            // nested expressions should not see outer path scope
            _binder._pathScope = null;
            _binder._implicitArgumentType = null;
        }

        @Override
        public void visitFunctionCallExpression(FunctionCallExpression node)
        {
            super.visitFunctionCallExpression(node);

            // function call arguments do not have path scope or special scope kinds like aggregate
            if (_position > node.name().end())
            {
                _binder._pathScope = null;
                _binder._scopeKind = _binder.getArgumentScope(node, _position, _binder._scopeKind);
                _binder._rowScope = Binder.getArgumentRowScope(node, _position, _binder._rowScope);
                _binder._implicitArgumentType = null;
            }
        }

        @Override
        public void visitPipeExpression(PipeExpression node)
        {
            super.visitPipeExpression(node);

            // operators on right-hand side of pipe have row scope
            if (_position >= node.operator().triviaStart())
            {
                _binder._rowScope = node.expression().resultType() instanceof TableSymbol ts ? ts : null; // PORT: §3.15 as
            }
        }

        @Override
        public void visitEvaluateOperator(EvaluateOperator node)
        {
            super.visitEvaluateOperator(node);

            if (_position >= node.parameters().end())
            {
                _binder._scopeKind = ScopeKind.PlugIn;
            }
        }

        @Override
        public void visitSummarizeOperator(SummarizeOperator node)
        {
            super.visitSummarizeOperator(node);

            if (node.byClause() == null || _position < node.byClause().textStart())
            {
                _binder._scopeKind = ScopeKind.Aggregate;
            }
        }

        @Override
        public void visitMacroExpandOperator(MacroExpandOperator node)
        {
            super.visitMacroExpandOperator(node);

            if (_position >= node.openParen().end())
            {
                // put entity group scope reference symbol into scope...
                // PORT: §3.14 node.ScopeReferenceName?.EntityGroupReferenceName?.ReferencedSymbol
                var scopeReferenceName = node.scopeReferenceName();
                var entityGroupReferenceName = scopeReferenceName != null ? scopeReferenceName.entityGroupReferenceName() : null;
                var referencedScopeSymbol = entityGroupReferenceName != null ? entityGroupReferenceName.referencedSymbol() : null;
                if (referencedScopeSymbol instanceof EntityGroupElementSymbol scopeSymbol)
                {
                    // scope symbol was set on scope reference name
                    _binder._localScope.addSymbol(scopeSymbol);
                }
                else if (node.entityGroup().resultType() instanceof EntityGroupSymbol egSymbol
                    && node.entityGroup() instanceof NameReference entityGroupName)
                {
                    // it is an implicit syntax of macro-expand
                    var implicitScopeSymbol = new EntityGroupElementSymbol(entityGroupName.simpleName(), egSymbol); // PORT: §3.15 C# reuses pattern variable scopeSymbol here; Java pattern scope forbids it
                    _binder._localScope.addSymbol(implicitScopeSymbol);
                }
            }
        }

        @Override
        public void visitNamedParameter(NamedParameter node)
        {
            super.visitNamedParameter(node);

            if (_position >= node.equalToken().end()
                && (node.expression().isMissing() || _position < node.end()))
            {
                _binder._scopeKind = ScopeKind.Normal;
            }
        }

        @Override
        public void visitMakeSeriesOperator(MakeSeriesOperator node)
        {
            super.visitMakeSeriesOperator(node);

            if (_position < node.onClause().textStart() ||
                (node.onClause().isMissing() && node.onClause().end() == node.end() && _position >= node.end()))
            {
                _binder._scopeKind = ScopeKind.Aggregate;
            }
        }

        @Override
        public void visitTopNestedClause(TopNestedClause node)
        {
            super.visitTopNestedClause(node);

            if (node.byKeyword().width() > 0 && _position > node.byKeyword().end())
            {
                _binder._scopeKind = ScopeKind.Aggregate;
            }
        }

        /// <summary>
        /// The node ends in a list or optional element
        /// </summary>
        private static boolean canHoldMore(SyntaxNode node)
        {
            // walk up tree looking for lists
            var lastToken = node.getLastToken(true); // PORT: §3.12 named argument includeZeroWidthTokens
            
            for (var subNode = lastToken.parent();
                subNode != node && subNode.textStart() > node.textStart();
                subNode = subNode.parent())
            {
                for (int i = subNode.childCount() - 1; i >= 0; i--)
                {
                    var child = subNode.getChild(i);
                    // missing optional element could be here
                    if (child == null && subNode.isOptional(i))
                        return true;
                    // lists can always have more
                    if (child instanceof SyntaxList)
                        return true;
                    // see-through zero width elements
                    if (child != null && child.width() > 0)
                        break;
                }
            }

            return false;
        }

        private static boolean isInTriviaAfter(SyntaxNode node, int position)
        {
            return isInTriviaAfter(node.getLastToken(), position);
        }

        private static boolean isInTriviaAfter(SyntaxToken token, int position)
        {
            var nextToken = token.getNextToken();
            return nextToken == null 
                || position < nextToken.textStart() 
                || nextToken.kind() == SyntaxKind.EndOfTextToken;
        }

        @Override
        public void visitFunctionDeclaration(FunctionDeclaration node)
        {
            super.visitFunctionDeclaration(node);

            if (_position >= node.body().textStart() && 
                (_position < node.body().end()
                 || node.body().closeBrace().isMissing() && _position <= node.body().end() + 1))
            {
                _binder.addDeclarationsToLocalScope(node.parameters().parameters());
            }
        }

        @Override
        public void visitFunctionBody(FunctionBody node)
        {
            super.visitFunctionBody(node);

            if (node.closeBrace().isMissing() || _position < node.closeBrace().textStart())
            {
                addStatementDeclarationsToScope(node.statements());
            }
        }

        @Override
        public void visitQueryBlock(QueryBlock node)
        {
            super.visitQueryBlock(node);

            applyDirectives(node.directives());
            addStatementDeclarationsToScope(node.statements());
        }

        @Override
        public void visitCommandBlock(CommandBlock node)
        {
            super.visitCommandBlock(node);

            applyDirectives(node.directives());

            if (node.statements().size() > 0
                && node.statements().get(0).separator() != null
                && _position > node.statements().get(0).end())
            {
                var command = node.statements().get(0).element().getFirstDescendant(Command.class); // PORT: §3.10
                if (command != null)
                {
                    var commandResults = new VariableSymbol("$command_results", getResultTypeOrError(command));
                    _binder._localScope.addSymbol(commandResults);
                }
            }
            else
            {
                addStatementDeclarationsToScope(node.statements());
            }
        }

        private void applyDirectives(SyntaxList1<Directive> directives)
        {
            for (Directive directive : directives)
            {
                if (_position >= directive.end())
                {
                    _binder.applyDirective(directive); // PORT: §3.12 diagnostics = null
                }
            }
        }

        private void addStatementDeclarationsToScope(SyntaxList1<SeparatedElement1<Statement>> statementList)
        {
            for (int i = 0, n = statementList.size(); i < n; i++)
            {
                var se = statementList.get(i);

                // don't include declarations not fully defined before position
                if (_position < se.end() ||
                    isInTriviaAfter(se.element(), _position)
                    && (isIncomplete(se.element()) || canHoldMore(se.element())))
                    break;

                if (se.element() instanceof LetStatement ls)
                {
                    _binder.addLetDeclarationToScope(_binder._localScope, ls); // PORT: §3.12 diagnostics = null
                }
                else if (se.element() instanceof QueryParametersStatement qps)
                {
                    _binder.addDeclarationsToLocalScope(qps.parameters());
                }
                else if (se.element() instanceof PatternStatement ps)
                {
                    _binder._localScope.addSymbol(getReferencedSymbol(ps.name()));
                }
            }
        }

        /// <summary>
        /// The node has a missing element as its last child.
        /// </summary>
        private static boolean isIncomplete(SyntaxNode node)
        {
            var last = node.getLastToken(true); // PORT: §3.12 named argument includeZeroWidthTokens
            return last != null ? last.isMissing() : false; // PORT: §3.14 ?. ??
        }

        @Override
        public void visitPatternDeclaration(PatternDeclaration node)
        {
            super.visitPatternDeclaration(node);

            if (_position > node.patterns().textStart())
            {
                _binder.addDeclarationsToLocalScopeOfParameters(node.parameters()); // PORT: §2.5

                if (node.pathParameter() != null)
                {
                    _binder.addDeclarationToLocalScope(node.pathParameter().parameter().name());
                }
            }
        }

        @Override
        public void visitJoinOperator(JoinOperator node)
        {
            super.visitJoinOperator(node);

            if (node.conditionClause() == null || _position < node.conditionClause().textStart())
            {
                // no row scope
                _binder._rowScope = null;
            }
            else if (node.conditionClause() != null && _position >= node.conditionClause().textStart())
            {
                _binder._rightRowScope = node.expression().resultType() instanceof TableSymbol ts ? ts : null; // PORT: §3.15 as
            }
        }

        @Override
        public void visitLookupOperator(LookupOperator node)
        {
            super.visitLookupOperator(node);

            if (node.lookupClause().isMissing() || _position < node.lookupClause().textStart())
            {
                // no row scope
                _binder._rowScope = null;
            }
            else if (_position >= node.lookupClause().textStart())
            {
                // this.position >= node.LookupClause.TextStart
                _binder._rightRowScope = node.expression().resultType() instanceof TableSymbol ts ? ts : null; // PORT: §3.15 as
            }
        }

        @Override
        public void visitUnionOperator(UnionOperator node)
        {
            super.visitUnionOperator(node);

            // union operator expressions are all tables.. they don't refer to row scope columns
            _binder._rowScope = null;
        }

        @Override
        public void visitFindOperator(FindOperator node)
        {
            super.visitFindOperator(node);

            if (node.inClause() == null || _position >= node.inClause().end())
            {
                _binder._rowScope = _binder.getFindColumnsTable(node);
            }
        }

        @Override
        public void visitSearchOperator(SearchOperator node)
        {
            super.visitSearchOperator(node);

            if (_position >= node.condition().textStart())
            {
                _binder._rowScope = _binder.getSearchColumnsTable(node);
            }

            if (node.inClause() != null && _position >= node.inClause().textStart())
            {
                // in clause arguments are all tables, no columns visible
                _binder._rowScope = null;
            }
        }

        @Override
        public void visitMvApplyOperator(MvApplyOperator node)
        {
            super.visitMvApplyOperator(node);

            if (!node.onKeyword().isMissing() && _position >= node.onKeyword().textStart())
            {
                var info = new NodeBinder(_binder).visitMvApplyOperator(node);
                _binder._rowScope = info != null && info.resultType() instanceof TableSymbol ts ? ts : null; // PORT: §3.14 ?. §3.15 as
            }
        }

        @Override
        public void visitInvokeOperator(InvokeOperator node)
        {
            super.visitInvokeOperator(node);

            if (node.function() != null && !node.function().isMissing() && _position > node.function().textStart())
            {
                _binder._rowScope = null;
            }
        }

        @Override
        public void visitPartitionOperator(PartitionOperator node)
        {
            super.visitPartitionOperator(node);

            if (_position >= node.operand().textStart())
            {
                var column = node.byExpression().referencedSymbol() instanceof ColumnSymbol cs ? cs : null; // PORT: §3.15 as
                if (column != null)
                {
                    _binder._localScope.addSymbol(column);
                }
            }
        }

        @Override
        public void visitScanOperator(ScanOperator node)
        {
            super.visitScanOperator(node);

            if (_position > node.withKeyword().textStart() && node.declareClause() != null)
            {
                _binder.addDeclarationsToLocalScope(node.declareClause().declarations());
                _binder.addStepDeclarationsToLocalScope(node);
            }
        }

        @Override
        public void visitInlineExternalTableExpression(InlineExternalTableExpression node)
        {
            super.visitInlineExternalTableExpression(node);

            if (node.pathFormat() != null && _position > node.pathFormat().textStart())
            {
                // Handle PathFormat that needs to get list of declared partition columns in scope.
                if (node.partitionClause() != null)
                {
                    for (var partitionColumn : node.partitionClause().partitionColumns())
                    {
                        _binder.addDeclarationToLocalScope(partitionColumn.element().name());
                    }
                }
            }
            else
            {
                //Handle Partition By that should get declared attributes in scope
                _binder.addDeclarationsToLocalScopeOfParameters(node.schema() != null ? node.schema().columns() : null); // PORT: §2.5, §3.14 ?.
            }
        }

        @Override
        public void visitPartialCommand(PartialCommand node)
        {
            super.visitPartialCommand(node);
            setCommandContext(node);
        }

        @Override
        public void visitCustomCommand(CustomCommand node)
        {
            super.visitCustomCommand(node);
            setCommandContext(node);
        }

        private void setCommandContext(SyntaxNode node)
        {
            // PORT: §3.6 hot path: node.GetDescendants<NameReference>(nr => nr.ReferencedSymbol is TableSymbol).Where(nr => nr.End <= _position).LastOrDefault()
            NameReference nearestTableRef = null;
            for (NameReference nr : node.getDescendants(NameReference.class, n -> n.referencedSymbol() instanceof TableSymbol)) // PORT: §3.10
            {
                if (nr.end() <= _position)
                {
                    nearestTableRef = nr;
                }
            }

            if (nearestTableRef != null)
            {
                _binder._rowScope = (TableSymbol)nearestTableRef.referencedSymbol();
            }
        }

        @Override
        public void visitToScalarExpression(ToScalarExpression node)
        {
            super.visitToScalarExpression(node);
            _binder._rowScope = null;
        }

        @Override
        public void visitToTableExpression(ToTableExpression node)
        {
            super.visitToTableExpression(node);
            _binder._rowScope = null;
        }

        @Override
        public void visitSetOptionStatement(SetOptionStatement node)
        {
            super.visitSetOptionStatement(node);

            if (_position >= node.setKeyword().end() && (node.valueClause() == null || _position <= node.valueClause().textStart()))
            {
                _binder._scopeKind = ScopeKind.Option;
            }
        }

        @Override
        public void visitMaterializedViewCombineExpression(MaterializedViewCombineExpression node)
        {
            super.visitMaterializedViewCombineExpression(node);

            if (_position > node.aggregationsClause().openParen().textStart())
            {
                _binder._rowScope = getResultType(node.deltaClause().expression()) instanceof TableSymbol ts ? ts : null; // PORT: §3.15 as
            }
        }

        @Override
        public void visitMakeGraphTableAndKeyClause(MakeGraphTableAndKeyClause node)
        {
            super.visitMakeGraphTableAndKeyClause(node);

            if (_position >= node.onKeyword().textStart())
            {
                _binder._rowScope = node.table().resultType() instanceof TableSymbol ts ? ts : null; // PORT: §3.15 as
            }
        }

        @Override
        public void visitGraphMatchOperator(GraphMatchOperator node)
        {
            super.visitGraphMatchOperator(node);

            if ((node.whereClause() != null && _position >= node.whereClause().textStart())
                || (node.projectClause() != null && _position >= node.projectClause().textStart()))
            {
                _binder._localScope = new LocalScope(_binder._localScope);
                _binder.addGraphMatchPatternDeclarationsToLocalScope(node.patterns());
            }
        }

        @Override
        public void visitGraphShortestPathsOperator(GraphShortestPathsOperator node)
        {
            super.visitGraphShortestPathsOperator(node);

            if ((node.whereClause() != null && _position >= node.whereClause().textStart())
                || (node.projectClause() != null && _position >= node.projectClause().textStart()))
            {
                _binder._localScope = new LocalScope(_binder._localScope);
                _binder.addGraphMatchPatternDeclarationsToLocalScope(node.patterns());
            }
        }
    }

    // ===== upstream part: Binder_FunctionCalls.cs =====
    /// <summary>
    /// Binds a function call or pattern invocation expression
    /// </summary>
    private SemanticInfo bindFunctionCallOrPattern(FunctionCallExpression functionCall)
    {
        // the result type of the name should be bound to the function/pattern
        var symbol = getResultTypeOrError(functionCall.name());

        if (symbol instanceof FunctionSymbol fn)
        {
            return bindFunctionCall(functionCall, fn);
        }
        else if (symbol instanceof PatternSymbol ps)
        {
            return bindPattern(functionCall, ps);
        }
        else if (!symbol.isError())
        {
            // the name was not a known function or pattern, but we decided to give it a result type, so let's use it
            return functionCall.name().getSemanticInfo();
        }
        else if (_isFuzzy)
        {
            return new SemanticInfo(
                new TableSymbol().withIsOpen(true),
                DiagnosticFacts.getFuzzyFunctionNotDefined(functionCall.name().simpleName()).withLocation(functionCall));
        }
        else
        {
            return ErrorInfo;
        }
    }

    /// <summary>
    /// Binds a function call
    /// </summary>
    private SemanticInfo bindFunctionCall(
        FunctionCallExpression functionCall, FunctionSymbol fn)
    {
        var arguments = s_expressionListPool.allocateFromPool();
        var argumentTypes = s_typeListPool.allocateFromPool();

        try
        {
            getArgumentsAndTypes(functionCall, arguments, argumentTypes);
            return bindFunctionCall(functionCall, fn, arguments, argumentTypes);
        }
        finally
        {
            s_expressionListPool.returnToPool(arguments);
            s_typeListPool.returnToPool(argumentTypes);
        }
    }

    /// <summary>
    /// Binds a function call
    /// </summary>
    private SemanticInfo bindFunctionCall(
        FunctionCallExpression functionCall,
        FunctionSymbol fn,
        List<Expression> arguments,
        List<TypeSymbol> argumentTypes)
    {
        var diagnostics = s_diagnosticListPool.allocateFromPool();
        var matchingSignatures = s_signatureListPool.allocateFromPool();

        try
        {
            getBestMatchingSignatures(fn.signatures(), arguments, argumentTypes, matchingSignatures);

            if (matchingSignatures.size() == 1)
            {
                checkSignature(matchingSignatures.get(0), arguments, argumentTypes, functionCall.name(), diagnostics);
                var funResult = getFunctionCallResult(matchingSignatures.get(0), arguments, argumentTypes, functionCall.name(), diagnostics);
                var resultType = funResult.type();

                // check for possible better dynamic result
                if (funResult.type() == ScalarTypes.Dynamic
                    && hasDynamicPrimitives(argumentTypes))
                {
                    var unwrappedArgumentTypes = s_typeListPool.allocateFromPool();
                    try
                    {
                        getUnwrappedDynamicPrimitives(argumentTypes, unwrappedArgumentTypes);
                        var unwrappedResultType = bindFunctionCall(functionCall, fn, arguments, unwrappedArgumentTypes).resultType();
                        if (unwrappedResultType instanceof ScalarSymbol
                            && !(unwrappedResultType instanceof DynamicSymbol)
                            && unwrappedResultType != ScalarTypes.Unknown)
                        {
                            resultType = ScalarTypes.getDynamic(unwrappedResultType);
                        }
                    }
                    finally
                    {
                        s_typeListPool.returnToPool(unwrappedArgumentTypes);
                    }
                }

                return new SemanticInfo(
                    matchingSignatures.get(0),
                    resultType,
                    diagnostics,
                    fn.isConstantFoldable() && allAreConstant(arguments), // PORT: §3.12 isConstant:
                    funResult.info()); // PORT: §3.12 calledFunctionInfo:
            }
            else
            {
                if (arguments.size() == 0 && fn.minArgumentCount() > 0)
                {
                    diagnostics.add(DiagnosticFacts.getFunctionExpectsArgumentCountRange(fn.name(), fn.minArgumentCount(), fn.maxArgumentCount()).withLocation(functionCall.name()));
                }
                else if (!argumentsHaveErrorsOrUnknown(argumentTypes))
                {
                    // var types = arguments.Select(e => GetResultTypeOrError(e)).ToList();
                    List<TypeSymbol> types = new ArrayList<>(); // PORT: §3.6
                    for (Expression e : arguments)
                    {
                        types.add(getResultTypeOrError(e));
                    }
                    diagnostics.add(DiagnosticFacts.getFunctionNotDefinedWithMatchingParameters(functionCall.name().simpleName(), types).withLocation(functionCall.name()));
                }

                var returnType = getCommonReturnType(matchingSignatures, arguments, argumentTypes, functionCall.name());

                return new SemanticInfo(fn, returnType, diagnostics, fn.isConstantFoldable() && allAreConstant(arguments), null); // PORT: §3.12 isConstant:, calledFunctionInfo = null
            }
        }
        finally
        {
            s_diagnosticListPool.returnToPool(diagnostics);
            s_signatureListPool.returnToPool(matchingSignatures);
        }
    }

    /// <summary>
    /// Binds a pattern
    /// </summary>
    private SemanticInfo bindPattern(FunctionCallExpression functionCall, PatternSymbol pattern)
    {
        var diagnostics = s_diagnosticListPool.allocateFromPool();
        var matchingSignatures = s_patternListPool.allocateFromPool();
        var arguments = s_expressionListPool.allocateFromPool();
        try
        {
            // check argument count
            if (pattern.parameters().size() != functionCall.argumentList().expressions().size())
            {
                diagnostics.add(DiagnosticFacts.getArgumentCountExpected(pattern.parameters().size()).withLocation(functionCall.name()));
            }

            // check actual arguments
            for (int i = 0, n = functionCall.argumentList().expressions().size(); i < n; i++)
            {
                var argument = functionCall.argumentList().expressions().get(i).element();
                arguments.add(argument);

                if (i < pattern.parameters().size())
                {
                    var type = pattern.parameters().get(i).declaredTypes().get(0);
                    if (checkIsExactType(argument, type, diagnostics))
                    {
                        checkIsLiteral(argument, diagnostics);
                    }
                }
            }

            getMatchingPatterns(pattern.signatures(), arguments, matchingSignatures);

            if (matchingSignatures.size() == 0)
            {
                diagnostics.add(DiagnosticFacts.getNoPatternMatchesArguments().withLocation(functionCall.name()));
                return new SemanticInfo(pattern, ErrorSymbol.Instance, diagnostics);
            }
            else
            {
                var result = getPatternReturnType(matchingSignatures);
                return new SemanticInfo(pattern, result, diagnostics);
            }
        }
        finally
        {
            s_diagnosticListPool.returnToPool(diagnostics);
            s_patternListPool.returnToPool(matchingSignatures);
            s_expressionListPool.returnToPool(arguments);
        }
    }

    /// <summary>
    /// Gets the set of pattern signatures that match the arguments.
    /// </summary>
    private void getMatchingPatterns(List<PatternSignature> signatures, List<Expression> arguments, List<PatternSignature> matchingSignatures)
    {
        // look for exact match
        for (PatternSignature sig : signatures)
        {
            if (patternMatches(sig, arguments, true)) // PORT: §3.12 exact:
            {
                matchingSignatures.add(sig);
            }
        }

        // if no exact matches, look for partial matches
        if (matchingSignatures.size() == 0)
        {
            for (PatternSignature sig : signatures)
            {
                if (patternMatches(sig, arguments, false)) // PORT: §3.12 exact:
                {
                    matchingSignatures.add(sig);
                }
            }
        }
    }

    /// <summary>
    /// Determines if the pattern signature matches the arguments.
    /// </summary>
    private boolean patternMatches(PatternSignature signature, List<Expression> arguments, boolean exact)
    {
        if (exact && signature.argumentValues().size() != arguments.size())
            return false;

        for (int i = 0; i < arguments.size(); i++)
        {
            if (i < signature.argumentValues().size())
            {
                String matchValue = signature.argumentValues().get(i);
                String argValue = DotNet.str(arguments.get(i).literalValue()); // PORT: §3.14 ?.ToString() ?? ""
                if (!Objects.equals(matchValue, argValue)) // PORT: §3.14
                    return false;
            }
        }

        return true;
    }

    /// <summary>
    /// Gets the return type of the set of pattern signatures.
    /// The return type is either the type of the signature body if there is no path,
    /// or a database symbol containing variables named for each path.
    /// </summary>
    private TypeSymbol getPatternReturnType(List<PatternSignature> signatures)
    {
        if (signatures.size() == 1 && signatures.get(0).pathValue() == null)
            return signatures.get(0).signature().getReturnType(_globals);

        var paths = s_symbolListPool.allocateFromPool();
        var types = s_typeListPool.allocateFromPool();
        try
        {
            for (PatternSignature sig : signatures)
            {
                var type = sig.signature().getReturnType(_globals);

                if (sig.pathValue() == null)
                {
                    if (!types.contains(type))
                    {
                        types.add(type);
                    }
                }
                else
                {
                    paths.add(new VariableSymbol(sig.pathValue(), type));
                }
            }

            if (paths.size() > 0)
            {
                if (types.size() > 0)
                {
                    // this should not happen, but in case it does
                    return new GroupSymbol(Linq.concat(paths, types)); // PORT: §3.6
                }
                else
                {
                    return new GroupSymbol(paths);
                }
            }
            else if (types.size() == 1)
            {
                return types.get(0);
            }
            else
            {
                return new GroupSymbol(types);
            }
        }
        finally
        {
            s_symbolListPool.returnToPool(paths);
            s_typeListPool.returnToPool(types);
        }
    }

    /// <summary>
    /// Extracts the argument expressions and their result types into two separate lists.
    /// Handles the special case of the implicit invoke operator argument
    /// </summary>
    private void getArgumentsAndTypes(
        FunctionCallExpression functionCall,
        List<Expression> arguments,
        List<TypeSymbol> argumentTypes)
    {
        var expressions = functionCall.argumentList().expressions();

        for (int i = 0, n = expressions.size(); i < n; i++)
        {
            var arg = expressions.get(i).element();
            arguments.add(arg);
            argumentTypes.add(getResultTypeOrError(arg));
        }

        if (isInvokeOperatorFunctionCall(functionCall))
        {
            // add argument to represent the implicit value
            Out<Expression> invokeArgument = new Out<>(); // PORT: §3.3 out var
            if (tryGetInvokeOperatorExpression(functionCall, invokeArgument))
            {
                arguments.add(0, invokeArgument.value);
            }
            else
            {
                // no preceding expression was found... use the name node instead.
                arguments.add(0, functionCall.name());
            }

            argumentTypes.add(0, _implicitArgumentType != null ? _implicitArgumentType : TableSymbol.Empty); // PORT: §3.14 ??
        }
    }

    private static boolean isInvokeOperatorFunctionCall(FunctionCallExpression functionCall)
    {
        // ignore dotted path that is part of function call
        Expression x = functionCall;
        while (x.parent() instanceof PathExpression p
            && p.selector() == x)
        {
            x = p.expression();
        }

        return x.parent() instanceof InvokeOperator io
            && io.function() == x;
    }

    private static boolean tryGetInvokeOperatorExpression(FunctionCallExpression functionCall, Out<Expression> invokeArgument) // PORT: §3.3 out
    {
        // ignore dotted path that is part of function call
        Expression x = functionCall;
        while (x.parent() instanceof PathExpression p
            && p.selector() == x)
        {
            x = p.expression();
        }

        // check for parent pipe expression to find implicit argument
        if (x.parent() instanceof InvokeOperator invokeOp
            && invokeOp.function() == x
            && invokeOp.parent() instanceof PipeExpression invokeOpPipe
            && invokeOpPipe.operator() == invokeOp)
        {
            invokeArgument.value = invokeOpPipe.expression();
            return true;
        }
        else
        {
            invokeArgument.value = null;
            return false;
        }
    }

    /// <summary>
    /// Gets the parameters that correspond to the arguments.
    /// </summary>
    private void getArgumentParameters(
        FunctionCallExpression functionCall,
        List<Parameter> parameters)
    {
        if (functionCall.referencedSignature() != null) // PORT: §3.15 'is Signature' on a Signature-typed property is a null test
        {
            Signature signature = functionCall.referencedSignature();
            var arguments = s_expressionListPool.allocateFromPool();
            var argumentTypes = s_typeListPool.allocateFromPool();

            try
            {
                getArgumentsAndTypes(functionCall, arguments, argumentTypes);
                signature.getArgumentParameters(arguments, parameters);
            }
            finally
            {
                s_expressionListPool.returnToPool(arguments);
                s_typeListPool.returnToPool(argumentTypes);
            }
        }
    }

    /// <summary>
    /// Gets the result information for the function call or operator invocation when invoked with the specified arguments.
    /// </summary>
    private FunctionCallResult getFunctionCallResult(
        Signature signature,
        List<Expression> arguments,
        List<TypeSymbol> argumentTypes,
        SyntaxElement location,
        List<Diagnostic> diagnostics)
    {
        if (arguments == null)
            throw new NullPointerException("arguments"); // PORT: §3.16 ArgumentNullException

        if (argumentTypes == null)
            throw new NullPointerException("argumentTypes"); // PORT: §3.16 ArgumentNullException

        var argumentParameters = s_parameterListPool.allocateFromPool();
        try
        {
            signature.getArgumentParameters(arguments, argumentParameters);
            return getFunctionCallResult(signature, arguments, argumentTypes, argumentParameters, location, diagnostics);
        }
        finally
        {
            s_parameterListPool.returnToPool(argumentParameters);
        }
    }

    private FunctionCallResult getFunctionCallResult(Signature signature, List<Expression> arguments, List<TypeSymbol> argumentTypes, SyntaxElement location) // PORT: §3.12 diagnostics = null
    {
        return getFunctionCallResult(signature, arguments, argumentTypes, location, null);
    }

    /// <summary>
    /// Gets the result information of the function call or operator invocation when invoked with the specified arguments.
    /// </summary>
    private FunctionCallResult getFunctionCallResult(
        Signature signature,
        List<Expression> arguments,
        List<TypeSymbol> argumentTypes,
        List<Parameter> argumentParameters,
        SyntaxElement location,
        List<Diagnostic> diagnostics)
    {
        if (arguments == null)
            throw new NullPointerException("arguments"); // PORT: §3.16 ArgumentNullException

        if (argumentTypes == null)
            throw new NullPointerException("argumentTypes"); // PORT: §3.16 ArgumentNullException

        if (argumentParameters == null)
            throw new NullPointerException("argumentParameters"); // PORT: §3.16 ArgumentNullException

        int iArg; // PORT: §3.12 C# switch sections share one scope; declared once
        switch (signature.returnKind())
        {
            case Declared:
                return FunctionCallResult.of(signature.declaredReturnType()); // PORT: §3.8 implicit conversion

            case Computed:
                return this.getComputedFunctionCallResult(signature, arguments, argumentTypes);

            case Parameter0:
                iArg = argumentParameters.indexOf(signature.parameters().get(0));
                return FunctionCallResult.of(iArg >= 0 && iArg < argumentTypes.size() ? argumentTypes.get(iArg) : ErrorSymbol.Instance); // PORT: §3.8

            case Parameter0Array:
                iArg = argumentParameters.indexOf(signature.parameters().get(0));
                return FunctionCallResult.of(iArg >= 0 && iArg < argumentTypes.size() ? (TypeSymbol)ScalarTypes.getDynamicArray(argumentTypes.get(iArg)) : ErrorSymbol.Instance); // PORT: §3.8

            case Parameter1:
                iArg = argumentParameters.indexOf(signature.parameters().get(1));
                return FunctionCallResult.of(iArg >= 0 && iArg < argumentTypes.size() ? argumentTypes.get(iArg) : ErrorSymbol.Instance); // PORT: §3.8

            case Parameter2:
                iArg = argumentParameters.indexOf(signature.parameters().get(2));
                return FunctionCallResult.of(iArg >= 0 && iArg < argumentTypes.size() ? argumentTypes.get(iArg) : ErrorSymbol.Instance); // PORT: §3.8

            case ParameterN:
                iArg = argumentParameters.indexOf(signature.parameters().get(signature.parameters().size() - 1));
                return FunctionCallResult.of(iArg >= 0 && iArg < argumentTypes.size() ? argumentTypes.get(iArg) : ErrorSymbol.Instance); // PORT: §3.8

            case Parameter0Literal:
                iArg = argumentParameters.indexOf(signature.parameters().get(0));
                return FunctionCallResult.of(iArg >= 0 && iArg < arguments.size() ? getTypeOfType(arguments.get(iArg)) : ErrorSymbol.Instance); // PORT: §3.8

            case Parameter1Literal:
                iArg = argumentParameters.indexOf(signature.parameters().get(1));
                return FunctionCallResult.of(iArg >= 0 && iArg < arguments.size() ? getTypeOfType(arguments.get(iArg)) : ErrorSymbol.Instance); // PORT: §3.8

            case ParameterNLiteral:
                iArg = argumentParameters.indexOf(signature.parameters().get(signature.parameters().size() - 1));
                return FunctionCallResult.of(iArg >= 0 && iArg < arguments.size() ? getTypeOfType(arguments.get(iArg)) : ErrorSymbol.Instance); // PORT: §3.8

            case Parameter0Promoted:
                iArg = argumentParameters.indexOf(signature.parameters().get(0));
                return FunctionCallResult.of(iArg >= 0 && iArg < argumentTypes.size() ? TypeFacts.promoteToLong(argumentTypes.get(iArg)) : ErrorSymbol.Instance); // PORT: §3.8

            case Common:
            {
                TypeSymbol commonType = TypeFacts.getCommonArgumentType(argumentParameters, argumentTypes);
                return FunctionCallResult.of(commonType != null ? commonType : ErrorSymbol.Instance); // PORT: §3.8, §3.14 ??
            }

            case CommonNonDynamic:
            {
                TypeSymbol commonType = TypeFacts.getCommonArgumentType(argumentParameters, argumentTypes, null, true); // PORT: §3.12 defaultType = null, ignoreDynamic: true
                return FunctionCallResult.of(commonType != null ? commonType : ErrorSymbol.Instance); // PORT: §3.8, §3.14 ??
            }

            case Widest:
            {
                TypeSymbol widestType = TypeFacts.promoteToLong(TypeFacts.getWidestScalarType(argumentTypes)); // PORT: §3.5 PromoteToLong extension
                return FunctionCallResult.of(widestType != null ? widestType : ErrorSymbol.Instance); // PORT: §3.8, §3.14 ??
            }

            case Parameter0Cluster:
            {
                iArg = argumentParameters.indexOf(signature.parameters().get(0));
                Out<String> clusterName = new Out<>(); // PORT: §3.3 out var
                if (iArg >= 0 && iArg < arguments.size()
                    && tryGetLiteralStringValue(arguments.get(iArg), clusterName))
                {
                    return FunctionCallResult.of(getClusterFunctionResult(clusterName.value, arguments.get(iArg), diagnostics)); // PORT: §3.8
                }
                else
                {
                    return FunctionCallResult.of(new ClusterSymbol("", null, true)); // PORT: §3.8, isOpen: true
                }
            }

            case Parameter0Database:
            {
                iArg = argumentParameters.indexOf(signature.parameters().get(0));
                Out<String> databaseName = new Out<>(); // PORT: §3.3 out var
                if (iArg >= 0 && iArg < arguments.size()
                    && tryGetLiteralStringValue(arguments.get(iArg), databaseName))
                {
                    return FunctionCallResult.of(getDatabaseFunctionResult(databaseName.value, arguments.get(iArg), diagnostics)); // PORT: §3.8
                }
                else if (arguments.size() == 0)
                {
                    // database() refers to current database
                    return FunctionCallResult.of(_currentDatabase); // PORT: §3.8
                }
                else
                {
                    return FunctionCallResult.of(new DatabaseSymbol("", null, true)); // PORT: §3.8, isOpen: true
                }
            }

            case Parameter0Table:
            {
                iArg = argumentParameters.indexOf(signature.parameters().get(0));
                Out<String> tableName = new Out<>(); // PORT: §3.3 out var
                if (iArg >= 0 && iArg < arguments.size()
                    && tryGetLiteralStringValue(arguments.get(iArg), tableName))
                {
                    return FunctionCallResult.of(getTableFunctionResult(tableName.value, arguments.get(iArg), diagnostics)); // PORT: §3.8
                }
                else
                {
                    return FunctionCallResult.of(TableSymbol.Empty.withIsOpen(true)); // PORT: §3.8
                }
            }

            case Parameter0ExternalTable:
            {
                iArg = argumentParameters.indexOf(signature.parameters().get(0));
                Out<String> externalTableName = new Out<>(); // PORT: §3.3 out var
                if (iArg >= 0 && iArg < arguments.size()
                    && tryGetLiteralStringValue(arguments.get(iArg), externalTableName))
                {
                    return FunctionCallResult.of(getExternalTableFunctionResult(externalTableName.value, arguments.get(iArg), diagnostics)); // PORT: §3.8
                }
                else
                {
                    return FunctionCallResult.of(TableSymbol.Empty.withIsOpen(true)); // PORT: §3.8
                }
            }

            case Parameter0MaterializedView:
            {
                iArg = argumentParameters.indexOf(signature.parameters().get(0));
                Out<String> materializedViewName = new Out<>(); // PORT: §3.3 out var
                if (iArg >= 0 && iArg < arguments.size()
                    && tryGetLiteralStringValue(arguments.get(iArg), materializedViewName))
                {
                    return FunctionCallResult.of(getMaterializedViewFunctionResult(materializedViewName.value, arguments.get(iArg), diagnostics)); // PORT: §3.8
                }
                else
                {
                    return FunctionCallResult.of(TableSymbol.Empty.withIsOpen(true)); // PORT: §3.8
                }
            }

            case Parameter0EntityGroup:
            {
                iArg = argumentParameters.indexOf(signature.parameters().get(0));
                Out<String> entityGroupName = new Out<>(); // PORT: §3.3 out var
                if (iArg >= 0 && iArg < arguments.size()
                    && tryGetLiteralStringValue(arguments.get(iArg), entityGroupName))
                {
                    return getEntityGroupFunctionResult(entityGroupName.value, arguments.get(iArg), diagnostics);
                }
                else
                {
                    return FunctionCallResult.of(new EntityGroupSymbol()); // PORT: §3.8
                }
            }

            case Parameter0StoredQueryResult:
            {
                iArg = argumentParameters.indexOf(signature.parameters().get(0));
                Out<String> sqrName = new Out<>(); // PORT: §3.3 out var
                if (iArg >= 0 && iArg < arguments.size()
                    && tryGetLiteralStringValue(arguments.get(iArg), sqrName))
                {
                    return FunctionCallResult.of(getStoredQueryResultFunctionResult(sqrName.value, arguments.get(iArg), diagnostics)); // PORT: §3.8
                }
                else
                {
                    return FunctionCallResult.of(StoredQueryResultSymbol.Empty); // PORT: §3.8
                }
            }

            case Parameter0Graph:
            {
                iArg = argumentParameters.indexOf(signature.parameters().get(0));
                Out<String> graphModelName = new Out<>(); // PORT: §3.3 out var
                if (iArg >= 0 && iArg < arguments.size()
                    && tryGetLiteralStringValue(arguments.get(iArg), graphModelName))
                {
                    iArg = argumentParameters.indexOf(signature.parameters().get(1));
                    if (iArg >= 0 && iArg < arguments.size())
                    {
                        Out<String> snapshotName = new Out<>(); // PORT: §3.3 out var
                        Out<Boolean> isVolatile = new Out<>(); // PORT: §3.3 out var
                        if (tryGetLiteralStringValue(arguments.get(iArg), snapshotName))
                        {
                            return FunctionCallResult.of(getGraphFunctionResult(graphModelName.value, snapshotName.value, null, location, diagnostics)); // PORT: §3.8
                        }
                        else if (tryGetLiteralValue(Boolean.class, arguments.get(iArg), isVolatile)) // PORT: §3.10 TryGetLiteralValue<bool>
                        {
                            return FunctionCallResult.of(getGraphFunctionResult(graphModelName.value, null, isVolatile.value, location, diagnostics)); // PORT: §3.8
                        }
                    }
                    else
                    {
                        return FunctionCallResult.of(getGraphFunctionResult(graphModelName.value, null, null, location, diagnostics)); // PORT: §3.8
                    }
                }
                return FunctionCallResult.of(GraphSymbol.Empty); // PORT: §3.8
            }

            case Custom:
            {
                var context = new BinderCallContext(this, location instanceof SyntaxNode sn ? sn : null, arguments, argumentTypes, argumentParameters, signature); // PORT: §3.15 'location as SyntaxNode'
                TypeSymbol customType = signature.customReturnType().invoke(context); // PORT: §3.8 delegate invoke
                return FunctionCallResult.of(customType != null ? customType : ErrorSymbol.Instance); // PORT: §3.8, §3.14 ??
            }

            default:
                throw new UnsupportedOperationException(); // PORT: §3.16 NotImplementedException
        }
    }

    private FunctionCallResult getFunctionCallResult(Signature signature, List<Expression> arguments, List<TypeSymbol> argumentTypes, List<Parameter> argumentParameters, SyntaxElement location) // PORT: §3.12 diagnostics = null
    {
        return getFunctionCallResult(signature, arguments, argumentTypes, argumentParameters, location, null);
    }

    private static final class BinderCallContext extends CustomReturnTypeContext
    {
        private final Binder _binder;
        private final SyntaxNode _location;
        private final List<Expression> _arguments;
        private final List<TypeSymbol> _argumentTypes;
        private final List<Parameter> _argumentParameters;
        private final Signature _signature;

        public BinderCallContext(
            Binder binder,
            SyntaxNode location,
            List<Expression> arguments,
            List<TypeSymbol> argumentTypes,
            List<Parameter> argumentParameters,
            Signature signature)
        {
            _binder = binder;
            _location = location;
            _arguments = arguments;
            _argumentTypes = argumentTypes;
            _argumentParameters = argumentParameters;
            _signature = signature;
        }

        @Override public SyntaxNode location() { return _location; }
        @Override public List<Expression> arguments() { return _arguments; }
        @Override public List<TypeSymbol> argumentTypes() { return _argumentTypes; }
        @Override public List<Parameter> argumentParameters() { return _argumentParameters; }
        @Override public Signature signature() { return _signature; }
        @Override public TableSymbol rowScope() { return _binder.rowScopeOrEmpty(); }
        @Override public GlobalState globals() { return _binder._globals; }
        @Override public ClusterSymbol currentCluster() { return _binder._currentCluster; }
        @Override public DatabaseSymbol currentDatabase() { return _binder._currentDatabase; }
        @Override public FunctionSymbol currentFunction() { return _binder._currentFunction; }

        private static final SyntaxNode Nowhere =
            new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken));

        @Override
        public Symbol getReferencedSymbol(String name)
        {
            var info = _binder.bindName(name, SymbolMatch.Default, Nowhere);
            return info != null ? info.referencedSymbol() : null; // PORT: §3.14 info?.ReferencedSymbol as Symbol
        }

        @Override
        public TypeSymbol getResultType(String name)
        {
            var info = _binder.bindName(name, SymbolMatch.Default, Nowhere);
            return info != null ? info.resultType() : null; // PORT: §3.14 info?.ResultType
        }

        @Override
        public String getResultName(Expression expr, String defaultName)
        {
            return Binder.getExpressionResultName(expr, defaultName, this.rowScope());
        }

        // PORT: §3.12 defaultName = "" is provided by CustomReturnTypeContext.getResultName(Expression)
    }

    @Internal
    public static boolean tryGetLiteralStringValue(Expression expression, Out<String> value) // PORT: §3.3 out
    {
        Out<ValueInfo> valueInfo = new Out<>(); // PORT: §3.3 out var
        if (tryGetLiteralValueInfo(expression, valueInfo))
        {
            value.value = valueInfo.value != null && valueInfo.value.value() instanceof String s ? s : null; // PORT: §3.14 valueInfo?.Value as string
            return value.value != null;
        }
        else
        {
            value.value = null;
            return false;
        }
    }

    @Internal
    public static <T> boolean tryGetLiteralValue(Class<T> type, Expression expression, Out<T> value) // PORT: §3.10 reified T, §3.3 out
    {
        Out<ValueInfo> valueInfo = new Out<>(); // PORT: §3.3 out var
        if (tryGetLiteralValueInfo(expression, valueInfo)
            && valueInfo.value != null
            && type.isInstance(valueInfo.value.value())) // PORT: §3.10 valueInfo?.Value is T tvalue
        {
            value.value = type.cast(valueInfo.value.value());
            return true;
        }
        else
        {
            value.value = null; // PORT: §3.10 default(T)
            return false;
        }
    }

    /// <summary>
    /// Gets the value of the literal if the expression is a literal or refers to literal
    /// </summary>
    @Internal
    public static boolean tryGetLiteralValueInfo(Expression expression, Out<ValueInfo> value) // PORT: §3.3 out
    {
        expression = getUnderlyingExpression(expression);

        if (expression.isLiteral())
        {
            value.value = expression.literalValueInfo();
            return value.value != null;
        }
        else if (expression instanceof NameReference nr && nr.referencedSymbol() instanceof VariableSymbol vs && vs.isConstant())
        {
            value.value = vs.constantValueInfo();
            return true;
        }
        else
        {
            value.value = null;
            return false;
        }
    }

    /// <summary>
    /// Determines if the name is a pattern (contains a *)
    /// </summary>
    private static boolean isPattern(String name)
    {
        return name.contains("*");
    }

    /// <summary>
    /// Gets the cluster for the specified name, or an empty open cluster.
    /// </summary>
    private ClusterSymbol getClusterFunctionResult(String name, SyntaxNode location, List<Diagnostic> diagnostics)
    {
        var cluster = _globals.getCluster(name);
        if (cluster == null)
        {
            if (diagnostics != null && location != null)
            {
                if (_isFuzzy)
                {
                    diagnostics.add(DiagnosticFacts.getFuzzyClusterNotDefined(name).withLocation(location));
                }
                else
                {
                    diagnostics.add(DiagnosticFacts.getNameDoesNotReferToAnyKnownCluster(name).withLocation(location));
                }
            }

            cluster = getOpenCluster(name);
        }

        return cluster;
    }

    /// <summary>
    /// Gets the result for an invocation of the database() function
    /// </summary>
    private TypeSymbol getDatabaseFunctionResult(String nameOrPattern, SyntaxNode location, List<Diagnostic> diagnostics)
    {
        var db = getMatchingDatabase(nameOrPattern, _pathScope);

        if (db == null)
        {
            if (diagnostics != null && location != null)
            {
                if (_isFuzzy)
                {
                    diagnostics.add(DiagnosticFacts.getFuzzyDatabaseNotDefined(nameOrPattern).withLocation(location));
                }
                else
                {
                    diagnostics.add(DiagnosticFacts.getNameDoesNotReferToAnyKnownDatabase(nameOrPattern).withLocation(location));
                }
            }

            if (!isPattern(nameOrPattern))
            {
                // return open database regardless of container's open state to reduce cascading errors
                if (_pathScope == null)
                {
                    db = getOpenDatabase(nameOrPattern, _currentCluster);
                }
                else if (_pathScope instanceof ClusterSymbol cluster)
                {
                    db = getOpenDatabase(nameOrPattern, cluster);
                }
                else if (_pathScope instanceof GroupSymbol g)
                {
                    // use the first cluster in the group 
                    // var gc = g.Members.OfType<ClusterSymbol>().FirstOrDefault();
                    ClusterSymbol gc = null; // PORT: §3.6
                    for (Symbol gm : g.members())
                    {
                        if (gm instanceof ClusterSymbol gmc)
                        {
                            gc = gmc;
                            break;
                        }
                    }
                    if (gc != null)
                    {
                        db = getOpenDatabase(nameOrPattern, gc);
                    }
                }
            }
        }

        return db;
    }

    /// <summary>
    /// Gets the named database or group of databases
    /// </summary>
    private TypeSymbol getMatchingDatabase(String nameOrPattern, Symbol clusterOrGroup)
    {
        if ((clusterOrGroup == null
            || clusterOrGroup == _currentCluster)
            && (DotNetStrings.isNullOrEmpty(nameOrPattern)
                || DotNetStrings.compare(_currentDatabase.name(), nameOrPattern, true) == 0
                || DotNetStrings.compare(_currentDatabase.alternateName(), nameOrPattern, true) == 0))
        {
            return _currentDatabase;
        }

        var db = _aliasedDatabases.get(nameOrPattern); // PORT: §3.3 TryGetValue; values are never null
        if (db != null)
        {
            return db;
        }

        var matching = s_symbolListPool.allocateFromPool();
        try
        {
            if (clusterOrGroup == null)
            {
                getMatchingDatabases(nameOrPattern, _currentCluster, matching);
            }
            else if (clusterOrGroup instanceof ClusterSymbol cluster)
            {
                getMatchingDatabases(nameOrPattern, cluster, matching);
            }
            else if (clusterOrGroup instanceof GroupSymbol group)
            {
                for (Symbol s : group.members())
                {
                    if (s instanceof ClusterSymbol c)
                    {
                        getMatchingDatabases(nameOrPattern, c, matching);
                    }
                }
            }

            if (matching.size() == 1)
            {
                return (TypeSymbol)matching.get(0);
            }
            else if (matching.size() > 1)
            {
                return new GroupSymbol(matching);
            }
            else if (!isPattern(nameOrPattern))
            {
                if (clusterOrGroup == null && _currentCluster.isOpen())
                {
                    return getOpenDatabase(nameOrPattern, _currentCluster);
                }
                else if (clusterOrGroup instanceof ClusterSymbol c && c.isOpen())
                {
                    return getOpenDatabase(nameOrPattern, c);
                }
                else if (clusterOrGroup instanceof GroupSymbol g)
                {
                    // if any cluster in the group is open, return an open database corresponding to that cluster
                    for (Symbol m : g.members())
                    {
                        if (m instanceof ClusterSymbol cs && cs.isOpen())
                        {
                            return getOpenDatabase(nameOrPattern, cs);
                        }
                    }
                }
            }

            // no matching name and cannot be open database
            return null;
        }
        finally
        {
            s_symbolListPool.returnToPool(matching);
        }
    }

    /// <summary>
    /// Gets the matching databases in the specified cluster
    /// </summary>
    private static void getMatchingDatabases(String nameOrPattern, ClusterSymbol cluster, List<Symbol> matches)
    {
        if (!DotNetStrings.isNullOrEmpty(nameOrPattern))
        {
            for (DatabaseSymbol cdb : cluster.databases())
            {
                if (KustoFacts.matches(nameOrPattern, cdb.name(), true)
                    || KustoFacts.matches(nameOrPattern, cdb.alternateName(), true))
                {
                    matches.add(cdb);
                }
            }
        }
    }

    /// <summary>
    /// Gets the result of calling the table() function in the current context.
    /// </summary>
    private TypeSymbol getTableFunctionResult(String nameOrPattern, SyntaxNode location, List<Diagnostic> diagnostics)
    {
        // check for local table first
        if (_pathScope == null && !isPattern(nameOrPattern))
        {
            var match = SymbolMatch.Table | SymbolMatch.Local | SymbolMatch.View;

            var symbols = s_symbolListPool.allocateFromPool();
            try
            {
                // check scope for variables, etc
                _localScope.getSymbols(nameOrPattern, match, symbols);

                if (symbols.size() > 0)
                {
                    Symbol result = symbols.get(0);

                    if (result instanceof FunctionSymbol fs
                        && fs.isView()
                        && fs.minArgumentCount() == 0)
                    {
                        result = fs.getReturnType(_globals);
                    }
                    else
                    {
                        result = getResultType(result);
                    }

                    return result instanceof TableSymbol ts ? ts : (TypeSymbol)ErrorSymbol.Instance; // PORT: §3.15 'as TableSymbol ??'
                }
            }
            finally
            {
                s_symbolListPool.returnToPool(symbols);
            }
        }

        var table = getMatchingDatabaseTable(nameOrPattern, _pathScope);

        if (table == null)
        {
            if (diagnostics != null && location != null)
            {
                if (_isFuzzy)
                {
                    diagnostics.add(DiagnosticFacts.getFuzzyTableNotDefined(nameOrPattern).withLocation(location));
                }
                else
                {
                    diagnostics.add(DiagnosticFacts.getNameDoesNotReferToAnyKnownTable(nameOrPattern).withLocation(location));
                }
            }

            // return open table regardless of containing tables's open state to reduce cascading errors
            if (!isPattern(nameOrPattern))
            {
                if (_pathScope == null)
                {
                    table = getOpenTable(nameOrPattern, _currentDatabase);
                }
                else if (_pathScope instanceof DatabaseSymbol db)
                {
                    table = getOpenTable(nameOrPattern, db);
                }
                else if (_pathScope instanceof GroupSymbol g)
                {
                    // make an open table based on the first database
                    // var gdb = g.Members.OfType<DatabaseSymbol>().FirstOrDefault();
                    DatabaseSymbol gdb = null; // PORT: §3.6
                    for (Symbol gm : g.members())
                    {
                        if (gm instanceof DatabaseSymbol gmd)
                        {
                            gdb = gmd;
                            break;
                        }
                    }
                    if (gdb != null)
                    {
                        table = getOpenTable(nameOrPattern, gdb);
                    }
                }
            }
        }

        return table;
    }

    /// <summary>
    /// Gets the matching table or group of tables
    /// </summary>
    private TypeSymbol getMatchingDatabaseTable(String nameOrPattern, Symbol databaseOrGroup)
    {
        var matches = s_symbolListPool.allocateFromPool();
        try
        {
            if (databaseOrGroup == null)
            {
                getMatchingDatabaseTables(nameOrPattern, _currentDatabase, matches);
            }
            else if (databaseOrGroup instanceof DatabaseSymbol database)
            {
                getMatchingDatabaseTables(nameOrPattern, database, matches);
            }
            else if (databaseOrGroup instanceof GroupSymbol group)
            {
                for (Symbol s : group.members())
                {
                    if (s instanceof DatabaseSymbol db)
                    {
                        getMatchingDatabaseTables(nameOrPattern, db, matches);
                    }
                }
            }

            if (matches.size() == 1)
            {
                return (TableSymbol)matches.get(0);
            }
            else if (matches.size() > 1)
            {
                return new GroupSymbol(matches);
            }
            else if (!isPattern(nameOrPattern))
            {
                if (databaseOrGroup == null && _currentDatabase.isOpen())
                {
                    return getOpenTable(nameOrPattern, _currentDatabase);
                }
                if (databaseOrGroup instanceof DatabaseSymbol db && db.isOpen())
                {
                    return getOpenTable(nameOrPattern, db);
                }
                else if (databaseOrGroup instanceof GroupSymbol g)
                {
                    // in any database in group is open, then return an open table corresponding to that database
                    for (Symbol m : g.members())
                    {
                        if (m instanceof DatabaseSymbol gdb && gdb.isOpen())
                        {
                            return getOpenTable(nameOrPattern, gdb);
                        }
                    }
                }
            }

            // nothing matched and could not invent an OpenTable
            return null;
        }
        finally
        {
            s_symbolListPool.returnToPool(matches);
        }
    }

    /// <summary>
    /// Gets all matching tables
    /// </summary>
    private void getMatchingDatabaseTables(String nameOrPattern, DatabaseSymbol database, List<Symbol> matches)
    {
        for (TableSymbol table : database.tables())
        {
            if (KustoFacts.matches(nameOrPattern, table.name()))
            {
                matches.add(table);
            }
        }
    }

    /// <summary>
    /// Gets the result of calling the external_table() function in the current context.
    /// </summary>
    private TypeSymbol getExternalTableFunctionResult(String name, SyntaxNode location, List<Diagnostic> diagnostics)
    {
        var db = _pathScope instanceof DatabaseSymbol pdb ? pdb : _currentDatabase; // PORT: §3.15 'as DatabaseSymbol ??'
        var table = db.getExternalTable(name);

        if (table == null)
        {
            if (diagnostics != null && location != null)
            {
                if (_isFuzzy)
                {
                    diagnostics.add(DiagnosticFacts.getFuzzyExternalTableNotDefined(name).withLocation(location));
                }
                else
                {
                    diagnostics.add(DiagnosticFacts.getNameDoesNotReferToAnyKnownExternalTable(name).withLocation(location));
                }
            }

            table = new TableSymbol(name).withIsExternal(true).withIsOpen(true);
        }

        return table;
    }

    /// <summary>
    /// Gets the result of calling the materialized_view() function in the current context.
    /// </summary>
    private TypeSymbol getMaterializedViewFunctionResult(String name, SyntaxNode location, List<Diagnostic> diagnostics)
    {
        var db = _pathScope instanceof DatabaseSymbol pdb ? pdb : _currentDatabase; // PORT: §3.15 'as DatabaseSymbol ??'
        TableSymbol table = db.getMaterializedView(name);

        if (table == null)
        {
            if (diagnostics != null && location != null)
            {
                if (_isFuzzy)
                {
                    diagnostics.add(DiagnosticFacts.getFuzzyMaterializedViewNotDefined(name).withLocation(location));
                }
                else
                {
                    diagnostics.add(DiagnosticFacts.getNameDoesNotReferToAnyKnownMaterializedView(name).withLocation(location));
                }
            }

            table = new TableSymbol(name).withIsMaterializedView(true).withIsOpen(true);
        }

        return table;
    }

    /// <summary>
    /// Gets the result of calling the entity_group() function in the current context.
    /// </summary>
    private FunctionCallResult getEntityGroupFunctionResult(String name, SyntaxNode location, List<Diagnostic> diagnostics)
    {
        var db = _pathScope instanceof DatabaseSymbol pdb ? pdb : _currentDatabase; // PORT: §3.15 'as DatabaseSymbol ??'
        var entityGroup = db.getEntityGroup(name);

        if (entityGroup == null)
        {
            if (diagnostics != null && location != null)
            {
                if (_isFuzzy)
                {
                    diagnostics.add(DiagnosticFacts.getFuzzyEntityGroupNotDefined(name).withLocation(location));
                }
                else
                {
                    diagnostics.add(DiagnosticFacts.getNameDoesNotReferToAnyKnownEntityGroup(name).withLocation(location));
                }
            }

            return FunctionCallResult.of(new EntityGroupSymbol()); // PORT: §3.8 implicit conversion
        }

        // return the computed result of the signature that refers to the entity group declaration.
        // This will allow the facts about the entities in the group to be visible to analysis.
        if (entityGroup.signature() != null)
            return this.getComputedFunctionCallResult(entityGroup.signature());

        return FunctionCallResult.of(entityGroup); // PORT: §3.8 implicit conversion
    }

    /// <summary>
    /// Gets the result of calling the stored_query_result() function in the current context.
    /// </summary>
    private TypeSymbol getStoredQueryResultFunctionResult(String name, SyntaxNode location, List<Diagnostic> diagnostics)
    {
        var db = _pathScope instanceof DatabaseSymbol pdb ? pdb : _currentDatabase; // PORT: §3.15 'as DatabaseSymbol ??'
        StoredQueryResultSymbol sqr = db.getStoredQueryResult(name);

        if (sqr == null)
        {
            if (diagnostics != null && location != null)
            {
                if (_isFuzzy)
                {
                    diagnostics.add(DiagnosticFacts.getFuzzyStoredQueryResultNotDefined(name).withLocation(location));
                }
                else
                {
                    diagnostics.add(DiagnosticFacts.getNameDoesNotReferToAnyKnownStoredQueryResult(name).withLocation(location));
                }
            }

            sqr = new StoredQueryResultSymbol(name, EmptyReadOnlyList.<ColumnSymbol>instance());
        }

        return sqr;
    }

    private GraphSymbol getGraphFunctionResult(
        String modelName, String snapshotName, Boolean isVolatile,
        SyntaxElement location, List<Diagnostic> diagnostics)
    {
        var db = _pathScope instanceof DatabaseSymbol pdb ? pdb : _currentDatabase; // PORT: §3.15 'as DatabaseSymbol ??'
        var model = db.getGraphModel(modelName);
        if (model != null)
        {
            if (snapshotName != null)
            {
                if (!model.tryGetSnapshot(snapshotName, new Out<>())) // PORT: §3.3 out _
                {
                    diagnostics.add(DiagnosticFacts.getNameDoesNotReferToAnyKnownGraphSnapshot(snapshotName, modelName).withLocation(location));
                }
            }

            if (model.computedGraphSymbol() == null)
            {
                var prevCluster = _currentCluster;
                var prevDatabase = _currentDatabase;

                _currentCluster = _globals.getCluster(db);
                _currentDatabase = db;

                var edgeShape = model.edges().size() > 0 ? getCombinedGraphResults(model.edges()) : null;
                var nodeShape = model.nodes().size() > 0 ? getCombinedGraphResults(model.nodes()) : null;
                var symbol = new GraphSymbol(edgeShape, nodeShape);

                model.setComputedGraphSymbol(symbol);

                _currentCluster = prevCluster;
                _currentDatabase = prevDatabase;
            }

            return model.computedGraphSymbol();
        }
        else
        {
            diagnostics.add(DiagnosticFacts.getNameDoesNotReferToAnyKnownGraphModel(modelName).withLocation(location));
        }

        return GraphSymbol.Empty;
    }

    private TableSymbol getCombinedGraphResults(List<Signature> signatures)
    {
        var tables = s_tableListPool.allocateFromPool();
        try
        {
            for (Signature signature : signatures)
            {
                FunctionCallResult fcResult = this.getComputedFunctionCallResult(signature);
                if (fcResult.type() instanceof TableSymbol table)
                    tables.add(table);
            }

            var resultTable = TableSymbol.combine(CombineKind.UnifySameName, tables);
            return resultTable;
        }
        finally
        {
            s_tableListPool.returnToPool(tables);
        }
    }

    /// <summary>
    /// Determines if <see cref="P:type1"/> can be promoted to <see cref="P:type2"/>
    /// </summary>
    public static boolean isPromotable(TypeSymbol type1, TypeSymbol type2)
    {
        return type1 instanceof ScalarSymbol type1Scalar && type2 instanceof ScalarSymbol type2Scalar && type2Scalar.isWiderThan(type1Scalar);
    }

    /// <summary>
    /// Promotes int to long
    /// </summary>
    public static TypeSymbol promote(TypeSymbol symbol)
    {
        if (symbol == ScalarTypes.Int)
        {
            return ScalarTypes.Long;
        }
        else
        {
            return symbol;
        }
    }


    /// <summary>
    /// Gets the common return type across a set of signatures, or error if there is no common type.
    /// The common return type is the return type all the signatures share, or the error type if the return types differ.
    /// </summary>
    private TypeSymbol getCommonReturnType(List<Signature> signatures, List<Expression> arguments, List<TypeSymbol> argumentTypes, SyntaxElement location)
    {
        if (signatures.size() == 0)
        {
            return ErrorSymbol.Instance;
        }
        else if (signatures.size() == 1)
        {
            return getFunctionCallResult(signatures.get(0), arguments, argumentTypes, location).type();
        }
        else
        {
            var firstType = getFunctionCallResult(signatures.get(0), arguments, argumentTypes, location).type();

            for (int i = 1; i < signatures.size(); i++)
            {
                var type = getFunctionCallResult(signatures.get(i), arguments, argumentTypes, location).type();
                if (!symbolsAssignable(firstType, type))
                {
                    if (argumentsHaveErrorsOrUnknown(argumentTypes))
                    {
                        return ScalarTypes.Unknown;
                    }
                    else
                    {
                        return ErrorSymbol.Instance;
                    }
                }
            }

            return firstType;
        }
    }

    /// <summary>
    /// Gets the signatures that best match the specified arguments.
    /// If there is no best match, then multiple signatures will be returned.
    /// </summary>
    private void getBestMatchingSignatures(
        List<Signature> signatures,
        List<Expression> arguments,
        List<TypeSymbol> argumentTypes,
        List<Signature> result,
        boolean requireAllArgumentsMatch)
    {
        var argCount = argumentTypes.size();

        if (signatures.size() == 0)
        {
            return;
        }
        else if (signatures.size() == 1)
        {
            result.add(signatures.get(0));
            return;
        }

        // determine candidates
        if (signatures.size() > 1)
        {
            var closestCount = 0;
            var maxCount = 0;

            for (Signature s : signatures)
            {
                if (argCount >= s.minArgumentCount() && argCount <= s.maxArgumentCount())
                {
                    result.add(s);
                }
                else if (argCount < s.minArgumentCount() && closestCount > s.minArgumentCount())
                {
                    closestCount = s.minArgumentCount();
                }

                if (s.maxArgumentCount() > maxCount)
                {
                    maxCount = s.maxArgumentCount();
                }
            }

            // if we didn't already find candidates, pick all with closest count
            if (result.size() == 0)
            {
                if (closestCount == 0)
                {
                    closestCount = maxCount;
                }

                for (Signature s : signatures)
                {
                    if (closestCount >= s.minArgumentCount() && closestCount <= s.maxArgumentCount())
                    {
                        result.add(s);
                    }
                }
            }
        }

        // reduce results to best matching functions
        if (result.size() > 1)
        {
            int mostMatchingParameterCount = 0;

            // determine the most matching parameter count
            for (Signature s : result)
            {
                var count = getParameterMatchCount(s, arguments, argumentTypes);
                if (count > mostMatchingParameterCount)
                {
                    mostMatchingParameterCount = count;
                }
            }

            if (requireAllArgumentsMatch && mostMatchingParameterCount < arguments.size())
            {
                mostMatchingParameterCount = arguments.size();
            }

            // remove all candidates that do not have the most matching parameters
            for (int i = result.size() - 1; i >= 0; i--)
            {
                var sig = result.get(i);
                if (getParameterMatchCount(sig, arguments, argumentTypes) != mostMatchingParameterCount)
                {
                    result.remove(i);
                }
            }

            // still more than one?  Try to find best match
            if (result.size() > 1)
            {
                var best = result.get(0);
                for (int i = 1; i < result.size(); i++)
                {
                    var signatureCompare = compareSignatureMatch(result.get(i), best, arguments, argumentTypes);
                    if (signatureCompare > 0)
                    {
                        best = result.get(i);
                    }
                    else if (signatureCompare == 0)
                    {
                        // these two signatures are ambiguous
                        return;
                    }
                }

                // go through again looking for signatures that somehow now compare
                // as better or equal to the prevously determined best.
                for (int i = 0; i < result.size(); i++)
                {
                    if (result.get(i) != best)
                    {
                        var signatureCompare = compareSignatureMatch(best, result.get(i), arguments, argumentTypes);
                        if (signatureCompare <= 0)
                        {
                            // now a different signature is better? This is ambiguous.
                            return;
                        }
                    }
                }

                // one was clearly the best
                result.clear();
                result.add(best);
            }
        }
    }

    private void getBestMatchingSignatures(List<Signature> signatures, List<Expression> arguments, List<TypeSymbol> argumentTypes, List<Signature> result) // PORT: §3.12 requireAllArgumentsMatch = false
    {
        getBestMatchingSignatures(signatures, arguments, argumentTypes, result, false);
    }

    /// <summary>
    /// Determines if <see cref="P:signature1"/> is a better match than <see cref="P:signature2"/> for the specified arguments.
    /// </summary>
    private int compareSignatureMatch(Signature signature1, Signature signature2, List<Expression> arguments, List<TypeSymbol> argumentTypes)
    {
        var argCount = argumentTypes.size();
        var matchCount1 = getParameterMatchCount(signature1, arguments, argumentTypes);
        var matchCount2 = getParameterMatchCount(signature2, arguments, argumentTypes);

        // if signature1 matches all arguments but signature2 does not, signature1 is better
        if (matchCount1 == argCount && matchCount2 < argCount)
            return matchCount1 - matchCount2;

        // signature with better worst overall parameter match wins
        var worstMatch1 = getWorstParameterMatch(signature1, arguments, argumentTypes);
        var worstMatch2 = getWorstParameterMatch(signature2, arguments, argumentTypes);

        var matchCompare = compareParameterMatch(worstMatch1, worstMatch2);
        if (matchCompare != 0)
            return matchCompare;

        // signature with the better best overall parameter match wins
        var bestMatch1 = getBestParameterMatch(signature1, arguments, argumentTypes);
        var bestMatch2 = getBestParameterMatch(signature2, arguments, argumentTypes);

        matchCompare = compareParameterMatch(bestMatch1, bestMatch2);
        if (matchCompare != 0)
            return matchCompare;

        // ambigous on betterness of parameter matches
        // signature with the most matching parameters wins
        return matchCount1 - matchCount2;
    }

    private static int compareParameterMatch(ParameterMatchKind match1, ParameterMatchKind match2)
    {
        return match1.ordinal() - match2.ordinal(); // PORT: §3.17 enum subtraction; ParameterMatchKind has no explicit values, so declaration order is the value
    }

    /// <summary>
    /// Determines the number of arguments that match their corresponding signature parameter.
    /// </summary>
    private int getParameterMatchCount(Signature signature, List<Expression> arguments, List<TypeSymbol> argumentTypes)
    {
        var argCount = argumentTypes.size();
        int matches = 0;

        var argumentParameters = s_parameterListPool.allocateFromPool();
        try
        {
            signature.getArgumentParameters(arguments, argumentParameters);

            for (int i = 0; i < argCount; i++)
            {
                if (getParameterMatchKind(
                    signature,
                    argumentParameters, argumentTypes,
                    argumentParameters.get(i), arguments.get(i), argumentTypes.get(i)) != ParameterMatchKind.None)
                {
                    matches++;
                }
            }
        }
        finally
        {
            s_parameterListPool.returnToPool(argumentParameters);
        }


        return matches;
    }

    private static boolean isDefaultValueIndicator(Parameter parameter, Expression argument)
    {
        return parameter.defaultValueIndicator() != null
            && argument.resultType() == ScalarTypes.String
            && argument instanceof LiteralExpression lit
            && lit.literalValue() instanceof String value
            && value.equals(parameter.defaultValueIndicator()); // PORT: §3.14 string ==
    }

    private ParameterMatchKind getWorstParameterMatch(
        Signature signature,
        List<Expression> arguments,
        List<TypeSymbol> argumentTypes)
    {
        var argumentParameters = s_parameterListPool.allocateFromPool();
        try
        {
            signature.getArgumentParameters(arguments, argumentParameters);

            var worstMatchKind = ParameterMatchKind.Exact;

            for (int argumentIndex = 0; argumentIndex < arguments.size(); argumentIndex++)
            {
                var matchKind = getParameterMatchKind(signature, argumentParameters, argumentTypes, argumentParameters.get(argumentIndex), arguments.get(argumentIndex), argumentTypes.get(argumentIndex));
                if (matchKind.compareTo(worstMatchKind) < 0) // PORT: §3.17 enum <
                    worstMatchKind = matchKind;
            }

            return worstMatchKind;
        }
        finally
        {
            s_parameterListPool.returnToPool(argumentParameters);
        }
    }

    private ParameterMatchKind getBestParameterMatch(
        Signature signature,
        List<Expression> arguments,
        List<TypeSymbol> argumentTypes)
    {
        var argumentParameters = s_parameterListPool.allocateFromPool();
        try
        {
            signature.getArgumentParameters(arguments, argumentParameters);

            var bestMatchKind = ParameterMatchKind.None;

            for (int argumentIndex = 0; argumentIndex < arguments.size(); argumentIndex++)
            {
                var matchKind = getParameterMatchKind(signature, argumentParameters, argumentTypes, argumentParameters.get(argumentIndex), arguments.get(argumentIndex), argumentTypes.get(argumentIndex));
                if (matchKind.compareTo(bestMatchKind) > 0) // PORT: §3.17 enum >
                    bestMatchKind = matchKind;
            }

            return bestMatchKind;
        }
        finally
        {
            s_parameterListPool.returnToPool(argumentParameters);
        }
    }

    private ParameterMatchKind getParameterMatchKind(
        Signature signature,
        List<Parameter> argumentParameters,
        List<TypeSymbol> argumentTypes,
        Parameter parameter,
        Expression argument,
        TypeSymbol argumentType)
    {
        return getParameterMatchKind(signature, argumentParameters, argumentTypes, parameter, argument, argumentType, allowImplicitArgumentCoercion(signature));
    }

    /// <summary>
    /// Determines the kind of match that the argument has with its corresponding signature parameter.
    /// </summary>
    public static ParameterMatchKind getParameterMatchKind(
        Signature signature,
        List<Parameter> argumentParameters,
        List<TypeSymbol> argumentTypes,
        Parameter parameter,
        Expression argument,
        TypeSymbol argumentType,
        boolean allowImplicitArgumentCoercion)
    {
        if (parameter == null)
            return ParameterMatchKind.None;

        if (argumentType == ScalarTypes.Unknown)
            return ParameterMatchKind.Unknown;

        if (isDefaultValueIndicator(parameter, argument))
        {
            return ParameterMatchKind.Exact;
        }

        if (argument instanceof StarExpression)
        {
            return (parameter.argumentKind() == ArgumentKind.StarOnly
                || parameter.argumentKind() == ArgumentKind.StarAllowed)
                    ? ParameterMatchKind.Exact
                    : ParameterMatchKind.None;
        }
        else if (parameter.argumentKind() == ArgumentKind.StarOnly)
        {
            return ParameterMatchKind.None;
        }

        switch (parameter.typeKind())
        {
            case Any:
                return ParameterMatchKind.Unknown;

            case Declared:
                if (symbolsAssignable(parameter.declaredTypes(), argumentType, Conversion.None))
                {
                    if (parameter.declaredTypes().size() == 1)
                    {
                        return ParameterMatchKind.Exact;
                    }
                    else
                    {
                        return ParameterMatchKind.OneOfMany;
                    }
                }
                else if (symbolsAssignable(parameter.declaredTypes(), argumentType, Conversion.Promotable))
                {
                    return ParameterMatchKind.Promoted;
                }
                else if (allowImplicitArgumentCoercion
                    && symbolsAssignable(parameter.declaredTypes(), argumentType, Conversion.Dynamic))
                {
                    return ParameterMatchKind.Dynamic;
                }
                else if (allowImplicitArgumentCoercion
                    && symbolsAssignable(parameter.declaredTypes(), argumentType, Conversion.Compatible))
                {
                    return ParameterMatchKind.Compatible;
                }
                break;

            case Scalar:
                if (argumentType.isScalar())
                    return ParameterMatchKind.Scalar;
                break;

            case Integer:
                if (TypeFacts.isInteger(argumentType))
                    return ParameterMatchKind.Integer;
                break;

            case RealOrDecimal:
                if (TypeFacts.isRealOrDecimal(argumentType))
                    return ParameterMatchKind.OneOfMany;
                break;

            case StringOrDynamic:
                if (TypeFacts.isStringOrDynamic(argumentType))
                    return ParameterMatchKind.OneOfMany;
                break;

            case StringOrArray:
                if (TypeFacts.isStringOrArray(argumentType))
                    return ParameterMatchKind.OneOfMany;
                break;

            case IntegerOrArray:
                if (TypeFacts.isIntegerOrArray(argumentType))
                    return ParameterMatchKind.OneOfMany;
                break;

            case DynamicArray:
                if (TypeFacts.isDynamicArray(argumentType))
                    return ParameterMatchKind.OneOfMany;
                break;

            case DynamicBag:
                if (TypeFacts.isDynamicBag(argumentType))
                    return ParameterMatchKind.OneOfMany;
                break;

            case Number:
                if (TypeFacts.isNumeric(argumentType))
                    return ParameterMatchKind.Number;
                break;

            case NumberOrBool:
                if (TypeFacts.isNumeric(argumentType) || argumentType == ScalarTypes.Bool)
                    return ParameterMatchKind.Number;
                break;

            case Summable:
                if (TypeFacts.isSummable(argumentType))
                    return ParameterMatchKind.Summable;
                break;
            case Orderable:
                if (TypeFacts.isOrderable(argumentType))
                    return ParameterMatchKind.Orderable;
                break;
            case Tabular:
                if (isTabular(argumentType))
                    return ParameterMatchKind.Tabular;
                break;

            case Database:
                if (isDatabase(argumentType))
                    return ParameterMatchKind.Database;
                break;

            case Cluster:
                if (isCluster(argumentType))
                    return ParameterMatchKind.Cluster;
                break;

            case NotBool:
                if (!symbolsAssignable(argumentType, ScalarTypes.Bool))
                    return ParameterMatchKind.NotType;
                break;

            case NotRealOrBool:
                if (!symbolsAssignable(argumentType, ScalarTypes.Real)
                    && !symbolsAssignable(argumentType, ScalarTypes.Bool))
                    return ParameterMatchKind.NotType;
                break;

            case NotDynamic:
                if (TypeFacts.isAnyScalarExceptDynamic(argumentType)) // PORT: §3.5 extension method
                    return ParameterMatchKind.NotType;
                break;

            case Parameter0:
                return getParameterMatchKind(signature, argumentParameters, argumentTypes, argumentParameters.get(0), argument, argumentType, allowImplicitArgumentCoercion);

            case Parameter1:
                return getParameterMatchKind(signature, argumentParameters, argumentTypes, argumentParameters.get(1), argument, argumentType, allowImplicitArgumentCoercion);

            case Parameter2:
                return getParameterMatchKind(signature, argumentParameters, argumentTypes, argumentParameters.get(2), argument, argumentType, allowImplicitArgumentCoercion);

            case CommonScalar:
            case CommonNumber:
            case CommonSummable:
            case CommonOrderable:
            case CommonScalarOrDynamic:
                var commonType = TypeFacts.getCommonArgumentType(argumentParameters, argumentTypes);
                if (commonType != null)
                {
                    if (symbolsAssignable(commonType, argumentType, Conversion.None))
                    {
                        return ParameterMatchKind.Exact;
                    }
                    else if (symbolsAssignable(commonType, argumentType, Conversion.Promotable))
                    {
                        return ParameterMatchKind.Promoted;
                    }
                    else if (symbolsAssignable(commonType, argumentType, Conversion.Dynamic))
                    {
                        return ParameterMatchKind.Dynamic;
                    }
                    else if (allowImplicitArgumentCoercion
                        && symbolsAssignable(commonType, argumentType, Conversion.Compatible))
                    {
                        return ParameterMatchKind.Compatible;
                    }
                    else if (parameter.typeKind() == ParameterTypeKind.CommonScalarOrDynamic
                             && symbolsAssignable(argumentType, ScalarTypes.Dynamic))
                    {
                        return ParameterMatchKind.Exact;
                    }
                }
                break;
        }

        return ParameterMatchKind.None;
    }

    /// <summary>
    /// Gets <see cref="FunctionCallResult"/> for computed functions that have bodies that must be parsed and bound before understanding the result type.
    /// </summary>
    private FunctionCallResult getComputedFunctionCallResult(Signature signature, List<Expression> arguments, List<TypeSymbol> argumentTypes)
    {
        Out<FunctionBodyFacts> funFacts = new Out<>(); // PORT: §3.3 out var
        tryGetFunctionBodyFacts(signature, funFacts);

        // if the function is not yet analyzed or is known to have a variable return type
        // then compute the function body facts and return type for this location by getting the expansion.
        if (funFacts.value == null || funFacts.value.hasVariableReturnType())
        {
            Out<CallSiteInfo> callSite = new Out<>(); // PORT: §3.3 out arg; value starts null as upstream 'CallSiteInfo callSite = null'
            Out<TypeSymbol> resultType = new Out<>(); // PORT: §3.3 out var

            if (funFacts.value != null
                && arguments != null
                && tryGetResultTypeCallSite(signature, funFacts.value.dependentParameters(), arguments, callSite)
                && canCacheResultType(callSite.value)
                && tryGetResultTypeFromCache(callSite.value, resultType))
            {
                // return known result type w/ deferred expansion
                return new FunctionCallResult(
                    resultType.value,
                    new FunctionCallInfo(getDeferredFunctionCallExpansion(signature, arguments, argumentTypes), funFacts.value));
            }

            // use expansion at this call site to determine correct return type
            // if signature facts was not yet known, it will be computed by calling GetCallSiteExpansion
            var expansion = this.getFunctionCallExpansion(signature, arguments, argumentTypes);

            // get computed fun facts
            tryGetFunctionBodyFacts(signature, funFacts);

            var returnType = getBodyResultType(expansion != null ? expansion.root() : null); // PORT: §3.14 expansion?.Root

            if (returnType == null || returnType.isError())
                returnType = ScalarTypes.Unknown;

            if (funFacts.value != null
                && arguments != null
                && (callSite.value != null || tryGetResultTypeCallSite(signature, funFacts.value.dependentParameters(), arguments, callSite))
                && canCacheResultType(callSite.value))
            {
                // add result type to cache for these arguments
                // so we don't have to recompute/re-expand it when referenced again.
                addResultTypeToCache(callSite.value, returnType);
            }

            return new FunctionCallResult(returnType, new FunctionCallInfo(expansion, funFacts.value));
        }
        else
        {
            // body has non-variable (fixed) return type.
            return new FunctionCallResult(
                funFacts.value.nonVariableReturnType(),
                new FunctionCallInfo(getDeferredFunctionCallExpansion(signature, arguments, argumentTypes), funFacts.value));
        }
    }

    private FunctionCallResult getComputedFunctionCallResult(Signature signature, List<Expression> arguments) // PORT: §3.12 argumentTypes = null
    {
        return getComputedFunctionCallResult(signature, arguments, null);
    }

    private FunctionCallResult getComputedFunctionCallResult(Signature signature) // PORT: §3.12 arguments = null, argumentTypes = null
    {
        return getComputedFunctionCallResult(signature, null, null);
    }

    /// <summary>
    /// Returns true if the function call at this callsite allows caching of result type.
    /// </summary>
    private boolean canCacheResultType(CallSiteInfo callSite)
    {
        Out<FunctionBodyFacts> funFacts = new Out<>(); // PORT: §3.3 out var
        tryGetFunctionBodyFacts(callSite.signature(), funFacts);

        if (funFacts.value == null)
            return false;

        if (!funFacts.value.hasVariableReturnType())
            return false;

        if (funFacts.value.hasUnqualifiedTableCall())
        {
            if (isLocalTabularVariable(funFacts.value.unqualifiedTableNames()))
                return false;

            for (Object value : callSite.values())
            {
                if (value instanceof String possibleTableName
                    && isLocalTabularVariable(possibleTableName))
                    return false;
            }
        }

        return true;
    }

    /// <summary>
    /// Gets a 'result type' callsite for a function call.
    /// </summary>
    private static boolean tryGetResultTypeCallSite(
        Signature signature,
        List<Parameter> dependentParameters,
        List<Expression> arguments,
        Out<CallSiteInfo> callSiteInfo) // PORT: §3.3 out
    {
        var argumentParameters = s_parameterListPool.allocateFromPool();
        try
        {
            signature.getArgumentParameters(arguments, argumentParameters);

            var values = new Object[dependentParameters.size()];
            for (int i = 0; i < dependentParameters.size(); i++)
            {
                var p = dependentParameters.get(i);
                if (p != null)
                {
                    var index = argumentParameters.indexOf(p);
                    if (index < 0 || index >= arguments.size())
                    {
                        callSiteInfo.value = null;
                        return false;
                    }

                    var arg = arguments.get(index);
                    Out<Object> value = new Out<>(); // PORT: §3.3 out var
                    if (tryGetCallSiteArgumentValue(arguments.get(index), value))
                    {
                        values[i] = value.value;
                    }
                    else
                    {
                        callSiteInfo.value = null;
                        return false;
                    }
                }
            }

            callSiteInfo.value = new CallSiteInfo(signature, dependentParameters, Arrays.asList(values)); // PORT: §3.17 object[] as IReadOnlyList<object>
            return true;
        }
        finally
        {
            s_parameterListPool.returnToPool(argumentParameters);
        }
    }

    /// <summary>
    /// Gets the value of a function call argument 
    /// that can be used as a callsite value.
    /// </summary>
    private static boolean tryGetCallSiteArgumentValue(Expression arg, Out<Object> value) // PORT: §3.3 out
    {
        if (arg.constantValueInfo() != null)
        {
            value.value = arg.constantValueInfo().value();
            return true;
        }
        else if (arg.resultType() instanceof TableSymbol table)
        {
            value.value = table;
            return true;
        }
        else
        {
            value.value = null;
            return false;
        }
    }

    /// <summary>
    /// Gets the result type for the callsite, if cached.
    /// </summary>
    private boolean tryGetResultTypeFromCache(CallSiteInfo callSiteInfo, Out<TypeSymbol> type) // PORT: §3.3 out
    {
        // PORT: §3.3 Dictionary.TryGetValue(k, out v): cached values are never null
        type.value = _localBindingCache.CallSiteToResultTypeMap.get(callSiteInfo);
        if (type.value != null)
            return true;

        Out<MostRecentlyUsedCache<CallSiteInfo, TypeSymbol>> globalMru = new Out<>(); // PORT: §3.3 out var
        if (_globalBindingCache.CallSiteToResultTypeMap.tryGetValue(callSiteInfo.signature(), globalMru)
            && globalMru.value.tryGetValue(callSiteInfo, type))
            return true;

        type.value = null;
        return false;
    }

    /// <summary>
    /// Adds the result type for the call site to the cache
    /// </summary>
    private void addResultTypeToCache(CallSiteInfo callsite, TypeSymbol resultType)
    {
        var shouldCacheGlobally = isDatabaseSymbolSignature(callsite.signature());
        if (shouldCacheGlobally)
        {
            Out<MostRecentlyUsedCache<CallSiteInfo, TypeSymbol>> globalMru = new Out<>(); // PORT: §3.3 out var
            if (!_globalBindingCache.CallSiteToResultTypeMap.tryGetValue(callsite.signature(), globalMru))
            {
                globalMru.value = _globalBindingCache.CallSiteToResultTypeMap.getOrAdd(
                    callsite.signature(), key -> new MostRecentlyUsedCache<CallSiteInfo, TypeSymbol>(_globals.getProperty(Properties.MaxCachedResultTypes))
                    );
            }

            globalMru.value.addOrUpdate(callsite, resultType);
        }
        else
        {
            DotNet.dictionaryAdd(_localBindingCache.CallSiteToResultTypeMap, callsite, resultType); // PORT: §3.17 Dictionary.Add throws on duplicate key
        }
    }

    private static TypeSymbol getBodyResultType(SyntaxNode body)
    {
        return
            body instanceof Expression exprBody ? exprBody.resultType()
            : body instanceof FunctionBody functionBody ? (functionBody.expression() != null ? functionBody.expression().resultType() : null) // PORT: §3.14 ?.
            : null;
    }

    private static boolean hasSyntaxErrors(SyntaxNode syntax)
    {
        if (syntax != null && syntax.containsSyntaxDiagnostics())
        {
            // syntax.GetContainedSyntaxDiagnostics().Any(d => d.Severity == DiagnosticSeverity.Error)
            for (Diagnostic d : syntax.getContainedSyntaxDiagnostics()) // PORT: §3.6
            {
                if (Objects.equals(d.severity(), DiagnosticSeverity.Error)) // PORT: §3.14 string ==
                    return true;
            }
        }

        return false;
    }

    private Supplier<FunctionCallExpansion> getDeferredFunctionCallExpansion(Signature signature, List<Expression> arguments, List<TypeSymbol> argumentTypes)
    {
        Out<FunctionCallExpansion> expansion = new Out<>(); // PORT: §3.8 captured local mutated inside the lambda
        List<Expression> args = ListExtensions.toReadOnly(arguments); // force copy
        List<TypeSymbol> types = ListExtensions.toReadOnly(argumentTypes); // force copy

        return () ->
        {
            if (expansion.value == null)
            {
                // re-introduce binding lock since deferred function can be called outside the current binding lock
                synchronized (this._globalBindingCache) // PORT: §3.13 lock
                {
                    expansion.value = this.getFunctionCallExpansion(signature, args, types);
                }
            }

            return expansion.value;
        };
    }

    private Supplier<FunctionCallExpansion> getDeferredFunctionCallExpansion(Signature signature, List<Expression> arguments) // PORT: §3.12 argumentTypes = null
    {
        return getDeferredFunctionCallExpansion(signature, arguments, null);
    }

    private Supplier<FunctionCallExpansion> getDeferredFunctionCallExpansion(Signature signature) // PORT: §3.12 arguments = null, argumentTypes = null
    {
        return getDeferredFunctionCallExpansion(signature, null, null);
    }

    /// <summary>
    /// Gets the inline expansion of a function call
    /// </summary>
    @Internal
    public FunctionCallExpansion getFunctionCallExpansion(
        Signature signature,
        List<Expression> arguments,
        List<TypeSymbol> argumentTypes)
    {
        if (signature.returnKind() != ReturnTypeKind.Computed)
            return null;

        // block cycles in computation
        if (_localBindingCache.SignaturesComputingExpansion.contains(signature))
            return null;

        _localBindingCache.SignaturesComputingExpansion.add(signature);
        try
        {
            FunctionCallExpansion expansion = null;

            Out<CallSiteInfo> callSiteInfo = new Out<>(); // PORT: §3.3 out var
            tryGetExpansionCallSiteInfo(signature, arguments, callSiteInfo);

            Out<FunctionCallExpansion> cachedExpansion = new Out<>(); // PORT: §3.3 out expansion
            if (callSiteInfo.value != null
                && canCacheExpansion(callSiteInfo.value)
                && tryGetExpansionFromCache(callSiteInfo.value, cachedExpansion))
            {
                expansion = cachedExpansion.value;
                return expansion;
            }

            try
            {
                var body = getUnboundBody(signature);
                if (body != null)
                {
                    var isInDatabase = isDatabaseSymbolSignature(signature);
                    var currentDatabase = isInDatabase ? _globals.getDatabase(signature.symbol()) : null;
                    var currentCluster = isInDatabase ? _globals.getCluster(currentDatabase) : null;

                    if (signature.declaration() != null)
                    {
                        // associate new tree with tree it originated from
                        expansion = new FunctionCallExpansion(body, signature.declaration().tree(), signature.declaration().triviaStart());
                    }
                    else
                    {
                        expansion = new FunctionCallExpansion(body);
                    }

                    var staticScope = getOuterScope(signature);

                    var locals = getCallSiteArgumentsAsVariables(signature, arguments, argumentTypes);
                    if (tryBindCalledFunctionBody(expansion, this, currentCluster, currentDatabase, signature.symbol() instanceof FunctionSymbol fsym ? fsym : null, staticScope, locals)) // PORT: §3.15 'as FunctionSymbol'
                    {
                        // compute function body facts as side effect
                        getOrComputeFunctionBodyFacts(signature, expansion.root()); // PORT: §3.20 'var _ =' discard
                    }
                    else
                    {
                        // don't return expansion that did not bind
                        expansion = null;
                    }
                }
            }
            catch (RuntimeException e) // PORT: §3.16 catch (Exception)
            {
                // don't return expansion that failed in binding
                expansion = null;
            }

            // record number of times each signature is expanded
            DictionaryExtensions.addOrUpdate(_localBindingCache.FunctionExpansionCounts, signature, k -> 1, (k, v) -> v + 1);

            if (expansion != null
                && callSiteInfo.value != null
                && canCacheExpansion(callSiteInfo.value))
            {
                addExpansionToCache(callSiteInfo.value, expansion);
            }

            return expansion;
        }
        finally
        {
            _localBindingCache.SignaturesComputingExpansion.remove(signature);
        }
    }

    @Internal
    public FunctionCallExpansion getFunctionCallExpansion(Signature signature, List<Expression> arguments) // PORT: §3.12 argumentTypes = null
    {
        return getFunctionCallExpansion(signature, arguments, null);
    }

    @Internal
    public FunctionCallExpansion getFunctionCallExpansion(Signature signature) // PORT: §3.12 arguments = null, argumentTypes = null
    {
        return getFunctionCallExpansion(signature, null, null);
    }

    /// <summary>
    /// Gets the cached expansion for the function at the call site.
    /// </summary>
    private boolean tryGetExpansionFromCache(CallSiteInfo callsite, Out<FunctionCallExpansion> expansion) // PORT: §3.3 out
    {
        // PORT: §3.3 Dictionary.TryGetValue(k, out v): cached values are never null
        expansion.value = _localBindingCache.CallSiteToExpansionMap.get(callsite);
        if (expansion.value != null)
            return true;

        Out<MostRecentlyUsedCache<CallSiteInfo, FunctionCallExpansion>> globalMru = new Out<>(); // PORT: §3.3 out var
        if (_globalBindingCache.CallSiteToExpansionMap.tryGetValue(callsite.signature(), globalMru)
            && globalMru.value.tryGetValue(callsite, expansion))
            return true;

        expansion.value = null;
        return false;
    }

    /// <summary>
    /// Returns true if the function can have its expansions cached.
    /// </summary>
    private boolean canCacheExpansion(CallSiteInfo callSite)
    {
        Out<FunctionBodyFacts> funFacts = new Out<>(); // PORT: §3.3 out var
        tryGetFunctionBodyFacts(callSite.signature(), funFacts);

        if (funFacts.value == null)
            return false;

        if (!funFacts.value.hasUnqualifiedTableCall())
            return true;

        if (isLocalTabularVariable(funFacts.value.unqualifiedTableNames()))
            return false;

        // check any argument value that might end up as argument to unqualified table call
        for (Parameter dp : funFacts.value.dependentParameters())
        {
            var index = callSite.parameters().indexOf(dp);
            if (index >= 0 && index < callSite.values().size()
                && callSite.values().get(index) instanceof String possibleTableName)
            {
                if (isLocalTabularVariable(possibleTableName))
                    return false;
            }
        }

        return true;
    }

    /// <summary>
    /// Return true if the name can refer to a local tabular variable in dynamic scope.
    /// </summary>
    private boolean isLocalTabularVariable(String name)
    {
        return _localScope.containsSymbol(name, SymbolMatch.Tabular | SymbolMatch.Local);
    }

    /// <summary>
    /// Returns true if any of the names can refer to a local tabular variable in dynamic scope.
    /// </summary>
    private boolean isLocalTabularVariable(List<String> names)
    {
        if (names.size() == 0)
            return false;

        if (names.size() == 1)
            return isLocalTabularVariable(names.get(0));

        // return names.Any(name => IsLocalTabularVariable(name));
        for (String name : names) // PORT: §3.6
        {
            if (isLocalTabularVariable(name))
                return true;
        }

        return false;
    }

    /// <summary>
    /// Adds expansion to either global or local cache.
    /// </summary>
    private void addExpansionToCache(CallSiteInfo callsite, FunctionCallExpansion expansion)
    {
        var shouldCacheGlobally = isDatabaseSymbolSignature(callsite.signature());
        if (shouldCacheGlobally)
        {
            Out<MostRecentlyUsedCache<CallSiteInfo, FunctionCallExpansion>> globalMru = new Out<>(); // PORT: §3.3 out var
            if (!_globalBindingCache.CallSiteToExpansionMap.tryGetValue(callsite.signature(), globalMru))
            {
                globalMru.value = _globalBindingCache.CallSiteToExpansionMap.getOrAdd(
                    callsite.signature(),
                    key -> new MostRecentlyUsedCache<CallSiteInfo, FunctionCallExpansion>(_globals.getProperty(Properties.MaxCachedExpansions)) // PORT: §3.8 lambda parameter '_' is not legal in Java 21
                    );
            }

            globalMru.value.addOrUpdate(callsite, expansion);
        }
        else
        {
            DotNet.dictionaryAdd(_localBindingCache.CallSiteToExpansionMap, callsite, expansion); // PORT: §3.17 Dictionary.Add throws on duplicate key
        }
    }

    /// <summary>
    /// True if the signature is declared by a symbol that is part of database known to the current <see cref="GlobalState"/>
    /// </summary>
    private boolean isDatabaseSymbolSignature(Signature signature)
    {
        return signature != null
            && signature.symbol() != null
            && signature.declaration() == null   // they don't have syntax trees (yet)
            && signature.body() != null          // they do have a body as text
            && _globals.isDatabaseSymbol(signature.symbol());       // and they are known by the global state
    }

    /// <summary>
    /// Gets the 'expansion' call site info for a function call.
    /// </summary>
    private boolean tryGetExpansionCallSiteInfo(
        Signature signature,
        List<Expression> arguments,
        Out<CallSiteInfo> callSite) // PORT: §3.3 out
    {
        if (arguments != null)
        {
            var parameters = new ArrayList<Parameter>(arguments.size());
            var values = new ArrayList<Object>(arguments.size());
            if (tryGetCallSiteParametersAndValues(signature, arguments, parameters, values))
            {
                callSite.value = new CallSiteInfo(signature, parameters, values);
                return true;
            }
        }

        callSite.value = null;
        return false;
    }

    /// <summary>
    /// Gets the correpsonding set of parameters and argument values
    /// for the function call.
    /// </summary>
    private boolean tryGetCallSiteParametersAndValues(
        Signature signature,
        List<Expression> arguments,
        List<Parameter> parameters,
        List<Object> values)
    {
        if (arguments == null)
            return false;

        var argumentParameters = s_parameterListPool.allocateFromPool();
        try
        {
            signature.getArgumentParameters(arguments, argumentParameters);

            for (Parameter p : signature.parameters())
            {
                var argIndex = argumentParameters != null ? argumentParameters.indexOf(p) : -1;

                if (argIndex >= 0
                    && argIndex < arguments.size())
                {
                    Out<Object> value = new Out<>(); // PORT: §3.3 out var
                    if (tryGetCallSiteArgumentValue(arguments.get(argIndex), value))
                    {
                        parameters.add(argumentParameters.get(argIndex));
                        values.add(value.value);
                    }
                }
                else if (p.defaultValue() != null)
                {
                    Out<Object> value = new Out<>(); // PORT: §3.3 out var
                    if (tryGetCallSiteArgumentValue(p.defaultValue(), value))
                    {
                        parameters.add(p);
                        values.add(value.value);
                    }
                }
            }

            return true;
        }
        finally
        {
            s_parameterListPool.returnToPool(argumentParameters);
        }
    }

    /// <summary>
    /// Gets the set of local variables to use instead of argument parameters for the expansion of the function call.
    /// </summary>
    private List<VariableSymbol> getCallSiteArgumentsAsVariables(Signature signature, List<Expression> arguments, List<TypeSymbol> argumentTypes)
    {
        var locals = new ArrayList<VariableSymbol>();

        var argumentParameters = s_parameterListPool.allocateFromPool();
        try
        {
            if (arguments != null)
            {
                signature.getArgumentParameters(arguments, argumentParameters);
            }
            else if (argumentTypes != null)
            {
                signature.getArgumentParametersForTypes(argumentTypes, argumentParameters); // PORT: §2.5 GetArgumentParameters(IReadOnlyList<TypeSymbol>)
            }

            for (Parameter p : signature.parameters())
            {
                var argIndex = argumentParameters != null ? argumentParameters.indexOf(p) : -1;

                // PORT-BUG: upstream uses the non-short-circuit '&' here; with arguments == null this throws (NullReferenceException upstream, NullPointerException here) and the caller's catch (Exception) absorbs it
                if (argIndex >= 0 && arguments != null & argIndex < arguments.size())
                {
                    var arg = arguments.get(argIndex);

                    var argType = argumentTypes != null && argIndex < argumentTypes.size()
                        ? argumentTypes.get(argIndex)
                        : arg.resultType();

                    // use parameter type as variable type if scalar, to avoid analyzing function bodies with incorrect types.
                    var localType = p.isScalar() && p.typeKind() == ParameterTypeKind.Declared
                        ? p.declaredTypes().get(0)
                        : argType;

                    Out<ValueInfo> valueInfo = new Out<>(); // PORT: §3.3 out var
                    var isLiteral = Binder.tryGetLiteralValueInfo(arg, valueInfo);
                    locals.add(new VariableSymbol(p.name(), localType, isLiteral, valueInfo.value, arg)); // PORT: §3.12 source: arg
                }
                else
                {
                    var type = getRepresentativeType(p);

                    var isConstant = p.isOptional() && p.defaultValue() != null;
                    Out<ValueInfo> valueInfo = new Out<>(); // PORT: §3.3 'ValueInfo valueInfo = null' passed as out
                    if (isConstant)
                    {
                        tryGetLiteralValueInfo(p.defaultValue(), valueInfo);
                    }

                    locals.add(new VariableSymbol(p.name(), type, isConstant, valueInfo.value));
                }
            }

            return ListExtensions.toReadOnly(locals);
        }
        finally
        {
            s_parameterListPool.returnToPool(argumentParameters);
        }
    }

    /// <summary>
    /// Builds an expanded declaration of the function customized given the arguments used at the call site.
    /// </summary>
    private static String getFunctionBodyText(Signature signature)
    {
        var body = DotNetStrings.trim(signature.body());

        if (!body.startsWith("{"))
            body = "{" + body;

        if (!body.endsWith("}"))
            body += "\n}";

        return body;
    }

    private static String getEntityGroupBodyText(Signature signature)
    {
        // translate to correct body form already handled by EntityGroupSymbol
        return signature.body();
    }

    private static SyntaxNode getUnboundBody(Signature signature)
    {
        if (signature.declaration() != null)
        {
            return signature.declaration().clone();
        }
        else if (signature.symbol() instanceof EntityGroupSymbol)
        {
            var text = getEntityGroupBodyText(signature);
            return QueryParser.parseEntityGroup(text);
        }
        else
        {
            var text = getFunctionBodyText(signature);
            return QueryParser.parseFunctionBody(text);
        }
    }

    private LocalScope getOuterScope(Signature signature)
    {
        if (signature.declaration() != null
            && signature.declaration().parent() instanceof FunctionDeclaration fd
            && _staticScopes.containsKey(fd)) // PORT: §3.3 TryGetValue
        {
            return _staticScopes.get(fd);
        }

        return null;
    }

    private static TypeSymbol getRepresentativeType(Parameter parameter)
    {
        switch (parameter.typeKind())
        {
            case Declared:
                return parameter.declaredTypes().get(0);
            case Tabular:
                return TableSymbol.Empty;
            default:
                return ScalarTypes.Dynamic;
        }
    }

    @Internal
    public FunctionBodyFacts getOrComputeFunctionBodyFacts(Signature signature, SyntaxNode body)
    {
        Out<FunctionBodyFacts> facts = new Out<>(); // PORT: §3.3 out var
        if (!tryGetFunctionBodyFacts(signature, facts))
        {
            facts.value = computeFunctionBodyFacts(signature, body);
            setFunctionBodyFacts(signature, facts.value);
        }

        return facts.value;
    }

    @Internal
    public boolean tryGetFunctionBodyFacts(Signature signature, Out<FunctionBodyFacts> facts) // PORT: §3.3 out
    {
        if (_globalBindingCache.DatabaseFunctionBodyFacts.tryGetValue(signature, facts))
            return true;

        // PORT: §3.3 Dictionary.TryGetValue(k, out v): stored facts are never null
        facts.value = _localBindingCache.NonDatabaseFunctionBodyFacts.get(signature);
        if (facts.value != null)
            return true;

        facts.value = null;
        return false;
    }

    @Internal
    public void setFunctionBodyFacts(Signature signature, FunctionBodyFacts facts)
    {
        if (isDatabaseSymbolSignature(signature))
        {
            _globalBindingCache.DatabaseFunctionBodyFacts.addOrUpdate(signature, facts);
        }
        else
        {
            _localBindingCache.NonDatabaseFunctionBodyFacts.put(signature, facts);
        }
    }

    private FunctionBodyFacts computeFunctionBodyFacts(Signature signature, SyntaxNode body)
    {
        Out<FunctionBodyFacts> result = new Out<>(FunctionBodyFacts.Default); // PORT: §3.8 local captured and mutated by the walk lambdas
        var isTabular = getBodyResultType(body) instanceof TableSymbol;

        // if the function returns a table, mark all tabular parameters as dependent
        // signature.Parameters.Any(p => p.IsTabular)
        List<Parameter> tabularParameters = new ArrayList<>(); // PORT: §3.6 Any(...) + Where(...) as one loop
        for (Parameter tp : signature.parameters())
        {
            if (tp.isTabular())
                tabularParameters.add(tp);
        }

        if (isTabular && tabularParameters.size() > 0)
        {
            result.value = result.value.addDependentParameters(tabularParameters);
        }

        var argParams = s_parameterListPool.allocateFromPool();

        // identify dependent parameters and other function body facts
        SyntaxElement.walkNodes(
            body,
            node -> // PORT: §3.12 fnBefore:
            {
                if (node instanceof FunctionCallExpression fc
                    && isSymbolLookupFunction(fc.referencedSymbol()))
                {
                    var isUnqualifiedTableCall = false;

                    if (fc.referencedSymbol() == Functions.Table)
                    {
                        // distinguish between database(d).table(t) vs just table(t)
                        // since table(t) can see local variables from dynamic scope
                        if (fc.parent() instanceof PathExpression p && p.selector() == fc)
                        {
                            result.value = result.value.withHasQualifiedTableCall(true);
                        }
                        else
                        {
                            // unqualified table calls (even with literal arguments) can be dependent on the call site since
                            // the names can reference tabular variables in outer scopes
                            result.value = result.value.withHasUnqualifiedTableCall(true);
                            isUnqualifiedTableCall = true;
                        }
                    }
                    else if (fc.referencedSymbol() == Functions.ExternalTable)
                    {
                        result.value = result.value.withHasExternalTableCall(true);
                    }
                    else if (fc.referencedSymbol() == Functions.MaterializedView)
                    {
                        result.value = result.value.withHasMaterializedViewCall(true);
                    }
                    else if (fc.referencedSymbol() == Functions.Database)
                    {
                        result.value = result.value.withHasDatabaseCall(true);
                    }
                    else if (fc.referencedSymbol() == Functions.Cluster)
                    {
                        result.value = result.value.withHasClusterCall(true);
                    }
                    else if (fc.referencedSymbol() == Functions.EntityGroup
                        && fc.getCalledFunctionFacts() != null) // PORT: §3.15 'is FunctionBodyFacts egCallFacts' on a FunctionBodyFacts-typed call is a null test
                    {
                        FunctionBodyFacts egCallFacts = fc.getCalledFunctionFacts();

                        // get facts from analysis of the entity-group definition
                        result.value = result.value.combineCalledFunction(egCallFacts);
                    }
                    else if (fc.referencedSymbol() == Functions.StoredQueryResult)
                    {
                        result.value = result.value.withHasStoredQueryResultCall(true);
                    }
                    else if (fc.referencedSymbol() == Functions.Graph)
                    {
                        result.value = result.value.withHasGraphCall(true);
                    }

                    // Any reference to an enclosing function's parameter in arguments is a dependent parameter.
                    // Some arguments may not affect the result type, but err on the side of caution.
                    for (int i = 0; i < fc.argumentList().expressions().size(); i++)
                    {
                        var arg = fc.argumentList().expressions().get(i).element();
                        result.value = addReferencedParametersAsDependentParameters(result.value, signature, arg);
                    }

                    // Unqualified table calls are additionally problematic, since they may refer to local table variables too
                    // and we are not doing analysis of all local variable source expressions and how they enter the enclosing function.
                    if (isUnqualifiedTableCall
                        && isTabular
                        && fc.argumentList().expressions().size() > 0
                        && fc.argumentList().expressions().get(0).element().constantValueInfo() != null // PORT: §3.14 ?.
                        && fc.argumentList().expressions().get(0).element().constantValueInfo().value() instanceof String tableName)
                    {
                        result.value = result.value.addUnqualifiedTableName(tableName);
                    }
                }
                else if (node instanceof ProjectByNamesOperator proj)
                {
                    // expressions of project-by-names operator alter the result schema,
                    // so any dependency of one of these on a function parameter means the result schema
                    // might be dependent on that parameter too.
                    result.value = addReferencedParametersAsDependentParameters(result.value, signature, proj);
                }
                else if (
                    node instanceof Expression ex
                    && ex.referencedSignature() != null // PORT: §3.15 'is Signature sig' on a Signature-typed property is a null test
                    && !isSymbolLookupFunction(ex.referencedSignature().symbol()))
                {
                    var facts = getFunctionBodyFacts(ex);
                    result.value = result.value.combineCalledFunction(facts);

                    // translate dependent parameters from the called function to parameters of the calling function
                    if (facts.dependentParameters().size() > 0
                        && ex instanceof FunctionCallExpression fcall
                        && isTabular)
                    {
                        argParams.clear();
                        getArgumentParameters(fcall, argParams);

                        for (int i = 0; i < fcall.argumentList().expressions().size(); i++)
                        {
                            // if this argument corresponds to a dependent parameter of the called function
                            // then any parameter referenced in the argument must also be dependent
                            var arg = fcall.argumentList().expressions().get(i).element();
                            if (facts.dependentParameters().contains(argParams.get(i)))
                            {
                                // note: these arguments should be constrained to only constant expressions due to requirements of symbol lookup functions.
                                result.value = addReferencedParametersAsDependentParameters(result.value, signature, arg);
                            }
                        }
                    }
                }
            },
            node -> // PORT: §3.12 fnAfter:
            {
                if (node.alternates() != null)
                {
                    for (SyntaxNode alt : node.alternates())
                    {
                        var facts = computeFunctionBodyFacts(signature, alt);
                        result.value = result.value.combineCalledFunction(facts);
                    }
                }
            },
            node -> // PORT: §3.12 fnDescend:
                node == body
                || (!(node instanceof FunctionDeclaration) && !(node instanceof FunctionBody))
            );

        s_parameterListPool.returnToPool(argParams);

        TypeSymbol nonVariableReturnType = null;
        if (!result.value.hasVariableReturnType())
        {
            TypeSymbol bodyResultType = getBodyResultType(body);
            nonVariableReturnType = bodyResultType != null ? bodyResultType : ErrorSymbol.Instance; // PORT: §3.14 ??
        }

        result.value = result.value.withNonVariableReturnType(nonVariableReturnType);

        var hasSyntaxErrors = hasSyntaxErrors(body);
        result.value = result.value.withHasSyntaxErrors(hasSyntaxErrors);

        return result.value;
    }

    /// <summary>
    /// Adds all referenced parameters of the specified signature found in the node sub-tree to 
    /// the <see cref="FunctionBodyFacts"/> dependent parameters list.
    /// </summary>
    private static FunctionBodyFacts addReferencedParametersAsDependentParameters(FunctionBodyFacts facts, Signature signature, SyntaxNode root)
    {
        var referencedParams = s_parameterListPool.allocateFromPool();
        try
        {
            getReferencedParameters(signature, root, referencedParams);
            facts = facts.addDependentParameters(referencedParams);
        }
        finally
        {
            s_parameterListPool.returnToPool(referencedParams);
        }

        return facts;
    }


    /// <summary>
    /// Gets all referenced parameters in the expression sub-tree
    /// </summary>
    private static void getReferencedParameters(Signature signature, SyntaxNode root, List<Parameter> parameters)
    {
        // look for all expressions that refer to a parameter or variable
        SyntaxElement.walkNodes(
            root,
            node ->
            {
                if (node instanceof SimpleNamedExpression sne)
                    node = sne.expression();

                if (node instanceof Expression expression)
                {
                    // PORT: §3.15 switch on type patterns over a possibly-null value: if/else keeps the C# fall-through-on-null behaviour
                    Symbol referencedSymbol = expression.referencedSymbol();
                    if (referencedSymbol instanceof ParameterSymbol p)
                    {
                        Parameter sparam = signature.getParameter(p.name());
                        if (sparam != null) // PORT: §3.15 'is Parameter sparam' on a Parameter-typed call is a null test
                        {
                            parameters.add(sparam);
                        }
                    }
                    else if (referencedSymbol instanceof VariableSymbol v)
                    {
                        if (v.source() != null)
                        {
                            if (v.source().tree() != expression.tree())
                            {
                                // variable is declared in different tree than its source expression,
                                // this indicates this is a fake variable being used in place of a parameter symbol
                                // for expansion binding to carry constant values.
                                Parameter vparam = signature.getParameter(v.name());
                                if (vparam != null) // PORT: §3.15 null test
                                {
                                    parameters.add(vparam);
                                }
                            }
                            else
                            {
                                // follow reference w/in same tree 
                                getReferencedParameters(signature, v.source(), parameters);
                            }
                        }
                    }
                }
            });
    }

    // PORT: §3.20 '#if false' block (GetReferencedParameter) is not compiled upstream; dropped

    private static boolean isSymbolLookupFunction(Symbol symbol)
    {
        return symbol == Functions.Table
            || symbol == Functions.ExternalTable
            || symbol == Functions.MaterializedView
            || symbol == Functions.EntityGroup
            || symbol == Functions.StoredQueryResult
            || symbol == Functions.Database
            || symbol == Functions.Cluster
            || symbol == Functions.Graph;
    }

    /// <summary>
    /// Gets the <see cref="FunctionBodyFacts"/> for the function invocation
    /// </summary>
    private FunctionBodyFacts getFunctionBodyFacts(Expression expr)
    {
        Signature signature = expr.referencedSignature();
        if (signature != null) // PORT: §3.15 'is Signature signature' on a Signature-typed property is a null test
        {
            Out<FunctionBodyFacts> funFacts = new Out<>(); // PORT: §3.3 out var
            if (!tryGetFunctionBodyFacts(signature, funFacts)
                && signature.returnKind() == ReturnTypeKind.Computed)
            {
                if (expr instanceof FunctionCallExpression functionCall)
                {
                    var arguments = s_expressionListPool.allocateFromPool();
                    var argumentTypes = s_typeListPool.allocateFromPool();

                    try
                    {
                        getArgumentsAndTypes(functionCall, arguments, argumentTypes);
                        getComputedFunctionCallResult(signature, arguments, argumentTypes);
                    }
                    finally
                    {
                        s_expressionListPool.returnToPool(arguments);
                        s_typeListPool.returnToPool(argumentTypes);
                    }
                }
                else if (expr instanceof NameReference)
                {
                    getComputedFunctionCallResult(signature, EmptyReadOnlyList.<Expression>instance(), EmptyReadOnlyList.<TypeSymbol>instance());
                }

                // try again
                tryGetFunctionBodyFacts(signature, funFacts);
            }

            return funFacts.value != null ? funFacts.value : FunctionBodyFacts.Default; // PORT: §3.14 ??
        }

        return FunctionBodyFacts.Default;
    }

    // ===== upstream part: Binder_Misc.cs =====
    // region Semantic Info accessors
    private void setSemanticInfo(SyntaxNode node, SemanticInfo info)
    {
        if (node != null)
        {
            if (_semanticInfoSetter != null) // PORT: §3.14 ?.
            {
                _semanticInfoSetter.accept(node, info);
            }
        }
    }

    private static TypeSymbol getResultTypeOrError(Expression expression)
    {
        // PORT: §3.14 expression?.ResultType ?? ErrorSymbol.Instance
        TypeSymbol resultType = expression != null ? expression.resultType() : null;
        return resultType != null ? resultType : ErrorSymbol.Instance;
    }

    private static TypeSymbol getResultType(Expression expression)
    {
        return expression != null ? expression.resultType() : null; // PORT: §3.14 ?.
    }

    private static Symbol getReferencedSymbol(Expression expression)
    {
        return expression != null ? expression.referencedSymbol() : null; // PORT: §3.14 ?.
    }

    private static boolean getIsConstant(Expression expression)
    {
        return expression != null ? expression.isConstant() : false; // PORT: §3.14 ?. ??
    }
    // endregion

    // region Symbol access/caching

    /// <summary>
    /// The set of open cluster symbols so far.
    /// This set is accumulated as the binder processes the query in lexical order.
    /// </summary>
    private Map<String, ClusterSymbol> _openClusters; // PORT: §3.17 Dictionary → Map (LinkedHashMap)

    /// <summary>
    /// Gets or creates an open cluster symbol of the given name.
    /// This is used when a cluster('...') call does not map to a known cluster.
    /// </summary>
    private ClusterSymbol getOpenCluster(String name)
    {
        if (_openClusters == null)
        {
            _openClusters = new LinkedHashMap<String, ClusterSymbol>();
        }

        var cluster = _openClusters.get(name); // PORT: §3.3 TryGetValue (values never null)
        if (cluster == null)
        {
            cluster = new ClusterSymbol(name, (Iterable<DatabaseSymbol>) null, true); // PORT: §3.12 named argument isOpen
            DotNet.dictionaryAdd(_openClusters, name, cluster); // PORT: §3.17 Dictionary.Add
        }

        return cluster;
    }

    /// <summary>
    /// A map between an open cluster symbol and the set of inferred open databases so far.
    /// This set is accumulated as the binder processes the query in lexical order.
    /// </summary>
    private Map<ClusterSymbol, Map<String, DatabaseSymbol>> _openDatabases; // PORT: §3.17 Dictionary → Map (LinkedHashMap)

    /// <summary>
    /// Gets or creates an open database symbol of the given name.
    /// This is primarily used for database('...') of an unknown database within an open cluster.
    /// </summary>
    private DatabaseSymbol getOpenDatabase(String name, ClusterSymbol cluster)
    {
        cluster = cluster != null ? cluster : _currentCluster; // PORT: §3.14 ??

        if (_openDatabases == null)
        {
            _openDatabases = new LinkedHashMap<ClusterSymbol, Map<String, DatabaseSymbol>>();
        }

        var map = _openDatabases.get(cluster); // PORT: §3.3 TryGetValue (values never null)
        if (map == null)
        {
            map = new LinkedHashMap<String, DatabaseSymbol>();
            DotNet.dictionaryAdd(_openDatabases, cluster, map); // PORT: §3.17 Dictionary.Add
        }

        var database = map.get(name); // PORT: §3.3 TryGetValue (values never null)
        if (database == null)
        {
            database = new DatabaseSymbol(name, (Iterable<Symbol>) null, true); // PORT: §3.12 named argument isOpen
            DotNet.dictionaryAdd(map, name, database); // PORT: §3.17 Dictionary.Add
        }

        return database;
    }

    /// <summary>
    /// A map between an open database symbol and the set of inferred open tables identified so far.
    /// This set is accumulated as the binder processes the query in lexical order.
    /// </summary>
    private Map<DatabaseSymbol, Map<String, TableSymbol>> _openTables; // PORT: §3.17 Dictionary → Map (LinkedHashMap)

    /// <summary>
    /// Gets or creates an open table symbol of the given name.
    /// This is primarily used for db.Table or db.table('...') of an unknown table within an open database.
    /// </summary>
    private TableSymbol getOpenTable(String name, DatabaseSymbol database)
    {
        if (_openTables == null)
        {
            _openTables = new LinkedHashMap<DatabaseSymbol, Map<String, TableSymbol>>();
        }

        var map = _openTables.get(database); // PORT: §3.3 TryGetValue (values never null)
        if (map == null)
        {
            map = new LinkedHashMap<String, TableSymbol>();
            DotNet.dictionaryAdd(_openTables, database, map); // PORT: §3.17 Dictionary.Add
        }

        var table = map.get(name); // PORT: §3.3 TryGetValue (values never null)
        if (table == null)
        {
            table = new TableSymbol(name).withIsOpen(true);
            DotNet.dictionaryAdd(map, name, table); // PORT: §3.17 Dictionary.Add
        }

        return table;
    }

    /// <summary>
    /// A map between an open table and the set of inferred columns (so far)
    /// This set is accumulated as the binder processes the query in lexical order.
    /// </summary>
    private Map<TableSymbol, Map<String, ColumnSymbol>> _openTableInferredColumns; // PORT: §3.17 Dictionary → Map (LinkedHashMap)

    /// <summary>
    /// Gets or creates an inferred column symbol and associates it with the specified open table.
    /// </summary>
    private ColumnSymbol getOpenTableInferredColumn(String name, TableSymbol table)
    {
        if (_openTableInferredColumns == null)
        {
            _openTableInferredColumns = new LinkedHashMap<TableSymbol, Map<String, ColumnSymbol>>();
        }

        var columnMap = _openTableInferredColumns.get(table); // PORT: §3.3 TryGetValue (values never null)
        if (columnMap == null)
        {
            columnMap = new LinkedHashMap<String, ColumnSymbol>();
            DotNet.dictionaryAdd(_openTableInferredColumns, table, columnMap); // PORT: §3.17 Dictionary.Add
        }

        var column = columnMap.get(name); // PORT: §3.3 TryGetValue (values never null)
        if (column == null)
        {
            column = new ColumnSymbol(name, ScalarTypes.Unknown);
            DotNet.dictionaryAdd(columnMap, name, column); // PORT: §3.17 Dictionary.Add
        }

        return column;
    }

    /// <summary>
    /// Gets all the declared or inferred columns for the specified table.
    /// </summary>
    private void getDeclaredAndInferredColumns(TableSymbol table, List<ColumnSymbol> columns)
    {
        columns.addAll(table.columns());

        if (table.isOpen() && _openTableInferredColumns != null)
        {
            var columnMap = _openTableInferredColumns.get(table); // PORT: §3.3 TryGetValue (values never null)
            if (columnMap != null)
            {
                columns.addAll(columnMap.values());
            }
        }
    }

    /// <summary>
    /// Gets all the declared or inferred columns for the specified table.
    /// </summary>
    public List<ColumnSymbol> getDeclaredAndInferredColumns(TableSymbol table) // PORT: §3.17 IReadOnlyList<T> → List<T>
    {
        if (table == null)
        {
            return EmptyReadOnlyList.<ColumnSymbol>instance();
        }
        else if (table.isOpen() && _openTableInferredColumns != null && _openTableInferredColumns.containsKey(table))
        {
            var list = new ArrayList<ColumnSymbol>();
            getDeclaredAndInferredColumns(table, list);
            return list;
        }
        else
        {
            return table.columns();
        }
    }

    /// <summary>
    /// Gets the declared or inferred column of the given name for the specified table.
    /// If the column is not declared and the table is open, a new column is inferred with the given name.
    /// </summary>
    public boolean tryGetDeclaredOrInferredColumn(TableSymbol table, String name, Out<ColumnSymbol> column) // PORT: §3.3 out
    {
        if (table.tryGetColumn(name, column))
        {
            return true;
        }
        else if (table.isOpen())
        {
            column.value = getOpenTableInferredColumn(name, table);
            return true;
        }
        else
        {
            column.value = null;
            return false;
        }
    }

    private Map<TableSymbol, TupleSymbol> _tupleMap; // PORT: §3.17 Dictionary → Map (LinkedHashMap)

    /// <summary>
    /// Gets a tuple with the same columns (declared and inferred) as the table.
    /// </summary>
    private TupleSymbol getTuple(TableSymbol table)
    {
        if (_tupleMap == null)
        {
            _tupleMap = new LinkedHashMap<TableSymbol, TupleSymbol>();
        }

        var tuple = _tupleMap.get(table); // PORT: §3.3 TryGetValue (values never null)
        if (tuple == null)
        {
            tuple = new TupleSymbol(table.columns(), table);
            DotNet.dictionaryAdd(_tupleMap, table, tuple); // PORT: §3.17 Dictionary.Add
        }

        return tuple;
    }

    /// <summary>
    /// Returns true if the list of tables can be cached globally (tied by global state cache)
    /// </summary>
    private boolean canGlobalCache(List<TableSymbol> tables) // PORT: §3.17 IReadOnlyList<T> → List<T>
    {
        // if this is the list of tables from the current database definition, then okay.
        // otherwise if its a list of stricly database tables then allow
        //     (note: this still may cause excessive caching if queries have lots of queries with joins,unions of strictly database tables)
        if (tables == _currentDatabase.tables()) // PORT: §3.14 reference equality
            return true;

        // tables.All(t => _globals.IsDatabaseTable(t))
        for (TableSymbol t : tables) // PORT: §3.6 hot path loop
        {
            if (!_globals.isDatabaseTable(t))
                return false;
        }

        return true;
    }

    /// <summary>
    /// A table that contains all the columns in the specified list of tables, unified on name.
    /// </summary>
    private TableSymbol getTableOfColumnsUnifiedByName(List<TableSymbol> tables) // PORT: §3.17 IReadOnlyList<T> → List<T>
    {
        // consider making this cache thread safe
        var unifiedColumnsTableOut = new Out<TableSymbol>(); // PORT: §3.3 out
        if (!_globalBindingCache.UnifiedNameColumnsMap.tryGetValue(tables, unifiedColumnsTableOut))
        {
            var canCache = canGlobalCache(tables);

            tables = ListExtensions.toReadOnly(tables);
            var columns = new ArrayList<ColumnSymbol>();

            for (TableSymbol table : tables)
            {
                columns.addAll(table.columns());
            }

            Binder.unifyColumnsWithSameName(columns);

            // tables.Any(t => t.IsOpen)
            boolean anyOpen = false; // PORT: §3.6 hot path loop
            for (TableSymbol t : tables)
            {
                if (t.isOpen())
                {
                    anyOpen = true;
                    break;
                }
            }

            unifiedColumnsTableOut.value = new TableSymbol(columns).withIsOpen(anyOpen);

            if (canCache)
            {
                _globalBindingCache.UnifiedNameColumnsMap.addOrUpdate(tables, unifiedColumnsTableOut.value);
            }
        }

        return unifiedColumnsTableOut.value;
    }

    /// <summary>
    /// A table that contains all the columns in the specified list of tables, unified on name and type.
    /// </summary>
    private TableSymbol getTableOfColumnsUnifiedByNameAndType(List<TableSymbol> tables) // PORT: §3.17 IReadOnlyList<T> → List<T>
    {
        // consider making this cache thread safe
        var unifiedColumnsTableOut = new Out<TableSymbol>(); // PORT: §3.3 out
        if (!_globalBindingCache.UnifiedNameAndTypeColumnsMap.tryGetValue(tables, unifiedColumnsTableOut))
        {
            var canCache = canGlobalCache(tables);

            tables = ListExtensions.toReadOnly(tables);
            var columns = new ArrayList<ColumnSymbol>();

            for (TableSymbol table : tables)
            {
                columns.addAll(table.columns());
            }

            Binder.unifyColumnsWithSameNameAndType(columns);

            // tables.Any(t => t.IsOpen)
            boolean anyOpen = false; // PORT: §3.6 hot path loop
            for (TableSymbol t : tables)
            {
                if (t.isOpen())
                {
                    anyOpen = true;
                    break;
                }
            }

            unifiedColumnsTableOut.value = new TableSymbol(columns).withIsOpen(anyOpen);

            if (canCache)
            {
                _globalBindingCache.UnifiedNameAndTypeColumnsMap.addOrUpdate(tables, unifiedColumnsTableOut.value);
            }
        }

        return unifiedColumnsTableOut.value;
    }

    /// <summary>
    /// A table that contains the common columns in the specified list of tables.
    /// </summary>
    private TableSymbol getTableOfCommonColumns(List<TableSymbol> tables) // PORT: §3.17 IReadOnlyList<T> → List<T>
    {
        // consider making this cache thread safe
        var commonColumnsTableOut = new Out<TableSymbol>(); // PORT: §3.3 out
        if (!_globalBindingCache.CommonColumnsMap.tryGetValue(tables, commonColumnsTableOut))
        {
            var canCache = canGlobalCache(tables);

            tables = ListExtensions.toReadOnly(tables);
            var columns = new ArrayList<ColumnSymbol>();

            Binder.getCommonColumns(tables, columns);

            commonColumnsTableOut.value = new TableSymbol(columns);

            // since these are the common columns, open columns can only exist if all tables are open
            // tables.Count > 0 && tables.All(t => t.IsOpen)
            boolean allOpen = tables.size() > 0; // PORT: §3.6 hot path loop
            if (allOpen)
            {
                for (TableSymbol t : tables)
                {
                    if (!t.isOpen())
                    {
                        allOpen = false;
                        break;
                    }
                }
            }

            if (allOpen)
            {
                commonColumnsTableOut.value = commonColumnsTableOut.value.withIsOpen(true);
            }

            if (canCache)
            {
                _globalBindingCache.CommonColumnsMap.addOrUpdate(tables, commonColumnsTableOut.value);
            }
        }

        return commonColumnsTableOut.value;
    }

    /// <summary>
    /// Gets the <see cref="GraphSymbol"/> from the given <paramref name="node"/>.
    /// </summary>
    private static GraphSymbol getGraphSymbol(SyntaxNode node)
    {
        // There are two valid configurations:
        //
        // 1. In a regular expression, the graph will be the result of the expression (left-side)
        //    of a pipe expression.
        //
        // 2. In a partitioned make-graph subquery, the node will have a MakeGraphPartitionedByClause
        //    node as one of its parents, which contains the graph symbol.

        var pe = node.parent() instanceof PipeExpression pex ? pex : null; // PORT: §3.15 as
        if (pe != null && pe.expression().resultType() instanceof GraphSymbol pegs) // PORT: §3.14 ?.
        {
            return pegs;
        }

        var current = node;
        while (current != null && !(current instanceof MakeGraphPartitionedByClause)) {
            current = current.parent() instanceof MakeGraphPartitionedByClause mgpb ? mgpb : current.parent();
        }

        if (current != null && current.parent() instanceof MakeGraphOperator mg && mg.resultType() instanceof GraphSymbol mggs) // PORT: §3.14 ?.
        {
            return mggs;
        }

        return null;
    }
    // endregion

    // region Common definitions
    private static final ObjectPool<List<Symbol>> s_symbolListPool =
        new ObjectPool<List<Symbol>>(() -> new ArrayList<Symbol>(), list -> list.clear());

    private static final ObjectPool<LinkedHashSet<Symbol>> s_symbolHashSetPool = // PORT: §3.17 HashSet → LinkedHashSet
        new ObjectPool<LinkedHashSet<Symbol>>(() -> new LinkedHashSet<Symbol>(), list -> list.clear());

    private static final ObjectPool<List<Diagnostic>> s_diagnosticListPool =
        new ObjectPool<List<Diagnostic>>(() -> new ArrayList<Diagnostic>(), list -> list.clear());

    private static final ObjectPool<List<ColumnSymbol>> s_columnListPool =
        new ObjectPool<List<ColumnSymbol>>(() -> new ArrayList<ColumnSymbol>(), list -> list.clear());

    private static final ObjectPool<List<TableSymbol>> s_tableListPool =
        new ObjectPool<List<TableSymbol>>(() -> new ArrayList<TableSymbol>(), list -> list.clear());

    private static final ObjectPool<List<FunctionSymbol>> s_functionListPool =
        new ObjectPool<List<FunctionSymbol>>(() -> new ArrayList<FunctionSymbol>(), list -> list.clear());

    private static final ObjectPool<List<Signature>> s_signatureListPool =
        new ObjectPool<List<Signature>>(() -> new ArrayList<Signature>(), list -> list.clear());

    private static final ObjectPool<List<PatternSignature>> s_patternListPool =
        new ObjectPool<List<PatternSignature>>(() -> new ArrayList<PatternSignature>(), list -> list.clear());

    private static final ObjectPool<List<Expression>> s_expressionListPool =
        new ObjectPool<List<Expression>>(() -> new ArrayList<Expression>(), list -> list.clear());

    private static final ObjectPool<List<TypeSymbol>> s_typeListPool =
        new ObjectPool<List<TypeSymbol>>(() -> new ArrayList<TypeSymbol>(), list -> list.clear());

    private static final ObjectPool<List<String>> s_stringListPool =
        new ObjectPool<List<String>>(() -> new ArrayList<String>(), list -> list.clear());

    private static final ObjectPool<LinkedHashSet<String>> s_stringSetPool = // PORT: §3.17 HashSet → LinkedHashSet
        new ObjectPool<LinkedHashSet<String>>(() -> new LinkedHashSet<String>(), s -> s.clear());

    private static final ObjectPool<UniqueNameTable> s_uniqueNameTablePool =
        new ObjectPool<UniqueNameTable>(() -> new UniqueNameTable(), t -> t.clear());

    private static final ObjectPool<ProjectionBuilder> s_projectionBuilderPool =
        new ObjectPool<ProjectionBuilder>(() -> new ProjectionBuilder(), b -> b.clear());

    private static final ObjectPool<List<Parameter>> s_parameterListPool =
        new ObjectPool<List<Parameter>>(() -> new ArrayList<Parameter>(), list -> list.clear());

    private static final ObjectPool<List<Object>> s_objectListPool =
        new ObjectPool<List<Object>>(() -> new ArrayList<Object>(), list -> list.clear());

    private static final ObjectPool<LinkedHashMap<String, Integer>> s_stringToIntMapPool = // PORT: §3.17 Dictionary → LinkedHashMap
        new ObjectPool<LinkedHashMap<String, Integer>>(() -> new LinkedHashMap<String, Integer>(), m -> m.clear());

    private static final SemanticInfo LiteralBoolInfo = new SemanticInfo(ScalarTypes.Bool, null, true); // PORT: §3.12 named argument isConstant
    private static final SemanticInfo LiteralIntInfo = new SemanticInfo(ScalarTypes.Int, null, true); // PORT: §3.12 named argument isConstant
    private static final SemanticInfo LiteralLongInfo = new SemanticInfo(ScalarTypes.Long, null, true); // PORT: §3.12 named argument isConstant
    private static final SemanticInfo LiteralRealInfo = new SemanticInfo(ScalarTypes.Real, null, true); // PORT: §3.12 named argument isConstant
    private static final SemanticInfo LiteralDecimalInfo = new SemanticInfo(ScalarTypes.Decimal, null, true); // PORT: §3.12 named argument isConstant
    private static final SemanticInfo LiteralStringInfo = new SemanticInfo(ScalarTypes.String, null, true); // PORT: §3.12 named argument isConstant
    private static final SemanticInfo LiteralDateTimeInfo = new SemanticInfo(ScalarTypes.DateTime, null, true); // PORT: §3.12 named argument isConstant
    private static final SemanticInfo LiteralTimeSpanInfo = new SemanticInfo(ScalarTypes.TimeSpan, null, true); // PORT: §3.12 named argument isConstant
    private static final SemanticInfo LiteralGuidInfo = new SemanticInfo(ScalarTypes.Guid, null, true); // PORT: §3.12 named argument isConstant
    private static final SemanticInfo LiteralDynamicInfo = new SemanticInfo(ScalarTypes.Dynamic, null, true); // PORT: §3.12 named argument isConstant
    private static final SemanticInfo LiteralNullInfo = new SemanticInfo(ScalarTypes.Null, null, true); // PORT: §3.12 named argument isConstant
    private static final SemanticInfo UnknownInfo = new SemanticInfo(ScalarTypes.Unknown, null, true); // PORT: §3.12 named argument isConstant
    private static final SemanticInfo ErrorInfo = new SemanticInfo(ErrorSymbol.Instance);
    private static final SemanticInfo VoidInfo = new SemanticInfo(VoidSymbol.Instance);
    // endregion

    // region Declarations
    private void addLetDeclarationToScope(LocalScope scope, LetStatement statement, List<Diagnostic> diagnostics)
    {
        scope.addSymbol(getReferencedSymbol(statement.name()));
    }

    private void addLetDeclarationToScope(LocalScope scope, LetStatement statement) // PORT: §3.12 diagnostics = null
    {
        addLetDeclarationToScope(scope, statement, null);
    }

    private void addDeclarationsToLocalScope(SyntaxList1<SeparatedElement1<FunctionParameter>> declarations)
    {
        for (int i = 0, n = declarations.size(); i < n; i++)
        {
            var d = declarations.get(i).element();
            addDeclarationToLocalScope(d.nameAndType().name());
        }
    }

    // PORT: §2.5 AddDeclarationsToLocalScope(SyntaxList<SeparatedElement<NameAndTypeDeclaration>>) erases to the same signature
    private void addDeclarationsToLocalScopeOfParameters(SyntaxList1<SeparatedElement1<NameAndTypeDeclaration>> declarations)
    {
        for (int i = 0, n = declarations.size(); i < n; i++)
        {
            var d = declarations.get(i).element();
            addDeclarationToLocalScope(d.name());
        }
    }

    private void addDeclarationToLocalScope(SyntaxNode node)
    {
        if (node.referencedSymbol() instanceof Symbol s)
        {
            _localScope.addSymbol(s);
        }
    }

    private void bindParameterDeclarations(SyntaxList1<SeparatedElement1<FunctionParameter>> parameters)
    {
        for (int i = 0; i < parameters.size(); i++)
        {
            var p = parameters.get(i).element();
            bindParameterDeclaration(p);
        }
    }

    private void bindParameterDeclaration(FunctionParameter node)
    {
        bindParameterDeclaration(node.nameAndType());
    }

    // PORT: §2.5 BindParameterDeclarations(SyntaxList<SeparatedElement<NameAndTypeDeclaration>>) erases to the same signature
    private void bindParameterDeclarationsOfFunctionParameters(SyntaxList1<SeparatedElement1<NameAndTypeDeclaration>> parameters)
    {
        for (int i = 0; i < parameters.size(); i++)
        {
            var p = parameters.get(i).element();
            bindParameterDeclaration(p);
        }
    }

    private void bindParameterDeclaration(NameAndTypeDeclaration node)
    {
        var name = node.name().simpleName();
        var type = getTypeFromTypeExpression(node.type());

        if (!DotNetStrings.isNullOrEmpty(name))
        {
            var symbol = new ParameterSymbol(name, type);
            setSemanticInfo(node.name(), new SemanticInfo(symbol, type));
        }
    }

    private void bindParameterDeclarationsAsVariables(SyntaxList1<SeparatedElement1<FunctionParameter>> parameters)
    {
        for (int i = 0; i < parameters.size(); i++)
        {
            var p = parameters.get(i).element();
            bindParameterDeclarationAsVariable(p);
        }
    }

    private void bindParameterDeclarationAsVariable(FunctionParameter node)
    {
        var name = node.nameAndType().name().simpleName();
        var type = getTypeFromTypeExpression(node.nameAndType().type());

        if (!DotNetStrings.isNullOrEmpty(name))
        {
            // PORT: §3.14 node.DefaultValue?.Value?.IsConstant ?? false, node.DefaultValue?.Value?.ConstantValueInfo, node.DefaultValue?.Value
            var defaultValue = node.defaultValue();
            Expression defaultValueExpression = defaultValue != null ? defaultValue.value() : null;
            boolean defaultValueIsConstant = defaultValueExpression != null ? defaultValueExpression.isConstant() : false;
            ValueInfo defaultValueConstantValueInfo = defaultValueExpression != null ? defaultValueExpression.constantValueInfo() : null;
            var variable = new VariableSymbol(name, type, defaultValueIsConstant, defaultValueConstantValueInfo, defaultValueExpression);
            setSemanticInfo(node.nameAndType().name(), new SemanticInfo(variable, type));
        }
    }

    private void bindColumnDeclarations(SyntaxList1<SeparatedElement1<FunctionParameter>> parameters)
    {
        for (int i = 0; i < parameters.size(); i++)
        {
            var p = parameters.get(i).element();
            bindColumnDeclaration(p.nameAndType());
        }
    }

    // PORT: §2.5 BindColumnDeclarations(SyntaxList<SeparatedElement<NameAndTypeDeclaration>>) erases to the same signature
    private void bindColumnDeclarationsOfSchema(SyntaxList1<SeparatedElement1<NameAndTypeDeclaration>> parameters)
    {
        for (int i = 0; i < parameters.size(); i++)
        {
            var p = parameters.get(i).element();
            bindColumnDeclaration(p);
        }
    }

    private void bindColumnDeclaration(NameAndTypeDeclaration node)
    {
        var name = node.name().simpleName();
        var type = getTypeFromTypeExpression(node.type());

        if (!DotNetStrings.isNullOrEmpty(name))
        {
            var symbol = new ColumnSymbol(name, type);
            setSemanticInfo(node.name(), new SemanticInfo(symbol, type));
        }
    }

    private TupleSymbol getScanStepTuple(ScanOperator node)
    {
        var columns = s_columnListPool.allocateFromPool();
        try
        {
            getDeclaredAndInferredColumns(this.rowScopeOrEmpty(), columns);

            if (node.declareClause() != null)
            {
                for (var elem : node.declareClause().declarations())
                {
                    if (elem.element().nameAndType().name().referencedSymbol() instanceof ColumnSymbol c)
                    {
                        columns.add(c);
                    }
                }
            }

            return new TupleSymbol(columns, _rowScope); // PORT: §3.12 named argument relatedTable
        }
        finally
        {
            s_columnListPool.returnToPool(columns);
        }
    }

    private void bindStepDeclarations(ScanOperator node)
    {
        var stepTuple = getScanStepTuple(node);

        for (var step : node.steps())
        {
            var name = step.name().simpleName();
            var local = new VariableSymbol(name, stepTuple);
            setSemanticInfo(step.name(), new SemanticInfo(local, stepTuple));
        }
    }

    private void addStepDeclarationsToLocalScope(ScanOperator node)
    {
        var stepTuple = getScanStepTuple(node);

        for (var step : node.steps())
        {
            addDeclarationToLocalScope(step.name());
        }
    }

    private void bindGraphMatchPatternDeclarations(SyntaxNode operatorNode, SyntaxList1<SeparatedElement1<GraphMatchPattern>> patterns)
    {
        var graphScope = getGraphSymbol(operatorNode);
        var edgeTuple = graphScope != null ? new TupleSymbol(graphScope.edgeShape().columns(), graphScope.edgeShape()) : TupleSymbol.Empty;
        var nodeTuple = graphScope != null && graphScope.nodeShape() != null ? new TupleSymbol(graphScope.nodeShape().columns(), graphScope.nodeShape()) : TupleSymbol.Empty; // PORT: §3.14 ?.

        for (var pattern : patterns)
        {
            for (var notation : pattern.element().patternElements())
            {
                if (notation instanceof GraphMatchPatternNode node && node.name() != null)
                {
                    var local = new VariableSymbol(node.name().simpleName(), nodeTuple);
                    setSemanticInfo(node.name(), new SemanticInfo(local, nodeTuple));
                }
                else if (notation instanceof GraphMatchPatternEdge edge && edge.name() != null)
                {
                    var local = new VariableSymbol(edge.name().simpleName(), edgeTuple);
                    setSemanticInfo(edge.name(), new SemanticInfo(local, edgeTuple));
                }
            }
        }
    }

    private void addGraphMatchPatternDeclarationsToLocalScope(SyntaxList1<SeparatedElement1<GraphMatchPattern>> patterns)
    {
        for (var pattern : patterns)
        {
            for (var notation : pattern.element().patternElements())
            {
                if (notation instanceof GraphMatchPatternNode node && node.name() != null)
                {
                    addDeclarationToLocalScope(node.name());
                }
                else if (notation instanceof GraphMatchPatternEdge edge && edge.name() != null)
                {
                    addDeclarationToLocalScope(edge.name());
                }
            }
        }
    }

    // endregion

    // region Directives

    public void applyDirective(Directive directive, List<Diagnostic> diagnostics)
    {
        switch (directive.name())
        {
            case "connect", "database" -> // PORT: §3.14 switch on possibly-null string
            {
                applyConnectOrDatabaseDirective(directive, directive.token(), diagnostics);
            }

            case null, default -> // PORT: §3.14 switch on possibly-null string
            {
                if (!KustoFacts.Directives.contains(directive.name())
                    && diagnostics != null)
                {
                    diagnostics.add(DiagnosticFacts.getUnknownDirective(directive.name()).withLocation(directive.token().textStart() + 1, directive.name().length()));
                }
            }
        }
    }

    public void applyDirective(Directive directive) // PORT: §3.12 diagnostics = null
    {
        applyDirective(directive, null);
    }

    private void applyConnectOrDatabaseDirective(Directive directive, SyntaxElement location, List<Diagnostic> diagnostics)
    {
        var clusterName = new Out<String>(); // PORT: §3.3 out var
        var databaseName = new Out<String>(); // PORT: §3.3 out var
        if (tryGetDirectiveClusterAndDatabase(directive, clusterName, databaseName))
        {
            if (clusterName.value != null)
            {
                var knownCluster = _globals.getCluster(clusterName.value);
                _currentCluster = knownCluster != null ? knownCluster : ClusterSymbol.Unknown; // PORT: §3.14 ??

                if (_currentCluster == ClusterSymbol.Unknown && diagnostics != null && location != null)
                {
                    diagnostics.add(DiagnosticFacts.getNameDoesNotReferToAnyKnownCluster(clusterName.value).withSeverity(DiagnosticSeverity.Error).withLocation(location));
                }
            }

            if (databaseName.value != null)
            {
                var knownDatabase = _currentCluster.getDatabase(databaseName.value);
                _currentDatabase = knownDatabase != null ? knownDatabase : DatabaseSymbol.Unknown; // PORT: §3.14 ??

                if (_currentDatabase == DatabaseSymbol.Unknown && diagnostics != null && location != null)
                {
                    diagnostics.add(DiagnosticFacts.getNameDoesNotReferToAnyKnownDatabase(databaseName.value).withLocation(location));
                }
            }
        }
    }

    @Internal
    public static boolean tryGetDirectiveClusterAndDatabase(
        Directive directive, Out<String> clusterName, Out<String> databaseName) // PORT: §3.3 out
    {
        clusterName.value = null;
        databaseName.value = null;

        if (Objects.equals(directive.name(), "database"))
        {
            if (directive.arguments().size() > 1)
            {
                clusterName.value = getDirectiveArgumentStringValue(directive.arguments().get(0));
                databaseName.value = getDirectiveArgumentStringValue(directive.arguments().get(1));
                return true;
            }
            else if (directive.arguments().size() == 1)
            {
                var arg = getDirectiveArgumentStringValue(directive.arguments().get(0));
                var hostname = new Out<String>(); // PORT: §3.3 out var
                var path = new Out<String>(); // PORT: §3.3 out var
                KustoFacts.getHostAndPath(arg, hostname, path);
                if (hostname.value != null && path.value != null)
                {
                    clusterName.value = hostname.value;
                    databaseName.value = path.value;
                    return true;
                }
                else if (hostname.value != null)
                {
                    clusterName.value = null;
                    databaseName.value = hostname.value;
                    return true;
                }
            }
        }
        else if (Objects.equals(directive.name(), "connect") && directive.arguments().size() > 0)
        {
            var arg = directive.arguments().get(0);
            if (arg.text().startsWith("cluster"))
            {
                var end = new Out<Integer>(); // PORT: §3.3 out var
                var cluster = getStringValueAfterPrefix(arg.text(), "cluster(", 0, end);
                if (cluster != null)
                {
                    var path = new Out<String>(); // PORT: §3.3 out var
                    KustoFacts.getHostAndPath(cluster, clusterName, path);
                    var databaseNameAfterPrefix = getStringValueAfterPrefix(arg.text(), "database(", end.value, new Out<Integer>()); // PORT: §3.3 out _
                    databaseName.value = databaseNameAfterPrefix != null ? databaseNameAfterPrefix : path.value; // PORT: §3.14 ??
                    return clusterName.value != null;
                }
            }
            else if (!arg.text().startsWith("@"))
            {
                var connection = getDirectiveArgumentStringValue(arg);
                var info = ConnectionInfo.parse(connection);
                var dataSource = info.dataSource();
                var host = new Out<String>(); // PORT: §3.3 out var
                var path = new Out<String>(); // PORT: §3.3 out var
                KustoFacts.getHostAndPath(info.dataSource(), host, path);
                clusterName.value = host.value;
                databaseName.value = path.value;
                return true;
            }
        }

        return false;
    }

    @Internal
    public static String getStringValueAfterPrefix(String text, String prefix, int start, Out<Integer> end) // PORT: §3.3 out
    {
        var index = text.indexOf(prefix, start);
        if (index >= 0)
        {
            var wsLen = TokenParser.scanWhitespace(text, index + prefix.length());
            var stringStart = index + prefix.length() + wsLen;
            int len = TokenParser.scanStringLiteral(text, stringStart); // PORT: §3.7 `is int len` on non-nullable int
            if (len > 0)
            {
                var stringLiteral = text.substring(stringStart, stringStart + len);
                end.value = index + len;
                return KustoFacts.getStringLiteralValue(stringLiteral);
            }
        }
        end.value = start;
        return null;
    }

    @Internal
    public static String getDirectiveArgumentStringValue(ClientDirectiveArgument argument)
    {
        return argument.value() instanceof String s ? s : ""; // PORT: §3.15 as string ?? ""
    }

    private void applyConnectDirective(Directive directive, SyntaxElement location, List<Diagnostic> diagnostics)
    {
        // uses same logic as #database directive
        applyConnectOrDatabaseDirective(directive, location, diagnostics);
    }

    // endregion

    // region Other

    public static boolean hasDynamicPrimitives(List<? extends TypeSymbol> types) // PORT: §3.10 covariance
    {
        for (var type : types)
        {
            if (type instanceof DynamicPrimitiveSymbol)
                return true;
        }

        return false;
    }

    public static void getUnwrappedDynamicPrimitives(List<? extends TypeSymbol> types, List<TypeSymbol> unwrapped) // PORT: §3.10 covariance
    {
        for (var type : types)
        {
            unwrapped.add(
                type instanceof DynamicPrimitiveSymbol dp
                    ? dp.underlyingType()
                    : type);
        }
    }

    /// <summary>
    /// Finds the <see cref="EntityGroupElementSymbol"/> associated with the location.
    /// </summary>
    public static EntityGroupElementSymbol getMacroExpandScope(SyntaxNode location)
    {
        PathExpression path = location instanceof PathExpression lpe ? lpe : null; // PORT: §3.15 as

        if (location instanceof NameReference nr)
        {
            if (nr.parent() instanceof FunctionCallExpression fc
                && fc.parent() instanceof PathExpression fcpe)
            {
                path = fcpe;
            }
            else if (nr.parent() instanceof PathExpression pe
                    && pe.selector() == nr)
            {
                path = pe;
            }
        }

        Symbol symbol = null;

        if (path != null)
        {
            symbol = path.expression().referencedSymbol();
        }

        if (symbol instanceof VariableSymbol vs)
        {
            symbol = vs.type();
        }

        return symbol instanceof EntityGroupElementSymbol egs ? egs : null; // PORT: §3.15 as
    }

    /// <summary>
    /// Gets the <see cref="ScopeKind"/> in effect for all of a function's arguments.
    /// </summary>
    private ScopeKind getArgumentScope(FunctionCallExpression fc, ScopeKind outerScope)
    {
        if (getReferencedSymbol(fc.name()) instanceof FunctionSymbol fs
            && _globals.isAggregateFunction(fs))
        {
            // aggregate function arguments are always normal
            return ScopeKind.Normal;
        }
        else if (outerScope == ScopeKind.Aggregate)
        {
            // if the function is not a known aggregate then keep aggregate scope as there may be
            // aggregates nested in the function arguments
            return ScopeKind.Aggregate;
        }
        else
        {
            return ScopeKind.Normal;
        }
    }

    /// <summary>
    /// Gets the <see cref="ScopeKind"/> in effect for a function's specific argument.
    /// </summary>
    private ScopeKind getArgumentScope(FunctionCallExpression fc, int position, ScopeKind outerScope)
    {
        var fs = fc.name().referencedSymbol() instanceof FunctionSymbol fsx ? fsx : null; // PORT: §3.15 as
        var sig = fc.name().referencedSignature();

        if (fs != null)
        {
            if (_globals.isAggregateFunction(fs))
            {
                // aggregate function arguments are always normal
                return ScopeKind.Normal;
            }
            else if ((sig != null && sig.hasAggregateParameters()) || (sig == null && hasAggregateParameters(fs)))
            {
                // if the specific argument may be an aggregate then use aggregate scoping
                var possibleParameters = s_parameterListPool.allocateFromPool();
                getPossibleArgumentParameters(fc, position, possibleParameters);
                // possibleParameters.Any(pp => pp.ArgumentKind == ArgumentKind.Aggregate)
                var anyAggregate = false; // PORT: §3.6 hot path loop
                for (Parameter pp : possibleParameters)
                {
                    if (pp.argumentKind() == ArgumentKind.Aggregate)
                    {
                        anyAggregate = true;
                        break;
                    }
                }
                s_parameterListPool.returnToPool(possibleParameters);
                if (anyAggregate)
                    return ScopeKind.Aggregate;
            }
        }

        if (outerScope == ScopeKind.Aggregate)
        {
            // if the function is not a known aggregate then keep aggregate scope as there may be
            // aggregates nested in the function arguments
            return ScopeKind.Aggregate;
        }
        else
        {
            return ScopeKind.Normal;
        }
    }

    private static TableSymbol getArgumentRowScope(FunctionCallExpression fc, int position, TableSymbol defaultRowScope)
    {
        var fs = fc.name().referencedSymbol() instanceof FunctionSymbol fsx ? fsx : null; // PORT: §3.15 as
        var sig = fc.name().referencedSignature();

        if (fs != null)
        {
            var possibleParameters = s_parameterListPool.allocateFromPool();
            getPossibleArgumentParameters(fc, position, possibleParameters);

            // possibleParameters.Any(p => p.ArgumentKind == ...)
            var anyP0 = false; // PORT: §3.6 hot path loop
            var anyP0_Common = false; // PORT: §3.6 hot path loop
            var anyP0_Expression = false; // PORT: §3.6 hot path loop
            for (Parameter p : possibleParameters)
            {
                if (p.argumentKind() == ArgumentKind.Column_Parameter0)
                    anyP0 = true;
                if (p.argumentKind() == ArgumentKind.Column_Parameter0_Common)
                    anyP0_Common = true;
                if (p.argumentKind() == ArgumentKind.Expression_Parameter0_Element)
                    anyP0_Expression = true;
            }

            if (anyP0_Expression
                && fc.argumentList().expressions().size() > 0
                && fc.argumentList().expressions().get(0).element().resultType() instanceof TypeSymbol argType
                && argType instanceof TupleSymbol tuple)
            {
                return new TableSymbol(tuple.columns());
            }

            if ((anyP0 || anyP0_Common)
                && fc.argumentList().expressions().size() > 0
                && fc.argumentList().expressions().get(0).element().resultType() instanceof TableSymbol p0Table)
            {
                // if both, show all p0Table columns
                if (anyP0)
                    return p0Table;

                var rowScope = defaultRowScope != null ? defaultRowScope : TableSymbol.Empty; // PORT: §3.14 ??
                var commonColumns = new ArrayList<ColumnSymbol>();
                getCommonColumnsOfColumns(rowScope.columns(), p0Table.columns(), commonColumns); // PORT: §2.5 GetCommonColumns(…, List<ColumnSymbol>)
                return new TableSymbol(commonColumns);
            }
        }

        return defaultRowScope;
    }

    private static boolean hasAggregateParameters(FunctionSymbol fs)
    {
        if (fs.signatures().size() == 1)
        {
            return fs.signatures().get(0).hasAggregateParameters();
        }
        else if (fs.signatures().size() > 1)
        {
            for (var sig : fs.signatures())
            {
                if (sig.hasAggregateParameters())
                    return true;
            }
        }

        return false;
    }

    private static void getPossibleArgumentParameters(
        FunctionCallExpression fc,
        int position,
        List<Parameter> possibleParameters)
    {
        var childIndex = getChildIndex(fc.argumentList().expressions(), position);
        var fs = fc.referencedSymbol() instanceof FunctionSymbol fsx ? fsx : null; // PORT: §3.15 as
        var sig = fc.referencedSignature();

        // check for easy lookup named parameter case
        if (sig != null
            && sig.allowsNamedArguments()
            && childIndex >= 0
            && childIndex <= fc.argumentList().expressions().size())
        {
            var child = fc.argumentList().expressions().get(childIndex).element();
            if (child instanceof SimpleNamedExpression nex)
            {
                var parameter = sig.getParameter(nex.name().simpleName());
                possibleParameters.add(parameter);
                return;
            }
        }

        // otherwise we need to do a parameter layout to know which to choose
        var arguments = s_expressionListPool.allocateFromPool();
        try
        {
            // get all arguments
            for (var arg : fc.argumentList().expressions())
            {
                if (arg.element() != null)
                {
                    arguments.add(arg.element());
                }
            }

            if (sig != null)
            {
                getPossibleArgumentParameters(sig, position, arguments, childIndex, possibleParameters);
            }
            else if (fs != null)
            {
                for (var fsig : fs.signatures())
                {
                    getPossibleArgumentParameters(fsig, position, arguments, childIndex, possibleParameters);
                }
            }
        }
        finally
        {
            s_expressionListPool.returnToPool(arguments);
        }
    }

    private static void getPossibleArgumentParameters(
        Signature sig,
        int position,
        List<Expression> arguments,
        int argumentIndex,
        List<Parameter> possibleParameters)
    {
        // the position is right of the existing arguments
        if (arguments.size() == 0 || position > arguments.get(arguments.size() - 1).end())
        {
            // drop any trailing missing arguments
            while (arguments.size() > 0 && arguments.get(arguments.size() - 1).isMissing())
            {
                arguments.remove(arguments.size() - 1);
            }

            sig.getNextPossibleParameters(arguments, possibleParameters);
        }
        else
        {
            var parameters = s_parameterListPool.allocateFromPool();
            sig.getArgumentParameters(arguments, parameters);

            if (argumentIndex >= 0 && argumentIndex <= parameters.size())
            {
                // we know the signature and are at a specific argument index
                // so use the argument here
                possibleParameters.add(parameters.get(argumentIndex));
            }

            s_parameterListPool.returnToPool(parameters);
        }
    }

    /// <summary>
    /// Gets the index of the child in the parent or -1 if none found
    /// </summary>
    private static int getChildIndex(SyntaxElement parent, int position)
    {
        var firstMissingChildIndex = -1;

        // look for existing child that matches position
        for (int i = 0; i < parent.childCount(); i++)
        {
            var child = parent.getChild(i);
            if (child != null)
            {
                if (position >= child.textStart() && position < child.end())
                {
                    return i;
                }
                else if (child.isMissing() && position == child.textStart())
                {
                    firstMissingChildIndex = i;
                }
            }
        }

        return firstMissingChildIndex;
    }

    /// <summary>
    /// Gets the type referenced in the type expression.
    /// </summary>
    private TypeSymbol getTypeFromTypeExpression(TypeExpression typeExpression, List<Diagnostic> diagnostics)
    {
        return getDeclaredType(typeExpression, diagnostics, this);
    }

    private TypeSymbol getTypeFromTypeExpression(TypeExpression typeExpression) // PORT: §3.12 diagnostics = null
    {
        return getTypeFromTypeExpression(typeExpression, null);
    }

    @Internal
    public static TypeSymbol getDeclaredType(TypeExpression typeExpression, List<Diagnostic> diagnostics, Binder binder)
    {
        // PORT: §3.15 switch (typeExpression) { case PrimitiveTypeExpression p: ... case SchemaTypeExpression s: ... default: ... } as an instanceof chain (null falls to default)
        if (typeExpression instanceof PrimitiveTypeExpression p)
        {
            return getType(p, diagnostics);
        }
        else if (typeExpression instanceof SchemaTypeExpression s)
        {
            var cannotBeEmpty = typeExpression.parent() instanceof NameAndTypeDeclaration;
            if (s.columns().size() == 0 && cannotBeEmpty && diagnostics != null)
            {
                diagnostics.add(DiagnosticFacts.getColumnDeclarationExpected().withLocation(s));
            }
            else if (s.columns().size() == 1 && s.columns().get(0).element() instanceof StarExpression)
            {
                // (*) was the entire declaration.. no columns specified.
                return TableSymbol.Empty;
            }

            var columns = s_columnListPool.allocateFromPool();
            try
            {
                for (int i = 0, n = s.columns().size(); i < n; i++)
                {
                    var expr = s.columns().get(i).element();
                    if (!expr.isMissing())
                    {
                        if (expr instanceof NameAndTypeDeclaration nat) // PORT: §3.15 nested switch (expr)
                        {
                            var declaredType = getDeclaredType(nat.type(), diagnostics, binder);
                            var newColumn = new ColumnSymbol(nat.name().simpleName(), declaredType);
                            columns.add(newColumn);

                            if (binder != null)
                            {
                                binder.setSemanticInfo(nat.name(), getSemanticInfo(newColumn));
                            }
                        }
                        else
                        {
                            if (diagnostics != null)
                            {
                                diagnostics.add(DiagnosticFacts.getInvalidColumnDeclaration().withLocation(expr));
                            }
                        }
                    }
                }

                return new TableSymbol(columns);
            }
            finally
            {
                s_columnListPool.returnToPool(columns);
            }
        }
        else
        {
            if (diagnostics != null)
            {
                diagnostics.add(DiagnosticFacts.getInvalidTypeExpression().withLocation(typeExpression));
            }

            return ErrorSymbol.Instance;
        }
    }

    @Internal
    public static TypeSymbol getDeclaredType(TypeExpression typeExpression, List<Diagnostic> diagnostics) // PORT: §3.12 binder = null
    {
        return getDeclaredType(typeExpression, diagnostics, null);
    }

    @Internal
    public static TypeSymbol getDeclaredType(TypeExpression typeExpression) // PORT: §3.12 diagnostics = null, binder = null
    {
        return getDeclaredType(typeExpression, null, null);
    }

    @Internal
    public TypeSymbol getTypeOfType(Expression typeofLiteral)
    {
        return getReferencedSymbol(typeofLiteral) instanceof TypeSymbol ts ? ts : ErrorSymbol.Instance; // PORT: §3.15 as TypeSymbol ??
    }

    @Internal
    public static TypeSymbol getType(PrimitiveTypeExpression primitiveType, List<Diagnostic> diagnostics)
    {
        var typeName = primitiveType.type().text();

        var type = ScalarTypes.getSymbol(typeName);

        if (type != null)
            return type;

        if (diagnostics != null && !primitiveType.containsSyntaxDiagnostics()) // diagnostic already handled by lexer
        {
            diagnostics.add(DiagnosticFacts.getInvalidTypeName(typeName).withLocation(primitiveType.type()));
        }

        return ErrorSymbol.Instance;
    }

    @Internal
    public static TypeSymbol getType(PrimitiveTypeExpression primitiveType) // PORT: §3.12 diagnostics = null
    {
        return getType(primitiveType, null);
    }

    private static boolean isTabular(TypeSymbol type)
    {
        return type != null && type.isTabular();
    }

    private boolean isTabular(Expression expr)
    {
        return isTabular(getResultTypeOrError(expr));
    }

    private boolean isColumn(Expression expr)
    {
        return getReferencedSymbol(expr) instanceof ColumnSymbol;
    }

    private static boolean isDatabase(Symbol symbol)
    {
        return symbol instanceof DatabaseSymbol;
    }

    private static boolean isCluster(Symbol symbol)
    {
        return symbol instanceof ClusterSymbol;
    }

    private static SemanticInfo getSemanticInfo(Symbol referencedSymbol, Diagnostic... diagnostics) // PORT: §3.10 params T[]
    {
        return createSemanticInfo(referencedSymbol, diagnostics != null ? Arrays.asList(diagnostics) : null); // PORT: §3.14 a null params array (callers pass null) stays null
    }

    private static SemanticInfo createSemanticInfo(Symbol referencedSymbol, Iterable<Diagnostic> diagnostics)
    {
        switch (referencedSymbol.kind())
        {
            case Operator:
            case Column:
            case Table:
            case Database:
            case Cluster:
            case Function:
            case Pattern:
            case Group:
            case MaterializedView:
            case Graph:
            case GraphModel:
            case GraphSnapshot:
            case EntityGroup:
            case EntityGroupElement:
            case StoredQueryResult:
                return new SemanticInfo(referencedSymbol, getResultType(referencedSymbol), diagnostics);
            case Parameter:
                // parameter is treated as probably constant so we don't raise an error diagnostic
                return new SemanticInfo(referencedSymbol, getResultType(referencedSymbol), diagnostics, ((ParameterSymbol) referencedSymbol).isScalar()); // PORT: §3.12 named argument isConstant
            case Variable:
                var v = (VariableSymbol) referencedSymbol;
                return new SemanticInfo(referencedSymbol, getResultType(referencedSymbol), diagnostics, v.isConstant()); // PORT: §3.12 named argument isConstant
            case Primitive:
            case Array:
            case Bag:
            case Tuple:
                return new SemanticInfo((TypeSymbol) referencedSymbol, diagnostics);
            default:
                return new SemanticInfo(ErrorSymbol.Instance, diagnostics);
        }
    }

    private static SemanticInfo createSemanticInfo(Symbol referencedSymbol) // PORT: §3.12 diagnostics = null
    {
        return createSemanticInfo(referencedSymbol, (Iterable<Diagnostic>) null);
    }

    private static TypeSymbol getResultType(Symbol symbol)
    {
        return Symbol.getResultType(symbol);
    }
    // endregion

    // region Symbol assignability

    private static boolean symbolsAssignable(List<? extends TypeSymbol> targetTypes, Symbol sourceType, Conversion allowedConversion) // PORT: §3.10 covariance
    {
        return sourceType.isAssignableToAny(targetTypes, allowedConversion);
    }

    private static boolean symbolsAssignable(List<? extends TypeSymbol> targetTypes, Symbol sourceType) // PORT: §3.12 allowedConversion = Conversion.None
    {
        return symbolsAssignable(targetTypes, sourceType, Conversion.None);
    }

    /// <summary>
    /// True if a value of type <see cref="P:valueType"/> can be assigned to a parameter of type <see cref="P:parameterType"/>
    /// </summary>
    private static boolean symbolsAssignable(Symbol targetType, Symbol sourceType, Conversion allowedConversion)
    {
        return sourceType.isAssignableTo(targetType, allowedConversion);
    }

    private static boolean symbolsAssignable(Symbol targetType, Symbol sourceType) // PORT: §3.12 allowedConversion = Conversion.None
    {
        return symbolsAssignable(targetType, sourceType, Conversion.None);
    }

    // endregion

    // region Check methods

    private void checkQueryOperatorParameters(SyntaxList1<NamedParameter> parameters, List<QueryOperatorParameter> queryParameters, List<Diagnostic> diagnostics)
    {
        var names = s_stringSetPool.allocateFromPool();
        try
        {
            for (int i = 0, n = parameters.size(); i < n; i++)
            {
                checkQueryOperatorParameter(parameters.get(i), queryParameters, names, diagnostics);
            }
        }
        finally
        {
            s_stringSetPool.returnToPool(names);
        }
    }

    // PORT: §2.5 CheckQueryOperatorParameters(SyntaxList<SeparatedElement<NamedParameter>>, …) erases to the same signature
    private void checkQueryOperatorParametersOfNamedParameters(SyntaxList1<SeparatedElement1<NamedParameter>> parameters, List<QueryOperatorParameter> queryParameters, List<Diagnostic> diagnostics)
    {
        var names = s_stringSetPool.allocateFromPool();
        try
        {
            for (int i = 0, n = parameters.size(); i < n; i++)
            {
                checkQueryOperatorParameter(parameters.get(i).element(), queryParameters, names, diagnostics);
            }
        }
        finally
        {
            s_stringSetPool.returnToPool(names);
        }
    }

    private void checkQueryOperatorParameter(NamedParameter parameter, List<QueryOperatorParameter> queryOperatorParameters, Set<String> namesAlreadySpecified, List<Diagnostic> diagnostics)
    {
        var name = parameter.name().simpleName();
        if (!DotNetStrings.isNullOrEmpty(name))
        {
            var qop = getQueryOperatorParameter(name, queryOperatorParameters);

            if (qop != null)
            {
                setSemanticInfo(parameter.name(), new SemanticInfo(qop, ScalarTypes.Unknown));

                if (!qop.isRepeatable())
                {
                    // qop.Aliases.Any(a => namesAlreadySpecified.Contains(a))
                    boolean anyAliasAlreadySpecified = false; // PORT: §3.6 hot path loop
                    if (qop.aliases().size() > 0)
                    {
                        for (String a : qop.aliases())
                        {
                            if (namesAlreadySpecified.contains(a))
                            {
                                anyAliasAlreadySpecified = true;
                                break;
                            }
                        }
                    }

                    if (namesAlreadySpecified.contains(qop.name())
                        || (qop.aliases().size() > 0 && anyAliasAlreadySpecified))
                    {
                        diagnostics.add(DiagnosticFacts.getParameterAlreadySpecified(name).withLocation(parameter.name()));
                    }
                    else
                    {
                        namesAlreadySpecified.add(qop.name());
                    }
                }

                checkQueryOperatorParameter(parameter, qop, diagnostics);
            }
            else
            {
                diagnostics.add(DiagnosticFacts.getUnknownQueryOperatorParameterName(name).withLocation(parameter.name()));
            }
        }
    }

    private void checkQueryOperatorParameter(NamedParameter parameter, QueryOperatorParameter qop, List<Diagnostic> diagnostics)
    {
        if (!isQueryOperatorParameterKind(parameter, qop))
        {
            var actualType = getResultTypeOrError(parameter.expression());

            switch (qop.valueKind())
            {
                case IntegerLiteral:
                    checkIsIntegerLiteral(parameter.expression(), diagnostics);
                    break;
                case NumericLiteral:
                case ForcedRealLiteral:
                    checkIsNumericLiteral(parameter.expression(), diagnostics);
                    break;
                case ScalarLiteral:
                    checkIsScalarLiteral(parameter.expression(), diagnostics);
                    break;
                case SummableLiteral:
                    checkIsSummableLiteral(parameter.expression(), diagnostics);
                    break;
                case StringLiteral:
                    checkIsStringLiteral(parameter.expression(), diagnostics);
                    break;
                case BoolLiteral:
                    checkIsBooleanlLiteral(parameter.expression(), diagnostics);
                    break;
                case String:
                    checkIsStringOrDynamic(parameter.expression(), diagnostics);
                    break;
                case Column:
                    checkIsColumn(parameter.expression(), diagnostics);
                    break;
                case Word:
                case WordOrNumber:
                    checkIsTokenLiteral(parameter.expression(), qop.values(), qop.isCaseSensitive(), diagnostics);
                    break;
                default: // PORT: §3.15 C# switch without default does nothing for the other members
                    break;
            }

            if (qop.valueKind() != QueryOperatorParameterValueKind.Word
                && qop.valueKind() != QueryOperatorParameterValueKind.WordOrNumber
                && qop.values() != null
                && qop.values().size() > 0)
            {
                checkIsLiteral(parameter.expression(), diagnostics);
                checkIsLiteralValue(parameter.expression(), qop.values(), qop.isCaseSensitive(), diagnostics);
            }
        }
    }

    private boolean isQueryOperatorParameterKind(NamedParameter parameter, QueryOperatorParameter qop)
    {
        var type = getResultTypeOrError(parameter.expression());
        switch (qop.valueKind())
        {
            case IntegerLiteral:
                if (!(parameter.expression().isLiteral() && TypeFacts.isInteger(type)))
                    return false;
                break;
            case NumericLiteral:
            case ForcedRealLiteral:
                if (!(parameter.expression().isLiteral() && TypeFacts.isNumeric(type)))
                    return false;
                break;
            case ScalarLiteral:
                if (!(parameter.expression().isLiteral() && type.isScalar()))
                    return false;
                break;
            case SummableLiteral:
                if (!(parameter.expression().isLiteral() && TypeFacts.isSummable(type)))
                    return false;
                break;
            case StringLiteral:
                if (!(parameter.expression().isLiteral() && isType(parameter.expression(), ScalarTypes.String)))
                    return false;
                break;
            case BoolLiteral:
                if (!(parameter.expression().isLiteral() && isType(parameter.expression(), ScalarTypes.Bool)))
                    return false;
                break;
            case String:
                if (!isType(parameter.expression(), ScalarTypes.String))
                    return false;
                break;
            case Column:
                if (!(getReferencedSymbol(parameter.expression()) instanceof ColumnSymbol))
                    return false;
                break;
            case Word:
                if (!isTokenLiteral(parameter.expression(), qop.values(), qop.isCaseSensitive()))
                    return false;
                break;
            case WordOrNumber:
                if (!TypeFacts.isNumeric(type) && !isTokenLiteral(parameter.expression(), qop.values(), qop.isCaseSensitive()))
                    return false;
                break;
            default: // PORT: §3.15 C# switch without default does nothing for the other members
                break;
        }

        if (qop.valueKind() != QueryOperatorParameterValueKind.Word
            && qop.valueKind() != QueryOperatorParameterValueKind.WordOrNumber
            && qop.values() != null
            && qop.values().size() > 0)
        {
            if (!parameter.expression().isLiteral())
                return false;

            if (!isLiteralValue(parameter.expression(), qop.values(), qop.isCaseSensitive()))
                return false;
        }

        return true;
    }

    private static QueryOperatorParameter getQueryOperatorParameter(String name, List<QueryOperatorParameter> parameters)
    {
        for (var p : parameters)
        {
            if (Objects.equals(p.name(), name)
                || (p.aliases().size() > 0 && p.aliases().contains(name)))
            {
                return p;
            }
        }

        return null;
    }

    /// <summary>
    /// Checks that the data value expressions have the types corresponding to the columns.
    /// </summary>
    private void checkDataValueTypes(SyntaxList1<SeparatedElement1<Expression>> expressions, List<ColumnSymbol> columns, List<Diagnostic> diagnostics)
    {
        if (columns.size() > 0 && expressions.size() % columns.size() != 0)
        {
            diagnostics.add(DiagnosticFacts.getIncorrectNumberOfDataValues(columns.size()).withLocation(expressions));
        }

        for (int i = 0, n = expressions.size(); i < n; i++)
        {
            var expr = expressions.get(i).element();
            checkIsScalar(expr, diagnostics);

            // note: data values are convertible at runtime so no check is given
            // consider adding checks for obvious incovertible values
            // var column = columns[i % columns.Count];
            // CheckIsType(expr, column.Type, true, diagnostics);
        }
    }

    private boolean checkIsScalar(Expression expression, List<Diagnostic> diagnostics, Symbol resultType)
    {
        if (resultType == null)
            resultType = getResultType(expression);

        // we don't know anything
        if (resultType == null)
            return true;

        if (resultType.isScalar())
            return true;

        if (!resultType.isError())
        {
            diagnostics.add(DiagnosticFacts.getScalarTypeExpected().withLocation(expression));
        }

        return false;
    }

    private boolean checkIsScalar(Expression expression, List<Diagnostic> diagnostics) // PORT: §3.12 resultType = null
    {
        return checkIsScalar(expression, diagnostics, null);
    }

    private boolean checkIsScalar(TypeSymbol type, SyntaxElement location, List<Diagnostic> diagnostics)
    {
        if (type.isScalar())
            return true;

        if (!type.isError())
        {
            diagnostics.add(DiagnosticFacts.getScalarTypeExpected().withLocation(location));
        }

        return false;
    }

    private boolean checkIsScalar(SyntaxList1<SeparatedElement1<Expression>> list, List<Diagnostic> diagnostics)
    {
        return checkAll(list, diagnostics, (expr, dx) -> checkIsScalar(expr, dx));
    }

    private boolean checkIsScalasr(List<ColumnSymbol> columns, SyntaxElement location, List<Diagnostic> diagnostics)
    {
        return checkAll(columns, location, diagnostics, (col, loc, dx) -> checkIsScalar(col.type(), loc, dx));
    }

    private boolean checkIsInteger(Expression expression, List<Diagnostic> diagnostics)
    {
        var type = getResultTypeOrError(expression);

        if (TypeFacts.isInteger(type))
            return true;

        if (!type.isError() && type != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getExpressionMustBeInteger().withLocation(expression));
        }

        return false;
    }

    private boolean checkIsIntegerLiteral(Expression expression, List<Diagnostic> diagnostics)
    {
        var type = getResultTypeOrError(expression);

        if (TypeFacts.isInteger(type) && expression.isLiteral())
            return true;

        if (!type.isError() && type != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getIntegerLiteralExpected().withLocation(expression));
        }

        return false;
    }

    private boolean checkIsStringLiteral(Expression expression, List<Diagnostic> diagnostics)
    {
        var type = getResultTypeOrError(expression);

        if (type == ScalarTypes.String && expression.isLiteral())
            return true;

        if (!type.isError() && type != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getStringLiteralExpected().withLocation(expression));
        }

        return false;
    }

    private boolean checkIsBooleanlLiteral(Expression expression, List<Diagnostic> diagnostics)
    {
        var type = getResultTypeOrError(expression);

        if (type == ScalarTypes.Bool && expression.isLiteral())
            return true;

        if (!type.isError() && type != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getBooleanLiteralExpected().withLocation(expression));
        }

        return false;
    }

    private boolean checkIsSummableLiteral(Expression expression, List<Diagnostic> diagnostics)
    {
        var type = getResultTypeOrError(expression);

        if (type instanceof ScalarSymbol s && s.isSummable() && expression.isLiteral())
            return true;

        if (!type.isError() && type != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getSummableLiteralExpected().withLocation(expression));
        }

        return false;
    }

    private boolean checkIsNumericLiteral(Expression expression, List<Diagnostic> diagnostics)
    {
        var type = getResultTypeOrError(expression);

        if (type instanceof ScalarSymbol s && s.isNumeric() && expression.isLiteral())
            return true;

        if (!type.isError() && type != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getSummableLiteralExpected().withLocation(expression));
        }

        return false;
    }

    private boolean checkIsScalarLiteral(Expression expression, List<Diagnostic> diagnostics)
    {
        var type = getResultTypeOrError(expression);

        if (type instanceof ScalarSymbol && expression.isLiteral())
            return true;

        if (!type.isError() && type != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getScalarLiteralExpected().withLocation(expression));
        }

        return false;
    }

    private boolean checkIsRealOrDecimal(Expression expression, List<Diagnostic> diagnostics)
    {
        var type = getResultTypeOrError(expression);

        if (TypeFacts.isRealOrDecimal(type))
            return true;

        if (!type.isError() && type != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getExpressionMustBeRealOrDecimal().withLocation(expression));
        }

        return false;
    }

    private boolean checkIsIntegerOrArray(Expression expression, List<Diagnostic> diagnostics)
    {
        var type = getResultTypeOrError(expression);

        if (TypeFacts.isIntegerOrArray(type))
            return true;

        if (!type.isError() && type != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getExpressionMustBeIntegerOrArray().withLocation(expression));
        }

        return false;
    }

    private boolean checkIsStringOrDynamic(Expression expression, List<Diagnostic> diagnostics)
    {
        var type = getResultTypeOrError(expression);

        if (TypeFacts.isStringOrDynamic(type))
            return true;

        if (!type.isError() && type != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getExpressionMustHaveType(ScalarTypes.String, ScalarTypes.Dynamic).withLocation(expression));
        }

        return false;
    }

    private boolean checkIsStringOrArray(Expression expression, List<Diagnostic> diagnostics)
    {
        var type = getResultTypeOrError(expression);

        if (TypeFacts.isStringOrArray(type))
            return true;

        if (!type.isError() && type != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getExpressionMustBeStringOrArray().withLocation(expression));
        }

        return false;
    }

    /// <summary>
    /// Checks if the expression is any dynamic type.
    /// </summary>
    private boolean checkIsDynamic(Expression expression, List<Diagnostic> diagnostics)
    {
        var type = getResultTypeOrError(expression);

        if (type instanceof DynamicSymbol)
            return true;

        if (!type.isError() && type != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getExpressionMustHaveType(ScalarTypes.Dynamic).withLocation(expression));
        }

        return false;
    }

    private boolean checkIsDynamicArray(Expression expression, List<Diagnostic> diagnostics)
    {
        var type = getResultTypeOrError(expression);

        if (TypeFacts.isDynamicArray(type))
            return true;

        if (!type.isError() && type != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getExpressionMustBeDynamicArray().withLocation(expression));
        }

        return false;
    }

    private boolean checkIsDynamicBag(Expression expression, List<Diagnostic> diagnostics)
    {
        var type = getResultTypeOrError(expression);

        if (TypeFacts.isDynamicBag(type))
            return true;

        if (!type.isError() && type != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getExpressionMustBeDynamicBag().withLocation(expression));
        }

        return false;
    }

    private boolean checkIsNumber(Expression expression, List<Diagnostic> diagnostics)
    {
        var type = getResultTypeOrError(expression);

        if (TypeFacts.isNumeric(type))
            return true;

        if (!type.isError() && type != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getExpressionMustBeNumeric().withLocation(expression));
        }

        return false;
    }

    private boolean checkIsNumberOrBool(Expression expression, List<Diagnostic> diagnostics)
    {
        var type = getResultTypeOrError(expression);

        if (TypeFacts.isNumeric(type) || type == ScalarTypes.Bool)
            return true;

        if (!type.isError() && type != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getExpressionMustBeNumericOrBool().withLocation(expression));
        }

        return false;
    }

    private boolean checkIsSummable(Expression expression, List<Diagnostic> diagnostics)
    {
        var type = getResultTypeOrError(expression);

        if (TypeFacts.isSummable(type))
            return true;

        if (!type.isError() && type != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getExpressionMustBeSummable().withLocation(expression));
        }

        return false;
    }

    private boolean checkIsOrderable(Expression expression, List<Diagnostic> diagnostics)
    {
        var type = getResultTypeOrError(expression);

        if (TypeFacts.isOrderable(type))
            return true;

        if (!type.isError() && type != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getExpressionMustBeOrderable().withLocation(expression));
        }

        return false;
    }

    private boolean checkIsExactType(Expression expression, TypeSymbol type, List<Diagnostic> diagnostics)
    {
        return checkIsType(expression, type, Conversion.None, diagnostics);
    }

    private boolean checkIsTypeOrDynamic(Expression expression, TypeSymbol type, boolean canPromote, List<Diagnostic> diagnostics)
    {
        var exprType = getResultTypeOrError(expression);

        if (symbolsAssignable(type, exprType, canPromote ? Conversion.Promotable : Conversion.None)
            || symbolsAssignable(ScalarTypes.Dynamic, exprType, Conversion.Dynamic))
            return true;

        if (!exprType.isError() && exprType != ScalarTypes.Unknown)
        {
            if (symbolsAssignable(ScalarTypes.Dynamic, type, Conversion.Dynamic))
            {
                diagnostics.add(DiagnosticFacts.getExpressionMustHaveType(type).withLocation(expression));
            }
            else
            {
                diagnostics.add(DiagnosticFacts.getExpressionMustHaveType(type, ScalarTypes.Dynamic).withLocation(expression));
            }
        }

        return false;
    }

    private boolean isType(Expression expression, TypeSymbol expectedType, Conversion conversionKind)
    {
        return isType(getResultTypeOrError(expression), expectedType, conversionKind);
    }

    private boolean isType(Expression expression, TypeSymbol expectedType) // PORT: §3.12 conversionKind = Conversion.None
    {
        return isType(expression, expectedType, Conversion.None);
    }

    private boolean isType(Symbol type, Symbol expectedType, Conversion conversionKind)
    {
        if (type == ScalarTypes.Unknown)
        {
            // we don't know that it not the type
            return true;
        }
        else if (expectedType == ScalarTypes.Dynamic)
        {
            return type instanceof DynamicSymbol;
        }
        else
        {
            return symbolsAssignable(expectedType, type, conversionKind);
        }
    }

    private boolean isType(Symbol type, Symbol expectedType) // PORT: §3.12 conversionKind = Conversion.None
    {
        return isType(type, expectedType, Conversion.None);
    }

    private boolean checkIsType(Expression expression, TypeSymbol type, Conversion conversionKind, List<Diagnostic> diagnostics)
    {
        if (isType(expression, type, conversionKind))
            return true;

        var exprType = getResultTypeOrError(expression);
        if (!exprType.isError() && !type.isError() && exprType != ScalarTypes.Unknown && type != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getExpressionMustHaveType(type).withLocation(expression));
        }

        return false;
    }

    private <T extends TypeSymbol> boolean isAnyType(Expression expression, List<T> types, Conversion conversionKind) // PORT: §3.10 generic method kept
    {
        var exprType = getResultTypeOrError(expression);

        for (var type : types)
        {
            if (symbolsAssignable(type, exprType, conversionKind))
                return true;
        }

        return false;
    }

    private <T extends TypeSymbol> boolean isAnyType(Expression expression, List<T> types) // PORT: §3.12 conversionKind = Conversion.None
    {
        return isAnyType(expression, types, Conversion.None);
    }

    private <T extends TypeSymbol> boolean checkIsAnyType(Expression expression, List<T> types, Conversion conversionKind, List<Diagnostic> diagnostics) // PORT: §3.10 generic method kept
    {
        if (isAnyType(expression, types, conversionKind))
            return true;

        var exprType = getResultTypeOrError(expression);
        if (!exprType.isError() && exprType != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getExpressionMustHaveType(types).withLocation(expression));
        }

        return false;
    }

    private boolean checkIsNotType(Expression expression, Symbol expectedType, List<Diagnostic> diagnostics)
    {
        var exprType = getResultTypeOrError(expression);

        if (exprType == ScalarTypes.Unknown)
            return true;

        if (expectedType == ScalarTypes.Dynamic
            && !(exprType instanceof DynamicSymbol))
            return true;

        if (!symbolsAssignable(expectedType, exprType))
            return true;

        // avoid additional errors
        if (!getResultTypeOrError(expression).isError())
        {
            diagnostics.add(DiagnosticFacts.getTypeNotAllowed(expectedType).withLocation(expression));
        }

        return false;
    }

    private boolean checkIsNotType(Symbol type, Symbol notExpectedType, SyntaxElement location, List<Diagnostic> diagnostics)
    {
        // we don't know that its the unexpected type.
        if (type == ScalarTypes.Unknown)
            return true;

        if (!isType(type, notExpectedType))
            return true;

        diagnostics.add(DiagnosticFacts.getTypeNotAllowed(type).withLocation(location));
        return false;
    }

    private boolean checkAll(SyntaxList1<SeparatedElement1<Expression>> expressions, List<Diagnostic> diagnostics, BiPredicate<Expression, List<Diagnostic>> fnCheck) // PORT: §3.8 Func<A,B,bool>
    {
        for (int i = 0; i < expressions.size(); i++)
        {
            var expr = expressions.get(i).element();
            if (!fnCheck.test(expr, diagnostics))
                return false;
        }

        return true;
    }

    private boolean checkAll(List<ColumnSymbol> columns, SyntaxElement location, List<Diagnostic> diagnostics, Func3<ColumnSymbol, SyntaxElement, List<Diagnostic>, Boolean> fnCheck) // PORT: §3.8 Func<A,B,C,bool>
    {
        for (var col : columns)
        {
            if (!fnCheck.apply(col, location, diagnostics))
                return false;
        }

        return true;
    }

    private boolean checkIsNotDynamic(SyntaxList1<SeparatedElement1<Expression>> expressions, List<Diagnostic> diagnostics)
    {
        return checkAll(expressions, diagnostics, (expr, dx) -> checkIsNotType(expr, ScalarTypes.Dynamic, dx));
    }

    private boolean checkIsNotDynamic(List<ColumnSymbol> columns, SyntaxElement location, List<Diagnostic> diagnostics)
    {
        return checkAll(columns, location, diagnostics, (col, loc, dx) -> checkIsNotType(col.type(), ScalarTypes.Dynamic, loc, diagnostics));
    }

    private boolean checkIsIntervalType(Expression expression, TypeSymbol rangeType, List<Diagnostic> diagnostics)
    {
        // check to see if add operator is defined between the expression's type and the range type
        var info = getBinaryOperatorInfo(OperatorKind.Add, expression, rangeType, expression, getResultTypeOrError(expression), expression);
        if (info.referencedSymbol() != null && symbolsAssignable(rangeType, info.resultType()))
            return true;

        var exprType = getResultTypeOrError(expression);
        if (!rangeType.isError() && !exprType.isError() && rangeType != ScalarTypes.Unknown && exprType != ScalarTypes.Unknown)
        {
            diagnostics.add(DiagnosticFacts.getTypeIsNotIntervalType(getResultTypeOrError(expression), rangeType).withLocation(expression));
        }

        return false;
    }

    private boolean isLiteralOrName(Expression expression)
    {
        return expression instanceof LiteralExpression ||
            expression instanceof CompoundStringLiteralExpression ||
            expression.kind() == SyntaxKind.DynamicExpression ||
            expression.kind() == SyntaxKind.NameReference;
    }

    private boolean checkIsIdentifierNameDeclaration(NameDeclaration name, List<Diagnostic> diagnostics)
    {
        if (name.name() instanceof TokenName)
            return true;

        diagnostics.add(DiagnosticFacts.getIdentifierNameOnly().withLocation(name));
        return false;
    }

    private boolean checkIsLiteralOrName(Expression expression, List<Diagnostic> diagnostics)
    {
        if (isLiteralOrName(expression))
            return true;

        var exprType = getResultTypeOrError(expression);
        if (!exprType.isError())
        {
            diagnostics.add(DiagnosticFacts.getExpressionMustBeConstantOrIdentifier().withLocation(expression));
        }

        return false;
    }

    private boolean checkIsTabular(Expression expression, List<Diagnostic> diagnostics, Symbol resultType)
    {
        resultType = resultType != null ? resultType : getResultType(expression); // PORT: §3.14 ??

        if (resultType != null)
        {
            if (resultType.isTabular())
                return true;

            if (!resultType.isError())
            {
                diagnostics.add(DiagnosticFacts.getTabularValueExpected().withLocation(expression));
            }
        }

        return false;
    }

    private boolean checkIsTabular(Expression expression, List<Diagnostic> diagnostics) // PORT: §3.12 resultType = null
    {
        return checkIsTabular(expression, diagnostics, null);
    }

    private boolean checkIsGraph(Expression expression, List<Diagnostic> diagnostics, Symbol resultType)
    {
        resultType = resultType != null ? resultType : getResultType(expression); // PORT: §3.14 ??

        if (resultType != null)
        {
            if (resultType instanceof GraphSymbol)
                return true;

            if (!resultType.isError())
            {
                diagnostics.add(DiagnosticFacts.getGraphExpected().withLocation(expression));
            }
        }

        return false;
    }

    private boolean checkIsGraph(Expression expression, List<Diagnostic> diagnostics) // PORT: §3.12 resultType = null
    {
        return checkIsGraph(expression, diagnostics, null);
    }

    private boolean checkIsTabularOrGraph(Expression expression, List<Diagnostic> diagnostics, Symbol resultType)
    {
        resultType = resultType != null ? resultType : getResultType(expression); // PORT: §3.14 ??
        if (resultType != null)
        {
            if (resultType.isTabular() || resultType instanceof GraphSymbol)
            {
                return true;
            }

            if (!resultType.isError())
            {
                diagnostics.add(DiagnosticFacts.getTableOrGraphExpected().withLocation(expression));
            }
        }

        return false;
    }

    private boolean checkIsTabularOrGraph(Expression expression, List<Diagnostic> diagnostics) // PORT: §3.12 resultType = null
    {
        return checkIsTabularOrGraph(expression, diagnostics, null);
    }

    private boolean checkIsSingleColumnTable(Expression expression, List<Diagnostic> diagnostics, Symbol resultType)
    {
        resultType = resultType != null ? resultType : getResultType(expression); // PORT: §3.14 ??

        if (resultType != null)
        {
            var table = resultType instanceof TableSymbol ts ? ts : null; // PORT: §3.15 as
            if (table != null && table.columns().size() == 1)
                return true;

            if (!resultType.isError())
            {
                diagnostics.add(DiagnosticFacts.getSingleColumnTableExpected().withLocation(expression));
            }
        }

        return false;
    }

    private boolean checkIsSingleColumnTable(Expression expression, List<Diagnostic> diagnostics) // PORT: §3.12 resultType = null
    {
        return checkIsSingleColumnTable(expression, diagnostics, null);
    }


    private boolean checkIsDatabase(Expression expression, List<Diagnostic> diagnostics)
    {
        if (isDatabase(getResultTypeOrError(expression)))
            return true;

        if (!getResultTypeOrError(expression).isError())
        {
            diagnostics.add(DiagnosticFacts.getDatabaseExpected().withLocation(expression));
        }

        return false;
    }

    private boolean checkIsCluster(Expression expression, List<Diagnostic> diagnostics)
    {
        if (isCluster(getResultTypeOrError(expression)))
            return true;

        if (!getResultTypeOrError(expression).isError())
        {
            diagnostics.add(DiagnosticFacts.getClusterExpected().withLocation(expression));
        }

        return false;
    }

    private boolean checkIsColumn(Expression expression, List<Diagnostic> diagnostics)
    {
        if (getReferencedSymbol(expression) instanceof ColumnSymbol)
            return true;

        if (!getResultTypeOrError(expression).isError())
        {
            diagnostics.add(DiagnosticFacts.getColumnExpected().withLocation(expression));
        }

        return false;
    }

    /// <summary>
    /// Check if the expression is a literal.
    /// </summary>
    private boolean checkIsLiteral(Expression expression, List<Diagnostic> diagnostics)
    {
        if (expression.isLiteral())
            return true;

        if (!getResultTypeOrError(expression).isError())
        {
            diagnostics.add(DiagnosticFacts.getExpressionMustBeLiteral().withLocation(expression));
        }

        return false;
    }

    /// <summary>
    /// Check if the expression is a scalar literal, but not a token literal.
    /// </summary>
    private boolean checkIsLiteralNotToken(Expression expression, List<Diagnostic> diagnostics)
    {
        if (expression.isLiteral() && expression.kind() != SyntaxKind.TokenLiteralExpression)
            return true;

        if (!getResultTypeOrError(expression).isError())
        {
            diagnostics.add(DiagnosticFacts.getExpressionMustBeLiteralScalarValue().withLocation(expression));
        }

        return false;
    }

    /// <summary>
    /// Check if the expression is a non-empty literal string.
    /// </summary>
    private boolean checkIsLiteralStringNotEmpty(Expression expression, List<Diagnostic> diagnostics)
    {
        var exprType = getResultTypeOrError(expression);
        if (!exprType.isError() && exprType != ScalarTypes.Unknown && expression.isLiteral())
        {
            Object literalValue = expression.literalValue(); // PORT: §3.14 expression.LiteralValue?.ToString()
            String value = literalValue != null ? DotNet.str(literalValue) : null; // PORT: §3.14 implicit ToString
            if (!DotNetStrings.isNullOrEmpty(value))
                return true;

            diagnostics.add(DiagnosticFacts.getExpressionMustNotBeEmpty().withLocation(expression));
        }

        return false;
    }

    /// <summary>
    /// Return true if the expression a literal that matches one of the values.
    /// </summary>
    private boolean isLiteralValue(Expression expression, List<?> values, boolean caseSensitive) // PORT: §3.10 IReadOnlyList<object> covariance
    {
        if (!expression.isLiteral())
            return false;

        return contains(values, expression.literalValue(), caseSensitive);
    }

    /// <summary>
    /// Checks if the expression is a literal that matches one of the listed values.
    /// </summary>
    private boolean checkIsLiteralValue(Expression expression, List<?> values, boolean caseSensitive, List<Diagnostic> diagnostics) // PORT: §3.10 IReadOnlyList<object> covariance
    {
        var result = getResultTypeOrError(expression);
        if (!result.isError())
        {
            if (isLiteralValue(expression, values, caseSensitive))
                return true;

            diagnostics.add(DiagnosticFacts.getExpressionMustHaveValue(values).withLocation(expression));
        }

        return false;
    }

    /// <summary>
    /// Returns true if the expression is a token literal with one of the listed values.
    /// </summary>
    private boolean isTokenLiteral(Expression expression, List<?> values, boolean caseSensitive) // PORT: §3.10 IReadOnlyList<object> covariance
    {
        if (expression.kind() == SyntaxKind.TokenLiteralExpression)
        {
            if (values != null && values.size() > 0)
            {
                return contains(values, expression.literalValue(), caseSensitive);
            }

            return true;
        }

        return false;
    }

    /// <summary>
    /// Checks if the expression is a token literal that matches one of the listed values.
    /// </summary>
    private boolean checkIsTokenLiteral(Expression expression, List<?> values, boolean caseSensitive, List<Diagnostic> diagnostics) // PORT: §3.10 IReadOnlyList<object> covariance
    {
        var result = getResultTypeOrError(expression);
        if (!result.isError())
        {
            if (isTokenLiteral(expression, values, caseSensitive))
                return true;

            // values.Select(v => v.ToString()).ToList()
            var valueTexts = new ArrayList<String>(); // PORT: §3.6 hot path loop
            for (Object v : values)
            {
                valueTexts.add(DotNet.str(v)); // PORT: §3.14 implicit ToString
            }

            diagnostics.add(DiagnosticFacts.getTokenExpected(valueTexts).withLocation(expression));
        }

        return false;
    }

    /// <summary>
    /// Checks if the token has one of the listed text values.
    /// </summary>
    private boolean checkIsToken(SyntaxToken token, List<?> values, boolean caseSensitive, List<Diagnostic> diagnostics) // PORT: §3.10 IReadOnlyList<object> covariance
    {
        if (contains(values, token.text(), caseSensitive))
            return true;

        if (!token.hasSyntaxDiagnostics())
        {
            // values.Select(v => v.ToString()).ToList()
            var valueTexts = new ArrayList<String>(); // PORT: §3.6 hot path loop
            for (Object v : values)
            {
                valueTexts.add(DotNet.str(v)); // PORT: §3.14 implicit ToString
            }

            diagnostics.add(DiagnosticFacts.getTokenExpected(valueTexts).withLocation(token));
        }

        return false;
    }

    /// <summary>
    /// Returns true if the value in the list of values.
    /// </summary>
    private static boolean contains(List<?> values, Object value, boolean caseSensitive) // PORT: §3.10 IReadOnlyList<object> covariance
    {
        for (int i = 0, n = values.size(); i < n; i++)
        {
            if (ValueComparer.areEquivalent(values.get(i), value, caseSensitive))
                return true;
        }

        return false;
    }

    /// <summary>
    /// Checks if the expression is a constant.
    /// </summary>
    private boolean checkIsConstant(Expression expression, List<Diagnostic> diagnostics)
    {
        if (getIsConstant(expression))
            return true;

        if (!getResultTypeOrError(expression).isError())
        {
            diagnostics.add(DiagnosticFacts.getExpressionMustBeConstant().withLocation(expression));
        }

        return false;
    }

    /// <summary>
    /// Returns true if the expression is a constant that matches one of the listed values.
    /// </summary>
    private static boolean isConstantValue(Expression expression, List<?> values, boolean caseSensitive) // PORT: §3.10 IReadOnlyList<object> covariance
    {
        if (!expression.isConstant())
            return false;

        return contains(values, expression.constantValue(), caseSensitive);
    }

    /// <summary>
    /// Returns true if the expression is either not constant or does not match any of the listed values.
    /// </summary>
    private boolean isNotConstantValue(Expression expression, List<?> values, boolean caseSensitive) // PORT: §3.10 IReadOnlyList<object> covariance
    {
        if (!expression.isConstant())
            return true;

        return !contains(values, expression.constantValue(), caseSensitive);
    }

    /// <summary>
    /// Checks if the expression is a constant that matches one of the listed values.
    /// </summary>
    private boolean checkIsConstantValue(Expression expression, List<?> values, boolean caseSensitive, List<Diagnostic> diagnostics) // PORT: §3.10 IReadOnlyList<object> covariance
    {
        var result = getResultTypeOrError(expression);
        if (!result.isError())
        {
            if (isConstantValue(expression, values, caseSensitive))
                return true;

            diagnostics.add(DiagnosticFacts.getExpressionMustHaveValue(values).withLocation(expression));
        }

        return false;
    }

    /// <summary>
    /// Checks if the expression is a constant that matches the specified value.
    /// </summary>
    private boolean checkIsConstantValue(Expression expression, Object value, boolean caseSensitive, List<Diagnostic> diagnostics)
    {
        return checkIsConstantValue(expression, Arrays.asList(new Object[] { value }), caseSensitive, diagnostics); // PORT: §3.17 new[] { value } as object[]
    }

    /// <summary>
    /// Checks if the expression is either not constant does not match any of the listed values.
    /// </summary>
    private boolean checkIsNotConstantValue(Expression expression, List<?> values, boolean caseSensitive, List<Diagnostic> diagnostics) // PORT: §3.10 IReadOnlyList<object> covariance
    {
        var result = getResultTypeOrError(expression);
        if (!result.isError())
        {
            if (isNotConstantValue(expression, values, caseSensitive))
                return true;

            diagnostics.add(DiagnosticFacts.getExpressionMustNotHaveValue(values).withLocation(expression));
        }

        return false;
    }

    /// <summary>
    /// Checks if the expression is either not constant does not match the specified value.
    /// </summary>
    private boolean checkIsNotConstantValue(Expression expression, Object value, boolean caseSensitive, List<Diagnostic> diagnostics)
    {
        return checkIsNotConstantValue(expression, Arrays.asList(new Object[] { value }), caseSensitive, diagnostics); // PORT: §3.17 new[] { value } as object[]
    }

    /// <summary>
    /// True if any argument type is an error type or unknown.
    /// </summary>
    private static boolean argumentsHaveErrorsOrUnknown(List<? extends TypeSymbol> argumentTypes) // PORT: §3.10 covariance
    {
        for (int i = 0; i < argumentTypes.size(); i++)
        {
            var type = argumentTypes.get(i);
            if (type.isError() || type == ScalarTypes.Unknown)
                return true;
        }

        return false;
    }

    /// <summary>
    /// Checks the invocation of a method/operator signature
    /// </summary>
    private void checkSignature(Signature signature, List<Expression> arguments, List<TypeSymbol> argumentTypes, SyntaxElement location, List<Diagnostic> dx)
    {
        var argCount = arguments.size();
        int initialDxCount = dx.size();

        if (!signature.isValidArgumentCount(argCount))
        {
            if (signature.hasRepeatableParameters())
            {
                if (argCount < signature.minArgumentCount() || argCount > signature.maxArgumentCount())
                {
                    dx.add(DiagnosticFacts.getFunctionExpectsArgumentCountRange(signature.symbol().name(), signature.minArgumentCount(), signature.maxArgumentCount()).withLocation(location));
                }
                else
                {
                    // not sure how else to say this.. the variable arguments are not specified correctly?
                    dx.add(DiagnosticFacts.getFunctionHasIncorrectNumberOfArguments().withLocation(location));
                }
            }
            else if (argCount != signature.parameters().size())
            {
                dx.add(DiagnosticFacts.getFunctionExpectsArgumentCountExact(signature.symbol().name(), signature.parameters().size()).withLocation(location));
            }
        }

        // check named arguments
        var namedArgumentsAllowed = namedArgumentsAllowed(signature);
        if (namedArgumentsAllowed && dx.size() == initialDxCount)
        {
            boolean hadOutOfOrderNamedArgument = false;
            boolean reportedUnnamedArgument = false;

            for (int i = 0; i < argCount; i++)
            {
                var argument = arguments.get(i);
                var simpleNamed = argument instanceof SimpleNamedExpression sne ? sne : null; // PORT: §3.15 as
                var isNamed = simpleNamed != null;
                var namedParameter = isNamed ? signature.getParameter(simpleNamed.name().simpleName()) : null;

                if (isNamed && namedParameter == null)
                {
                    dx.add(DiagnosticFacts.getUnknownArgumentName().withLocation(simpleNamed.name()));
                }

                if (isNamed && !hadOutOfOrderNamedArgument)
                {
                    var orderedParameter = i < signature.parameters().size() ? signature.parameters().get(i) : null;
                    hadOutOfOrderNamedArgument = orderedParameter != namedParameter;
                }
                else if (!isNamed && hadOutOfOrderNamedArgument && !reportedUnnamedArgument)
                {
                    dx.add(DiagnosticFacts.getUnnamedArgumentAfterOutofOrderNamedArgument().withLocation(argument));
                    reportedUnnamedArgument = true;
                }
            }
        }

        var argumentParameters = s_parameterListPool.allocateFromPool();
        try
        {
            signature.getArgumentParameters(arguments, argumentParameters);

            if (dx.size() == initialDxCount)
            {
                // check if a common parameter type is required and
                // if it can be determined from the arguments.
                checkCommonArgumentTypes(signature, argumentParameters, arguments, argumentTypes, location, dx);
            }

            // check arguments...
            if (dx.size() == initialDxCount)
            {
                for (int i = 0; i < argCount; i++)
                {
                    checkArgument(signature, argumentParameters, arguments, argumentTypes, i, dx);
                }
            }

            // check for missing arguments to non-optional parameters
            if (namedArgumentsAllowed && dx.size() == initialDxCount)
            {
                for (var parameter : signature.parameters())
                {
                    if (!parameter.isOptional())
                    {
                        var iArg = argumentParameters.indexOf(parameter);
                        if (iArg < 0)
                        {
                            dx.add(DiagnosticFacts.getMissingArgumentForParameter(parameter.name()).withLocation(location));
                        }
                    }
                }
            }
        }
        finally
        {
            s_parameterListPool.returnToPool(argumentParameters);
        }
    }

    private void checkCommonArgumentTypes(Signature signature, List<Parameter> argumentParameters, List<Expression> arguments, List<TypeSymbol> argumentTypes, SyntaxElement location, List<Diagnostic> dx)
    {
        for (int i = 0; i < argumentParameters.size(); i++)
        {
            var p = argumentParameters.get(i);

            switch (p.typeKind())
            {
                case CommonNumber:
                case CommonSummable:
                case CommonOrderable:
                case CommonScalar:
                case CommonScalarOrDynamic:
                    var commonType = TypeFacts.getCommonArgumentType(argumentParameters, argumentTypes);
                    if (commonType == null)
                    {
                        dx.add(DiagnosticFacts.getNoCommonArgumentType().withLocation(location));
                        return;
                    }
                    break;
                default: // PORT: §3.15 C# switch without default does nothing for the other members
                    break;
            }
        }
    }

    private static boolean isNamedArgument(Expression argument)
    {
        return argument instanceof NamedExpression;
    }

    private static SyntaxNode getNamedArgumentNameNode(Expression argument)
    {
        // PORT: §3.15 switch (argument) { case SimpleNamedExpression sn: ... case CompoundNamedExpression cn: ... default: ... } as an instanceof chain (null falls to default)
        if (argument instanceof SimpleNamedExpression sn)
        {
            return sn.name();
        }
        else if (argument instanceof CompoundNamedExpression cn)
        {
            return cn.names();
        }
        else
        {
            return null;
        }
    }

    /// <summary>
    /// True if named arguments are allowed for this signature.
    /// </summary>
    private boolean namedArgumentsAllowed(Signature signature)
    {
        var fn = signature.symbol() instanceof FunctionSymbol fsx ? fsx : null; // PORT: §3.15 as
        return fn != null && !_globals.isBuiltInFunction(fn);
    }

    /// <summary>
    /// True if the signature allows implicit argument coercion.
    /// Most built-in function require arguments to explicitly match types of parameters.
    /// User functions, however, allow implicit coercion of scalar arguments.
    /// </summary>
    private boolean allowImplicitArgumentCoercion(Signature signature)
    {
        // check to see if function is user function.
        return signature.symbol() instanceof FunctionSymbol fs
            && (_globals.isDatabaseFunction(fs)  // user function stored in database
                || fs.signatures().get(0).declaration() != null); // local user function have declarations (built-in functions do not).
    }

    private void checkArgument(Signature signature, List<Parameter> argumentParameters, List<Expression> arguments, List<TypeSymbol> argumentTypes, int argumentIndex, List<Diagnostic> diagnostics)
    {
        Expression argument = arguments.get(argumentIndex);
        var argumentType = argumentTypes.get(argumentIndex);
        var parameter = argumentParameters.get(argumentIndex);

        if (parameter != null)
        {
            if (argument instanceof StarExpression && signature.symbol().kind() != SymbolKind.Operator)
            {
                if (parameter.argumentKind() != ArgumentKind.StarOnly
                    && parameter.argumentKind() != ArgumentKind.StarAllowed)
                {
                    diagnostics.add(DiagnosticFacts.getStarExpressionNotAllowed().withLocation(argument));
                }
            }
            else if (isDefaultValueIndicator(parameter, argument))
            {
                // do nothing, this is a legal value for this parameter
            }
            else
            {
                if (argument instanceof CompoundNamedExpression cn)
                {
                    diagnostics.add(DiagnosticFacts.getCompoundNamedArgumentsNotSupported().withLocation(cn.names()));
                }

                // see through any named argument
                argument = getUnderlyingExpression(argument);

                switch (parameter.typeKind())
                {
                    case Any:
                        // do no checks
                        break;

                    case Declared:
                        if (parameter.declaredTypes().size() == 1
                            && parameter.declaredTypes().get(0) instanceof TableSymbol tablePattern)
                        {
                            if (argumentType instanceof TableSymbol argumentTable)
                            {
                                // tablePattern.Columns.All(c => argumentTable.TryGetColumn(c.Name, out var ac) && SymbolsAssignable(c.Type, ac.Type, Conversion.None))
                                var compatible = true; // PORT: §3.6 hot path loop
                                for (ColumnSymbol c : tablePattern.columns())
                                {
                                    var ac = new Out<ColumnSymbol>(); // PORT: §3.3 out var
                                    if (!(argumentTable.tryGetColumn(c.name(), ac) && symbolsAssignable(c.type(), ac.value.type(), Conversion.None)))
                                    {
                                        compatible = false;
                                        break;
                                    }
                                }
                                if (!compatible)
                                {
                                    diagnostics.add(DiagnosticFacts.getTabularValueDoesNotHaveRequiredColumns().withLocation(argument));
                                }
                            }
                            else
                            {
                                diagnostics.add(DiagnosticFacts.getTabularValueExpected().withLocation(argument));
                            }
                        }
                        else
                        {
                            switch (getParameterMatchKind(signature, argumentParameters, argumentTypes, parameter, argument, argumentType))
                            {
                                case Compatible:
                                case None:
                                    if (!allowImplicitArgumentCoercion(signature))
                                    {
                                        diagnostics.add(DiagnosticFacts.getTypeExpected(parameter.declaredTypes()).withLocation(argument));
                                    }
                                    break;
                                default: // PORT: §3.15 C# switch without default does nothing for the other members
                                    break;
                            }
                        }
                        break;

                    case Scalar:
                        checkIsScalar(argument, diagnostics, argumentType);
                        break;

                    case Integer:
                        checkIsInteger(argument, diagnostics);
                        break;

                    case RealOrDecimal:
                        checkIsRealOrDecimal(argument, diagnostics);
                        break;

                    case IntegerOrArray:
                        checkIsIntegerOrArray(argument, diagnostics);
                        break;

                    case StringOrDynamic:
                        checkIsStringOrDynamic(argument, diagnostics);
                        break;

                    case StringOrArray:
                        checkIsStringOrArray(argument, diagnostics);
                        break;

                    case DynamicArray:
                        checkIsDynamicArray(argument, diagnostics);
                        break;

                    case DynamicBag:
                        checkIsDynamicBag(argument, diagnostics);
                        break;

                    case Number:
                        checkIsNumber(argument, diagnostics);
                        break;

                    case NumberOrBool:
                        checkIsNumberOrBool(argument, diagnostics);
                        break;

                    case Summable:
                        checkIsSummable(argument, diagnostics);
                        break;

                    case Orderable:
                        checkIsOrderable(argument, diagnostics);
                        break;

                    case NotBool:
                        if (checkIsScalar(argument, diagnostics))
                        {
                            checkIsNotType(argument, ScalarTypes.Bool, diagnostics);
                        }
                        break;

                    case NotRealOrBool:
                        if (checkIsScalar(argument, diagnostics))
                        {
                            checkIsNotType(argument, ScalarTypes.Real, diagnostics);
                            checkIsNotType(argument, ScalarTypes.Bool, diagnostics);
                        }
                        break;

                    case NotDynamic:
                        if (checkIsScalar(argument, diagnostics))
                        {
                            checkIsNotType(argument, ScalarTypes.Dynamic, diagnostics);
                        }
                        break;

                    case Tabular:
                        checkIsTabular(argument, diagnostics, argumentType);
                        break;

                    case Database:
                        checkIsDatabase(argument, diagnostics);
                        break;

                    case Cluster:
                        checkIsCluster(argument, diagnostics);
                        break;

                    case Parameter0:
                        checkIsExactType(argument, argumentTypes.get(0), diagnostics);
                        break;

                    case Parameter1:
                        checkIsExactType(argument, argumentTypes.get(1), diagnostics);
                        break;

                    case Parameter2:
                        checkIsExactType(argument, argumentTypes.get(2), diagnostics);
                        break;

                    case CommonScalar:
                        if (checkIsScalar(argument, diagnostics))
                        {
                            var commonType = TypeFacts.getCommonArgumentType(argumentParameters, argumentTypes);
                            if (commonType != null)
                            {
                                checkIsType(argument, commonType, Conversion.Promotable, diagnostics);
                            }
                        }
                        break;

                    case CommonScalarOrDynamic:
                        if (checkIsScalar(argument, diagnostics))
                        {
                            var commonType = TypeFacts.getCommonArgumentType(argumentParameters, argumentTypes);
                            if (commonType != null)
                            {
                                checkIsTypeOrDynamic(argument, commonType, true, diagnostics);
                            }
                        }
                        break;

                    case CommonNumber:
                        if (checkIsNumber(argument, diagnostics))
                        {
                            var commonType = TypeFacts.getCommonArgumentType(argumentParameters, argumentTypes);
                            if (commonType != null)
                            {
                                checkIsType(argument, commonType, Conversion.Promotable, diagnostics);
                            }
                        }
                        break;

                    case CommonSummable:
                        if (checkIsSummable(argument, diagnostics))
                        {
                            var commonType = TypeFacts.getCommonArgumentType(argumentParameters, argumentTypes);
                            if (commonType != null)
                            {
                                checkIsType(argument, commonType, Conversion.Promotable, diagnostics);
                            }
                        }
                        break;

                    case CommonOrderable:
                        if (checkIsOrderable(argument, diagnostics))
                        {
                            var commonType = TypeFacts.getCommonArgumentType(argumentParameters, argumentTypes);
                            if (commonType != null)
                            {
                                checkIsType(argument, commonType, Conversion.Promotable, diagnostics);
                            }
                        }
                        break;
                }

                switch (parameter.argumentKind())
                {
                    case Column:
                    case Column_Parameter0:
                    case Column_Parameter0_Common:
                        checkIsColumn(argument, diagnostics);
                        break;

                    case Constant:
                        checkIsConstant(argument, diagnostics);
                        break;

                    case Literal:
                        if (checkIsLiteral(argument, diagnostics) && parameter.values().size() > 0)
                        {
                            checkIsLiteralValue(argument, parameter.values(), parameter.isCaseSensitive(), diagnostics);
                        }
                        break;

                    case LiteralNotEmpty:
                        if (checkIsLiteral(argument, diagnostics))
                        {
                            checkIsLiteralStringNotEmpty(argument, diagnostics);
                        }
                        break;

                    default: // PORT: §3.15 C# switch without default does nothing for the other members
                        break;
                }
            }
        }
    }

    private static boolean checkArgumentCount(SyntaxList1<SeparatedElement1<Expression>> expressions, int expectedCount, List<Diagnostic> diagnostics)
    {
        if (expressions.size() == expectedCount)
            return true;

        diagnostics.add(DiagnosticFacts.getArgumentCountExpected(expectedCount).withLocation(expressions));
        return false;
    }

    // keep line for BRIDGE.NET bug
    // endregion

    // ===== upstream part: Binder_Names.cs =====
    private SemanticInfo bindName(String name, int match, SyntaxNode location, boolean includeRowScope, boolean inferColumns) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        switch (_scopeKind)
        {
            case Normal:
            default:
                return bindNameInNormalScope(name, match, location, includeRowScope, inferColumns);
            case Aggregate:
                return bindNameInAggregateScope(name, match, location);
            case Option:
                return bindNameInOptionScope(name, match, location);
            case PlugIn:
                return bindNameInPlugInScope(name, match, location);
        }
    }

    private SemanticInfo bindName(String name, int match, SyntaxNode location, boolean includeRowScope) // PORT: §3.12 inferColumns = true
    {
        return bindName(name, match, location, includeRowScope, true);
    }

    private SemanticInfo bindName(String name, int match, SyntaxNode location) // PORT: §3.12 includeRowScope = true
    {
        return bindName(name, match, location, true, true);
    }

    private SemanticInfo bindNameInAggregateScope(String name, int match, SyntaxNode location) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        // bind using normal scope but do not allow columns, and allow aggregate functions
        return bindNameInNormalScope(name, match, location, false, false);
    }

    private SemanticInfo bindNameInOptionScope(String name, int match, SyntaxNode location) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        var list = s_symbolListPool.allocateFromPool();
        try
        {
            var option = _globals.getOption(name);
            if (option != null)
                list.add(option);
            return getMatchingSymbolResult(name, location, list, false);
        }
        finally
        {
            s_symbolListPool.returnToPool(list);
        }
    }

    private SemanticInfo bindNameInPlugInScope(String name, int match, SyntaxNode location) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        var list = s_symbolListPool.allocateFromPool();
        try
        {
            getFunctionsInPlugInScope(location, name, IncludeFunctionKind.All, list);
            return getMatchingSymbolResult(name, location, list, false);
        }
        finally
        {
            s_symbolListPool.returnToPool(list);
        }
    }

    private SemanticInfo bindNameInNormalScope(String name, int match, SyntaxNode location, boolean includeRowScope, boolean inferColumns) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        if ("".equals(name)) // PORT: §3.14 string ==
            return ErrorInfo;

        var list = s_symbolListPool.allocateFromPool();
        try
        {
            boolean allowZeroArgumentInvocation = false;

            if (_pathScope != null)
            {
                if (getMacroExpandScope(location) instanceof EntityGroupElementSymbol eges)
                {
                    eges.getMembers(name, match, list);
                    if (list.size() > 0)
                    {
                        return getMatchingSymbolResult(name, location, list, allowZeroArgumentInvocation);
                    }
                }

                if (_pathScope instanceof DynamicBagSymbol)
                {
                    _pathScope.getMembers(name, match, list);
                    if (list.size() == 1
                        && list.get(0) instanceof ColumnSymbol col)
                    {
                        return new SemanticInfo(ScalarTypes.getDynamic(col.type()));
                    }
                    else
                    {
                        // x.y where x is a known bag will at least return dynamic
                        return LiteralDynamicInfo;
                    }
                }
                else if (_pathScope instanceof DynamicSymbol)
                {
                    // any x.y where x is dynamic, is also dynamic
                    return LiteralDynamicInfo;
                }
                else if (_pathScope == ScalarTypes.Unknown)
                {
                    // any x.y where x is unknown, is also unknown (though probably dynamic)
                    return UnknownInfo;
                }
                else if (_pathScope == ErrorSymbol.Instance)
                {
                    // any x.y where x is an error, is also an error
                    return ErrorInfo;
                }
                else if (_pathScope instanceof GroupSymbol grp
                    && isPassThrough(grp))
                {
                    // get all symbols for all databases
                    var savePathScope = _pathScope;
                    for (Symbol s : grp.members())
                    {
                        _pathScope = s;
                        var alz = getMatchingSymbolsInNormalScope(name, match, location, list, includeRowScope, inferColumns);
                        allowZeroArgumentInvocation |= alz;
                    }
                    _pathScope = savePathScope;

                    makeDistinct(list);
                    return getMatchingSymbolResult(name, location, list, allowZeroArgumentInvocation);
                }
            }
            else if ("$left".equals(name) && _rowScope != null && _rightRowScope != null) // PORT: §3.14 string ==
            {
                var tuple = getTuple(_rowScope);
                return new SemanticInfo(tuple, tuple);
            }
            else if ("$right".equals(name) && _rightRowScope != null) // PORT: §3.14 string ==
            {
                var tuple = getTuple(_rightRowScope);
                return new SemanticInfo(tuple, tuple);
            }

            if (list.size() == 0)
            {
                allowZeroArgumentInvocation = getMatchingSymbolsInNormalScope(name, match, location, list, includeRowScope, inferColumns);
            }

            return getMatchingSymbolResult(name, location, list, allowZeroArgumentInvocation);
        }
        finally
        {
            s_symbolListPool.returnToPool(list);
        }
    }

    private boolean canBindName(String name, int match, SyntaxNode location, boolean includeRowScope, boolean inferColumns) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        if ("".equals(name)) // PORT: §3.14 string ==
            return false;

        var list = s_symbolListPool.allocateFromPool();
        try
        {
            if (_pathScope != null)
            {
                if (_pathScope == ScalarTypes.Dynamic)
                {
                    // any x.y where x is dynamic, is also dynamic
                    return true;
                }
                else if (_pathScope == ScalarTypes.Unknown)
                {
                    // any x.y where x is unknown, is also unknown (though probably dynamic)
                    return true;
                }
                else if (_pathScope == ErrorSymbol.Instance)
                {
                    // any x.y where x is an error, is also an error
                    return true;
                }
                else if (_pathScope instanceof GroupSymbol grp
                    && isPassThrough(grp))
                {
                    // get all symbols for all databases
                    var savePathScope = _pathScope;
                    for (Symbol s : grp.members())
                    {
                        _pathScope = s;
                        getMatchingSymbolsInNormalScope(name, match, location, list, includeRowScope, inferColumns); // PORT: §3.14 discard (`var _` is not a Java 21 identifier)
                    }
                    _pathScope = savePathScope;

                    return list.size() > 0;
                }
            }
            else if ("$left".equals(name) && _rowScope != null && _rightRowScope != null) // PORT: §3.14 string ==
            {
                return true;
            }
            else if ("$right".equals(name) && _rightRowScope != null) // PORT: §3.14 string ==
            {
                return true;
            }

            if (list.size() == 0)
            {
                getMatchingSymbolsInNormalScope(name, match, location, list, includeRowScope, inferColumns); // PORT: §3.14 discard (`var _` is not a Java 21 identifier)
            }

            return list.size() > 0;
        }
        finally
        {
            s_symbolListPool.returnToPool(list);
        }
    }

    private boolean canBindName(String name, int match, SyntaxNode location, boolean includeRowScope) // PORT: §3.12 inferColumns = true
    {
        return canBindName(name, match, location, includeRowScope, true);
    }

    private boolean canBindName(String name, int match, SyntaxNode location) // PORT: §3.12 includeRowScope = true
    {
        return canBindName(name, match, location, true, true);
    }

    private static boolean isFunctionCallName(SyntaxNode name)
    {
        return name.parent() instanceof FunctionCallExpression fn && fn.name() == name;
    }

    /// <summary>
    /// Returns true if the location is inside a database function body
    /// for the current database.
    /// </summary>
    public boolean isInsideCurrentDatabaseFunctionBody(SyntaxNode location)
    {
        if (getCurrentDatabaseFunctionName(location) != null)
            return true;

        if (isInsideCreateFunctionCommand(location))
            return true;

        return false;
    }

    /// <summary>
    /// Gets the name of the current database function declaration the location is inside of.
    /// </summary>
    private String getCurrentDatabaseFunctionName(SyntaxNode location)
    {
        if (getCurrentDatabaseFunction() instanceof FunctionSymbol fn)
        {
            return fn.name();
        }

        return getCreateFunctionCommandName(location);
    }

    /// <summary>
    /// Returns true if the function symbol we are currently analyzing
    /// is from the current database.
    /// </summary>
    private FunctionSymbol getCurrentDatabaseFunction()
    {
        var binder = this;

        while (true)
        {
            if (binder._currentFunction != null
                && _globals.getDatabase(binder._currentFunction) == _currentDatabase)
            {
                return binder._currentFunction;
            }

            // try outer binder in case the current function is a local function
            // inside a database function.
            if (binder._outerBinder != null)
            {
                binder = binder._outerBinder;
                continue;
            }

            return null;
        }
    }

    /// <summary>
    /// Returns true if the location is inside a create function command.
    /// </summary>
    private static boolean isInsideCreateFunctionCommand(SyntaxNode location)
    {
        // get the node from in the original tree
        // in case we are analyzing a tree fragment.
        location = location.getOriginalNode();

        while (true)
        {
            var functionDeclaration = location.getFirstAncestor(FunctionDeclaration.class); // PORT: §3.10
            if (functionDeclaration != null)
            {
                if (functionDeclaration.parent() instanceof CustomNode)
                    return true;
                location = functionDeclaration;
                continue;
            }

            var functionBody = location.getFirstAncestor(FunctionBody.class); // PORT: §3.10
            if (functionBody != null)
            {
                if (functionBody.parent() instanceof CustomNode)
                    return true;
                location = functionBody;
                continue;
            }

            return false;
        }
    }

    /// <summary>
    /// Returns the name of the function that the create function command is creating.
    /// </summary>
    private static String getCreateFunctionCommandName(SyntaxNode location)
    {
        // get the node from in the original tree
        // in case we are analyzing a tree fragment.
        location = location.getOriginalNode();

        while (true)
        {
            var functionDeclaration = location.getFirstAncestor(FunctionDeclaration.class); // PORT: §3.10
            if (functionDeclaration != null)
            {
                if (functionDeclaration.parent() instanceof CustomNode cn)
                    return getFunctionName(cn);

                location = functionDeclaration;
                continue;
            }

            var functionBody = location.getFirstAncestor(FunctionBody.class); // PORT: §3.10
            if (functionBody != null)
            {
                if (functionBody.parent() instanceof CustomNode cn)
                    return getFunctionName(cn);
                location = functionBody;
                continue;
            }

            return null;
        }
    }

    /// <summary>
    /// Gets the text of the custom node named 'FunctionName'
    /// </summary>
    private static String getFunctionName(CustomNode node)
    {
        if (node.getFirstDescendant(SyntaxElement.class, cn -> "FunctionName".equals(cn.nameInParent())) instanceof SyntaxElement element // PORT: §3.10, §3.14 string ==
            && element.getFirstDescendantOrSelf(Name.class) instanceof Name name) // PORT: §3.10
        {
            return name.simpleName();
        }
        return null;
    }

    private static boolean isInsideControlCommand(SyntaxNode location)
    {
        return location.getFirstAncestor(Command.class) != null; // PORT: §3.10
    }

    private static boolean isInsideControlCommandProper(SyntaxNode location)
    {
        // its part of the control command but not part of any
        // function declaration or input query
        return isInsideControlCommand(location)
            && !isInsideCreateFunctionCommand(location)
            && !isInsideControlCommandInputQuery(location);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean isInsideControlCommandInputQuery(SyntaxNode location)
    {
        // looking for statement list that is child of a CustomNode
        // PORT: §3.10 SyntaxList<SeparatedElement<Statement>> is a reified generic type test; the element type is erased in Java,
        // so it is approximated by testing the first element (a list with no elements does not match).
        return location.getFirstAncestor(SyntaxList1.class,
            (SyntaxList1 s) -> s.parent() instanceof CustomNode
                && s.size() > 0
                && s.get(0) instanceof SeparatedElement se
                && se.element() instanceof Statement) != null;
    }

    private static boolean isInvocableFunctionName(SyntaxNode location)
    {
        // function names in non-executable parts of control commands are not invocable
        return !isInsideControlCommandProper(location);
    }

    private static boolean isPossibleInvocableFunctionWithoutArgumentList(SyntaxNode location)
    {
        return !isFunctionCallName(location)
            && isInvocableFunctionName(location);
    }

    private static boolean isEvaluateFunctionName(SyntaxNode name)
    {
        return name.parent() instanceof FunctionCallExpression fn
            && fn.parent() instanceof EvaluateOperator;
    }

    /// <summary>
    /// True if member access operators (dot) on this apply to the members
    /// as opposed to matching the members themselves.
    /// </summary>
    private static boolean isPassThrough(GroupSymbol group)
    {
        // dot access on groups of clusters or databases is meant to be 
        // the aggregate of the dot access on all the clusters or databases
        for (Symbol m : group.members()) // PORT: §3.6 hot path loop (Any)
        {
            if (m instanceof DatabaseSymbol || m instanceof ClusterSymbol)
                return true;
        }
        return false;
    }

    private static void makeDistinct(List<Symbol> list)
    {
        if (list.size() > 1)
        {
            var hset = s_symbolHashSetPool.allocateFromPool();
            var newList = s_symbolListPool.allocateFromPool();

            for (Symbol item : list)
            {
                if (!hset.contains(item))
                {
                    hset.add(item);
                    newList.add(item);
                }
            }

            list.clear();
            list.addAll(newList);

            s_symbolListPool.returnToPool(newList);
            s_symbolHashSetPool.returnToPool(hset);
        }
    }

    private boolean getMatchingSymbolsInNormalScope(String name, int match, SyntaxNode location, List<Symbol> list, boolean includeRowScope, boolean inferColumns) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        var allowZeroArgumentInvocation = false;

        if (isFunctionCallName(location))
        {
            if (_pathScope instanceof DatabaseSymbol)
            {
                if (Objects.equals(name, Functions.Table.name())) // PORT: §3.14 string ==
                {
                    list.add(Functions.Table);
                }
                else if (Objects.equals(name, Functions.ExternalTable.name())) // PORT: §3.14 string ==
                {
                    list.add(Functions.ExternalTable);
                }
                else if (Objects.equals(name, Functions.MaterializedView.name())) // PORT: §3.14 string ==
                {
                    list.add(Functions.MaterializedView);
                }
                else if (Objects.equals(name, Functions.EntityGroup.name())) // PORT: §3.14 string ==
                {
                    list.add(Functions.EntityGroup);
                }
                else if (Objects.equals(name, Functions.StoredQueryResult.name())) // PORT: §3.14 string ==
                {
                    list.add(Functions.StoredQueryResult);
                }
                else if (Objects.equals(name, Functions.Graph.name())) // PORT: §3.14 string ==
                {
                    list.add(Functions.Graph);
                }
                else
                {
                    _pathScope.getMembers(name, SymbolMatch.Function | SymbolMatch.View, list);
                }
            }
            else if (_pathScope instanceof ClusterSymbol && Objects.equals(name, Functions.Database.name())) // PORT: §3.14 string ==
            {
                list.add(Functions.Database);
            }
            else
            {
                getFunctionsInScope(_scopeKind, location, name, IncludeFunctionKind.All, list);
            }
        }
        else
        {
            // don't match the database functions that have same name as database tables
            // if we are inside declaration of a database function
            if ((_pathScope == null || _pathScope == _currentDatabase)
                && Objects.equals(getCurrentDatabaseFunctionName(location), name) // PORT: §3.14 string ==
                && _currentDatabase.getAnyTable(name) != null)
            {
                // don't allow match to function this code is inside of
                // match same name table instead.
                match &= ~SymbolMatch.Function;
            }

            // if there is a path scope, then the operation was <path>.<name>
            if (_pathScope != null)
            {
                Out<ColumnSymbol> col = new Out<ColumnSymbol>(); // PORT: §3.3 out var

                // check for inferred columns associated with scan operator step variables (encoded as tuples associated with a table)
                if (_pathScope instanceof TupleSymbol tuple
                    && tuple.relatedTable() != null
                    && tuple.relatedTable().isOpen()
                    && inferColumns
                    && tryGetDeclaredOrInferredColumn(tuple.relatedTable(), name, col))
                {
                    list.add(col.value);
                }
                // database('...').name
                else if (_pathScope instanceof DatabaseSymbol ds)
                {
                    // first look for functions
                    _pathScope.getMembers(name, match & SymbolMatch.Function, list);
                    removeFunctionsThatCannotBeInvokedWithZeroArgs(list);

                    // database functions don't require argument lists to invoke
                    allowZeroArgumentInvocation = list.size() > 0;

                    if (list.size() == 0)
                    {
                        // next look for anything else (tables)
                        _pathScope.getMembers(name, match & ~SymbolMatch.Function, list);
                    }

                    // otherwise this is possible an open table
                    if (list.size() == 0 && ds.isOpen())
                    {
                        var table = getOpenTable(name, ds);
                        list.add(table);
                        return allowZeroArgumentInvocation;
                    }
                }
                // kusto does not allow Table.Column, unless its part of a control command
                else if (!(_pathScope instanceof TableSymbol)
                    || isInsideControlCommandProper(location))
                {
                    // lookup named members
                    _pathScope.getMembers(name, match, list);
                }
            }
            else
            {
                // check binding against any columns in the row scope
                // note: the row scope is the scope containing the columns from the left that are in scope on the right of a pipe operator.
                if (list.size() == 0 && _rowScope != null && includeRowScope)
                {
                    _rowScope.getMembers(name, match, list);

                    if (list.size() == 1 && _rightRowScope != null && _commonColumnsOnly)
                    {
                        _rightRowScope.getMembers(name, match, list);

                        if (list.size() == 2)
                        {
                            // combine these matching columns into a common column
                            var left = (ColumnSymbol)list.get(0);
                            var right = (ColumnSymbol)list.get(1);
                            var commonColumn = new ColumnSymbol(left.name(), left.type(), left.description(), Arrays.asList(left, right)); // PORT: §3.12 named argument originalColumns
                            list.clear();
                            list.add(commonColumn);
                        }
                    }
                }

                // try secondary right-side row scope (used in join operator)
                if (list.size() == 0 && _rightRowScope != null)
                {
                    _rightRowScope.getMembers(name, match, list);
                }

                // try local variables (includes any user-defined functions)
                // these are defined by a previous let statement
                if (list.size() == 0)
                {
                    _localScope.getSymbols(name, match, list);

                    // user defined functions do not require argument list if it has no arguments
                    allowZeroArgumentInvocation = list.size() > 0;
                }

                // look for zero-argument database functions
                if (list.size() == 0 && isPossibleInvocableFunctionWithoutArgumentList(location)
                    && (match & SymbolMatch.Function) != 0)
                {
                    // database functions only (locally defined functions are already handled above)
                    getFunctionsInScope(_scopeKind, location, name, IncludeFunctionKind.DatabaseFunctions, list);
                    removeFunctionsThatCannotBeInvokedWithZeroArgs(list);

                    // database functions do not require argument list if it has zero arguments.
                    allowZeroArgumentInvocation = list.size() > 0;
                }

                // other items in database (tables, etc)
                if (list.size() == 0 && _currentDatabase != null)
                {
                    // try matching without external tables first
                    _currentDatabase.getMembers(name, match & ~SymbolMatch.ExternalTable, list);

                    // if nothing was found, try matching external tables (if requested)
                    if (list.size() == 0 && (match & SymbolMatch.ExternalTable) != 0)
                    {
                        _currentDatabase.getMembers(name, SymbolMatch.ExternalTable, list);
                    }
                }

                // databases can be directly referenced in commands
                if (list.size() == 0 && _currentCluster != null && (match & SymbolMatch.Database) != 0)
                {
                    _currentCluster.getMembers(name, match, list);
                }

                // look for any built-in functions with matching name (even those with parameters)
                if (list.size() == 0 && (match & SymbolMatch.Function) != 0)
                {
                    getFunctionsInScope(_scopeKind, location, name, IncludeFunctionKind.BuiltInFunctions, list);
                }

                // infer column for this otherwise unbound reference?
                if (list.size() == 0 && _rowScope != null && _rowScope.isOpen() && (match & SymbolMatch.Column) != 0
                    && includeRowScope && inferColumns)
                {
                    // row scope table has open definition, so create an inferred column for the otherwise unbound name
                    list.add(getOpenTableInferredColumn(name, _rowScope));
                }
            }
        }

        return allowZeroArgumentInvocation;
    }

    private SemanticInfo getMatchingSymbolResult(String name, SyntaxNode location, List<Symbol> matches, boolean allowZeroArgumentInvocation)
    {
        if (matches.size() == 1)
        {
            var item = matches.get(0);
            var resultType = getResultType(item);

            // check for zero-parameter function invocation not part of a function call node
            if (resultType instanceof FunctionSymbol fn && isPossibleInvocableFunctionWithoutArgumentList(location))
            {
                Signature sig = null; // PORT: §3.6 hot path loop (FirstOrDefault)
                for (Signature s : fn.signatures())
                {
                    if (s.minArgumentCount() == 0)
                    {
                        sig = s;
                        break;
                    }
                }

                if (sig != null && allowZeroArgumentInvocation)
                {
                    var funResult = getFunctionCallResult(sig, EmptyReadOnlyList.<Expression>instance(), EmptyReadOnlyList.<TypeSymbol>instance(), location);
                    return new SemanticInfo(item, funResult.type(), null, false, funResult.info()); // PORT: §3.12 named argument calledFunctionInfo
                }
                else
                {
                    var returnType = getCommonReturnType(fn.signatures(), EmptyReadOnlyList.<Expression>instance(), EmptyReadOnlyList.<TypeSymbol>instance(), location);
                    return new SemanticInfo(item, returnType, DiagnosticFacts.getFunctionRequiresArgumentList(name).withLocation(location));
                }
            }
            else if (resultType instanceof EntityGroupSymbol eg)
            {
                // entity group symbols are like function symbols that have a body to be evaluated
                // in order to determine their result type
                var result = getFunctionCallResult(eg.signature(), EmptyReadOnlyList.<Expression>instance(), EmptyReadOnlyList.<TypeSymbol>instance(), location);
                return new SemanticInfo(item, result.type(), null, false, result.info()); // PORT: §3.12 named argument calledFunctionInfo
            }
            else
            {
                return createSemanticInfo(item);
            }
        }
        else if (matches.size() == 0)
        {
            if (_scopeKind != ScopeKind.Normal)
            {
                var oldScopeKind = _scopeKind;
                _scopeKind = ScopeKind.Normal;
                getMatchingSymbolsInNormalScope(name, SymbolMatch.Any, location, matches, true, true);
                _scopeKind = oldScopeKind;

                if (matches.size() > 0)
                {
                    switch (_scopeKind)
                    {
                        case Aggregate:
                            return new SemanticInfo(ErrorSymbol.Instance, DiagnosticFacts.getInvalidNameInAggregateContext(name).withLocation(location));
                        case PlugIn:
                            return new SemanticInfo(ErrorSymbol.Instance, DiagnosticFacts.getInvalidNameInPlugInContext(name).withLocation(location));
                        case Option:
                            // invalid option names do not produce an error
                            return null;
                        default:
                            break;
                    }
                }
            }

            if (isFunctionCallName(location))
            {
                if (_globals.getAggregate(name) != null && _scopeKind != ScopeKind.Aggregate)
                {
                    return new SemanticInfo(
                        ErrorSymbol.Instance,
                        DiagnosticFacts.getAggregateNotAllowedInThisContext(name).withLocation(location));
                }
                else if (_globals.getPlugIn(name) != null && _scopeKind != ScopeKind.PlugIn)
                {
                    return new SemanticInfo(
                        ErrorSymbol.Instance,
                        DiagnosticFacts.getPluginNotAllowedInThisContext(name).withLocation(location));
                }
                else if (isEvaluateFunctionName(location))
                {
                    if (PlugIns.getPlugIn(name) != null)
                    {
                        return new SemanticInfo(
                            ErrorSymbol.Instance,
                            DiagnosticFacts.getPlugInFunctionIsNotEnabled(name).withLocation(location));
                    }
                    else
                    {
                        return new SemanticInfo(
                            ErrorSymbol.Instance,
                            DiagnosticFacts.getPlugInFunctionNotDefined(name).withLocation(location));
                    }
                }
                else if (_isFuzzy)
                {
                    return null;
                }
                else if (_pathScope instanceof DatabaseSymbol ds)
                {
                    if (ds != null && ds.isOpen())
                    {
                        return new SemanticInfo(new TableSymbol("").withIsOpen(true));
                    }
                    else
                    {
                        return new SemanticInfo(
                            new TableSymbol("").withIsOpen(true),
                            DiagnosticFacts.getNameDoesNotReferToAnyKnownFunction(name).withLocation(location));
                    }
                }
                else if (isInTabularContext(location))
                {
                    return new SemanticInfo(
                        new TableSymbol("").withIsOpen(true),
                        DiagnosticFacts.getNameDoesNotReferToAnyKnownFunction(name).withLocation(location));
                }
                else if (isPossibleInternalFunction(name))
                {
                    // if it looks like it might be an internal function, give it a pass.
                    return UnknownInfo;
                }
                else
                {
                    return new SemanticInfo(
                        ErrorSymbol.Instance,
                        DiagnosticFacts.getNameDoesNotReferToAnyKnownFunction(name).withLocation(location));
                }
            }
            else if (isInTabularContext(location))
            {
                if (_isFuzzy)
                {
                    // unknown table name in fuzzy context?
                    return new SemanticInfo(
                        new TableSymbol().withIsOpen(true),
                        DiagnosticFacts.getFuzzyTableNotDefined(name).withLocation(location));
                }
                else if (_pathScope instanceof DatabaseSymbol ds
                    && ds.isOpen())
                {
                    return new SemanticInfo(new TableSymbol(name).withIsOpen(true));
                }
                else
                {
                    return new SemanticInfo(
                        new TableSymbol(name).withIsOpen(true),
                        DiagnosticFacts.getNameDoesNotReferToAnyKnownTable(name).withLocation(location));
                }
            }

            return new SemanticInfo(
                ErrorSymbol.Instance,
                DiagnosticFacts.getNameDoesNotReferToAnyKnownItem(name).withLocation(location));
        }
        else
        {
            return new SemanticInfo(
                new GroupSymbol(new ArrayList<Symbol>(matches)),
                ErrorSymbol.Instance,
                DiagnosticFacts.getNameRefersToMoreThanOneItem(name).withLocation(location));
        }
    }

    private static boolean isPossibleInternalFunction(String name)
    {
        return name.startsWith("__"); // PORT: D12 culture-sensitive StartsWith → ordinal
    }

    private static void removeFunctionsThatCannotBeInvokedWithZeroArgs(List<Symbol> list)
    {
        for (int i = list.size() - 1; i >= 0; i--)
        {
            if (list.get(i) instanceof FunctionSymbol fn && fn.minArgumentCount() > 0)
            {
                list.remove(i);
            }
        }
    }

    private static void getWildcardSymbols(String pattern, List<Symbol> symbols, List<Symbol> matchingSymbols)
    {
        for (Symbol symbol : symbols)
        {
            if (KustoFacts.matches(pattern, symbol.name()))
            {
                matchingSymbols.add(symbol);
            }
        }
    }

    /// <summary>
    /// Determine if the element (a name) is in a known tabular context
    /// </summary>
    private static boolean isInTabularContext(SyntaxElement element)
    {
        // if function call name, look further up to determine context
        if (element.parent() instanceof FunctionCallExpression fc
            && fc.name() == element)
        {
            element = element.parent();
        }

        // if inside parenthesis or part of a list, look further up
        while (element.parent() instanceof ParenthesizedExpression
            || element.parent() instanceof SyntaxList
            || element.parent() instanceof SeparatedElement)
        {
            element = element.parent();
        }

        if (element.parent() != null)
        {
            // if x | op then x is expected to be tabular
            if (element.parent() instanceof PipeExpression pe && pe.expression() == element)
            {
                return true;
            }

            // if database().x then x is expected to be tabular
            if (element.parent() instanceof PathExpression pt
                && pt.selector() == element)
            {
                if (pt.expression().resultType() instanceof DatabaseSymbol)
                    return true;

                if (pt.expression().resultType() instanceof GroupSymbol g)
                {
                    for (Symbol m : g.members()) // PORT: §3.6 hot path loop (Any)
                    {
                        if (m instanceof DatabaseSymbol)
                            return true;
                    }
                }
            }

            // use completion hint to help us determine if context is tabular
            var hint = element.parent().getCompletionHint(element.indexInParent());

            if (hint == CompletionHint.Table
                || hint == CompletionHint.Tabular
                || hint == CompletionHint.NonScalar
                || hint == CompletionHint.MaterializedView
                || hint == CompletionHint.ExternalTable)
            {
                return true;
            }
        }

        return false;
    }

    // ===== upstream part: Binder_NodeBinder.cs =====
    /// <summary>
    /// The <see cref="NodeBinder"/> is a <see cref="SyntaxVisitor"/> that computes
    /// the <see cref="SemanticInfo"/> foreach each kind of <see cref="SyntaxNode"/>.
    /// </summary>
    private static final class NodeBinder extends SyntaxVisitor1<SemanticInfo>
    {
        private final Binder _binder;

        public NodeBinder(Binder binder)
        {
            _binder = binder;
        }

        public TableSymbol rowScopeOrEmpty() { return _binder.rowScopeOrEmpty(); } // PORT: §3.1
        public TableSymbol rightRowScopeOrEmpty() { return _binder.rightRowScopeOrEmpty(); } // PORT: §3.1

        // region declarations
        @Override
        public SemanticInfo visitNameAndTypeDeclaration(NameAndTypeDeclaration node)
        {
            // this declaration does not have a type on its own
            return VoidInfo;
        }

        @Override
        public SemanticInfo visitFunctionDeclaration(FunctionDeclaration node)
        {
            var diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                var parameters = new ArrayList<Parameter>();

                // first series of parameters can be tabular
                var canBeTabular = true;

                // get parameter symbols already defined
                for (int i = 0; i < node.parameters().parameters().size(); i++)
                {
                    var fp = node.parameters().parameters().get(i).element();

                    boolean isOptional = fp.defaultValue() != null;

                    if (fp.nameAndType().name().referencedSymbol() instanceof ParameterSymbol p)
                    {
                        if (p.isTabular() && !canBeTabular)
                        {
                            diagnostics.add(DiagnosticFacts.getTabularParametersMustBeDeclaredFirst().withLocation(fp));
                        }
                        canBeTabular = p.isTabular();

                        parameters.add(Parameter.from(p, isOptional, fp.defaultValue() != null ? fp.defaultValue().value() : null)); // PORT: §3.14 ?.
                    }
                }

                var name = getNameFromContext(node);
                var fs = new FunctionSymbol(name, node.body(), parameters).withIsView(node.viewKeyword() != null);

                return new SemanticInfo(fs, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitFunctionParameter(FunctionParameter node)
        {
            if (node.defaultValue() != null)
            {
                var diagnostics = s_diagnosticListPool.allocateFromPool();
                try
                {
                    var type = _binder.getTypeFromTypeExpression(node.nameAndType().type());
                    if (type != null && !type.isError())
                    {
                        if (_binder.checkIsLiteralNotToken(node.defaultValue().value(), diagnostics))
                        {
                            _binder.checkIsType(node.defaultValue().value(), type, Conversion.Compatible, diagnostics);
                        }
                    }

                    if (diagnostics.size() > 0)
                    {
                        return new SemanticInfo(diagnostics);
                    }
                }
                finally
                {
                    s_diagnosticListPool.returnToPool(diagnostics);
                }
            }

            return null;
        }

        @Override
        public SemanticInfo visitDefaultValueDeclaration(DefaultValueDeclaration node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitFunctionBody(FunctionBody node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitFunctionParameters(FunctionParameters node)
        {
            return null;
        }

        /// <summary>
        /// Get the name the expression will be assigned by a let statement
        /// it is part of, or empty string.
        /// </summary>
        private static String getNameFromContext(Expression expr)
        {
            return (expr.parent() instanceof LetStatement ls) ? ls.name().simpleName() : "";
        }

        @Override
        public SemanticInfo visitPatternStatement(PatternStatement node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitPatternDeclaration(PatternDeclaration node)
        {
            var diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                var patternsigs = new ArrayList<PatternSignature>();

                var parameters = new ArrayList<Parameter>();
                for (int i = 0, n = node.parameters().size(); i < n; i++)
                {
                    var parameter = node.parameters().get(0).element(); // PORT-BUG: upstream indexes Parameters[0] instead of Parameters[i]
                    var name = parameter.name().simpleName();
                    var type = _binder.getTypeFromTypeExpression(parameter.type(), diagnostics);
                    parameters.add(new Parameter(name, type));
                }

                Parameter pathParameter = null;
                if (node.pathParameter() != null)
                {
                    var name = node.pathParameter().parameter().name().simpleName();
                    var type = _binder.getTypeFromTypeExpression(node.pathParameter().parameter().type());
                    pathParameter = new Parameter(name, type);
                }

                for (int i = 0, n = node.patterns().size(); i < n; i++)
                {
                    var pattern = node.patterns().get(i);

                    // check match values
                    if (pattern.parameterValues().expressions().size() != node.parameters().size())
                    {
                        diagnostics.add(DiagnosticFacts.getValueCountMustEqualParameterCount().withLocation(pattern.parameterValues()));
                    }

                    // check all match values are literals of the correct type
                    var values = new ArrayList<String>();

                    for (int v = 0, vn = pattern.parameterValues().expressions().size(); v < vn; v++)
                    {
                        var parameter = node.parameters().get(v).element();
                        var value = pattern.parameterValues().expressions().get(v).element();

                        // all values must be string literals
                        if (_binder.checkIsExactType(value, ScalarTypes.String, diagnostics))
                        {
                            _binder.checkIsLiteral(value, diagnostics);
                        }

                        values.add(DotNet.str(value.literalValue())); // PORT: §3.14 ?.ToString() ?? ""
                    }

                    // check path value
                    String pathValue = null;

                    if (pattern.pathValue() == null && node.pathParameter() != null)
                    {
                        diagnostics.add(DiagnosticFacts.getPathValueExpected().withLocation(pattern.equalToken()));
                    }
                    else if (pattern.pathValue() != null && node.pathParameter() == null)
                    {
                        diagnostics.add(DiagnosticFacts.getPathValueWithNoPathParameter().withLocation(pattern.pathValue()));
                    }
                    else if (pattern.pathValue() != null && node.pathParameter() != null)
                    {
                        // path value must be string literal
                        if (_binder.checkIsExactType(pattern.pathValue().value(), ScalarTypes.String, diagnostics))
                        {
                            _binder.checkIsLiteral(pattern.pathValue().value(), diagnostics);
                        }

                        pathValue = DotNet.str(pattern.pathValue().value().literalValue()); // PORT: §3.14 ?.ToString() ?? ""
                    }

                    patternsigs.add(new PatternSignature(values, pathValue, pattern.body()));
                }

                var patternName = node.parent() instanceof PatternStatement ps ? ps.name().simpleName() : null; // PORT: §3.14 as, ?.
                var resultType = new PatternSymbol(patternName, parameters, pathParameter, patternsigs);

                return new SemanticInfo(resultType, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitMaterializeExpression(MaterializeExpression node)
        {
            var diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                _binder.checkIsTabularOrGraph(node.expression(), diagnostics);
                return new SemanticInfo(getResultTypeOrError(node.expression()), diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitNameDeclaration(NameDeclaration node)
        {
            return null;
        }

        // endregion

        // region literals
        @Override
        public SemanticInfo visitLiteralExpression(LiteralExpression node)
        {
            switch (node.kind())
            {
                case BooleanLiteralExpression:
                    return LiteralBoolInfo;
                case IntLiteralExpression:
                    return LiteralIntInfo;
                case LongLiteralExpression:
                    return LiteralLongInfo;
                case RealLiteralExpression:
                    return LiteralRealInfo;
                case DecimalLiteralExpression:
                    if (_binder._dynamicDepth != 0)
                    {
                        return new SemanticInfo(ScalarTypes.Decimal, DiagnosticFacts.getDecimalInDynamic().withLocation(node));
                    }
                    return LiteralDecimalInfo;
                case StringLiteralExpression:
                    return LiteralStringInfo;
                case DateTimeLiteralExpression:
                    return LiteralDateTimeInfo;
                case TimespanLiteralExpression:
                    return LiteralTimeSpanInfo;
                case GuidLiteralExpression:
                    return LiteralGuidInfo;
                case TokenLiteralExpression:
                    return VoidInfo;
                case NullLiteralExpression:
                    return LiteralNullInfo;
                default:
                    throw new IllegalStateException("Unknown literal kind: " + DotNet.str(node.kind())); // PORT: §3.16 InvalidOperationException
            }
        }

        @Override
        public SemanticInfo visitTypeOfLiteralExpression(TypeOfLiteralExpression node)
        {
            var diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                TypeSymbol type;

                if (node.types().size() == 1 && node.types().get(0).element() instanceof PrimitiveTypeExpression pt)
                {
                    type = Binder.getType(pt, diagnostics);
                }
                else
                {
                    var columns = s_columnListPool.allocateFromPool();
                    try
                    {
                        for (int i = 0; i < node.types().size(); i++)
                        {
                            var element = node.types().get(i).element();
                            // PORT: §3.15 switch (element) type patterns → instanceof chain
                            if (element instanceof StarExpression s)
                            {
                                if (_binder._rowScope != null)
                                {
                                    // include all columns in scope
                                    columns.addAll(_binder.getDeclaredAndInferredColumns(rowScopeOrEmpty()));
                                }
                                else
                                {
                                    diagnostics.add(DiagnosticFacts.getNoColumnsInScope().withLocation(s));
                                }
                            }
                            else if (element instanceof NameAndTypeDeclaration nat)
                            {
                                var declaredType = _binder.getTypeFromTypeExpression(nat.type(), diagnostics);
                                var newColumn = new ColumnSymbol(nat.name().simpleName(), declaredType);
                                columns.add(newColumn);
                            }
                            else
                            {
                                diagnostics.add(DiagnosticFacts.getInvalidColumnDeclaration().withLocation(element));
                            }
                        }

                        type = new TableSymbol(columns);
                    }
                    finally
                    {
                        s_columnListPool.returnToPool(columns);
                    }
                }

                return new SemanticInfo(type, ScalarTypes.Type, diagnostics, true); // PORT: §3.12 named argument isConstant
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitDynamicExpression(DynamicExpression node)
        {
            var info = node.expression().getSemanticInfo();
            return new SemanticInfo(ScalarTypes.getDynamic(info.resultType()), (Iterable<Diagnostic>) null, true); // PORT: §3.12 named argument isConstant, skipped diagnostics = null
        }

        @Override
        public SemanticInfo visitCompoundStringLiteralExpression(CompoundStringLiteralExpression node)
        {
            return LiteralStringInfo;
        }
        // endregion

        // region scalar operators
        @Override
        public SemanticInfo visitBinaryExpression(BinaryExpression node)
        {
            var opKind = getOperatorKind(node.kind());
            if (opKind != OperatorKind.None)
            {
                return _binder.getBinaryOperatorInfo(opKind, node.left(), node.right(), node.operator());
            }
            else
            {
                throw new IllegalStateException("Unknown binary operator kind: " + DotNet.str(node.kind())); // PORT: §3.16 InvalidOperationException
            }
        }

        @Override
        public SemanticInfo visitPrefixUnaryExpression(PrefixUnaryExpression node)
        {
            var opKind = getOperatorKind(node.kind());
            if (opKind != OperatorKind.None)
            {
                return _binder.getUnaryOperatorInfo(opKind, node.expression(), node.operator());
            }
            else
            {
                throw new IllegalStateException("Unknown unary operator kind: " + DotNet.str(node.kind())); // PORT: §3.16 InvalidOperationException
            }
        }

        @Override
        public SemanticInfo visitInExpression(InExpression node)
        {
            var dx = s_diagnosticListPool.allocateFromPool();
            var args = s_expressionListPool.allocateFromPool();
            try
            {
                args.add(node.left());

                for (int i = 0; i < node.right().expressions().size(); i++)
                {
                    args.add(node.right().expressions().get(i).element());
                }

                var op = SyntaxFacts.getOperatorKind(node.operator().kind());
                return _binder.getOperatorInfo(op, args, node.operator());
            }
            finally
            {
                s_diagnosticListPool.returnToPool(dx);
                s_expressionListPool.returnToPool(args);
            }
        }

        @Override
        public SemanticInfo visitHasAnyExpression(HasAnyExpression node)
        {
            var dx = s_diagnosticListPool.allocateFromPool();
            var args = s_expressionListPool.allocateFromPool();
            try
            {
                args.add(node.left());

                for (int i = 0; i < node.right().expressions().size(); i++)
                {
                    args.add(node.right().expressions().get(i).element());
                }

                var op = SyntaxFacts.getOperatorKind(node.operator().kind());
                return _binder.getOperatorInfo(op, args, node.operator());
            }
            finally
            {
                s_diagnosticListPool.returnToPool(dx);
                s_expressionListPool.returnToPool(args);
            }
        }

        @Override
        public SemanticInfo visitHasAllExpression(HasAllExpression node)
        {
            var dx = s_diagnosticListPool.allocateFromPool();
            var args = s_expressionListPool.allocateFromPool();
            try
            {
                args.add(node.left());

                for (int i = 0; i < node.right().expressions().size(); i++)
                {
                    args.add(node.right().expressions().get(i).element());
                }

                var op = SyntaxFacts.getOperatorKind(node.operator().kind());
                return _binder.getOperatorInfo(op, args, node.operator());
            }
            finally
            {
                s_diagnosticListPool.returnToPool(dx);
                s_expressionListPool.returnToPool(args);
            }
        }

        @Override
        public SemanticInfo visitBetweenExpression(BetweenExpression node)
        {
            var dx = s_diagnosticListPool.allocateFromPool();
            var args = s_expressionListPool.allocateFromPool();
            try
            {
                args.add(node.left());
                args.add(node.right().first());
                args.add(node.right().second());

                var op = SyntaxFacts.getOperatorKind(node.operator().kind());
                return _binder.getOperatorInfo(op, args, node.operator());
            }
            finally
            {
                s_diagnosticListPool.returnToPool(dx);
                s_expressionListPool.returnToPool(args);
            }
        }

        @Override
        public SemanticInfo visitToScalarExpression(ToScalarExpression node)
        {
            var dx = s_diagnosticListPool.allocateFromPool();
            try
            {
                if (node.kindParameter() != null)
                {
                    _binder.checkQueryOperatorParameter(node.kindParameter(), QueryOperatorParameters.ToScalarKindParameter, dx);
                }

                var resultType = getResultType(node.expression());
                if (resultType instanceof TableSymbol table)
                {
                    if (table.columns().size() > 0)
                    {
                        var col = table.columns().get(0);
                        return new SemanticInfo(col.type(), dx, true); // PORT: §3.12 named argument isConstant
                    }
                    else
                    {
                        dx.add(DiagnosticFacts.getTableHasNoColumns().withLocation(node.expression()));
                        return new SemanticInfo(ErrorSymbol.Instance, dx, true); // PORT: §3.12 named argument isConstant
                    }
                }
                else if (resultType instanceof ScalarSymbol)
                {
                    return new SemanticInfo(resultType, dx, true); // PORT: §3.12 named argument isConstant
                }
                else
                {
                    dx.add(DiagnosticFacts.getTableOrScalarExpected().withLocation(node.expression()));
                    return new SemanticInfo(ErrorSymbol.Instance, dx, true); // PORT: §3.12 named argument isConstant
                }
            }
            finally
            {
                s_diagnosticListPool.returnToPool(dx);
            }
        }

        @Override
        public SemanticInfo visitToTableExpression(ToTableExpression node)
        {
            var dx = s_diagnosticListPool.allocateFromPool();
            try
            {
                if (node.kindParameter() != null)
                {
                    _binder.checkQueryOperatorParameter(node.kindParameter(), QueryOperatorParameters.ToTableKindParameter, dx);
                }

                if (_binder.checkIsTabular(node.expression(), dx))
                {
                    var table = (TableSymbol)getResultType(node.expression());
                    return new SemanticInfo(table, dx);
                }

                return new SemanticInfo(ErrorSymbol.Instance, dx);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(dx);
            }
        }

        // endregion

        // region names and paths
        @Override
        public SemanticInfo visitTokenName(TokenName node)
        {
            // handled by parent node
            return null;
        }

        @Override
        public SemanticInfo visitBracketedName(BracketedName node)
        {
            // handled by parent node
            return null;
        }

        @Override
        public SemanticInfo visitBracedName(BracedName node)
        {
            // handled by parent node
            return null;
        }

        @Override
        public SemanticInfo visitWildcardedName(WildcardedName node)
        {
            // handled by parent node
            return null;
        }

        @Override
        public SemanticInfo visitBracketedWildcardedName(BracketedWildcardedName node)
        {
            // handled by parent node
            return null;
        }

        @Override
        public SemanticInfo visitNameReference(NameReference node)
        {
            // PORT: §3.15 switch (node.Name) type patterns → instanceof chain
            var nameNode = node.name();
            if (nameNode instanceof TokenName)
            {
                return _binder.bindName(node.simpleName(), node.match(), node);
            }
            else if (nameNode instanceof BracketedName)
            {
                return _binder.bindName(node.simpleName(), node.match(), node);
            }
            else if (nameNode instanceof WildcardedName wc)
            {
                return visitWildcardedNameReference(node, wc.simpleName());
            }
            else if (nameNode instanceof BracketedWildcardedName bwc)
            {
                return visitWildcardedNameReference(node, bwc.simpleName());
            }
            else if (nameNode instanceof BracedName)
            {
                return visitClientParameterReference(node);
            }
            else
            {
                throw new UnsupportedOperationException(); // PORT: §3.16 NotImplementedException
            }
        }

        private SemanticInfo visitClientParameterReference(NameReference node)
        {
            if (_binder._globals.getProperty(Properties.AllowClientParameters))
            {
                // check for supplied symbol for client parameter
                if (_binder._globals.getClientSymbol(node.simpleName()) instanceof Symbol symbol)
                {
                    return getSemanticInfo(symbol, (Diagnostic[]) null); // PORT: §3.12 params Diagnostic[] passed null (upstream passes null)
                }
                else
                {
                    // client parameter does not have a known type
                    if (isInTabularContext(node))
                    {
                        // but it is probably tabular
                        var table = new TableSymbol(node.simpleName()).withIsOpen(true);
                        return new SemanticInfo(table);
                    }
                    else
                    {
                        // otherwise its an unknown scalar
                        return new SemanticInfo(ScalarTypes.Unknown);
                    }
                }
            }
            else
            {
                // error if client parameters not supported
                return new SemanticInfo(ScalarTypes.Unknown, DiagnosticFacts.getClientParametersNotSupported().withLocation(node));
            }
        }

        private SemanticInfo visitWildcardedNameReference(NameReference node, String pattern)
        {
            var candidateList = s_symbolListPool.allocateFromPool();
            var matchingList = s_symbolListPool.allocateFromPool();

            try
            {
                if (isInWildcardedColumnContext(node))
                {
                    _binder.getSymbolsInContext(node, SymbolMatch.Column, IncludeFunctionKind.None, candidateList);
                    getWildcardSymbols(pattern, candidateList, matchingList);

                    if (matchingList.size() == 1)
                    {
                        return createSemanticInfo(matchingList.get(0));
                    }
                    else if (matchingList.size() > 1)
                    {
                        return createSemanticInfo(new GroupSymbol(matchingList));
                    }
                    else if (_binder._rowScope != null && _binder._rowScope.isOpen())
                    {
                        // this might match zero or more columns from the row scope if it is open
                        return createSemanticInfo(new GroupSymbol());
                    }
                    else
                    {
                        return new SemanticInfo(ErrorSymbol.Instance, DiagnosticFacts.getNameDoesNotReferToAnyKnownItem(pattern, "column").withLocation(node));
                    }
                }
                else if (isInWildcardedTableContext(node))
                {
                    if (_binder._pathScope instanceof DatabaseSymbol || 
                        _binder._pathScope instanceof GroupSymbol g && Linq.all(g.members(), m -> m instanceof DatabaseSymbol)) // PORT: §3.6 g.Members.All(...)
                    {
                        // match all tables in specified database
                        _binder.getSymbolsInContext(node, SymbolMatch.Table, IncludeFunctionKind.None, candidateList);                            
                    }
                    else if (_binder._currentDatabase != null)
                    {
                        // match all tables in database only
                        _binder._currentDatabase.getMembers(SymbolMatch.Table, candidateList);
                        // match local views
                        _binder.getSymbolsInContext(node, SymbolMatch.View, IncludeFunctionKind.LocalViews, candidateList);
                    }

                    getWildcardSymbols(pattern, candidateList, matchingList);

                    if (matchingList.size() == 1)
                    {
                        return createSemanticInfo(matchingList.get(0));
                    }
                    else if (matchingList.size() > 1)
                    {
                        return createSemanticInfo(new GroupSymbol(matchingList));
                    }
                    else
                    {
                        return new SemanticInfo(ErrorSymbol.Instance, DiagnosticFacts.getNameDoesNotReferToAnyKnownItem(pattern, "table").withLocation(node));
                    }
                }
                else
                {
                    return new SemanticInfo(ErrorSymbol.Instance, DiagnosticFacts.getWildcardedNamesNotAllowedInThisContext().withLocation(node));
                }
            }
            finally
            {
                s_symbolListPool.returnToPool(candidateList);
                s_symbolListPool.returnToPool(matchingList);
            }
        }

        private static boolean isInWildcardedColumnContext(SyntaxElement element)
        {
            // project-away, project-keep and project-reorder operators only allows columns to bind in wildcards
            return element.getFirstAncestor(QueryOperator.class) instanceof QueryOperator op
                && (op instanceof ProjectAwayOperator || op instanceof ProjectKeepOperator || op instanceof ProjectReorderOperator);
        }

        private static boolean isInWildcardedTableContext(SyntaxElement element)
        {
            return (element.getFirstAncestor(QueryOperator.class) instanceof QueryOperator op
                    && (op instanceof UnionOperator || op instanceof FindOperator || op instanceof SearchOperator))
                || (element.getFirstAncestor(Statement.class) instanceof RestrictStatement);
        }

        @Override
        public SemanticInfo visitBracketedExpression(BracketedExpression node)
        {
            if (node.parent() instanceof ElementExpression ee && ee.selector() == node)
            {
                // element selector: container[indexer]
                return getElementExpressionInfo(ee.expression(), node);
            }
            else if (node.parent() instanceof PathExpression pe && pe.selector() == node)
            {
                // path selector: container.[indexer] 
                // treat same as element selector
                return getElementExpressionInfo(pe.expression(), node);
            }
            else
            {
                // unqualified bracketed expression -- this might happen in a partially typed case
                // or an incorrectly typed case:  [foo]  when they meant to type ['foo']
                var indexerType = getResultTypeOrError(node.expression());

                if (indexerType.isError())
                {
                    return ErrorInfo;
                }

                if (indexerType != ScalarTypes.String)
                {
                    return new SemanticInfo(ErrorSymbol.Instance, DiagnosticFacts.getExpressionMustHaveType(ScalarTypes.String).withLocation(node.expression()));
                }
                else if (!node.expression().isLiteral())
                {
                    // computed name lookup?? Is this valid here?
                    return new SemanticInfo(ErrorSymbol.Instance, DiagnosticFacts.getExpressionMustBeLiteral().withLocation(node.expression()));
                }
                else
                {
                    // we should never reach here, but if we do then treat it like a valid name reference
                    return _binder.bindName((String)node.expression().literalValue(), SymbolMatch.Default, node.expression());
                }
            }
        }

        private SemanticInfo getElementExpressionInfo(Expression collection, BracketedExpression selector)
        {
            var indexerType = getResultTypeOrError(selector.expression());
            if (indexerType.isError())
            {
                return ErrorInfo;
            }

            var collectionType = collection.resultType();
            if (collectionType == null || collectionType.isError())
            {
                return ErrorInfo;
            }
            else if (collectionType instanceof DynamicArraySymbol arrayType)
            {
                var elementType = ScalarTypes.getDynamic(arrayType.elementType());

                if (!TypeFacts.isIntegerOrDynamic(indexerType))
                {
                    // must be a integer array index (dynamic okay)
                    return new SemanticInfo(elementType, DiagnosticFacts.getExpressionMustHaveType(ScalarTypes.Int, ScalarTypes.Long).withLocation(selector.expression()));
                }
                else
                {
                    // you've successfully accessed an element of the array.
                    return new SemanticInfo(elementType);
                }
            }
            else if (collectionType instanceof DynamicBagSymbol bagType)
            {
                Out<ColumnSymbol> prop = new Out<>(); // PORT: §3.3 out var prop

                if (!TypeFacts.isStringOrDynamic(indexerType))
                {
                    // must be a string member name index (dynamic okay)
                    return new SemanticInfo(ScalarTypes.Dynamic, DiagnosticFacts.getExpressionMustHaveType(ScalarTypes.String).withLocation(selector.expression()));
                }
                else if (selector.expression().constantValue() instanceof String name
                        && bagType.tryGetProperty(name, prop))
                {
                    // you've successfully accessed a known property of the dynamic bag
                    // you get a dynamic version of whatever type the property is.
                    return new SemanticInfo(ScalarTypes.getDynamic(prop.value.type()));
                }
                else
                {
                    // you've successfully accessed an element of a dynamic value
                    // you get another dynamic value.
                    return new SemanticInfo(ScalarTypes.Dynamic);
                }
            }
            else if (collectionType == ScalarTypes.Dynamic)
            {
                if (!TypeFacts.isStringOrDynamic(indexerType) && !TypeFacts.isIntegerOrDynamic(indexerType))
                {
                    // must be a integer array index or a string member name index (dynamic okay)
                    return new SemanticInfo(ScalarTypes.Dynamic, DiagnosticFacts.getExpressionMustHaveType(ScalarTypes.Int, ScalarTypes.Long, ScalarTypes.String).withLocation(selector.expression()));
                }
                else
                {
                    // you've successfully accessed an element of a dynamic value: you get another dynamic value.
                    return new SemanticInfo(ScalarTypes.Dynamic);
                }
            }
            else if (collectionType instanceof TupleSymbol ts)
            {
                Out<Integer> index = new Out<>(); // PORT: §3.3 out var index

                if (TypeFacts.isInteger(indexerType)
                    && selector.expression().isConstant()
                    && tryGetIntValue(selector.expression().constantValue(), index))
                {
                    if (index.value >= 0 && index.value < ts.columns().size())
                    {
                        var col = ts.columns().get(index.value);
                        return new SemanticInfo(col, col.type());
                    }
                }

                return new SemanticInfo(ScalarTypes.Unknown);
            }
            else if (collectionType == ScalarTypes.Unknown)
            {
                // unknown is unknown
                return new SemanticInfo(ScalarTypes.Unknown);
            }
            else
            {
                // element access only works for dynamic values
                return new SemanticInfo(ErrorSymbol.Instance, DiagnosticFacts.getTheElementAccessOperatorIsNotAllowedInThisContext().withLocation(selector));
            }
        }

        private static boolean tryGetIntValue(Object value, Out<Integer> intValue) // PORT: §3.3 out int
        {
            if (value instanceof Integer iVal)
            {
                intValue.value = iVal;
                return true;
            }
            else if (value instanceof Long longVal)
            {

                intValue.value = (int)(long)longVal; // PORT: §3.7 unchecked (int)longVal truncation
                return true;
            }
            else
            {
                intValue.value = 0;
                return false;
            }
        }

        @Override
        public SemanticInfo visitPathExpression(PathExpression node)
        {
            // same as selector (without repeating diagnositcs)
            return new SemanticInfo(getReferencedSymbol(node.selector()), getResultTypeOrError(node.selector()));
        }

        @Override
        public SemanticInfo visitElementExpression(ElementExpression node)
        {
            // same as selector (without repeating diagnostics)
            return new SemanticInfo(getReferencedSymbol(node.selector()), getResultTypeOrError(node.selector()));
        }
        // endregion

        // region function calls
        @Override
        public SemanticInfo visitFunctionCallExpression(FunctionCallExpression node)
        {
            return _binder.bindFunctionCallOrPattern(node);
        }

        // endregion

        // region other nodes
        @Override
        public SemanticInfo visitParenthesizedExpression(ParenthesizedExpression node)
        {
            return new SemanticInfo(getResultTypeOrError(node.expression()));
        }

        @Override
        public SemanticInfo visitEntityGroup(EntityGroup node)
        {
            var dxs = s_diagnosticListPool.allocateFromPool();
            var symbols = s_symbolListPool.allocateFromPool();
            try
            {
                if (node.entities().size() > 0)
                {
                    for (var se : node.entities())
                    {
                        SymbolKind expectedKind = symbols.size() > 0 ? symbols.get(0).kind() : null; // PORT: §3.7 SymbolKind? → null
                        if (checkEntityGroupElementKind(expectedKind, se.element(), dxs))
                        {
                            symbols.add(se.element().resultType());
                        }
                    }

                    var name = getNameFromContext(node);
                    var type = new EntityGroupSymbol(name, symbols);
                    return new SemanticInfo(type, dxs);
                }
                else
                {
                    dxs.add(DiagnosticFacts.getClusterDatabaseOrTableExpected().withLocation(node.openBracket()));
                    return new SemanticInfo(ErrorSymbol.Instance, dxs);
                }
            }
            finally
            {
                s_diagnosticListPool.returnToPool(dxs);
                s_symbolListPool.returnToPool(symbols);
            }
        }

        private boolean checkEntityGroupElementKind(SymbolKind kind, Expression expr, List<Diagnostic> diagnostics)
        {
            var resultType = getResultTypeOrError(expr);

            if (kind != null) // PORT: §3.15 kind is SymbolKind expectedKind (SymbolKind? has-value test)
            {
                SymbolKind expectedKind = kind;
                if (resultType.kind() == expectedKind)
                {
                    return true;
                }
                else
                {
                    switch (expectedKind)
                    {
                        case Table:
                            diagnostics.add(DiagnosticFacts.getTableExpected().withLocation(expr));
                            break;
                        case Database:
                            diagnostics.add(DiagnosticFacts.getDatabaseExpected().withLocation(expr));
                            break;
                        case Cluster:
                            diagnostics.add(DiagnosticFacts.getClusterExpected().withLocation(expr));
                            break;
                        default:
                            break; // PORT: §3.15 Java switch on enum; C# switch has no default
                    }

                    return false;
                }
            }
            else if (resultType instanceof ClusterSymbol
                || resultType instanceof DatabaseSymbol
                || resultType instanceof TableSymbol)
            {
                return true;
            }
            else
            {
                diagnostics.add(DiagnosticFacts.getClusterDatabaseOrTableExpected().withLocation(expr));
                return false;
            }
        }

        @Override
        public SemanticInfo visitOrderedExpression(OrderedExpression node)
        {
            return new SemanticInfo(getReferencedSymbol(node.expression()), getResultTypeOrError(node.expression()));
        }

        @Override
        public SemanticInfo visitSimpleNamedExpression(SimpleNamedExpression node)
        {
            return new SemanticInfo(getResultTypeOrError(node.expression()));
        }

        @Override
        public SemanticInfo visitCompoundNamedExpression(CompoundNamedExpression node)
        {
            return new SemanticInfo(getResultTypeOrError(node.expression()));
        }

        @Override
        public SemanticInfo visitPipeExpression(PipeExpression node)
        {
            return new SemanticInfo(getResultTypeOrError(node.operator()));
        }

        @Override
        public SemanticInfo visitAtExpression(AtExpression node)
        {
            // TODO:
            return null;
        }

        @Override
        public SemanticInfo visitDataScopeExpression(DataScopeExpression node)
        {
            var diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                _binder.checkIsTabular(node.expression(), diagnostics);

                return new SemanticInfo(getResultTypeOrError(node.expression()), diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitExpressionCouple(ExpressionCouple node)
        {
            // this is used in between operator.. its not an expression itself.
            return null;
        }

        @Override
        public SemanticInfo visitExpressionList(ExpressionList node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitForkExpression(ForkExpression node)
        {
            var resultType = getResultTypeOrError(node.expression());

            if (node.nameEquals() != null)
            {
                if (resultType instanceof TableSymbol table)
                {
                    resultType = new TableSymbol(_binder.getDeclaredAndInferredColumns(table))
                        .withInheritableProperties(table)
                        .withName(node.nameEquals().name().simpleName());
                }
            }

            return new SemanticInfo(resultType);
        }

        @Override
        public SemanticInfo visitPartitionSubquery(PartitionSubquery node)
        {
            var resultType = getResultTypeOrError(node.subquery());

            if (resultType instanceof TableSymbol table)
            {
                resultType = new TableSymbol(_binder.getDeclaredAndInferredColumns(table))
                    .withInheritableProperties(table);
            }

            return new SemanticInfo(resultType);
        }

        @Override
        public SemanticInfo visitPartitionQuery(PartitionQuery node)
        {
            var resultType = getResultTypeOrError(node.query());

            if (resultType instanceof TableSymbol table)
            {
                resultType = new TableSymbol(_binder.getDeclaredAndInferredColumns(table))
                    .withInheritableProperties(table);
            }

            return new SemanticInfo(resultType);
        }

        @Override
        public SemanticInfo visitPartitionScope(PartitionScope node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitJsonArrayExpression(JsonArrayExpression node)
        {
            var commonType = TypeFacts.getCommonResultType(node.values(), Conversion.None);
            var arrayType = ScalarTypes.getDynamicArray(commonType);
            return new SemanticInfo(arrayType);
        }

        @Override
        public SemanticInfo visitJsonObjectExpression(JsonObjectExpression node)
        {
            var columns = s_columnListPool.allocateFromPool();
            try
            {
                for (int i = 0; i < node.pairs().size(); i++)
                {
                    var pair = node.pairs().get(i).element();
                    var column = new ColumnSymbol(pair.name().valueText(), pair.value().resultType() != null ? pair.value().resultType() : ScalarTypes.Unknown); // PORT: §3.14 ??
                    columns.add(column);
                }

                var bagType = ScalarTypes.getDynamicBag(columns);
                return new SemanticInfo(bagType);
            }
            finally
            {
                s_columnListPool.returnToPool(columns);
            }
        }

        @Override
        public SemanticInfo visitJsonPair(JsonPair node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitList(SyntaxList list)
        {
            return null;
        }

        @Override
        public SemanticInfo visitSeparatedElement(SeparatedElement separatedElement)
        {
            return null;
        }

        @Override
        public SemanticInfo visitMakeSeriesExpression(MakeSeriesExpression node)
        {
            return new SemanticInfo(getResultTypeOrError(node.expression()));
        }

        @Override
        public SemanticInfo visitNamedParameter(NamedParameter node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitPackExpression(PackExpression node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitPatternMatch(PatternMatch node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitPatternPathValue(PatternPathValue node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitPatternPathParameter(PatternPathParameter node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitPrimitiveTypeExpression(PrimitiveTypeExpression node)
        {
            var dx = s_diagnosticListPool.allocateFromPool();
            try
            {
                var type = _binder.getTypeFromTypeExpression(node, dx);
                return new SemanticInfo(type, VoidSymbol.Instance, dx);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(dx);
            }
        }

        @Override
        public SemanticInfo visitSchemaTypeExpression(SchemaTypeExpression node)
        {
            var dx = s_diagnosticListPool.allocateFromPool();
            try
            {
                var type = _binder.getTypeFromTypeExpression(node, dx);
                return new SemanticInfo(type, VoidSymbol.Instance, dx);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(dx);
            }
        }

        @Override
        public SemanticInfo visitQueryBlock(QueryBlock node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitSkippedTokens(SkippedTokens node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitRenameList(RenameList node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitNameReferenceList(NameReferenceList node)
        {
            return null;
        }

        private static boolean isArgument(Expression e)
        {
            // PORT: §3.1 expression-bodied; §3.10 SeparatedElement<Expression> closed-generic test erased to SeparatedElement1<?>
            return e.parent() instanceof SeparatedElement1<?> se
                && se.parent() instanceof SyntaxList list
                && list.parent() instanceof ExpressionList el
                && el.parent() instanceof FunctionCallExpression;
        }

        private static boolean isLeftOperand(Expression e)
        {
            // PORT: §3.1 expression-bodied
            return e.parent() instanceof BinaryExpression be && be.left() == e;
        }

        @Override
        public SemanticInfo visitStarExpression(StarExpression node)
        {
            if (isArgument(node))
            {
                var columns = _binder.getDeclaredAndInferredColumns(rowScopeOrEmpty());
                var refSymbol = new GroupSymbol(columns);
                var resultSymbol = new TupleSymbol(columns);
                return new SemanticInfo(refSymbol, resultSymbol);
            }
            else if (isLeftOperand(node))
            {
                return new SemanticInfo(ScalarTypes.Dynamic);
            }
            else
            {
                return null;
            }
        }

        @Override
        public SemanticInfo visitTypedColumnReference(TypedColumnReference node)
        {
            return new SemanticInfo(getResultTypeOrError(node.column()));
        }

        @Override
        public SemanticInfo visitCustom(CustomNode node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitMaterializedViewCombineExpression(MaterializedViewCombineExpression node)
        {
            var resultType = getResultTypeOrError(node.aggregationsClause().expression());
            return new SemanticInfo(resultType);
        }

        @Override
        public SemanticInfo visitMaterializedViewCombineNameClause(MaterializedViewCombineNameClause node)
        {
            // verify string literal 
            var diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                if (!_binder.checkIsExactType(node.value(), ScalarTypes.String, diagnostics) ||
                !_binder.checkIsLiteral(node.value(), diagnostics))
                {
                    return new SemanticInfo(diagnostics);
                }

                return null;

            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitMaterializedViewCombineClause(MaterializedViewCombineClause node)
        {
            // handled by VisitMaterializedViewCombineExpression
            return null;
        }
        // endregion
        // region query operators
        private void checkFirstInPipe(QueryOperator queryOp, List<Diagnostic> diagnostics)
        {
            if (KustoFacts.hasPipedInput(queryOp))
            {
                SyntaxElement firstChild = queryOp.getChild(0); // PORT: §3.14 ??
                diagnostics.add(DiagnosticFacts.getQueryOperatorMustBeFirst().withLocation(firstChild != null ? firstChild : queryOp));
            }
        }

        private void checkNotFirstInPipe(QueryOperator queryOp, List<Diagnostic> diagnostics)
        {
            if (!KustoFacts.hasPipedInput(queryOp))
            {
                SyntaxElement firstChild = queryOp.getChild(0); // PORT: §3.14 ??
                diagnostics.add(DiagnosticFacts.getQueryOperatorCannotBeFirst().withLocation(firstChild != null ? firstChild : queryOp));
            }
        }

        @Override
        public SemanticInfo visitBadQueryOperator(BadQueryOperator node)
        {
            return ErrorInfo;
        }

        @Override
        public SemanticInfo visitFilterOperator(FilterOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);
                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.FilterParameters, diagnostics);
                _binder.checkIsExactType(node.condition(), ScalarTypes.Bool, diagnostics);

                TableSymbol resultTable = new TableSymbol(_binder.getDeclaredAndInferredColumns(rowScopeOrEmpty()))
                    .withInheritableProperties(rowScopeOrEmpty());

                return new SemanticInfo(resultTable, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitTakeOperator(TakeOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);
                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.TakeParameters, diagnostics);
                _binder.checkIsInteger(node.expression(), diagnostics);

                TableSymbol resultTable = new TableSymbol(_binder.getDeclaredAndInferredColumns(rowScopeOrEmpty()))
                    .withInheritableProperties(rowScopeOrEmpty())
                    .withIsSorted(false);

                return new SemanticInfo(resultTable, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitSampleOperator(SampleOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);
                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.SampleParameters, diagnostics);
                _binder.checkIsInteger(node.expression(), diagnostics);

                TableSymbol resultTable = new TableSymbol(_binder.getDeclaredAndInferredColumns(rowScopeOrEmpty()))
                    .withInheritableProperties(rowScopeOrEmpty())
                    .withIsSorted(false);

                return new SemanticInfo(resultTable, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitSampleDistinctOperator(SampleDistinctOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);
                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.SampleDistinctParameters, diagnostics);
                _binder.checkIsInteger(node.expression(), diagnostics);
                boolean _discard = _binder.checkIsColumn(node.ofExpression(), diagnostics) // PORT: §2.3 '_' is a Java keyword
                    && _binder.checkIsNotType(node.ofExpression(), ScalarTypes.Dynamic, diagnostics);

                ColumnSymbol ofCol = getOrDeclareColumnForExpression(node.ofExpression(), null, null, "Column1"); // PORT: §3.12 named arg defaultName

                TableSymbol result = new TableSymbol(ofCol)
                        .withInheritableProperties(rowScopeOrEmpty())
                        .withIsSorted(false);

                return new SemanticInfo(result, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitCountOperator(CountOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                String name = node.asIdentifier() != null ? node.asIdentifier().identifier().valueText() : "Count";
                return new SemanticInfo(new TableSymbol(new ColumnSymbol(name, ScalarTypes.Long)), diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitProjectOperator(ProjectOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            ProjectionBuilder builder = s_projectionBuilderPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                _binder.checkIsScalar(node.expressions(), diagnostics);

                _binder.createProjectionColumns(node.expressions(), builder, diagnostics);

                TableSymbol resultTable = new TableSymbol(builder.getProjection())
                    .withInheritableProperties(rowScopeOrEmpty());

                return new SemanticInfo(resultTable, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_projectionBuilderPool.returnToPool(builder);
            }
        }

        @Override
        public SemanticInfo visitProjectAwayOperator(ProjectAwayOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            List<ColumnSymbol> columns = s_columnListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                _binder.getColumnsInColumnList(node.expressions(), columns, diagnostics);
                Set<String> namesToRemove = new LinkedHashSet<>(); // PORT: §3.17 HashSet<string>
                for (ColumnSymbol c : columns) // PORT: §3.6 columns.Select(c => c.Name)
                {
                    namesToRemove.add(c.name());
                }
                columns.clear();

                // only include columns from the original table that are not included in the list
                for (Symbol member : rowScopeOrEmpty().members())
                {
                    ColumnSymbol column = (ColumnSymbol) member; // PORT: §3.10 foreach (ColumnSymbol column in ...) casts each element
                    if (!namesToRemove.contains(column.name()))
                    {
                        columns.add(column);
                    }
                }

                TableSymbol resultType = new TableSymbol(columns).withInheritableProperties(rowScopeOrEmpty());
                SemanticInfo info = new SemanticInfo(resultType, diagnostics);
                return info;
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_columnListPool.returnToPool(columns);
            }
        }

        @Override
        public SemanticInfo visitProjectKeepOperator(ProjectKeepOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            List<ColumnSymbol> columns = s_columnListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                _binder.getColumnsInColumnList(node.expressions(), columns, diagnostics);
                Set<String> namesToKeep = new LinkedHashSet<>(); // PORT: §3.17 HashSet<string>
                for (ColumnSymbol c : columns) // PORT: §3.6 columns.Select(c => c.Name)
                {
                    namesToKeep.add(c.name());
                }
                columns.clear();

                // only include columns from the original table that are not included in the list
                for (Symbol member : rowScopeOrEmpty().members())
                {
                    ColumnSymbol column = (ColumnSymbol) member; // PORT: §3.10 foreach (ColumnSymbol column in ...) casts each element
                    if (namesToKeep.contains(column.name()))
                    {
                        columns.add(column);
                    }
                }

                TableSymbol resultType = new TableSymbol(columns).withInheritableProperties(rowScopeOrEmpty());
                SemanticInfo info = new SemanticInfo(resultType, diagnostics);
                return info;
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_columnListPool.returnToPool(columns);
            }
        }

        @Override
        public SemanticInfo visitProjectRenameOperator(ProjectRenameOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            ProjectionBuilder builder = s_projectionBuilderPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                builder.addRange(_binder.getDeclaredAndInferredColumns(rowScopeOrEmpty()), true, true);
                _binder.createProjectionColumns(node.expressions(), builder, diagnostics, ProjectionStyle.Rename);

                TableSymbol resultTable = new TableSymbol(builder.getProjection())
                    .withInheritableProperties(rowScopeOrEmpty());

                return new SemanticInfo(resultTable, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_projectionBuilderPool.returnToPool(builder);
            }
        }

        @Override
        public SemanticInfo visitProjectReorderOperator(ProjectReorderOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            ProjectionBuilder builder = s_projectionBuilderPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                _binder.createProjectionColumns(node.expressions(), builder, diagnostics, ProjectionStyle.Reorder, true);

                // add any remaining columns not explicit in projection
                for (ColumnSymbol col : rowScopeOrEmpty().columns())
                {
                    builder.add(col);
                }

                TableSymbol resultTable = new TableSymbol(builder.getProjection())
                    .withInheritableProperties(rowScopeOrEmpty());

                SemanticInfo info = new SemanticInfo(resultTable, diagnostics);
                return info;
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_projectionBuilderPool.returnToPool(builder);
            }
        }

        @Override
        public SemanticInfo visitProjectByNamesOperator(ProjectByNamesOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            ProjectionBuilder builder = s_projectionBuilderPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                _binder.createProjectionColumns(node.expressions(), builder, diagnostics, ProjectionStyle.ByNames, true);

                TableSymbol resultTable = new TableSymbol(builder.getProjection())
                    .withInheritableProperties(rowScopeOrEmpty());

                SemanticInfo info = new SemanticInfo(resultTable, diagnostics);
                return info;
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_projectionBuilderPool.returnToPool(builder);
            }
        }

        @Override
        public SemanticInfo visitExtendOperator(ExtendOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            ProjectionBuilder builder = s_projectionBuilderPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                _binder.checkIsScalar(node.expressions(), diagnostics);

                builder.addRange(_binder.getDeclaredAndInferredColumns(rowScopeOrEmpty()), false, true); // PORT: §3.12 named arg doNotRepeat
                _binder.createProjectionColumns(node.expressions(), builder, diagnostics, ProjectionStyle.Extend);

                TableSymbol resultType = new TableSymbol(builder.getProjection())
                    .withInheritableProperties(rowScopeOrEmpty());

                SemanticInfo info = new SemanticInfo(resultType, diagnostics);
                return info;
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_projectionBuilderPool.returnToPool(builder);
            }
        }

        @Override
        public SemanticInfo visitSummarizeOperator(SummarizeOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            ProjectionBuilder builder = s_projectionBuilderPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.SummarizeParameters, diagnostics);

                _binder.checkIsScalar(node.aggregates(), diagnostics);

                if (node.byClause() != null)
                {
                    boolean _discard = _binder.checkIsScalar(node.byClause().expressions(), diagnostics) // PORT: §2.3 '_' is a Java keyword
                        && _binder.checkIsNotDynamic(node.byClause().expressions(), diagnostics);

                    // all columns corresponding to by-clause expressions
                    _binder.createProjectionColumns(node.byClause().expressions(), builder, diagnostics);

                    // don't re-add any columns already added from by-clause
                    builder.doNotAddAny(builder.getProjection());
                }

                // all columns corresponding to aggregate expressions
                _binder.createProjectionColumns(node.aggregates(), builder, diagnostics, ProjectionStyle.Summarize);

                TableSymbol resultTable = new TableSymbol(builder.getProjection())
                    .withInheritableProperties(rowScopeOrEmpty())
                    .withIsSorted(false);

                return new SemanticInfo(resultTable, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_projectionBuilderPool.returnToPool(builder);
            }
        }

        @Override
        public SemanticInfo visitDistinctOperator(DistinctOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            ProjectionBuilder builder = s_projectionBuilderPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);
                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.DistinctParameters, diagnostics);

                _binder.createProjectionColumns(node.expressions(), builder, diagnostics);

                // PORT: §3.6 node.Expressions.FirstOrDefault(e => e.Element is StarExpression)?.Element
                Expression star = null;
                for (SeparatedElement1<Expression> e : node.expressions())
                {
                    if (e.element() instanceof StarExpression)
                    {
                        star = e.element();
                        break;
                    }
                }
                List<ColumnSymbol> projection = builder.getProjection();

                boolean _discard = _binder.checkIsScalar(node.expressions(), diagnostics) // PORT: §2.3 '_' is a Java keyword
                    && _binder.checkIsNotDynamic(node.expressions(), diagnostics)
                    && (star == null || _binder.checkIsNotDynamic(projection, star, diagnostics));

                TableSymbol resultTable = new TableSymbol(projection)
                    .withInheritableProperties(rowScopeOrEmpty())
                    .withIsSorted(false);

                return new SemanticInfo(resultTable, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_projectionBuilderPool.returnToPool(builder);
            }
        }

        @Override
        public SemanticInfo visitAssertSchemaOperator(AssertSchemaOperator node)
        {
            TableSymbol resultTable = new TableSymbol(_binder.getDeclaredAndInferredColumns(rowScopeOrEmpty()))
                    .withInheritableProperties(rowScopeOrEmpty());

            return new SemanticInfo(resultTable);
        }

        @Override
        public SemanticInfo visitTopOperator(TopOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                _binder.checkIsInteger(node.expression(), diagnostics);
                _binder.checkIsScalar(node.byExpression(), diagnostics);

                // does not change table shape
                TableSymbol resultTable = new TableSymbol(_binder.getDeclaredAndInferredColumns(rowScopeOrEmpty()))
                    .withInheritableProperties(rowScopeOrEmpty())
                    .withIsSorted(true);

                return new SemanticInfo(resultTable, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitTopHittersOperator(TopHittersOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            ProjectionBuilder builder = s_projectionBuilderPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                _binder.checkIsInteger(node.expression(), diagnostics);
                _binder.checkIsColumn(node.ofExpression(), diagnostics);

                _binder.createProjectionColumns(node.ofExpression(), builder, diagnostics);

                if (node.byClause() != null)
                {
                    _binder.checkIsNumber(node.byClause().expression(), diagnostics);
                    ColumnSymbol approxSumCol = getOrDeclareColumnForExpression(node.byClause().expression(), "approximate_sum_" + DotNet.str(getExpressionResultName(node.byClause().expression()))); // PORT: §3.14 string concat
                    builder.add(approxSumCol);
                }
                else
                {
                    ColumnSymbol approxCountCol = getOrDeclareColumnForExpression(node.ofExpression(), "approximate_count_" + DotNet.str(getExpressionResultName(node.ofExpression())), ScalarTypes.Long); // PORT: §3.14 string concat
                    builder.add(approxCountCol);
                }

                TableSymbol resultTable = new TableSymbol(builder.getProjection())
                    .withInheritableProperties(rowScopeOrEmpty())
                    .withIsSorted(true);

                return new SemanticInfo(resultTable, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_projectionBuilderPool.returnToPool(builder);
            }
        }

        @Override
        public SemanticInfo visitTopNestedOperator(TopNestedOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            List<ColumnSymbol> columns = s_columnListPool.allocateFromPool();
            UniqueNameTable uniqueNames = s_uniqueNameTablePool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                for (int i = 0, n = node.clauses().size(); i < n; i++)
                {
                    TopNestedClause clause = node.clauses().get(i).element();

                    if (clause.expression() != null)
                    {
                        _binder.checkIsInteger(clause.expression(), diagnostics);
                    }

                    _binder.checkIsScalar(clause.ofExpression(), diagnostics);

                    if (clause.withOthersClause() != null)
                    {
                        _binder.checkIsScalar(clause.withOthersClause().expression(), diagnostics);
                    }

                    _binder.checkIsScalar(clause.byExpression(), diagnostics);

                    String ofName = uniqueNames.getOrAddName(getExpressionResultName(clause.ofExpression()));
                    ColumnSymbol ofCol = getOrDeclareColumnForExpression(clause.ofExpression(), ofName);
                    columns.add(ofCol);

                    String declaredByName = getExpressionDeclaredName(clause.byExpression());
                    String byName = uniqueNames.getOrAddName(declaredByName != null ? declaredByName : "aggregated_" + DotNet.str(ofName)); // PORT: §3.14 ?? and string concat
                    ColumnSymbol byCol = getOrDeclareColumnForExpression(clause.byExpression(), byName);
                    columns.add(byCol);
                }

                TableSymbol resultTable = new TableSymbol(columns)
                    .withInheritableProperties(rowScopeOrEmpty())
                    .withIsSorted(true);

                return new SemanticInfo(resultTable, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_columnListPool.returnToPool(columns);
                s_uniqueNameTablePool.returnToPool(uniqueNames);
            }
        }

        @Override
        public SemanticInfo visitConsumeOperator(ConsumeOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.ConsumeParameters, diagnostics);

                // consume doesn't produce anything
                return new SemanticInfo(VoidSymbol.Instance, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitExecuteAndCacheOperator(ExecuteAndCacheOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                // execute and cache doesn't change anything?
                TableSymbol resultTable = new TableSymbol(rowScopeOrEmpty().columns())
                    .withInheritableProperties(rowScopeOrEmpty());

                return new SemanticInfo(resultTable, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitRowSchema(RowSchema node)
        {
            // handled by parent node
            return null;
        }

        @Override
        public SemanticInfo visitEvaluateRowSchema(EvaluateRowSchema node)
        {
            // handled by parent node
            return null;
        }

        @Override
        public SemanticInfo visitDataTableExpression(DataTableExpression node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            List<ColumnSymbol> columns = s_columnListPool.allocateFromPool();
            try
            {
                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.DataTableParameters, diagnostics);
                createColumnsFromRowSchema(node.schema().columns(), columns, diagnostics);
                _binder.checkDataValueTypes(node.values(), columns, diagnostics);
                return new SemanticInfo(new TableSymbol(columns), diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_columnListPool.returnToPool(columns);
            }
        }

        @Override
        public SemanticInfo visitContextualDataTableExpression(ContextualDataTableExpression node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            List<ColumnSymbol> columns = s_columnListPool.allocateFromPool();
            try
            {
                createColumnsFromRowSchema(node.schema().columns(), columns, diagnostics);

                if (node.id() != null)
                {
                    boolean _discard = _binder.checkIsExactType(node.id(), ScalarTypes.Guid, diagnostics) // PORT: §2.3 '_' is a Java keyword
                        && _binder.checkIsLiteral(node.id(), diagnostics);
                }

                return new SemanticInfo(new TableSymbol(columns), diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_columnListPool.returnToPool(columns);
            }
        }

        @Override
        public SemanticInfo visitExternalDataExpression(ExternalDataExpression node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            List<ColumnSymbol> columns = s_columnListPool.allocateFromPool();
            try
            {
                createColumnsFromRowSchema(node.schema().columns(), columns, diagnostics);

                // PORT-BUG: upstream builds a lazy Select(...) and never enumerates it, so the URI checks never run:
                // node.URIs.Select(item => _binder.CheckIsExactType(item.Element, ScalarTypes.String, diagnostics));

                if (node.withClause() != null)
                {
                    // Does not check properties in with clause. Any property name is legal?
                }

                return new SemanticInfo(new TableSymbol(columns), diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_columnListPool.returnToPool(columns);
            }
        }

        @Override
        public SemanticInfo visitInlineExternalTableExpression(InlineExternalTableExpression node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            List<ColumnSymbol> columns = s_columnListPool.allocateFromPool();
            LinkedHashSet<String> partitionColumnNames = s_stringSetPool.allocateFromPool();
            LinkedHashSet<String> partitionColumnsDirectlyUsed = s_stringSetPool.allocateFromPool();
            List<Expression> partitionColumnsFunctionUsed = s_expressionListPool.allocateFromPool();
            try
            {
                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.InlineExternalTableProperties, diagnostics);

                String kindParameterValueText = node.kindParameter() != null && node.kindParameter().value() != null ? node.kindParameter().value().valueText() : null; // PORT: §3.14 ?.
                boolean isDeltaInlineExternalTable = DotNetStrings.equalsOrdinalIgnoreCase(kindParameterValueText, "delta");
                boolean hasOmittedSchema =
                    node.schema() != null
                    && node.schema().openParen().isMissing()
                    && node.schema().closeParen().isMissing()
                    && node.schema().columns().size() == 0
                    && !node.schema().containsSyntaxDiagnostics();

                if (!hasOmittedSchema)
                {
                    createColumnsFromRowSchema(node.schema().columns(), columns, diagnostics);
                }

                if (isDeltaInlineExternalTable)
                {
                    if (node.partitionClause() != null)
                    {
                        diagnostics.add(DiagnosticFacts.getInlineExternalTableDeltaPartitionByNotSupported().withLocation(node.partitionClause()));
                    }

                    if (node.pathFormat() != null)
                    {
                        diagnostics.add(DiagnosticFacts.getInlineExternalTableDeltaPathFormatNotSupported().withLocation(node.pathFormat()));
                    }

                    if (node.dataFormatParameter() != null && !node.dataFormatParameter().dataFormatKeyword().isMissing())
                    {
                        diagnostics.add(DiagnosticFacts.getInlineExternalTableDeltaDataFormatNotSupported().withLocation(node.dataFormatParameter()));
                    }

                    if (node.connectionStrings().connectionStrings().size() > 1)
                    {
                        diagnostics.add(DiagnosticFacts.getInlineExternalTableDeltaRequiresSingleRootUri().withLocation(node.connectionStrings()));
                    }
                }
                else
                {
                    if (node.partitionClause() != null)
                    {
                        for (SeparatedElement1<PartitionColumnDeclaration> partitionColumn : node.partitionClause().partitionColumns())
                        {
                            // Check Partition column names uniqueness
                            if (!declareColumnName(partitionColumnNames, partitionColumn.element().name().simpleName(), diagnostics, partitionColumn.element().name().name()))
                            {
                                diagnostics.add(DiagnosticFacts.getDuplicateColumnDeclaration(partitionColumn.element().name().simpleName()).withLocation(partitionColumn.element()));
                                break;
                            }

                            TypeSymbol type = null;
                            if (partitionColumn.element().type() instanceof PrimitiveTypeExpression p) // PORT: §3.15 switch pattern
                            {
                                type = Binder.getType(p);
                            }
                            else
                            {
                                diagnostics.add(DiagnosticFacts.getWrongPartitionColumnType().withLocation(partitionColumn.element())); // PORT: §3.14 diagnostics?.Add
                            }

                            if (type == null)
                            {
                                break;
                            }
                            if (partitionColumn.element().expr() == null)
                            {
                                boolean anyDuplicate = false; // PORT: §3.6 columns.Any(item => item.Name == partitionColumn.Element.Name.SimpleName)
                                for (ColumnSymbol item : columns)
                                {
                                    if (Objects.equals(item.name(), partitionColumn.element().name().simpleName()))
                                    {
                                        anyDuplicate = true;
                                        break;
                                    }
                                }
                                if (anyDuplicate)
                                {
                                    diagnostics.add(DiagnosticFacts.getDuplicateColumnDeclaration(partitionColumn.element().name().simpleName()).withLocation(partitionColumn.element()));
                                    break;
                                }

                                if (symbolsAssignable(type, ScalarTypes.Long, Conversion.None))
                                {
                                    diagnostics.add(DiagnosticFacts.getWrongVirtualPartitionColumnType().withLocation(partitionColumn.element())); // PORT: §3.14 diagnostics?.Add
                                    break;
                                }
                                // Virtual Column need to be added to the list of columns
                                columns.add(new ColumnSymbol(partitionColumn.element().name().simpleName(), type, null, null, partitionColumn.element().name())); // PORT: §3.12 named arg source
                            }
                            else
                            {
                                _binder.checkIsExactType(partitionColumn.element().expr(), type, diagnostics);
                                // Validate that only closed list of functions is allowed for partition column
                                if (partitionColumn.element().expr().kind() == SyntaxKind.FunctionCallExpression)
                                {
                                    if (!KustoFacts.InlineExternalTablePartitionColumnFunctions.contains(((FunctionCallExpression)partitionColumn.element().expr()).name().simpleName()))
                                    {
                                        diagnostics.add(DiagnosticFacts.getWrongPartitionColumnFunction().withLocation(partitionColumn.element())); // PORT: §3.14 diagnostics?.Add
                                    }
                                }
                            }
                        }
                    }
                    if (node.pathFormat() != null)
                    {
                        for (var pathFormatElement : node.pathFormat().pathExpressions())
                        {
                            if (pathFormatElement.partitionColumnExpression().kind() == SyntaxKind.NameReference)
                            {
                                partitionColumnsDirectlyUsed.add(((NameReference)pathFormatElement.partitionColumnExpression()).simpleName());
                            }
                            else if (pathFormatElement.partitionColumnExpression().kind() == SyntaxKind.DateTimePattern)
                            {
                                DateTimePattern dateTimePattern = (DateTimePattern)pathFormatElement.partitionColumnExpression();
                                if (!checkDateTimePatternAllowed(partitionColumnsFunctionUsed, dateTimePattern))
                                {
                                    diagnostics.add(DiagnosticFacts.getPartitionColumnCanNotBeUsedBothDirectlyAndPattern(((NameReference)dateTimePattern.partitionColumn()).simpleName()).withLocation(node.pathFormat()));
                                    break;
                                }
                                partitionColumnsFunctionUsed.add(dateTimePattern);
                            }
                        }

                        // Find all partition column names not present in either set
                        // PORT: §3.6 partitionColumnNames.Where(name => !partitionColumnsDirectlyUsed.Contains(name) && !partitionColumnsFunctionUsed.Any(item => ((DateTimePattern)item).PartitionColumn.SimpleName == name)).ToList()
                        List<String> unusedPartitionColumns = new ArrayList<>();
                        for (String name : partitionColumnNames)
                        {
                            if (partitionColumnsDirectlyUsed.contains(name))
                            {
                                continue;
                            }
                            boolean usedByFunction = false;
                            for (Expression item : partitionColumnsFunctionUsed)
                            {
                                if (Objects.equals(((DateTimePattern)item).partitionColumn().simpleName(), name))
                                {
                                    usedByFunction = true;
                                    break;
                                }
                            }
                            if (!usedByFunction)
                            {
                                unusedPartitionColumns.add(name);
                            }
                        }

                        // Add diagnostics for each unused column
                        for (String name : unusedPartitionColumns)
                        {
                            diagnostics.add(DiagnosticFacts.getPartitionColumnNotUsedInPathFormat(name).withLocation(node.pathFormat()));
                        }

                        // Find partitions that have both direct and function usage
                        // PORT: §3.6 partitionColumnsFunctionUsed.Where(expr => partitionColumnsDirectlyUsed.Contains(((DateTimePattern)expr).PartitionColumn.SimpleName)).ToList()
                        List<Expression> conflictingPartitionColumns = new ArrayList<>();
                        for (Expression expr : partitionColumnsFunctionUsed)
                        {
                            if (partitionColumnsDirectlyUsed.contains(((DateTimePattern)expr).partitionColumn().simpleName()))
                            {
                                conflictingPartitionColumns.add(expr);
                            }
                        }

                        // Add diagnostics for each conflicting partition
                        for (Expression expr : conflictingPartitionColumns)
                        {
                            diagnostics.add(DiagnosticFacts
                                .getPartitionColumnCanNotBeUsedBothDirectlyAndPattern(((DateTimePattern)expr).partitionColumn().simpleName())
                                .withLocation(node.pathFormat()));
                        }
                    }
                }

                // PORT-BUG: upstream builds a lazy Select(...) and never enumerates it, so the connection string checks never run:
                // node.ConnectionStrings.ConnectionStrings.Select(item => _binder.CheckIsExactType(item.Element, ScalarTypes.String, diagnostics));

                if (!isDeltaInlineExternalTable
                    && !KustoFacts.InlineExternalTableDataFormats.contains(node.dataFormatParameter().value().valueText()))
                {
                    diagnostics.add(DiagnosticFacts.getWrongDataStreamType(node.dataFormatParameter().value().valueText()).withLocation(node.dataFormatParameter()));
                }

                TableSymbol resultTable = new TableSymbol(columns);
                if (hasOmittedSchema)
                {
                    resultTable = resultTable.withIsOpen(true);
                }

                return new SemanticInfo(resultTable, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_columnListPool.returnToPool(columns);
                s_stringSetPool.returnToPool(partitionColumnNames);
                s_stringSetPool.returnToPool(partitionColumnsDirectlyUsed);
                s_expressionListPool.returnToPool(partitionColumnsFunctionUsed);
            }
        }

        private boolean checkDateTimePatternAllowed(List<Expression> existingExpressions, DateTimePattern current)
        {
            for (Expression existingExpression : existingExpressions)
            {
                DateTimePattern existingDateTimePattern = existingExpression instanceof DateTimePattern dtp ? dtp : null; // PORT: §3.15 as
                if (Objects.equals(existingDateTimePattern.partitionColumn().simpleName(), current.partitionColumn().simpleName())
                    && !Objects.equals(existingDateTimePattern.stringLiteral().token().valueText(), current.stringLiteral().token().valueText()))
                {
                    return false;
                }
            }
            return true;
        }

        @Override
        public SemanticInfo visitDateTimePattern(DateTimePattern node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                _binder.checkIsExactType(node.partitionColumn(), ScalarTypes.DateTime, diagnostics);
                return new SemanticInfo(ScalarTypes.DateTime, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitSortOperator(SortOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);
                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.SortParameters, diagnostics);

                boolean _discard = _binder.checkIsScalar(node.expressions(), diagnostics) // PORT: §2.3 '_' is a Java keyword
                    && _binder.checkIsNotDynamic(node.expressions(), diagnostics);

                TableSymbol resultTable = new TableSymbol(_binder.getDeclaredAndInferredColumns(rowScopeOrEmpty()))
                    .withInheritableProperties(rowScopeOrEmpty())
                    .withIsSorted(true);

                return new SemanticInfo(resultTable, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitSerializeOperator(SerializeOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            ProjectionBuilder builder = s_projectionBuilderPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);
                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.SerializedParameters, diagnostics);

                _binder.checkIsScalar(node.expressions(), diagnostics);

                builder.addRange(_binder.getDeclaredAndInferredColumns(rowScopeOrEmpty()), false, true); // PORT: §3.12 named arg doNotRepeat
                _binder.createProjectionColumns(node.expressions(), builder, diagnostics);

                TableSymbol resultType = new TableSymbol(builder.getProjection())
                    .withInheritableProperties(rowScopeOrEmpty())
                    .withIsSerialized(true);

                SemanticInfo info = new SemanticInfo(resultType, diagnostics);
                return info;
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_projectionBuilderPool.returnToPool(builder);
            }
        }

        @Override
        public SemanticInfo visitAsOperator(AsOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);
                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.AsParameters, diagnostics);

                TableSymbol resultTable = new TableSymbol(rowScopeOrEmpty().columns())
                    .withInheritableProperties(rowScopeOrEmpty());

                return new SemanticInfo(resultTable);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitForkOperator(ForkOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                List<TableSymbol> tables = new ArrayList<>();
                for (int i = 0, n = node.expressions().size(); i < n; i++)
                {
                    Expression expr = node.expressions().get(i).expression();
                    _binder.checkIsTabular(expr, diagnostics);
                    checkQueryOperators(expr, KustoFacts.ForkOperatorKinds, diagnostics);

                    TableSymbol tableType = getResultType(expr) instanceof TableSymbol ts ? ts : null; // PORT: §3.15 as
                    if (tableType != null)
                    {
                        String name = tables.size() == 0 ? "Results" : "Results_" + (tables.size() + 1);
                        TableSymbol table = new TableSymbol(name, _binder.getDeclaredAndInferredColumns(tableType)).withInheritableProperties(tableType);
                        tables.add(table);
                    }
                }

                return new SemanticInfo(new GroupSymbol(tables), diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        private static void checkQueryOperators(
            Expression expr,
            List<SyntaxKind> validQueryOperators,
            List<Diagnostic> diagnostics,
            boolean operatorRequired,
            boolean allowContextualRoot)
        {
            while (expr instanceof PipeExpression pe)
            {
                checkQueryOperator(pe.operator(), validQueryOperators, diagnostics);
                expr = pe.expression();
            }

            if (expr instanceof QueryOperator q)
            {
                checkQueryOperator(q, validQueryOperators, diagnostics);
            }
            else if (operatorRequired && !(allowContextualRoot && expr instanceof ContextualDataTableExpression))
            {
                diagnostics.add(DiagnosticFacts.getQueryOperatorExpected().withLocation(expr));
            }
        }

        private static void checkQueryOperators(
            Expression expr,
            List<SyntaxKind> validQueryOperators,
            List<Diagnostic> diagnostics,
            boolean operatorRequired) // PORT: §3.12 optional parameter allowContextualRoot = false
        {
            checkQueryOperators(expr, validQueryOperators, diagnostics, operatorRequired, false);
        }

        private static void checkQueryOperators(
            Expression expr,
            List<SyntaxKind> validQueryOperators,
            List<Diagnostic> diagnostics) // PORT: §3.12 optional parameter operatorRequired = true
        {
            checkQueryOperators(expr, validQueryOperators, diagnostics, true);
        }

        private static void checkQueryOperator(QueryOperator queryOperator, List<SyntaxKind> validQueryOperators, List<Diagnostic> diagnostics)
        {
            SyntaxToken keyword = queryOperator.getFirstToken();
            if (keyword != null
                && !validQueryOperators.contains(queryOperator.kind())
                && !queryOperator.containsSyntaxDiagnostics())
            {
                diagnostics.add(DiagnosticFacts.getQueryOperatorNotAllowedInContext(keyword.text()).withLocation(keyword));
            }
        }

        @Override
        public SemanticInfo visitPartitionByOperator(PartitionByOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                // todo: check parameters when the correct set is known
                //_binder.CheckQueryOperatorParameters(node.Parameters, QueryOperatorParameters.PartitionParameters, diagnostics);

                boolean _discard = _binder.checkIsColumn(node.entity(), diagnostics) // PORT: §2.3 '_' is a Java keyword
                    && _binder.checkIsNotType(node.entity(), ScalarTypes.Dynamic, diagnostics);

                checkQueryOperators(node.subquery(), KustoFacts.PostPipeOperatorKinds, diagnostics, true, true); // PORT: §3.12 named arg allowContextualRoot

                TableSymbol tableType = node.subquery().resultType() instanceof TableSymbol ts ? ts : null; // PORT: §3.15 as

                TableSymbol result = new TableSymbol(_binder.getDeclaredAndInferredColumns(tableType))
                                .withInheritableProperties(rowScopeOrEmpty())
                                .withIsSorted(false);

                return new SemanticInfo(result, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitPartitionByIdClause(PartitionByIdClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitPartitionOperator(PartitionOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);
                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.PartitionParameters, diagnostics);

                Expression operand = node.operand();
                boolean _discard = _binder.checkIsColumn(node.byExpression(), diagnostics) // PORT: §2.3 '_' is a Java keyword
                    && _binder.checkIsNotType(node.byExpression(), ScalarTypes.Dynamic, diagnostics);
                _binder.checkIsTabular(operand, diagnostics);

                if (operand instanceof PartitionSubquery ps)
                {
                    checkQueryOperators(ps.subquery(), KustoFacts.PostPipeOperatorKinds, diagnostics);
                }

                TableSymbol tableType = getResultType(operand) instanceof TableSymbol ts ? ts : null; // PORT: §3.15 as
                if (tableType == null)
                {
                    // Failed to resolve operand as table operator
                    return new SemanticInfo(rowScopeOrEmpty(), diagnostics);
                }

                TableSymbol result = new TableSymbol(_binder.getDeclaredAndInferredColumns(tableType))
                    .withInheritableProperties(rowScopeOrEmpty())
                    .withIsSorted(false);

                return new SemanticInfo(result, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitSearchOperator(SearchOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            List<ColumnSymbol> columns = s_columnListPool.allocateFromPool();
            List<TableSymbol> tables = s_tableListPool.allocateFromPool();
            try
            {
                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.SearchParameters, diagnostics);
                _binder.checkIsExactType(node.condition(), ScalarTypes.Bool, diagnostics);

                columns.add(new ColumnSymbol("$table", ScalarTypes.String));

                TableSymbol result;
                if (node.inClause() != null)
                {
                    checkFirstInPipe(node, diagnostics);

                    for (int i = 0, n = node.inClause().expressions().size(); i < n; i++)
                    {
                        Expression expr = node.inClause().expressions().get(i).element();
                        _binder.checkIsTabular(expr, diagnostics);
                    }
                }

                TableSymbol searchColumnsTable = _binder.getSearchColumnsTable(node);
                _binder.getDeclaredAndInferredColumns(searchColumnsTable, columns);

                if (_binder._rowScope != null)
                {
                    // if no in-clause can be any position in pipe
                    result = new TableSymbol(columns)
                        .withInheritableProperties(_binder._rowScope)
                        .withIsSorted(false);
                }
                else
                {
                    result = new TableSymbol(columns)
                        .withIsOpen(searchColumnsTable.isOpen());
                }

                return new SemanticInfo(result, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_tableListPool.returnToPool(tables);
                s_columnListPool.returnToPool(columns);
            }
        }

        @Override
        public SemanticInfo visitFindOperator(FindOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            List<ColumnSymbol> columns = s_columnListPool.allocateFromPool();
            List<ColumnSymbol> refColumns = s_columnListPool.allocateFromPool();
            List<ColumnSymbol> packColumns = s_columnListPool.allocateFromPool();
            List<TableSymbol> refTables = s_tableListPool.allocateFromPool();

            try
            {
                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.FindParameters, diagnostics);
                _binder.checkIsExactType(node.condition(), ScalarTypes.Bool, diagnostics);

                String sourceColumnName = SyntaxExtensions.getParameterNameValue(node.parameters(), QueryOperatorParameters.WithSource); // PORT: §3.5
                if (sourceColumnName == null) // PORT: §3.14 ??
                {
                    sourceColumnName = "source_";
                }
                columns.add(new ColumnSymbol(sourceColumnName, ScalarTypes.String));

                List<TableSymbol> tables = _binder.getFindTables(node);
                boolean resultIsOpen = false; // PORT: §3.6 tables.Any(t => t.IsOpen)
                for (TableSymbol t : tables)
                {
                    if (t.isOpen())
                    {
                        resultIsOpen = true;
                        break;
                    }
                }
                boolean explicitPack = false;

                // only consider tables that have column references
                _binder.getReferencedColumnsInTree(node.condition(), refColumns);

                if (tables.size() == 1)
                {
                    // only one table
                    refTables.addAll(tables);
                }
                else if (referencesAllTablesImplicitly(node))
                {
                    // references all columns from all tables
                    refTables.addAll(tables);
                }
                else if (tables.size() > 0 && refColumns.size() > 0)
                {
                    // only include tables that have a column the same name as one
                    // referenced in the condition
                    for (TableSymbol t : tables)
                    {
                        for (ColumnSymbol c : refColumns)
                        {
                            // if the table has a column of the same name
                            if (t.isOpen() || t.tryGetColumn(c.name(), new Out<ColumnSymbol>())) // PORT: §3.3 out _ discard
                            {
                                refTables.add(t);
                                break;
                            }
                        }
                    }
                }

                if (node.project() == null || node.project().projectKeyword().kind() == SyntaxKind.ProjectSmartKeyword)
                {
                    // project-smart

                    // any column that is common to all referenced tables
                    TableSymbol commonColumnsTable = _binder.getTableOfCommonColumns(refTables);
                    columns.addAll(commonColumnsTable.columns());

                    // any columns referenced explicitly in the predicate
                    for (ColumnSymbol c : refColumns)
                    {
                        if (!columns.contains(c))
                        {
                            columns.add(c);
                        }
                    }

                    getPackColumns(refTables, columns, packColumns);

                    if (packColumns.size() > 0)
                    {
                        columns.add(new ColumnSymbol("pack_", ScalarTypes.Dynamic, null, packColumns)); // PORT: §3.12 named arg originalColumns
                    }

                    Binder.unifyColumnsWithSameNameAndType(columns);
                }
                else
                {
                    // explicit projection
                    resultIsOpen = false;

                    // regular project
                    for (int i = 0; i < node.project().columns().size(); i++)
                    {
                        Expression exp = node.project().columns().get(i).element();

                        // PORT: §3.15 switch (exp) with type patterns, no default
                        if (exp instanceof PackExpression)
                        {
                            explicitPack = true;
                            if (i == node.project().columns().size() - 1)
                            {
                                getPackColumns(refTables, columns, packColumns);
                                columns.add(new ColumnSymbol("pack_", ScalarTypes.Dynamic, null, packColumns)); // PORT: §3.12 named arg originalColumns
                            }
                            else
                            {
                                diagnostics.add(DiagnosticFacts.getPackMustBeLastItemInList().withLocation(exp));
                            }
                        }
                        else if (exp instanceof TypedColumnReference tc)
                        {
                            _binder.checkIsColumn(tc.column(), diagnostics);
                            if (getReferencedSymbol(tc.column()) instanceof ColumnSymbol c)
                            {
                                TypeSymbol type = _binder.getTypeFromTypeExpression(tc.type(), diagnostics);
                                columns.add(new ColumnSymbol(c.name(), type, null, Arrays.asList(c))); // PORT: §3.12 named arg originalColumns
                            }
                        }
                        else if (exp instanceof NameReference nr)
                        {
                            _binder.checkIsColumn(nr, diagnostics);
                            if (getReferencedSymbol(nr) instanceof ColumnSymbol c2)
                            {
                                columns.add(c2);
                            }
                        }
                    }
                }

                if (node.projectAway() != null && node.projectAway().columns().size() > 0)
                {
                    if (node.projectAway().columns().get(0).element() instanceof StarExpression)
                    {
                        final String sourceColumnNameFinal = sourceColumnName; // PORT: §3.6 lambda captures must be effectively final
                        final boolean explicitPackFinal = explicitPack;
                        columns.removeIf(c -> !Objects.equals(c.name(), sourceColumnNameFinal) && (!"pack_".equals(c.name()) || !explicitPackFinal)); // PORT: §3.17 RemoveAll
                    }
                    else
                    {
                        // remove specified columns
                        LinkedHashSet<String> columnNamesToRemove = s_stringSetPool.allocateFromPool();
                        try
                        {
                            for (int i = 0; i < node.projectAway().columns().size(); i++)
                            {
                                Expression columnExp = node.projectAway().columns().get(i).element();
                                if (getReferencedSymbol(columnExp) instanceof ColumnSymbol col)
                                {
                                    columnNamesToRemove.add(col.name());
                                }
                            }

                            columns.removeIf(c -> columnNamesToRemove.contains(c.name())); // PORT: §3.17 RemoveAll
                        }
                        finally
                        {
                            s_stringSetPool.returnToPool(columnNamesToRemove);
                        }
                    }
                }

                TableSymbol resultTable = new TableSymbol(columns).withIsOpen(resultIsOpen);

                return new SemanticInfo(resultTable, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_columnListPool.returnToPool(columns);
                s_columnListPool.returnToPool(refColumns);
                s_columnListPool.returnToPool(packColumns);
                s_tableListPool.returnToPool(refTables);
            }
        }

        private static void getPackColumns(List<TableSymbol> tables, List<ColumnSymbol> projectedColumns, List<ColumnSymbol> packColumns)
        {
            LinkedHashSet<String> colNameMap = s_stringSetPool.allocateFromPool();
            try
            {
                // check to see if there is a table that has extra columns that need to be packed
                for (ColumnSymbol c : projectedColumns)
                {
                    colNameMap.add(c.name());
                }

                for (TableSymbol t : tables)
                {
                    // if table is open, then we don't know what ends up in projected set at execution time
                    if (t.isOpen())
                        continue;

                    // if the table has more columns than end up in the projected set
                    // then the rest will appear in a packed_ column
                    for (ColumnSymbol c : t.columns())
                    {
                        if (!colNameMap.contains(c.name()))
                        {
                            packColumns.add(c);
                            break;
                        }
                    }
                }
            }
            finally
            {
                s_stringSetPool.returnToPool(colNameMap);
            }
        }

        private static boolean referencesAllTablesImplicitly(FindOperator node)
        {
            // contains '*' expression as in '* has xxx', so this condition references all columns
            if (node.condition().getFirstDescendantOrSelf(StarExpression.class) != null) // PORT: §3.10
                return true;

            // has a stand alone search term, which is an abbreviation of '* has xxx'
            return node.condition().getFirstDescendantOrSelf(Expression.class, e -> isStandAloneSearchTerm(e)) != null; // PORT: §3.10
        }

        private static boolean isStandAloneSearchTerm(Expression expr)
        {
            // a stand-alone search term is a constant string that was adjusted to bool by SearchAndPredicateBinder
            return expr.isConstant() && expr.constantValue() instanceof String && expr.resultType() == ScalarTypes.Bool;
        }

        @Override
        public SemanticInfo visitUnionOperator(UnionOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            List<ColumnSymbol> columns = s_columnListPool.allocateFromPool();
            List<TableSymbol> tables = s_tableListPool.allocateFromPool();
            try
            {
                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.UnionParameters, diagnostics);

                String name = SyntaxExtensions.getParameterNameValue(node.parameters(), QueryOperatorParameters.WithSource); // PORT: §3.5
                if (name != null) // PORT: §3.15 is string name
                {
                    columns.add(new ColumnSymbol(name, ScalarTypes.String));
                }

                if (rowScopeOrEmpty() != null)
                {
                    tables.add(rowScopeOrEmpty());
                }

                for (int i = 0, n = node.expressions().size(); i < n; i++)
                {
                    Expression expr = node.expressions().get(i).element();
                    _binder.checkIsTabular(expr, diagnostics);
                    _binder.addTables(getResultType(expr), tables);
                }

                TableSymbol unifiedTable = _binder.getTableOfColumnsUnifiedByNameAndType(tables);

                TableSymbol resultTable = unifiedTable;

                if (columns.size() > 0)
                {
                    columns.addAll(unifiedTable.columns());
                    resultTable = new TableSymbol(columns);
                }

                boolean anyOpen = false; // PORT: §3.6 tables.Any(t => t.IsOpen)
                for (TableSymbol t : tables)
                {
                    if (t.isOpen())
                    {
                        anyOpen = true;
                        break;
                    }
                }
                if (anyOpen)
                {
                    resultTable = resultTable.withIsOpen(true);
                }

                return new SemanticInfo(resultTable.withIsSorted(false), diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_columnListPool.returnToPool(columns);
                s_tableListPool.returnToPool(tables);
            }
        }

        private static final ObjectPool<List<JoinColumnPair>> s_joinColumnsPool =
            new ObjectPool<List<JoinColumnPair>>(() -> new ArrayList<JoinColumnPair>(), list -> list.clear());

        @Override
        public SemanticInfo visitLookupOperator(LookupOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            List<ColumnSymbol> columns = s_columnListPool.allocateFromPool();
            List<ColumnSymbol> exprColumns = s_columnListPool.allocateFromPool();
            List<JoinColumnPair> joinColumns = s_joinColumnsPool.allocateFromPool();

            try
            {
                checkNotFirstInPipe(node, diagnostics);

                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.LookupParameters, diagnostics);

                _binder.checkIsTabular(node.expression(), diagnostics);

                // check the lookup clause(s)
                if (node.lookupClause() instanceof JoinOnClause joc)
                {
                    checkJoinOnClause(joc, diagnostics, joinColumns);
                }

                // figure out the result columns
                _binder.getDeclaredAndInferredColumns(rowScopeOrEmpty(), exprColumns);

                // substitute common column for any left-side column used in join condition
                for (ColumnSymbol col : exprColumns)
                {
                    int index = ListExtensions.firstIndex(joinColumns, jc0 -> jc0.left() == col); // PORT: §3.6 lambda parameter renamed, jc is declared below
                    if (index >= 0 && index < joinColumns.size())
                    {
                        JoinColumnPair jc = joinColumns.get(index);
                        columns.add(new ColumnSymbol(jc.left().name(), jc.left().type(), jc.left().description(), Arrays.asList(jc.left(), jc.right()))); // PORT: §3.12 named arg originalColumns
                    }
                    else
                    {
                        columns.add(col);
                    }
                }

                boolean resultIsOpen = rowScopeOrEmpty().isOpen();

                TableSymbol exprTable = getResultType(node.expression()) instanceof TableSymbol ts ? ts : null; // PORT: §3.15 as
                if (exprTable != null)
                {
                    exprColumns.clear();
                    _binder.getDeclaredAndInferredColumns(exprTable, exprColumns);

                    // do not include any right-side columns that were used in join condition
                    // since they are already represented by the common column
                    // PORT: §3.6 exprColumns.RemoveAll(c => joinColumns.Any(jc => jc.Right == c))
                    exprColumns.removeIf(c ->
                    {
                        for (JoinColumnPair jc : joinColumns)
                        {
                            if (jc.right() == c)
                            {
                                return true;
                            }
                        }
                        return false;
                    });

                    columns.addAll(exprColumns);
                    resultIsOpen |= exprTable.isOpen();
                }

                Binder.makeColumnNamesUnique(columns);

                TableSymbol resultTable = new TableSymbol(columns).withIsOpen(resultIsOpen);

                return new SemanticInfo(resultTable, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_columnListPool.returnToPool(columns);
                s_columnListPool.returnToPool(exprColumns);
                s_joinColumnsPool.returnToPool(joinColumns);
            }
        }

        private static boolean isAntiOrSemiJoin(String joinKind)
        {
            return isLeftAntiOrSemiJoin(joinKind)
                || isRightAntiOrSemiJoin(joinKind);
        }

        private static boolean isLeftAntiOrSemiJoin(String joinKind)
        {
            if (joinKind == null) // PORT: §3.14 switch on null string falls to default
                return false;

            switch (joinKind)
            {
                case "anti":
                case "leftanti":
                case "leftsemi":
                case "leftantisemi":
                    return true;
                default:
                    return false;
            }
        }

        private static boolean isRightAntiOrSemiJoin(String joinKind)
        {
            if (joinKind == null) // PORT: §3.14 switch on null string falls to default
                return false;

            switch (joinKind)
            {
                case "rightanti":
                case "rightsemi":
                case "rightantisemi":
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public SemanticInfo visitJoinOperator(JoinOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            List<ColumnSymbol> columns = s_columnListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.JoinParameters, diagnostics);

                _binder.checkIsTabular(node.expression(), diagnostics);

                // check the join condition(s)
                // PORT: §3.15 switch (node.ConditionClause) with type patterns
                if (node.conditionClause() instanceof JoinOnClause c)
                {
                    checkJoinOnClause(c, diagnostics);
                }
                else if (node.conditionClause() instanceof JoinWhereClause c2)
                {
                    _binder.checkIsExactType(c2.expression(), ScalarTypes.Bool, diagnostics);
                }
                else
                {
                    diagnostics.add(DiagnosticFacts.getMissingJoinOnClause().withLocation(node));
                }

                String joinKind = SyntaxExtensions.getParameterLiteralValue(node.parameters(), String.class, QueryOperatorParameters.Kind); // PORT: §3.5, §3.10
                boolean resultIsOpen = false;

                // if not explicitly a right-anti/semi join, then add left-side columns
                if (!isRightAntiOrSemiJoin(joinKind))
                {
                    // add left-side columns
                    columns.addAll(_binder.getDeclaredAndInferredColumns(rowScopeOrEmpty()));
                    resultIsOpen |= rowScopeOrEmpty().isOpen();
                }

                TableSymbol exprTable = getResultType(node.expression()) instanceof TableSymbol ts ? ts : null; // PORT: §3.15 as
                if (exprTable != null && !isLeftAntiOrSemiJoin(joinKind))
                {
                    // add right-side columns
                    _binder.getDeclaredAndInferredColumns(exprTable, columns);
                    resultIsOpen |= exprTable.isOpen();
                }

                Binder.makeColumnNamesUnique(columns);

                TableSymbol resultTable = new TableSymbol(columns).withIsOpen(resultIsOpen);

                return new SemanticInfo(resultTable, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_columnListPool.returnToPool(columns);
            }
        }

        private static final class JoinColumnPair // PORT: §2.6 nested class
        {
            private final ColumnSymbol _left; // PORT: §3.1 Left { get; }
            private final ColumnSymbol _right; // PORT: §3.1 Right { get; }
            public ColumnSymbol left() { return _left; }
            public ColumnSymbol right() { return _right; }
            public JoinColumnPair(ColumnSymbol left, ColumnSymbol right) { this._left = left; this._right = right; }
        }

        private void checkJoinOnClause(
            JoinOnClause clause,
            List<Diagnostic> diagnostics,
            List<JoinColumnPair> joinColumns)
        {
            for (int i = 0, n = clause.expressions().size(); i < n; i++)
            {
                Expression expr = clause.expressions().get(i).element();
                checkJoinOnExpression(expr, diagnostics, joinColumns);
            }
        }

        private void checkJoinOnClause(
            JoinOnClause clause,
            List<Diagnostic> diagnostics) // PORT: §3.12 optional parameter joinColumns = null
        {
            checkJoinOnClause(clause, diagnostics, null);
        }

        private void checkJoinOnExpression(
            Expression condition,
            List<Diagnostic> diagnostics,
            List<JoinColumnPair> joinColumns)
        {
            condition = removeParenthesis(condition);

            if (condition instanceof BinaryExpression be)
            {
                if (be.kind() == SyntaxKind.EqualExpression)
                {
                    Out<ColumnSymbol> leftMatchingColumn = new Out<>();
                    Out<ColumnSymbol> rightMatchingColumn = new Out<>();
                    if (checkJoinOnEquality(be, diagnostics, leftMatchingColumn, rightMatchingColumn))
                    {
                        if (leftMatchingColumn.value != null && rightMatchingColumn.value != null && joinColumns != null)
                        {
                            joinColumns.add(new JoinColumnPair(leftMatchingColumn.value, rightMatchingColumn.value));
                        }
                    }
                }
                else if (be.kind() == SyntaxKind.AndExpression)
                {
                    checkJoinOnExpression(be.left(), diagnostics, joinColumns);
                    checkJoinOnExpression(be.right(), diagnostics, joinColumns);
                }
                else
                {
                    diagnostics.add(DiagnosticFacts.getInvalidJoinCondition().withLocation(condition));
                }
            }
            else if (condition instanceof NameReference nr)
            {
                if (_binder.checkIsColumn(nr, diagnostics))
                {
                    Out<ColumnSymbol> leftColumn = new Out<>();
                    Out<ColumnSymbol> rightColumn = new Out<>();
                    if (checkCommonColumn(nr, diagnostics, leftColumn, rightColumn))
                    {
                        if (leftColumn.value != null && rightColumn.value != null && joinColumns != null)
                        {
                            joinColumns.add(new JoinColumnPair(leftColumn.value, rightColumn.value));
                        }
                    }

                    _binder.checkIsScalar(condition, diagnostics); // are there non-scalar columns?
                }
            }
            else
            {
                diagnostics.add(DiagnosticFacts.getInvalidJoinCondition().withLocation(condition));
            }
        }

        private void checkJoinOnExpression(
            Expression condition,
            List<Diagnostic> diagnostics) // PORT: §3.12 optional parameter joinColumns = null
        {
            checkJoinOnExpression(condition, diagnostics, null);
        }

        private boolean checkCommonColumn(
            NameReference name,
            List<Diagnostic> diagnostics,
            Out<ColumnSymbol> leftColumn,
            Out<ColumnSymbol> rightColumn)
        {
            rightColumn.value = null;
            leftColumn.value = null; // PORT: §3.3 an out parameter is always assigned; the callee may short-circuit past it

            if (_binder.tryGetDeclaredOrInferredColumn(rowScopeOrEmpty(), name.simpleName(), leftColumn)
                && _binder.tryGetDeclaredOrInferredColumn(rightRowScopeOrEmpty(), name.simpleName(), rightColumn))
            {
                if (leftColumn.value.type() != rightColumn.value.type()
                    && leftColumn.value.type() != ScalarTypes.Unknown
                    && rightColumn.value.type() != ScalarTypes.Unknown)
                {
                    diagnostics.add(DiagnosticFacts.getCommonJoinColumnsMustHaveSameType(name.simpleName()).withLocation(name));
                    return false;
                }
                else
                {
                    return true;
                }
            }
            else
            {
                diagnostics.add(DiagnosticFacts.getColumnMustExistOnBothSidesOfJoin(name.simpleName()).withLocation(name));
                return false;
            }
        }

        private boolean checkJoinOnEquality(
            Expression condition,
            List<Diagnostic> diagnostics,
            Out<ColumnSymbol> leftColumn,
            Out<ColumnSymbol> rightColumn)
        {
            leftColumn.value = null;
            rightColumn.value = null;
            condition = removeParenthesis(condition);

            if (!(condition instanceof BinaryExpression be
                && condition.kind() == SyntaxKind.EqualExpression))
            {
                diagnostics.add(DiagnosticFacts.getInvalidJoinCondition().withLocation(condition));
                return false;
            }

            if (checkJoinOnEqualityOperand(be.left(), "$left", diagnostics, leftColumn)
                & checkJoinOnEqualityOperand(be.right(), "$right", diagnostics, rightColumn)) // non-short-circuit & is intentional upstream
            {
                return checkJoinOnKeysComparable(be.left(), be.right(), diagnostics);
            }

            return false;
        }

        private static boolean checkJoinOnKeysComparable(Expression left, Expression right, List<Diagnostic> diagnostics)
        {
            ScalarSymbol leftType = left.resultType() instanceof ScalarSymbol ls ? ls : ScalarTypes.Unknown; // PORT: §3.14 as ... ??
            ScalarSymbol rightType = right.resultType() instanceof ScalarSymbol rs ? rs : ScalarTypes.Unknown; // PORT: §3.14 as ... ??

            // join keys cannot be dynamic
            if (leftType instanceof DynamicSymbol)
            {
                if (diagnostics != null)
                    diagnostics.add(DiagnosticFacts.getJoinKeyCannotBeDynamic().withLocation(left));
                return false;
            }
            else if (rightType instanceof DynamicSymbol)
            {
                if (diagnostics != null)
                    diagnostics.add(DiagnosticFacts.getJoinKeyCannotBeDynamic().withLocation(right));
                return false;
            }

            // unknown or error so ignore
            if (leftType == ScalarTypes.Unknown || leftType.isError()
                || rightType == ScalarTypes.Unknown || rightType.isError())
            {
                return true;
            }

            // join keys must be consistent data type
            if (promote(leftType) != promote(rightType))
            {
                if (diagnostics != null)
                    diagnostics.add(DiagnosticFacts.getJoinKeysNotComparable(leftType.name(), rightType.name()));
                return false;
            }

            return true;
        }

        private boolean checkJoinOnEqualityOperand(Expression operand, String prefix, List<Diagnostic> diagnostics, Out<ColumnSymbol> column)
        {
            column.value = null;
            operand = removeParenthesis(operand);

            if (!(operand instanceof PathExpression path
                && Objects.equals(getReferencedName(path.expression()), prefix))) // look for $left.c or $right.c
            {
                diagnostics.add(DiagnosticFacts.getInvalidJoinConditionOperand(prefix).withLocation(operand));
                return false;
            }

            // look for $left.c or $right.c
            if (_binder.checkIsColumn(operand, diagnostics))
            {
                column.value = getReferencedSymbol(operand) instanceof ColumnSymbol cs ? cs : null; // PORT: §3.15 as
                return _binder.checkIsScalar(operand, diagnostics); // are there non-scalar columns?
            }

            return false;
        }

        private static Expression removeParenthesis(Expression expression)
        {
            while (expression instanceof ParenthesizedExpression pe)
                expression = pe.expression();

            return expression;
        }

        private static String getReferencedName(Expression expression)
        {
            // PORT: §3.15 switch (expression) with type patterns
            if (expression instanceof NameReference nr)
            {
                return nr.simpleName();
            }
            else if (expression instanceof BracketedExpression br)
            {
                if (br.expression().kind() == SyntaxKind.StringLiteralExpression
                    || br.expression().kind() == SyntaxKind.CompoundStringLiteralExpression)
                {
                    return (String)br.expression().literalValue();
                }
            }

            return null;
        }

        @Override
        public SemanticInfo visitRangeOperator(RangeOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkFirstInPipe(node, diagnostics);

                _binder.checkIsSummable(node.from(), diagnostics);
                _binder.checkIsSummable(node.to(), diagnostics);
                _binder.checkIsSummable(node.step(), diagnostics);
                _binder.checkIsNotConstantValue(node.step(), 0L, false, diagnostics);

                TypeSymbol fromType = getResultTypeOrError(node.from());
                TypeSymbol toType = getResultTypeOrError(node.to());
                TypeSymbol stepType = getResultTypeOrError(node.step());
                TypeSymbol fromToType = TypeFacts.getCommonScalarType(fromType, toType);
                if (fromToType == null) // PORT: §3.14 ??
                {
                    fromToType = ScalarTypes.Unknown;
                }
                TypeSymbol rangeType = (fromToType == stepType && stepType == ScalarTypes.Int)
                    ? ScalarTypes.Int // does not match add semantics here
                    : _binder.getBinaryOperatorResultType(OperatorKind.Add, fromToType, stepType, node.step(), diagnostics);

                String rangeName = node.name().simpleName();
                TableSymbol result = new TableSymbol(new ColumnSymbol(rangeName, rangeType));

                return new SemanticInfo(result, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitFacetOperator(FacetOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                List<TableSymbol> tables = new ArrayList<>();

                // add with table type to combined output if there is a with clause
                if (node.withClause() != null)
                {
                    TableSymbol tableType;
                    // PORT: §3.15 switch (node.WithClause) with type patterns, no default
                    if (node.withClause() instanceof FacetWithOperatorClause c)
                    {
                        _binder.checkIsTabular(c.operator(), diagnostics);
                        checkQueryOperators(c.operator(), KustoFacts.ForkOperatorKinds, diagnostics);
                        tableType = getResultType(c.operator()) instanceof TableSymbol ts ? ts : null; // PORT: §3.15 as
                        if (tableType != null)
                        {
                            tables.add(tableType);
                        }
                    }
                    else if (node.withClause() instanceof FacetWithExpressionClause c2)
                    {
                        _binder.checkIsTabular(c2.expression(), diagnostics);
                        tableType = getResultType(c2.expression()) instanceof TableSymbol ts2 ? ts2 : null; // PORT: §3.15 as
                        if (tableType != null)
                        {
                            tables.add(tableType);
                        }
                    }
                }

                // create a separate facet table for each column specified
                for (int i = 0, n = node.expressions().size(); i < n; i++)
                {
                    Expression expr = node.expressions().get(i).element();
                    boolean _discard = _binder.checkIsColumn(expr, diagnostics) // PORT: §2.3 '_' is a Java keyword
                        && _binder.checkIsNotType(expr, ScalarTypes.Dynamic, diagnostics);

                    String name = getExpressionResultName(expr);
                    String tableName = i == 0 ? "Facet" : "Facet_" + (i + 1);

                    TableSymbol table = new TableSymbol(
                            tableName,
                            getOrDeclareColumnForExpression(expr, name),
                            getOrDeclareColumnForExpression(expr, "count_" + DotNet.str(name), ScalarTypes.Long)) // PORT: §3.14 string concat
                        .withInheritableProperties(rowScopeOrEmpty())
                        .withIsSorted(false);

                    tables.add(table);
                }

                return new SemanticInfo(new GroupSymbol(tables), diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitMakeSeriesOperator(MakeSeriesOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            ProjectionBuilder builder = s_projectionBuilderPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);
                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.MakeSeriesParameters, diagnostics);

                // check by clause first, because these columns are first in the result
                if (node.byClause() != null)
                {
                    for (int i = 0, n = node.byClause().expressions().size(); i < n; i++)
                    {
                        Expression expr = node.byClause().expressions().get(i).element();
                        _binder.checkIsScalar(expr, diagnostics);
                        _binder.createProjectionColumns(expr, builder, diagnostics);
                    }
                }

                for (int i = 0, n = node.aggregates().size(); i < n; i++)
                {
                    MakeSeriesExpression agg = node.aggregates().get(i).element();

                    _binder.checkIsScalar(agg.expression(), diagnostics);

                    if (agg.defaultExpression() != null)
                    {
                        _binder.checkIsLiteral(agg.defaultExpression(), diagnostics);
                        _binder.checkIsType(agg.defaultExpression(), getResultTypeOrError(agg.expression()), Conversion.Promotable, diagnostics);
                    }

                    _binder.createProjectionColumns(agg.expression(), builder, diagnostics, ProjectionStyle.Summarize, false, ScalarTypes.Dynamic); // PORT: §3.12 named args style, columnType
                }

                if (node.onClause() != null)
                {
                    _binder.checkIsScalar(node.onClause().expression(), diagnostics);
                    _binder.createProjectionColumns(node.onClause().expression(), builder, diagnostics, ProjectionStyle.Default, false, ScalarTypes.Dynamic); // PORT: §3.12 named arg columnType

                    if (node.rangeClause() instanceof MakeSeriesInRangeClause inRangeClause)
                    {
                        if (!inRangeClause.containsSyntaxDiagnostics())
                        {
                            checkArgumentCount(inRangeClause.arguments().expressions(), 3, diagnostics);
                        }

                        if (inRangeClause.arguments().expressions().size() == 3)
                        {
                            _binder.checkIsType(inRangeClause.arguments().expressions().get(0).element(), getResultTypeOrError(node.onClause().expression()), Conversion.Promotable, diagnostics);
                            _binder.checkIsType(inRangeClause.arguments().expressions().get(1).element(), getResultTypeOrError(node.onClause().expression()), Conversion.Promotable, diagnostics);
                            _binder.checkIsIntervalType(inRangeClause.arguments().expressions().get(2).element(), getResultTypeOrError(node.onClause().expression()), diagnostics);
                        }
                    }
                    else if (node.rangeClause() instanceof MakeSeriesFromToStepClause fromToClause)
                    {
                        if (fromToClause.makeSeriesFromClause() != null && fromToClause.makeSeriesFromClause().expression() != null) // PORT: §3.14 ?.
                        {
                            _binder.checkIsType(fromToClause.makeSeriesFromClause().expression(), getResultTypeOrError(node.onClause().expression()), Conversion.Promotable, diagnostics);
                        }

                        if (fromToClause.makeSeriesToClause() != null && fromToClause.makeSeriesToClause().expression() != null) // PORT: §3.14 ?.
                        {
                            _binder.checkIsType(fromToClause.makeSeriesToClause().expression(), getResultTypeOrError(node.onClause().expression()), Conversion.Promotable, diagnostics);
                        }
                        _binder.checkIsIntervalType(fromToClause.makeSeriesStepClause().expression(), getResultTypeOrError(node.onClause().expression()), diagnostics);
                    }
                }

                TableSymbol resultType = new TableSymbol(builder.getProjection())
                    .withInheritableProperties(rowScopeOrEmpty())
                    .withIsSorted(false);

                return new SemanticInfo(resultType, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_projectionBuilderPool.returnToPool(builder);
            }
        }

        @Override
        public SemanticInfo visitMvExpandOperator(MvExpandOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            ProjectionBuilder builder = s_projectionBuilderPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                builder.addRange(_binder.getDeclaredAndInferredColumns(rowScopeOrEmpty()), false, true); // PORT: §3.12 named arg doNotRepeat

                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.MvExpandParameters, diagnostics);

                for (int i = 0, n = node.expressions().size(); i < n; i++)
                {
                    MvExpandExpression expr = node.expressions().get(i).element();

                    _binder.checkIsDynamic(expr.expression(), diagnostics);

                    TypeSymbol newType = getMvExpandResultType(node.parameters(), expr.expression(), expr.toTypeOf());
                    _binder.createProjectionColumns(expr.expression(), builder, diagnostics, ProjectionStyle.Replace, false, newType); // PORT: §3.12 named args style, columnType
                }

                String indexName = SyntaxExtensions.getParameterNameValue(node.parameters(), QueryOperatorParameters.WithItemIndex); // PORT: §3.5
                if (indexName != null) // PORT: §3.15 is string indexName
                {
                    builder.add(new ColumnSymbol(indexName, ScalarTypes.Long));
                }

                if (node.rowLimitClause() != null)
                {
                    _binder.checkIsInteger(node.rowLimitClause().rowLimit(), diagnostics);
                }

                TableSymbol result = new TableSymbol(builder.getProjection())
                    .withInheritableProperties(rowScopeOrEmpty());

                return new SemanticInfo(result, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_projectionBuilderPool.returnToPool(builder);
            }
        }

        private static TypeSymbol getMvExpandResultType(SyntaxList1<NamedParameter> parameters, Expression expression, ToTypeOfClause toTypeOf)
        {
            TypeSymbol newType;

            // PORT: §3.14 toTypeOf?.TypeOf?.ReferencedSymbol is TypeSymbol toTypeOfType
            Symbol toTypeOfSymbol = toTypeOf != null && toTypeOf.typeOf() != null ? toTypeOf.typeOf().referencedSymbol() : null;
            if (toTypeOfSymbol instanceof TypeSymbol toTypeOfType)
            {
                // to type of clause gives result type
                newType = toTypeOfType;
            }
            else if (expression.resultType() instanceof DynamicArraySymbol)
            {
                // initial expression is known to be an array, so each item will be an element of the array
                newType = TypeFacts.getElementType(expression.resultType());
            }
            else if (expression.resultType() instanceof DynamicBagSymbol)
            {
                String bagexpKind = SyntaxExtensions.getParameterNameValue(parameters, QueryOperatorParameters.BagExpansion); // PORT: §3.5
                if (bagexpKind == null) // PORT: §3.14 ??
                {
                    bagexpKind = SyntaxExtensions.getParameterNameValue(parameters, QueryOperatorParameters.Kind); // PORT: §3.5
                }

                // initial expression is known to be a bag, so give better result type than just 'dynamic'.
                if ("array".equals(bagexpKind)) // PORT: §3.14
                {
                    // each row of expansion of bag gets an array for each name:value pair containing the name and value.
                    newType = ScalarTypes.DynamicArray;
                }
                else
                {
                    // each row of expansion of bag gets a small bag with one name:value
                    newType = ScalarTypes.DynamicBag;
                }
            }
            else if (expression.resultType() instanceof DynamicSymbol)
            {
                // we don't actually know if this column is a bag or array,
                // so must return type as dynamic.
                newType = ScalarTypes.Dynamic;
            }
            else
            {
                // not even dynamic?  Error case, just return column's type.
                newType = expression.resultType();
            }

            return newType;
        }

        @Override
        public SemanticInfo visitMvExpandExpression(MvExpandExpression node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                TypeSymbol colType = getResultTypeOrError(node.expression());

                if (node.toTypeOf() != null)
                {
                    colType = getReferencedSymbol(node.toTypeOf().typeOf()) instanceof TypeSymbol ts ? ts : ErrorSymbol.Instance; // PORT: §3.14 as ... ??
                }

                return new SemanticInfo(colType, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitMvApplyOperator(MvApplyOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            ProjectionBuilder builder = s_projectionBuilderPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                builder.addRange(_binder.getDeclaredAndInferredColumns(rowScopeOrEmpty()), false, true); // PORT: §3.12 named arg doNotRepeat

                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.MvApplyParameters, diagnostics);

                for (int i = 0, n = node.expressions().size(); i < n; i++)
                {
                    MvApplyExpression expr = node.expressions().get(i).element();

                    _binder.checkIsDynamic(expr.expression(), diagnostics);

                    TypeSymbol newType = getMvExpandResultType(node.parameters(), expr.expression(), expr.toTypeOf());

                    _binder.createProjectionColumns(expr.expression(), builder, diagnostics, ProjectionStyle.Replace, false, newType); // PORT: §3.12 named args columnType, style
                }

                String indexName = SyntaxExtensions.getParameterNameValue(node.parameters(), QueryOperatorParameters.WithItemIndex); // PORT: §3.5
                if (indexName != null) // PORT: §3.15 is string indexName
                {
                    builder.add(new ColumnSymbol(indexName, ScalarTypes.Long));
                }

                if (node.rowLimitClause() != null)
                {
                    _binder.checkIsInteger(node.rowLimitClause().rowLimit(), diagnostics);
                }

                // ignore Subquery value here (see TreeBinder)
                // the schema returned here is used for the type flowing into the subquery
                TableSymbol result = new TableSymbol(builder.getProjection())
                    .withInheritableProperties(rowScopeOrEmpty());

                return new SemanticInfo(result, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_projectionBuilderPool.returnToPool(builder);
            }
        }

        @Override
        public SemanticInfo visitMvApplyExpression(MvApplyExpression node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                TypeSymbol colType = node.expression() != null && node.expression().resultType() != null ? node.expression().resultType() : ErrorSymbol.Instance; // PORT: §3.14 ?. and ??

                if (node.toTypeOf() != null)
                {
                    // PORT: §3.14 node.ToTypeOf?.TypeOf?.ResultType as TypeSymbol ?? ErrorSymbol.Instance
                    TypeSymbol toTypeOfResult = node.toTypeOf().typeOf() != null ? node.toTypeOf().typeOf().resultType() : null;
                    colType = toTypeOfResult != null ? toTypeOfResult : ErrorSymbol.Instance;
                }

                return new SemanticInfo(colType, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitMvApplySubqueryExpression(MvApplySubqueryExpression node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkQueryOperators(node.expression(), KustoFacts.PostPipeOperatorKinds, diagnostics, true, true); // PORT: §3.12 named arg allowContextualRoot

                TypeSymbol resultType = getResultTypeOrError(node.expression());
                if (resultType instanceof TableSymbol table)
                {
                    resultType = new TableSymbol(_binder.getDeclaredAndInferredColumns(table))
                        .withInheritableProperties(table);
                }

                return new SemanticInfo(resultType, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitPrintOperator(PrintOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            ProjectionBuilder builder = s_projectionBuilderPool.allocateFromPool();
            try
            {
                checkFirstInPipe(node, diagnostics);

                for (int i = 0, n = node.expressions().size(); i < n; i++)
                {
                    Expression expr = node.expressions().get(i).element();
                    _binder.checkIsScalar(expr, diagnostics);

                    _binder.createProjectionColumns(expr, builder, diagnostics, ProjectionStyle.Print, false, null, "print_" + i); // PORT: §3.12 named args style, columnName
                }

                TableSymbol resultType = new TableSymbol(builder.getProjection());
                return new SemanticInfo(resultType, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_projectionBuilderPool.returnToPool(builder);
            }
        }

        @Override
        public SemanticInfo visitReduceByOperator(ReduceByOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            List<ColumnSymbol> columns = s_columnListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.ReduceParameters, diagnostics);

                _binder.checkIsExactType(node.expression(), ScalarTypes.String, diagnostics);

                if (node.with() != null)
                {
                    _binder.checkQueryOperatorParametersOfNamedParameters(node.with().parameters(), QueryOperatorParameters.ReduceWithParameters, diagnostics); // PORT: §2.5
                }

                TableSymbol resultType;

                String kind = SyntaxExtensions.getParameterLiteralValue(node.parameters(), String.class, QueryOperatorParameters.Kind); // PORT: §3.5, §3.10
                if ("source".equals(kind)) // PORT: §3.14
                {
                    _binder.getDeclaredAndInferredColumns(this.rowScopeOrEmpty(), columns);
                }

                columns.add(new ColumnSymbol("Pattern", ScalarTypes.String, null, null, node.expression())); // PORT: §3.12 named arg source
                columns.add(new ColumnSymbol("Count", ScalarTypes.Long, null, null, node.expression())); // PORT: §3.12 named arg source
                columns.add(new ColumnSymbol("Representative", ScalarTypes.String, null, null, node.expression())); // PORT: §3.12 named arg source
                resultType = new TableSymbol(columns);
                return new SemanticInfo(resultType, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_columnListPool.returnToPool(columns);
            }
        }

        @Override
        public SemanticInfo visitRenderOperator(RenderOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            List<ColumnSymbol> columns = s_columnListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                _binder.checkIsToken(node.chartType(), KustoFacts.ChartTypes, true, diagnostics);
                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.RenderParameters, diagnostics);

                if (node.withClause() != null)
                {
                    _binder.checkQueryOperatorParametersOfNamedParameters(node.withClause().properties(), QueryOperatorParameters.RenderWithProperties, diagnostics); // PORT: §2.5
                }

                _binder.getDeclaredAndInferredColumns(this.rowScopeOrEmpty(), columns);

                TableSymbol resultTable = new TableSymbol(columns)
                    .withInheritableProperties(rowScopeOrEmpty());

                return new SemanticInfo(resultTable, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_columnListPool.returnToPool(columns);
            }
        }

        private SemanticInfo parseVisitCommon(QueryOperator node, Expression expression, SyntaxList1<SyntaxNode> patterns, SyntaxList1<NamedParameter> parameters)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            List<ColumnSymbol> columns = s_columnListPool.allocateFromPool();
            LinkedHashSet<String> declaredNames = s_stringSetPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                _binder.getDeclaredAndInferredColumns(rowScopeOrEmpty(), columns);

                _binder.checkQueryOperatorParameters(parameters, QueryOperatorParameters.ParseParameters, diagnostics);
                _binder.checkIsScalar(expression, diagnostics);

                for (int i = 0, n = patterns.size(); i < n; i++)
                {
                    SyntaxNode part = patterns.get(i);

                    // check for legal pattern arrangment
                    switch (part.kind())
                    {
                        case StringLiteralExpression:
                        case CompoundStringLiteralExpression:
                            break;

                        case StarExpression:
                            if (i < patterns.size() - 1)
                            {
                                SyntaxNode nextPart = patterns.get(i + 1);
                                if (nextPart.kind() != SyntaxKind.StringLiteralExpression && nextPart.kind() != SyntaxKind.CompoundStringLiteralExpression)
                                {
                                    diagnostics.add(DiagnosticFacts.getParsePatternStringLiteralMustFollowStar().withLocation(part));
                                }
                            }

                            if (i > 0)
                            {
                                SyntaxNode prevPart = patterns.get(i - 1);
                                if (prevPart.kind() == SyntaxKind.NameDeclaration
                                    || prevPart.kind() == SyntaxKind.BracketedExpression
                                    || (prevPart instanceof NameAndTypeDeclaration nat
                                        && nat.type() instanceof PrimitiveTypeExpression pt
                                        && Binder.getType(pt) == ScalarTypes.String))
                                {
                                    diagnostics.add(DiagnosticFacts.getParsePatternUsingStarAfterStringColumnIsAmbiguous().withLocation(part));
                                }
                            }
                            break;

                        case NameDeclaration:
                        case NameAndTypeDeclaration:
                            if (i > 0)
                            {
                                SyntaxNode prevPart = patterns.get(i - 1);
                                if (prevPart.kind() != SyntaxKind.StringLiteralExpression && prevPart.kind() != SyntaxKind.CompoundStringLiteralExpression)
                                {
                                    diagnostics.add(DiagnosticFacts.getParsePatternNameDoesNotFollowStringLiteral().withLocation(part));
                                }
                            }
                            break;

                        default:
                            // TODO: does this ever happen?
                            diagnostics.add(DiagnosticFacts.getInvalidPatternPart().withLocation(part));
                            break;
                    }

                    // gather column declarations
                    // PORT: §3.15 switch (part) with type patterns, no default
                    if (part instanceof NameDeclaration nd)
                    {
                        if (declareColumnName(declaredNames, nd.simpleName(), diagnostics, nd))
                        {
                            ColumnSymbol col = new ColumnSymbol(nd.simpleName(), ScalarTypes.String, null, null, expression); // PORT: §3.12 named arg source
                            columns.add(col);
                            _binder.setSemanticInfo(nd, getSemanticInfo(col));
                        }
                    }
                    else if (part instanceof NameAndTypeDeclaration nat2)
                    {
                        if (nat2.type() instanceof PrimitiveTypeExpression pt2
                            && declareColumnName(declaredNames, nat2.name().simpleName(), diagnostics, nat2.name()))
                        {
                            TypeSymbol type = Binder.getType(pt2);
                            ColumnSymbol col = new ColumnSymbol(nat2.name().simpleName(), type, null, null, expression); // PORT: §3.12 named arg source
                            columns.add(col);
                            _binder.setSemanticInfo(nat2.name(), getSemanticInfo(col));
                        }
                    }
                }

                TableSymbol result = new TableSymbol(columns)
                    .withInheritableProperties(rowScopeOrEmpty());

                return new SemanticInfo(result, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_columnListPool.returnToPool(columns);
                s_stringSetPool.returnToPool(declaredNames);
            }
        }

        @Override
        public SemanticInfo visitParseWhereOperator(ParseWhereOperator node)
        {
            return parseVisitCommon(node, node.expression(), node.patterns(), node.parameters());
        }

        @Override
        public SemanticInfo visitParseOperator(ParseOperator node)
        {
            return parseVisitCommon(node, node.expression(), node.patterns(), node.parameters());
        }

        @Override
        public SemanticInfo visitParseKvWithClause(ParseKvWithClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitParseKvOperator(ParseKvOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            List<ColumnSymbol> columns = s_columnListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                _binder.getDeclaredAndInferredColumns(rowScopeOrEmpty(), columns);
                _binder.checkIsScalar(node.expression(), diagnostics);

                createColumnsFromRowSchema(node.keys().columns(), columns, diagnostics);

                if (node.withClause() != null)
                {
                    _binder.checkQueryOperatorParametersOfNamedParameters(node.withClause().properties(), QueryOperatorParameters.ParseKvWithProperties, diagnostics); // PORT: §2.5
                }

                TableSymbol result = new TableSymbol(columns)
                    .withInheritableProperties(rowScopeOrEmpty());

                return new SemanticInfo(result, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_columnListPool.returnToPool(columns);
            }
        }

        @Override
        public SemanticInfo visitInvokeOperator(InvokeOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                return new SemanticInfo(getResultTypeOrError(node.function()), diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitEvaluateOperator(EvaluateOperator node)
        {
            FunctionCallExpression call = node.functionCall();
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            List<ColumnSymbol> columns = s_columnListPool.allocateFromPool();

            try
            {
                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.EvaluateParameters, diagnostics);

                if (node.schema() != null)
                {
                    // if starts with asterisk (*) add any existing columns that is not re-declared by the output schema expression
                    if (node.schema().schema().asteriskToken() != null
                        && node.schema().schema().asteriskToken().width() > 0)
                    {
                        if (getResultTypeOrError(node.functionCall()) instanceof TableSymbol pluginResult)
                        {
                            LinkedHashSet<String> explicitNames = s_stringSetPool.allocateFromPool();
                            try
                            {
                                for (int i = 0; i < node.schema().schema().columns().size(); i++)
                                {
                                    explicitNames.add(node.schema().schema().columns().get(i).element().name().simpleName());
                                }

                                for (ColumnSymbol col : pluginResult.columns())
                                {
                                    if (!explicitNames.contains(col.name()))
                                        columns.add(col);
                                }
                            }
                            finally
                            {
                                s_stringSetPool.returnToPool(explicitNames);
                            }
                        }
                    }

                    createColumnsFromRowSchema(node.schema().schema().columns(), columns);
                    return new SemanticInfo(new TableSymbol(columns), diagnostics);
                }
                else
                {
                    return new SemanticInfo(getResultTypeOrError(node.functionCall()), diagnostics);
                }
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_columnListPool.returnToPool(columns);
            }
        }

        @Override
        public SemanticInfo visitGetSchemaOperator(GetSchemaOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                // PORT: §3.14 (string)node.KindParameter?.Expression?.LiteralValue == "csl"
                Object kindLiteral = node.kindParameter() != null && node.kindParameter().expression() != null ? node.kindParameter().expression().literalValue() : null;
                if ("csl".equals((String)kindLiteral))
                {
                    return s_GetSchemaAsCslInfo;
                }
                return s_GetSchemaInfo;
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        private static final TableSymbol s_GetSchemaSchema = new TableSymbol(
            new ColumnSymbol("ColumnName", ScalarTypes.String),
            new ColumnSymbol("ColumnOrdinal", ScalarTypes.Long),
            new ColumnSymbol("DataType", ScalarTypes.String),
            new ColumnSymbol("ColumnType", ScalarTypes.String));

        private static final SemanticInfo s_GetSchemaInfo = new SemanticInfo(s_GetSchemaSchema);

        private static final TableSymbol s_GetSchemaAsCslSchema = new TableSymbol(
            new ColumnSymbol("Schema", ScalarTypes.String));

        private static final SemanticInfo s_GetSchemaAsCslInfo = new SemanticInfo(s_GetSchemaAsCslSchema);


        @Override
        public SemanticInfo visitScanOperator(ScanOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            List<ColumnSymbol> columns = s_columnListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);
                _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.ScanParameters, diagnostics);

                // TODO: check other clauses here

                _binder.getDeclaredAndInferredColumns(rowScopeOrEmpty(), columns);

                if (node.declareClause() != null)
                {
                    for (var element : node.declareClause().declarations())
                    {
                        var decl = element.element();
                        columns.add(new ColumnSymbol(decl.nameAndType().name().simpleName(), getDeclaredType(decl.nameAndType().type()), null, null, decl.nameAndType().name())); // PORT: §3.12 named arg source
                    }
                }

                // PORT: §3.6 node.Parameters.FirstOrDefault(np => np.Name.SimpleName == QueryOperatorParameters.WithMatchId.Name)
                NamedParameter matchIdParam = null;
                for (NamedParameter np : node.parameters())
                {
                    if (Objects.equals(np.name().simpleName(), QueryOperatorParameters.WithMatchId.name()))
                    {
                        matchIdParam = np;
                        break;
                    }
                }
                String matchIdColumnName = (matchIdParam != null && matchIdParam.expression() instanceof NameDeclaration matchNd) ? matchNd.simpleName() : "match_id";
                columns.add(new ColumnSymbol(matchIdColumnName, ScalarTypes.Long));

                TableSymbol resultTable = new TableSymbol(columns)
                    .withInheritableProperties(rowScopeOrEmpty());

                return new SemanticInfo(resultTable, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_columnListPool.returnToPool(columns);
            }
        }

        @Override
        public SemanticInfo visitScanOrderByClause(ScanOrderByClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitScanPartitionByClause(ScanPartitionByClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitScanDeclareClause(ScanDeclareClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitScanAssignment(ScanAssignment node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitScanStep(ScanStep node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitScanStepOutput(ScanStepOutput node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitScanComputationClause(ScanComputationClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitMacroExpandScopeReferenceName(MacroExpandScopeReferenceName node)
        {
            // set symbol for scope reference declaration
            if (node.parent() instanceof MacroExpandOperator macroExpand
                && macroExpand.entityGroup() != null
                && macroExpand.entityGroup().resultType() instanceof EntityGroupSymbol entityGroup) // PORT: §3.14 ?.
            {
                EntityGroupElementSymbol scopeSymbol = new EntityGroupElementSymbol(
                    node.entityGroupReferenceName().simpleName(),
                    entityGroup);
                _binder.setSemanticInfo(node.entityGroupReferenceName(), new SemanticInfo(scopeSymbol, scopeSymbol));
            }

            return null;
        }

        @Override
        public SemanticInfo visitMacroExpandOperator(MacroExpandOperator node)
        {
            // handled in TreeBinder
            return null;
        }

        @Override
        public SemanticInfo visitMakeGraphOperator(MakeGraphOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            List<TableSymbol> nodesShape = null;
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                _binder.checkIsColumn(node.sourceColumn(), diagnostics);
                _binder.checkIsColumn(node.targetColumn(), diagnostics);

                if (!TypeFacts.isAnyScalarExceptDynamic(node.sourceColumn().resultType())) // PORT: §3.5 extension method
                {
                    diagnostics.add(DiagnosticFacts.getMakeGraphDynamicNodeIdColumnNotSupported().withLocation(node.sourceColumn()));
                }

                if (!TypeFacts.isAnyScalarExceptDynamic(node.targetColumn().resultType())) // PORT: §3.5 extension method
                {
                    diagnostics.add(DiagnosticFacts.getMakeGraphDynamicNodeIdColumnNotSupported().withLocation(node.targetColumn()));
                }

                if (node.withClause() instanceof MakeGraphWithTablesAndKeysClause tablesAndKeysClause)
                {
                    nodesShape = s_tableListPool.allocateFromPool();
                    for (int i = 0; i < tablesAndKeysClause.tablesAndKeys().size(); i++)
                    {
                        MakeGraphTableAndKeyClause tableAndKey = tablesAndKeysClause.tablesAndKeys().get(i).element();
                        _binder.checkIsTabular(tableAndKey.table(), diagnostics);
                        if (!TypeFacts.isAnyScalarExceptDynamic(tableAndKey.column().resultType())) // PORT: §3.5 extension method
                        {
                            diagnostics.add(DiagnosticFacts.getMakeGraphDynamicNodeIdColumnNotSupported().withLocation(tableAndKey.column()));
                        }

                        if (tableAndKey.table().resultType() instanceof TableSymbol table)
                        {
                            nodesShape.add(table);
                        }
                    }
                }

                else if (node.withClause() instanceof MakeGraphWithImplicitIdClause implicitIdClause)
                {
                    nodesShape = s_tableListPool.allocateFromPool();
                    if (DotNetStrings.isNullOrEmpty(implicitIdClause.name().simpleName()))
                    {
                        diagnostics.add(DiagnosticFacts.getMakeGraphImplicityIdShouldNotBeEmpty().withLocation(implicitIdClause.name()));
                    }
                    // Create a symbol representing a "table" with only the implicit node id.
                    TableSymbol table = new TableSymbol(new ColumnSymbol(implicitIdClause.name().simpleName(), node.sourceColumn().resultType()));
                    nodesShape.add(table);
                }

                // Note that we don't handled the PartitionedByClause here but rather in the dedicated Visit* method.

                GraphSymbol symbol = new GraphSymbol(this.rowScopeOrEmpty(), nodesShape);
                return new SemanticInfo(symbol, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                if (nodesShape != null)
                {
                    s_tableListPool.returnToPool(nodesShape);
                }
            }
        }

        @Override
        public SemanticInfo visitMakeGraphWithTablesAndKeysClause(MakeGraphWithTablesAndKeysClause node)
        {
            // handled by VisitMakeGraphOperator
            return null;
        }

        @Override
        public SemanticInfo visitMakeGraphWithImplicitIdClause(MakeGraphWithImplicitIdClause node)
        {
            // handled by VisitMakeGraphOperator
            return null;
        }

        @Override
        public SemanticInfo visitMakeGraphTableAndKeyClause(MakeGraphTableAndKeyClause node)
        {
            // handled by VisitMakeGraphOperator
            return null;
        }

        @Override
        public SemanticInfo visitMakeGraphPartitionedByClause(MakeGraphPartitionedByClause node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                _binder.checkIsColumn(node.entity(), diagnostics);
                checkQueryOperators(node.subquery(), KustoFacts.PostPipeOperatorKinds, diagnostics, true, true); // PORT: §3.12 named arg allowContextualRoot

                TableSymbol tableType = node.subquery().resultType() instanceof TableSymbol ts ? ts : null; // PORT: §3.15 as

                TableSymbol result = new TableSymbol(_binder.getDeclaredAndInferredColumns(tableType))
                                .withInheritableProperties(rowScopeOrEmpty())
                                .withIsSorted(false);

                return new SemanticInfo(result, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitGraphMatchOperator(GraphMatchOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            ProjectionBuilder builder = s_projectionBuilderPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                TypeSymbol symbol = null;

                GraphSymbol leftGraph = getGraphSymbol(node);
                if (leftGraph == null)
                {
                    diagnostics.add(DiagnosticFacts.getQueryOperatorExpectsGraph().withLocation(node.graphMatchKeyword()));
                }

                if (node.patterns() == null || node.patterns().size() == 0)
                {
                    diagnostics.add(DiagnosticFacts.getMissingGraphMatchPattern().withLocation(node.patterns()));
                }
                else
                {
                    for (var pattern : node.patterns())
                    {
                        checkGraphMatchPattern(pattern.element(), diagnostics);
                    }
                }

                if (node.whereClause() != null)
                {
                    _binder.checkIsExactType(node.whereClause().condition(), ScalarTypes.Bool, diagnostics);
                }

                if (node.projectClause() != null)
                {
                    // Getting all edges that are variable edges and has name
                    Set<String> variableEdges = new LinkedHashSet<>(); // PORT: §3.17 HashSet<string>
                    node.patterns().walkElements(element ->
                    {
                        if (element instanceof GraphMatchPatternEdge edge && edge.range() != null && edge.name() != null)
                        {
                            variableEdges.add(edge.name().simpleName());
                        }
                    });

                    for (var expr : node.projectClause().expressions())
                    {
                        TypeSymbol columnType = null;
                        Set<String> referencedElements = new LinkedHashSet<>(); // PORT: §3.17 HashSet<string>
                        expr.element().walkNodes(elementNode ->
                        {
                            if (elementNode instanceof NameReference nameRef)
                            {
                                referencedElements.add(nameRef.simpleName());
                            }
                        });
                        boolean anyVariableEdgeReferenced = false; // PORT: §3.6 variableEdges.Any(e => referencedElements.Contains(e))
                        for (String e : variableEdges)
                        {
                            if (referencedElements.contains(e))
                            {
                                anyVariableEdgeReferenced = true;
                                break;
                            }
                        }
                        if (anyVariableEdgeReferenced)
                        {
                            TypeSymbol colType = getResultTypeOrError(expr.element());
                            columnType = ScalarTypes.getDynamicArray(colType);
                        }

                        _binder.createProjectionColumns(expr.element(), builder, diagnostics, ProjectionStyle.GraphMatch, false, columnType); // PORT: §3.12 named arg columnType
                    }

                    symbol = new TableSymbol(builder.getProjection());
                }
                else
                {
                    symbol = leftGraph != null ? (TypeSymbol)leftGraph : ErrorSymbol.Instance; // PORT: §3.14 ??
                }

                return new SemanticInfo(symbol, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_projectionBuilderPool.returnToPool(builder);
            }
        }

        @Override
        public SemanticInfo visitGraphShortestPathsOperator(GraphShortestPathsOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            ProjectionBuilder builder = s_projectionBuilderPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                TypeSymbol symbol = null;

                GraphSymbol leftGraph = getGraphSymbol(node);
                if (leftGraph == null)
                {
                    diagnostics.add(DiagnosticFacts.getQueryOperatorExpectsGraph().withLocation(node.graphShortestPathsKeyword()));
                }

                if (node.patterns() == null || node.patterns().size() == 0)
                {
                    diagnostics.add(DiagnosticFacts.getMissingGraphMatchPattern().withLocation(node.patterns()));
                }
                else
                {
                    for (var pattern : node.patterns())
                    {
                        checkGraphMatchPattern(pattern.element(), diagnostics);
                    }
                }

                if (node.whereClause() != null)
                {
                    _binder.checkIsExactType(node.whereClause().condition(), ScalarTypes.Bool, diagnostics);
                }

                if (node.projectClause() != null)
                {
                    // Getting all edges that are variable edges and has name
                    Set<String> variableEdges = new LinkedHashSet<>(); // PORT: §3.17 HashSet<string>
                    node.patterns().walkElements(element ->
                    {
                        if (element instanceof GraphMatchPatternEdge edge && edge.range() != null && edge.name() != null)
                        {
                            variableEdges.add(edge.name().simpleName());
                        }
                    });

                    for (var expr : node.projectClause().expressions())
                    {
                        TypeSymbol columnType = null;
                        Set<String> referencedElements = new LinkedHashSet<>(); // PORT: §3.17 HashSet<string>
                        expr.element().walkNodes(elementNode ->
                        {
                            if (elementNode instanceof NameReference nameRef)
                            {
                                referencedElements.add(nameRef.simpleName());
                            }
                        });
                        boolean anyVariableEdgeReferenced = false; // PORT: §3.6 variableEdges.Any(e => referencedElements.Contains(e))
                        for (String e : variableEdges)
                        {
                            if (referencedElements.contains(e))
                            {
                                anyVariableEdgeReferenced = true;
                                break;
                            }
                        }
                        if (anyVariableEdgeReferenced)
                        {
                            TypeSymbol colType = getResultTypeOrError(expr.element());
                            columnType = ScalarTypes.getDynamicArray(colType);
                        }

                        _binder.createProjectionColumns(expr.element(), builder, diagnostics, ProjectionStyle.GraphMatch, false, columnType); // PORT: §3.12 named arg columnType
                    }

                    symbol = new TableSymbol(builder.getProjection());
                }
                else
                {
                    symbol = leftGraph != null ? (TypeSymbol)leftGraph : ErrorSymbol.Instance; // PORT: §3.14 ??
                }

                return new SemanticInfo(symbol, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
                s_projectionBuilderPool.returnToPool(builder);
            }
        }

        private void checkGraphMatchPattern(GraphMatchPattern node, List<Diagnostic> diagnostics)
        {
            if (node.patternElements() == null || node.patternElements().size() == 0)
            {
                diagnostics.add(DiagnosticFacts.getMissingGraphMatchPatternElement().withLocation(node));
                return;
            }

            for (int i = 0; i < node.patternElements().size(); i++)
            {
                var element = node.patternElements().get(i);
                boolean expectNode = i % 2 == 0;

                // Validate every element is the expected type
                if (expectNode && !(element instanceof GraphMatchPatternNode))
                {
                    diagnostics.add(DiagnosticFacts.getGraphMatchPatternSyntaxError("node", "edge").withLocation(element));
                }
                else if (!expectNode && !(element instanceof GraphMatchPatternEdge))
                {
                    diagnostics.add(DiagnosticFacts.getGraphMatchPatternSyntaxError("edge", "node").withLocation(element));
                }
            }

            // Validating pattern ends with node
            var lastElement = node.patternElements().get(node.patternElements().size() - 1);
            if (!(lastElement instanceof GraphMatchPatternNode))
            {
                diagnostics.add(DiagnosticFacts.getMissingGraphMatchPatternElement().withLocation(lastElement));
            }
        }

        @Override
        public SemanticInfo visitGraphMatchPattern(GraphMatchPattern node)
        {
            // handled by VisitGraphMatchOperator
            return null;
        }

        @Override
        public SemanticInfo visitGraphMatchPatternNode(GraphMatchPatternNode node)
        {
            // handled by VisitGraphMatchOperator
            return null;
        }

        @Override
        public SemanticInfo visitGraphMatchPatternEdge(GraphMatchPatternEdge node)
        {
            // handled by VisitGraphMatchOperator
            return null;
        }

        @Override
        public SemanticInfo visitGraphMatchPatternEdgeRange(GraphMatchPatternEdgeRange node)
        {
            // handled by VisitGraphMatchOperator
            return null;
        }

        @Override
        public SemanticInfo visitWhereClause(WhereClause node)
        {
            // handled by containing node
            return null;
        }

        @Override
        public SemanticInfo visitProjectClause(ProjectClause node)
        {
            // handled by containing node
            return null;
        }

        @Override
        public SemanticInfo visitGraphMarkComponentsOperator(GraphMarkComponentsOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                if (node.parameters() != null)
                {
                    _binder.checkQueryOperatorParameters(node.parameters(), QueryOperatorParameters.GraphMarkComponentsParameters, diagnostics);
                }

                // get existing graph symbol from left-side of parent pipe operator
                // PORT: §3.14 (node.Parent as PipeExpression)?.Expression?.ResultType is GraphSymbol g
                TypeSymbol parentLeftType = node.parent() instanceof PipeExpression pe && pe.expression() != null ? pe.expression().resultType() : null;
                GraphSymbol graphSymbol = parentLeftType instanceof GraphSymbol g
                    ? new GraphSymbol(g.edgeShape(), g.nodeShape())
                    : new GraphSymbol(this.rowScopeOrEmpty());

                // add component-id column to node shape
                String componentIdName = SyntaxExtensions.getParameterNameValue(node.parameters(), QueryOperatorParameters.WithComponentId); // PORT: §3.5
                if (componentIdName == null) // PORT: §3.14 ??
                {
                    componentIdName = "ComponentId";
                }
                ColumnSymbol componentIdColumn = new ColumnSymbol(componentIdName, ScalarTypes.Long);
                TableSymbol newNodeShape = graphSymbol.nodeShape() != null
                    ? graphSymbol.nodeShape().addColumns(componentIdColumn)
                    : new TableSymbol(componentIdColumn);
                graphSymbol = graphSymbol.withNodeShape(newNodeShape);

                return new SemanticInfo(graphSymbol, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitGraphWhereNodesOperator(GraphWhereNodesOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);
                _binder.checkIsExactType(node.condition(), ScalarTypes.Bool, diagnostics);

                // get existing graph symbol from left-side of parent pipe operator
                // PORT: §3.14 (node.Parent as PipeExpression)?.Expression?.ResultType is GraphSymbol g
                TypeSymbol parentLeftType = node.parent() instanceof PipeExpression pe && pe.expression() != null ? pe.expression().resultType() : null;
                GraphSymbol graphSymbol = parentLeftType instanceof GraphSymbol g
                    ? new GraphSymbol(g.edgeShape(), g.nodeShape())
                    : new GraphSymbol(this.rowScopeOrEmpty());

                return new SemanticInfo(graphSymbol, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitGraphWhereEdgesOperator(GraphWhereEdgesOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);
                _binder.checkIsExactType(node.condition(), ScalarTypes.Bool, diagnostics);

                // get existing graph symbol from left-side of parent pipe operator
                // PORT: §3.14 (node.Parent as PipeExpression)?.Expression?.ResultType is GraphSymbol g
                TypeSymbol parentLeftType = node.parent() instanceof PipeExpression pe && pe.expression() != null ? pe.expression().resultType() : null;
                GraphSymbol graphSymbol = parentLeftType instanceof GraphSymbol g
                    ? new GraphSymbol(g.edgeShape(), g.nodeShape())
                    : new GraphSymbol(this.rowScopeOrEmpty());

                return new SemanticInfo(graphSymbol, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitGraphToTableOperator(GraphToTableOperator node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                checkNotFirstInPipe(node, diagnostics);

                TypeSymbol symbol = null;

                GraphSymbol leftGraph = getGraphSymbol(node);
                if (leftGraph == null)
                {
                    diagnostics.add(DiagnosticFacts.getQueryOperatorExpectsGraph().withLocation(node.graphToTableKeyword()));
                }
                else if (node.outputClause().size() == 1)
                {
                    GraphToTableOutputClause clause = node.outputClause().get(0).element();
                    if (clause.entityKeyword().kind() == SyntaxKind.GraphEdgesKeyword)
                    {
                        symbol = visitGraphToTableEdgesClause(clause, leftGraph);
                    }
                    else if (clause.entityKeyword().kind() == SyntaxKind.NodesKeyword)
                    {
                        symbol = visitGraphToTableNodesClause(clause, leftGraph);
                    }
                }
                else if (node.outputClause().size() == 2)
                {
                    // PORT: §3.6 node.OutputClause.FirstOrDefault(oc => oc.Element.EntityKeyword.Kind == SyntaxKind.NodesKeyword)?.Element
                    GraphToTableOutputClause nodesTableClause = null;
                    for (var oc : node.outputClause())
                    {
                        if (oc.element().entityKeyword().kind() == SyntaxKind.NodesKeyword)
                        {
                            nodesTableClause = oc.element();
                            break;
                        }
                    }
                    // PORT: §3.6 node.OutputClause.FirstOrDefault(oc => oc.Element.EntityKeyword.Kind == SyntaxKind.GraphEdgesKeyword)?.Element
                    GraphToTableOutputClause edgesTableClause = null;
                    for (var oc : node.outputClause())
                    {
                        if (oc.element().entityKeyword().kind() == SyntaxKind.GraphEdgesKeyword)
                        {
                            edgesTableClause = oc.element();
                            break;
                        }
                    }

                    if (nodesTableClause == null || edgesTableClause == null)
                    {
                        diagnostics.add(DiagnosticFacts.getMissingGraphEntityType().withLocation(node.outputClause()));
                        return new SemanticInfo(diagnostics);
                    }

                    if (nodesTableClause.asClause() == null)
                        diagnostics.add(DiagnosticFacts.getMultipleGraphOutputsRequireAliases().withLocation(nodesTableClause));

                    if (edgesTableClause.asClause() == null)
                        diagnostics.add(DiagnosticFacts.getMultipleGraphOutputsRequireAliases().withLocation(edgesTableClause));

                    TableSymbol nodesTable = visitGraphToTableNodesClause(nodesTableClause, leftGraph);
                    TableSymbol edgesTable = visitGraphToTableEdgesClause(edgesTableClause, leftGraph);
                    symbol = new GroupSymbol(nodesTable, edgesTable);
                }
                else
                {
                    diagnostics.add(DiagnosticFacts.getIncorrectNumberOfOutputGraphToTableEntities().withLocation(node.outputClause()));
                }

                return new SemanticInfo(symbol, diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        private TableSymbol visitGraphToTableEdgesClause(GraphToTableOutputClause node, GraphSymbol graph)
        {
            TableSymbol edges = graph != null && graph.edgeShape() != null ? graph.edgeShape() : this.rowScopeOrEmpty(); // PORT: §3.14 ?. and ??
            String name = node.asClause() != null && node.asClause().name() != null ? node.asClause().name().simpleName() : null; // PORT: §3.14 ?.
            List<ColumnSymbol> columns = new ArrayList<>(edges.columns().size());
            addGraphToTableHashColumn(node, columns, QueryOperatorParameters.WithSourceId);
            addGraphToTableHashColumn(node, columns, QueryOperatorParameters.WithTargetId);
            columns.addAll(edges.columns());

            return new TableSymbol(name, columns);
        }

        private TableSymbol visitGraphToTableNodesClause(GraphToTableOutputClause node, GraphSymbol graph)
        {
            TableSymbol nodes = graph != null && graph.nodeShape() != null ? graph.nodeShape() : this.rowScopeOrEmpty(); // PORT: §3.14 ?. and ??
            String name = node.asClause() != null && node.asClause().name() != null ? node.asClause().name().simpleName() : null; // PORT: §3.14 ?.
            List<ColumnSymbol> columns = new ArrayList<>(nodes.columns().size());
            addGraphToTableHashColumn(node, columns, QueryOperatorParameters.WithNodeId);
            columns.addAll(nodes.columns());

            return new TableSymbol(name, columns);
        }

        private void addGraphToTableHashColumn(GraphToTableOutputClause node, List<ColumnSymbol> columns, QueryOperatorParameter parameter)
        {
            String hashColumn = SyntaxExtensions.getParameterNameValue(node.parameters(), parameter); // PORT: §3.5
            if (!DotNetStrings.isNullOrEmpty(hashColumn))
            {
                columns.add(new ColumnSymbol(hashColumn, ScalarTypes.Long));
            }
        }

        @Override
        public SemanticInfo visitGraphToTableOutputClause(GraphToTableOutputClause node)
        {
            // handled by containing node
            return null;
        }

        @Override
        public SemanticInfo visitGraphToTableAsClause(GraphToTableAsClause node)
        {
            // handled by containing node
            return null;
        }
        // endregion

        // region clauses
        // Clauses don't have semantics on their own but may influence their parent node's semantics
        // typically handled by the parent node's visit method.

        @Override
        public SemanticInfo visitCountAsIdentifierClause(CountAsIdentifierClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitDataScopeClause(DataScopeClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitDefaultExpressionClause(DefaultExpressionClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitExternalDataWithClause(ExternalDataWithClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitInlineExternalTableKindClause(InlineExternalTableKindClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitInlineExternalTablePathFormatPartitionColumnReference(InlineExternalTablePathFormatPartitionColumnReference node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitInlineExternalTableDataFormatClause(InlineExternalTableDataFormatClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitInlineExternalTablePathFormatClause(InlineExternalTablePathFormatClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitPartitionColumnDeclaration(PartitionColumnDeclaration node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitInlineExternalTablePartitionClause(InlineExternalTablePartitionClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitInlineExternalTableConnectionStringsClause(InlineExternalTableConnectionStringsClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitFacetWithOperatorClause(FacetWithOperatorClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitFacetWithExpressionClause(FacetWithExpressionClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitFindInClause(FindInClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitFindProjectClause(FindProjectClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitJoinOnClause(JoinOnClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitJoinWhereClause(JoinWhereClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitMakeSeriesByClause(MakeSeriesByClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitMakeSeriesInRangeClause(MakeSeriesInRangeClause node)
        {
            return null;
        }
        @Override
        public SemanticInfo visitMakeSeriesFromClause(MakeSeriesFromClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitMakeSeriesToClause(MakeSeriesToClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitMakeSeriesStepClause(MakeSeriesStepClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitMakeSeriesFromToStepClause(MakeSeriesFromToStepClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitMakeSeriesOnClause(MakeSeriesOnClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitMvExpandRowLimitClause(MvExpandRowLimitClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitMvApplyRowLimitClause(MvApplyRowLimitClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitMvApplyContextIdClause(MvApplyContextIdClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitNameEqualsClause(NameEqualsClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitOrderingClause(OrderingClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitOrderingNullsClause(OrderingNullsClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitReduceByWithClause(ReduceByWithClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitRenderWithClause(RenderWithClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitSummarizeByClause(SummarizeByClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitTopHittersByClause(TopHittersByClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitTopNestedClause(TopNestedClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitTopNestedWithOthersClause(TopNestedWithOthersClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitToTypeOfClause(ToTypeOfClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitEvaluateSchemaClause(EvaluateSchemaClause node)
        {
            return null;
        }
        // endregion

        // region statements
        @Override
        public SemanticInfo visitAliasStatement(AliasStatement node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                if (!getResultTypeOrError(node.expression()).isError())
                {
                    _binder.checkIsDatabase(node.expression(), diagnostics);
                }
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }

            return null;
        }

        @Override
        public SemanticInfo visitExpressionStatement(ExpressionStatement node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitLetStatement(LetStatement node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                String name = node.name().name().simpleName();

                if (_binder._localScope.hasSymbol(name))
                {
                    diagnostics.add(DiagnosticFacts.getVariableAlreadyDeclared(name).withLocation(node.name()));
                }

                if (getResultType(node.expression()) instanceof TupleSymbol)
                {
                    diagnostics.add(DiagnosticFacts.getMultiValuedExpressionCannotBeAssignedToVariable().withLocation(node.expression()));
                }

                if (diagnostics.size() > 0)
                {
                    return new SemanticInfo(diagnostics);
                }

                return null;
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitQueryParametersStatement(QueryParametersStatement node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitRestrictStatement(RestrictStatement node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitSetOptionStatement(SetOptionStatement node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                _binder.checkIsIdentifierNameDeclaration(node.name(), diagnostics);

                OptionSymbol option = _binder._globals.getOption(node.name().simpleName());
                if (option != null)
                {
                    _binder.setSemanticInfo(node.name(), new SemanticInfo(option, (TypeSymbol)null));
                }

                if (node.valueClause() != null)
                {
                    if (_binder.checkIsLiteralOrName(node.valueClause().expression(), diagnostics))
                    {
                        if (option != null && option.types().size() > 0)
                        {
                            _binder.checkIsAnyType(node.valueClause().expression(), option.types(), Conversion.Compatible, diagnostics);
                        }
                    }
                }

                if (diagnostics.size() > 0)
                {
                    return new SemanticInfo(diagnostics);
                }
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }

            return null;
        }

        @Override
        public SemanticInfo visitOptionValueClause(OptionValueClause node)
        {
            // handled by VisitSetOptionStatement
            return null;
        }
        // endregion

        // region commands
        @Override
        public SemanticInfo visitCommandWithValueClause(CommandWithValueClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitCommandWithPropertyListClause(CommandWithPropertyListClause node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitBadCommand(BadCommand node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitCommandAndSkippedTokens(CommandAndSkippedTokens node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitCommandBlock(CommandBlock node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitCustomCommand(CustomCommand node)
        {
            CommandSymbol commandSymbol = _binder._globals.getCommand(node.commandKind());
            if (commandSymbol != null)
            {
                return new SemanticInfo(commandSymbol, commandSymbol.resultType());
            }

            return null;
        }

        @Override
        public SemanticInfo visitPartialCommand(PartialCommand node)
        {
            return null;
        }

        @Override
        public SemanticInfo visitUnknownCommand(UnknownCommand node)
        {
            return null;
        }
        // endregion

        // region Directives
        @Override
        public SemanticInfo visitDirectiveBlock(DirectiveBlock node)
        {
            // no longer used
            return null;
        }

        @Override
        public SemanticInfo visitDirective(Directive node)
        {
            List<Diagnostic> diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                _binder.applyDirective(node, diagnostics);
                return new SemanticInfo(diagnostics);
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public SemanticInfo visitRestrictStatementWithClause(RestrictStatementWithClause node)
        {
            return null;
        }
        // endregion
    }

    // ===== upstream part: Binder_Operators.cs =====
    private SemanticInfo getBinaryOperatorInfo(OperatorKind kind, Expression left, Expression right, SyntaxElement location)
    {
        return getBinaryOperatorInfo(kind, left, getResultTypeOrError(left), right, getResultTypeOrError(right), location);
    }

    private SemanticInfo getBinaryOperatorInfo(OperatorKind kind, Expression left, TypeSymbol leftType, Expression right, TypeSymbol rightType, SyntaxElement location)
    {
        var arguments = s_expressionListPool.allocateFromPool();
        var argumentTypes = s_typeListPool.allocateFromPool();

        try
        {
            arguments.add(left);
            arguments.add(right);

            argumentTypes.add(leftType);
            argumentTypes.add(rightType);

            return getOperatorInfo(kind, arguments, argumentTypes, location, true); // PORT: §3.12 named argument requireAllArgumentsMatch
        }
        finally
        {
            s_expressionListPool.returnToPool(arguments);
            s_typeListPool.returnToPool(argumentTypes);
        }
    }

    private SemanticInfo getUnaryOperatorInfo(OperatorKind kind, Expression operand, SyntaxElement location)
    {
        var arguments = s_expressionListPool.allocateFromPool();

        try
        {
            arguments.add(operand);

            return getOperatorInfo(kind, arguments, location, true); // PORT: §3.12 named argument requireAllArgumentsMatch
        }
        finally
        {
            s_expressionListPool.returnToPool(arguments);
        }
    }

    private SemanticInfo getOperatorInfo(
        OperatorKind kind, 
        List<Expression> arguments, // PORT: §3.17 IReadOnlyList<T> → List<T>
        SyntaxElement location,
        boolean requireAllArgumentsMatch)
    {
        var argumentTypes = s_typeListPool.allocateFromPool();

        try
        {
            for (int i = 0; i < arguments.size(); i++)
            {
                argumentTypes.add(getResultTypeOrError(arguments.get(i)));
            }

            return getOperatorInfo(kind, arguments, argumentTypes, location, requireAllArgumentsMatch);
        }
        finally
        {
            s_typeListPool.returnToPool(argumentTypes);
        }
    }

    private SemanticInfo getOperatorInfo(OperatorKind kind, List<Expression> arguments, SyntaxElement location) // PORT: §3.12 requireAllArgumentsMatch = false
    {
        return getOperatorInfo(kind, arguments, location, false);
    }

    private SemanticInfo getOperatorInfo(
        OperatorKind kind, 
        List<Expression> arguments, // PORT: §3.17 IReadOnlyList<T> → List<T>
        List<TypeSymbol> argumentTypes, // PORT: §3.17 IReadOnlyList<T> → List<T>
        SyntaxElement location,
        boolean requireAllArgumentsMatch)
    {
        var matchingSignatures = s_signatureListPool.allocateFromPool();
        var diagnostics = s_diagnosticListPool.allocateFromPool();

        try
        {
            var op = _globals.getOperator(kind);

            getBestMatchingSignatures(op.signatures(), arguments, argumentTypes, matchingSignatures, requireAllArgumentsMatch);

            if (matchingSignatures.size() == 1)
            {
                checkSignature(matchingSignatures.get(0), arguments, argumentTypes, location, diagnostics);
                var funResult = getFunctionCallResult(matchingSignatures.get(0), arguments, argumentTypes, location);
                var resultType = funResult.type();

                // check for possible better dynamic result
                if (funResult.type() == ScalarTypes.Dynamic
                    && hasDynamicPrimitives(argumentTypes))
                {
                    var unwrappedArgumentTypes = s_typeListPool.allocateFromPool();
                    try
                    {
                        getUnwrappedDynamicPrimitives(argumentTypes, unwrappedArgumentTypes);
                        var unwrappedResultType = getOperatorInfo(kind, arguments, unwrappedArgumentTypes, location, requireAllArgumentsMatch).resultType();
                        if (unwrappedResultType instanceof ScalarSymbol
                            && !(unwrappedResultType instanceof DynamicSymbol)
                            && unwrappedResultType != ScalarTypes.Unknown)
                        {
                            resultType = ScalarTypes.getDynamic(unwrappedResultType);
                        }
                    }
                    finally
                    {
                        s_typeListPool.returnToPool(unwrappedArgumentTypes);
                    }
                }

                return new SemanticInfo(matchingSignatures.get(0), resultType, diagnostics, allAreConstant(arguments)); // PORT: §3.12 named argument isConstant
            }
            else
            {
                if (matchingSignatures.size() == 0 && requireAllArgumentsMatch)
                {
                    // try again to get better return type
                    getBestMatchingSignatures(op.signatures(), arguments, argumentTypes, matchingSignatures, false); // PORT: §3.12 named argument requireAllArgumentsMatch
                }

                if (!argumentsHaveErrorsOrUnknown(argumentTypes))
                {
                    diagnostics.add(DiagnosticFacts.getOperatorNotDefined(location.toString(IncludeTrivia.Interior), argumentTypes).withLocation(location));
                }

                var returnType = getCommonReturnType(matchingSignatures, arguments, argumentTypes, location);
                return new SemanticInfo(op, returnType, diagnostics);
            }
        }
        finally
        {
            s_signatureListPool.returnToPool(matchingSignatures);
            s_diagnosticListPool.returnToPool(diagnostics);
        }
    }


    private static boolean allAreConstant(List<Expression> expressions) // PORT: §3.17 IReadOnlyList<T> → List<T>
    {
        for (int i = 0; i < expressions.size(); i++)
        {
            if (!getIsConstant(expressions.get(i)))
                return false;
        }

        return true;
    }

    private static OperatorKind getOperatorKind(SyntaxKind kind)
    {
        switch (kind)
        {
            case AddExpression:
                return OperatorKind.Add;
            case SubtractExpression:
                return OperatorKind.Subtract;
            case MultiplyExpression:
                return OperatorKind.Multiply;
            case DivideExpression:
                return OperatorKind.Divide;
            case ModuloExpression:
                return OperatorKind.Modulo;
            case UnaryMinusExpression:
                return OperatorKind.UnaryMinus;
            case UnaryPlusExpression:
                return OperatorKind.UnaryPlus;
            case EqualExpression:
                return OperatorKind.Equal;
            case NotEqualExpression:
                return OperatorKind.NotEqual;
            case LessThanExpression:
                return OperatorKind.LessThan;
            case LessThanOrEqualExpression:
                return OperatorKind.LessThanOrEqual;
            case GreaterThanExpression:
                return OperatorKind.GreaterThan;
            case GreaterThanOrEqualExpression:
                return OperatorKind.GreaterThanOrEqual;
            case EqualTildeExpression:
                return OperatorKind.EqualTilde;
            case BangTildeExpression:
                return OperatorKind.BangTilde;
            case HasExpression:
                return OperatorKind.Has;
            case HasCsExpression:
                return OperatorKind.HasCs;
            case NotHasExpression:
                return OperatorKind.NotHas;
            case NotHasCsExpression:
                return OperatorKind.NotHasCs;
            case HasPrefixExpression:
                return OperatorKind.HasPrefix;
            case HasPrefixCsExpression:
                return OperatorKind.HasPrefixCs;
            case NotHasPrefixExpression:
                return OperatorKind.NotHasPrefix;
            case NotHasPrefixCsExpression:
                return OperatorKind.NotHasPrefixCs;
            case HasSuffixExpression:
                return OperatorKind.HasSuffix;
            case HasSuffixCsExpression:
                return OperatorKind.HasSuffixCs;
            case NotHasSuffixExpression:
                return OperatorKind.NotHasSuffix;
            case NotHasSuffixCsExpression:
                return OperatorKind.NotHasSuffixCs;
            case LikeExpression:
                return OperatorKind.Like;
            case LikeCsExpression:
                return OperatorKind.LikeCs;
            case NotLikeExpression:
                return OperatorKind.NotLike;
            case NotLikeCsExpression:
                return OperatorKind.NotLikeCs;
            case ContainsExpression:
                return OperatorKind.Contains;
            case ContainsCsExpression:
                return OperatorKind.ContainsCs;
            case NotContainsExpression:
                return OperatorKind.NotContains;
            case NotContainsCsExpression:
                return OperatorKind.NotContainsCs;
            case StartsWithExpression:
                return OperatorKind.StartsWith;
            case StartsWithCsExpression:
                return OperatorKind.StartsWithCs;
            case NotStartsWithExpression:
                return OperatorKind.NotStartsWith;
            case NotStartsWithCsExpression:
                return OperatorKind.NotStartsWithCs;
            case EndsWithExpression:
                return OperatorKind.EndsWith;
            case EndsWithCsExpression:
                return OperatorKind.EndsWithCs;
            case NotEndsWithExpression:
                return OperatorKind.NotEndsWith;
            case NotEndsWithCsExpression:
                return OperatorKind.NotEndsWithCs;
            case MatchesRegexExpression:
                return OperatorKind.MatchRegex;
            case InExpression:
                return OperatorKind.In;
            case InCsExpression:
                return OperatorKind.InCs;
            case NotInExpression:
                return OperatorKind.NotIn;
            case NotInCsExpression:
                return OperatorKind.NotInCs;
            case BetweenExpression:
                return OperatorKind.Between;
            case NotBetweenExpression:
                return OperatorKind.NotBetween;
            case AndExpression:
                return OperatorKind.And;
            case OrExpression:
                return OperatorKind.Or;
            case SearchExpression:
                return OperatorKind.Search;
            case HasAnyExpression:
                return OperatorKind.HasAny;
            case HasAllExpression:
                return OperatorKind.HasAll;
            default:
                return OperatorKind.None;
        }
    }

    /// <summary>
    /// Returns the result type for the binary operator given the two argument types.
    /// </summary>
    private TypeSymbol getBinaryOperatorResultType(OperatorKind kind, TypeSymbol leftType, TypeSymbol rightType, SyntaxElement location, List<Diagnostic> diagnostics)
    {
        var fakeLeftArg = FakeExpression.create(leftType);
        var fakeRightArg = FakeExpression.create(rightType);
        return getBinaryOperatorResultType(kind, fakeLeftArg, fakeRightArg, location != null ? location : fakeLeftArg, diagnostics); // PORT: §3.14 ??
    }

    private TypeSymbol getBinaryOperatorResultType(OperatorKind kind, TypeSymbol leftType, TypeSymbol rightType, SyntaxElement location) // PORT: §3.12 diagnostics = null
    {
        return getBinaryOperatorResultType(kind, leftType, rightType, location, null);
    }

    /// <summary>
    /// Returns the result type for the binary operator given the two argument expressions.
    /// </summary>
    private TypeSymbol getBinaryOperatorResultType(OperatorKind kind, Expression left, Expression right, SyntaxElement location, List<Diagnostic> diagnostics)
    {
        var info = getBinaryOperatorInfo(kind, left, getResultTypeOrError(left), right, getResultTypeOrError(right), location != null ? location : left); // PORT: §3.14 ??
        if (diagnostics != null)
            diagnostics.addAll(info.diagnostics());
        return info.resultType();
    }

    private TypeSymbol getBinaryOperatorResultType(OperatorKind kind, Expression left, Expression right, SyntaxElement location) // PORT: §3.12 diagnostics = null
    {
        return getBinaryOperatorResultType(kind, left, right, location, null);
    }

    // ===== upstream part: Binder_Projection.cs =====
    private enum ProjectionStyle
    {
        Default,
        Extend,
        Print,
        Rename,
        Replace,
        Reorder,
        Summarize,
        GraphMatch,
        ByNames,
    }

    /// <summary>
    /// Creates projection columns for all the expressions.
    /// </summary>
    private void createProjectionColumns(
        SyntaxList1<SeparatedElement1<Expression>> expressions,
        ProjectionBuilder builder,
        List<Diagnostic> diagnostics,
        ProjectionStyle style,
        boolean doNotRepeat)
    {
        for (var elem : expressions)
        {
            createProjectionColumns(
                elem.element(),
                builder,
                diagnostics,
                style, // PORT: §3.12 named argument style
                doNotRepeat); // PORT: §3.12 named argument doNotRepeat
        }
    }

    private void createProjectionColumns(SyntaxList1<SeparatedElement1<Expression>> expressions, ProjectionBuilder builder, List<Diagnostic> diagnostics, ProjectionStyle style) // PORT: §3.12 doNotRepeat = false
    {
        createProjectionColumns(expressions, builder, diagnostics, style, false);
    }

    private void createProjectionColumns(SyntaxList1<SeparatedElement1<Expression>> expressions, ProjectionBuilder builder, List<Diagnostic> diagnostics) // PORT: §3.12 style = ProjectionStyle.Default
    {
        createProjectionColumns(expressions, builder, diagnostics, ProjectionStyle.Default, false);
    }

    /// <summary>
    /// Creates projection columns for the expression.
    /// </summary>
    private void createProjectionColumns(
        Expression expression,
        ProjectionBuilder builder,
        List<Diagnostic> diagnostics,
        ProjectionStyle style,
        boolean doNotRepeat,
        TypeSymbol columnType,
        String columnName)
    {
        ColumnSymbol col;
        TypeSymbol type;

        // look through ordered expressions to find column references
        var oe = expression instanceof OrderedExpression oex ? oex : null; // PORT: §3.15 as
        if (oe != null)
        {
            expression = oe.expression();
        }

        // this is poorly formed syntax?
        if (expression == null)
            return;

        if (style == ProjectionStyle.Rename)
        {
            switch (expression)
            {
                case SimpleNamedExpression n:
                    if (getReferencedSymbol(n.expression()) instanceof ColumnSymbol cs)
                    {
                        col = builder.rename(cs.name(), n.name().simpleName(), diagnostics, n.name());
                        if (col != null)
                        {
                            setSemanticInfo(n.name(), createSemanticInfo(col));
                        }
                    }
                    else
                    {
                        diagnostics.add(DiagnosticFacts.getColumnExpected().withLocation(n.expression()));
                    }
                    break;

                default:
                    diagnostics.add(DiagnosticFacts.getRenameAssignmentExpected().withLocation(expression));
                    break;
            }
        }
        else if (style == ProjectionStyle.ByNames
            && _rowScope != null)
        {
            var namesOrPatterns = s_stringListPool.allocateFromPool();
            var matchingColumns = s_columnListPool.allocateFromPool();
            try
            {
                getProjectByNamesNames(expression, namesOrPatterns);
                for (String nameOrPattern : namesOrPatterns)
                {
                    matchingColumns.clear();
                    _rowScope.getMatchingColumns(nameOrPattern, matchingColumns);
                    builder.addRange(matchingColumns, false, true); // PORT: §3.12 named argument doNotRepeat
                }
            }
            finally
            {
                s_stringListPool.returnToPool(namesOrPatterns);
                s_columnListPool.returnToPool(matchingColumns);
            }
        }
        else
        {
            switch (expression) // PORT: §3.15 pattern switch
            {
                case SimpleNamedExpression n:
                    {
                        // single name assigned from multi-value tuple just assigns the first value. equivalant to (name) = tuple
                        if (n.expression().rawResultType() instanceof TupleSymbol tu)
                        {
                            if (tu.columns().size() > 0)
                            {
                                // first column has declared name so it uses declared name add/replace rule
                                var firstCol = tu.columns().get(0);
                                col = new ColumnSymbol(n.name().simpleName(), columnType != null ? columnType : firstCol.type(), null, Arrays.asList(firstCol), firstCol.source()); // PORT: §3.12 named arguments originalColumns, source; §3.14 ??
                                builder.declare(col, diagnostics, n.name(), true); // PORT: §3.12 named argument replace
                                setSemanticInfo(n.name(), createSemanticInfo(col));

                                if (doNotRepeat)
                                {
                                    builder.doNotAdd(tu.columns().get(0));
                                }

                                // don't add unnamed tuple columns if print style
                                if (style == ProjectionStyle.Print)
                                    break;

                                // all other columns are not declared, so they must be unique
                                for (int i = 1; i < tu.members().size(); i++)
                                {
                                    if (getReferencedSymbol(n.expression()) instanceof FunctionSymbol fs1)
                                    {
                                        addFunctionTupleResultColumn(fs1, tu.columns().get(i), builder, doNotRepeat, style == ProjectionStyle.Summarize);
                                    }
                                    else
                                    {
                                        builder.add(tu.columns().get(i), null, false, doNotRepeat); // PORT: §3.12 named argument doNotRepeat
                                    }
                                }
                            }
                        }
                        else if (n.expression().referencedSymbol() instanceof ColumnSymbol c)
                        {
                            col = new ColumnSymbol(n.name().simpleName(), columnType != null ? columnType : c.type(), null, null, n.expression()); // PORT: §3.12 named argument source; §3.14 ??
                            builder.declare(col, diagnostics, n.name(), true); // PORT: §3.12 named argument replace
                            setSemanticInfo(n.name(), createSemanticInfo(col));

                            if (doNotRepeat)
                            {
                                builder.doNotAdd(c);
                            }
                        }
                        else
                        {
                            col = new ColumnSymbol(n.name().simpleName(), columnType != null ? columnType : getResultTypeOrError(n.expression()), null, null, n.expression()); // PORT: §3.12 named argument source; §3.14 ??
                            builder.declare(col, diagnostics, n.name(), style == ProjectionStyle.Replace || style == ProjectionStyle.Extend); // PORT: §3.12 named argument replace
                            setSemanticInfo(n.name(), createSemanticInfo(col));
                        }
                    }
                    break;

                case CompoundNamedExpression cn:
                    {
                        if (cn.expression().rawResultType() instanceof TupleSymbol tupleType)
                        {
                            for (int i = 0; i < tupleType.columns().size(); i++)
                            {
                                col = tupleType.columns().get(i);
                                type = columnType != null ? columnType : col.type(); // PORT: §3.14 ??

                                // if element has name declaration then use name declaration rule
                                if (i < cn.names().names().size())
                                {
                                    var nameDecl = cn.names().names().get(i).element();
                                    var name = nameDecl.simpleName();
                                    col = new ColumnSymbol(name, type, null, col.originalColumns(), col.source()); // PORT: §3.12 named arguments originalColumns, source

                                    builder.declare(col, diagnostics, nameDecl, style == ProjectionStyle.Replace || style == ProjectionStyle.Extend); // PORT: §3.12 named argument replace
                                    setSemanticInfo(nameDecl, createSemanticInfo(col));

                                    if (doNotRepeat)
                                    {
                                        builder.doNotAdd(tupleType.columns().get(i));
                                    }
                                }
                                else if (style != ProjectionStyle.Print)
                                {
                                    if (cn.expression().referencedSymbol() instanceof FunctionSymbol fs1)
                                    {
                                        addFunctionTupleResultColumn(fs1, col, builder, doNotRepeat, style == ProjectionStyle.Summarize);
                                    }
                                    else
                                    {
                                        // not-declared so make unique column
                                        builder.add(col, null, style == ProjectionStyle.Replace || style == ProjectionStyle.Extend, doNotRepeat); // PORT: §3.12 named arguments replace, doNotRepeat
                                    }
                                }
                            }

                            // any additional names without matching tuple members gets a diagnostic
                            for (int i = tupleType.members().size(); i < cn.names().names().size(); i++)
                            {
                                var nameDecl = cn.names().names().get(i);
                                diagnostics.add(DiagnosticFacts.getTheNameDoesNotHaveCorrespondingExpression().withLocation(nameDecl));
                            }
                        }
                        else if (cn.names().names().size() == 1)
                        {
                            var expr = cn.expression();
                            var name = cn.names().names().get(0).element();
                            if (expr.referencedSymbol() instanceof ColumnSymbol c)
                            {
                                col = new ColumnSymbol(name.simpleName(), columnType != null ? columnType : c.type(), null, Arrays.asList(c)); // PORT: §3.12 named argument originalColumns; §3.14 ??
                                builder.declare(col, diagnostics, name, true); // PORT: §3.12 named argument replace
                                setSemanticInfo(name, createSemanticInfo(col));

                                if (doNotRepeat)
                                {
                                    builder.doNotAdd(c);
                                }
                            }
                            else
                            {
                                col = new ColumnSymbol(name.simpleName(), columnType != null ? columnType : getResultTypeOrError(cn.expression()), null, null, expr); // PORT: §3.12 named argument source; §3.14 ??
                                builder.declare(col, diagnostics, name, style == ProjectionStyle.Replace || style == ProjectionStyle.Extend); // PORT: §3.12 named argument replace
                                setSemanticInfo(name, createSemanticInfo(col));
                            }
                        }
                        else
                        {
                            diagnostics.add(DiagnosticFacts.getTheExpressionDoesNotHaveMultipleValues().withLocation(cn.names()));
                        }
                    }
                    break;

                case FunctionCallExpression f:
                    // check for trivial case of no-op conversion operator
                    col = getResultColumn(f);
                    if (col != null)
                    {
                        // if the expression is a column reference, then consider it a declaration
                        builder.declare(col.withType(columnType != null ? columnType : col.type()), diagnostics, expression, style == ProjectionStyle.Replace); // PORT: §3.12 named argument replace; §3.14 ??

                        if (doNotRepeat)
                        {
                            builder.doNotAdd(col);
                        }
                    }
                    else
                    {
                        TypeSymbol ftype = f.rawResultType() != null ? f.rawResultType() : ErrorSymbol.Instance; // PORT: §3.14 ??
                        var ts = ftype instanceof TupleSymbol tsx ? tsx : null; // PORT: §3.15 as

                        if (style == ProjectionStyle.Print
                            && columnName != null
                            && (ts == null || ts.columns().size() == 1))
                        {
                            if (ts != null && ts.columns().size() == 1)
                                ftype = ts.columns().get(0).type();

                            col = new ColumnSymbol(columnName, columnType != null ? columnType : ftype, null, null, f); // PORT: §3.12 named argument source; §3.14 ??
                            builder.add(col, columnName, false); // PORT: §3.12 named argument replace
                        }
                        else if (ts != null && getReferencedSymbol(f) instanceof FunctionSymbol fs)
                        {
                            for (Symbol member : ts.members()) // PORT: §3.17 foreach (ColumnSymbol c in ...) explicit element cast
                            {
                                ColumnSymbol c = (ColumnSymbol)member;
                                addFunctionTupleResultColumn(fs, c, builder, doNotRepeat, style == ProjectionStyle.Summarize);
                            }
                        }
                        else
                        {
                            var name = getFunctionResultName(f, null, _rowScope);
                            if (name == null) // PORT: §3.14 ??
                                name = columnName;
                            if (name == null) // PORT: §3.14 ??
                                name = getDefaultColumnName(expression, style == ProjectionStyle.Extend);
                            col = new ColumnSymbol(name, columnType != null ? columnType : ftype, null, null, f); // PORT: §3.12 named argument source; §3.14 ??
                            builder.add(col, null, style == ProjectionStyle.Replace || style == ProjectionStyle.Extend); // PORT: §3.12 named argument replace
                        }
                    }
                    break;

                case StarExpression s:
                    for (ColumnSymbol c : getDeclaredAndInferredColumns(rowScopeOrEmpty()))
                    {
                        builder.add(c, null, true, doNotRepeat); // PORT: §3.12 named arguments replace, doNotRepeat
                    }
                    break;

                default:
                    var rs = getReferencedSymbol(expression);
                    col = getResultColumn(expression);
                    if (col != null && style != ProjectionStyle.GraphMatch)
                    {
                        // if the expression is a column reference, then consider it a declaration
                        builder.declare(col.withType(columnType != null ? columnType : col.type()), diagnostics, expression, style == ProjectionStyle.Replace); // PORT: §3.12 named argument replace; §3.14 ??

                        if (doNotRepeat)
                        {
                            builder.doNotAdd(col);
                        }
                    }
                    else if (rs instanceof GroupSymbol group && style == ProjectionStyle.Reorder)
                    {
                        addProjectionColumns(builder, orderColumns(Linq.ofType(group.members(), ColumnSymbol.class), oe != null ? oe.ordering() : null), true); // PORT: §3.6 OfType; §3.14 ?.; §3.12 named argument doNotRepeat
                    }
                    else if (getResultType(expression) instanceof GroupSymbol g)
                    {
                        diagnostics.add(DiagnosticFacts.getTheExpressionRefersToMoreThanOneColumn().withLocation(expression));
                    }
                    else if (style == ProjectionStyle.GraphMatch)
                    {
                        var colName = getGraphExpressionResultName(expression);
                        if (colName == null) // PORT: §3.14 ??
                            colName = col != null ? col.name() : null; // PORT: §3.14 ?.
                        if (col == null)
                        {
                            type = getResultTypeOrError(expression);
                            if (!type.isError() && !type.isScalar())
                            {
                                diagnostics.add(DiagnosticFacts.getScalarTypeExpected().withLocation(expression));
                                type = ScalarTypes.Unknown;
                            }

                            col = getOrDeclareColumnForExpression(expression, colName, columnType != null ? columnType : type); // PORT: §3.14 ??
                        }

                        if (!DotNetStrings.isNullOrWhiteSpace(colName))
                        {
                            col = col.withName(colName);
                        }

                        builder.add(col.withType(columnType != null ? columnType : col.type())); // PORT: §3.14 ??
                    }
                    else
                    {
                        type = getResultTypeOrError(expression);
                        if (!type.isError() && !type.isScalar())
                        {
                            diagnostics.add(DiagnosticFacts.getScalarTypeExpected().withLocation(expression));
                            type = ScalarTypes.Unknown;
                        }

                        if (style == ProjectionStyle.Print && columnName != null)
                        {
                            col = getOrDeclareColumnForExpression(expression, columnName, columnType != null ? columnType : type); // PORT: §3.14 ??
                            builder.add(col, columnName, false); // PORT: §3.12 named argument replace
                        }
                        else
                        {
                            var name = getExpressionResultName(expression, null);
                            if (name == null) // PORT: §3.14 ??
                                name = columnName;
                            if (name == null) // PORT: §3.14 ??
                                name = getDefaultColumnName(expression, style == ProjectionStyle.Extend);
                            col = getOrDeclareColumnForExpression(expression, name, columnType != null ? columnType : type); // PORT: §3.14 ??
                            builder.add(col, null, style == ProjectionStyle.Replace || style == ProjectionStyle.Extend); // PORT: §3.12 named argument replace
                        }
                    }
                    break;
            }
        }
    }

    private void createProjectionColumns(Expression expression, ProjectionBuilder builder, List<Diagnostic> diagnostics, ProjectionStyle style, boolean doNotRepeat, TypeSymbol columnType) // PORT: §3.12 columnName = null
    {
        createProjectionColumns(expression, builder, diagnostics, style, doNotRepeat, columnType, null);
    }

    private void createProjectionColumns(Expression expression, ProjectionBuilder builder, List<Diagnostic> diagnostics, ProjectionStyle style, boolean doNotRepeat) // PORT: §3.12 columnType = null
    {
        createProjectionColumns(expression, builder, diagnostics, style, doNotRepeat, null, null);
    }

    private void createProjectionColumns(Expression expression, ProjectionBuilder builder, List<Diagnostic> diagnostics, ProjectionStyle style) // PORT: §3.12 doNotRepeat = false
    {
        createProjectionColumns(expression, builder, diagnostics, style, false, null, null);
    }

    private void createProjectionColumns(Expression expression, ProjectionBuilder builder, List<Diagnostic> diagnostics) // PORT: §3.12 style = ProjectionStyle.Default
    {
        createProjectionColumns(expression, builder, diagnostics, ProjectionStyle.Default, false, null, null);
    }

    /// <summary>
    /// Gets all the column names specified as literals, dynamic arrays or from column_names_of 
    /// </summary>
    private static void getProjectByNamesNames(Expression expression, List<String> names)
    {
        while (expression.referencedSymbol() instanceof VariableSymbol vs
            && vs.source() != null)
        {
            // see through variable to initializer expression..
            // this will work with parameters too when functions are rebound
            expression = vs.source();
        }

        if (expression instanceof DynamicExpression dex)
        {
            if (dex.expression().constantValue() instanceof String dstring)
            {
                names.add(dstring);
            }
            else if (dex.expression() instanceof JsonArrayExpression jex)
            {
                for (int i = 0; i < jex.values().size(); i++)
                {
                    if (jex.values().get(i).element().constantValue() instanceof String astring)
                    {
                        names.add(astring);
                    }
                }
            }
        }
        else if (expression instanceof FunctionCallExpression fc
            && expression.referencedSymbol() == Functions.ColumnNamesOf
            && fc.argumentList().expressions().size() > 0
            && fc.argumentList().expressions().get(0).element().resultType() instanceof TableSymbol rt)
        {
            // get names of columns in the argument to column_names_of() function
            for (var rtcol : rt.columns())
            {
                names.add(rtcol.name());
            }
        }
        else if (expression.constantValue() instanceof String name)
        {
            // a string literal or constant
            names.add(name);
        }
    }

    private enum ColumnOrdering
    {
        None,
        Ascending,
        Descending,
        GrannyAscending,
        GrannyDescending
    }

    private static ColumnOrdering getColumnOrdering(OrderingClause ordering)
    {
        if (ordering == null)
            return ColumnOrdering.None;

        switch (ordering.ascOrDescKeyword().kind())
        {
            case AscKeyword:
                return ColumnOrdering.Ascending;
            case DescKeyword:
                return ColumnOrdering.Descending;
            case GrannyAscKeyword:
                return ColumnOrdering.GrannyAscending;
            case GrannyDescKeyword:
                return ColumnOrdering.GrannyDescending;
            default:
                return ColumnOrdering.None;
        }
    }

    private static Iterable<ColumnSymbol> orderColumns(Iterable<ColumnSymbol> columns, OrderingClause ordering) // PORT: §3.17 IEnumerable<ColumnSymbol> → Iterable (OrderBy results are eager lists, §3.6)
    {
        switch (getColumnOrdering(ordering))
        {
            case Ascending:
                return Linq.orderBy(columns, c -> c.name(), (a, b) -> DotNetStrings.compareOrdinalIgnoreCase(a, b)); // PORT: §3.6, §5 StringComparer.OrdinalIgnoreCase
            case Descending:
                return Linq.orderByDescending(columns, c -> c.name(), (a, b) -> DotNetStrings.compareOrdinalIgnoreCase(a, b)); // PORT: §3.6, §5 StringComparer.OrdinalIgnoreCase
            case GrannyAscending:
                return Linq.orderBy(columns, c -> c.name(), StringAndNumberComparer.OrdinalIgnoreCase); // PORT: §3.6
            case GrannyDescending:
                return Linq.orderByDescending(columns, c -> c.name(), StringAndNumberComparer.OrdinalIgnoreCase); // PORT: §3.6
            case None:
            default:
                return columns;
        }
    }

    private static void addProjectionColumns(ProjectionBuilder builder, Iterable<ColumnSymbol> columns, boolean doNotRepeat) // PORT: §3.17 IEnumerable<ColumnSymbol> → Iterable
    {
        // add any columns referenced in group
        for (var c : columns)
        {
            builder.add(c, null, false, doNotRepeat); // PORT: §3.12 named argument doNotRepeat
        }
    }

    /// <summary>
    /// Returns a column symbol representing the result of the expression.
    /// If the expression just references a column, then it returns that column.
    /// Otherwise it creates new column symbol.
    /// </summary>
    private static ColumnSymbol getOrDeclareColumnForExpression(Expression expression, String name, TypeSymbol type, String defaultName)
    {
        name = name != null ? name : getExpressionResultName(expression, defaultName); // PORT: §3.14 ??
        if (getResultColumn(expression) instanceof ColumnSymbol col)
        {
            if (name != null && !Objects.equals(col.name(), name)) // PORT: §3.14 string !=
            {
                return new ColumnSymbol(name, type != null ? type : col.type(), null, Arrays.asList(col), expression); // PORT: §3.12 named arguments originalColumns, source; §3.14 ??
            }
            else
            {
                return col;
            }
        }
        else
        {
            type = type != null ? type : getResultTypeOrError(expression); // PORT: §3.14 ??
            return new ColumnSymbol(name, type, null, null, expression); // PORT: §3.12 named argument source
        }
    }

    private static ColumnSymbol getOrDeclareColumnForExpression(Expression expression, String name, TypeSymbol type) // PORT: §3.12 defaultName = null
    {
        return getOrDeclareColumnForExpression(expression, name, type, null);
    }

    private static ColumnSymbol getOrDeclareColumnForExpression(Expression expression, String name) // PORT: §3.12 type = null
    {
        return getOrDeclareColumnForExpression(expression, name, null, null);
    }

    private static ColumnSymbol getOrDeclareColumnForExpression(Expression expression) // PORT: §3.12 name = null
    {
        return getOrDeclareColumnForExpression(expression, null, null, null);
    }


    private int _defaultColumnNameSuffix = 1;
    private String getDefaultColumnName(SyntaxNode location, boolean includeRowScope)
    {
        var name = "Column" + _defaultColumnNameSuffix++;

        while (this.canBindName(name, SymbolMatch.Any, location, includeRowScope, false)) // PORT: §3.12 named arguments includeRowScope, inferColumns
        {
            name = "Column" + _defaultColumnNameSuffix++;
        }

        return name;
    }

    public static ColumnSymbol getResultColumn(Expression expr)
    {
        if (expr == null)
        {
            return null;
        }
        else if (expr instanceof ParenthesizedExpression p)
        {
            return getResultColumn(p.expression());
        }
        else if (expr.referencedSymbol() instanceof ColumnSymbol c)
        {
            return c;
        }
        else if (expr instanceof FunctionCallExpression fc
            && isConversionFunction(fc)
            && fc.argumentList().expressions().size() == 1
            && fc.argumentList().expressions().get(0).element().referencedSymbol() instanceof ColumnSymbol ac
            && fc.resultType() == ac.type())
        {
            // this is a no-op conversion with column argument, so use argument column as 
            // the column reference for this expression too.
            return ac;
        }
        else
        {
            return null;
        }
    }

    public static boolean isConversionFunction(Expression expr)
    {
        return expr.referencedSymbol() instanceof FunctionSymbol fs
            && isConversionFunction(fs);
    }

    public static boolean isConversionFunction(FunctionSymbol fn)
    {
        return fn == Functions.ToBool
            || fn == Functions.ToBool
            || fn == Functions.ToDateTime
            || fn == Functions.ToDecimal
            || fn == Functions.ToDouble
            || fn == Functions.ToDynamic_
            || fn == Functions.ToGuid
            || fn == Functions.ToInt
            || fn == Functions.ToLong
            || fn == Functions.ToReal
            || fn == Functions.ToString
            || fn == Functions.ToTime
            || fn == Functions.ToTimespan;
    }

    private void addFunctionTupleResultColumn(FunctionSymbol function, ColumnSymbol column, ProjectionBuilder builder, boolean doNotRepeat, boolean isAggregate)
    {
        //if (builder.CanAdd(column))
        {
            var prefix = function.resultNamePrefix();

            if (prefix != null)
            {
                var prefixedColumn = column.withName(DotNetStrings.concat(function.resultNamePrefix(), "_", column.name())); // PORT: §3.14 string +
                builder.add(prefixedColumn, null, false, doNotRepeat); // PORT: §3.12 named argument doNotRepeat
            }
            else
            {
                builder.add(column, null, false, doNotRepeat); // PORT: §3.12 named argument doNotRepeat
            }
        }
    }

    private static boolean declareColumnName(Set<String> declaredNames, String newName, List<Diagnostic> diagnostics, SyntaxNode location) // PORT: §3.17 HashSet<string> → Set<String> (callers pass LinkedHashSet)
    {
        if (declaredNames.contains(newName))
        {
            if (diagnostics != null) // PORT: §3.14 ?.
                diagnostics.add(DiagnosticFacts.getDuplicateColumnDeclaration(newName).withLocation(location));
            return false;
        }
        else
        {
            declaredNames.add(newName);
            return true;
        }
    }

    /// <summary>
    /// Gets the name that a function call expression will use as its column name in a projection.
    /// </summary>
    private static String getFunctionResultName(FunctionCallExpression fc, String defaultName, TableSymbol row)
    {
        var fs = fc.referencedSymbol() instanceof FunctionSymbol fsx ? fsx : null; // PORT: §3.15 as
        var kind = fs != null ? fs.resultNameKind() : ResultNameKind.None; // PORT: §3.14 ?. ??
        var prefix = fs != null ? fs.resultNamePrefix() : null; // PORT: §3.14 ?.

        if (kind == ResultNameKind.NameAndFirstArgument)
        {
            prefix = fs.name();
            kind = ResultNameKind.PrefixAndFirstArgument;
        }
        else if (kind == ResultNameKind.NameAndOnlyArgument)
        {
            prefix = fs.name();
            kind = ResultNameKind.PrefixAndOnlyArgument;
        }

        if (kind == ResultNameKind.PrefixAndFirstArgument)
        {
            if (fc.argumentList().expressions().size() > 0)
            {
                var name = getExpressionResultName(fc.argumentList().expressions().get(0).element(), defaultName);
                if (prefix != null)
                {
                    return DotNetStrings.concat(prefix, "_", name); // PORT: §3.14 string +
                }
                else
                {
                    return name;
                }
            }
            else if (prefix != null)
            {
                return prefix + "_";
            }
            else
            {
                return null;
            }
        }
        else if (kind == ResultNameKind.PrefixAndOnlyArgument
            && fc.argumentList().expressions().size() == 1)
        {
            var name = getExpressionResultName(fc.argumentList().expressions().get(0).element(), defaultName);
            if (prefix != null)
            {
                return DotNetStrings.concat(prefix, "_", name); // PORT: §3.14 string +
            }
            else
            {
                return name;
            }
        }
        else if (kind == ResultNameKind.FirstArgumentValueIfColumn
            && fc.argumentList().expressions().size() > 0
            && fc.argumentList().expressions().get(0).element().constantValue() instanceof String name)
        {
            if (row != null && row.tryGetColumn(name, new Out<ColumnSymbol>())) // PORT: §3.3 out _
            {
                return name;
            }
            else
            {
                return defaultName;
            }
        }
        else if (kind == ResultNameKind.FirstArgument)
        {
            if (fc.argumentList().expressions().size() > 0)
            {
                return getExpressionResultName(fc.argumentList().expressions().get(0).element(), defaultName);
            }
            else
            {
                return null;
            }
        }
        else if (kind == ResultNameKind.PrefixOnly && prefix != null)
        {
            return prefix;
        }
        else if (kind == ResultNameKind.OnlyArgument && fc.argumentList().expressions().size() == 1)
        {
            return getExpressionResultName(fc.argumentList().expressions().get(0).element(), defaultName);
        }
        else
        {
            return defaultName;
        }
    }

    private static String getFunctionResultName(FunctionCallExpression fc, String defaultName) // PORT: §3.12 row = null
    {
        return getFunctionResultName(fc, defaultName, null);
    }

    private static String getFunctionResultName(FunctionCallExpression fc) // PORT: §3.12 defaultName = ""
    {
        return getFunctionResultName(fc, "", null);
    }

    /// <summary>
    /// Gets the name that an expression will use for its column name in a projection.
    /// </summary>
    public static String getExpressionResultName(Expression expr, String defaultName, TableSymbol row)
    {
        switch (expr) // PORT: §3.15 pattern switch
        {
            case ParenthesizedExpression p:
                return getExpressionResultName(p.expression(), defaultName, row);
            case NameReference n:
                return n.simpleName();
            case BracketedExpression be:
                if (be.expression().isLiteral()
                    && be.expression().resultType() instanceof TypeSymbol bet
                    && (bet == ScalarTypes.String || bet == ScalarTypes.Long || bet == ScalarTypes.Int))
                {
                    return DotNet.str(be.expression().literalValue()); // PORT: §3.14 ToString()
                }
                return defaultName;
            case PathExpression p:
                if (TypeFacts.isDynamicArrayOrBag(p.expression().resultType()) // PORT: §3.5
                    || p.expression().resultType() == ScalarTypes.Unknown)
                {
                    var left = getExpressionResultName(p.expression(), null);
                    var right = getExpressionResultName(p.selector(), null);
                    if (!DotNetStrings.isNullOrWhiteSpace(left))
                    {
                        return DotNetStrings.concat(left, "_", right); // PORT: §3.14 interpolation
                    }
                    else
                    {
                        return right;
                    }
                }
                else
                {
                    return getExpressionResultName(p.selector(), defaultName);
                }
            case ElementExpression e:
                if (TypeFacts.isDynamicArrayOrBag(e.expression().resultType()) // PORT: §3.5
                    || e.expression().resultType() == ScalarTypes.Unknown)
                {
                    var left = getExpressionResultName(e.expression(), null);
                    var right = getExpressionResultName(e.selector(), null);
                    if (!DotNetStrings.isNullOrWhiteSpace(left))
                    {
                        return DotNetStrings.concat(left, "_", right); // PORT: §3.14 interpolation
                    }
                    else
                    {
                        return right;
                    }
                }
                else
                {
                    return getExpressionResultName(e.selector(), defaultName);
                }
            case OrderedExpression o:
                return getExpressionResultName(o.expression(), defaultName);
            case SimpleNamedExpression s:
                return s.name().simpleName();
            case FunctionCallExpression f:
                return getFunctionResultName(f, defaultName, row);
            default:
                return defaultName;
        }
    }

    public static String getExpressionResultName(Expression expr, String defaultName) // PORT: §3.12 row = null
    {
        return getExpressionResultName(expr, defaultName, null);
    }

    public static String getExpressionResultName(Expression expr) // PORT: §3.12 defaultName = ""
    {
        return getExpressionResultName(expr, "", null);
    }

    /// <summary>
    /// Gets the declared name of a <see cref="SimpleNamedExpression"/> or null. 
    /// </summary>
    public static String getExpressionDeclaredName(Expression expr)
    {
        switch (expr) // PORT: §3.15 pattern switch
        {
            case SimpleNamedExpression n:
                return n.name().simpleName();
            case OrderedExpression o:
                return getExpressionDeclaredName(o.expression());
            default:
                return null;
        }
    }

    /// <summary>
    /// Gets the expression underlying adornments such as name assignment or ordering
    /// </summary>
    public static Expression getUnderlyingExpression(Expression expression)
    {
        switch (expression) // PORT: §3.15 pattern switch
        {
            case SimpleNamedExpression n:
                return getUnderlyingExpression(n.expression());
            case OrderedExpression o:
                return getUnderlyingExpression(o.expression());
            default:
                return expression;
        }
    }

    /// <summary>
    /// Gets the name that an expression will use for its column name in a graph projection.
    /// </summary>
    public static String getGraphExpressionResultName(Expression expression)
    {
        if (expression == null)
        {
            return "";
        }

        Expression patternElementExpression;
        Expression selector;
        if (expression instanceof PathExpression pathExpression)
        {
            patternElementExpression = pathExpression.expression();
            selector = pathExpression.selector();
        }
        else if (expression instanceof ElementExpression elementExpression)
        {
            patternElementExpression = elementExpression.expression();
            selector = elementExpression.selector();
        }
        else
        {
            return getExpressionResultName(expression);
        }

        var left = patternElementExpression.kind() == SyntaxKind.ElementExpression || patternElementExpression.kind() == SyntaxKind.PathExpression
            ? getGraphExpressionResultName(patternElementExpression)
            : getExpressionResultName(patternElementExpression);

        var right = getExpressionResultName(selector);    
        
        return !DotNetStrings.isNullOrWhiteSpace(left) ? DotNetStrings.concat(left, "_", right) : right; // PORT: §3.14 interpolation
    }

    // ===== upstream part: Binder_SearchPredicateBinder.cs =====
    /// <summary>
    /// The <see cref="SearchPredicateBinder"/> handles special binding logic for predicates used by search and find operators.
    /// </summary>
    private static class SearchPredicateBinder extends DefaultSyntaxVisitor
    {
        private final Binder _binder;
        private final TreeBinder _treeBinder;

        public SearchPredicateBinder(
            Binder binder,
            TreeBinder treeBinder)
        {
            _binder = binder;
            _treeBinder = treeBinder;
        }

        public TableSymbol rowScopeOrEmpty() { return _binder._rowScope != null ? _binder._rowScope : TableSymbol.Empty; } // PORT: §3.1, §3.14 ??

        @Override
        protected void defaultVisit(SyntaxNode node)
        {
            // if we get here, fall back to normal expression binding.
            node.accept(_treeBinder);
        }

        @Override
        public void visitStarExpression(StarExpression node)
        {
            if (node.parent() instanceof BinaryExpression b)
            {
                // use dynamic type as union of all column types
                _binder.setSemanticInfo(node, new SemanticInfo(ScalarTypes.Dynamic));
            }
            else
            {
                // stand alone asterisk equiv to 'true'
                _binder.setSemanticInfo(node, new SemanticInfo(ScalarTypes.Bool));
            }
        }

        @Override
        public void visitLiteralExpression(LiteralExpression node)
        {
            bindStandAloneSearchTerm(node);
        }

        @Override
        public void visitNameReference(NameReference node)
        {
            bindStandAloneSearchTerm(node);
        }

        private void bindStandAloneSearchTerm(Expression node)
        {
            // bind it first and then adjust if necessary
            node.accept(_treeBinder);

            if (node.isConstant() && node.resultType() == ScalarTypes.String)
            {
                // stand alone constant search terms are abbreviations of: * has <constant>
                // so make them claim to be boolean
                _binder.setSemanticInfo(node, node.getSemanticInfo().withResultType(ScalarTypes.Bool));
            }
        }

        @Override
        public void visitParenthesizedExpression(ParenthesizedExpression node)
        {
            // stay in special rules for parenthesized expression
            node.expression().accept(this);
            _binder.setSemanticInfo(node, new SemanticInfo(node.expression().resultType()));
        }

        @Override
        public void visitBinaryExpression(BinaryExpression node)
        {
            var opKind = getOperatorKind(node.kind());
            switch (opKind)
            {
                case And:
                case Or:
                    // stay in special rules for and/or
                    node.left().accept(this);
                    node.right().accept(this);
                    var info = _binder.getBinaryOperatorInfo(opKind, node.left(), node.right(), node.operator());
                    _binder.setSemanticInfo(node, info);
                    break;

                default:
                    // drop back to normal expression binding for anything else
                    node.accept(_treeBinder);
                    break;
            }
        }
    }

    // ===== upstream part: Binder_TablesAndColumns.cs =====
    /// <summary>
    /// Adds all the columns declared by the symbol to the list of columns.
    /// </summary>
    private void addTableColumns(Symbol symbol, List<ColumnSymbol> columns)
    {
        // PORT: §3.15 switch (symbol) { case TableSymbol t: ... case GroupSymbol g: ... } as an instanceof chain (null matches no case)
        if (symbol instanceof TableSymbol t)
        {
            getDeclaredAndInferredColumns(t, columns);
        }
        else if (symbol instanceof GroupSymbol g)
        {
            for (Symbol s : g.members())
            {
                addTableColumns(s, columns);
            }
        }
    }

    /// <summary>
    /// Add the table (or all the tables in a group) to the list of tables.
    /// </summary>
    private void addTables(Symbol symbol, List<TableSymbol> tables)
    {
        // PORT: §3.15 switch (symbol) { case TableSymbol t: ... case GroupSymbol g: ... } as an instanceof chain (null matches no case)
        if (symbol instanceof TableSymbol t)
        {
            tables.add(t);
        }
        else if (symbol instanceof GroupSymbol g)
        {
            for (Symbol m : g.members())
            {
                addTables(m, tables);
            }
        }
    }

    /// <summary>
    /// Gets a table representing the aggregate set of columns in scope
    /// for find operator expressions.
    /// </summary>
    private TableSymbol getFindColumnsTable(FindOperator node)
    {
        var tables = getFindTables(node);
        return getTableOfColumnsUnifiedByName(tables);
    }

    /// <summary>
    /// Get the set of tables applicable to the find operator.
    /// </summary>
    private List<TableSymbol> getFindTables(FindOperator node) // PORT: §3.17 IReadOnlyList<T> → List<T>
    {
        if (node.inClause() != null)
        {
            return getReferencedTables(node.inClause().expressions());
        }
        else
        {
            // no in clause or row scope, so all tables in universe then!
            return getImpliedTables();
        }
    }

    /// <summary>
    /// Gets the set of columns from the tables applicable to the search operator.
    /// </summary>
    private TableSymbol getSearchColumnsTable(SearchOperator node)
    {
        if (_rowScope != null && node.inClause() == null)
        {
            return _rowScope;
        }
        else
        {
            var tables = getSearchTables(node);

            // access through cache
            return getTableOfColumnsUnifiedByNameAndType(tables);
        }
    }

    /// <summary>
    /// Gets the set of tables used by the search operator
    /// </summary>
    private List<TableSymbol> getSearchTables(SearchOperator node) // PORT: §3.17 IReadOnlyList<T> → List<T>
    {
        if (node.inClause() != null)
        {
            return getReferencedTables(node.inClause().expressions());
        }
        else if (_rowScope != null)
        {
            return Arrays.asList(_rowScope); // PORT: §3.17 new[] { _rowScope }
        }
        else
        {
            // no in clause or row scope, so all tables in universe then!
            return getImpliedTables();
        }
    }

    /// <summary>
    /// Gets all the tables accessible to the current operator through osmosis,
    /// not from pipe operator or sub clause.
    /// </summary>
    private List<TableSymbol> getImpliedTables() // PORT: §3.17 IReadOnlyList<T> → List<T>
    {
        // include current database's tables and any views in scope
        var declaredViews = s_tableListPool.allocateFromPool();
        try
        {
            getViewResultTablesInScope(declaredViews);
            if (declaredViews.size() > 0)
            {
                var all = new ArrayList<TableSymbol>(_currentDatabase.tables()); // PORT: §3.6 Concat(...).ToList()
                all.addAll(declaredViews);
                return all;
            }
            else
            {
                return _currentDatabase.tables();
            }
        }
        finally
        {
            s_tableListPool.returnToPool(declaredViews);
        }
    }

    /// <summary>
    /// Gets all the result tables of declared views in scope
    /// </summary>
    private void getViewResultTablesInScope(List<TableSymbol> views)
    {
        var localSymbols = s_symbolListPool.allocateFromPool();
        try
        {
            // get all declared tabular functions
            _localScope.getSymbols(SymbolMatch.Tabular | SymbolMatch.View, localSymbols);

            // pick out just view function declarations
            for (Symbol sym : localSymbols)
            {
                if (sym instanceof FunctionSymbol fs
                    && fs.isView()
                    && fs.minArgumentCount() == 0)
                {
                    var fts = fs.getReturnType(_globals) instanceof TableSymbol t ? t : null; // PORT: §3.15 as
                    if (fts != null)
                    {
                        views.add(fts);
                    }
                }
            }
        }
        finally
        {
            s_symbolListPool.returnToPool(localSymbols);
        }
    }

    /// <summary>
    /// Gets the set of tables referenced by the entity expressions, 
    /// where each expression is a reference to a table or group of tables.
    /// </summary>
    private List<TableSymbol> getReferencedTables(SyntaxList1<SeparatedElement1<Expression>> list) // PORT: §3.17 IReadOnlyList<T> → List<T>
    {
        var tables = new ArrayList<TableSymbol>();

        for (var x : list)
        {
            if (x.element().resultType() instanceof TableSymbol ts)
            {
                tables.add(ts);
            }
            else if (x.element().resultType() instanceof GroupSymbol gs)
            {
                for (Symbol m : gs.members()) // PORT: §3.6 hot path loop: gs.Members.OfType<TableSymbol>()
                {
                    if (m instanceof TableSymbol mt)
                    {
                        tables.add(mt);
                    }
                }
            }
        }

        return tables;
    }

    /// <summary>
    /// Converts a list of columns into a list of unique (unioned columns)
    /// Columns with the same name and type will be merged into one column.
    /// Columns with the same name but different type will be renamed to include the type name as a suffix.
    /// </summary>
    @Internal
    public static void unifyColumnsWithSameNameAndType(List<ColumnSymbol> columns)
    {
        var uniqueNames = s_uniqueNameTablePool.allocateFromPool();
        var newColumns = s_columnListPool.allocateFromPool();
        try
        {
            var map = new ColumnMap(columns);

            // go through original column order and build out new column list
            for (int i = 0; i < columns.size(); i++)
            {
                var col = columns.get(i);

                if (map.hasColumns(col.name()))
                {
                    if (map.hasMultipleTypes(col.name()))
                    {
                        var types = map.getTypes(col.name());
                        for (var type : types)
                        {
                            var sameTypeColumns = map.getColumns(col.name(), type);
                            var newType = TypeFacts.getCommonColumnType(sameTypeColumns, Conversion.None);
                            if (newType == null) // PORT: §3.14 ??
                                newType = type;
                            var suggestedName = col.name() + "_" + newType.name();
                            var newName = uniqueNames.getOrAddName(suggestedName);
                            var newCol = new ColumnSymbol(newName, newType, null, sameTypeColumns); // PORT: §3.12 named argument originalColumns
                            newColumns.add(newCol);
                        }
                    }
                    else if (map.hasMultipleColumns(col.name(), col.type()))
                    {
                        var cols = map.getColumns(col.name(), col.type());
                        var newType = TypeFacts.getCommonColumnType(cols, Conversion.None);
                        if (newType == null) // PORT: §3.14 ??
                            newType = col.type();
                        var newCol = new ColumnSymbol(col.name(), newType, null, cols); // PORT: §3.12 named argument originalColumns
                        newColumns.add(getUniqueColumn(newCol, uniqueNames));
                    }
                    else
                    {
                        newColumns.add(getUniqueColumn(col, uniqueNames));
                    }
                }

                // we've already handled this name, remove it so we don't try adding it again
                map.remove(col.name());
            }

            // copy new list back to original
            columns.clear();
            columns.addAll(newColumns);
        }
        finally
        {
            s_uniqueNameTablePool.returnToPool(uniqueNames);
            s_columnListPool.returnToPool(newColumns);
        }
    }

    /// <summary>
    /// Converts list of columns to a list of columns with distinct names.
    /// If multiple columns have the same name, but differ in type, the resulting single columns has the type dynamic.
    /// </summary>
    @Internal
    public static void unifyColumnsWithSameName(List<ColumnSymbol> columns)
    {
        var newColumns = s_columnListPool.allocateFromPool();
        try
        {
            var map = new ColumnMap(columns);

            // go through original column order and build out new column list
            for (int i = 0; i < columns.size(); i++)
            {
                var col = columns.get(i);

                if (map.hasColumns(col.name()))
                {
                    if (map.hasMultipleTypes(col.name()))
                    {
                        var types = map.getTypes(col.name());
                        var commonType = TypeFacts.getCommonScalarType(types);
                        if (commonType == null) // PORT: §3.14 ??
                            commonType = ScalarTypes.Dynamic;
                        var originalCols = new ArrayList<ColumnSymbol>(map.getColumns(col.name())); // PORT: §3.6 ToList()
                        var newCol = new ColumnSymbol(col.name(), commonType, null, originalCols); // PORT: §3.12 named argument originalColumns
                        newColumns.add(newCol);
                    }
                    else if (map.hasMultipleColumns(col.name(), col.type()))
                    {
                        var originalCols = map.getColumns(col.name(), col.type());
                        var newCol = new ColumnSymbol(col.name(), col.type(), null, originalCols); // PORT: §3.12 named argument originalColumns
                        newColumns.add(newCol);
                    }
                    else
                    {
                        newColumns.add(col);
                    }
                }

                // we've already handled this name, so remove it so we don't add it again
                map.remove(col.name());
            }

            // copy new list back to original
            columns.clear();
            columns.addAll(newColumns);
        }
        finally
        {
            s_columnListPool.returnToPool(newColumns);
        }
    }

    /// <summary>
    /// Converts a list of columns into a list of unique columns by name.
    /// Columns with the same name will be renamed to include a numeric suffix.
    /// </summary>
    @Internal
    public static void makeColumnNamesUnique(List<ColumnSymbol> columns)
    {
        var nameToIndexMap = s_stringToIntMapPool.allocateFromPool();
        var nameTable = s_uniqueNameTablePool.allocateFromPool();
        var newColumns = s_columnListPool.allocateFromPool();
        try
        {
            // associate all unique names with first index where they appear
            // to avoid renamed columns influcing the names of other columns
            // further down the list,
            // so [c, c, c1] will map to [c, c2, c1] not [c, c1, c11]
            for (int index = 0; index < columns.size(); index++)
            {
                var col = columns.get(index);
                if (!nameToIndexMap.containsKey(col.name()))
                    DotNet.dictionaryAdd(nameToIndexMap, col.name(), index); // PORT: §3.17 Dictionary.Add
            }

            nameTable.addNames(nameToIndexMap.keySet());

            // go through original column order and build out new column list
            for (int index = 0; index < columns.size(); index++)
            {
                var col = columns.get(index);
                var firstIndex = nameToIndexMap.get(col.name()); // PORT: §3.3 TryGetValue (values never null)
                if (firstIndex != null
                    && index != firstIndex)
                {
                    newColumns.add(getUniqueColumn(col, nameTable));
                }
                else
                {
                    newColumns.add(col);
                }
            }

            // copy new list back to original
            columns.clear();
            columns.addAll(newColumns);
        }
        finally
        {
            s_uniqueNameTablePool.returnToPool(nameTable);
            s_columnListPool.returnToPool(newColumns);
            s_stringToIntMapPool.returnToPool(nameToIndexMap);
        }
    }

    /// <summary>
    /// Gets the columns that appear in both list of columns (by name)
    /// </summary>
    private static void getCommonColumns(List<ColumnSymbol> columnsA, List<ColumnSymbol> columnsB, List<Symbol> result) // PORT: §2.5, §3.17 IReadOnlyList<T> → List<T>
    {
        var columns = s_columnListPool.allocateFromPool();
        try
        {
            getCommonColumnsOfColumns(columnsA, columnsB, columns); // PORT: §2.5

            for (ColumnSymbol c : columns)
            {
                result.add(c);
            }
        }
        finally
        {
            s_columnListPool.returnToPool(columns);
        }
    }

    /// <summary>
    /// Gets the columns that appear in both list of columns (by name)
    /// </summary>
    private static void getCommonColumnsOfColumns(List<ColumnSymbol> columnsA, List<ColumnSymbol> columnsB, List<ColumnSymbol> result) // PORT: §2.5, §3.17 IReadOnlyList<T> → List<T>
    {
        var names = s_stringSetPool.allocateFromPool();
        try
        {
            for (ColumnSymbol c : columnsB)
            {
                names.add(c.name());
            }

            for (ColumnSymbol c : columnsA)
            {
                if (names.contains(c.name()))
                {
                    result.add(c);
                }
            }

            names.clear();
            for (ColumnSymbol c : columnsA)
            {
                names.add(c.name());
            }

            for (ColumnSymbol c : columnsB)
            {
                if (names.contains(c.name()))
                {
                    result.add(c);
                }
            }

            unifyColumnsWithSameName(result);
        }
        finally
        {
            s_stringSetPool.returnToPool(names);
        }
    }

    /// <summary>
    /// Gets the columns that appear in all tables.
    /// </summary>
    @Internal
    public static void getCommonColumns(List<TableSymbol> tables, List<ColumnSymbol> common) // PORT: §3.17 IReadOnlyList<T> → List<T>
    {
        common.clear();

        if (tables.size() == 1)
        {
            common.addAll(tables.get(0).columns());
        }
        else if (tables.size() == 2)
        {
            getCommonColumnsOfColumns(tables.get(0).columns(), tables.get(1).columns(), common); // PORT: §2.5
        }
        else if (tables.size() > 2)
        {
            var columnsA = s_columnListPool.allocateFromPool();
            var columnsC = s_columnListPool.allocateFromPool();
            try
            {
                getCommonColumnsOfColumns(tables.get(0).columns(), tables.get(1).columns(), columnsA); // PORT: §2.5

                for (int i = 2; i < tables.size(); i++)
                {
                    getCommonColumnsOfColumns(columnsA, tables.get(i).columns(), columnsC); // PORT: §2.5

                    if (i < tables.size() - 1)
                    {
                        columnsA.clear();
                        columnsA.addAll(columnsC);
                        columnsC.clear();
                    }
                }

                common.addAll(columnsC);
            }
            finally
            {
                s_columnListPool.returnToPool(columnsA);
                s_columnListPool.returnToPool(columnsC);
            }
        }
    }

    /// <summary>
    /// Gets a column with a unique name (given a set of already used names).
    /// </summary>
    private static ColumnSymbol getUniqueColumn(ColumnSymbol column, UniqueNameTable uniqueNames)
    {
        var uniqueName = uniqueNames.getOrAddName(column.name());
        if (!Objects.equals(uniqueName, column.name())) // PORT: §3.14 string !=
        {
            return new ColumnSymbol(uniqueName, column.type(), null, Arrays.asList(column)); // PORT: §3.12 named argument originalColumns
        }
        else
        {
            return column;
        }
    }

    /// <summary>
    /// Creates column symbols for all the columns declared in the schema.
    /// </summary>
    public static void createColumnsFromRowSchema(SyntaxList1<SeparatedElement1<NameAndTypeDeclaration>> schemaColumns, List<ColumnSymbol> columns, List<Diagnostic> diagnostics)
    {
        var declaredNames = s_stringSetPool.allocateFromPool();
        try
        {
            for (int i = 0, n = schemaColumns.size(); i < n; i++)
            {
                var nat = schemaColumns.get(i).element();

                // PORT: §3.15 switch (nat.Type) { case PrimitiveTypeExpression p: ... default: ... } as an instanceof chain (null falls to default)
                if (nat.type() instanceof PrimitiveTypeExpression p)
                {
                    var type = getType(p); // diagnostics should already have been added
                    if (declareColumnName(declaredNames, nat.name().simpleName(), diagnostics, nat.name().name()))
                    {
                        columns.add(new ColumnSymbol(nat.name().simpleName(), type, null, null, nat.name())); // PORT: §3.12 named argument source
                    }
                }
                else
                {
                    if (diagnostics != null) // PORT: §3.14 ?.
                        diagnostics.add(DiagnosticFacts.getInvalidColumnDeclaration().withLocation(nat));
                }
            }
        }
        finally
        {
            s_stringSetPool.returnToPool(declaredNames);
        }
    }

    public static void createColumnsFromRowSchema(SyntaxList1<SeparatedElement1<NameAndTypeDeclaration>> schemaColumns, List<ColumnSymbol> columns) // PORT: §3.12 diagnostics = null
    {
        createColumnsFromRowSchema(schemaColumns, columns, null);
    }

    /// <summary>
    /// Gets the columns referenced by all expressions
    /// </summary>
    private void getColumnsInColumnList(SyntaxList1<SeparatedElement1<Expression>> expressions, List<ColumnSymbol> columns, List<Diagnostic> diagnostics)
    {
        for (var elem : expressions)
        {
            getReferencedColumns(elem.element(), columns, diagnostics);
        }
    }

    /// <summary>
    /// Gets the columns referenced by one expression.
    /// </summary>
    private void getReferencedColumns(Expression expression, List<ColumnSymbol> columns, List<Diagnostic> diagnostics)
    {
        var symbol = getReferencedSymbol(expression);

        // PORT: §3.15 switch (symbol) { case ColumnSymbol c: ... case GroupSymbol g: ... default: ... } as an instanceof chain (null falls to default)
        if (symbol instanceof ColumnSymbol c)
        {
            columns.add(c);
        }
        else if (symbol instanceof GroupSymbol g)
        {
            for (Symbol m : g.members())
            {
                if (m instanceof ColumnSymbol c)
                {
                    columns.add(c);
                }
            }
        }
        else
        {
            if (diagnostics != null) // PORT: §3.14 ?.
                diagnostics.add(DiagnosticFacts.getColumnExpected().withLocation(expression));
        }
    }

    private void getReferencedColumns(Expression expression, List<ColumnSymbol> columns) // PORT: §3.12 diagnostics = null
    {
        getReferencedColumns(expression, columns, null);
    }

    /// <summary>
    /// Gets all the columns referenced in the syntax tree.
    /// </summary>
    private void getReferencedColumnsInTree(SyntaxNode node, List<ColumnSymbol> columns)
    {
        for (var nr : node.getDescendantsOrSelf(NameReference.class)) // PORT: §3.10
        {
            getReferencedColumns(nr, columns);
        }
    }

    // ===== upstream part: Binder_TreeBinder.cs =====
    /// <summary>
    /// The <see cref="TreeBinder"/> is a <see cref="SyntaxVisitor"/> that orchestrates binding the entire syntax tree.
    /// From the top down, it adjusts the <see cref="Binder"/>'s state to determine symbols in scope for each node and its descendants, etc.
    /// From bottom up, it invokes the <see cref="NodeBinder"/> on each <see cref="SyntaxNode"/> to evalute its <see cref="SemanticInfo"/> if any.
    /// All child nodes are thus bound before any parent nodes.
    /// </summary>
    private static class TreeBinder extends DefaultSyntaxVisitor
    {
        private final Binder _binder;
        private final NodeBinder _nodeBinder;

        public TreeBinder(Binder binder)
        {
            _binder = binder;
            _nodeBinder = new NodeBinder(binder);
        }

        @Override
        protected void defaultVisit(SyntaxNode node)
        {
            // first bind child nodes
            this.visitChildren(node);

            // bind this node
            this.bindNode(node);
        }

        private void visitChildren(SyntaxNode node)
        {
            if (node != null)
            {
                for (int i = 0, n = node.childCount(); i < n; i++)
                {
                    var child = node.getChild(i) instanceof SyntaxNode sn ? sn : null; // PORT: §3.15 as
                    if (child != null)
                    {
                        child.accept(this);
                    }
                }
            }
        }

        private void bindNode(SyntaxNode node)
        {
            _binder._cancellationToken.throwIfCancellationRequested();

            // use NodeBinder to determine semantic info for this node.
            var info = node.accept(_nodeBinder);

            // remember semantic info
            _binder.setSemanticInfo(node, info);
        }

        @Override
        public void visitPathExpression(PathExpression node)
        {
            // bracketed expressions are not evaluated in scope of the left-hand side
            if (node.selector() instanceof BracketedExpression)
            {
                super.visitPathExpression(node);
                return;
            }
            else
            {
                node.expression().accept(this);

                // result type of left-side expression is in scope after the dot.
                var oldPathScope = _binder._pathScope;
                _binder._pathScope = getResultTypeOrError(node.expression());
                try
                {
                    node.selector().accept(this);
                }
                finally
                {
                    _binder._pathScope = oldPathScope;
                }

                bindNode(node);
            }
        }

        @Override
        public void visitDynamicExpression(DynamicExpression node)
        {
            _binder._dynamicDepth++;
            try
            {
                super.visitDynamicExpression(node);
            }
            finally
            {
                _binder._dynamicDepth--;
            }
        }

        @Override
        public void visitPipeExpression(PipeExpression node)
        {
            if (node.operator() instanceof UnionOperator union)
            {
                // set fuzziness of left side expression if operator is fuzzy union
                var oldIsFuzzy = _binder._isFuzzy;
                Boolean isFuzzy = SyntaxExtensions.getParameterLiteralValue(union.parameters(), Boolean.class, QueryOperatorParameters.IsFuzzy); // PORT: §3.5, §3.10 GetParameterLiteralValue<bool?>
                if (isFuzzy != null)
                    _binder._isFuzzy = isFuzzy; // PORT: §3.7 bool?.Value
                node.expression().accept(this);
                _binder._isFuzzy = oldIsFuzzy;
            }
            else
            {
                node.expression().accept(this);
            }

            // result of left-side expression is in scope for right-side query operator
            var oldRowScope = _binder._rowScope;
            var oldScopeKind = _binder._scopeKind;
            _binder._rowScope = getResultType(node.expression()) instanceof TableSymbol ts ? ts : null; // PORT: §3.15 as
            _binder._scopeKind = ScopeKind.Normal;
            try
            {
                node.operator().accept(this);
            }
            finally
            {
                _binder._rowScope = oldRowScope;
                _binder._scopeKind = oldScopeKind;
            }

            bindNode(node);
        }

        @Override
        public void visitLookupOperator(LookupOperator node)
        {
            node.parameters().accept(this);

            // table expression should not see row scope...
            var oldRowScope = _binder._rowScope;
            _binder._rowScope = null;
            try
            {
                node.expression().accept(this);
            }
            finally
            {
                _binder._rowScope = oldRowScope;
            }

            // condition clause should see both left & right row scopes.
            _binder._rightRowScope = getResultType(node.expression()) instanceof TableSymbol ts ? ts : null; // PORT: §3.15 as

            try
            {
                node.lookupClause().accept(this);

                // allow right scope to stay for binding of lookup operator node too.
                bindNode(node);
            }
            finally
            {
                _binder._rightRowScope = null;
            }
        }

        @Override
        public void visitJoinOperator(JoinOperator node)
        {
            node.parameters().accept(this);

            // table expression should not see row scope...
            var oldRowScope = _binder._rowScope;
            _binder._rowScope = null;
            try
            {
                node.expression().accept(this);
            }
            finally
            {
                _binder._rowScope = oldRowScope;
            }

            // condition clause should see both left & right row scopes.
            _binder._rightRowScope = getResultType(node.expression()) instanceof TableSymbol ts ? ts : null; // PORT: §3.15 as
            try
            {
                if (node.conditionClause() != null) // PORT: §3.14 ?.
                {
                    node.conditionClause().accept(this);
                }

                // allow right scope to stay for binding of join operator node too.
                bindNode(node);
            }
            finally
            {
                _binder._rightRowScope = null;
            }
        }

        @Override
        public void visitJoinOnClause(JoinOnClause node)
        {
            for (int i = 0; i < node.expressions().size(); i++)
            {
                visitJoinOnExpression(node.expressions().get(i).element());
            }
        }

        private void visitJoinOnExpression(Expression expr)
        {
            if (expr instanceof BinaryExpression be && be.kind() == SyntaxKind.AndExpression)
            {
                visitJoinOnExpression(be.left());
                visitJoinOnExpression(be.right());
                _binder.setSemanticInfo(be, new SemanticInfo(ScalarTypes.Bool));
            }
            else
            {
                _binder._commonColumnsOnly = true;
                expr.accept(this);
                _binder._commonColumnsOnly = false;
            }
        }

        @Override
        public void visitUnionOperator(UnionOperator node)
        {
            // union operator expressions do not bind to row scope columns (they are only tables)
            var oldRowScope = _binder._rowScope;
            _binder._rowScope = null;

            // set fuzziness of input evaluation
            var oldIsFuzzy = _binder._isFuzzy;
            Boolean isFuzzy = SyntaxExtensions.getParameterLiteralValue(node.parameters(), Boolean.class, QueryOperatorParameters.IsFuzzy); // PORT: §3.5, §3.10 GetParameterLiteralValue<bool?>
            if (isFuzzy != null)
                _binder._isFuzzy = isFuzzy; // PORT: §3.7 bool?.Value

            visitChildren(node);

            _binder._rowScope = oldRowScope;
            _binder._isFuzzy = oldIsFuzzy;

            bindNode(node);
        }

        @Override
        public void visitSummarizeOperator(SummarizeOperator node)
        {
            node.parameters().accept(this);

            // visit by clause before aggregates so by expressions are already bound
            // when resolving aggregate expression result types.
            if (node.byClause() != null) // PORT: §3.14 ?.
            {
                node.byClause().accept(this);
            }

            visitInScopeKind(node.aggregates(), ScopeKind.Aggregate);

            bindNode(node);
        }

        @Override
        public void visitMacroExpandOperator(MacroExpandOperator node)
        {
            // analyze parameters
            node.parameters().accept(this);

            // set fuzziness of entity evaluation
            var oldIsFuzzy = _binder._isFuzzy;
            Boolean isFuzzy = SyntaxExtensions.getParameterLiteralValue(node.parameters(), Boolean.class, QueryOperatorParameters.IsFuzzy); // PORT: §3.5, §3.10 GetParameterLiteralValue<bool?>
            if (isFuzzy != null)
            {
                _binder._isFuzzy = isFuzzy; // PORT: §3.7 bool?.Value
            }

            // analyze entity group
            node.entityGroup().accept(this);
            _binder._isFuzzy = false;

            // define scope symbol
            if (node.scopeReferenceName() != null) // PORT: §3.14 ?.
            {
                node.scopeReferenceName().accept(this);
            }

            var diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                var resultTables = new ArrayList<TableSymbol>();
                var alternateStatementLists = new ArrayList<SyntaxNode>();

                var egSymbol = node.entityGroup().resultType() instanceof EntityGroupSymbol eg ? eg : null; // PORT: §3.15 as
                // PORT: §3.14 node.ScopeReferenceName?.EntityGroupReferenceName?.ReferencedSymbol as EntityGroupElementSymbol
                var scopeReferenceName = node.scopeReferenceName();
                var entityGroupReferenceName = scopeReferenceName != null ? scopeReferenceName.entityGroupReferenceName() : null;
                var declaredScope = entityGroupReferenceName != null && entityGroupReferenceName.referencedSymbol() instanceof EntityGroupElementSymbol des ? des : null;
                var entityGroupNameReference = node.entityGroup() instanceof NameReference egnr ? egnr : null; // PORT: §3.15 as
                // PORT: §3.14 ?. ?? chain
                String scopeName = entityGroupReferenceName != null ? entityGroupReferenceName.simpleName() : null;
                if (scopeName == null)
                    scopeName = entityGroupNameReference != null ? entityGroupNameReference.simpleName() : null;
                if (scopeName == null)
                    scopeName = egSymbol != null ? egSymbol.name() : null;
                if (scopeName == null)
                    scopeName = "$scope";

                var oldScope = _binder._localScope;

                if (egSymbol != null)
                {
                    // evaluate statement list once per entity group member
                    for (Symbol member : egSymbol.members()) // PORT: §3.6 hot path: egSymbol.Members.OfType<TypeSymbol>()
                    {
                        if (!(member instanceof TypeSymbol entitySymbol))
                            continue;

                        _binder._localScope = new LocalScope(oldScope);

                        SyntaxList1<SeparatedElement1<Statement>> statements;

                        // the primary entity is the one associated with the declared scope
                        var isPrimaryEntity = (declaredScope != null ? declaredScope.underlyingSymbol() : null) == entitySymbol; // PORT: §3.14 ?.
                        if (isPrimaryEntity)
                        {
                            // associate declared scope with original statement list
                            _binder._localScope.addSymbol(declaredScope);
                            statements = node.statementList();
                        }
                        else
                        {
                            // use alternate scopes for other entity group members 
                            var alternateScope = new EntityGroupElementSymbol(scopeName, egSymbol, entitySymbol);
                            _binder._localScope.addSymbol(alternateScope);

                            // make copy of statement list to hold alternate semantic evaluation
                            statements = SyntaxNodeExtensions.copyAsFragment(node.statementList()); // PORT: §3.5

                            // remember alternate evaluated statement lists
                            alternateStatementLists.add(statements);
                        }

                        statements.accept(this);

                        if (getFirstExpressionStatement(statements) instanceof ExpressionStatement es)
                        {
                            _binder.checkIsTabular(es.expression(), diagnostics); // PORT: §3.12 resultType = null
                            if (getResultType(es.expression()) instanceof TableSymbol ts)
                            {
                                resultTables.add(ts);
                            }
                        }
                    }
                }
                else
                {
                    // no valid entity group, so evaluate statements just once with temp scope.
                    if (declaredScope != null)
                    {
                        _binder._localScope.addSymbol(declaredScope);
                    }
                    else
                    {
                        var tempScopeSymbol = new EntityGroupElementSymbol(scopeName);
                        _binder._localScope.addSymbol(tempScopeSymbol);
                    }

                    node.statementList().accept(this);
                }

                _binder._localScope = oldScope;
                _binder._isFuzzy = oldIsFuzzy;

                var resultType = TableSymbol.combine(CombineKind.UnifySameNameAndType, resultTables);
                var info = new SemanticInfo(resultType, diagnostics);
                _binder.setSemanticInfo(node, info);

                if (alternateStatementLists.size() > 0)
                {
                    var statementsInfo = new SemanticInfo((TypeSymbol)null).withAlternates(alternateStatementLists); // PORT: §3.15 C# overload resolution for new SemanticInfo(null); both candidate constructors yield the same state
                    _binder.setSemanticInfo(node.statementList(), statementsInfo);
                }
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        private static ExpressionStatement getFirstExpressionStatement(SyntaxList1<SeparatedElement1<Statement>> statements)
        {
            for (int i = 0; i < statements.size(); i++)
            {
                if (statements.get(i).element() instanceof ExpressionStatement es)
                {
                    return es;
                }
            }

            return null;
        }

        @Override
        public void visitMakeSeriesOperator(MakeSeriesOperator node)
        {
            node.parameters().accept(this);
            visitInScopeKind(node.aggregates(), ScopeKind.Aggregate);
            if (node.onClause() != null) // PORT: §3.14 ?.
                node.onClause().accept(this);
            if (node.rangeClause() != null) // PORT: §3.14 ?.
                node.rangeClause().accept(this);
            if (node.byClause() != null) // PORT: §3.14 ?.
                node.byClause().accept(this);

            bindNode(node);
        }

        private void visitInRowScope(SyntaxNode node, TableSymbol rowScope)
        {
            if (node == null)
                return;

            var oldRowScope = _binder._rowScope;
            _binder._rowScope = rowScope;
            node.accept(this);
            _binder._rowScope = oldRowScope;
        }

        private void visitInScopeKind(SyntaxNode node, ScopeKind kind)
        {
            if (node == null)
                return;

            var oldScopeKind = _binder._scopeKind;
            _binder._scopeKind = kind;
            node.accept(this);
            _binder._scopeKind = oldScopeKind;
        }

        @Override
        public void visitTopNestedClause(TopNestedClause node)
        {
            if (node.expression() != null) // PORT: §3.14 ?.
                node.expression().accept(this);
            node.ofExpression().accept(this);
            if (node.withOthersClause() != null) // PORT: §3.14 ?.
                node.withOthersClause().accept(this);
            visitInScopeKind(node.byExpression(), ScopeKind.Aggregate);

            bindNode(node);
        }

        @Override
        public void visitAsOperator(AsOperator node)
        {
            super.visitAsOperator(node);

            if (_binder._rowScope != null)
            {
                var name = node.name().simpleName();
                var symbol = new VariableSymbol(name, _binder._rowScope);
                _binder.setSemanticInfo(node.name(), new SemanticInfo(symbol, null));
                _binder._localScope.addSymbol(symbol);
            }

            bindNode(node);
        }

        @Override
        public void visitPartitionOperator(PartitionOperator node)
        {
            node.byExpression().accept(this);

            var oldLocalScope = _binder._localScope;

            // put column referenced in by-expression into scope during evaluation of partition expression
            var column = getReferencedSymbol(node.byExpression()) instanceof ColumnSymbol cs ? cs : null; // PORT: §3.15 as
            if (column != null)
            {
                _binder._localScope = new LocalScope(_binder._localScope);
                _binder._localScope.addSymbol(column);
            }

            if (node.operand() instanceof PartitionQuery)
            {
                // partition-expressions { xxx } don't have an implied row-scope, since you 
                // are required to specify a complete query expression.
                var oldRowScope = _binder._rowScope;
                _binder._rowScope = null;
                node.operand().accept(this);
                _binder._rowScope = oldRowScope;
            }
            else
            {
                // do nothing here, this sub expression assumes same row-scope as partition operator has
                node.operand().accept(this);
            }

            _binder._localScope = oldLocalScope;

            bindNode(node);
        }

        @Override
        public void visitForkOperator(ForkOperator node)
        {
            var oldRowScope = _binder._rowScope;
            try
            {
                // reset back to the original row scope for each fork
                for (var expr : node.expressions())
                {
                    _binder._rowScope = oldRowScope;
                    expr.accept(this);
                }
            }
            finally
            {
                _binder._rowScope = oldRowScope;
            }

            bindNode(node);
        }

        @Override
        public void visitMaterializedViewCombineExpression(MaterializedViewCombineExpression node)
        {
            node.viewName().accept(this);
            node.baseClause().accept(this);
            node.deltaClause().accept(this);

            var oldScope = _binder._rowScope;
            try
            {
                _binder._rowScope = getResultType(node.deltaClause().expression()) instanceof TableSymbol ts ? ts : null; // PORT: §3.15 as
                node.aggregationsClause().accept(this);
            }
            finally
            {
                _binder._rowScope = oldScope;

            }

            bindNode(node);
        }

        @Override
        public void visitParenthesizedExpression(ParenthesizedExpression node)
        {
            // nested expressions should not see any existing path scope
            var oldPathScope = _binder._pathScope;
            _binder._pathScope = null;
            try
            {
                super.visitParenthesizedExpression(node);
            }
            finally
            {
                _binder._pathScope = oldPathScope;
            }
        }

        @Override
        public void visitFunctionCallExpression(FunctionCallExpression node)
        {
            // first bind name to determine the function
            node.name().accept(this);

            // function call arguments should not see any existing path scope
            var oldPathScope = _binder._pathScope;
            _binder._pathScope = null; 
            try
            {
                var argumentScope = _binder.getArgumentScope(node, _binder._scopeKind);

                if (getReferencedSymbol(node.name()) instanceof FunctionSymbol fn && fn.signatures().size() == 1)
                {
                    // handle arguments from a known signature specially
                    this.visitArgumentList(node.argumentList(), fn.signatures().get(0), argumentScope);
                }
                else
                {
                    this.visitInScopeKind(node.argumentList(), argumentScope);
                }
            }
            finally
            {
                _binder._pathScope = oldPathScope;
            }

            bindNode(node);

            // copy final semantic info of function call to name node, unless binding the name was an error
            if (node.name().resultType() == null || !node.name().resultType().isError())
            {
                var fcInfo = node.getSemanticInfo();
                _binder.setSemanticInfo(node.name(), new SemanticInfo(fcInfo != null ? fcInfo.referencedSymbol() : null, fcInfo != null ? fcInfo.resultType() : null)); // PORT: §3.14 ?.
            }
        }

        private void visitArgumentList(ExpressionList list, Signature signature, ScopeKind argumentScope)
        {
            var arguments = s_expressionListPool.allocateFromPool();
            var argumentParameters = s_parameterListPool.allocateFromPool();

            for (int i = 0, n = list.expressions().size(); i < n; i++)
            {
                arguments.add(list.expressions().get(i).element());
            }

            signature.getArgumentParameters(arguments, argumentParameters);

            for (int i = 0, n = arguments.size(); i < n; i++)
            {
                var arg = arguments.get(i);
                var p = argumentParameters.get(i);

                if (p != null)
                {
                    switch (p.argumentKind())
                    {
                        case Aggregate:
                            // switch to aggregate scope for arguments that need to be aggregate expressions
                            this.visitInScopeKind(arg, ScopeKind.Aggregate);
                            break;

                        case Column_Parameter0:
                        case Column_Parameter0_Common:
                            if (i > 0 && arguments.get(0) != null && arguments.get(0).resultType() instanceof TableSymbol p0Table) // PORT: §3.14 ?.
                            {
                                var oldRowScope = _binder._rowScope;

                                if (p.argumentKind() == ArgumentKind.Column_Parameter0_Common)
                                {
                                    var commonColumns = new ArrayList<ColumnSymbol>();
                                    getCommonColumnsOfColumns(_binder._rowScope.columns(), p0Table.columns(), commonColumns); // PORT: §2.5
                                    _binder._rowScope = new TableSymbol(commonColumns);
                                }
                                else
                                {
                                    _binder._rowScope = p0Table;
                                }

                                this.visitInScopeKind(arg, argumentScope);
                                _binder._rowScope = oldRowScope;
                            }
                            else
                            {
                                this.visitInScopeKind(arg, ScopeKind.Aggregate);
                            }
                            break;

                        case Expression_Parameter0_Element:
                            if (i > 0 && arguments.get(0).resultType() instanceof TupleSymbol tuple)
                            {
                                this.visitInRowScope(arg, new TableSymbol(tuple.columns()));
                            }
                            else
                            {
                                this.visitInScopeKind(arg, argumentScope);
                            }
                            break;

                        default:
                            this.visitInScopeKind(arg, argumentScope);
                            break;
                    }
                }
                else
                {
                    this.visitInScopeKind(arg, argumentScope);
                }
            }

            s_expressionListPool.returnToPool(arguments);
            s_parameterListPool.returnToPool(argumentParameters);
        }

        @Override
        public void visitInvokeOperator(InvokeOperator node)
        {
            var oldRowScope = _binder._rowScope;
            _binder._implicitArgumentType = _binder.rowScopeOrEmpty();
            _binder._rowScope = null;
            try
            {
                node.function().accept(this);
            }
            finally
            {
                _binder._rowScope = oldRowScope;
                _binder._implicitArgumentType = null;
            }

            bindNode(node);
        }

        @Override
        public void visitEvaluateOperator(EvaluateOperator node)
        {
            var oldScopeKind = _binder._scopeKind;
            _binder._scopeKind = ScopeKind.PlugIn;

            try
            {
                visitChildren(node);
            }
            finally
            {
                _binder._scopeKind = oldScopeKind;
            }

            bindNode(node);
        }

        @Override
        public void visitLetStatement(LetStatement node)
        {
            super.visitLetStatement(node);

            Out<ValueInfo> valueInfo = new Out<ValueInfo>(); // PORT: §3.3 out var
            tryGetLiteralValueInfo(node.expression(), valueInfo); // PORT: §3.3

            var exprType = getResultTypeOrError(node.expression());
            Symbol local = (exprType instanceof FunctionSymbol || exprType instanceof EntityGroupSymbol)
                ? exprType
                : (Symbol)new VariableSymbol(node.name().simpleName(), exprType, getIsConstant(node.expression()), valueInfo.value, node.expression());

            // put local symbol definition on name
            _binder.setSemanticInfo(node.name(), new SemanticInfo(local, null));

            // add to local scope
            _binder._localScope.addSymbol(local);
        }

        @Override
        public void visitFunctionDeclaration(FunctionDeclaration node)
        {
            var oldLocalScope = _binder._localScope;
            var oldDefaultColumnNameSuffix = _binder._defaultColumnNameSuffix;
            try
            {
                // remember scope before function declaration
                // to use when evaluating function expansions
                _binder._staticScopes.put(node, oldLocalScope.copy());

                _binder._localScope = new LocalScope(oldLocalScope);
                _binder._defaultColumnNameSuffix = 1;
                super.visitFunctionDeclaration(node);
            }
            finally
            {
                _binder._localScope = oldLocalScope;
                _binder._defaultColumnNameSuffix = oldDefaultColumnNameSuffix;
            }
        }

        @Override
        public void visitFunctionParameters(FunctionParameters node)
        {
            super.visitFunctionParameters(node);

            // declare all parameters in the local scope
            var diagnostics = s_diagnosticListPool.allocateFromPool();
            try
            {
                _binder.bindParameterDeclarations(node.parameters());
                _binder.addDeclarationsToLocalScope(node.parameters());
            }
            finally
            {
                s_diagnosticListPool.returnToPool(diagnostics);
            }
        }

        @Override
        public void visitQueryParametersStatement(QueryParametersStatement node)
        {
            super.visitQueryParametersStatement(node);

            // declare all query parameters in the local scope
            _binder.bindParameterDeclarationsAsVariables(node.parameters());
            _binder.addDeclarationsToLocalScope(node.parameters());
        }

        @Override
        public void visitScanOperator(ScanOperator node)
        {
            node.parameters().accept(this);
            if (node.orderByClause() != null) // PORT: §3.14 ?.
                node.orderByClause().accept(this);
            if (node.partitionByClause() != null) // PORT: §3.14 ?.
                node.partitionByClause().accept(this);

            var oldLocalScope = _binder._localScope;
            _binder._localScope = new LocalScope(oldLocalScope);
            try
            {
                if (node.declareClause() != null)
                {
                    _binder.bindColumnDeclarations(node.declareClause().declarations());
                    _binder.addDeclarationsToLocalScope(node.declareClause().declarations());
                }

                _binder.bindStepDeclarations(node);
                _binder.addStepDeclarationsToLocalScope(node);

                node.steps().accept(this);
            }
            finally
            {
                _binder._localScope = oldLocalScope;
            }

            bindNode(node);
        }

        @Override
        public void visitRowSchema(RowSchema node)
        {
            _binder.bindColumnDeclarationsOfSchema(node.columns()); // PORT: §2.5
        }

        @Override
        public void visitEvaluateRowSchema(EvaluateRowSchema node)
        {
            _binder.bindColumnDeclarationsOfSchema(node.columns()); // PORT: §2.5
        }

        @Override
        public void visitInlineExternalTableExpression(InlineExternalTableExpression node)
        {
            node.parameters().accept(this);
            if (node.schema() != null) // PORT: §3.14 ?.
                node.schema().accept(this);

            var oldLocalScope = _binder._localScope;

            try
            {
                _binder._localScope = new LocalScope(oldLocalScope);
                if (node.schema() != null)
                {
                    _binder.addDeclarationsToLocalScopeOfParameters(node.schema().columns()); // PORT: §2.5
                }
                if (node.partitionClause() != null) // PORT: §3.14 ?.
                    node.partitionClause().accept(this);

                _binder._localScope = new LocalScope(oldLocalScope);
                if (node.partitionClause() != null)
                {
                    for (int i = 0; i < node.partitionClause().partitionColumns().size(); i++)
                    {
                        var p = node.partitionClause().partitionColumns().get(i).element();
                        var name = p.name().simpleName();
                        var type = _binder.getTypeFromTypeExpression(p.type()); // PORT: §3.12 diagnostics = null

                        if (!DotNetStrings.isNullOrEmpty(name)) // PORT: §3.14 string.IsNullOrEmpty
                        {
                            var symbol = new ColumnSymbol(name, type);
                            _binder.setSemanticInfo(p.name(), new SemanticInfo(symbol, type));

                            _binder.addDeclarationToLocalScope(p.name());
                        }
                    }
                }
                if (node.pathFormat() != null) // PORT: §3.14 ?.
                    node.pathFormat().accept(this);
            }
            finally
            {
                _binder._localScope = oldLocalScope;
            }

            bindNode(node);
        }

        @Override
        public void visitPatternStatement(PatternStatement node)
        {
            super.visitPatternStatement(node);

            TypeSymbol type = node.pattern() != null
                ? getResultTypeOrError(node.pattern())
                : new PatternSymbol(node.name().simpleName());

            var local = new VariableSymbol(node.name().simpleName(), type);

            // put local symbol definition on name
            _binder.setSemanticInfo(node.name(), new SemanticInfo(local, null));

            // add to local scope
            _binder._localScope.addSymbol(local);
        }

        @Override
        public void visitPatternDeclaration(PatternDeclaration node)
        {
            // pre-bind parameters
            _binder.bindParameterDeclarationsOfFunctionParameters(node.parameters()); // PORT: §2.5
            if (node.pathParameter() != null)
            {
                _binder.bindParameterDeclaration(node.pathParameter().parameter());
            };

            var oldLocalScope = _binder._localScope;
            try
            {
                _binder._localScope = new LocalScope(oldLocalScope);

                // add bound parameter symbols to scope
                _binder.addDeclarationsToLocalScopeOfParameters(node.parameters()); // PORT: §2.5

                if (node.pathParameter() != null)
                {
                    _binder.addDeclarationToLocalScope(node.pathParameter().parameter().name());
                }

                // parameters in scope while bindng pattern match bodies
                super.visitPatternDeclaration(node);
            }
            finally
            {
                _binder._localScope = oldLocalScope;
            }
        }

        @Override
        public void visitAliasStatement(AliasStatement node)
        {
            super.visitAliasStatement(node);

            // remember database as aliased name.
            var name = node.name().simpleName();
            var db = getResultTypeOrError(node.expression()) instanceof DatabaseSymbol ds ? ds : null; // PORT: §3.15 as

            if (name != null && db != null)
            {
                _binder._aliasedDatabases.put(name, db);
            }
        }

        @Override
        public void visitFindOperator(FindOperator node)
        {
            if (node.dataScope() != null) // PORT: §3.14 ?.
                node.dataScope().accept(this);
            if (node.parameters() != null) // PORT: §3.14 ?.
                node.parameters().accept(this);
            if (node.inClause() != null) // PORT: §3.14 ?.
                node.inClause().accept(this);

            var oldRowScope = _binder._rowScope;
            try
            {
                // gather all columns to put into scope for condition
                _binder._rowScope = _binder.getFindColumnsTable(node);

                if (this.predicateBinder == null)
                {
                    this.predicateBinder = new SearchPredicateBinder(_binder, this);
                }

                node.condition().accept(this.predicateBinder);

                if (node.project() != null) // PORT: §3.14 ?.
                    node.project().accept(this);
                if (node.projectAway() != null) // PORT: §3.14 ?.
                    node.projectAway().accept(this);
            }
            finally
            {
                _binder._rowScope = oldRowScope;
            }

            bindNode(node);
        }

        private SearchPredicateBinder predicateBinder;

        @Override
        public void visitSearchOperator(SearchOperator node)
        {
            if (node.parameters() != null) // PORT: §3.14 ?.
                node.parameters().accept(this);
            if (node.dataScope() != null) // PORT: §3.14 ?.
                node.dataScope().accept(this);

            var oldRowScope = _binder._rowScope;
            _binder._rowScope = null;

            if (node.inClause() != null) // PORT: §3.14 ?.
                node.inClause().accept(this);

            // gather all columns to put in scope for condition
            _binder._rowScope = oldRowScope;
            _binder._rowScope = _binder.getSearchColumnsTable(node);

            if (this.predicateBinder == null)
            {
                this.predicateBinder = new SearchPredicateBinder(_binder, this);
            }

            node.condition().accept(this.predicateBinder);

            _binder._rowScope = oldRowScope;

            bindNode(node);
        }

        @Override
        public void visitMvApplyOperator(MvApplyOperator node)
        {
            if (node.expressions() != null) // PORT: §3.14 ?.
                node.expressions().accept(this);
            if (node.rowLimitClause() != null) // PORT: §3.14 ?.
                node.rowLimitClause().accept(this);
            if (node.contextIdClause() != null) // PORT: §3.14 ?.
                node.contextIdClause().accept(this);

            var info = node.accept(_nodeBinder);

            // now that we know the result schema (table) put it in scope and evaluate the subquery
            var oldRowScope = _binder._rowScope;
            var builder = s_projectionBuilderPool.allocateFromPool();
            try
            {
                _binder._rowScope = info.resultType() instanceof TableSymbol ts ? ts : null; // PORT: §3.15 as
                node.subquery().accept(this);

                // apply sub-query's semantic info back to overall apply operator
                var subqueryInfo = node.subquery().getSemanticInfo();

                if (oldRowScope != null)
                {
                    // add all columns not applied/iterated over
                    // PORT: §3.6 hot path: new HashSet<ColumnSymbol>(node.Expressions.Select(e => e.Element.Expression?.ReferencedSymbol as ColumnSymbol).Where(e => e != null))
                    Set<ColumnSymbol> appliedColumns = new LinkedHashSet<ColumnSymbol>(); // PORT: §3.17 HashSet → LinkedHashSet
                    for (var e : node.expressions())
                    {
                        var appliedExpression = e.element().expression();
                        var appliedColumn = appliedExpression != null && appliedExpression.referencedSymbol() instanceof ColumnSymbol acs ? acs : null; // PORT: §3.14 ?. §3.15 as
                        if (appliedColumn != null)
                        {
                            appliedColumns.add(appliedColumn);
                        }
                    }

                    for (var col : oldRowScope.columns())
                    {
                        if (!appliedColumns.contains(col))
                        {
                            builder.add(col, null, false, true); // PORT: §3.12 named argument doNotRepeat: true
                        }
                    }
                }

                if (subqueryInfo.resultType() instanceof TableSymbol subqueryTable)
                {
                    for (var col : subqueryTable.columns())
                    {
                        builder.add(col, null, true, false); // PORT: §3.12 named argument replace: true
                    }
                }
               
                var resultTable = new TableSymbol(builder.getProjection())
                    .withInheritableProperties(_binder.rowScopeOrEmpty());

                var applyInfo = new SemanticInfo(resultTable, info.diagnostics());

                _binder.setSemanticInfo(node, applyInfo);
            }
            finally
            {
                _binder._rowScope = oldRowScope;
                s_projectionBuilderPool.returnToPool(builder);
            }
        }

        @Override
        public void visitNameReference(NameReference node)
        {
            super.visitNameReference(node);

            // some commands have unqualified column reference relative to a previous table reference
            if (node.referencedSymbol() instanceof TableSymbol ts && isCommandButNotQueryPart(node))
            {
                _binder._rowScope = ts;
            }
        }

        /// <summary>
        /// Returns true if the node is part of a command syntax but not in the input/output query.
        /// </summary>
        private static boolean isCommandButNotQueryPart(SyntaxNode node)
        {
            // if the name/path is a part of a command expression
            while (node.parent() instanceof PathExpression
                || node.parent() instanceof SeparatedElement
                || node.parent() instanceof SyntaxList
                )
            {
                node = node.parent();
            }

            return node.parent() instanceof Command
                || node.parent() instanceof CustomNode;
        }

        @Override
        public void visitCommandBlock(CommandBlock node)
        {
            this.visitList(node.directives());

            if (node.statements().size() > 0)
            {
                var commandStatement = node.statements().get(0).element();
                commandStatement.accept(this);

                var command = commandStatement.getFirstDescendant(Command.class); // PORT: §3.10
                if (command != null)
                {
                    var commandResults = new VariableSymbol("$command_results", getResultTypeOrError(command));
                    _binder._localScope.addSymbol(commandResults);
                }

                // all other statements
                for (int i = 1; i < node.statements().size(); i++)
                {
                    node.statements().get(i).element().accept(this);
                }
            }
        }

        @Override
        public void visitToScalarExpression(ToScalarExpression node)
        {
            if (node.kindParameter() != null) // PORT: §3.14 ?.
                node.kindParameter().accept(this);

            var oldScope = _binder._rowScope;
            _binder._rowScope = null;
            try
            {
                if (node.expression() != null) // PORT: §3.14 ?.
                    node.expression().accept(this);
            }
            finally
            {
                _binder._rowScope = oldScope;
            }

            bindNode(node);
        }

        @Override
        public void visitToTableExpression(ToTableExpression node)
        {
            if (node.kindParameter() != null) // PORT: §3.14 ?.
                node.kindParameter().accept(this);

            var oldScope = _binder._rowScope;
            _binder._rowScope = null;
            try
            {
                if (node.expression() != null) // PORT: §3.14 ?.
                    node.expression().accept(this);
            }
            finally
            {
                _binder._rowScope = oldScope;
            }

            bindNode(node);
        }

        @Override
        public void visitMakeGraphOperator(MakeGraphOperator node)
        {
            // We want to visit (and bind) the partitioned-by subquery last.
            for (int i = 0, n = node.childCount(); i < n; i++)
            {
                if (node.getChild(i) instanceof SyntaxNode child && !"PartitionedByClause".equals(node.getName(i))) // PORT: §2.3 nameof(node.PartitionedByClause)
                {
                    child.accept(this);
                }
            }
            bindNode(node);

            // In case we have a partitioned-by subquery, we want the result symbol to be that of the 
            // subquery.
            if (node.partitionedByClause() != null)
            {
                node.partitionedByClause().accept(this);

                var mgInfo = node.getSemanticInfo();
                var info = node.partitionedByClause().subquery().getSemanticInfo().withDiagnostics(mgInfo.diagnostics());
                _binder.setSemanticInfo(node, info);
            }
            
        }

        @Override
        public void visitMakeGraphTableAndKeyClause(MakeGraphTableAndKeyClause node)
        {
            if (node.table() != null) // PORT: §3.14 ?.
                node.table().accept(this);

            var oldScope = _binder._rowScope;
            _binder._rowScope = node.table().resultType() instanceof TableSymbol ts ? ts : null; // PORT: §3.15 as
            try
            {
                if (node.column() != null) // PORT: §3.14 ?.
                    node.column().accept(this);
            }
            finally
            {
                _binder._rowScope = oldScope;
            }

            bindNode(node);
        }

        @Override
        public void visitGraphMatchOperator(GraphMatchOperator node)
        {
            var oldScope = _binder._rowScope;
            var oldLocalScope = _binder._localScope;
            _binder._rowScope = null;
            _binder._localScope = new LocalScope(oldLocalScope);
            try
            {
                node.parameters().accept(this);

                _binder.bindGraphMatchPatternDeclarations(node, node.patterns());
                _binder.addGraphMatchPatternDeclarationsToLocalScope(node.patterns());

                if (node.whereClause() != null) // PORT: §3.14 ?.
                    node.whereClause().accept(this);
                if (node.projectClause() != null) // PORT: §3.14 ?.
                    node.projectClause().accept(this);
            }
            finally
            {
                _binder._rowScope = oldScope;
                _binder._localScope = oldLocalScope;
            }

            bindNode(node);
        }

        @Override
        public void visitGraphShortestPathsOperator(GraphShortestPathsOperator node)
        {
            var oldScope = _binder._rowScope;
            var oldLocalScope = _binder._localScope;
            _binder._rowScope = null;
            _binder._localScope = new LocalScope(oldLocalScope);
            try
            {
                node.parameters().accept(this);

                _binder.bindGraphMatchPatternDeclarations(node, node.patterns());
                _binder.addGraphMatchPatternDeclarationsToLocalScope(node.patterns());

                if (node.whereClause() != null) // PORT: §3.14 ?.
                    node.whereClause().accept(this);
                if (node.projectClause() != null) // PORT: §3.14 ?.
                    node.projectClause().accept(this);
            }
            finally
            {
                _binder._rowScope = oldScope;
                _binder._localScope = oldLocalScope;
            }

            bindNode(node);
        }

        @Override
        public void visitGraphToTableOperator(GraphToTableOperator node)
        {
            super.visitGraphToTableOperator(node);
            var oldScope = _binder._rowScope;
            _binder._rowScope = null;

            try
            {                                        
                if (node.outputClause().size() > 1 && node.resultType() instanceof GroupSymbol group)
                {
                    // PORT: §3.6 hot path: group.Members.Where(m => m is TableSymbol)
                    var tables = new ArrayList<Symbol>();
                    for (Symbol m : group.members())
                    {
                        if (m instanceof TableSymbol)
                        {
                            tables.add(m);
                        }
                    }
                    _binder._localScope.addSymbols(tables);
                }
            }
            finally
            {
                _binder._rowScope = oldScope;
            }

            bindNode(node);
        }
    }
}
