// Ported from: src/Kusto.Language/Parser/QueryGrammar.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import static org.graylog.kusto.language.parsing.LexicalTokenParsers.*;
import static org.graylog.kusto.language.parsing.SyntaxParsers.*;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import org.graylog.kusto.language.Aggregates;
import org.graylog.kusto.language.DiagnosticFacts;
import org.graylog.kusto.language.Functions;
import org.graylog.kusto.language.GlobalState;
import org.graylog.kusto.language.KustoFacts;
import org.graylog.kusto.language.ParseOptions;
import org.graylog.kusto.language.QueryOperatorParameter;
import org.graylog.kusto.language.QueryOperatorParameterValueKind;
import org.graylog.kusto.language.QueryOperatorParameters;
import org.graylog.kusto.language.editor.CompletionHint;
import org.graylog.kusto.language.editor.CompletionItem;
import org.graylog.kusto.language.editor.CompletionKind;
import org.graylog.kusto.language.editor.CompletionPriority;
import org.graylog.kusto.language.editor.CompletionRank;
import org.graylog.kusto.language.syntax.*;
import org.graylog.kusto.language.utils.Interlocked;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.symbols.SymbolMatch;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetChars;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Out;

/// <summary>
/// Parsers for the Kusto query grammar.
/// </summary>
public class QueryGrammar
{
    private QueryGrammar(ParseOptions options)
    {
        this.initialize(options);
    }

    /// <summary>
    /// Gets the <see cref="QueryGrammar"/> associated with the specified <see cref="GlobalState"/>.
    /// </summary>
    public static QueryGrammar from(GlobalState globals)
    {
        var cg = s_currentGrammar;
        if (cg != null && cg.Options.equalExceptForParseKind(globals.parseOptions()))
        {
            return cg.Grammar;
        }
        else
        {
            CachedGrammar newCachedGrammar;

            if (globals.cache() != null)
            {
                // try to access/store grammar from globals cache if there is one
                Out<CachedGrammar> cached = new Out<>(); // PORT: §3.3 out param
                if (!globals.cache().tryGetValue(CachedGrammar.class, cached))
                {
                    newCachedGrammar = globals.cache().getOrCreate(CachedGrammar.class, () -> // PORT: §3.10 GetOrCreate<T>(Func<T>)
                        new CachedGrammar(
                            new QueryGrammar(globals.parseOptions()),
                            globals.parseOptions()));
                }
                else
                {
                    newCachedGrammar = cached.value;
                }
            }
            else
            {
                // otherwise create a new grammar that corresponds the parse options
                newCachedGrammar = new CachedGrammar(
                    new QueryGrammar(globals.parseOptions()),
                    globals.parseOptions());
            }

            // PORT-BUG: the cache is keyed by type only, so a hit returns the grammar built for whatever options first populated
            // the cache; the options are not compared (mirrored; only the s_currentGrammar fast path checks them)
            // store recent grammar instance so we it can be used later
            Interlocked.compareExchange(S_CURRENT_GRAMMAR, newCachedGrammar, cg); // PORT: §3.13

            return newCachedGrammar.Grammar;
        }
    }

    private static class CachedGrammar
    {
        public final QueryGrammar Grammar;
        public final ParseOptions Options;
        public CachedGrammar(QueryGrammar grammar, ParseOptions options)
        {
            this.Grammar = grammar;
            this.Options = options;
        }
    }

    private static volatile CachedGrammar s_currentGrammar; // PORT: §3.13 CAS-published
    private static final VarHandle S_CURRENT_GRAMMAR = Interlocked.staticHandle(QueryGrammar.class, "s_currentGrammar", CachedGrammar.class); // PORT: §3.13

    private Parser2<LexicalToken, QueryBlock> queryBlock;
    public Parser2<LexicalToken, QueryBlock> queryBlock() { return this.queryBlock; }

    private Parser2<LexicalToken, Statement> statement;
    public Parser2<LexicalToken, Statement> statement() { return this.statement; }

    private Parser2<LexicalToken, Directive> directive;
    public Parser2<LexicalToken, Directive> directive() { return this.directive; }

    private Parser2<LexicalToken, SyntaxList1<SeparatedElement1<Statement>>> statementList;
    public Parser2<LexicalToken, SyntaxList1<SeparatedElement1<Statement>>> statementList() { return this.statementList; }

    private Parser2<LexicalToken, FunctionBody> functionBody;
    public Parser2<LexicalToken, FunctionBody> functionBody() { return this.functionBody; }

    private Parser2<LexicalToken, FunctionParameters> functionParameters;
    public Parser2<LexicalToken, FunctionParameters> functionParameters() { return this.functionParameters; }

    private Parser2<LexicalToken, QueryOperator> queryOperator;
    public Parser2<LexicalToken, QueryOperator> queryOperator() { return this.queryOperator; }

    private Parser2<LexicalToken, Expression> pipeExpression;
    public Parser2<LexicalToken, Expression> pipeExpression() { return this.pipeExpression; }

    private Parser2<LexicalToken, Expression> pipeSubExpression;
    public Parser2<LexicalToken, Expression> pipeSubExpression() { return this.pipeSubExpression; }

    private Parser2<LexicalToken, SyntaxList1<SeparatedElement1<Statement>>> macroExpandSubQuery;
    public Parser2<LexicalToken, SyntaxList1<SeparatedElement1<Statement>>> macroExpandSubQuery() { return this.macroExpandSubQuery; }

    private Parser2<LexicalToken, QueryOperator> followingPipeElementExpression;
    public Parser2<LexicalToken, QueryOperator> followingPipeElementExpression() { return this.followingPipeElementExpression; }

    private Parser2<LexicalToken, Expression> expression;
    public Parser2<LexicalToken, Expression> expression() { return this.expression; }

    private Parser2<LexicalToken, Expression> namedExpression;
    public Parser2<LexicalToken, Expression> namedExpression() { return this.namedExpression; }

    private Parser2<LexicalToken, Expression> unnamedExpression;
    public Parser2<LexicalToken, Expression> unnamedExpression() { return this.unnamedExpression; }

    private Parser2<LexicalToken, NameDeclaration> simpleNameDeclaration;
    public Parser2<LexicalToken, NameDeclaration> simpleNameDeclaration() { return this.simpleNameDeclaration; }

    private Parser2<LexicalToken, Expression> simpleNameDeclarationExpression;
    public Parser2<LexicalToken, Expression> simpleNameDeclarationExpression() { return this.simpleNameDeclarationExpression; }

    private Parser2<LexicalToken, NameDeclaration> bracketedNameDeclaration;
    public Parser2<LexicalToken, NameDeclaration> bracketedNameDeclaration() { return this.bracketedNameDeclaration; }

    private Parser2<LexicalToken, Name> identifierName;
    public Parser2<LexicalToken, Name> identifierName() { return this.identifierName; }

    private Parser2<LexicalToken, Name> bracketedName;
    public Parser2<LexicalToken, Name> bracketedName() { return this.bracketedName; }

    private Parser2<LexicalToken, Name> bracedName;
    public Parser2<LexicalToken, Name> bracedName() { return this.bracedName; }

    private Parser2<LexicalToken, TypeExpression> paramTypeExtended;
    public Parser2<LexicalToken, TypeExpression> paramTypeExtended() { return this.paramTypeExtended; }

    private Parser2<LexicalToken, SchemaTypeExpression> schemaType;
    public Parser2<LexicalToken, SchemaTypeExpression> schemaType() { return this.schemaType; }

    private Parser2<LexicalToken, Expression> simpleNameReference;
    public Parser2<LexicalToken, Expression> simpleNameReference() { return this.simpleNameReference; }

    private Parser2<LexicalToken, Expression> wildcardedNameReference;
    public Parser2<LexicalToken, Expression> wildcardedNameReference() { return this.wildcardedNameReference; }

    private Parser2<LexicalToken, SyntaxToken> wildcardedIdentifier;
    public Parser2<LexicalToken, SyntaxToken> wildcardedIdentifier() { return this.wildcardedIdentifier; }

    private Parser2<LexicalToken, Expression> literal;
    public Parser2<LexicalToken, Expression> literal() { return this.literal; }

    private Parser2<LexicalToken, Expression> stringLiteral;
    public Parser2<LexicalToken, Expression> stringLiteral() { return this.stringLiteral; }

    private Parser2<LexicalToken, Expression> jsonValue;
    public Parser2<LexicalToken, Expression> jsonValue() { return this.jsonValue; }

    private Parser2<LexicalToken, SyntaxList1<SeparatedElement1<Expression>>> literalList;
    public Parser2<LexicalToken, SyntaxList1<SeparatedElement1<Expression>>> literalList() { return this.literalList; }

    private Parser2<LexicalToken, SkippedTokens> skippedTokens;
    public Parser2<LexicalToken, SkippedTokens> skippedTokens() { return this.skippedTokens; }

    private static final Set<SyntaxKind> _extendedKeywordsAsIdentifiers = ListExtensions.toHashSetEx(KustoFacts.ExtendedKeywordsAsIdentifiers); // PORT: §3.17

    private static boolean isExtendedKeywordAsIdentifier(LexicalToken token)
    {
        return SyntaxFacts.isKeyword(token.kind()) && _extendedKeywordsAsIdentifiers.contains(token.kind()); // PORT: §3.5 extension method
    }

    private static boolean isKeywordAsIdentifier(LexicalToken token)
    {
        return SyntaxFacts.getCategory(token.kind()) == SyntaxCategory.Keyword && SyntaxFacts.canBeIdentifier(token.kind()); // PORT: §3.5 extension methods
    }

    private Parser2<LexicalToken, Expression> ExpressionCore; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> UnnamedExpressionCore; // PORT: §3.19 split
    private Parser2<LexicalToken, NameAndTypeDeclaration> NameAndTypeDeclarationCore; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> LiteralCore; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> StringOrCompoundStringLiteralCore; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> JsonValueCore; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> PrimaryExpressionCore; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> FunctionCallOrPathCore; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> ForkPipeOperatorCore; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> ForkPipeExpressionCore; // PORT: §3.19 split
    private Parser2<LexicalToken, Statement> LetStatementCore; // PORT: §3.19 split
    private Parser2<LexicalToken, Statement> DeclareQueryParametersStatementCore; // PORT: §3.19 split
    private Parser2<LexicalToken, FunctionParameter> FunctionParameterCore; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> PipeExpressionCore; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> PipeSubExpressionCore; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> ContextualSubExpressionCore; // PORT: §3.19 split
    private Parser2<LexicalToken, NameAndTypeDeclaration> NameAndTypeDeclaration; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> StringOrCompoundStringLiteral; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> PrimaryExpression; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> FunctionCallOrPath; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> ForkPipeOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> ForkPipeExpression; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> ContextualSubExpression; // PORT: §3.19 split
    private Parser2<LexicalToken, Statement> LetStatement; // PORT: §3.19 split
    private Parser2<LexicalToken, Statement> DeclareQueryParametersStatement; // PORT: §3.19 split
    private Parser2<LexicalToken, FunctionParameter> FunctionParameter; // PORT: §3.19 split
    private Parser2<LexicalToken, SyntaxToken> ScanIdentifierName; // PORT: §3.19 split
    private Parser<LexicalToken> ScanExtendedKeywordAsIdentifier; // PORT: §3.19 split
    private Parser2<LexicalToken, SyntaxToken> KeywordAsIdentifier; // PORT: §3.19 split
    private Parser2<LexicalToken, NameDeclaration> IdentifierNameDeclaration; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> IdentifierNameReference; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> ClientParameterReference; // PORT: §3.19 split
    private Parser<LexicalToken> ScanBracketedName; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> BracketedNameReference; // PORT: §3.19 split
    private Parser2<LexicalToken, NameDeclaration> ExtendedKeywordAsIdentifierNameDeclaration; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> KeywordAsIdentifierNameReference; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> NameTokenLiteral; // PORT: §3.19 split
    private Parser<LexicalToken> ScanSimpleName; // PORT: §3.19 split
    private Parser<LexicalToken> ScanExtendedName; // PORT: §3.19 split
    private Parser2<LexicalToken, NameDeclaration> ExtendedNameDeclaration; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> ExtendedNameDeclarationExpression; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> ExtendedNameReference; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> InvalidKeywordAsNameReference; // PORT: §3.19 split
    private Parser2<LexicalToken, TokenName> DashedName; // PORT: §3.19 split
    private Parser2<LexicalToken, TypeExpression> ParamType; // PORT: §3.19 split
    private Parser2<LexicalToken, TypeExpression> IdentifierTypeExpression; // PORT: §3.19 split
    private Parser2<LexicalToken, TypeExpression> InvalidParamType; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> StarExpression; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> BooleanLiteral; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> BooleanLiteralWithCompletion; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> LongLiteral; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> RealLiteral; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> RawGuidLiteral; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> DateTimeLiteral; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> TypeofLiteral; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> NumericLiteral; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> IdentifierOrKeywordTokenLiteral; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> DynamicLiteral; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> ForcedRealLiteral; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> AnyQueryOperatorParameterValue; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> AnyQueryOperatorParameterForcedRealValue; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> ParenthesizedExpression; // PORT: §3.19 split
    private Parser2<LexicalToken, NameDeclaration> RenameName; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> FunctionCall; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> RequiredFunctionCall; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> DotCompositeFunctionCall; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> BarePathElementSelector; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> InvocationExpression; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> PathElementSelector; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> BracketedEntityNamePathElementSelector; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> EntityPathExpression; // PORT: §3.19 split
    private Parser<LexicalToken> ScanQualifiedEntityStart; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> SimplePathExpression; // PORT: §3.19 split
    private Parser<LexicalToken> ScanWildcardedEntityReferenceOrFunctionCall; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> SimpleOrWildcardedEntityReference; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> WildcardedEntityReference; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> RequiredFunctionCallOrPath; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> LogicalOr; // PORT: §3.19 split
    private Parser2<LexicalToken, RowSchema> RowSchema; // PORT: §3.19 split
    private Parser2<LexicalToken, RowSchema> MandatoryRowSchema; // PORT: §3.19 split
    private Parser2<LexicalToken, EvaluateRowSchema> EvaluateRowSchema; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> DataTableExpression; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> ContextualDataTableExpression; // PORT: §3.19 split
    private Parser2<LexicalToken, ExternalDataWithClause> ExternalDataWithClause; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> ExternalDataExpression; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> InlineExternalTableExpression; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> ConsumeOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> CountOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> ExecuteAndCacheOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> ExtendOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> FacetOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> FilterOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> GetSchemaOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> AsOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, FindInClause> FindInClause; // PORT: §3.19 split
    private Parser2<LexicalToken, FindProjectClause> FindProjectClause; // PORT: §3.19 split
    private Parser2<LexicalToken, FindProjectClause> FindProjectAwayClause; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> FindOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> SearchOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> ForkOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> JoinOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> LookupOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> MakeSeriesOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, ToTypeOfClause> ToTypeOfClause; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> MvExpandOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> MvApplyOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> EvaluateOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> ParseOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> ParseWhereOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> ParseKvOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> PartitionOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> PartitionByOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> ProjectOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> ProjectAwayOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> ProjectByNamesOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> ProjectKeepOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> ProjectRenameOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> SampleOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> SampleDistinctOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> ReduceByOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> SummarizeOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> DistinctOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> TakeOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, OrderingClause> OrderingClause; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> SortExpression; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> SortOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> ProjectReorderOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> ScanOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> TopHittersOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> TopOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> TopNestedOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> UnionOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> SerializeOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> RangeOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> InvokeOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> RenderOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> PrintOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> AssertSchemaOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, Expression> EntityGroup; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> MacroExpandOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> MakeGraphOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> GraphMarkComponentsOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> GraphWhereNodesOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> GraphWhereEdgesOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> GraphToTableOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> GraphMatchOperator; // PORT: §3.19 split
    private Parser2<LexicalToken, QueryOperator> GraphShortestPathsOperator; // PORT: §3.19 split

    /// <summary>
    /// Constructs the grammar as a Parser
    /// </summary>
    private void initialize(ParseOptions options)
    {
        this.initialize_Forwards(); // PORT: §3.19 split
        this.initialize_Names(); // PORT: §3.19 split
        this.initialize_SchemaAndTypes(); // PORT: §3.19 split
        this.initialize_Literals(); // PORT: §3.19 split
        this.initialize_QueryOperatorParameters(); // PORT: §3.19 split
        this.initialize_Expressions(options); // PORT: §3.19 split
        this.initialize_QueryOperators_DataTableAndExternalData(); // PORT: §3.19 split
        this.initialize_QueryOperators_InlineExternalTable(); // PORT: §3.19 split
        this.initialize_QueryOperators_ConsumeThroughFindClauses(); // PORT: §3.19 split
        this.initialize_QueryOperators_FindSearchForkJoinLookupMakeSeries(); // PORT: §3.19 split
        this.initialize_QueryOperators_MvExpandMvApplyEvaluateParsePartition(); // PORT: §3.19 split
        this.initialize_QueryOperators_ProjectSampleReduceSummarizeSort(); // PORT: §3.19 split
        this.initialize_QueryOperators_ScanTopUnionRangeRenderPrint(); // PORT: §3.19 split
        this.initialize_QueryOperators_MacroExpandMakeGraphGraphMatch(); // PORT: §3.19 split
        this.initialize_QueryOperators_PipeAndQueryOperator(); // PORT: §3.19 split
        this.initialize_Statements(); // PORT: §3.19 split
        this.initialize_QueryBlock(); // PORT: §3.19 split
    }

    // PORT: §3.19 split
    private void initialize_Forwards()
    {
        // region Forwards

        this.expression =
                forward(() -> ExpressionCore)
                .withTag("<expression>");

        this.unnamedExpression =
                forward(() -> UnnamedExpressionCore)
                .withTag("<expression>");

        NameAndTypeDeclaration =
                forward(() -> NameAndTypeDeclarationCore)
                .withTag("<name-and-type>");

        this.literal =
                forward(() -> LiteralCore)
                .withTag("<literal>");

        StringOrCompoundStringLiteral =
                forward(() -> StringOrCompoundStringLiteralCore)
                .withTag("<string-literal>");

        this.jsonValue =
                forward(() -> JsonValueCore)
                .withTag("<json-value>");

        PrimaryExpression =
                forward(() -> PrimaryExpressionCore)
                .withTag("<primary-expression>");

        FunctionCallOrPath =
                forward(() -> FunctionCallOrPathCore)
                .withTag("<function-call-or-path>");

        ForkPipeOperator =
                forward(() -> ForkPipeOperatorCore)
                .withTag("<fork-pipe-operator>");

        ForkPipeExpression =
                forward(() -> ForkPipeExpressionCore)
                .withTag("<fork-pipe-expression>");

        this.pipeExpression =
                forward(() -> PipeExpressionCore)
                .withTag("<pipe-expression>");

        this.pipeSubExpression =
                forward(() -> PipeSubExpressionCore)
                .withTag("<pipe-sub-expression>");

        this.macroExpandSubQuery =
                forward(() -> this.statementList)
                .withTag("<macro-expand-subquery>");

        ContextualSubExpression =
                forward(() -> ContextualSubExpressionCore)
                .withTag("<contextual-sub-expression>");

        LetStatement =
                forward(() -> LetStatementCore)
                .withTag("<let>");

        DeclareQueryParametersStatement =
                forward(() -> DeclareQueryParametersStatementCore)
                .withTag("<query-parameters>");

        FunctionParameter =
                forward(() -> FunctionParameterCore)
                .withTag("<function-parameter>");

        // endregion
    }

    // PORT: §3.19 split
    private void initialize_Names()
    {
        // region Names

        ScanIdentifierName =
                token(SyntaxKind.IdentifierToken);

        ScanExtendedKeywordAsIdentifier =
                match(t -> isExtendedKeywordAsIdentifier(t))
                .withTag("<extendedKeywordAsIdentifier>");

        var ScanKeywordAsIdentifier =
                match(t -> isKeywordAsIdentifier(t))
                .withTag("<keywordAsIdentifier>");

        KeywordAsIdentifier =
                match(t -> isKeywordAsIdentifier(t), t -> SyntaxToken.from(t))
                .withTag("<keywordAsIdentifier>");

        var IdentifierOrKeyword =
                first(token(SyntaxKind.IdentifierToken), KeywordAsIdentifier);

        this.identifierName =
                rule(IdentifierOrKeyword,
                    token -> (Name)new TokenName(token));

        this.bracketedName =
                rule(
                    token(SyntaxKind.OpenBracketToken),
                    StringOrCompoundStringLiteral,
                    token(SyntaxKind.CloseBracketToken),
                    (open, name, close) -> (Name)new BracketedName(open, name, close));

        this.bracedName =
                // only match rule if name parts are adjacent (no whitespace)
                match(
                    // consume (scan)
                    (Source<LexicalToken> source, int start) ->
                    {
                        var pos = start;

                        var token = source.peek(pos);
                        if (token == null || token.kind() != SyntaxKind.OpenBraceToken)    
                            return -1;
                        pos++;

                        token = source.peek(pos);
                        if (token == null
                            || (token.kind() != SyntaxKind.IdentifierToken && SyntaxFacts.getCategory(token.kind()) != SyntaxCategory.Keyword) // PORT: §3.5 extension method
                            || token.trivia().length() > 0)
                            return -1;
                        pos++;

                        token = source.peek(pos);
                        if (token != null 
                            && token.kind() == SyntaxKind.OpenBracketToken
                            && token.trivia().length() == 0)
                        {
                            pos++;

                            token = source.peek(pos);
                            if (token != null
                                && token.kind() == SyntaxKind.MinusToken
                                && token.trivia().length() == 0)
                                pos++;

                            token = source.peek(pos);
                            if (token == null
                                || token.kind() != SyntaxKind.LongLiteralToken
                                || token.trivia().length() > 0)
                                return -1;
                            pos++;

                            token = source.peek(pos);
                            if (token == null
                                || token.kind() != SyntaxKind.CloseBracketToken
                                || token.trivia().length() > 0)
                                return -1;
                            pos++;
                        }

                        token = source.peek(pos);
                        if (token == null
                            || token.kind() != SyntaxKind.CloseBraceToken
                            || token.trivia().length() > 0)
                            return -1;
                        pos++;

                        return pos - start;
                    },
                    // produce (convert)
                    (Source<LexicalToken> source, int start, int length) ->
                    {
                        var open = SyntaxToken.from(source.peek(start));
                        var nameText = getCombinedTokenText(source, start + 1, length - 2);
                        var nameToken = SyntaxToken.identifier("", nameText);
                        var close = SyntaxToken.from(source.peek(start + length - 1));
                        return (Name)new BracedName(open, nameToken, close);
                    });

        IdentifierNameDeclaration =
                rule(
                    token(SyntaxKind.IdentifierToken),
                    id -> (NameDeclaration)new NameDeclaration(id))
                    .withTag("<identifer>");

        IdentifierNameReference =
                rule(
                    token(SyntaxKind.IdentifierToken),
                    id -> (Expression)new NameReference(id))
                    .withTag("<identifer>");

        ClientParameterReference =
                rule(
                    this.bracedName,
                    name -> (Expression)new NameReference(name, SymbolMatch.None))
                .withTag("<client-parameter>");

        ScanBracketedName =
                and(token(SyntaxKind.OpenBracketToken),
                    oneOrMore(token(SyntaxKind.StringLiteralToken)),
                    optional(token(SyntaxKind.CloseBracketToken)));

        this.bracketedNameDeclaration =
                rule(
                    token(SyntaxKind.OpenBracketToken),
                    required(StringOrCompoundStringLiteral, (source, start) -> createMissingStringLiteral(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseBracketToken),
                    (openBracket, name, closeBracket) ->
                        (NameDeclaration)new NameDeclaration(new BracketedName(openBracket, name, closeBracket)));

        BracketedNameReference =
                rule(
                    token(SyntaxKind.OpenBracketToken),
                    required(StringOrCompoundStringLiteral, (source, start) -> createMissingStringLiteral(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseBracketToken),
                    (openBracket, name, closeBracket) ->
                        (Expression)new NameReference(new BracketedName(openBracket, name, closeBracket)));

        var KeywordAsIdentifierNameDeclaration =
                asIdentifierNameDeclaration(KeywordAsIdentifier)
                .withTag("<keywordAsIdentifier>");

        var ExtendedKeywordAsIdentifierToken =
                match(t -> isExtendedKeywordAsIdentifier(t), lt -> SyntaxToken.from(lt));

        ExtendedKeywordAsIdentifierNameDeclaration =
                asIdentifierNameDeclaration(ExtendedKeywordAsIdentifierToken)
                .withTag("<extendedKeywordAsIdentifierNameDeclaration>");

        KeywordAsIdentifierNameReference =
                asIdentifierNameReference(KeywordAsIdentifier)
                .withTag("<keywordAsIdentifierNameReference>");

        var ExtendedKeywordAsIdentifierNameReference =
                asIdentifierNameReference(ExtendedKeywordAsIdentifierToken)
                .withTag("<extendedKeywordAsIdentifierNameReference>");

        NameTokenLiteral =
                asTokenLiteral(first(token(SyntaxKind.IdentifierToken), KeywordAsIdentifier));

        ScanSimpleName =
                or(ScanIdentifierName, ScanBracketedName, ScanKeywordAsIdentifier);

        ScanExtendedName =
                or(ScanIdentifierName, ScanBracketedName, ScanExtendedKeywordAsIdentifier);

        this.simpleNameDeclaration =
                first(
                    IdentifierNameDeclaration,
                    this.bracketedNameDeclaration,
                    KeywordAsIdentifierNameDeclaration)
                .withTag("<name>");

        this.simpleNameDeclarationExpression =
                rule(this.simpleNameDeclaration, nd -> (Expression)nd);

        this.simpleNameReference =
                first(
                    IdentifierNameReference,
                    BracketedNameReference,
                    KeywordAsIdentifierNameReference,
                    ClientParameterReference)
                .withTag("<name>");

        ExtendedNameDeclaration =
                first(
                    IdentifierNameDeclaration,
                    this.bracketedNameDeclaration,
                    ExtendedKeywordAsIdentifierNameDeclaration)
                .withTag("<name>");

        ExtendedNameDeclarationExpression =
                rule(ExtendedNameDeclaration, nd -> (Expression)nd);

        ExtendedNameReference =
                first(
                    IdentifierNameReference,
                    BracketedNameReference,
                    ExtendedKeywordAsIdentifierNameReference,
                    ClientParameterReference)
                .withTag("<name>");

        InvalidKeywordAsNameReference =
                match(
                    (source, start) ->
                        QueryParser.isKeywordInNamePosition(source, start) ? 1 : -1,
                    (source, start, length) ->
                        (Expression)new NameReference(
                            new TokenName(SyntaxToken.from(source.peek(start))),
                            List.of( DiagnosticFacts.getNameRequiresBrackets(source.peek(start).text()) )) // PORT: §3.17 new[] -> List.of
                    );

        DashedName =
                match((source, start) ->
                    QueryParser.scanDashedName(source, start),
                (source, start, length) ->
                    new TokenName(SyntaxParsers.produceSyntaxToken(source, start, length))
                );

        // endregion
    }

    // PORT: §3.19 split
    private void initialize_SchemaAndTypes()
    {
        // region Schema and Types

        var ParamTypeToken =
                first(
                    token(SyntaxKind.BoolKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.BooleanKeyword).hide(),
                    token(SyntaxKind.DateKeyword).hide(),
                    token(SyntaxKind.DateTimeKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.DecimalKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.DoubleKeyword).hide(),
                    token(SyntaxKind.DynamicKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.GuidKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.IntKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.Int64Keyword).hide(),
                    token(SyntaxKind.Int8Keyword).hide(),
                    token(SyntaxKind.LongKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.RealKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.StringKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.TimeKeyword).hide(),
                    token(SyntaxKind.TimespanKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.UniqueIdKeyword).hide()
                    );

        ParamType =
                asPrimitiveTypeExpression(ParamTypeToken).withTag("<param-type>");

        var ParamTypeExtendedToken =
                first(
                    token(SyntaxKind.BoolKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.BooleanKeyword).hide(),
                    token(SyntaxKind.DateKeyword).hide(),
                    token(SyntaxKind.DateTimeKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.DecimalKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.DoubleKeyword).hide(),
                    token(SyntaxKind.DynamicKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.FloatKeyword).hide(),
                    token(SyntaxKind.GuidKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.IntKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.Int16Keyword).hide(),
                    token(SyntaxKind.Int32Keyword).hide(),
                    token(SyntaxKind.Int64Keyword).hide(),
                    token(SyntaxKind.Int8Keyword).hide(),
                    token(SyntaxKind.LongKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.RealKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.DecimalKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.StringKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.TimeKeyword).hide(),
                    token(SyntaxKind.TimespanKeyword, CompletionKind.ScalarType),
                    token(SyntaxKind.UIntKeyword).hide(),
                    token(SyntaxKind.UInt16Keyword).hide(),
                    token(SyntaxKind.UInt32Keyword).hide(),
                    token(SyntaxKind.UInt64Keyword).hide(),
                    token(SyntaxKind.UInt8Keyword).hide(),
                    token(SyntaxKind.ULongKeyword).hide(),
                    token(SyntaxKind.UniqueIdKeyword).hide()
                    );

        this.paramTypeExtended =
                asPrimitiveTypeExpression(ParamTypeExtendedToken);

        IdentifierTypeExpression =
                asPrimitiveTypeExpression(
                    first(token(SyntaxKind.IdentifierToken), KeywordAsIdentifier));

        InvalidParamType =
                convert(
                    first(
                        ParamTypeExtendedToken,
                        token(SyntaxKind.IdentifierToken),
                        match(tk -> SyntaxFacts.isKeyword(tk.kind()))), // PORT: §3.5 extension method
                    (token) -> (TypeExpression)new PrimitiveTypeExpression(SyntaxToken.from(token), List.of( DiagnosticFacts.getInvalidTypeName(token.text()) ))); // PORT: §3.17 new[] -> List.of

        var ScanSchemaTypeStart =
                and(
                    ScanExtendedName,
                    token(SyntaxKind.ColonToken),
                    token(SyntaxKind.OpenParenToken));

        StarExpression =
                rule(
                    token(SyntaxKind.AsteriskToken).hide(),
                    (star) -> (Expression)new StarExpression(star));

        var SchemaAsteriskType =
                rule(
                    token(SyntaxKind.OpenParenToken),
                    StarExpression,
                    token(SyntaxKind.CloseParenToken),

                    (openParen, star, closeParen) ->
                        new SchemaTypeExpression(
                            openParen,
                            new SyntaxList1<SeparatedElement1<Expression>>(new SeparatedElement1<Expression>(star)),
                            closeParen));

        var SchemaMultipartType =
                    rule(
                        token(SyntaxKind.OpenParenToken),
                        SyntaxParsers.<Expression>commaList( // PORT: §3.10 explicit type arguments
                            rule(NameAndTypeDeclaration, nat -> (Expression)nat),
                            (source, start) -> createMissingNameAndTypeDeclaration(source, start), false, // PORT: §3.8 method group
                            true), // PORT: §3.12 named arguments
                        requiredToken(SyntaxKind.CloseParenToken),

                        (openParen, columns, closeParen) ->
                            new SchemaTypeExpression(openParen, columns, closeParen));

        this.schemaType =
                first(
                    if_(and(token(SyntaxKind.OpenParenToken), token(SyntaxKind.AsteriskToken)), SchemaAsteriskType),
                    SchemaMultipartType);

        NameAndTypeDeclarationCore =
                first(
                    rule( // error case: missing name
                        token(SyntaxKind.ColonToken),
                        required(first(ParamType, InvalidParamType), (source, start) -> createMissingType(source, start)), // PORT: §3.8 method group
                        (colon, type) -> new NameAndTypeDeclaration(createMissingNameDeclaration(), colon, type)),

                    if_(ScanSchemaTypeStart,
                        rule(
                            ExtendedNameDeclaration,
                            token(SyntaxKind.ColonToken),
                            this.schemaType,
                            (name, colon, type) -> new NameAndTypeDeclaration(name, colon, type))),

                    rule(
                        ExtendedNameDeclaration,
                        requiredToken(SyntaxKind.ColonToken),
                        required(first(ParamType, InvalidParamType), (source, start) -> createMissingType(source, start)), // PORT: §3.8 method group
                        (name, colon, type) -> new NameAndTypeDeclaration(name, colon, type)));

        // endregion
    }

    // PORT: §3.19 split
    private void initialize_Literals()
    {
        // region Literals

        BooleanLiteral =
                rule(
                    token(SyntaxKind.BooleanLiteralToken),
                    token -> (Expression)new LiteralExpression(SyntaxKind.BooleanLiteralExpression, token))
                .withTag("<bool-literal>");

        BooleanLiteralWithCompletion =
                first(
                    if_(or(token("true"), token("false")), BooleanLiteral), // completion will see ScanToken's
                    BooleanLiteral);

        LongLiteral =
                withCompletion(rule(
                    token(SyntaxKind.LongLiteralToken),
                    token -> (Expression)new LiteralExpression(SyntaxKind.LongLiteralExpression, token))
                .withTag("<long-literal>"), new CompletionItem(CompletionKind.ScalarPrefix, "long()", "long(", ")", "long", null, CompletionRank.Function)); // PORT: §3.12 named arguments; §3.5 extension method

        RealLiteral =
                withCompletion(rule(
                    token(SyntaxKind.RealLiteralToken),
                    token -> (Expression)new LiteralExpression(SyntaxKind.RealLiteralExpression, token))
                .withTag("<real-literal>"), new CompletionItem(CompletionKind.ScalarPrefix, "real()", "real(", ")", "real", null, CompletionRank.Function), // PORT: §3.12 named arguments
                    new CompletionItem(CompletionKind.ScalarPrefix, "double()", "double(", ")", "double", null, CompletionRank.Function)); // PORT: §3.12 named arguments; §3.5 extension method

        var DecimalLiteral =
                withCompletion(rule(
                    token(SyntaxKind.DecimalLiteralToken),
                    token -> (Expression)new LiteralExpression(SyntaxKind.DecimalLiteralExpression, token))
                .withTag("<decimal-literal>"), new CompletionItem(CompletionKind.ScalarPrefix, "decimal()", "decimal(", ")", "decimal", null, CompletionRank.Function)); // PORT: §3.12 named arguments; §3.5 extension method

        var IntLiteral =
                withCompletion(rule(
                    token(SyntaxKind.IntLiteralToken),
                    token -> (Expression)new LiteralExpression(SyntaxKind.IntLiteralExpression, token))
                .withTag("<int-literal>"), new CompletionItem(CompletionKind.ScalarPrefix, "int()", "int(", ")", "int", null, CompletionRank.Function)); // PORT: §3.12 named arguments; §3.5 extension method

        var GuidLiteral =
                withCompletion(rule(
                    token(SyntaxKind.GuidLiteralToken),
                    token -> (Expression)new LiteralExpression(SyntaxKind.GuidLiteralExpression, token))
                .withTag("<guid-literal>"), new CompletionItem(CompletionKind.ScalarPrefix, "guid()", "guid(", ")", "guid", null, CompletionRank.Function)); // PORT: §3.12 named arguments; §3.5 extension method

        RawGuidLiteral =
                rule(
                    token(SyntaxKind.RawGuidLiteralToken),
                    token -> (Expression)new LiteralExpression(SyntaxKind.GuidLiteralExpression, token))
                .withTag("<raw-guid-literal>");

        DateTimeLiteral =
                withCompletion(rule(token(SyntaxKind.DateTimeLiteralToken),
                    token -> (Expression)new LiteralExpression(SyntaxKind.DateTimeLiteralExpression, token))
                .withTag("<datetime-literal>"), new CompletionItem(CompletionKind.ScalarPrefix, "datetime()", "datetime(", ")", "datetime", null, CompletionRank.Function)); // PORT: §3.12 named arguments; §3.5 extension method

        var TimespanLiteral =
                withCompletion(rule(token(SyntaxKind.TimespanLiteralToken),
                    token -> (Expression)new LiteralExpression(SyntaxKind.TimespanLiteralExpression, token))
                .withTag("<timespan-literal>"), new CompletionItem(CompletionKind.ScalarPrefix, "timespan()", "timespan(", ")", "timespan", null, CompletionRank.Function)); // PORT: §3.12 named arguments; §3.5 extension method

        this.stringLiteral =
                rule(
                    token(SyntaxKind.StringLiteralToken),
                    token -> (Expression)new LiteralExpression(SyntaxKind.StringLiteralExpression, token))
                .withTag("<string-literal>");

        var TypeofElement =
                first(
                    rule(NameAndTypeDeclaration, nat -> (Expression)nat),
                    StarExpression);

        var ScanTypeOfScalar =
                and(token(SyntaxKind.TypeOfKeyword).hide(),
                    token(SyntaxKind.OpenParenToken),
                    or(
                        ParamType,
                        this.paramTypeExtended.hide(),
                        and(token(SyntaxKind.IdentifierToken).hide(), token(SyntaxKind.CloseParenToken))));

        var ScanTypeOfTabular =
                and(token(SyntaxKind.TypeOfKeyword).hide(), token(SyntaxKind.OpenParenToken));

        TypeofLiteral =
                first(
                    if_(ScanTypeOfScalar,
                        rule(
                            token(SyntaxKind.TypeOfKeyword).hide(),
                            token(SyntaxKind.OpenParenToken),
                            first(ParamType, this.paramTypeExtended.hide(), IdentifierTypeExpression.hide()),
                            requiredToken(SyntaxKind.CloseParenToken),
                            (keyword, openParen, type, closeParen) ->
                                (Expression)new TypeOfLiteralExpression(
                                    keyword,
                                    openParen,
                                    new SyntaxList1<SeparatedElement1<Expression>>(new SeparatedElement1<Expression>(type)),
                                    closeParen))),
                    if_(ScanTypeOfTabular,
                        rule(
                            token(SyntaxKind.TypeOfKeyword).hide(),
                            requiredToken(SyntaxKind.OpenParenToken),
                            commaList(TypeofElement, (source, start) -> createMissingType(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                            requiredToken(SyntaxKind.CloseParenToken),
                            (keyword, openParen, list, closeParen) ->
                                (Expression)new TypeOfLiteralExpression(keyword, openParen, list, closeParen)
                            )))
                .withTag("<typeof-literal>");

        StringOrCompoundStringLiteralCore =
                oneOrMore(
                    token(SyntaxKind.StringLiteralToken),
                    list -> list.size() == 1
                        ? (Expression)new LiteralExpression(SyntaxKind.StringLiteralExpression, list.get(0)) // PORT: §3.1 indexer
                        : new CompoundStringLiteralExpression(new SyntaxList1<SyntaxToken>(list)))
                .withTag("<string-literal>");

        var IsSignedNumericLiteral =
                match((source, start) ->
                {
                    var sign = source.peek(start);
                    var number = source.peek(start + 1);
                    if (sign != null && number != null
                        && (sign.kind() == SyntaxKind.PlusToken || sign.kind() == SyntaxKind.MinusToken)
                        && number.trivia().length() == 0
                        && (number.kind() == SyntaxKind.LongLiteralToken
                            || number.kind() == SyntaxKind.RealLiteralToken
                            || number.kind() == SyntaxKind.TimespanLiteralToken)
                        && number.text().length() > 0 && DotNetChars.isDigit(number.text().charAt(0))) // PORT: §5.1 char.IsDigit
                    {
                        return 2;
                    }
                    else
                    {
                        return -1;
                    }
                });

        var SignedNumericLiteral =
                if_(IsSignedNumericLiteral,
                    rule(
                        first(token(SyntaxKind.MinusToken), token(SyntaxKind.PlusToken)).hide(),
                        first(LongLiteral, RealLiteral, TimespanLiteral),
                        (sign, expr) ->
                        {
                            // combine sign and literal into single literal token and expression
                            var lit = (LiteralExpression)expr;
                            var combinedToken = SyntaxToken.literal(sign.trivia(), sign.text() + lit.token().text(), lit.token().kind());
                            return (Expression)new LiteralExpression(lit.kind(), combinedToken);
                        }));

        NumericLiteral =
                first(
                    LongLiteral,
                    IntLiteral,
                    RealLiteral,
                    DateTimeLiteral,
                    TimespanLiteral,
                    SignedNumericLiteral)
                .withTag("<numeric-constant>");

        var IdentifierTokenLiteral =
                asTokenLiteral(token(SyntaxKind.IdentifierToken)).withTag("<identifier>");

        var KeywordTokenLiteral =
                asTokenLiteral(KeywordAsIdentifier).withTag("<keyword>");

        IdentifierOrKeywordTokenLiteral =
                first(IdentifierTokenLiteral, KeywordTokenLiteral);

        var NullLiteralExpression =
                withCompletion(rule(
                    token("null"),
                    (token) -> (Expression)new LiteralExpression(SyntaxKind.NullLiteralExpression, token)), new CompletionItem(CompletionKind.Syntax, "null")); // PORT: §3.5 extension method

        var JsonPair =
                first(
                    rule(token(SyntaxKind.StringLiteralToken), requiredToken(SyntaxKind.ColonToken), required(this.jsonValue, (source, start) -> createMissingJsonValue(source, start)), // PORT: §3.8 method group
                        (name, colon, value) ->
                            new JsonPair(name, colon, value)),
                    rule(token(SyntaxKind.ColonToken).hide(), required(this.jsonValue, (source, start) -> createMissingJsonValue(source, start)), // PORT: §3.8 method group
                        (colonToken, value) ->
                            new JsonPair(createMissingToken(SyntaxKind.StringLiteralToken, DiagnosticFacts.getMissingString()), colonToken, value)),
                    rule(this.jsonValue.hide(),
                        (value) ->
                            new JsonPair(createMissingToken(SyntaxKind.StringLiteralToken, DiagnosticFacts.getMissingString()), createMissingToken(SyntaxKind.ColonToken), value)))
                .withTag("<json-pair>");

        var JsonObject =
                rule(
                    token(SyntaxKind.OpenBraceToken),
                    commaList(JsonPair, (source, start) -> createMissingJsonPair(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseBraceToken),
                    (openBrace, list, closeBrace) ->
                        (Expression)new JsonObjectExpression(openBrace, list, closeBrace))
                .withTag("<json-object>");

        var JsonArray =
                rule(
                    token(SyntaxKind.OpenBracketToken),
                    commaList(this.jsonValue, (source, start) -> createMissingJsonValue(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseBracketToken),
                    (openBracket, values, closeBracket) ->
                        (Expression)new JsonArrayExpression(openBracket, values, closeBracket))
                .withTag("<json-array>");

        DynamicLiteral =
                withCompletion(rule(
                    token(SyntaxKind.DynamicKeyword, CompletionKind.ScalarPrefix),
                    requiredToken(SyntaxKind.OpenParenToken),
                    required(first(NullLiteralExpression, this.jsonValue), (source, start) -> createMissingJsonValue(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseParenToken),
                    (dynamicKeyword, openParen, value, closeParen) ->
                        (Expression)new DynamicExpression(dynamicKeyword, openParen, value, closeParen))
                .withTag("<dynamic-literal>"), new CompletionItem(CompletionKind.ScalarPrefix, "dynamic()", "dynamic(", ")", "dynamic", null, CompletionRank.Function)); // PORT: §3.12 named arguments; §3.5 extension method

        var JsonNumber =
                first(
                    rule(
                        token(SyntaxKind.MinusToken).hide(),
                        first(LongLiteral, RealLiteral),
                        (unaryOp, value) -> (Expression)new PrefixUnaryExpression(SyntaxKind.UnaryMinusExpression, unaryOp, value)),
                    LongLiteral,
                    RealLiteral);

        JsonValueCore =
                first(
                    StringOrCompoundStringLiteral,
                    JsonNumber,
                    TimespanLiteral,
                    BooleanLiteral,
                    DateTimeLiteral,
                    GuidLiteral,
                    DecimalLiteral,
                    DynamicLiteral,
                    NullLiteralExpression,
                    JsonObject,
                    JsonArray,
                    ClientParameterReference);

        LiteralCore =
                first(
                    StringOrCompoundStringLiteral,
                    BooleanLiteral,
                    LongLiteral,
                    RealLiteral,
                    DecimalLiteral,
                    IntLiteral,
                    GuidLiteral,
                    rule(token(SyntaxKind.RawGuidLiteralToken),
                        tk -> (Expression)new LiteralExpression(SyntaxKind.GuidLiteralExpression, tk, List.of( DiagnosticFacts.getRawGuidLiteralNotAllowed() ))), // PORT: §3.17 new[] -> List.of
                    DateTimeLiteral,
                    TimespanLiteral,
                    SignedNumericLiteral,
                    DynamicLiteral,
                    TypeofLiteral,
                    ClientParameterReference)
                .withTag("<literal>");

        var ForcedSignedRealLiteral =
                if_(IsSignedNumericLiteral,
                    rule(
                        first(token(SyntaxKind.MinusToken), token(SyntaxKind.PlusToken)).hide(),
                        first(LongLiteral, RealLiteral),
                        (sign, expr) ->
                        {
                            // combine sign and literal into single literal token and expression
                            var lit = (LiteralExpression)expr;
                            var combinedToken = SyntaxToken.literal(sign.trivia(), sign.text() + lit.token().text(), lit.token().kind());
                            return (Expression)new LiteralExpression(SyntaxKind.RealLiteralExpression, combinedToken);
                        }));

        // a numeric literal that is always encoded as a double/real literal expression

        ForcedRealLiteral =
                first(
                    rule(
                        first(token(SyntaxKind.LongLiteralToken), token(SyntaxKind.IntLiteralToken), token(SyntaxKind.RealLiteralToken), token(SyntaxKind.DecimalLiteralToken)),
                        token -> (Expression)new LiteralExpression(SyntaxKind.RealLiteralExpression, token)),
                    ForcedSignedRealLiteral)
                .withTag("<forced-real-literal>");

        // endregion
    }

    // PORT: §3.19 split
    private void initialize_QueryOperatorParameters()
    {
        // region Query Operator Parameters

        AnyQueryOperatorParameterValue =
                first(
                    this.literal.hide(),
                    IdentifierOrKeywordTokenLiteral,
                    this.simpleNameReference);

        AnyQueryOperatorParameterForcedRealValue =
                first(
                    ForcedRealLiteral.hide(),
                    this.literal.hide(),
                    IdentifierOrKeywordTokenLiteral,
                    this.simpleNameReference);

        // constructs a parser for query operator parameter lists

        // constructs a parser for comma separated query operator parameter lists

        // endregion
    }

    // PORT: §3.19 split
    private void initialize_Expressions(ParseOptions options)
    {
        // region Expressions

        ParenthesizedExpression =
                rule(
                    token(SyntaxKind.OpenParenToken),
                    required(this.expression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseParenToken),
                    (openParen, expression, closeParen) ->
                        (Expression)new ParenthesizedExpression(openParen, expression, closeParen));

        var ScanRenameName = or(
                ScanIdentifierName,
                ScanExtendedKeywordAsIdentifier,
                ScanBracketedName);

        var ScanRenameList =
                and(token(SyntaxKind.OpenParenToken),
                    zeroOrMore(and(ScanRenameName, optional(token(SyntaxKind.CommaToken)))),
                    optional(token(SyntaxKind.CloseParenToken)));

        var RenameNameDeclaration =
                first(
                    IdentifierNameDeclaration,
                    ExtendedKeywordAsIdentifierNameDeclaration,
                    this.bracketedNameDeclaration)
                .withTag("<name>");

        RenameName =
                ExtendedNameDeclaration;

        var RenameList =
                rule(
                    token(SyntaxKind.OpenParenToken),
                    commaList(RenameName, (source, start) -> createMissingNameDeclaration(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                    requiredToken(SyntaxKind.CloseParenToken),
                    (openParen, list, closeParen) ->
                        new RenameList(openParen, list, closeParen));

        this.namedExpression =
                first(
                    if_(and(RenameName, token(SyntaxKind.EqualToken)),
                        rule(RenameName, token(SyntaxKind.EqualToken), required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                            (name, equals, expr) ->
                                (Expression)new SimpleNamedExpression(name, equals, expr))),

                    // special case for invalid named-expression names
                    // PORT-BUG: D33 upstream grammar-mode bug: scanDashedName returns 0 for a non-name token, so a bare '=' in
                    // name position yields an IdentifierToken "=" (KS228) AND the EqualToken at the same offset (print f(=) -> print f(==)).
                    if_(and(DashedName, token(SyntaxKind.EqualToken)),
                        rule(DashedName, token(SyntaxKind.EqualToken), required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                            (name, equals, expr) ->
                                (Expression)new SimpleNamedExpression(
                                    new NameDeclaration(name, List.of( DiagnosticFacts.getNameRequiresBrackets(name.name().text()) )), // PORT: §3.17 new[] -> List.of
                                    equals, expr))),

                    if_(and(ScanRenameList, token(SyntaxKind.EqualToken)),
                        rule(RenameList, token(SyntaxKind.EqualToken), required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                            (list, equals, expr) ->
                                (Expression)new CompoundNamedExpression(list, equals, expr))),

                    this.unnamedExpression)
                .withTag("<expression>");

        var Argument =
                first(
                    if_(and(token(SyntaxKind.AsteriskToken), or(token(SyntaxKind.CloseParenToken), token(SyntaxKind.CommaToken))),
                        StarExpression),
                    this.namedExpression);

        var FunctionArgumentList =
                rule(
                    token(SyntaxKind.OpenParenToken),
                    commaList(Argument, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseParenToken),
                    (openParen, list, closeParen) -> new ExpressionList(openParen, list, closeParen));

        // PORT: §3.6 Functions.All.Select(f => f.Name).Concat(Aggregates.All.Select(f => f.Name)).ToArray()
            List<String> knownFunctionNames = new ArrayList<>();
            for (var f : Functions.All)
            {
                knownFunctionNames.add(f.name());
            }
            for (var f : Aggregates.All)
            {
                knownFunctionNames.add(f.name());
            }

        // some built-in function names are not identifiers so we add the ones we know about here.

        var ScanKnownFunctionNames =
                tokenText(knownFunctionNames) // PORT: §2.5 overload on IReadOnlyList<string>
                .hide();

        var KnownFunctionNameReference =
                asIdentifierNameReference(tokenText(knownFunctionNames).hide()); // PORT: §2.5 overload on IReadOnlyList<string>

        var ScanFunctionCall =
                and(or(ScanIdentifierName, ScanBracketedName, ScanKnownFunctionNames), token(SyntaxKind.OpenParenToken));

        var FunctionCallNames =
                first(this.simpleNameReference, KnownFunctionNameReference);

        FunctionCall =
                rule(
                    FunctionCallNames,
                    FunctionArgumentList,
                    (name, arguments) ->
                        (Expression)new FunctionCallExpression((NameReference)name, arguments))
                .withTag("<FunctionCall>");

        RequiredFunctionCall =
                required(
                    first(
                        FunctionCall,
                        rule(FunctionCallNames,
                            (name) -> (Expression)new FunctionCallExpression((NameReference)name, createMissingArgumentList()))),
                    (source, start) -> createMissingFunctionCallExpression(source, start)); // PORT: §3.8 method group

        DotCompositeFunctionCall =
                applyZeroOrMore(
                    FunctionCall,
                    _left ->
                        rule(
                            _left,
                            token(SyntaxKind.DotToken),
                            if_(ScanFunctionCall, FunctionCall),
                            (left, op, right) ->
                                (Expression)new PathExpression(left, op, right)));

        var AtTokenSelector =
                rule(token(SyntaxKind.AtToken), token -> (Expression)new AtExpression(token));

        var SpecialKeywordsAsIdentifierName =
                rule(token(KustoFacts.SpecialKeywordsAsIdentifiers),
                    tk -> (Expression)new NameReference(new TokenName(tk))).hide();

        // this is unquoted name after dot in dotted path

        BarePathElementSelector =
                first(
                    AtTokenSelector,
                    IdentifierNameReference,
                    KeywordAsIdentifierNameReference,
                    SpecialKeywordsAsIdentifierName,
                    ClientParameterReference
                    );

        // wild cards can use any keyword (but will need an asterisk somewhere)

        var ScanWildcard =
                and(
                    first(
                        and(
                            match(t -> t.kind() == SyntaxKind.IdentifierToken || isExtendedKeywordAsIdentifier(t)), // can have leading trivia
                            match(t -> t.kind() == SyntaxKind.AsteriskToken && (t.trivia().length() == 0) || options.allowNonAdjacentWildcardParts())),
                        token(SyntaxKind.AsteriskToken)), // can have leading trivia
                    zeroOrMore(
                        match(t -> (t.kind() == SyntaxKind.IdentifierToken
                                || t.kind() == SyntaxKind.LongLiteralToken
                                || t.kind() == SyntaxKind.AsteriskToken
                                || isExtendedKeywordAsIdentifier(t))
                                && (t.trivia().length() == 0 || options.allowNonAdjacentWildcardParts()))));

        this.wildcardedIdentifier =
                convert(
                    ScanWildcard.hide(),
                    (Source<LexicalToken> source, int start, int len) ->
                    {
                        var trivia = source.peek(start).trivia();
                        var text = SyntaxParsers.getCombinedTokenText(source, start, len);
                        var valueText = options.allowNonAdjacentWildcardParts()
                            ? SyntaxParsers.getCombinedTokenText(source, start, len, false) // PORT: §3.12 named arguments
                            : text;
                        return SyntaxToken.identifier(trivia, text, valueText);
                    })
                .withTag("<wildcard>");

        this.wildcardedNameReference =
                rule(
                    this.wildcardedIdentifier,
                    (token) -> (Expression)new NameReference(new WildcardedName(token)))
                .withTag("<wildcard>");

        var ScanBracketedWildcardName =
                and(
                    token(SyntaxKind.OpenBracketToken),
                    ScanWildcard);

        var BracketedWildcardedNameReference =
                rule(
                    token(SyntaxKind.OpenBracketToken),
                    this.wildcardedIdentifier,
                    requiredToken(SyntaxKind.CloseBracketToken),
                    (open, wildcard, close) ->
                        (Expression)new NameReference(new BracketedWildcardedName(open, wildcard, close)))
                .withTag("<bracketed-wildcard>");

        InvocationExpression =
                first(
                    rule(
                        token(SyntaxKind.MinusToken).hide(),
                        FunctionCallOrPath,
                        (op, expr) -> (Expression)new PrefixUnaryExpression(SyntaxKind.UnaryMinusExpression, op, expr)),
                    rule(
                        token(SyntaxKind.PlusToken).hide(),
                        FunctionCallOrPath,
                        (op, expr) -> (Expression)new PrefixUnaryExpression(SyntaxKind.UnaryPlusExpression, op, expr)),
                    FunctionCallOrPath);

        var BracketedExpression =
                rule(
                    token(SyntaxKind.OpenBracketToken),
                    required(this.unnamedExpression, (source, start) -> createMissingNameReference(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseBracketToken),
                    (openBracket, expr, closeBracket) ->
                        (Expression)new BracketedExpression(openBracket, expr, closeBracket));

        var BracketedPathElementSelector =
                first(
                    if_(ScanBracketedName, BracketedNameReference),
                    if_(ScanBracketedWildcardName, BracketedWildcardedNameReference),
                    BracketedExpression);

        // this is a name after a dot in a dotted path

        PathElementSelector =
                first(
                    BarePathElementSelector,
                    BracketedPathElementSelector);

        BracketedEntityNamePathElementSelector =
                first(
                    if_(ScanBracketedWildcardName, BracketedWildcardedNameReference),
                    BracketedNameReference);

        var PathElementSelectorOrFunctionCall =
                first(
                    if_(ScanFunctionCall, FunctionCall),
                    PathElementSelector);

        EntityPathExpression =
                applyZeroOrMore(
                    PathElementSelectorOrFunctionCall,
                    _left ->
                        first(
                            rule(_left, token(SyntaxKind.DotToken), required(PathElementSelectorOrFunctionCall, (source, start) -> createMissingNameReference(source, start)), // PORT: §3.8 method group
                                (left, dot, selector) ->
                                    (Expression)new PathExpression(left, dot, selector)),
                            rule(_left, BracketedPathElementSelector,
                                (left, right) ->
                                    (Expression)new ElementExpression(left, right))));

        ScanQualifiedEntityStart =
                and(or(
                    hiddenToken(Functions.Database.name()),
                    hiddenToken(Functions.Cluster.name())),
                    token(SyntaxKind.OpenParenToken));

        SimplePathExpression =
                applyZeroOrMore(
                    PathElementSelector,
                    _left ->
                        first(
                            rule(_left, token(SyntaxKind.DotToken), required(PathElementSelector, (source, start) -> createMissingNameReference(source, start)), // PORT: §3.8 method group
                                (left, dot, selector) ->
                                    (Expression)new PathExpression(left, dot, selector)),
                            rule(_left, BracketedPathElementSelector,
                                (left, right) ->
                                    (Expression)new ElementExpression(left, right))));

        ScanWildcardedEntityReferenceOrFunctionCall =
                or(ScanWildcard, ScanFunctionCall);

        SimpleOrWildcardedEntityReference =
                first(
                    this.wildcardedNameReference,
                    this.simpleNameReference)
                .withTag("<simple-or-wildcarded-entity>");

        var WildcardedEntityReferencePathSelector =
                first(
                    this.wildcardedNameReference,
                    BarePathElementSelector,
                    BracketedEntityNamePathElementSelector);

        // everything up until the dot-wildcard

        var WildcardedEntityPathRoot =
                applyZeroOrMore(
                    PathElementSelectorOrFunctionCall,
                    _left ->
                        first(
                            if_(and(token(SyntaxKind.DotToken), not(ScanWildcard)),
                                rule(_left, token(SyntaxKind.DotToken), required(PathElementSelectorOrFunctionCall, (source, start) -> createMissingNameReference(source, start)), // PORT: §3.8 method group
                                    (left, dot, selector) ->
                                        (Expression)new PathExpression(left, dot, selector))
                            ),
                            rule(_left, BracketedPathElementSelector,
                                (left, right) ->
                                    (Expression)new ElementExpression(left, right))));

        WildcardedEntityReference =
                first(
                    this.wildcardedNameReference,
                    applyOptional(
                        WildcardedEntityPathRoot,
                        _left ->
                            rule(
                                _left,
                                token(SyntaxKind.DotToken),
                                required(this.wildcardedNameReference, (source, start) -> createMissingNameReference(source, start)), // PORT: §3.8 method group
                                (path, dot, selector) ->
                                    (Expression)new PathExpression(path, dot, selector))))
                .withTag("<wildcarded-entity-reference>");

        var ToScalarExpression =
                rule(
                    token(SyntaxKind.ToScalarKeyword, CompletionKind.ScalarPrefix),
                    optional(queryParameter(QueryOperatorParameters.ToScalarKindParameter, false)), // PORT: §3.12 named arguments
                    requiredToken(SyntaxKind.OpenParenToken),
                    required(this.expression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseParenToken),
                    (name, kind, openParen, expression, closeParen) ->
                        (Expression)new ToScalarExpression(name, kind, openParen, expression, closeParen));

        var ToTableExpression =
                rule(
                    token(SyntaxKind.ToTableKeyword, CompletionKind.TabularPrefix).hide(),
                    optional(queryParameter(QueryOperatorParameters.ToTableKindParameter, false)), // PORT: §3.12 named arguments
                    requiredToken(SyntaxKind.OpenParenToken),
                    required(this.expression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseParenToken),
                    (name, kind, openParen, expression, closeParen) ->
                        (Expression)new ToTableExpression(name, kind, openParen, expression, closeParen));

        FunctionCallOrPathCore =
                first(
                    ToTableExpression, // first to preempt being seen as function call

                    applyZeroOrMore(
                        first(
                            ToScalarExpression, // first to preempt being seen as function call
                            if_(ScanFunctionCall, FunctionCall),
                            PrimaryExpression),

                        _left ->
                            first(
                                //If(And(Token(SyntaxKind.DotToken), ScanFunctionCall),
                                //    Rule(_left, Token(SyntaxKind.DotToken), FunctionCall,
                                //        (left, dot, fc) => (Expression)new PathExpression(left, dot, fc))),
                                rule(_left, token(SyntaxKind.DotToken), required(PathElementSelectorOrFunctionCall, (source, start) -> createMissingNameReference(source, start)), // PORT: §3.8 method group
                                    (left, dot, selector) -> (Expression)new PathExpression(left, dot, selector)),
                                rule(_left, BracketedExpression,
                                    (left, right) -> (Expression)new ElementExpression(left, right)))));

        RequiredFunctionCallOrPath =
                required(FunctionCallOrPath, (source, start) -> createMissingExpression(source, start)); // PORT: §3.8 method group

        var UnaryPlusOrMinus =
                first(
                    rule(
                        token(SyntaxKind.PlusToken).hide(),
                        RequiredFunctionCallOrPath,
                        (op, expr) -> (Expression)new PrefixUnaryExpression(SyntaxKind.UnaryPlusExpression, op, expr)),
                    rule(
                        token(SyntaxKind.MinusToken).hide(),
                        RequiredFunctionCallOrPath,
                        (op, expr) -> (Expression)new PrefixUnaryExpression(SyntaxKind.UnaryMinusExpression, op, expr)),
                    FunctionCallOrPath);

        var RequiredUnaryPlusOrMinus =
                required(UnaryPlusOrMinus, (source, start) -> createMissingExpression(source, start)); // PORT: §3.8 method group

        // PORT: §3.6 First(StringOperatorMap.Keys.Select(k => ...).ToArray())
            List<Parser2<LexicalToken, SyntaxToken>> stringOperatorTokenList = new ArrayList<>();
            for (SyntaxKind k : StringOperatorMap.keySet())
            {
                stringOperatorTokenList.add(isTokenVisible(k)
                    ? SyntaxParsers.token(k, CompletionKind.ScalarInfix, CompletionPriority.Normal, SyntaxFacts.getText(k) + " \"|\"")
                    : SyntaxParsers.token(k).hide());
            }
            var StringOperatorTokens =
                first(stringOperatorTokenList.toArray((Parser2<LexicalToken, SyntaxToken>[]) new Parser2[0]));

        var StringOperation =
                first(
                    rule(
                        token(SyntaxKind.AsteriskToken).hide(),
                        StringOperatorTokens,
                        RequiredUnaryPlusOrMinus,
                        (left, op, right) ->
                            (Expression)new BinaryExpression(StringOperatorMap.get(op.kind()), new StarExpression(left), op, right)), // PORT: §3.1 indexer
                    applyOptional(
                        UnaryPlusOrMinus,
                        _left ->
                            rule(
                                _left,
                                StringOperatorTokens,
                                RequiredUnaryPlusOrMinus,
                                (left, op, right) ->
                                    (Expression)new BinaryExpression(StringOperatorMap.get(op.kind()), left, op, right)))); // PORT: §3.1 indexer

        var RequiredStringOperation =
                required(StringOperation, (source, start) -> createMissingExpression(source, start)); // PORT: §3.8 method group

        var Multiplicative =
                applyZeroOrMore(StringOperation, _left ->
                    first(
                        rule(_left, token(SyntaxKind.AsteriskToken, CompletionKind.ScalarInfix), RequiredStringOperation,
                            (left, op, right) -> (Expression)new BinaryExpression(SyntaxKind.MultiplyExpression, left, op, right)),

                        rule(_left, token(SyntaxKind.SlashToken, CompletionKind.ScalarInfix), RequiredStringOperation,
                            (left, op, right) -> (Expression)new BinaryExpression(SyntaxKind.DivideExpression, left, op, right)),

                        rule(_left, token(SyntaxKind.PercentToken, CompletionKind.ScalarInfix), RequiredStringOperation,
                            (left, op, right) -> (Expression)new BinaryExpression(SyntaxKind.ModuloExpression, left, op, right))
                        ));

        var RequiredMultiplicative =
                required(Multiplicative, (source, start) -> createMissingExpression(source, start)); // PORT: §3.8 method group

        var Additive =
                applyZeroOrMore(Multiplicative, _left ->
                    first(
                        rule(_left, token(SyntaxKind.PlusToken, CompletionKind.ScalarInfix), RequiredMultiplicative,
                            (left, op, right) -> (Expression)new BinaryExpression(SyntaxKind.AddExpression, left, op, right)),

                        rule(_left, token(SyntaxKind.MinusToken, CompletionKind.ScalarInfix), RequiredMultiplicative,
                            (left, op, right) -> (Expression)new BinaryExpression(SyntaxKind.SubtractExpression, left, op, right))
                        ));

        var RequiredAdditive =
                required(Additive, (source, start) -> createMissingExpression(source, start)); // PORT: §3.8 method group

        var Relational =
                applyOptional(Additive, _left ->
                    first(
                        rule(_left, token(SyntaxKind.LessThanToken, CompletionKind.ScalarInfix), RequiredAdditive,
                            (left, op, right) -> (Expression)new BinaryExpression(SyntaxKind.LessThanExpression, left, op, right)),

                        rule(_left, token(SyntaxKind.LessThanOrEqualToken, CompletionKind.ScalarInfix), RequiredAdditive,
                            (left, op, right) -> (Expression)new BinaryExpression(SyntaxKind.LessThanOrEqualExpression, left, op, right)),

                        rule(_left, token(SyntaxKind.GreaterThanToken, CompletionKind.ScalarInfix), RequiredAdditive,
                            (left, op, right) -> (Expression)new BinaryExpression(SyntaxKind.GreaterThanExpression, left, op, right)),

                        rule(_left, token(SyntaxKind.GreaterThanOrEqualToken, CompletionKind.ScalarInfix), RequiredAdditive,
                            (left, op, right) -> (Expression)new BinaryExpression(SyntaxKind.GreaterThanOrEqualExpression, left, op, right))
                        ));

        var RequiredRelational =
                required(Relational, (source, start) -> createMissingExpression(source, start)); // PORT: §3.8 method group

        var ExpressionCouple =
                rule(
                    token(SyntaxKind.OpenParenToken),
                    required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.DotDotToken),
                    required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseParenToken),

                    (openParen, first, dotDot, second, closeParen) ->
                        new ExpressionCouple(openParen, first, dotDot, second, closeParen));

        var InOperatorExpressionList =
                rule(
                    token(SyntaxKind.OpenParenToken),
                    best(
                        // this is a special path meant to influence completion for first argument only when an extra parenthesis is typed 
                        if_(and(token(SyntaxKind.OpenParenToken), withCompletionHint(AnyToken, CompletionHint.NonScalar | CompletionHint.Scalar)), // PORT: §3.5 extension method
                            commaList(this.unnamedExpression, (source, start) -> createMissingExpression(source, start), true)), // PORT: §3.8 method group; §3.12 named arguments
                        // normal list of expressions
                        commaList(this.unnamedExpression, (source, start) -> createMissingExpression(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                        // allows full query expression as only item in list
                        rule(
                            withCompletionHint(this.expression, CompletionHint.NonScalar | CompletionHint.Scalar), // PORT: §3.5 extension method
                            expr -> new SyntaxList1<SeparatedElement1<Expression>>(new SeparatedElement1<Expression>(expr)))
                        ),
                    requiredToken(SyntaxKind.CloseParenToken),

                    (openParen, list, closeParen) ->
                        new ExpressionList(openParen, list, closeParen));

        var Equality =
                first(
                    rule(token(SyntaxKind.AsteriskToken).hide(), token(SyntaxKind.EqualEqualToken), Relational, (asterisk, equal, expression) ->
                            (Expression)new BinaryExpression(SyntaxKind.EqualExpression, new StarExpression(asterisk), equal, expression)),

                    applyOptional(Relational, _left ->
                        first(
                            rule(_left, token(SyntaxKind.EqualEqualToken, CompletionKind.ScalarInfix, CompletionPriority.Top), RequiredRelational,
                                (left, op, right) -> (Expression)new BinaryExpression(SyntaxKind.EqualExpression, left, op, right)),

                            rule(_left, token(SyntaxKind.BangEqualToken, CompletionKind.ScalarInfix), RequiredRelational,
                                (left, op, right) -> (Expression)new BinaryExpression(SyntaxKind.NotEqualExpression, left, op, right)),

                            rule(_left, token(SyntaxKind.LessThanGreaterThanToken, CompletionKind.ScalarInfix).hide(), RequiredRelational,
                                (left, op, right) -> (Expression)new BinaryExpression(SyntaxKind.NotEqualExpression, left, op, right)),

                            rule(_left, inToken(SyntaxKind.InKeyword), InOperatorExpressionList,
                                (left, op, right) -> (Expression)new InExpression(SyntaxKind.InExpression, left, op, right)),

                            rule(_left, inToken(SyntaxKind.InCsKeyword), InOperatorExpressionList,
                                (left, op, right) -> (Expression)new InExpression(SyntaxKind.InCsExpression, left, op, right)),

                            rule(_left, inToken(SyntaxKind.NotInKeyword), InOperatorExpressionList,
                                (left, op, right) -> (Expression)new InExpression(SyntaxKind.NotInExpression, left, op, right)),

                            rule(_left, inToken(SyntaxKind.NotInCsKeyword), InOperatorExpressionList,
                                (left, op, right) -> (Expression)new InExpression(SyntaxKind.NotInCsExpression, left, op, right)),

                            rule(_left, inToken(SyntaxKind.HasAnyKeyword), InOperatorExpressionList,
                                // PORT-BUG: HasAnyExpression is built with kind HasAnyKeyword, QueryParser builds it with kind HasAnyExpression; mirrored, see traps/0355
                                (left, op, right) -> (Expression)new HasAnyExpression(SyntaxKind.HasAnyKeyword, left, op, right)),

                              rule(_left, inToken(SyntaxKind.HasAllKeyword), InOperatorExpressionList,
                                // PORT-BUG: HasAllExpression is built with kind HasAllKeyword, QueryParser builds it with kind HasAllExpression; mirrored, see traps/0361
                                (left, op, right) -> (Expression)new HasAllExpression(SyntaxKind.HasAllKeyword, left, op, right)),

                            rule(_left, token(SyntaxKind.BetweenKeyword, CompletionKind.ScalarInfix, CompletionPriority.Normal, SyntaxFacts.getText(SyntaxKind.BetweenKeyword) + " (| .. )"), ExpressionCouple, // PORT: §3.12 named arguments
                                (left, op, right) -> (Expression)new BetweenExpression(SyntaxKind.BetweenExpression, left, op, right)),

                            rule(_left, token(SyntaxKind.NotBetweenKeyword, CompletionKind.ScalarInfix, CompletionPriority.Normal, SyntaxFacts.getText(SyntaxKind.NotBetweenKeyword) + " (| .. )"), ExpressionCouple, // PORT: §3.12 named arguments
                                (left, op, right) -> (Expression)new BetweenExpression(SyntaxKind.NotBetweenExpression, left, op, right))
                            )));

        var LogicalAnd =
                applyZeroOrMore(Equality, _left ->
                    rule(_left, token(SyntaxKind.AndKeyword, CompletionKind.ScalarInfix), required(Equality, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                        (left, op, right) -> (Expression)new BinaryExpression(SyntaxKind.AndExpression, left, op, right)));

        LogicalOr =
                applyZeroOrMore(LogicalAnd, _left ->
                    rule(_left, token(SyntaxKind.OrKeyword, CompletionKind.ScalarInfix), required(LogicalAnd, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                        (left, op, right) -> (Expression)new BinaryExpression(SyntaxKind.OrExpression, left, op, right)));

        // endregion
    }

    // PORT: §3.19 split
    private void initialize_QueryOperators_DataTableAndExternalData()
    {
        // region Query Operators

        this.literalList = commaList(this.literal, (source, start) -> createMissingExpression(source, start), false, true); // PORT: §3.8 method group; §3.12 named arguments

        RowSchema =
                    rule(
                        token(SyntaxKind.OpenParenToken),
                        optional(token(SyntaxKind.CommaToken)),
                        SyntaxParsers.<NameAndTypeDeclaration>commaList( // PORT: §3.10 explicit type arguments
                            NameAndTypeDeclaration,
                            (source, start) -> createMissingNameAndTypeDeclaration(source, start), false, // PORT: §3.8 method group
                            true), // PORT: §3.12 named arguments
                        requiredToken(SyntaxKind.CloseParenToken),
                        (openParen, leadingComma, columns, closeParen) ->
                            new RowSchema(openParen, leadingComma, columns, closeParen));

        MandatoryRowSchema =
                rule(
                    token(SyntaxKind.OpenParenToken),
                    optional(token(SyntaxKind.CommaToken)),
                    SyntaxParsers.<NameAndTypeDeclaration>commaList( // PORT: §3.10 explicit type arguments
                        NameAndTypeDeclaration,
                        (source, start) -> createMissingNameAndTypeDeclaration(source, start), // PORT: §3.8 method group
                        true,
                        true), // PORT: §3.12 named arguments
                    requiredToken(SyntaxKind.CloseParenToken),
                    (openParen, leadingComma, columns, closeParen) ->
                        new RowSchema(openParen, leadingComma, columns, closeParen));

        EvaluateRowSchema =
                    rule(
                        token(SyntaxKind.OpenParenToken),
                        optional(token(SyntaxKind.CommaToken)),
                        optional(token(SyntaxKind.AsteriskToken)),
                        optional(token(SyntaxKind.CommaToken)),
                        SyntaxParsers.<NameAndTypeDeclaration>commaList( // PORT: §3.10 explicit type arguments
                            NameAndTypeDeclaration,
                            (source, start) -> createMissingNameAndTypeDeclaration(source, start), false, // PORT: §3.8 method group
                            true), // PORT: §3.12 named arguments
                        requiredToken(SyntaxKind.CloseParenToken),
                        (openParen, leadingComma, asteriskToken, asteriskTokenComma, columns, closeParen) ->
                            new EvaluateRowSchema(openParen, leadingComma, asteriskToken, asteriskTokenComma, columns, closeParen));

        DataTableExpression =
                rule(
                    token(SyntaxKind.DataTableKeyword, CompletionKind.QueryPrefix),
                    queryParameterList(QueryOperatorParameters.DataTableParameters),
                    required(RowSchema, (source, start) -> createMissingRowSchema(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.OpenBracketToken),
                    optional(token(SyntaxKind.CommaToken)),
                    this.literalList,
                    requiredToken(SyntaxKind.CloseBracketToken),
                    (keyword, parameters, schema, openBracket, leadingComma, values, closeBracket) ->
                        (Expression)new DataTableExpression(keyword, parameters, schema, openBracket, leadingComma, values, closeBracket));

        ContextualDataTableExpression =
                rule(
                    token(SyntaxKind.ContextualDataTableKeyword).hide(),
                    // PORT: §3.8 method group
                    required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // guid literal expected, though parse any expression
                    required(RowSchema, (source, start) -> createMissingRowSchema(source, start)), // PORT: §3.8 method group
                    (keyword, id, schema) ->
                        (Expression)new ContextualDataTableExpression(keyword, id, schema));

        var ExternalDataWithClausePropertyValue =
                first(
                    this.stringLiteral,
                    LongLiteral,
                    RealLiteral,
                    BooleanLiteral,
                    DateTimeLiteral,
                    TypeofLiteral,
                    RawGuidLiteral,
                    rule(RenameName, n -> (Expression)n));

        var ExternalDataWithClauseProperty =
                rule(
                    RenameName,
                    token(SyntaxKind.EqualToken),
                    required(ExternalDataWithClausePropertyValue, (source, start) -> createMissingValue(source, start)), // PORT: §3.8 method group
                    (name, equals, value) -> new NamedParameter(name, equals, value));

        ExternalDataWithClause =
                rule(
                    token(SyntaxKind.WithKeyword),
                    requiredToken(SyntaxKind.OpenParenToken),
                    commaList(ExternalDataWithClauseProperty, (source, start) -> createMissingNamedParameter(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseParenToken),
                    (keyword, openParen, list, closeParen) ->
                        new ExternalDataWithClause(keyword, openParen, list, closeParen));

        ExternalDataExpression =
                rule(
                    first(
                        token(SyntaxKind.ExternalDataKeyword, CompletionKind.QueryPrefix),
                        token(SyntaxKind.External_DataKeyword).hide()),
                    queryParameterList(QueryOperatorParameters.ExternalDataWithClauseProperties),
                    required(RowSchema, (source, start) -> createMissingRowSchema(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.OpenBracketToken),
                    commaList(this.unnamedExpression, (source, start) -> createMissingExpression(source, start), true, true), // PORT: §3.8 method group; §3.12 named arguments
                    requiredToken(SyntaxKind.CloseBracketToken),
                    optional(ExternalDataWithClause),
                    (keyword, parameters, schema, openBracket, name, closeBracket, withClause) ->
                        (Expression)new ExternalDataExpression(keyword, parameters, schema, openBracket, name, closeBracket, withClause));

        // Inline External Table Expression
    }

    // PORT: §3.19 split
    private void initialize_QueryOperators_InlineExternalTable()
    {
        var InlineExternalTableKindClause = rule(
                token(SyntaxKind.KindKeyword),
                requiredToken(SyntaxKind.EqualToken),
                requiredTokenText(KustoFacts.InlineExternalTableKinds), // PORT: §2.5 overload on IReadOnlyList<string>
                (keyword, equals, kind) ->
                    new InlineExternalTableKindClause(keyword, equals, kind));

        var InlineExternalTableDataFormatClause = rule(
                token(SyntaxKind.DataFormatKeyword),
                requiredToken(SyntaxKind.EqualToken),
                requiredToken(SyntaxKind.IdentifierToken),
                (keyword, equals, type) ->
                    new InlineExternalTableDataFormatClause(keyword, equals, type));

        var ParseInlineExternalTablePathFormat =
                rule(
                    first(
                        IdentifierNameReference,
                        rule(
                            token(SyntaxKind.DateTimePatternKeyword),
                            requiredToken(SyntaxKind.OpenParenToken),
                            required(this.stringLiteral, (source, start) -> createMissingStringLiteral(source, start)), // PORT: §3.8 method group
                            requiredToken(SyntaxKind.CommaToken),
                            required(IdentifierNameReference, (source, start) -> createMissingNameReference(source, start)), // PORT: §3.8 method group
                            requiredToken(SyntaxKind.CloseParenToken),
                            (keyword, openParen, pattern, comma, partitionColumn, closeBracket) ->
                                (Expression)new DateTimePattern(keyword, openParen, (LiteralExpression)pattern, comma, (NameReference)partitionColumn, closeBracket))),
                    optional(this.stringLiteral),
                    (partitionColumnReference, optionalSeparator) ->
                        new InlineExternalTablePathFormatPartitionColumnReference(partitionColumnReference, (LiteralExpression)optionalSeparator)
                );

        var InlineExternalTablePathFormatClause =
                rule(
                    token(SyntaxKind.PathFormatKeyword),
                    requiredToken(SyntaxKind.EqualToken),
                    requiredToken(SyntaxKind.OpenParenToken),
                    optional(this.stringLiteral),
                    list(ParseInlineExternalTablePathFormat, (source, start) -> createMissingPathFormatTokens(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                    requiredToken(SyntaxKind.CloseParenToken),
                    (keyword, equals, openBracket, optionalSeparator, pathFormat, closeBracket ) ->
                        new InlineExternalTablePathFormatClause(keyword, equals, openBracket, (LiteralExpression)optionalSeparator, pathFormat, closeBracket));

        var PartitionColumnType =
                asPrimitiveTypeExpression(
                    first(
                        token(SyntaxKind.DateTimeKeyword, CompletionKind.ScalarType),
                        token(SyntaxKind.LongKeyword, CompletionKind.ScalarType),
                        token(SyntaxKind.StringKeyword, CompletionKind.ScalarType)));

        var PartitionColumnDeclaration =
                rule(
                    ExtendedNameDeclaration,
                    requiredToken(SyntaxKind.ColonToken),
                    required(first(PartitionColumnType, InvalidParamType), (source, start) -> createMissingType(source, start)), // PORT: §3.8 method group
                    optional(token(SyntaxKind.EqualToken)),
                    optional(this.unnamedExpression),
                    (name, colon, type, equal, expr) -> new PartitionColumnDeclaration(name, colon, type, equal, expr));

        var InlineExternalTablePartitionClause =
                rule(
                    token(SyntaxKind.PartitionKeyword),
                    requiredToken(SyntaxKind.ByKeyword),
                    requiredToken(SyntaxKind.OpenParenToken),
                    optional(token(SyntaxKind.CommaToken)),
                    SyntaxParsers.<PartitionColumnDeclaration>commaList(PartitionColumnDeclaration, (source, start) -> createMissingPartitionColumnDeclaration(source, start), false, true), // PORT: §3.10 explicit type arguments; §3.8 method group; §3.12 named arguments
                    requiredToken(SyntaxKind.CloseParenToken),
                    (keyword, byKeyword, openBracket, optionalComma, partitions, closeBracket) ->
                        new InlineExternalTablePartitionClause(keyword, byKeyword, openBracket, optionalComma, partitions, closeBracket));

        var InlineExternalTableConnectionStringsClause =
                rule(
                    requiredToken(SyntaxKind.OpenParenToken),
                    commaList(this.unnamedExpression, (source, start) -> createMissingExpression(source, start), true, true), // PORT: §3.8 method group; §3.12 named arguments
                    requiredToken(SyntaxKind.CloseParenToken),
                    (openBracket, connectionStrings, closeBracket) ->
                        new InlineExternalTableConnectionStringsClause(openBracket, connectionStrings, closeBracket));

        var InlineExternalTableExpressionWithSchema =
                if_(and(token(SyntaxKind.InlineExternalTableKeyword, CompletionKind.QueryPrefix), token(SyntaxKind.OpenParenToken)),
                    rule(
                        token(SyntaxKind.InlineExternalTableKeyword, CompletionKind.QueryPrefix),
                        queryParameterList(QueryOperatorParameters.InlineExternalTableProperties, AllowedNameKind.DeclaredOnly), // PORT: §3.12 named arguments
                        required(MandatoryRowSchema, (source, start) -> createMissingRowSchema(source, start)), // PORT: §3.8 method group
                        required(InlineExternalTableKindClause, (source, start) -> createMissingInlineExternalTableKindClause(source, start)), // PORT: §3.8 method group
                        optional(InlineExternalTablePartitionClause),
                        optional(InlineExternalTablePathFormatClause),
                        optional(InlineExternalTableDataFormatClause),
                        required(InlineExternalTableConnectionStringsClause, (source, start) -> createMissingInlineExternalTableConnectionStringsClause(source, start)), // PORT: §3.8 method group
                        optional(ExternalDataWithClause),
                        (keyword, parameters, schema, kindClause, partitionClause, pathFormatClause, dataFormat, connectionStrings, withClause) ->
                            (Expression)new InlineExternalTableExpression(
                                keyword,
                                parameters,
                                schema,
                                kindClause,
                                partitionClause,
                                pathFormatClause,
                                getInlineExternalTableDataFormatClause(kindClause, dataFormat),
                                connectionStrings,
                                withClause)));

        var InlineExternalTableExpressionWithoutSchema =
                if_(and(
                        token(SyntaxKind.InlineExternalTableKeyword, CompletionKind.QueryPrefix),
                        token(SyntaxKind.KindKeyword)),
                    rule(
                        token(SyntaxKind.InlineExternalTableKeyword, CompletionKind.QueryPrefix),
                        queryParameterList(QueryOperatorParameters.InlineExternalTableProperties, AllowedNameKind.DeclaredOnly), // PORT: §3.12 named arguments
                        required(InlineExternalTableKindClause, (source, start) -> createMissingInlineExternalTableKindClause(source, start)), // PORT: §3.8 method group
                        optional(InlineExternalTablePartitionClause),
                        optional(InlineExternalTablePathFormatClause),
                        optional(InlineExternalTableDataFormatClause),
                        required(InlineExternalTableConnectionStringsClause, (source, start) -> createMissingInlineExternalTableConnectionStringsClause(source, start)), // PORT: §3.8 method group
                        optional(ExternalDataWithClause),
                        (keyword, parameters, kindClause, partitionClause, pathFormatClause, dataFormat, connectionStrings, withClause) ->
                            (Expression)new InlineExternalTableExpression(
                                keyword,
                                parameters,
                                getInlineExternalTableRowSchema(kindClause),
                                kindClause,
                                partitionClause,
                                pathFormatClause,
                                getInlineExternalTableDataFormatClause(kindClause, dataFormat),
                                connectionStrings,
                                withClause)));

        InlineExternalTableExpression =
                first(
                    InlineExternalTableExpressionWithSchema,
                    InlineExternalTableExpressionWithoutSchema);

        // End of Inline External Table Expression
    }

    // PORT: §3.19 split
    private void initialize_QueryOperators_ConsumeThroughFindClauses()
    {
        ConsumeOperator =
                rule(
                    token(SyntaxKind.ConsumeKeyword, CompletionKind.QueryPrefix).hide(),
                    queryParameterList(QueryOperatorParameters.ConsumeParameters),
                    (consume, parameters) ->
                        (QueryOperator)new ConsumeOperator(consume, parameters))
                .withTag("<consume>");

        var CountAsIdentifierClause =
                rule(
                    token(SyntaxKind.AsKeyword).hide(),
                    requiredToken(SyntaxKind.IdentifierToken),
                    (asKeyword, identifier) ->
                        new CountAsIdentifierClause(asKeyword, identifier));

        CountOperator =
                rule(
                    token(SyntaxKind.CountKeyword, CompletionKind.QueryPrefix, CompletionPriority.High),
                    optional(CountAsIdentifierClause),
                    (countKeyword, asIdentifier) ->
                        (QueryOperator)new CountOperator(countKeyword, asIdentifier))
                .withTag("<count>");

        ExecuteAndCacheOperator =
                rule(
                    token(SyntaxKind.ExecuteAndCacheKeyword, CompletionKind.QueryPrefix),
                    (keyword) ->
                        (QueryOperator)new ExecuteAndCacheOperator(keyword))
                .withTag("<execute-and-cache>");

        ExtendOperator =
                rule(
                    token(SyntaxKind.ExtendKeyword, CompletionKind.QueryPrefix, CompletionPriority.High),
                    SyntaxParsers.<Expression>commaList(this.namedExpression, (source, start) -> createMissingExpression(source, start), true), // PORT: §3.10 explicit type arguments; §3.8 method group; §3.12 named arguments
                    (keyword, list) ->
                        (QueryOperator)new ExtendOperator(keyword, list))
                .withTag("<extend>");

        var FacetWithClause =
                first(
                    rule(token(SyntaxKind.WithKeyword), token(SyntaxKind.OpenParenToken), required(ForkPipeExpression, (source, start) -> createMissingQueryOperatorExpression(source, start)), requiredToken(SyntaxKind.CloseParenToken), // PORT: §3.8 method group
                        (withKeyword, openParen, expr, closeParen) ->
                            (FacetWithClause)new FacetWithExpressionClause(withKeyword, openParen, expr, closeParen)),

                    rule(token(SyntaxKind.WithKeyword), required(ForkPipeOperator, (source, start) -> createMissingQueryOperator(source, start)), // PORT: §3.8 method group
                        (withKeyword, op) ->
                            (FacetWithClause)new FacetWithOperatorClause(withKeyword, op)));

        FacetOperator =
                rule(
                    token(SyntaxKind.FacetKeyword, CompletionKind.QueryPrefix).hide(),
                    requiredToken(SyntaxKind.ByKeyword),
                    SyntaxParsers.<Expression>separatedList(SimplePathExpression, SyntaxKind.CommaToken, (source, start) -> createMissingNameReference(source, start), (Parser<LexicalToken>) null, true), // PORT: §3.10 explicit type arguments; §3.8 method group; §3.12 named arguments
                    optional(FacetWithClause),
                    (facetKeyword, byKeyword, list, withClause) ->
                        (QueryOperator)new FacetOperator(facetKeyword, byKeyword, list, withClause))
                .withTag("<facet>");

        FilterOperator =
                rule(
                    first(
                        token(SyntaxKind.WhereKeyword, CompletionKind.QueryPrefix, CompletionPriority.Top),
                        token(SyntaxKind.FilterKeyword).hide()),
                    queryParameterList(QueryOperatorParameters.FilterParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    required(this.namedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (keyword, parameters, condition) ->
                        (QueryOperator)new FilterOperator(keyword, parameters, condition))
                .withTag("<filter>");

        GetSchemaOperator =
                rule(
                    token(SyntaxKind.GetSchemaKeyword, CompletionKind.QueryPrefix, CompletionPriority.Low),
                    optional(queryParameter(QueryOperatorParameters.GetSchemaKind, false)), // PORT: §3.12 named arguments
                    (keyword, kind) -> (QueryOperator)new GetSchemaOperator(keyword, kind))
                .withTag("<get-schema>");

        AsOperator =
                rule(
                    token(SyntaxKind.AsKeyword, CompletionKind.QueryPrefix, CompletionPriority.Low),
                    queryParameterList(QueryOperatorParameters.AsParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    required(this.simpleNameDeclaration, (source, start) -> createMissingNameDeclaration(source, start)), // PORT: §3.8 method group
                    (keyword, parameters, name) -> (QueryOperator)new AsOperator(keyword, parameters, name))
                .withTag("<as>");

        var FindOperand =
                best(
                    WildcardedEntityReference,
                    applyOptional(
                        first(
                            BracketedEntityNamePathElementSelector,
                            BarePathElementSelector),
                        _left ->
                            rule(_left, token(SyntaxKind.BarToken), required(AsOperator, (source, start) -> createMissingQueryOperator(source, start)), // PORT: §3.8 method group
                                (left, bar, op) -> (Expression)new PipeExpression(left, bar, op)))
                    );

        FindInClause =
                rule(
                    token(SyntaxKind.InKeyword),
                    requiredToken(SyntaxKind.OpenParenToken),
                    commaList(FindOperand, (source, start) -> createMissingExpression(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                    requiredToken(SyntaxKind.CloseParenToken),
                    (inKeyword, openParen, exprs, closeParen) ->
                        new FindInClause(inKeyword, openParen, exprs, closeParen));

        var ColumnNameReference =
                first(
                    IdentifierNameReference,
                    KeywordAsIdentifierNameReference,
                    BracketedNameReference,
                    ClientParameterReference)
                .withTag("<column>");

        var TypedColumnNameReference =
                applyOptional(
                    ColumnNameReference,
                    _left -> rule(
                        _left,
                        token(SyntaxKind.ColonToken),
                        required(first(this.paramTypeExtended, InvalidParamType), (source, start) -> createMissingType(source, start)), // PORT: §3.8 method group
                        (name, colon, type) -> (Expression)new TypedColumnReference((NameReference)name, colon, type)));

        var PackExpression =
                rule(
                    token(SyntaxKind.PackKeyword),
                    requiredToken(SyntaxKind.OpenParenToken),
                    requiredToken(SyntaxKind.AsteriskToken, CompletionKind.Syntax),
                    requiredToken(SyntaxKind.CloseParenToken),
                    (pack, openParen, asterisk, closeParen) ->
                        (Expression)new PackExpression(pack, openParen, asterisk, closeParen));

        var FindProjectColumn =
                first(
                    PackExpression,
                    StarExpression,
                    TypedColumnNameReference);

        FindProjectClause =
                first(
                    rule(
                        token(SyntaxKind.ProjectKeyword),
                        commaList(FindProjectColumn, (source, start) -> createMissingExpression(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                        (token, list) -> new FindProjectClause(token, list)),
                    rule(token(SyntaxKind.ProjectSmartKeyword),
                        (token) -> new FindProjectClause(token, SyntaxList1.<SeparatedElement1<Expression>>empty()))); // PORT: §3.10 explicit type arguments

        FindProjectAwayClause =
                rule(
                    token(SyntaxKind._ProjectAwayKeyword),
                    commaList(FindProjectColumn, (source, start) -> createMissingExpression(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                    (token, list) -> new FindProjectClause(token, list));
    }

    // PORT: §3.19 split
    private void initialize_QueryOperators_FindSearchForkJoinLookupMakeSeries()
    {
        FindOperator =
                rule(
                    token(SyntaxKind.FindKeyword, CompletionKind.QueryPrefix),
                    optional(dataScopeClause(CompletionKind.Syntax)),
                    queryParameterList(QueryOperatorParameters.FindParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    optional(FindInClause),
                    optional(token(SyntaxKind.WhereKeyword)),
                    // PORT: §3.8 method group
                    required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // condition
                    optional(FindProjectClause),
                    optional(FindProjectAwayClause).hide(), // internal use?
                    (findKeyword, dataScope, parameters, inClause, whereKeyword, condition, project, projectAway) ->
                        (QueryOperator)new FindOperator(
                            findKeyword,
                            dataScope,
                            parameters,
                            inClause,
                            whereKeyword != null ? whereKeyword : ((parameters.childCount() > 0 || inClause != null) ? createMissingToken(SyntaxKind.WhereKeyword) : null) /* PORT: §3.14 ?? */,
                            condition,
                            project,
                            projectAway))
                .withTag("<find>");

        var SearchPredicate =
                first(
                    this.unnamedExpression,
                    applyOptional(
                        StarExpression,
                        _left ->
                            rule(
                                _left,
                                token(SyntaxKind.AndKeyword),
                                required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                                (left, op, right) -> (Expression)new BinaryExpression(SyntaxKind.AndExpression, left, op, right))));

        SearchOperator =
                rule(
                    token(SyntaxKind.SearchKeyword, CompletionKind.QueryPrefix),
                    queryParameterList(QueryOperatorParameters.SearchParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    optional(dataScopeClause(CompletionKind.Syntax)),
                    optional(FindInClause),
                    required(SearchPredicate, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (keyword, parameters, dataScope, inClause, predicate) ->
                        (QueryOperator)new SearchOperator(keyword, parameters, dataScope, inClause, predicate))
                .withTag("<search>");

        var NameEqualsClause =
                if_(or(token(SyntaxKind.IdentifierToken), token(SyntaxKind.OpenBracketToken)),
                    rule(this.simpleNameDeclaration, requiredToken(SyntaxKind.EqualToken),
                        (name, equalsToken) -> new NameEqualsClause(name, equalsToken)));

        var ForkExpression =
                rule(
                    optional(NameEqualsClause),
                    token(SyntaxKind.OpenParenToken),
                    required(first(ForkPipeExpression, this.expression.hide()), (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseParenToken),
                    (nameClause, openParen, expr, closeParen) ->
                        new ForkExpression(nameClause, openParen, expr, closeParen));

        ForkOperator =
                rule(
                    token(SyntaxKind.ForkKeyword, CompletionKind.QueryPrefix).hide(),
                    list(ForkExpression, (source, start) -> createMissingForkExpression(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                    (forkKeyword, list) -> (QueryOperator)new ForkOperator(forkKeyword, list))
                .withTag("<fork>");

        var JoinEqualityExpression =
                applyOptional(
                    FunctionCallOrPath,
                    _left ->
                        rule(
                            _left,
                            token(SyntaxKind.EqualEqualToken, CompletionKind.ScalarInfix), RequiredFunctionCallOrPath,
                            (left, op, right) -> (Expression)new BinaryExpression(SyntaxKind.EqualExpression, left, op, right)));

        var JoinAndExpression =
                applyZeroOrMore(
                    JoinEqualityExpression,
                    _left ->
                        rule(_left, token(SyntaxKind.AndKeyword, CompletionKind.ScalarInfix), required(JoinEqualityExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                            (left, op, right) -> (Expression)new BinaryExpression(SyntaxKind.AndExpression, left, op, right)));

        var JoinOnExpression =
                best(
                    JoinAndExpression, // only legal join expressions will show in intellisense
                    this.unnamedExpression.hide());

        // otherwise parse any expression and tag it in semantic analysis.

        var JoinOnClause =
                rule(
                    token(SyntaxKind.OnKeyword),
                    commaList(JoinOnExpression, (source, start) -> createMissingExpression(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                    (onKeyword, list) -> (JoinConditionClause)new JoinOnClause(onKeyword, list));

        var JoinWhereClause =
                rule(
                    token(SyntaxKind.WhereKeyword),
                    required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (keyword, predicate) -> (JoinConditionClause)new JoinWhereClause(keyword, predicate));

        JoinOperator =
                rule(
                    token(SyntaxKind.JoinKeyword, CompletionKind.QueryPrefix, CompletionPriority.High),
                    queryParameterList(QueryOperatorParameters.JoinParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    optional(first(
                        JoinOnClause,
                        JoinWhereClause.hide())),
                    (joinKeyword, parameters, expr, condition) ->
                        (QueryOperator)new JoinOperator(joinKeyword, parameters, expr, condition))
                .withTag("<join>");

        LookupOperator =
                rule(
                    token(SyntaxKind.LookupKeyword, CompletionKind.QueryPrefix, CompletionPriority.High),
                    queryParameterList(QueryOperatorParameters.LookupParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    required(JoinOnClause, (source, start) -> createMissingJoinOnClause(source, start)), // PORT: §3.8 method group
                    (LookupKeyword, parameters, expr, onClause) ->
                        (QueryOperator)new LookupOperator(LookupKeyword, parameters, expr, onClause))
                .withTag("<lookup>");

        var MakeSeriesOnClause =
                rule(
                    requiredToken(SyntaxKind.OnKeyword),
                    required(this.namedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (keyword, expr) -> new MakeSeriesOnClause(keyword, expr));

        var MakeSeriesInRangeClause =
                rule(
                    requiredToken(SyntaxKind.InKeyword).hide(), // this syntax is deprecated so hide the first keyword
                    requiredToken(SyntaxKind.RangeKeyword).hide(),
                    //new CompletionItem(CompletionKind.Keyword, "range (start, stop, step)", "range (", ")", "range")),
                    requiredToken(SyntaxKind.OpenParenToken),
                    commaList(this.namedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseParenToken),
                    (inKeyword, rangeKeyword, openParen, list, closeParen) ->
                        (MakeSeriesRangeClause)new MakeSeriesInRangeClause(inKeyword, rangeKeyword, new ExpressionList(openParen, list, closeParen)));

        var MakeSeriesFromClause =
                rule(
                    token(SyntaxKind.FromKeyword),
                    required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (FromToken, fromEx) ->
                        new MakeSeriesFromClause(FromToken, fromEx));

        var MakeSeriesToClause =
               rule(
                   token(SyntaxKind.ToKeyword),
                   required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                   (ToToken, toEx) ->
                       new MakeSeriesToClause(ToToken, toEx));

        var MakeSeriesStepClause =
              rule(
                  requiredToken(SyntaxKind.StepKeyword),
                  required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                  (stepToken, stepEx) ->
                      new MakeSeriesStepClause(stepToken, stepEx));

        var MakeSeriesFromToStepClause =
                if_(first(token(SyntaxKind.FromKeyword), token(SyntaxKind.ToKeyword), token(SyntaxKind.StepKeyword)),
                    rule(
                        optional(MakeSeriesFromClause),
                        optional(MakeSeriesToClause),
                        MakeSeriesStepClause,
                        (fromClause, toClause, stepClause) ->
                            (MakeSeriesRangeClause)new MakeSeriesFromToStepClause(fromClause, toClause, stepClause)));

        var MakeSeriesByClause =
                rule(
                    token(SyntaxKind.ByKeyword),
                    commaList(this.namedExpression, (source, start) -> createMissingExpression(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                    (keyword, list) -> new MakeSeriesByClause(keyword, list));

        var DefaultExpressionClause =
                rule(
                    token(SyntaxKind.DefaultKeyword),
                    requiredToken(SyntaxKind.EqualToken),
                    required(this.namedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (defaultKeyword, equalToken, expr) ->
                        new DefaultExpressionClause(defaultKeyword, equalToken, expr));

        var MakeSeriesExpression =
                rule(
                    this.namedExpression,
                    optional(DefaultExpressionClause),
                    (agg, defexp) -> new MakeSeriesExpression(agg, defexp));

        MakeSeriesOperator =
                rule(
                    token(SyntaxKind.MakeSeriesKeyword, CompletionKind.QueryPrefix),
                    queryParameterList(QueryOperatorParameters.MakeSeriesParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    separatedList(MakeSeriesExpression, SyntaxKind.CommaToken, (source, start) -> createMissingMakeSeriesExpression(source, start), (Parser<LexicalToken>) null, true), // PORT: §3.8 method group; §3.12 named arguments
                    MakeSeriesOnClause,
                    first(MakeSeriesFromToStepClause, MakeSeriesInRangeClause),
                    optional(MakeSeriesByClause),
                    (keyword, parameters, aggregates, onClause, rangeClause, byClause) ->
                        (QueryOperator)new MakeSeriesOperator(keyword, parameters, aggregates, onClause, rangeClause, byClause))
                .withTag("<make-series>");

        ToTypeOfClause =
                rule(
                    token(SyntaxKind.ToKeyword),
                    required(TypeofLiteral, (source, start) -> createMissingTypeOfLiteral(source, start)), // PORT: §3.8 method group
                    (toKeyword, typeOfLiteral) -> new ToTypeOfClause(toKeyword, (TypeOfLiteralExpression)typeOfLiteral));
    }

    // PORT: §3.19 split
    private void initialize_QueryOperators_MvExpandMvApplyEvaluateParsePartition()
    {
        var MvExpandExpression =
                first(
                    // check for missing initial expression error case
                    if_(token(SyntaxKind.ToKeyword).hide(),
                        rule(
                            required(this.namedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                            ToTypeOfClause,
                            (expr, toTypeOfClause) ->
                                new MvExpandExpression(expr, toTypeOfClause))),
                    rule(this.namedExpression, optional(ToTypeOfClause),
                        (expr, toTypeOfClause) ->
                            new MvExpandExpression(expr, toTypeOfClause)));

        var MvExpandExpressionList =
               separatedList(MvExpandExpression, SyntaxKind.CommaToken, (source, start) -> createMissingMvExpandExpression(source, start), (Parser<LexicalToken>) null, true); // PORT: §3.8 method group; §3.12 named arguments

        var MvExpandRowLimitClause =
                rule(
                    token(SyntaxKind.LimitKeyword),
                    required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (keyword, expr) -> new MvExpandRowLimitClause(keyword, expr));

        MvExpandOperator =
                rule(
                    first(
                        token(SyntaxKind.MvExpandKeyword, CompletionKind.QueryPrefix).hide(),
                        token(SyntaxKind.MvDashExpandKeyword, CompletionKind.QueryPrefix)),
                    queryParameterList(QueryOperatorParameters.MvExpandParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    MvExpandExpressionList,
                    optional(MvExpandRowLimitClause),
                    (keyword, parameters, list, rowLimit) ->
                        (QueryOperator)new MvExpandOperator(keyword, parameters, list, rowLimit))
                .withTag("<mvexpand>");

        var MvApplyExpression =
                first(
                    // check for missing initial expression error case
                    if_(token(SyntaxKind.ToKeyword).hide(),
                        rule(ToTypeOfClause,
                            (clause) ->
                                new MvApplyExpression(createMissingExpression(), clause))),
                    rule(this.namedExpression, optional(ToTypeOfClause),
                        (expr, toTypeOfClause) ->
                            new MvApplyExpression(expr, toTypeOfClause)));

        var MvApplyExpressionList =
                first(
                    // if only one item that is just "to typeof(xxx)" then allow expression to be null w/o error
                    if_(and(token(SyntaxKind.ToKeyword), TypeofLiteral, fails(token(SyntaxKind.CommaToken))),
                        rule(ToTypeOfClause,
                            clause -> new SyntaxList1<SeparatedElement1<MvApplyExpression>>(List.of( // PORT: §3.17 new[] -> List.of
                                new SeparatedElement1<MvApplyExpression>(new MvApplyExpression(null, clause)) ))))
                        .hide(),
                    separatedList(MvApplyExpression, SyntaxKind.CommaToken, (source, start) -> createMissingMvApplyExpression(source, start), (Parser<LexicalToken>) null, true)); // PORT: §3.8 method group; §3.12 named arguments

        var MvApplyRowLimitClause =
                rule(
                    token(SyntaxKind.LimitKeyword),
                    required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (keyword, expr) -> new MvApplyRowLimitClause(keyword, expr));

        var MvApplyContextIdClause =
              rule(
                  token(SyntaxKind.IdKeyword),
                  required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                  (keyword, expr) -> new MvApplyContextIdClause(keyword, expr));

        var MvApplySubqueryExpression =
                rule(
                    token(SyntaxKind.OpenParenToken),
                    required(ContextualSubExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseParenToken),
                    (openParen, expr, closeParen) ->
                        new MvApplySubqueryExpression(openParen, expr, closeParen));

        MvApplyOperator =
                rule(
                    first(
                        token(SyntaxKind.MvApplyKeyword, CompletionKind.QueryPrefix).hide(),
                        token(SyntaxKind.MvDashApplyKeyword, CompletionKind.QueryPrefix)),
                    queryParameterList(QueryOperatorParameters.MvApplyParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    MvApplyExpressionList,
                    optional(MvApplyRowLimitClause),
                    optional(MvApplyContextIdClause).hide(),
                    requiredToken(SyntaxKind.OnKeyword),
                    required(MvApplySubqueryExpression, (source, start) -> createMissingMvApplySubqueryExpression(source, start)), // PORT: §3.8 method group
                    (keyword, parameters, list, rowLimit, contextId, onKeyword, subquery) ->
                        (QueryOperator)new MvApplyOperator(keyword, parameters, list, rowLimit, contextId, onKeyword, subquery))
                .withTag("<mvapply>");

        var EvaluateSchemaClause =
                rule(
                    token(SyntaxKind.ColonToken),
                    required(EvaluateRowSchema, (source, start) -> createMissingEvaluateRowSchema(source, start)), // PORT: §3.8 method group
                    (keyword, expr) ->
                        new EvaluateSchemaClause(keyword, expr));

        EvaluateOperator =
                rule(
                    token(SyntaxKind.EvaluateKeyword, CompletionKind.QueryPrefix, CompletionPriority.Low),
                    queryParameterList(QueryOperatorParameters.EvaluateParameters),
                    RequiredFunctionCall,
                    optional(EvaluateSchemaClause),
                    (keyword, parameters, expr, schema) ->
                        (QueryOperator)new EvaluateOperator(keyword, parameters, (FunctionCallExpression)expr, schema))
                .withTag("<evaluate>");

        var NameAndOptionalTypeDeclaration =
                first(
                    applyOptional(
                        ExtendedNameDeclarationExpression,
                        _left ->
                            rule(
                                _left,
                                token(SyntaxKind.ColonToken),
                                required(first(ParamType, InvalidParamType), (source, start) -> createMissingType(source, start)), // PORT: §3.8 method group
                                (name, colon, type) ->
                                    (Expression)new NameAndTypeDeclaration((NameDeclaration)name, colon, type))),
                    rule(
                        token(SyntaxKind.ColonToken).hide(),
                        required(first(ParamType, InvalidParamType), (source, start) -> createMissingType(source, start)), // PORT: §3.8 method group
                        (colon, type) ->
                            (Expression)new NameAndTypeDeclaration(createMissingNameDeclaration(), colon, type)));

        var ParseWithExpression =
                first(
                    StarExpression,
                    StringOrCompoundStringLiteral,
                    NameAndOptionalTypeDeclaration);

        ParseOperator =
                rule(
                    token(SyntaxKind.ParseKeyword, CompletionKind.QueryPrefix, CompletionPriority.Low),
                    queryParameterList(QueryOperatorParameters.ParseParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.WithKeyword),
                    list(rule(ParseWithExpression, e -> (SyntaxNode)e)),
                    (parseKeyword, parameters, expr, withKeyword, expressions) ->
                        (QueryOperator)new ParseOperator(parseKeyword, parameters, expr, withKeyword, expressions))
                .withTag("<parse>");

        ParseWhereOperator =
                rule(
                    token(SyntaxKind.ParseWhereKeyword, CompletionKind.QueryPrefix, CompletionPriority.Low),
                    queryParameterList(QueryOperatorParameters.ParseParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.WithKeyword),
                    list(rule(ParseWithExpression, e -> (SyntaxNode)e)),
                    (parseKeyword, parameters, expr, withKeyword, expressions) ->
                        (QueryOperator)new ParseWhereOperator(parseKeyword, parameters, expr, withKeyword, expressions))
                .withTag("<parse-where>");

        var ParseKvWithClause =
                rule(
                    token(SyntaxKind.WithKeyword).hide(),
                    requiredToken(SyntaxKind.OpenParenToken),
                    queryParameterCommaList(QueryOperatorParameters.ParseKvWithProperties),
                    requiredToken(SyntaxKind.CloseParenToken),
                    (withKeyword, openParen, properties, closeParen) ->
                        new ParseKvWithClause(withKeyword, openParen, properties, closeParen));

        ParseKvOperator =
                rule(
                    token(SyntaxKind.ParseKvKeyword, CompletionKind.QueryPrefix, CompletionPriority.Low),
                    required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.AsKeyword),
                    required(RowSchema, (source, start) -> createMissingRowSchema(source, start)), // PORT: §3.8 method group
                    optional(ParseKvWithClause),
                    (parseKvKeyword, expression, asKeyword, keys, withClause) ->
                        (QueryOperator)new ParseKvOperator(parseKvKeyword, expression, asKeyword, keys, withClause))
                .withTag("<parse-kv>");

        var PartitionScopeClause =
                rule(
                    token(SyntaxKind.InKeyword).hide(),
                    required(first(FunctionCall, DynamicLiteral), (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (inKeyword, expr) -> new PartitionScope(inKeyword, expr));

        var PartitionQueryExpression =
               rule(
                   token(SyntaxKind.OpenBraceToken),
                   required(this.expression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                   requiredToken(SyntaxKind.CloseBraceToken),
                   (openBrace, expr, closeBrace) ->
                       (PartitionOperand)new PartitionQuery(openBrace, expr, closeBrace));

        var PartitionSubqueryExpression =
                rule(
                    token(SyntaxKind.OpenParenToken),
                    required(first(this.pipeSubExpression, this.expression.hide()), (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseParenToken),
                    (openParen, expr, closeParen) ->
                        (PartitionOperand)new PartitionSubquery(openParen, expr, closeParen));

        PartitionOperator =
                rule(
                    token(SyntaxKind.PartitionKeyword, CompletionKind.QueryPrefix, CompletionPriority.Low),
                    queryParameterList(QueryOperatorParameters.PartitionParameters),
                    requiredToken(SyntaxKind.ByKeyword),
                    required(SimplePathExpression, (source, start) -> createMissingNameReference(source, start)), // PORT: §3.8 method group
                    optional(PartitionScopeClause),
                    required(
                        first(
                            PartitionSubqueryExpression,
                            PartitionQueryExpression),
                        (source, start) -> createMissingPartitionOperand(source, start)), // PORT: §3.8 method group
                    (partitionKeyword, parameters, byKeyword, byExpression, scope, operand) ->
                        (QueryOperator)new PartitionOperator(partitionKeyword, parameters, byKeyword, byExpression, scope, operand))
                .withTag("<partition>");

        var PartitionByIdClause =
                rule(
                    token(SyntaxKind.IdKeyword),
                    this.literal,
                    (keyword, value) ->
                        new PartitionByIdClause(keyword, value));

        PartitionByOperator =
                rule(
                    hiddenToken(SyntaxKind.PartitionByKeyword),
                    queryParameterList(QueryOperatorParameters.PartitionByParameters),
                    required(SimplePathExpression, (source, start) -> createMissingNameReference(source, start)), // PORT: §3.8 method group
                    optional(PartitionByIdClause),
                    requiredToken(SyntaxKind.OpenParenToken),
                    required(ContextualSubExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseParenToken),
                    (keyword, parameters, entity, idClause, openParen, subQuery, closeParen) ->
                        (QueryOperator)new PartitionByOperator(keyword, parameters, entity, idClause, openParen, subQuery, closeParen));
    }

    // PORT: §3.19 split
    private void initialize_QueryOperators_ProjectSampleReduceSummarizeSort()
    {
        ProjectOperator =
                rule(
                    token(SyntaxKind.ProjectKeyword, CompletionKind.QueryPrefix, CompletionPriority.High),
                    commaList(this.namedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (keyword, list) -> (QueryOperator)new ProjectOperator(keyword, list))
                .withTag("<project>");

        ProjectAwayOperator =
                rule(
                    token(SyntaxKind.ProjectAwayKeyword, CompletionKind.QueryPrefix, CompletionPriority.High),
                    commaList(SimpleOrWildcardedEntityReference, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (keyword, list) -> (QueryOperator)new ProjectAwayOperator(keyword, list))
                .withTag("<project-away>");

        ProjectByNamesOperator =
                rule(
                    token(SyntaxKind.ProjectByNamesKeyword, CompletionKind.QueryPrefix, CompletionPriority.High),
                    commaList(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (keyword, list) -> (QueryOperator)new ProjectByNamesOperator(keyword, list))
                .withTag("<project-by-names>")
                .hide();

        ProjectKeepOperator =
               rule(
                   token(SyntaxKind.ProjectKeepKeyword, CompletionKind.QueryPrefix, CompletionPriority.High),
                   commaList(SimpleOrWildcardedEntityReference, (source, start) -> createMissingExpression(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                   (keyword, list) -> (QueryOperator)new ProjectKeepOperator(keyword, list))
               .withTag("<project-keep>");

        ProjectRenameOperator =
                rule(
                    token(SyntaxKind.ProjectRenameKeyword, CompletionKind.QueryPrefix, CompletionPriority.High),
                    commaList(this.namedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (keyword, list) -> (QueryOperator)new ProjectRenameOperator(keyword, list))
                .withTag("<project-rename>");

        SampleOperator =
                rule(
                    token(SyntaxKind.SampleKeyword, CompletionKind.QueryPrefix, CompletionPriority.Low),
                    queryParameterList(QueryOperatorParameters.SampleParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    required(this.namedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (sampleKeyword, parameters, expression) -> (QueryOperator)new SampleOperator(sampleKeyword, parameters, expression))
                .withTag("<sample>");

        SampleDistinctOperator =
                rule(
                    token(SyntaxKind.SampleDistinctKeyword, CompletionKind.QueryPrefix, CompletionPriority.Low),
                    queryParameterList(QueryOperatorParameters.SampleDistinctParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    required(this.namedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.OfKeyword),
                    required(this.namedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (keyword, parameters, expr, ofKeyword, ofExpr) ->
                        (QueryOperator)new SampleDistinctOperator(keyword, parameters, expr, ofKeyword, ofExpr))
                .withTag("<sample-distinct>");

        var ReduceByWithClause =
                rule(
                    token(SyntaxKind.WithKeyword),
                    queryParameterCommaList(QueryOperatorParameters.ReduceWithParameters),
                    (keyword, list) -> new ReduceByWithClause(keyword, list));

        ReduceByOperator =
                rule(
                    token(SyntaxKind.ReduceKeyword, CompletionKind.QueryPrefix, CompletionPriority.Low),
                    queryParameterList(QueryOperatorParameters.ReduceParameters),
                    requiredToken(SyntaxKind.ByKeyword),
                    required(this.namedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    optional(ReduceByWithClause),
                    (reduceKeyword, parameters, byKeyword, expr, withClause) ->
                        (QueryOperator)new ReduceByOperator(reduceKeyword, parameters, byKeyword, expr, withClause))
                .withTag("<reduce-by>");

        var SummarizeBinClause =
                if_(and(token(SyntaxKind.BinKeyword), token(SyntaxKind.EqualToken)),
                    rule(
                        token(SyntaxKind.BinKeyword),
                        token(SyntaxKind.EqualToken),
                        required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                        (bin, equal, value) ->
                            new SimpleNamedExpression(new NameDeclaration(new TokenName(bin)), equal, value)));

        var SummarizeByExpression =
                if_(not(and(token(SyntaxKind.BinKeyword), token(SyntaxKind.EqualToken), match(tk -> SyntaxFacts.isLiteral(tk.kind())))), // PORT: §3.5 extension method
                    this.namedExpression);

        var SummarizeByClause =
                rule(
                    token(SyntaxKind.ByKeyword),
                    commaList(SummarizeByExpression, (source, start) -> createMissingExpression(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                    optional(SummarizeBinClause.hide()), // legacy syntax
                    (byKeyword, expressions, binClause) ->
                        new SummarizeByClause(byKeyword, expressions, binClause))
                .withTag("<summarize-by>");

        var SummarizeExpression =
                if_(not(token(SyntaxKind.ByKeyword)),
                    this.namedExpression);

        SummarizeOperator =
                rule(
                    token(SyntaxKind.SummarizeKeyword, CompletionKind.QueryPrefix, CompletionPriority.High),
                    queryParameterList(QueryOperatorParameters.SummarizeParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    separatedList(SummarizeExpression, SyntaxKind.CommaToken, (source, start) -> createMissingExpression(source, start), (Parser<LexicalToken>) null, false), // PORT: §3.8 method group; §3.12 named arguments
                    optional(SummarizeByClause),
                    (summarizeKeyword, parameters, aggregates, byClause) ->
                        (QueryOperator)new SummarizeOperator(summarizeKeyword, parameters, aggregates, byClause))
                .withTag("<summarize>");

        DistinctOperator =
                rule(
                    token(SyntaxKind.DistinctKeyword, CompletionKind.QueryPrefix),
                    queryParameterList(QueryOperatorParameters.DistinctParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    commaList(first(StarExpression, this.namedExpression), (source, start) -> createMissingExpression(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                    (keyword, parameters, list) -> (QueryOperator)new DistinctOperator(keyword, parameters, list))
                .withTag("<distinct>");

        TakeOperator =
                rule(
                    first(
                        token(SyntaxKind.LimitKeyword, CompletionKind.QueryPrefix, CompletionPriority.High),
                        token(SyntaxKind.TakeKeyword, CompletionKind.QueryPrefix)),
                    queryParameterList(QueryOperatorParameters.TakeParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    required(examples(this.namedExpression, KustoFacts.LimitExamples), (source, start) -> createMissingExpression(source, start)), // PORT: §3.5 extension method; §3.8 method group
                    (keyword, parameters, expression) ->
                        (QueryOperator)new TakeOperator(keyword, parameters, expression))
                .withTag("<take>");

        var MissingFirstOrLastToken =
                SyntaxToken.missing("", SyntaxKind.FirstKeyword, List.of( DiagnosticFacts.getMissingFirstOrLast() )); // PORT: §3.17 new[] -> List.of

        var OrderingNullsClause =
                rule(
                    token(SyntaxKind.NullsKeyword),
                    required(first(token(SyntaxKind.FirstKeyword), token(SyntaxKind.LastKeyword)), () -> MissingFirstOrLastToken.clone()),
                    (keyword, firstOrLast) -> new OrderingNullsClause(keyword, firstOrLast));

        OrderingClause =
                rule(
                    optional(token(List.of( SyntaxKind.AscKeyword, SyntaxKind.DescKeyword ))), // PORT: §3.17 new[] -> List.of
                    optional(OrderingNullsClause),
                    (ascOrDesc, nullsClause) -> new OrderingClause(ascOrDesc, nullsClause));

        SortExpression =
                applyOptional(
                    this.namedExpression,
                    _left ->
                        rule(
                            _left,
                            if_(or(token(SyntaxKind.AscKeyword), token(SyntaxKind.DescKeyword), token(SyntaxKind.NullsKeyword)),
                                OrderingClause),
                            (left, right) -> (Expression)new OrderedExpression(left, right)));

        SortOperator =
                rule(
                    first(
                        token(SyntaxKind.OrderKeyword, CompletionKind.QueryPrefix, CompletionPriority.High),
                        token(SyntaxKind.SortKeyword, CompletionKind.QueryPrefix, CompletionPriority.High)),
                    // we hide these on purpose since understanding them requires understanding the internal
                    // implemenation of sort operator and we don't want to document it.
                    queryParameterList(QueryOperatorParameters.SortParameters).hide(),
                    requiredToken(SyntaxKind.ByKeyword),
                    commaList(SortExpression, (source, start) -> createMissingExpression(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                    (keyword, parameters, byKeyword, list) ->
                        (QueryOperator)new SortOperator(keyword, parameters, byKeyword, list))
                .withTag("<sort>");

        var ReorderOrderingClause =
               rule(
                   token(List.of( SyntaxKind.AscKeyword, SyntaxKind.DescKeyword, SyntaxKind.GrannyAscKeyword, SyntaxKind.GrannyDescKeyword )), // PORT: §3.17 new[] -> List.of
                   (ascOrDesc) -> new OrderingClause(ascOrDesc, null));

        var ReorderExpression =
                applyOptional(
                    SimpleOrWildcardedEntityReference,
                    _left ->
                        rule(
                            _left,
                            ReorderOrderingClause,
                            (left, right) -> (Expression)new OrderedExpression(left, right)));

        ProjectReorderOperator =
                rule(
                   token(SyntaxKind.ProjectReorderKeyword, CompletionKind.QueryPrefix, CompletionPriority.High),
                   commaList(ReorderExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                   (keyword, list) -> (QueryOperator)new ProjectReorderOperator(keyword, list))
                .withTag("<project-reorder>");
    }

    // PORT: §3.19 split
    private void initialize_QueryOperators_ScanTopUnionRangeRenderPrint()
    {
        var ScanAssignment =
                rule(ExtendedNameReference, token(SyntaxKind.EqualToken), required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (name, equals, expr) ->
                        new ScanAssignment((NameReference)name, equals, expr))
                .withTag("<assignment>");

        var ScanComputationClause =
                rule(
                    token(SyntaxKind.FatArrowToken),
                    commaList(ScanAssignment, (source, start) -> createMissingScanAssignment(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                    (token, list) -> new ScanComputationClause(token, list));

        var ScanStepOutput =
                rule(
                    token(SyntaxKind.OutputKeyword),
                    requiredToken(SyntaxKind.EqualToken),
                    requiredTokenText(KustoFacts.ScanStepOutputValues), // PORT: §2.5 overload on IReadOnlyList<string>
                    (output, equality, outputKind) -> new ScanStepOutput(output, equality, outputKind));

        var ScanStep =
                rule(
                    token(SyntaxKind.StepKeyword),
                    // PORT: §3.8 method group
                    required(RenameName, (source, start) -> createMissingNameDeclaration(source, start)), // name                    
                    optional(hiddenToken(SyntaxKind.OptionalKeyword)), // not yet supported                    
                    optional(ScanStepOutput.hide()),
                    requiredToken(SyntaxKind.ColonToken),
                    required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    optional(ScanComputationClause),
                    requiredToken(SyntaxKind.SemicolonToken),
                    (step, name, optional, output, colon, predicate, computation, semi) ->
                        new ScanStep(step, name, optional, output, colon, predicate, computation, semi));

        var ScanOrderByClause =
                rule(
                    hiddenToken(SyntaxKind.OrderKeyword), // not yet supported
                    requiredToken(SyntaxKind.ByKeyword),
                    commaList(SortExpression, (source, start) -> createMissingExpression(source, start), true, false, List.of( SyntaxKind.PartitionKeyword, SyntaxKind.DeclareKeyword, SyntaxKind.WithKeyword )), // PORT: §3.8 method group; §3.17 new[] -> List.of; §3.12 named arguments
                    (order, by, list) ->
                        new ScanOrderByClause(order, by, list));

        var ScanPartitionByClause =
                rule(
                    hiddenToken(SyntaxKind.PartitionKeyword), // not yet supported
                    requiredToken(SyntaxKind.ByKeyword),
                    commaList(this.unnamedExpression, (source, start) -> createMissingExpression(source, start), true, false, List.of( SyntaxKind.DeclareKeyword, SyntaxKind.WithKeyword )), // PORT: §3.8 method group; §3.17 new[] -> List.of; §3.12 named arguments
                    (partition, by, list) ->
                        new ScanPartitionByClause(partition, by, list));

        var ScanDeclareClause =
                rule(
                    token(SyntaxKind.DeclareKeyword),
                    requiredToken(SyntaxKind.OpenParenToken),
                    commaList(FunctionParameter, (source, start) -> createMissingFunctionParameter(source, start), false, false, List.of( SyntaxKind.WithKeyword )), // PORT: §3.8 method group; §3.17 new[] -> List.of; §3.12 named arguments
                    requiredToken(SyntaxKind.CloseParenToken),
                    (declare, open, declarations, close) -> new ScanDeclareClause(declare, open, declarations, close));

        ScanOperator =
                rule(
                    token(SyntaxKind.ScanKeyword, CompletionKind.QueryPrefix),
                    queryParameterList(QueryOperatorParameters.ScanParameters),
                    optional(ScanOrderByClause),
                    optional(ScanPartitionByClause),
                    optional(ScanDeclareClause),
                    requiredToken(SyntaxKind.WithKeyword,
                        new CompletionItem(CompletionKind.Syntax, "with", "with (", ")")),
                    requiredToken(SyntaxKind.OpenParenToken),
                    list(ScanStep),
                    requiredToken(SyntaxKind.CloseParenToken),
                    (scan, parameters, orderBy, partitionBy, declare, with, openParen, steps, closeParen) ->
                        (QueryOperator)new ScanOperator(scan, parameters, orderBy, partitionBy, declare, with, openParen, steps, closeParen));

        var TopHittersByClause =
                rule(
                    token(SyntaxKind.ByKeyword),
                    required(this.namedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (keyword, expression) -> new TopHittersByClause(keyword, expression));

        TopHittersOperator =
                rule(
                    token(SyntaxKind.TopHittersKeyword, CompletionKind.QueryPrefix),
                    required(examples(this.namedExpression, KustoFacts.TopExamples), (source, start) -> createMissingExpression(source, start)), // PORT: §3.5 extension method; §3.8 method group
                    requiredToken(SyntaxKind.OfKeyword),
                    required(this.namedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    optional(TopHittersByClause),
                    (keyword, expr, ofKeyword, ofExpr, byClause) ->
                        (QueryOperator)new TopHittersOperator(keyword, expr, ofKeyword, ofExpr, byClause))
                .withTag("<top-hitters>");

        TopOperator =
                rule(
                    token(SyntaxKind.TopKeyword, CompletionKind.QueryPrefix),
                    queryParameterList(QueryOperatorParameters.TopParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    required(examples(this.namedExpression, KustoFacts.TopExamples), (source, start) -> createMissingExpression(source, start)), // PORT: §3.5 extension method; §3.8 method group
                    requiredToken(SyntaxKind.ByKeyword),
                    required(SortExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (keyword, parameters, expr, byKeyword, byExpr) ->
                        (QueryOperator)new TopOperator(keyword, parameters, expr, byKeyword, byExpr))
                .withTag("<top>");

        var TopNestedWithOthersClause =
                rule(
                    token(SyntaxKind.WithKeyword),
                    requiredToken(SyntaxKind.OthersKeyword),
                    requiredToken(SyntaxKind.EqualToken),
                    this.literal,
                    (withKeyword, othersKeyword, equals, expression) ->
                        new TopNestedWithOthersClause(withKeyword, othersKeyword, equals, expression));

        var TopNestedByExpression =
                applyOptional(
                    this.namedExpression,
                    _left ->
                        rule(
                            _left,
                            if_(or(token(SyntaxKind.AscKeyword), token(SyntaxKind.DescKeyword), token(SyntaxKind.NullsKeyword)),
                                OrderingClause),
                            (expr, orderingClause) -> (Expression)new OrderedExpression(expr, orderingClause)));

        var TopNestedClause =
                rule(
                    token(SyntaxKind.TopNestedKeyword),
                    optional(this.namedExpression),
                    requiredToken(SyntaxKind.OfKeyword),
                    required(this.namedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    optional(TopNestedWithOthersClause),
                    requiredToken(SyntaxKind.ByKeyword),
                    required(TopNestedByExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group

                    (keyword, expr, ofKeyword, ofExpr, withOthersClause, byKeyword, byExpr) ->
                        new TopNestedClause(keyword, expr, ofKeyword, ofExpr, withOthersClause, byKeyword, byExpr));

        TopNestedOperator =
                if_(token(SyntaxKind.TopNestedKeyword, CompletionKind.QueryPrefix),
                    rule(commaList(TopNestedClause, (source, start) -> createMissingTopNestedClause(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                        list -> (QueryOperator)new TopNestedOperator(list)))
                .withTag("<top-nested>");

        var UnionExpression =
                first(
                    ParenthesizedExpression,
                    WildcardedEntityReference,
                    BracketedEntityNamePathElementSelector,
                    BarePathElementSelector);

        UnionOperator =
                rule(
                    token(SyntaxKind.UnionKeyword, CompletionKind.QueryPrefix),
                    queryParameterList(QueryOperatorParameters.UnionParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    SyntaxParsers.<Expression>commaList(UnionExpression, (source, start) -> createMissingExpression(source, start), true), // PORT: §3.10 explicit type arguments; §3.8 method group; §3.12 named arguments
                    (keyword, parameters, list) -> (QueryOperator)new UnionOperator(keyword, parameters, list))
                .withTag("<union>");

        SerializeOperator =
                rule(
                    token(SyntaxKind.SerializeKeyword, CompletionKind.QueryPrefix, CompletionPriority.Low),
                    queryParameterList(QueryOperatorParameters.SerializedParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    commaList(this.namedExpression, (source, start) -> createMissingExpression(source, start), false), // PORT: §3.8 method group; §3.12 named arguments
                    (keyword, parameters, exprs) ->
                        (QueryOperator)new SerializeOperator(keyword, parameters, exprs))
                .withTag("<serialize>");

        RangeOperator =
                // don't parse as range operator if it looks like the range function
                if_(and(token(SyntaxKind.RangeKeyword, CompletionKind.QueryPrefix), fails(token("("))),
                    rule(
                        token(SyntaxKind.RangeKeyword, CompletionKind.QueryPrefix),
                        required(this.simpleNameDeclaration, (source, start) -> createMissingNameDeclaration(source, start)), // PORT: §3.8 method group
                        requiredToken(SyntaxKind.FromKeyword),
                        required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                        requiredToken(SyntaxKind.ToKeyword),
                        required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                        requiredToken(SyntaxKind.StepKeyword),
                        required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                        (rangeToken, name, FromToken, fromEx, ToToken, toEx, stepToken, stepEx) ->
                            (QueryOperator)new RangeOperator(rangeToken, name, FromToken, fromEx, ToToken, toEx, stepToken, stepEx)))
                .withTag("<range>");

        InvokeOperator =
                rule(
                    token(SyntaxKind.InvokeKeyword, CompletionKind.QueryPrefix, CompletionPriority.Low),
                    required(DotCompositeFunctionCall, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (keyword, function) -> (QueryOperator)new InvokeOperator(keyword, function))
                .withTag("<invoke>");

        var RenderWithClause =
                rule(
                    token(SyntaxKind.WithKeyword),
                    requiredToken(SyntaxKind.OpenParenToken),
                    optional(token(SyntaxKind.CommaToken)),
                    queryParameterCommaList(QueryOperatorParameters.RenderWithProperties),
                    requiredToken(SyntaxKind.CloseParenToken),

                    (withKeyword, openParen, leadingComma, properties, closeParen) ->
                        new RenderWithClause(withKeyword, openParen, leadingComma, properties, closeParen));

        // PORT: §3.6 KustoFacts.ChartTypes.Select(c => ...).ToArray()
            List<Parser2<LexicalToken, SyntaxToken>> chartTypeTokens = new ArrayList<>();
            for (String c : KustoFacts.ChartTypes)
            {
                chartTypeTokens.add(KustoFacts.HiddenChartTypes.contains(c) ? token(c).hide() : token(c, CompletionKind.RenderChart));
            }
            var RenderChartType =
                first(
                    first(chartTypeTokens.toArray((Parser2<LexicalToken, SyntaxToken>[]) new Parser2[0])),
                    token(SyntaxKind.IdentifierToken)); // allow any identifier as a chart type and flag it later during semantic analysis (binding)

        // allow any identifier as a chart type and flag it later during semantic analysis (binding)

        var DeprecatedRenderByPropertyName =
                if_(not(or(
                        token("kind"),  // exclude other property names from possibly by-names
                        token("title"),
                        token("accumulate"),
                        token("with"))),
                    this.simpleNameReference.<NameReference>cast()); // PORT: §3.10 explicit type arguments

        var DeprecatedRenderProperty =
                first(
                    queryParameter(QueryOperatorParameters.RenderKind.hide(), false), // PORT: §3.12 named arguments
                    queryParameter(QueryOperatorParameters.RenderTitle.hide(), false), // PORT: §3.12 named arguments
                    queryParameter(QueryOperatorParameters.RenderAccumulate.hide(), false), // PORT: §3.12 named arguments
                    if_(and(token(SyntaxKind.WithKeyword).hide(), not(token(SyntaxKind.OpenParenToken))),
                        rule(token(SyntaxKind.WithKeyword).hide(), required(this.literal, (source, start) -> createMissingValue(source, start)), // PORT: §3.8 method group
                            (keyword, value) -> new NamedParameter(new NameDeclaration(new TokenName(keyword)), SyntaxToken.missing(SyntaxKind.EqualToken), value))),
                    rule(token(SyntaxKind.ByKeyword).hide(), nameReferenceList(DeprecatedRenderByPropertyName),
                        (keyword, list) -> new NamedParameter(new NameDeclaration(new TokenName(keyword)), SyntaxToken.missing(SyntaxKind.EqualToken), list)));

        RenderOperator =
                rule(
                    token(SyntaxKind.RenderKeyword, CompletionKind.QueryPrefix, CompletionPriority.High),
                    required(RenderChartType, () -> createMissingTokenText(KustoFacts.ChartTypes)), // PORT: §2.5 overload on IReadOnlyList<string>
                    list(DeprecatedRenderProperty),
                    optional(RenderWithClause),
                    (keyword, chart, parameters, withClause) -> (QueryOperator)new RenderOperator(keyword, chart, parameters, withClause, null))
                .withTag("<render>");

        PrintOperator =
                rule(
                    token(SyntaxKind.PrintKeyword, CompletionKind.QueryPrefix),
                    commaList(this.namedExpression, (source, start) -> createMissingExpression(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                    (keyword, exprs) ->
                        (QueryOperator)new PrintOperator(keyword, exprs))
                .withTag("<print>");

        AssertSchemaOperator =
                rule(
                    token(SyntaxKind.AssertSchemaKeyword, CompletionKind.QueryPrefix),
                    required(RowSchema, (source, start) -> createMissingRowSchema(source, start)), // PORT: §3.8 method group
                    (keyword, schema) ->
                        (QueryOperator)new AssertSchemaOperator(keyword, schema)).hide();
    }

    // PORT: §3.19 split
    private void initialize_QueryOperators_MacroExpandMakeGraphGraphMatch()
    {
        EntityGroup = rule(
                token(SyntaxKind.EntityGroupKeyword),
                requiredToken(SyntaxKind.OpenBracketToken),
                commaList(this.unnamedExpression, (source, start) -> createMissingExpression(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                requiredToken(SyntaxKind.CloseBracketToken),
                (keyword, open, entitiesList, close) ->
                    (Expression)(new EntityGroup(keyword, open, entitiesList, close)));

        var macroExpandScopeReferenceName =
                rule(
                    requiredToken(SyntaxKind.AsKeyword),
                    IdentifierNameDeclaration,
                    (asKeyword, macroReferenceName) -> new MacroExpandScopeReferenceName(asKeyword, macroReferenceName));

        MacroExpandOperator =
                rule(
                    token(SyntaxKind.MacroExpandKeyword, CompletionKind.QueryPrefix, CompletionPriority.High),
                    queryParameterList(QueryOperatorParameters.MacroExpandParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    first(EntityGroup, if_(ScanQualifiedEntityStart, EntityPathExpression), FunctionCall, this.simpleNameReference),
                    optional(macroExpandScopeReferenceName),
                    requiredToken(SyntaxKind.OpenParenToken),
                    this.macroExpandSubQuery,
                    requiredToken(SyntaxKind.CloseParenToken),
                    (macroExpandKeyword, parameters, entitygroup, scopeReferenceName, openParen, statementList, closeParen) ->
                        (QueryOperator)new MacroExpandOperator(macroExpandKeyword, parameters, entitygroup, scopeReferenceName, openParen, statementList, closeParen))
                .withTag("<macro-expand>");

        var MakeGraphTableAndKeyClause =
                rule(
                    InvocationExpression,
                    requiredToken(SyntaxKind.OnKeyword),
                    required(this.simpleNameReference, (source, start) -> createMissingNameReference(source, start)), // PORT: §3.8 method group
                    (table, onKeyword, column) ->
                        new MakeGraphTableAndKeyClause(table, onKeyword, (NameReference)column))
                .withTag("<table-and-key-clause>");

        var MakeGraphWithTablesAndKeysClause =
                rule(
                    token(SyntaxKind.WithKeyword),
                    commaList(MakeGraphTableAndKeyClause, (source, start) -> createMissingMakeGraphTableAndKeyClause(source, start), true, false, List.of( SyntaxKind.PartitionedByKeyword )), // PORT: §3.8 method group; §3.17 new[] -> List.of; §3.12 named arguments
                    (withKeyword, tablesAndKeys) ->
                        (MakeGraphWithClause)new MakeGraphWithTablesAndKeysClause(withKeyword, tablesAndKeys))
                .withTag("<make-graph-with-tables-and-keys-clause>");

        var MakeGraphWithImplicitIdClause =
                rule(
                    token(SyntaxKind.WithNodeIdKeyword, (CompletionKind) null, CompletionPriority.Normal, DotNet.str(SyntaxFacts.getText(SyntaxKind.WithNodeIdKeyword)) + "="), // PORT: §3.14 interpolation; §3.12 named argument; §3.12 named arguments
                    requiredToken(SyntaxKind.EqualToken),
                    required(this.simpleNameDeclaration, (source, start) -> createMissingNameDeclaration(source, start)), // PORT: §3.8 method group
                    (withNodeId, equals, name) ->
                        (MakeGraphWithClause)new MakeGraphWithImplicitIdClause(withNodeId, equals, name))
                .withTag("<make-graph-with-implicit-node-id-clause>");

        var MakeGraphPartitionedByClause =
                rule(
                    token(SyntaxKind.PartitionedByKeyword),
                    required(SimplePathExpression, (source, start) -> createMissingNameReference(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.OpenParenToken),
                    required(ContextualSubExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseParenToken),
                    (keyword, entity, openParen, subQuery, closeParen) ->
                        new MakeGraphPartitionedByClause(keyword, (NameReference)entity, openParen, subQuery, closeParen))
                .withTag("<make-graph-partitioned-by-clause>");

        MakeGraphOperator =
                rule(
                    token(SyntaxKind.MakeGraphKeyword, CompletionKind.QueryPrefix),
                    queryParameterList(QueryOperatorParameters.GraphMakeParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    required(this.simpleNameReference, (source, start) -> createMissingNameReference(source, start)), // PORT: §3.8 method group
                    required(
                        first(
                            token("-->", SyntaxKind.DashDashGreaterThanToken),
                            token("--", SyntaxKind.DashDashToken).hide()),
                        () -> createMissingToken(List.of( SyntaxKind.DashDashGreaterThanToken, SyntaxKind.DashDashToken ))), // PORT: §3.17 new[] -> List.of
                    required(this.simpleNameReference, (source, start) -> createMissingNameReference(source, start)), // PORT: §3.8 method group
                    optional(
                        first(
                            MakeGraphWithImplicitIdClause, 
                            MakeGraphWithTablesAndKeysClause)),
                    optional(MakeGraphPartitionedByClause),
                    (keyword, parameters, sourceColumn, direction, targetColumn, withClause, partitionedByClause) ->
                        (QueryOperator)new MakeGraphOperator(keyword, parameters, (NameReference)sourceColumn, direction, (NameReference)targetColumn, withClause, partitionedByClause)
                    )
                .withTag("<make-graph>");

        GraphMarkComponentsOperator =
                rule(
                    token(SyntaxKind.GraphMarkComponentsKeyword, CompletionKind.QueryPrefix),
                    queryParameterList(QueryOperatorParameters.GraphMarkComponentsParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    (graphMarkComponentsKeyword, parameters) ->
                        (QueryOperator)new GraphMarkComponentsOperator(graphMarkComponentsKeyword, parameters)
                    )
                .withTag("<graph-mark-components>");

        GraphWhereNodesOperator =
                rule(
                    token(SyntaxKind.GraphWhereNodesKeyword, CompletionKind.QueryPrefix),
                    required(this.expression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (graphWhereNodesKeyword, expression) ->
                        (QueryOperator)new GraphWhereNodesOperator(graphWhereNodesKeyword, expression)
                    )
                .withTag("<graph-where-nodes>");

        GraphWhereEdgesOperator =
                rule(
                    token(SyntaxKind.GraphWhereEdgesKeyword, CompletionKind.QueryPrefix),
                    required(this.expression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (graphWhereEdgesKeyword, expression) ->
                        (QueryOperator)new GraphWhereEdgesOperator(graphWhereEdgesKeyword, expression)
                    )
                .withTag("<graph-where-edges>");

        var GraphToTableAsClause =
                rule(
                    token(SyntaxKind.AsKeyword, CompletionKind.Keyword, CompletionPriority.Low),
                    required(this.simpleNameDeclaration, (source, start) -> createMissingNameDeclaration(source, start)), // PORT: §3.8 method group
                    (keyword, name) -> new GraphToTableAsClause(keyword, name))
                .withTag("<graph-to-table-as-clause>");

        var GraphToTableNodesClause =
                rule(
                    token(SyntaxKind.NodesKeyword, CompletionKind.Keyword),
                    optional(GraphToTableAsClause),
                    queryParameterList(QueryOperatorParameters.GraphToTableNodesParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    (keyword, asClause, parameters) -> new GraphToTableOutputClause(keyword, asClause, parameters))
                .withTag("graph-to-table-output-nodes-clause");

        var GraphToTableEdgesClause =
                rule(
                     token(SyntaxKind.GraphEdgesKeyword, CompletionKind.Keyword),
                     optional(GraphToTableAsClause),
                     queryParameterList(QueryOperatorParameters.GraphToTableEdgesParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                     (keyword, asClause, parameters) -> new GraphToTableOutputClause(keyword, asClause, parameters))
                .withTag("graph-to-table-output-edges-clause");

        var GraphToTableOutputClause =
                first(
                    GraphToTableNodesClause,
                    GraphToTableEdgesClause
                ).withTag("<graph-to-table-output-clause>");

        GraphToTableOperator =
                rule(
                    token(SyntaxKind.GraphToTableKeyword, CompletionKind.QueryPrefix),
                    commaList(GraphToTableOutputClause, (source, start) -> createMissingGraphToTableOutputClause(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                    (keyword, outputClause) -> (QueryOperator)new GraphToTableOperator(keyword, outputClause))
                .withTag("<graph-to-table>");

        var WhereClause =
                rule(
                    token(SyntaxKind.WhereKeyword),
                    required(this.expression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (keyword, expression) ->
                        new WhereClause(keyword, expression));

        var ProjectClause =
                rule(
                    token(SyntaxKind.ProjectKeyword),
                    commaList(this.namedExpression, (source, start) -> createMissingExpression(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                    (keyword, list) ->
                        new ProjectClause(keyword, list));

        var GraphMatchPatternEdgeRange =
                rule(
                    token(SyntaxKind.AsteriskToken),
                    required(InvocationExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.DotDotToken),
                    required(InvocationExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (asterisk, rangeStart, dotDotToken, rangeEnd) ->
                        new GraphMatchPatternEdgeRange(asterisk, rangeStart, dotDotToken, rangeEnd));

        var GraphMatchPatternEdge =
                first(
                    rule(
                        first(
                            matchText("-->", SyntaxKind.DashDashGreaterThanToken),
                            matchText("<--", SyntaxKind.LessThanDashDashToken),
                            matchText("--", SyntaxKind.DashDashToken)),
                        (token) -> (GraphMatchPatternNotation)new GraphMatchPatternEdge(token, null, null, null)),
                    rule(
                        first(
                            matchText("<-[", SyntaxKind.LessThanDashBracketToken),
                            matchText("-[", SyntaxKind.DashBracketToken)),
                        optional(this.simpleNameDeclaration),
                        optional(GraphMatchPatternEdgeRange),
                        first(
                            matchText("]->", SyntaxKind.BracketDashGreaterThanToken),
                            matchText("]-", SyntaxKind.BracketDashToken)),
                        (firstToken, name, range, lastToken) ->
                            (GraphMatchPatternNotation)new GraphMatchPatternEdge(firstToken, name, range, lastToken))
                    );

        var GraphMatchPatternNode =
                rule(
                    token(SyntaxKind.OpenParenToken),
                    optional(this.simpleNameDeclaration),
                    token(SyntaxKind.CloseParenToken),
                    (open, name, close) ->
                        (GraphMatchPatternNotation)new GraphMatchPatternNode(open, name, close));

        var GraphMatchPattern = withCompletion(rule(
                list(
                    first(GraphMatchPatternNode, GraphMatchPatternEdge),
                    (source, start) -> createMissingGraphMatchPatternNotation(source, start), // PORT: §3.8 method group
                    true), // PORT: §3.12 named arguments
                (elements) -> new GraphMatchPattern(elements)), new CompletionItem(CompletionKind.Syntax, "(n1)-[e]->(n2)"),
                    new CompletionItem(CompletionKind.Syntax, "(n1)-[e1]->(n2)-[e2]->(n3)"),
                    new CompletionItem(CompletionKind.Syntax, "(n1)-[e*1..3]->(n2)")); // PORT: §3.5 extension method

        var GraphMatchPatternClause =
                commaList(
                   GraphMatchPattern,
                    (source, start) -> createMissingGraphMatchPattern(source, start), // PORT: §3.8 method group
                    true, false,
                    List.of( SyntaxKind.WhereKeyword, SyntaxKind.ProjectKeyword )).withTag("<graph-match-pattern-clause>"); // PORT: §3.17 new[] -> List.of; §3.12 named arguments

        GraphMatchOperator =
                rule(
                    token(SyntaxKind.GraphMatchKeyword, CompletionKind.QueryPrefix),
                    queryParameterList(QueryOperatorParameters.GraphMatchParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    GraphMatchPatternClause,
                    optional(WhereClause),
                    optional(ProjectClause),
                    (keyword, parameters, patterns, whereClause, projectClause) ->
                        (QueryOperator)new GraphMatchOperator(keyword, parameters, patterns, whereClause, projectClause))
                .withTag("<graph-match-operator>");

        GraphShortestPathsOperator =
                rule(
                    token(SyntaxKind.GraphShortestPathsKeyword, CompletionKind.QueryPrefix),
                    queryParameterList(QueryOperatorParameters.GraphShortestPathsParameters, AllowedNameKind.DeclaredOrKnown, true), // PORT: §3.12 named arguments
                    GraphMatchPatternClause,
                    optional(WhereClause),
                    optional(ProjectClause),
                    (keyword, parameters, patterns, whereClause, projectClause) ->
                        (QueryOperator)new GraphShortestPathsOperator(keyword, parameters, patterns, whereClause, projectClause))
                .withTag("<graph-shortest-paths-operator>");
    }

    // PORT: §3.19 split
    private void initialize_QueryOperators_PipeAndQueryOperator()
    {
        var PrePipeQueryOperator =
                first(
                    EvaluateOperator,
                    FindOperator,
                    SearchOperator,
                    UnionOperator,
                    MacroExpandOperator,
                    RangeOperator,
                    PrintOperator);

        var BadQueryOperator =
                rule(
                    token(SyntaxKind.IdentifierToken),
                    id -> (QueryOperator)new BadQueryOperator((SyntaxToken)id, List.of( DiagnosticFacts.getQueryOperatorExpected() ))); // PORT: §3.17 new[] -> List.of

        var PostPipeQueryOperator =
                first(
                    AsOperator,
                    AssertSchemaOperator,
                    ConsumeOperator,
                    CountOperator,
                    DistinctOperator,
                    EvaluateOperator,
                    ExecuteAndCacheOperator,
                    ExtendOperator,
                    FacetOperator,
                    FilterOperator,
                    ForkOperator,
                    GetSchemaOperator,
                    GraphMatchOperator,
                    GraphShortestPathsOperator,
                    GraphMarkComponentsOperator,
                    // currently hidden until we document this feature.
                    GraphWhereNodesOperator.hide(),
                    GraphWhereEdgesOperator.hide(),
                    GraphToTableOperator,
                    InvokeOperator,
                    JoinOperator,
                    LookupOperator,
                    MakeGraphOperator,
                    MakeSeriesOperator,
                    MvApplyOperator,
                    MvExpandOperator,
                    ParseOperator,
                    ParseWhereOperator,
                    ParseKvOperator,
                    PartitionByOperator,
                    PartitionOperator,
                    ProjectOperator,
                    ProjectAwayOperator,
                    ProjectByNamesOperator,
                    ProjectKeepOperator,
                    ProjectRenameOperator,
                    ProjectReorderOperator,
                    ReduceByOperator,
                    RenderOperator,
                    SampleOperator,
                    SampleDistinctOperator,
                    ScanOperator,
                    SearchOperator,
                    SerializeOperator,
                    SortOperator,
                    SummarizeOperator,
                    TakeOperator,
                    TopHittersOperator,
                    TopOperator,
                    TopNestedOperator,
                    UnionOperator);

        // all operators

        this.queryOperator =
                first(PrePipeQueryOperator, PostPipeQueryOperator);

        ForkPipeOperatorCore =
                first(
                    CountOperator,
                    ExtendOperator,
                    FilterOperator,
                    ParseOperator,
                    ParseWhereOperator,
                    ParseKvOperator,
                    TakeOperator,
                    TopNestedOperator,
                    ProjectOperator,
                    ProjectAwayOperator,
                    ProjectByNamesOperator,
                    ProjectKeepOperator,
                    ProjectRenameOperator,
                    ProjectReorderOperator,
                    SummarizeOperator,
                    DistinctOperator,
                    TopHittersOperator,
                    TopOperator,
                    SortOperator,
                    MvExpandOperator,
                    MvApplyOperator,
                    ReduceByOperator,
                    SampleOperator,
                    SampleDistinctOperator,
                    AsOperator,
                    InvokeOperator,
                    ExecuteAndCacheOperator,
                    ScanOperator,
                    this.queryOperator.hide());

        // allow other query operators to parser, but fail in binding

        ForkPipeExpressionCore =
                applyZeroOrMore(
                    rule(ForkPipeOperator, o -> (Expression)o),
                    _left ->
                        rule(_left, token(SyntaxKind.BarToken), required(ForkPipeOperator, (source, start) -> createMissingQueryOperator(source, start)), // PORT: §3.8 method group
                            (left, pipeToken, right) -> (Expression)new PipeExpression(left, pipeToken, right)));

        var InitialPipeElementExpression =
                first(
                    rule(PrePipeQueryOperator, o -> (Expression)o),
                    if_(not(ScanSimpleName), // allow post-pipe operators that don't start with a legal name to parse and fail in binding
                        rule(PostPipeQueryOperator, o -> (Expression)o).hide()), 
                    this.unnamedExpression);

        this.followingPipeElementExpression =
                first(
                    PostPipeQueryOperator,
                    PrePipeQueryOperator.hide(), // allow any pre-pipe operator, but fail in binding
                    BadQueryOperator.hide());

        // allow these to parse, but fail in binding

        PipeExpressionCore =
                applyZeroOrMore(
                    InitialPipeElementExpression,
                    _left ->
                        rule(_left, token(SyntaxKind.BarToken), required(this.followingPipeElementExpression, (source, start) -> createMissingQueryOperator(source, start)), // PORT: §3.8 method group
                            (left, op, right) -> (Expression)new PipeExpression(left, op, right)));

        PipeSubExpressionCore =
                applyZeroOrMore(
                    first(
                        PostPipeQueryOperator.<Expression>cast(), // PORT: §3.10 explicit type arguments
                        InitialPipeElementExpression.hide()),
                    _left ->
                        rule(_left, token(SyntaxKind.BarToken), required(this.followingPipeElementExpression, (source, start) -> createMissingQueryOperator(source, start)), // PORT: §3.8 method group
                            (left, op, right) -> (Expression)new PipeExpression(left, op, right)));

        ContextualSubExpressionCore =
                first(
                    applyZeroOrMore(
                        ContextualDataTableExpression,
                        _left ->
                            rule(_left, token(SyntaxKind.BarToken), required(this.followingPipeElementExpression, (source, start) -> createMissingQueryOperator(source, start)), // PORT: §3.8 method group
                                (left, op, right) -> (Expression)new PipeExpression(left, op, right))),
                    this.pipeSubExpression);

        UnnamedExpressionCore =
                LogicalOr;

        ExpressionCore =
                PipeExpressionCore;

        // endregion
    }

    // PORT: §3.19 split
    private void initialize_Statements()
    {
        // region Statements

        var AliasStatement =
                rule(
                    token(SyntaxKind.AliasKeyword, CompletionKind.QueryPrefix).hide(),
                    requiredToken(SyntaxKind.DatabaseKeyword),
                    required(this.simpleNameDeclaration, (source, start) -> createMissingNameDeclaration(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.EqualToken),
                    required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (aliasKeyword, databaseKeyword, name, equalToken, expression) ->
                        (Statement)new AliasStatement(aliasKeyword, databaseKeyword, name, equalToken, expression))
                .withTag("<alias>");

        var MaterializeExpression =
                rule(
                    token(SyntaxKind.MaterializeKeyword),
                    requiredToken(SyntaxKind.OpenParenToken),
                    required(this.pipeExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseParenToken),
                    (keyword, openParen, expr, closeParen) ->
                        (Expression)new MaterializeExpression(keyword, openParen, expr, closeParen));

        var DefaultValueDeclaration =
                rule(
                    token(SyntaxKind.EqualToken),
                    required(first(this.literal, NameTokenLiteral), (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (equalToken, value) -> new DefaultValueDeclaration(equalToken, value));

        FunctionParameterCore =
                rule(
                    NameAndTypeDeclaration,
                    optional(DefaultValueDeclaration),
                    (nameAndType, defaultValue) -> new FunctionParameter(nameAndType, defaultValue));

        this.functionParameters =
                rule(
                    requiredToken(SyntaxKind.OpenParenToken),
                    commaList(FunctionParameter, (source, start) -> createMissingFunctionParameter(source, start), false), // PORT: §3.8 method group; §3.12 named arguments
                    requiredToken(SyntaxKind.CloseParenToken),
                    (openParen, parameters, closeParen) -> new FunctionParameters(openParen, parameters, closeParen));

        var FunctionBodyStatement =
                first(
                    rule(LetStatement, requiredToken(SyntaxKind.SemicolonToken),
                        (statement, semicolon) -> new SeparatedElement1<Statement>(statement, semicolon)),
                    rule(DeclareQueryParametersStatement, requiredToken(SyntaxKind.SemicolonToken),
                        (statement, semicolon) -> new SeparatedElement1<Statement>(statement, semicolon)));

        var FunctionBodyStatementList =
                list(FunctionBodyStatement, (source, start) -> createMissingStatementElement(source, start), false) // PORT: §3.8 method group; §3.12 named arguments
                .withTag("<statement-list>");

        this.functionBody =
                rule(
                    requiredToken(SyntaxKind.OpenBraceToken),
                    FunctionBodyStatementList,
                    optional(this.expression),
                    optional(token(SyntaxKind.SemicolonToken)),
                    requiredToken(SyntaxKind.CloseBraceToken),
                    (openBrace, statements, expr, semicolon, closeBrace) ->
                        new FunctionBody(openBrace, statements, expr, semicolon, closeBrace));

        var FunctionDeclaration =
                rule(
                    optional(token(SyntaxKind.ViewKeyword)),
                    this.functionParameters,
                    this.functionBody,
                    (view, parameters, body) -> new FunctionDeclaration(view, parameters, body))
                .withTag("<function-declaration>");

        LetStatementCore =
                first(
                    // looks like let with function declaration?
                    if_(
                        and(token(SyntaxKind.LetKeyword, CompletionKind.QueryPrefix),
                            ScanSimpleName,
                            token(SyntaxKind.EqualToken),
                            optional(token(SyntaxKind.ViewKeyword)),
                            token(SyntaxKind.OpenParenToken),
                            or(token(SyntaxKind.CloseParenToken), token(SyntaxKind.AsteriskToken), and(ScanSimpleName, token(SyntaxKind.ColonToken)))),
                        rule(
                            token(SyntaxKind.LetKeyword),
                            this.simpleNameDeclaration,
                            token(SyntaxKind.EqualToken),
                            FunctionDeclaration,
                            (letKeyword, name, equal, expression) ->
                                (Statement)new LetStatement(letKeyword, name, equal, expression))),
                    // let with materialize?
                    if_(
                        and(
                            token(SyntaxKind.LetKeyword, CompletionKind.QueryPrefix),
                            ScanSimpleName,
                            token(SyntaxKind.EqualToken),
                            token(SyntaxKind.MaterializeKeyword)),
                        rule(
                            token(SyntaxKind.LetKeyword),
                            required(this.simpleNameDeclaration, (source, start) -> createMissingNameDeclaration(source, start)), // PORT: §3.8 method group
                            token(SyntaxKind.EqualToken),
                            MaterializeExpression,
                            (keyword, name, equal, expr) ->
                                (Statement)new LetStatement(keyword, name, equal, expr))),
                    if_(
                        and(
                            token(SyntaxKind.LetKeyword, CompletionKind.QueryPrefix),
                            ScanSimpleName,
                            token(SyntaxKind.EqualToken),
                            token(SyntaxKind.EntityGroupKeyword)),
                        rule(
                            token(SyntaxKind.LetKeyword),
                            required(this.simpleNameDeclaration, (source, start) -> createMissingNameDeclaration(source, start)), // PORT: §3.8 method group
                            token(SyntaxKind.EqualToken),
                            EntityGroup,
                            (keyword, name, equal, expr) ->
                                (Statement)new LetStatement(keyword, name, equal, expr))),
                    // otherwise regular let statement
                    rule(
                        token(SyntaxKind.LetKeyword, CompletionKind.QueryPrefix),
                        required(this.simpleNameDeclaration, (source, start) -> createMissingNameDeclaration(source, start)), // PORT: §3.8 method group
                        requiredToken(SyntaxKind.EqualToken),
                        required(this.expression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                        (letKeyword, name, equalToken, expression) ->
                            (Statement)new LetStatement(letKeyword, name, equalToken, expression)));

        var OptionValueClause =
                rule(
                    token(SyntaxKind.EqualToken),
                    required(this.unnamedExpression, (source, start) -> createMissingExpression(source, start)), // PORT: §3.8 method group
                    (equal, expr) -> new OptionValueClause(equal, expr));

        var SetOptionStatement =
                rule(
                    token(SyntaxKind.SetKeyword, CompletionKind.QueryPrefix),
                    required(this.simpleNameDeclaration, (source, start) -> createMissingNameDeclaration(source, start)), // PORT: §3.8 method group
                    optional(OptionValueClause),
                    (keyword, name, value) ->
                        (Statement)new SetOptionStatement(keyword, name, value))
                .withTag("<set-option>");

        DeclareQueryParametersStatementCore =
                rule(
                    token(SyntaxKind.DeclareKeyword, CompletionKind.QueryPrefix).hide(),
                    requiredToken(SyntaxKind.QueryParametersKeyword),
                    requiredToken(SyntaxKind.OpenParenToken),
                    commaList(FunctionParameter, (source, start) -> createMissingFunctionParameter(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                    requiredToken(SyntaxKind.CloseParenToken),
                    (declareKeyword, queryParametersKeyword, open, list, close) ->
                        (Statement)new QueryParametersStatement(declareKeyword, queryParametersKeyword, open, list, close));

        var Restriction =
                withCompletionHint(first(
                    if_(ScanWildcardedEntityReferenceOrFunctionCall, WildcardedEntityReference),
                    this.simpleNameReference), CompletionHint.Table | CompletionHint.MaterializedView | CompletionHint.ExternalTable | CompletionHint.GraphModel); // PORT: §3.5 extension method

        var RestrictStatementWithClause = 
                rule(
                    token(SyntaxKind.WithKeyword),
                    requiredToken(SyntaxKind.OpenParenToken),
                    queryParameterCommaList(QueryOperatorParameters.RestrictStatementParameters),
                    requiredToken(SyntaxKind.CloseParenToken),
                    (withKeyword, openParen, properties, closeParen) ->
                        new RestrictStatementWithClause(withKeyword, openParen, properties, closeParen));

        var RestrictStatement =
                rule(
                    token(SyntaxKind.RestrictKeyword, CompletionKind.QueryPrefix).hide(),
                    requiredToken(SyntaxKind.AccessKeyword),
                    requiredToken(SyntaxKind.ToKeyword),
                    requiredToken(SyntaxKind.OpenParenToken),
                    SyntaxParsers.<Expression>commaList(Restriction, (source, start) -> createMissingExpression(source, start), true), // PORT: §3.10 explicit type arguments; §3.8 method group; §3.12 named arguments
                    requiredToken(SyntaxKind.CloseParenToken),
                    optional(RestrictStatementWithClause),
                    (restrictKeyword, accessKeyword, toKeyword, openParen, list, closeParen, withProperties) ->
                        (Statement)new RestrictStatement(restrictKeyword, accessKeyword, toKeyword, openParen, list, closeParen, withProperties))
                .withTag("<restrict>");

        var PatternPathValue =
                rule(
                    token(SyntaxKind.DotToken),
                    requiredToken(SyntaxKind.OpenBracketToken),
                    required(this.stringLiteral, (source, start) -> createMissingStringLiteral(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseBracketToken),
                    (dot, openBracket, value, closeBracket) ->
                        new PatternPathValue(dot, openBracket, value, closeBracket));

        var PatternMatchStatementElement =
                rule(
                    LetStatement,
                    requiredToken(SyntaxKind.SemicolonToken),
                    (statement, semicolon) ->
                        new SeparatedElement1<Statement>(statement, semicolon));

        var PatternMatchBody =
                rule(
                    requiredToken(SyntaxKind.OpenBraceToken),
                    list(PatternMatchStatementElement, (source, start) -> createMissingStatementElement(source, start), false), // PORT: §3.8 method group; §3.12 named arguments
                    optional(this.expression),
                    optional(token(SyntaxKind.SemicolonToken)),
                    requiredToken(SyntaxKind.CloseBraceToken),
                    (open, statements, expression, semi, close) ->
                        new FunctionBody(open, statements, expression, semi, close));

        var PatternMatchValue =
                first(
                    this.stringLiteral,
                    this.unnamedExpression.hide());

        var PatternMatch =
                rule(
                    token(SyntaxKind.OpenParenToken),
                    SyntaxParsers.<Expression>commaList(PatternMatchValue, (source, start) -> createMissingStringLiteral(source, start), true), // PORT: §3.10 explicit type arguments; §3.8 method group; §3.12 named arguments
                    requiredToken(SyntaxKind.CloseParenToken),
                    optional(PatternPathValue),
                    requiredToken(SyntaxKind.EqualToken),
                    PatternMatchBody,
                    requiredToken(SyntaxKind.SemicolonToken),
                    (openParen, exprs, closeParen, path, equalToken, body, semicolon) ->
                        new PatternMatch(new ExpressionList(openParen, exprs, closeParen), path, equalToken, body, semicolon));

        var PatternPathParameter =
                rule(
                    token(SyntaxKind.OpenBracketToken),
                    required(NameAndTypeDeclaration, (source, start) -> createMissingNameAndTypeDeclaration(source, start)), // PORT: §3.8 method group
                    requiredToken(SyntaxKind.CloseBracketToken),
                    (openBracket, parameter, closeBracket) ->
                        new PatternPathParameter(openBracket, parameter, closeBracket));

        var PatternDeclaration =
                rule(
                    token(SyntaxKind.EqualToken),
                    requiredToken(SyntaxKind.OpenParenToken),
                    commaList(NameAndTypeDeclaration, (source, start) -> createMissingNameAndTypeDeclaration(source, start), false), // PORT: §3.8 method group; §3.12 named arguments
                    requiredToken(SyntaxKind.CloseParenToken),
                    optional(PatternPathParameter),
                    requiredToken(SyntaxKind.OpenBraceToken),
                    list(PatternMatch, (source, start) -> createMissingPatternMatch(source, start), true), // PORT: §3.8 method group; §3.12 named arguments
                    requiredToken(SyntaxKind.CloseBraceToken),

                    (equal, openParen, parameters, closeParen, pathParameter, openBrace, patterns, closeBrace) ->
                        new PatternDeclaration(equal, openParen, parameters, closeParen, pathParameter, openBrace, patterns, closeBrace));

        var DeclarePatternStatement =
                rule(
                    token(SyntaxKind.DeclareKeyword, CompletionKind.QueryPrefix).hide(),
                    token(SyntaxKind.PatternKeyword),
                    required(this.simpleNameDeclaration, (source, start) -> createMissingNameDeclaration(source, start)), // PORT: §3.8 method group
                    optional(PatternDeclaration),
                    (declareKeyword, patternKeyword, name, pattern) ->
                        (Statement)new PatternStatement(declareKeyword, patternKeyword, name, pattern))
                .withTag("<pattern-statement>");

        var QueryStatement =
                rule(
                    this.expression,
                    expr -> (Statement)new ExpressionStatement(expr));

        var PrimaryPathSelector =
                applyOptional(
                    PathElementSelector,
                    _left ->
                        rule(
                            _left,
                            dataScopeClause(CompletionKind.TabularSuffix),
                            (path, clause) ->
                                (Expression)new DataScopeExpression(path, clause)));

        var ParenthesizedSummarizeOperator =
                rule(
                    token("("),
                    required(SummarizeOperator, (source, start) -> createMissingQueryOperator(source, start)), // PORT: §3.8 method group
                    requiredToken(")"),
                    (openParen, summarize, closeParen) ->
                        (Expression)new ParenthesizedExpression(openParen, summarize, closeParen));

        var MaterializedViewCombineViewNameClause =
                rule(
                    requiredToken(SyntaxKind.OpenParenToken),
                    withCompletionHint(required(this.expression, (source, start) -> createMissingExpression(source, start)), CompletionHint.Literal), // PORT: §3.8 method group; §3.5 extension method
                    requiredToken(SyntaxKind.CloseParenToken),
                    (open, expression, close) ->
                        new MaterializedViewCombineNameClause(open, expression, close));

        var MaterializedViewCombineBaseClause =
                rule(
                    withCompletion(token("base"), new CompletionItem(CompletionKind.Syntax, "base", "base (", ")")), // PORT: §3.5 extension method
                    requiredToken(SyntaxKind.OpenParenToken),
                    withCompletionHint(required(this.expression, (source, start) -> createMissingExpression(source, start)), CompletionHint.Table), // PORT: §3.8 method group; §3.5 extension method
                    requiredToken(SyntaxKind.CloseParenToken),
                    (keyword, open, expression, close) ->
                        new MaterializedViewCombineClause(keyword, open, expression, close));

        var MaterializedViewCombineDeltaClause =
                rule(
                    withCompletion(token("delta"), new CompletionItem(CompletionKind.Syntax, "delta", "delta (", ")")), // PORT: §3.5 extension method
                    requiredToken(SyntaxKind.OpenParenToken),
                    withCompletionHint(required(this.expression, (source, start) -> createMissingExpression(source, start)), CompletionHint.NonScalar), // PORT: §3.8 method group; §3.5 extension method
                    requiredToken(SyntaxKind.CloseParenToken),
                    (keyword, open, expression, close) ->
                        new MaterializedViewCombineClause(keyword, open, expression, close));

        var MaterializedViewCombineAggregationsClause =
                rule(
                    withCompletion(token("aggregations"), new CompletionItem(CompletionKind.Syntax, "aggregations", "aggregations (summarize ", ")")), // PORT: §3.5 extension method
                    requiredToken(SyntaxKind.OpenParenToken),
                    withCompletionHint(required(SummarizeOperator, (source, start) -> createMissingQueryOperator(source, start)), CompletionHint.Query), // PORT: §3.8 method group; §3.5 extension method
                    requiredToken(SyntaxKind.CloseParenToken),
                    (keyword, open, summarize, close) ->
                        new MaterializedViewCombineClause(keyword, open, summarize, close));

        var MaterializedViewCombineExpression =
                rule(
                    token(SyntaxKind.MaterializedViewCombineKeyword, CompletionKind.TabularPrefix).hide(),
                    required(MaterializedViewCombineViewNameClause, (source, start) -> createMissingMaterializedViewCombineNameClause(source, start)), // PORT: §3.8 method group
                    required(MaterializedViewCombineBaseClause, (source, start) -> createMissingMaterializedViewCombineBaseClause(source, start)), // PORT: §3.8 method group
                    required(MaterializedViewCombineDeltaClause, (source, start) -> createMissingMaterializedViewCombineDeltaClause(source, start)), // PORT: §3.8 method group
                    required(MaterializedViewCombineAggregationsClause, (source, start) -> createMissingMaterializedViewCombineAggregatesClause(source, start)), // PORT: §3.8 method group
                    (keyword, viewname, baseClause, deltaClause, aggregatesClause) ->
                        (Expression)new MaterializedViewCombineExpression(keyword, viewname, baseClause, deltaClause, aggregatesClause));

        var ScanKeywordInNamePosition =
                match((source, start) ->
                    QueryParser.isKeywordInNamePosition(source, start) ? 1 : -1);

        PrimaryExpressionCore =
                first(
                    this.literal,
                    ParenthesizedExpression,
                    DataTableExpression,
                    ContextualDataTableExpression,
                    ExternalDataExpression,
                    InlineExternalTableExpression,
                    MaterializedViewCombineExpression,
                    PrimaryPathSelector,
                    InvalidKeywordAsNameReference);

        this.statement =
                first(
                    AliasStatement,
                    LetStatement,
                    SetOptionStatement,
                    DeclarePatternStatement,
                    DeclareQueryParametersStatement,
                    RestrictStatement,
                    QueryStatement)
                .withTag("<statement>");

        // endregion
    }

    // PORT: §3.19 split
    private void initialize_QueryBlock()
    {
        // region QueryBlock

        this.statementList =
                separatedList(
                    this.statement, SyntaxKind.SemicolonToken, 
                    (source, start) -> createMissingStatement(source, start), // PORT: §3.8 method group
                    EndOfText, false,
                    true); // PORT: §3.12 named arguments

        this.skippedTokens =
                convertList( // PORT: §2.5 Convert(Parser, Func<IReadOnlyList<I>,O>)
                    oneOrMore(AnyTokenButEnd),
                    (List<LexicalToken> list) ->
                    {
                        // PORT: §3.6 list.Select((tok, i) => i == 0 ? SyntaxToken.From(tok, DiagnosticFacts.GetIncompleteFragment()) : SyntaxToken.From(tok))
                        List<SyntaxToken> tokens = new ArrayList<>();
                        for (int i = 0; i < list.size(); i++)
                        {
                            LexicalToken tok = list.get(i);
                            tokens.add(i == 0 // only tag first token with diagnostic
                                ? SyntaxToken.from(tok, DiagnosticFacts.getIncompleteFragment())
                                : SyntaxToken.from(tok));
                        }
                        return new SkippedTokens(new SyntaxList1<SyntaxToken>(tokens));
                    })
                .withTag("<skipped-tokens>");

        this.directive =
                rule(
                    token(SyntaxKind.DirectiveToken),
                    token -> new Directive(token));

        this.queryBlock =
                rule(
                    list(this.directive),
                    this.statementList,
                    optional(this.skippedTokens),
                    optional(token(SyntaxKind.EndOfTextToken)),

                    (directives, statements, skipped, end) ->
                        new QueryBlock(directives, statements, skipped, end));

        // endregion
    }

    // region local functions
    // PORT: §3.12 the local functions of Initialize (QueryGrammar.cs:853-1957) become private methods placed after the
    // initialize_* methods, with telescoping overloads. They read the PORT §3.19 split fields declared at the top of the class.

    // PORT: §3.12 local function QParameter (QueryGrammar.cs:853)
    private Parser2<LexicalToken, NamedParameter> qParameter(
        Parser2<LexicalToken, SyntaxToken> tokenParser,
        boolean hasEquals,
        boolean equalsNeeded,
        Parser2<LexicalToken, Expression> valueParser,
        BiFunction<Source<LexicalToken>, Integer, Expression> fnMissingValue,
        int expressionHint)
    {
        // PORT: §3.14 `fnMissingValue ?? CreateMissingValue` hoisted (it is evaluated identically at all three sites)
        BiFunction<Source<LexicalToken>, Integer, Expression> missingValue =
            fnMissingValue != null ? fnMissingValue : (source, start) -> createMissingValue(source, start);

        if (hasEquals)
        {
            if (equalsNeeded)
            {
                return
                    if_(and(tokenParser, token(SyntaxKind.EqualToken)),
                        rule(
                            asIdentifierNameDeclaration(tokenParser),
                            requiredToken(SyntaxKind.EqualToken),
                            required(valueParser, missingValue),
                            (name, equal, value) ->
                                new NamedParameter(name, equal, value, expressionHint)));
            }
            else
            {
                return rule(
                    asIdentifierNameDeclaration(tokenParser),
                    requiredToken(SyntaxKind.EqualToken),
                    required(valueParser, missingValue),
                    (name, equal, value) ->
                        new NamedParameter(name, equal, value, expressionHint));
            }
        }
        else
        {
            return
                rule(
                    asIdentifierNameDeclaration(tokenParser),
                    required(valueParser, missingValue),
                    (name, value) ->
                        new NamedParameter(name, SyntaxToken.missing(SyntaxKind.EqualToken), value, expressionHint));
        }
    }

    private Parser2<LexicalToken, NamedParameter> qParameter( // PORT: §3.12 optional parameter expressionHint = CompletionHint.None
        Parser2<LexicalToken, SyntaxToken> tokenParser,
        boolean hasEquals,
        boolean equalsNeeded,
        Parser2<LexicalToken, Expression> valueParser,
        BiFunction<Source<LexicalToken>, Integer, Expression> fnMissingValue)
    {
        return qParameter(tokenParser, hasEquals, equalsNeeded, valueParser, fnMissingValue, CompletionHint.None);
    }

    private Parser2<LexicalToken, NamedParameter> qParameter( // PORT: §3.12 optional parameter fnMissingValue = null
        Parser2<LexicalToken, SyntaxToken> tokenParser,
        boolean hasEquals,
        boolean equalsNeeded,
        Parser2<LexicalToken, Expression> valueParser)
    {
        return qParameter(tokenParser, hasEquals, equalsNeeded, valueParser, null, CompletionHint.None);
    }

    // PORT: §3.12 local function NameReferenceList (QueryGrammar.cs:895)
    private Parser2<LexicalToken, Expression> nameReferenceList(Parser2<LexicalToken, NameReference> nameParser)
    {
        return rule(
            oneOrMoreCommaList(nameParser, (source, start) -> (NameReference) createMissingNameReference(source, start)),
            list -> (Expression) new NameReferenceList(list));
    }

    // PORT: §3.12 local function QueryParameterName (QueryGrammar.cs:913)
    @SuppressWarnings("unchecked")
    private Parser2<LexicalToken, SyntaxToken> queryParameterName(QueryOperatorParameter parameter)
    {
        Parser2<LexicalToken, SyntaxToken> parser;

        if (parameter.isHidden())
        {
            parser = hiddenToken(parameter.name());
        }
        else if (parameter.hasNoEquals())
        {
            parser = token(parameter.name());
        }
        else if (parameter.valueKind() == QueryOperatorParameterValueKind.StringLiteral)
        {
            parser = token(parameter.name(), (SyntaxKind) null, (CompletionKind) null, CompletionPriority.Normal, DotNet.str(parameter.name()) + "=\"|\""); // PORT: §3.12 named argument ctext; §3.14 interpolation
        }
        else
        {
            parser = token(parameter.name(), (SyntaxKind) null, (CompletionKind) null, CompletionPriority.Normal, DotNet.str(parameter.name()) + "="); // PORT: §3.12 named argument ctext; §3.14 interpolation
        }

        // add aliases if any, but hide them from intellisense
        if (parameter.aliases().size() > 0)
        {
            // PORT: §3.6 new[] { parser }.Concat(parameter.Aliases.Select(a => HiddenToken(a))).ToArray()
            List<Parser2<LexicalToken, SyntaxToken>> all = new ArrayList<>();
            all.add(parser);
            for (String a : parameter.aliases())
            {
                all.add(hiddenToken(a));
            }
            parser = first(all.toArray((Parser2<LexicalToken, SyntaxToken>[]) new Parser2[0]));
        }

        return parser;
    }

    // PORT: §3.12 local function QueryParameter (QueryGrammar.cs:943)
    private Parser2<LexicalToken, NamedParameter> queryParameter(
        QueryOperatorParameter parameter,
        boolean equalsNeeded,  // if true then equals is needed when deciding if the name starts a parameter (unless the parameter does not uses equals syntax)
        List<QueryOperatorParameter> allParameters)
    {
        boolean hasEquals = !parameter.hasNoEquals();

        switch (parameter.valueKind())
        {
            case Any:
                return qParameter(
                    queryParameterName(parameter),
                    hasEquals, equalsNeeded,
                    AnyQueryOperatorParameterValue,
                    (source, start) -> createMissingValue(source, start));
            case StringLiteral:
                return qParameter(
                    queryParameterName(parameter),
                    hasEquals, equalsNeeded,
                    AnyQueryOperatorParameterValue,
                    (source, start) -> createMissingStringLiteral(source, start));
            case BoolLiteral:
                return qParameter(
                    queryParameterName(parameter),
                    hasEquals, equalsNeeded,
                    first(BooleanLiteralWithCompletion, AnyQueryOperatorParameterValue),
                    (source, start) -> createMissingBooleanLiteral(source, start));
            case IntegerLiteral:
            case NumericLiteral:
            case SummableLiteral:
                return qParameter(
                    queryParameterName(parameter),
                    hasEquals, equalsNeeded,
                    AnyQueryOperatorParameterValue,
                    (source, start) -> createMissingLongLiteral(source, start));
            case ForcedRealLiteral:
                return qParameter(
                    queryParameterName(parameter),
                    hasEquals, equalsNeeded,
                    AnyQueryOperatorParameterForcedRealValue,
                    (source, start) -> createMissingRealLiteral(source, start));
            case ScalarLiteral:
                return qParameter(
                    queryParameterName(parameter),
                    hasEquals, equalsNeeded,
                    AnyQueryOperatorParameterValue);
            case String:
                return qParameter(
                    queryParameterName(parameter),
                    hasEquals, equalsNeeded,
                    FunctionCallOrPath);
            case Word:
            case WordOrNumber:
                return parameter.values().size() > 0
                    ? qParameter(
                        queryParameterName(parameter),
                        hasEquals, equalsNeeded,
                        first(asTokenLiteral(tokenText(parameter.values())), AnyQueryOperatorParameterValue),
                        createMissingTokenLiteral(parameter.values()))
                    : qParameter(
                        queryParameterName(parameter),
                        hasEquals, equalsNeeded,
                        AnyQueryOperatorParameterValue,
                        createMissingTokenLiteral("token"));
            case NameDeclaration:
                return qParameter(
                    queryParameterName(parameter),
                    hasEquals, equalsNeeded,
                    first(this.simpleNameDeclarationExpression, AnyQueryOperatorParameterValue),
                    (source, start) -> createMissingNameDeclarationExpression(source, start));
            case Column:
                return qParameter(
                    queryParameterName(parameter),
                    hasEquals, equalsNeeded,
                    first(this.simpleNameReference, AnyQueryOperatorParameterValue),
                    (source, start) -> createMissingNameReference(source, start),
                    CompletionHint.Column);
            case ColumnList:
            {
                // PORT: §3.6 allParameters.Select(p => p.Name).ToList()
                List<String> allParameterNames = new ArrayList<>();
                for (QueryOperatorParameter p : allParameters)
                {
                    allParameterNames.add(p.name());
                }
                var nameRule = if_(not(and(tokenText(allParameterNames), token(SyntaxKind.EqualToken))), ExtendedNameReference.<NameReference>cast());
                var nameList = nameReferenceList(nameRule);
                return qParameter(
                    queryParameterName(parameter),
                    hasEquals, equalsNeeded,
                    first(nameList, AnyQueryOperatorParameterValue),
                    null,
                    CompletionHint.Column);
            }
            default:
                throw new IllegalStateException("Unhandled query operator parameter kind: " + parameter.valueKind());
        }
    }

    private Parser2<LexicalToken, NamedParameter> queryParameter( // PORT: §3.12 optional parameter allParameters = null
        QueryOperatorParameter parameter,
        boolean equalsNeeded)
    {
        return queryParameter(parameter, equalsNeeded, null);
    }

    private Parser2<LexicalToken, NamedParameter> queryParameter( // PORT: §3.12 optional parameter equalsNeeded = false
        QueryOperatorParameter parameter)
    {
        return queryParameter(parameter, false, null);
    }

    // PORT: §3.12 local function GetQueryOperatorParameterParsers (QueryGrammar.cs:1034)
    private List<Parser2<LexicalToken, NamedParameter>> getQueryOperatorParameterParsers(List<QueryOperatorParameter> parameters, AllowedNameKind allowedNames, boolean equalsNeeded)
    {
        // PORT: §3.6 parameters.Select(p => QueryParameter(p, equalsNeeded, parameters)).ToList()
        List<Parser2<LexicalToken, NamedParameter>> paramParsers = new ArrayList<>();
        for (QueryOperatorParameter p : parameters)
        {
            paramParsers.add(queryParameter(p, equalsNeeded, parameters));
        }

        if (allowedNames != AllowedNameKind.DeclaredOnly)
        {
            // PORT: §3.6 QueryOperatorParameters.AllParameters.Where(p => !parameters.Any(p2 => p.Name == p2.Name)).ToList()
            List<QueryOperatorParameter> additionalParameters = new ArrayList<>();
            for (QueryOperatorParameter p : QueryOperatorParameters.AllParameters)
            {
                boolean any = false;
                for (QueryOperatorParameter p2 : parameters)
                {
                    if (java.util.Objects.equals(p.name(), p2.name())) // PORT: §3.14 string ==
                    {
                        any = true;
                        break;
                    }
                }

                if (!any)
                {
                    additionalParameters.add(p);
                }
            }

            for (QueryOperatorParameter ap : additionalParameters)
            {
                paramParsers.add(queryParameter(ap, equalsNeeded, QueryOperatorParameters.AllParameters));
            }
        }

        return paramParsers;
    }

    private List<Parser2<LexicalToken, NamedParameter>> getQueryOperatorParameterParsers(List<QueryOperatorParameter> parameters, AllowedNameKind allowedNames) // PORT: §3.12 optional parameter equalsNeeded = false
    {
        return getQueryOperatorParameterParsers(parameters, allowedNames, false);
    }

    private List<Parser2<LexicalToken, NamedParameter>> getQueryOperatorParameterParsers(List<QueryOperatorParameter> parameters) // PORT: §3.12 optional parameter allowedNames = AllowedNameKind.DeclaredOrKnown
    {
        return getQueryOperatorParameterParsers(parameters, AllowedNameKind.DeclaredOrKnown, false);
    }

    // constructs a parser for query operator parameter lists
    // PORT: §3.12 local function QueryParameterList (QueryGrammar.cs:1051)
    @SuppressWarnings("unchecked")
    private Parser2<LexicalToken, SyntaxList1<NamedParameter>> queryParameterList(List<QueryOperatorParameter> parameters, AllowedNameKind allowedNames, boolean equalsNeeded)
    {
        var paramParsers = getQueryOperatorParameterParsers(parameters, allowedNames, equalsNeeded);
        var first = first(paramParsers.toArray((Parser2<LexicalToken, NamedParameter>[]) new Parser2[0])); // PORT: §3.6 ToArray()
        return list(first);
    }

    private Parser2<LexicalToken, SyntaxList1<NamedParameter>> queryParameterList(List<QueryOperatorParameter> parameters, AllowedNameKind allowedNames) // PORT: §3.12 optional parameter equalsNeeded = false
    {
        return queryParameterList(parameters, allowedNames, false);
    }

    private Parser2<LexicalToken, SyntaxList1<NamedParameter>> queryParameterList(List<QueryOperatorParameter> parameters) // PORT: §3.12 optional parameter allowedNames = AllowedNameKind.DeclaredOrKnown
    {
        return queryParameterList(parameters, AllowedNameKind.DeclaredOrKnown, false);
    }

    // constructs a parser for comma separated query operator parameter lists
    // PORT: §3.12 local function QueryParameterCommaList (QueryGrammar.cs:1059)
    @SuppressWarnings("unchecked")
    private Parser2<LexicalToken, SyntaxList1<SeparatedElement1<NamedParameter>>> queryParameterCommaList(List<QueryOperatorParameter> parameters, AllowedNameKind allowedNames, boolean equalsNeeded)
    {
        var paramParsers = getQueryOperatorParameterParsers(parameters, allowedNames, equalsNeeded);
        var first = first(paramParsers.toArray((Parser2<LexicalToken, NamedParameter>[]) new Parser2[0])); // PORT: §3.6 ToArray()
        return commaList(first, (source, start) -> createMissingNamedParameter(source, start));
    }

    private Parser2<LexicalToken, SyntaxList1<SeparatedElement1<NamedParameter>>> queryParameterCommaList(List<QueryOperatorParameter> parameters, AllowedNameKind allowedNames) // PORT: §3.12 optional parameter equalsNeeded = false
    {
        return queryParameterCommaList(parameters, allowedNames, false);
    }

    private Parser2<LexicalToken, SyntaxList1<SeparatedElement1<NamedParameter>>> queryParameterCommaList(List<QueryOperatorParameter> parameters) // PORT: §3.12 optional parameter allowedNames = AllowedNameKind.DeclaredOrKnown
    {
        return queryParameterCommaList(parameters, AllowedNameKind.DeclaredOrKnown, false);
    }

    // PORT: §3.12 local function InToken (QueryGrammar.cs:1534)
    private static Parser2<LexicalToken, SyntaxToken> inToken(SyntaxKind inKind)
    {
        var opText = SyntaxFacts.getText(inKind);
        return token(inKind,
            createCompletionItem(opText, CompletionKind.ScalarInfix, CompletionPriority.Normal, opText + " (|)", opText) // PORT: §3.12 named arguments ctext, matchText
            );
    }

    // PORT: §3.12 local function CreateMissingInlineExternalTableDataFormatClause (QueryGrammar.cs:1714)
    private static InlineExternalTableDataFormatClause createMissingInlineExternalTableDataFormatClause()
    {
        return new InlineExternalTableDataFormatClause(
            SyntaxToken.missing(SyntaxKind.DataFormatKeyword),
            SyntaxToken.missing(SyntaxKind.EqualToken),
            SyntaxToken.missing(SyntaxKind.StringLiteralToken),
            List.of(DiagnosticFacts.getMissingDataFormat()));
    }

    // PORT: §3.12 local function CreateOmittedInlineExternalTableDataFormatClause (QueryGrammar.cs:1721)
    private static InlineExternalTableDataFormatClause createOmittedInlineExternalTableDataFormatClause()
    {
        return new InlineExternalTableDataFormatClause(
            SyntaxToken.missing(SyntaxKind.DataFormatKeyword),
            SyntaxToken.missing(SyntaxKind.EqualToken),
            SyntaxToken.missing(SyntaxKind.StringLiteralToken));
    }

    // PORT: §3.12 local function CreateOmittedInlineExternalTableRowSchema (QueryGrammar.cs:1727)
    private static RowSchema createOmittedInlineExternalTableRowSchema()
    {
        return new RowSchema(
            SyntaxToken.missing(SyntaxKind.OpenParenToken),
            null,
            SyntaxList1.<SeparatedElement1<NameAndTypeDeclaration>>empty(),
            SyntaxToken.missing(SyntaxKind.CloseParenToken));
    }

    // PORT: §3.12 local function IsInlineExternalTableDeltaKind (QueryGrammar.cs:1734)
    private static boolean isInlineExternalTableDeltaKind(InlineExternalTableKindClause kindClause)
    {
        // PORT: §3.14 ?. chain; §5.4 StringComparison.OrdinalIgnoreCase
        String valueText = kindClause != null && kindClause.value() != null ? kindClause.value().valueText() : null;
        return DotNetStrings.equalsOrdinalIgnoreCase(valueText, "delta");
    }

    // PORT: §3.12 local function GetInlineExternalTableRowSchema (QueryGrammar.cs:1737)
    private static RowSchema getInlineExternalTableRowSchema(InlineExternalTableKindClause kindClause)
    {
        return isInlineExternalTableDeltaKind(kindClause)
            ? createOmittedInlineExternalTableRowSchema()
            : createMissingRowSchema(null, 0);
    }

    // PORT: §3.12 local function GetInlineExternalTableDataFormatClause (QueryGrammar.cs:1742)
    private static InlineExternalTableDataFormatClause getInlineExternalTableDataFormatClause(
        InlineExternalTableKindClause kindClause,
        InlineExternalTableDataFormatClause dataFormatClause)
    {
        // PORT: §3.14 ??
        return dataFormatClause != null
            ? dataFormatClause
            : (isInlineExternalTableDeltaKind(kindClause)
                ? createOmittedInlineExternalTableDataFormatClause()
                : createMissingInlineExternalTableDataFormatClause());
    }

    // PORT: §3.12 local function DataScopeClause (QueryGrammar.cs:1957)
    private static Parser2<LexicalToken, DataScopeClause> dataScopeClause(CompletionKind ckind)
    {
        return
            rule(
                token(SyntaxKind.DataScopeKeyword, ckind).hide(),
                requiredToken(SyntaxKind.EqualToken),
                requiredTokenText(KustoFacts.DataScopeValues),
                (dataScopeKeyword, equalToken, valueToken) ->
                    new DataScopeClause(dataScopeKeyword, equalToken, valueToken));
    }
    // endregion

    // region Missing Element Factories
    public static NameDeclaration createMissingNameDeclaration(Source<LexicalToken> source, int start)
    {
        return new NameDeclaration(SyntaxToken.missing(SyntaxKind.IdentifierToken), List.of( DiagnosticFacts.getMissingName() )); // PORT: §3.17 new[] -> List.of
    }

    public static NameDeclaration createMissingNameDeclaration(Source<LexicalToken> source) // PORT: §3.12 optional parameters
    {
        return createMissingNameDeclaration(source, 0);
    }

    public static NameDeclaration createMissingNameDeclaration() // PORT: §3.12 optional parameters
    {
        return createMissingNameDeclaration(null, 0);
    }

    public static Expression createMissingNameDeclarationExpression(Source<LexicalToken> source, int start)
    {
        return new NameDeclaration(SyntaxToken.missing(SyntaxKind.IdentifierToken), List.of( DiagnosticFacts.getMissingName() )); // PORT: §3.17 new[] -> List.of
    }

    public static Expression createMissingNameReference(Source<LexicalToken> source, int start)
    {
        return new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken), List.of( DiagnosticFacts.getMissingName() )); // PORT: §3.17 new[] -> List.of
    }

    public static SyntaxToken createMissingNameToken(Source<LexicalToken> source, int start)
    {
        return SyntaxToken.missing(SyntaxKind.IdentifierToken);
    }

    public static Expression createMissingExpression(Source<LexicalToken> source, int start)
    {
            // check to see if following token was a keyword and if so report enhanced diagnostic
            var dx = (source != null && source.peek(start) != null && SyntaxFacts.isKeyword(source.peek(start).kind()) /* PORT: §3.15 pattern variable */) // PORT: §3.5 extension method
                ? DiagnosticFacts.getMissingExpressionWithKeyword(source.peek(start).text())
                : DiagnosticFacts.getMissingExpression();
            return new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken), List.of( dx )); // PORT: §3.17 new[] -> List.of
        }

    public static Expression createMissingExpression(Source<LexicalToken> source) // PORT: §3.12 optional parameters
    {
        return createMissingExpression(source, 0);
    }

    public static Expression createMissingExpression() // PORT: §3.12 optional parameters
    {
        return createMissingExpression(null, 0);
    }

    public static NamedExpression createMissingNamedExpression(Source<LexicalToken> source, int start)
    {
        return new SimpleNamedExpression(
                new NameDeclaration(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                SyntaxToken.missing(SyntaxKind.EqualToken),
                new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                List.of( DiagnosticFacts.getMissingName() )); // PORT: §3.17 new[] -> List.of
    }

    public static ScanAssignment createMissingScanAssignment(Source<LexicalToken> source, int start)
    {
        return new ScanAssignment(
                new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                SyntaxToken.missing(SyntaxKind.EqualEqualToken),
                new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                List.of( DiagnosticFacts.getMissingName() )); // PORT: §3.17 new[] -> List.of
    }

    public static Expression createMissingValue(Source<LexicalToken> source, int start)
    {
        return new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken), List.of( DiagnosticFacts.getMissingValue() )); // PORT: §3.17 new[] -> List.of
    }

    public static TypeExpression createMissingType(Source<LexicalToken> source, int start)
    {
        return new PrimitiveTypeExpression(SyntaxToken.missing(SyntaxKind.IdentifierToken), List.of( DiagnosticFacts.getMissingTypeName() )); // PORT: §3.17 new[] -> List.of
    }

    public static Expression createMissingLongLiteral(Source<LexicalToken> source, int start)
    {
        return new LiteralExpression(SyntaxKind.LongLiteralExpression, SyntaxToken.missing(SyntaxKind.LongLiteralToken), List.of( DiagnosticFacts.getMissingNumber() )); // PORT: §3.17 new[] -> List.of
    }

    public static Expression createMissingRealLiteral(Source<LexicalToken> source, int start)
    {
        return new LiteralExpression(SyntaxKind.RealLiteralExpression, SyntaxToken.missing(SyntaxKind.RealLiteralToken), List.of( DiagnosticFacts.getMissingNumber() )); // PORT: §3.17 new[] -> List.of
    }

    public static Expression createMissingStringLiteral(Source<LexicalToken> source, int start)
    {
        return new LiteralExpression(SyntaxKind.StringLiteralExpression, SyntaxToken.missing(SyntaxKind.StringLiteralToken), List.of( DiagnosticFacts.getMissingString() )); // PORT: §3.17 new[] -> List.of
    }

    public static Expression createMissingBooleanLiteral(Source<LexicalToken> source, int start)
    {
        return new LiteralExpression(SyntaxKind.BooleanLiteralExpression, SyntaxToken.missing(SyntaxKind.BooleanLiteralToken), List.of( DiagnosticFacts.getMissingBoolean() )); // PORT: §3.17 new[] -> List.of
    }

    public static Expression createMissingTypeOfLiteral(Source<LexicalToken> source, int start)
    {
        return new TypeOfLiteralExpression(
                SyntaxToken.missing(SyntaxKind.TypeOfKeyword),
                SyntaxToken.missing(SyntaxKind.OpenParenToken),
                SyntaxList1.<SeparatedElement1<Expression>>empty(), // PORT: §3.10 explicit type arguments
                SyntaxToken.missing(SyntaxKind.CloseParenToken),
                List.of( DiagnosticFacts.getMissingTypeOfLiteral() )); // PORT: §3.17 new[] -> List.of
    }

    public static Expression createMissingJsonValue(Source<LexicalToken> source, int start)
    {
        return new LiteralExpression(
                SyntaxKind.StringLiteralExpression,
                SyntaxToken.missing(SyntaxKind.StringLiteralToken),
                List.of( DiagnosticFacts.getMissingJsonValue() )); // PORT: §3.17 new[] -> List.of
    }

    public static JsonPair createMissingJsonPair(Source<LexicalToken> source, int start)
    {
        return new JsonPair(
                SyntaxToken.missing(SyntaxKind.StringLiteralToken),
                SyntaxToken.missing(SyntaxKind.ColonToken),
                new LiteralExpression(SyntaxKind.StringLiteralExpression, SyntaxToken.missing(SyntaxKind.StringLiteralToken)),
                List.of( DiagnosticFacts.getMissingJsonPair() )); // PORT: §3.17 new[] -> List.of
    }

    public static JoinConditionClause createMissingJoinOnClause(Source<LexicalToken> source, int start)
    {
        return new JoinOnClause(
                SyntaxToken.missing(SyntaxKind.JoinOnClause),
                SyntaxList1.<SeparatedElement1<Expression>>empty(), // PORT: §3.10 explicit type arguments
                List.of( DiagnosticFacts.getMissingJoinOnClause() )); // PORT: §3.17 new[] -> List.of
    }

    private static ExpressionList createMissingArgumentList(Source<LexicalToken> source, int start)
    {
        return new ExpressionList(
                SyntaxToken.missing(SyntaxKind.OpenParenToken, DiagnosticFacts.getTokenExpected(SyntaxKind.OpenParenToken)),
                SyntaxList1.<SeparatedElement1<Expression>>empty(), // PORT: §3.10 explicit type arguments
                SyntaxToken.missing(SyntaxKind.CloseParenToken, DiagnosticFacts.getTokenExpected(SyntaxKind.CloseParenToken)));
    }

    private static ExpressionList createMissingArgumentList(Source<LexicalToken> source) // PORT: §3.12 optional parameters
    {
        return createMissingArgumentList(source, 0);
    }

    private static ExpressionList createMissingArgumentList() // PORT: §3.12 optional parameters
    {
        return createMissingArgumentList(null, 0);
    }

    public static FunctionCallExpression createMissingFunctionCall(Source<LexicalToken> source, int start)
    {
        return new FunctionCallExpression(
                new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                new ExpressionList(
                    SyntaxToken.missing(SyntaxKind.OpenParenToken),
                    SyntaxList1.<SeparatedElement1<Expression>>empty(), // PORT: §3.10 explicit type arguments
                    SyntaxToken.missing(SyntaxKind.CloseParenToken)),
                List.of( DiagnosticFacts.getMissingFunctionCall() )); // PORT: §3.17 new[] -> List.of
    }

    public static Expression createMissingFunctionCallExpression(Source<LexicalToken> source, int start)
    {
        return new FunctionCallExpression(
                new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                new ExpressionList(
                    SyntaxToken.missing(SyntaxKind.OpenParenToken),
                    SyntaxList1.<SeparatedElement1<Expression>>empty(), // PORT: §3.10 explicit type arguments
                    SyntaxToken.missing(SyntaxKind.CloseParenToken)),
                List.of( DiagnosticFacts.getMissingFunctionCall() )); // PORT: §3.17 new[] -> List.of
    }

    public static SchemaTypeExpression createMissingSchemaType(Source<LexicalToken> source, int start)
    {
        return new SchemaTypeExpression(
                SyntaxToken.missing(SyntaxKind.OpenParenToken),
                SyntaxList1.<SeparatedElement1<Expression>>empty(), // PORT: §3.10 explicit type arguments
                SyntaxToken.missing(SyntaxKind.CloseParenToken),
                List.of( DiagnosticFacts.getMissingSchemaDeclaration() )); // PORT: §3.17 new[] -> List.of
    }

    public static RowSchema createMissingRowSchema(Source<LexicalToken> source, int start)
    {
        return new RowSchema(
                SyntaxToken.missing(SyntaxKind.OpenParenToken),
                null,
                SyntaxList1.<SeparatedElement1<NameAndTypeDeclaration>>empty(), // PORT: §3.10 explicit type arguments
                SyntaxToken.missing(SyntaxKind.CloseParenToken),
                List.of( DiagnosticFacts.getMissingSchemaDeclaration() )); // PORT: §3.17 new[] -> List.of
    }

    public static InlineExternalTableKindClause createMissingInlineExternalTableKindClause(Source<LexicalToken> source, int start)
    {
        return new InlineExternalTableKindClause(
                SyntaxToken.missing(SyntaxKind.KindKeyword),
                SyntaxToken.missing(SyntaxKind.EqualToken),
                SyntaxToken.missing(SyntaxKind.StringLiteralToken),
                List.of( DiagnosticFacts.getMissingExternalTableKind() )); // PORT: §3.17 new[] -> List.of
    }

    public static InlineExternalTableDataFormatClause createMissingInlineExternalTableDataFormatClause(Source<LexicalToken> source, int start)
    {
        return new InlineExternalTableDataFormatClause(
                SyntaxToken.missing(SyntaxKind.DataFormatKeyword),
                SyntaxToken.missing(SyntaxKind.EqualToken),
                SyntaxToken.missing(SyntaxKind.StringLiteralToken),
                List.of( DiagnosticFacts.getMissingDataFormat() )); // PORT: §3.17 new[] -> List.of
    }

    public static InlineExternalTableConnectionStringsClause createMissingInlineExternalTableConnectionStringsClause(Source<LexicalToken> source, int start)
    {
        return new InlineExternalTableConnectionStringsClause(
                SyntaxToken.missing(SyntaxKind.OpenParenToken),
                SyntaxList1.<SeparatedElement1<Expression>>empty(), // PORT: §3.10 explicit type arguments
                SyntaxToken.missing(SyntaxKind.CloseParenToken),
                List.of( DiagnosticFacts.getMissingConnectionStrings() )); // PORT: §3.17 new[] -> List.of
    }

    public static InlineExternalTablePathFormatPartitionColumnReference createMissingPathFormatTokens(Source<LexicalToken> source, int start)
    {
        return new InlineExternalTablePathFormatPartitionColumnReference(
                createMissingExpression(),
                new LiteralExpression(SyntaxKind.StringLiteralExpression, SyntaxToken.missing(SyntaxKind.StringLiteralToken)),
                List.of( DiagnosticFacts.getMissingPathFormatTokens() )); // PORT: §3.17 new[] -> List.of
    }

    public static PartitionColumnDeclaration createMissingPartitionColumnDeclaration(Source<LexicalToken> source, int start)
    {
        return new PartitionColumnDeclaration(
                new NameDeclaration(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                SyntaxToken.missing(SyntaxKind.ColonToken),
                new PrimitiveTypeExpression(SyntaxToken.missing(SyntaxKind.StringKeyword)),
                SyntaxToken.missing(SyntaxKind.EqualToken),
                createMissingExpression(source, start),
                List.of( DiagnosticFacts.getMissingPartitionColumnDeclaration() )); // PORT: §3.17 new[] -> List.of
    }

    public static EvaluateRowSchema createMissingEvaluateRowSchema(Source<LexicalToken> source, int start)
    {
        return new EvaluateRowSchema(
                SyntaxToken.missing(SyntaxKind.OpenParenToken),
                null,
                null,
                null,
                SyntaxList1.<SeparatedElement1<NameAndTypeDeclaration>>empty(), // PORT: §3.10 explicit type arguments
                SyntaxToken.missing(SyntaxKind.CloseParenToken),
                List.of( DiagnosticFacts.getMissingSchemaDeclaration() )); // PORT: §3.17 new[] -> List.of
    }

    public static QueryOperator createMissingQueryOperator(Source<LexicalToken> source, int start)
    {
        return new BadQueryOperator(SyntaxToken.missing(SyntaxKind.IdentifierToken), List.of( DiagnosticFacts.getQueryOperatorExpected() )); // PORT: §3.17 new[] -> List.of
    }

    public static Expression createMissingQueryOperatorExpression(Source<LexicalToken> source, int start)
    {
        return new BadQueryOperator(SyntaxToken.missing(SyntaxKind.IdentifierToken), List.of( DiagnosticFacts.getQueryOperatorExpected() )); // PORT: §3.17 new[] -> List.of
    }

    public static MakeSeriesExpression createMissingMakeSeriesExpression(Source<LexicalToken> source, int start)
    {
        return new MakeSeriesExpression(createMissingExpression(source, start), null);
    }

    public static MvExpandExpression createMissingMvExpandExpression(Source<LexicalToken> source, int start)
    {
        return new MvExpandExpression(createMissingExpression(source, start), null);
    }

    public static MvApplyExpression createMissingMvApplyExpression(Source<LexicalToken> source, int start)
    {
        return new MvApplyExpression(createMissingExpression(source, start), null);
    }

    public static MvApplySubqueryExpression createMissingMvApplySubqueryExpression(Source<LexicalToken> source, int start)
    {
        return new MvApplySubqueryExpression(
                createMissingToken(SyntaxKind.OpenParenToken),
                createMissingExpression(source, start),
                createMissingToken(SyntaxKind.CloseParenToken));
    }

    public static ForkExpression createMissingForkExpression(Source<LexicalToken> source, int start)
    {
        return new ForkExpression(
                null,
                createMissingToken(SyntaxKind.OpenParenToken),
                createMissingExpression(source, start),
                createMissingToken(SyntaxKind.CloseParenToken));
    }

    public static PartitionOperand createMissingPartitionOperand(Source<LexicalToken> source, int start)
    {
        return new PartitionSubquery(
                createMissingToken(SyntaxKind.OpenParenToken),
                createMissingExpression(source, start),
                createMissingToken(SyntaxKind.CloseParenToken));
    }

    public static Statement createMissingStatement(Source<LexicalToken> source, int start)
    {
        return new ExpressionStatement(
                new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                List.of( DiagnosticFacts.getMissingStatement() )); // PORT: §3.17 new[] -> List.of
    }

    public static SeparatedElement1<Statement> createMissingStatementElement(Source<LexicalToken> source, int start)
    {
        return new SeparatedElement1<Statement>(createMissingStatement(source, start));
    }

    public static NameAndTypeDeclaration createMissingNameAndTypeDeclaration(Source<LexicalToken> source, int start)
    {
        return new NameAndTypeDeclaration(
                new NameDeclaration(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                SyntaxToken.missing(SyntaxKind.ColonToken),
                new PrimitiveTypeExpression(SyntaxToken.missing(SyntaxKind.StringKeyword)),
                List.of( DiagnosticFacts.getMissingParameter() )); // PORT: §3.17 new[] -> List.of
    }

    public static FunctionParameter createMissingFunctionParameter(Source<LexicalToken> source, int start)
    {
        return new FunctionParameter(
                new NameAndTypeDeclaration(
                    new NameDeclaration(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                    SyntaxToken.missing(SyntaxKind.ColonToken),
                    new PrimitiveTypeExpression(SyntaxToken.missing(SyntaxKind.StringKeyword))),
                 null,
                 List.of( DiagnosticFacts.getMissingParameter() )); // PORT: §3.17 new[] -> List.of
    }

    public static NamedParameter createMissingNamedParameter(Source<LexicalToken> source, int start)
    {
        return new NamedParameter(
                new NameDeclaration(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                SyntaxToken.missing(SyntaxKind.EqualToken),
                new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                CompletionHint.None,
                List.of( DiagnosticFacts.getMissingParameter() )); // PORT: §3.17 new[] -> List.of
    }

    public static FunctionDeclaration createMissingFunctionDeclaration(Source<LexicalToken> source, int start)
    {
        return new FunctionDeclaration(null,
                new FunctionParameters(
                    SyntaxToken.missing(SyntaxKind.OpenParenToken),
                    SyntaxList1.<SeparatedElement1<FunctionParameter>>empty(), // PORT: §3.10 explicit type arguments
                    SyntaxToken.missing(SyntaxKind.CloseParenToken)),
                new FunctionBody(
                    SyntaxToken.missing(SyntaxKind.OpenBraceToken),
                    SyntaxList1.<SeparatedElement1<Statement>>empty(), // PORT: §3.10 explicit type arguments
                    null,
                    null,
                    SyntaxToken.missing(SyntaxKind.CloseBraceToken)),
                List.of( DiagnosticFacts.getMissingFunctionDeclaration() )); // PORT: §3.17 new[] -> List.of
    }

    public static BiFunction<Source<LexicalToken>, Integer, Expression> createMissingTokenLiteral(List<String> tokens)
    {
            var diagnostic = DiagnosticFacts.getTokenExpected(tokens);
            return (source, start) -> new LiteralExpression(SyntaxKind.TokenLiteralExpression,
                SyntaxToken.missing(SyntaxKind.IdentifierToken), List.of( diagnostic )); // PORT: §3.17 new[] -> List.of
        }

    public static BiFunction<Source<LexicalToken>, Integer, Expression> createMissingTokenLiteral(String... tokens)
    {
        return createMissingTokenLiteral(java.util.Arrays.asList(tokens) /* PORT: §3.17 array as IReadOnlyList */);
    }

    private static MakeGraphTableAndKeyClause createMissingMakeGraphTableAndKeyClause(Source<LexicalToken> source, int start)
    {
        return new MakeGraphTableAndKeyClause(
                new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                SyntaxToken.missing(SyntaxKind.OnKeyword),
                new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                List.of( DiagnosticFacts.getMissingExpression() )); // PORT: §3.17 new[] -> List.of
    }

    private static GraphMatchPatternNotation createMissingGraphMatchPatternNotation(Source<LexicalToken> source, int start)
    {
        return new GraphMatchPatternNode(
                SyntaxToken.missing(SyntaxKind.OpenParenToken),
                new NameDeclaration(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                SyntaxToken.missing(SyntaxKind.CloseParenToken),
                List.of( DiagnosticFacts.getMissingGraphMatchPatternElement() )); // PORT: §3.17 new[] -> List.of
    }

    private static GraphMatchPattern createMissingGraphMatchPattern(Source<LexicalToken> source, int start)
    {
        return new GraphMatchPattern(
                new SyntaxList1<GraphMatchPatternNotation>(
                    List.of( createMissingGraphMatchPatternNotation(source, start) ), // PORT: §3.17 new[] -> List.of
                    List.of( DiagnosticFacts.getMissingGraphMatchPattern() ) // PORT: §3.17 new[] -> List.of
                )
            );
    }

    private static GraphToTableOutputClause createMissingGraphToTableOutputClause(Source<LexicalToken> source, int start)
    {
        return new GraphToTableOutputClause(
                SyntaxToken.missing(SyntaxKind.OpenParenToken),
                new GraphToTableAsClause(SyntaxToken.missing(SyntaxKind.OpenParenToken), createMissingNameDeclaration()),
                null);
    }

    private static TopNestedClause createMissingTopNestedClause(Source<LexicalToken> source, int start)
    {
        return new TopNestedClause(
                SyntaxToken.missing(SyntaxKind.TopNestedKeyword),
                null,
                SyntaxToken.missing(SyntaxKind.OfKeyword),
                new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                null,
                SyntaxToken.missing(SyntaxKind.ByKeyword),
                new NameReference(SyntaxToken.missing(SyntaxKind.IdentifierToken)),
                List.of( DiagnosticFacts.getMissingClause() ) // PORT: §3.17 new[] -> List.of
                );
    }

    private static TopNestedClause createMissingTopNestedClause(Source<LexicalToken> source) // PORT: §3.12 optional parameters
    {
        return createMissingTopNestedClause(source, 0);
    }

    private static TopNestedClause createMissingTopNestedClause() // PORT: §3.12 optional parameters
    {
        return createMissingTopNestedClause(null, 0);
    }

    private static PatternMatch createMissingPatternMatch(Source<LexicalToken> source, int start)
    {
        return new PatternMatch(
                new ExpressionList(
                    SyntaxToken.missing(SyntaxKind.OpenParenToken),
                    SyntaxList1.<SeparatedElement1<Expression>>empty(), // PORT: §3.10 explicit type arguments
                    SyntaxToken.missing(SyntaxKind.CloseParenToken)),
                null, // path value okay to be null
                SyntaxToken.missing(SyntaxKind.EqualToken),
                new FunctionBody(
                    SyntaxToken.missing(SyntaxKind.OpenBraceToken),
                    SyntaxList1.<SeparatedElement1<Statement>>empty(), // PORT: §3.10 explicit type arguments
                    null, // expression okay to be null
                    null, // semicolon okay to be null
                    SyntaxToken.missing(SyntaxKind.CloseBraceToken)),
                SyntaxToken.missing(SyntaxKind.SemicolonToken),
                List.of( DiagnosticFacts.getMissingPatternMatch() )); // PORT: §3.17 new[] -> List.of
    }

    private static PatternMatch createMissingPatternMatch(Source<LexicalToken> source) // PORT: §3.12 optional parameters
    {
        return createMissingPatternMatch(source, 0);
    }

    private static PatternMatch createMissingPatternMatch() // PORT: §3.12 optional parameters
    {
        return createMissingPatternMatch(null, 0);
    }

    public static MaterializedViewCombineClause createMissingMaterializedViewCombineBaseClause(Source<LexicalToken> source, int start)
    {
        return new MaterializedViewCombineClause(
                // PORT-BUG: a node kind (not a keyword) is used as the missing token kind; mirrored
                SyntaxToken.missing(SyntaxKind.MaterializedViewCombineClause),
                SyntaxToken.missing(SyntaxKind.OpenParenToken),
                createMissingExpression(source, start),
                SyntaxToken.missing(SyntaxKind.CloseParenToken),
                List.of( DiagnosticFacts.getMissingClause("base") )); // PORT: §3.17 new[] -> List.of
    }

    public static MaterializedViewCombineClause createMissingMaterializedViewCombineBaseClause(Source<LexicalToken> source) // PORT: §3.12 optional parameters
    {
        return createMissingMaterializedViewCombineBaseClause(source, 0);
    }

    public static MaterializedViewCombineClause createMissingMaterializedViewCombineBaseClause() // PORT: §3.12 optional parameters
    {
        return createMissingMaterializedViewCombineBaseClause(null, 0);
    }

    public static MaterializedViewCombineClause createMissingMaterializedViewCombineDeltaClause(Source<LexicalToken> source, int start)
    {
        return new MaterializedViewCombineClause(
                // PORT-BUG: a node kind (not a keyword) is used as the missing token kind; mirrored
                SyntaxToken.missing(SyntaxKind.MaterializedViewCombineClause),
                SyntaxToken.missing(SyntaxKind.OpenParenToken),
                createMissingExpression(source, start),
                SyntaxToken.missing(SyntaxKind.CloseParenToken),
                List.of( DiagnosticFacts.getMissingClause("delta") )); // PORT: §3.17 new[] -> List.of
    }

    public static MaterializedViewCombineClause createMissingMaterializedViewCombineDeltaClause(Source<LexicalToken> source) // PORT: §3.12 optional parameters
    {
        return createMissingMaterializedViewCombineDeltaClause(source, 0);
    }

    public static MaterializedViewCombineClause createMissingMaterializedViewCombineDeltaClause() // PORT: §3.12 optional parameters
    {
        return createMissingMaterializedViewCombineDeltaClause(null, 0);
    }

    public static MaterializedViewCombineClause createMissingMaterializedViewCombineAggregatesClause(Source<LexicalToken> source, int start)
    {
        return new MaterializedViewCombineClause(
                // PORT-BUG: a node kind (not a keyword) is used as the missing token kind; mirrored
                SyntaxToken.missing(SyntaxKind.MaterializedViewCombineClause),
                SyntaxToken.missing(SyntaxKind.OpenParenToken),
                createMissingExpression(source, start),
                SyntaxToken.missing(SyntaxKind.CloseParenToken),
                List.of( DiagnosticFacts.getMissingClause("aggregates") )); // PORT: §3.17 new[] -> List.of
    }

    public static MaterializedViewCombineClause createMissingMaterializedViewCombineAggregatesClause(Source<LexicalToken> source) // PORT: §3.12 optional parameters
    {
        return createMissingMaterializedViewCombineAggregatesClause(source, 0);
    }

    public static MaterializedViewCombineClause createMissingMaterializedViewCombineAggregatesClause() // PORT: §3.12 optional parameters
    {
        return createMissingMaterializedViewCombineAggregatesClause(null, 0);
    }

    public static MaterializedViewCombineNameClause createMissingMaterializedViewCombineNameClause(Source<LexicalToken> source, int start)
    {
        return new MaterializedViewCombineNameClause(
                SyntaxToken.missing(SyntaxKind.OpenParenToken),
                createMissingExpression(source, start),
                SyntaxToken.missing(SyntaxKind.CloseParenToken));
    }

    public static MaterializedViewCombineNameClause createMissingMaterializedViewCombineNameClause(Source<LexicalToken> source) // PORT: §3.12 optional parameters
    {
        return createMissingMaterializedViewCombineNameClause(source, 0);
    }

    public static MaterializedViewCombineNameClause createMissingMaterializedViewCombineNameClause() // PORT: §3.12 optional parameters
    {
        return createMissingMaterializedViewCombineNameClause(null, 0);
    }

    // endregion
    // region other
    private static Parser2<LexicalToken, NameDeclaration> asIdentifierNameDeclaration(Parser2<LexicalToken, SyntaxToken> tokenParser)
    {
        return rule(tokenParser, (keyword) -> new NameDeclaration(keyword));
    }

    private static Parser2<LexicalToken, Expression> asIdentifierNameReference(Parser2<LexicalToken, SyntaxToken> tokenParser)
    {
        return rule(tokenParser, (keyword) -> (Expression)new NameReference(keyword));
    }

    private static Parser2<LexicalToken, TypeExpression> asPrimitiveTypeExpression(Parser2<LexicalToken, SyntaxToken> tokenParser)
    {
        return rule(tokenParser,
                typeToken -> (TypeExpression)new PrimitiveTypeExpression(typeToken));
    }

    private static Parser2<LexicalToken, Expression> asTokenLiteral(Parser2<LexicalToken, SyntaxToken> tokenParser)
    {
        return rule(tokenParser, (token) -> (Expression)new LiteralExpression(SyntaxKind.TokenLiteralExpression, token));
    }

    public static final Map<SyntaxKind, SyntaxKind> StringOperatorMap = new LinkedHashMap<SyntaxKind, SyntaxKind>(); // PORT: §3.17 Dictionary -> LinkedHashMap
    static // PORT: §3.9 collection initializer
    {
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.EqualTildeToken, SyntaxKind.EqualTildeExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.BangTildeToken, SyntaxKind.BangTildeExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.HasKeyword, SyntaxKind.HasExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.ColonToken, SyntaxKind.SearchExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.NotHasKeyword, SyntaxKind.NotHasExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.HasCsKeyword, SyntaxKind.HasCsExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.NotHasCsKeyword, SyntaxKind.NotHasCsExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.HasPrefixKeyword, SyntaxKind.HasPrefixExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.NotHasPrefixKeyword, SyntaxKind.NotHasPrefixExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.HasPrefixCsKeyword, SyntaxKind.HasPrefixCsExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.NotHasPrefixCsKeyword, SyntaxKind.NotHasPrefixCsExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.HasSuffixKeyword, SyntaxKind.HasSuffixExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.NotHasSuffixKeyword, SyntaxKind.NotHasSuffixExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.HasSuffixCsKeyword, SyntaxKind.HasSuffixCsExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.NotHasSuffixCsKeyword, SyntaxKind.NotHasSuffixCsExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.LikeKeyword, SyntaxKind.LikeExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.NotLikeKeyword, SyntaxKind.NotLikeExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.LikeCsKeyword, SyntaxKind.LikeCsExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.NotLikeCsKeyword, SyntaxKind.NotLikeCsExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.ContainsKeyword, SyntaxKind.ContainsExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.NotContainsKeyword, SyntaxKind.NotContainsExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.NotBangContainsKeyword, SyntaxKind.NotContainsExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.ContainsCsKeyword, SyntaxKind.ContainsCsExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.Contains_CsKeyword, SyntaxKind.ContainsCsExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.NotContainsCsKeyword, SyntaxKind.NotContainsCsExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.NotBangContainsCsKeyword, SyntaxKind.NotContainsCsExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.StartsWithKeyword, SyntaxKind.StartsWithExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.NotStartsWithKeyword, SyntaxKind.NotStartsWithExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.StartsWithCsKeyword, SyntaxKind.StartsWithCsExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.NotStartsWithCsKeyword, SyntaxKind.NotStartsWithCsExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.EndsWithKeyword, SyntaxKind.EndsWithExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.NotEndsWithKeyword, SyntaxKind.NotEndsWithExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.EndsWithCsKeyword, SyntaxKind.EndsWithCsExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.NotEndsWithCsKeyword, SyntaxKind.NotEndsWithCsExpression); // PORT: §3.17 Dictionary.Add
        DotNet.dictionaryAdd(StringOperatorMap, SyntaxKind.MatchesRegexKeyword, SyntaxKind.MatchesRegexExpression); // PORT: §3.17 Dictionary.Add
    }

    private static boolean isTokenVisible(SyntaxKind tokenKind)
    {
            switch (tokenKind)
            {
                case SyntaxKind.LikeCsKeyword:
                case SyntaxKind.LikeKeyword:
                case SyntaxKind.NotLikeCsKeyword:
                case SyntaxKind.NotLikeKeyword:
                case SyntaxKind.ContainsCsKeyword:      // use contains_cs
                case SyntaxKind.NotContainsCsKeyword:   // use !contains_cs
                case SyntaxKind.NotContainsKeyword:     // use !contains
                    return false;
                default:
                    return true;
            }
        }

    // keep this blank line separation before the #endregion to keep BRIDGE.Net from crashing
    // endregion
}
