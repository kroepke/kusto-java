// Ported from: src/Kusto.Language/Parser/PredefinedRuleParsers.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import static org.graylog.kusto.language.parsing.LexicalTokenParsers.*; // PORT: §3.10 using static Parsers<LexicalToken>
import static org.graylog.kusto.language.parsing.SyntaxParsers.*;

import java.util.List;
import java.util.function.BiFunction;

import org.graylog.kusto.language.editor.CompletionHint;
import org.graylog.kusto.language.symbols.SymbolMatch;
import org.graylog.kusto.language.syntax.*;

/// <summary>
/// All predefined rules used by command grammar parsers
/// </summary>
public class PredefinedRuleParsers
{
    private final Parser2<LexicalToken, SyntaxElement> value;
    public Parser2<LexicalToken, SyntaxElement> value() { return this.value; }
    private final Parser2<LexicalToken, SyntaxElement> stringLiteral;
    public Parser2<LexicalToken, SyntaxElement> stringLiteral() { return this.stringLiteral; }
    private final Parser2<LexicalToken, SyntaxElement> bracketedStringLiteral;
    public Parser2<LexicalToken, SyntaxElement> bracketedStringLiteral() { return this.bracketedStringLiteral; }
    private final Parser2<LexicalToken, SyntaxElement> rawGuidLiteral;
    public Parser2<LexicalToken, SyntaxElement> rawGuidLiteral() { return this.rawGuidLiteral; }
    private final Parser2<LexicalToken, SyntaxElement> guidLiteral;
    public Parser2<LexicalToken, SyntaxElement> guidLiteral() { return this.guidLiteral; }
    private final Parser2<LexicalToken, SyntaxElement> anyGuidLiteralOrString;
    public Parser2<LexicalToken, SyntaxElement> anyGuidLiteralOrString() { return this.anyGuidLiteralOrString; }
    private final Parser2<LexicalToken, SyntaxElement> type;
    public Parser2<LexicalToken, SyntaxElement> type() { return this.type; }

    private final Parser2<LexicalToken, SyntaxElement> nameDeclaration;
    public Parser2<LexicalToken, SyntaxElement> nameDeclaration() { return this.nameDeclaration; }
    private final Parser2<LexicalToken, SyntaxElement> qualifiedNameDeclaration;
    public Parser2<LexicalToken, SyntaxElement> qualifiedNameDeclaration() { return this.qualifiedNameDeclaration; }
    private final Parser2<LexicalToken, SyntaxElement> wildcardedNameDeclaration;
    public Parser2<LexicalToken, SyntaxElement> wildcardedNameDeclaration() { return this.wildcardedNameDeclaration; }
    private final Parser2<LexicalToken, SyntaxElement> qualifiedWildcardedNameDeclaration;
    public Parser2<LexicalToken, SyntaxElement> qualifiedWildcardedNameDeclaration() { return this.qualifiedWildcardedNameDeclaration; }

    private final Parser2<LexicalToken, SyntaxElement> columnNameReference;
    public Parser2<LexicalToken, SyntaxElement> columnNameReference() { return this.columnNameReference; }
    private final Parser2<LexicalToken, SyntaxElement> tableNameReference;
    public Parser2<LexicalToken, SyntaxElement> tableNameReference() { return this.tableNameReference; }
    private final Parser2<LexicalToken, SyntaxElement> externalTableNameReference;
    public Parser2<LexicalToken, SyntaxElement> externalTableNameReference() { return this.externalTableNameReference; }
    private final Parser2<LexicalToken, SyntaxElement> materializedViewNameReference;
    public Parser2<LexicalToken, SyntaxElement> materializedViewNameReference() { return this.materializedViewNameReference; }
    private final Parser2<LexicalToken, SyntaxElement> functionNameReference;
    public Parser2<LexicalToken, SyntaxElement> functionNameReference() { return this.functionNameReference; }
    private final Parser2<LexicalToken, SyntaxElement> entityGroupNameReference;
    public Parser2<LexicalToken, SyntaxElement> entityGroupNameReference() { return this.entityGroupNameReference; }
    private final Parser2<LexicalToken, SyntaxElement> databaseNameReference;
    public Parser2<LexicalToken, SyntaxElement> databaseNameReference() { return this.databaseNameReference; }
    private final Parser2<LexicalToken, SyntaxElement> clusterNameReference;
    public Parser2<LexicalToken, SyntaxElement> clusterNameReference() { return this.clusterNameReference; }
    private final Parser2<LexicalToken, SyntaxElement> graphModelNameReference;
    public Parser2<LexicalToken, SyntaxElement> graphModelNameReference() { return this.graphModelNameReference; }
    private final Parser2<LexicalToken, SyntaxElement> graphSnapshotNameReference;
    public Parser2<LexicalToken, SyntaxElement> graphSnapshotNameReference() { return this.graphSnapshotNameReference; }
    private final Parser2<LexicalToken, SyntaxElement> graphModelSnapshotNameReference;
    public Parser2<LexicalToken, SyntaxElement> graphModelSnapshotNameReference() { return this.graphModelSnapshotNameReference; }

    private final Parser2<LexicalToken, SyntaxElement> databaseOrTableNameReference;
    public Parser2<LexicalToken, SyntaxElement> databaseOrTableNameReference() { return this.databaseOrTableNameReference; }
    private final Parser2<LexicalToken, SyntaxElement> databaseOrTableOrColumnNameReference;
    public Parser2<LexicalToken, SyntaxElement> databaseOrTableOrColumnNameReference() { return this.databaseOrTableOrColumnNameReference; }
    private final Parser2<LexicalToken, SyntaxElement> tableOrColumnNameReference;
    public Parser2<LexicalToken, SyntaxElement> tableOrColumnNameReference() { return this.tableOrColumnNameReference; }

    private final Parser2<LexicalToken, SyntaxElement> databaseTableNameReference;
    public Parser2<LexicalToken, SyntaxElement> databaseTableNameReference() { return this.databaseTableNameReference; }
    private final Parser2<LexicalToken, SyntaxElement> databaseTableColumnNameReference;
    public Parser2<LexicalToken, SyntaxElement> databaseTableColumnNameReference() { return this.databaseTableColumnNameReference; }
    private final Parser2<LexicalToken, SyntaxElement> tableColumnNameReference;
    public Parser2<LexicalToken, SyntaxElement> tableColumnNameReference() { return this.tableColumnNameReference; }
    private final Parser2<LexicalToken, SyntaxElement> databaseExternalTableNameReference;
    public Parser2<LexicalToken, SyntaxElement> databaseExternalTableNameReference() { return this.databaseExternalTableNameReference; }
    private final Parser2<LexicalToken, SyntaxElement> databaseMaterializedViewNameReference;
    public Parser2<LexicalToken, SyntaxElement> databaseMaterializedViewNameReference() { return this.databaseMaterializedViewNameReference; }
    private final Parser2<LexicalToken, SyntaxElement> databaseFunctionNameReference;
    public Parser2<LexicalToken, SyntaxElement> databaseFunctionNameReference() { return this.databaseFunctionNameReference; }
    private final Parser2<LexicalToken, SyntaxElement> databaseEntityGroupNameReference;
    public Parser2<LexicalToken, SyntaxElement> databaseEntityGroupNameReference() { return this.databaseEntityGroupNameReference; }

    private final Parser2<LexicalToken, SyntaxElement> functionDeclaration;
    public Parser2<LexicalToken, SyntaxElement> functionDeclaration() { return this.functionDeclaration; }
    private final Parser2<LexicalToken, SyntaxElement> functionBody;
    public Parser2<LexicalToken, SyntaxElement> functionBody() { return this.functionBody; }
    private final Parser2<LexicalToken, SyntaxElement> queryInput;
    public Parser2<LexicalToken, SyntaxElement> queryInput() { return this.queryInput; }
    private final Parser2<LexicalToken, SyntaxElement> scriptInput;
    public Parser2<LexicalToken, SyntaxElement> scriptInput() { return this.scriptInput; }
    private final Parser2<LexicalToken, SyntaxElement> inputText;
    public Parser2<LexicalToken, SyntaxElement> inputText() { return this.inputText; }
    private final Parser2<LexicalToken, SyntaxElement> bracketedInputText;
    public Parser2<LexicalToken, SyntaxElement> bracketedInputText() { return this.bracketedInputText; }

    private final BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingStringLiteral;
    public BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingStringLiteral() { return this.missingStringLiteral; }
    private final BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingValue;
    public BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingValue() { return this.missingValue; }
    private final BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingType;
    public BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingType() { return this.missingType; }
    private final BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingNameReference;
    public BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingNameReference() { return this.missingNameReference; }
    private final BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingNameDeclaration;
    public BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingNameDeclaration() { return this.missingNameDeclaration; }
    private final BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingFunctionDeclaration;
    public BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingFunctionDeclaration() { return this.missingFunctionDeclaration; }
    private final BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingFunctionBody;
    public BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingFunctionBody() { return this.missingFunctionBody; }
    private final BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingExpression;
    public BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingExpression() { return this.missingExpression; }
    private final BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingStatement;
    public BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingStatement() { return this.missingStatement; }
    private final BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingInputText;
    public BiFunction<Source<LexicalToken>, Integer, SyntaxElement> missingInputText() { return this.missingInputText; }
    public PredefinedRuleParsers(
        QueryGrammar queryParser,
        Parser2<LexicalToken, SyntaxElement> queryInput,
        Parser2<LexicalToken, SyntaxElement> scriptInput)
    {
        // casts are required here, bridge.net has problems with covariant delegates
        this.missingStringLiteral = (source, start) -> (SyntaxElement) QueryGrammar.createMissingStringLiteral(source, start); // PORT: §2.3 Q.CreateMissing*
        this.missingValue = (source, start) -> (SyntaxElement) QueryGrammar.createMissingValue(source, start);
        this.missingType = (source, start) -> (SyntaxElement) QueryGrammar.createMissingType(source, start);
        this.missingNameReference = (source, start) -> (SyntaxElement) QueryGrammar.createMissingNameReference(source, start);
        this.missingNameDeclaration = (source, start) -> (SyntaxElement) QueryGrammar.createMissingNameDeclaration(source, start);
        this.missingExpression = (source, start) -> (SyntaxElement) QueryGrammar.createMissingExpression(source, start);
        this.missingStatement = (source, start) -> (SyntaxElement) QueryGrammar.createMissingStatement(source, start);

        this.missingFunctionDeclaration = (source, start) ->
             (SyntaxElement) new FunctionDeclaration(
                 null,
                 new FunctionParameters(
                     createMissingToken(SyntaxKind.OpenParenToken),
                     SyntaxList1.<SeparatedElement1<FunctionParameter>>empty(),
                     createMissingToken(SyntaxKind.CloseParenToken)),
                 new FunctionBody(
                     createMissingToken(SyntaxKind.OpenBraceToken),
                     SyntaxList1.<SeparatedElement1<Statement>>empty(),
                     null,
                     null,
                     createMissingToken(SyntaxKind.CloseBraceToken)));

        this.missingFunctionBody = (source, start) ->
            (SyntaxElement) new FunctionBody(
                createMissingToken(SyntaxKind.OpenBraceToken),
                SyntaxList1.<SeparatedElement1<Statement>>empty(),
                null,
                null,
                createMissingToken(SyntaxKind.CloseBraceToken));

        this.missingInputText = (source, start) ->
                (SyntaxElement) SyntaxToken.other("", "", SyntaxKind.InputTextToken);

        this.rawGuidLiteral =
              rule(
                  token(SyntaxKind.RawGuidLiteralToken),
                  (token) -> (SyntaxElement) new LiteralExpression(SyntaxKind.GuidLiteralExpression, token));

        this.guidLiteral =
              rule(
                  token(SyntaxKind.GuidLiteralToken),
                  (token) -> (SyntaxElement) new LiteralExpression(SyntaxKind.GuidLiteralExpression, token));

        this.stringLiteral = queryParser.stringLiteral().<SyntaxElement>cast();

        this.anyGuidLiteralOrString =
            first(this.guidLiteral, this.stringLiteral, this.rawGuidLiteral);

        var StringName =
            rule(token(SyntaxKind.StringLiteralToken), token -> (Name) new TokenName(token));

        var BracketedStringLiteralToken =
            convertList(
                and(
                    token("["),
                    zeroOrMore(match((LexicalToken t) ->
                        !t.text().equals("]")
                        && !t.text().equals("[")
                        && !TextFacts.hasLineBreaks(t.trivia())
                        && !TextFacts.hasLineBreaks(t.text()))),
                    optional(token("]"))),
                (List<LexicalToken> list) ->
                {
                    // PORT: §3.6 string.Concat(list.Select(e => (e != list[0] ? e.Trivia : "") + e.Text)); reference comparison as upstream
                    var builder = new StringBuilder();
                    for (LexicalToken e : list)
                    {
                        builder.append(e != list.get(0) ? e.trivia() : "");
                        builder.append(e.text());
                    }
                    var text = builder.toString();
                    return SyntaxToken.literal(list.get(0).trivia(), text, SyntaxKind.StringLiteralToken);
                }).withTag("<bracketed-string>");

        this.bracketedStringLiteral =
            rule(BracketedStringLiteralToken,
                token -> (SyntaxElement) new LiteralExpression(SyntaxKind.StringLiteralExpression, token));

        var BracketedStringLiteralName =
            rule(BracketedStringLiteralToken,
                token -> (Name) new TokenName(token));

        var Name =
            first(
                queryParser.identifierName(),
                queryParser.bracketedName(),
                queryParser.bracedName(),
                StringName,
                BracketedStringLiteralName)
                .withTag("<name>");

        this.nameDeclaration =
            rule(Name,
                name -> (SyntaxElement) new NameDeclaration(name))
            .withTag("<name>");

        this.qualifiedNameDeclaration =
            applyZeroOrMore(
                this.nameDeclaration,
                _left ->
                    rule(_left, token(".").hide(), required(this.nameDeclaration, this.missingNameReference),
                        (expr, dot, selector) -> (SyntaxElement) new PathExpression((Expression) expr, dot, (Expression) selector)))
                .withTag("<qualified_name>");

        this.columnNameReference =
            withCompletionHint(rule(Name, name -> (SyntaxElement) new NameReference(name, SymbolMatch.Column)), CompletionHint.Column) // PORT: §3.5 extension method
                .withTag("<column>");

        this.tableNameReference =
            withCompletionHint(rule(Name, name -> (SyntaxElement) new NameReference(name, SymbolMatch.Table)), CompletionHint.Table)
                .withTag("<table>");

        this.externalTableNameReference =
            withCompletionHint(rule(Name, name -> (SyntaxElement) new NameReference(name, SymbolMatch.ExternalTable)), CompletionHint.ExternalTable)
                .withTag("<externaltable>");

        this.materializedViewNameReference =
            withCompletionHint(rule(Name, name -> (SyntaxElement) new NameReference(name, SymbolMatch.MaterializedView)), CompletionHint.MaterializedView)
                .withTag("<materializedview>");

        this.entityGroupNameReference =
            withCompletionHint(rule(Name, name -> (SyntaxElement) new NameReference(name, SymbolMatch.EntityGroup)), CompletionHint.EntityGroup)
                .withTag("<entitygroup>");

        this.graphModelNameReference =
            withCompletionHint(rule(Name, name -> (SyntaxElement) new NameReference(name, SymbolMatch.GraphModel)), CompletionHint.GraphModel)
                .withTag("<graphmodel>");

        this.graphSnapshotNameReference =
            withCompletionHint(rule(Name, name -> (SyntaxElement) new NameReference(name, SymbolMatch.GraphSnapshot)), CompletionHint.GraphSnapshot)
                .withTag("<graphsnapshot>");

        this.graphModelSnapshotNameReference =
            applyOptional(
                this.graphModelNameReference,
                _left ->
                    rule(_left, token(".").hide(), required(this.graphSnapshotNameReference, this.missingNameReference),
                        (expr, dot, selector) -> (SyntaxElement) new PathExpression((Expression) expr, dot, (Expression) selector)))
                .withTag("<graphmodelsnapshot>");

        this.databaseNameReference =
            withCompletionHint(rule(Name, name -> (SyntaxElement) new NameReference(name, SymbolMatch.Database)), CompletionHint.Database)
                .withTag("<database>");

        this.clusterNameReference =
            withCompletionHint(rule(Name, name -> (SyntaxElement) new NameReference(name, SymbolMatch.Cluster)), CompletionHint.Cluster)
                .withTag("<cluster>");

        this.functionNameReference =
            withCompletionHint(rule(Name, name -> (SyntaxElement) new NameReference(name, SymbolMatch.Function)), CompletionHint.DatabaseFunction)
                .withTag("<function>");

        this.databaseOrTableNameReference =
            withCompletionHint(rule(Name, name -> (SyntaxElement) new NameReference(name, SymbolMatch.Database | SymbolMatch.Table)), CompletionHint.Database | CompletionHint.Table)
                .withTag("<database_or_table>");

        this.databaseTableNameReference =
            applyZeroOrMore(
                this.databaseOrTableNameReference,
                _left ->
                    rule(_left, token(".").hide(), required(this.tableNameReference, this.missingNameReference),
                        (expr, dot, selector) -> (SyntaxElement) new PathExpression((Expression) expr, dot, (Expression) selector)))
                .withTag("<database_table>");

        this.databaseOrTableOrColumnNameReference =
            withCompletionHint(rule(Name, name -> (SyntaxElement) new NameReference(name, SymbolMatch.Database | SymbolMatch.Table | SymbolMatch.Column)), CompletionHint.Database | CompletionHint.Table | CompletionHint.Column)
                .withTag("<database_or_table_or_column>");

        this.databaseTableColumnNameReference =
            applyZeroOrMore(
                this.databaseOrTableOrColumnNameReference,
                _left ->
                    rule(_left, token(".").hide(), required(this.databaseOrTableOrColumnNameReference, this.missingNameReference),
                        (expr, dot, selector) -> (SyntaxElement) new PathExpression((Expression) expr, dot, (Expression) selector)))
                .withTag("<database_table_column>");

        this.tableOrColumnNameReference =
            withCompletionHint(rule(Name, name -> (SyntaxElement) new NameReference(name, SymbolMatch.Table | SymbolMatch.Column)), CompletionHint.Table | CompletionHint.Column)
                .withTag("<table_or_column>");

        this.tableColumnNameReference =
            applyZeroOrMore(
                this.tableOrColumnNameReference,
                _left ->
                    rule(_left, token(".").hide(), required(this.tableOrColumnNameReference, this.missingNameReference),
                        (expr, dot, selector) -> (SyntaxElement) new PathExpression((Expression) expr, dot, (Expression) selector)))
                .withTag("<table_column>");

        var databaseOrExternalTableNameReference =
            withCompletionHint(rule(Name, name -> (SyntaxElement) new NameReference(name, SymbolMatch.Database | SymbolMatch.ExternalTable)), CompletionHint.Database | CompletionHint.ExternalTable)
                .withTag("<database_or_externaltable>");

        this.databaseExternalTableNameReference =
            applyZeroOrMore(
                databaseOrExternalTableNameReference,
                _left ->
                    rule(_left, token(".").hide(), required(this.externalTableNameReference, this.missingNameReference),
                        (expr, dot, selector) -> (SyntaxElement) new PathExpression((Expression) expr, dot, (Expression) selector)))
                .withTag("<database_externaltable>");

        var databaseOrMaterializedViewNameReference =
            withCompletionHint(rule(Name, name -> (SyntaxElement) new NameReference(name, SymbolMatch.Database | SymbolMatch.MaterializedView)), CompletionHint.Database | CompletionHint.MaterializedView)
                .withTag("<database_or_materializedview>");

        this.databaseMaterializedViewNameReference =
            applyZeroOrMore(
                databaseOrMaterializedViewNameReference,
                _left ->
                    rule(_left, token(".").hide(), required(this.materializedViewNameReference, this.missingNameReference),
                        (expr, dot, selector) -> (SyntaxElement) new PathExpression((Expression) expr, dot, (Expression) selector)))
                .withTag("<database_materializedview>");

        var databaseOrFunctionNameReference =
            withCompletionHint(rule(Name, name -> (SyntaxElement) new NameReference(name, SymbolMatch.Database | SymbolMatch.Function)), CompletionHint.Database | CompletionHint.DatabaseFunction)
                .withTag("<database_or_function>");

        this.databaseFunctionNameReference =
            applyZeroOrMore(
                databaseOrFunctionNameReference,
                _left ->
                    rule(_left, token(".").hide(), required(this.functionNameReference, this.missingNameReference),
                        (expr, dot, selector) -> (SyntaxElement) new PathExpression((Expression) expr, dot, (Expression) selector)))
                .withTag("<database_function>");

        var databaseOrEntityGroupNameReference =
            withCompletionHint(rule(Name, name -> (SyntaxElement) new NameReference(name, SymbolMatch.Database | SymbolMatch.EntityGroup)), CompletionHint.Database | CompletionHint.EntityGroup)
                .withTag("<database_or_entitygroup>");

        this.databaseEntityGroupNameReference =
            applyZeroOrMore(
                databaseOrEntityGroupNameReference,
                _left ->
                    rule(_left, token(".").hide(), required(this.entityGroupNameReference, this.missingNameReference),
                        (expr, dot, selector) -> (SyntaxElement) new PathExpression((Expression) expr, dot, (Expression) selector)))
                .withTag("<database_entitygroup>");

        this.value = first(this.guidLiteral, this.rawGuidLiteral, queryParser.literal().<SyntaxElement>cast());

        this.type = queryParser.paramTypeExtended().<SyntaxElement>cast();

        this.wildcardedNameDeclaration =
            rule(queryParser.wildcardedIdentifier(),
                id -> (SyntaxElement) new NameDeclaration(new WildcardedName(id)));

        var WildcardedOrNameDeclaration =
            first(
                this.wildcardedNameDeclaration,
                this.nameDeclaration);

        // either name.wildname or wildname
        this.qualifiedWildcardedNameDeclaration =
            first(
                if_(and(this.nameDeclaration, token("."), WildcardedOrNameDeclaration),
                    rule(this.nameDeclaration, token("."), WildcardedOrNameDeclaration,
                        (qual, dot, name) -> (SyntaxElement) new PathExpression((Expression) qual, dot, (Expression) name))),
                WildcardedOrNameDeclaration.<SyntaxElement>cast());

        // make a forward parser to stop partial parsing
        var fb = forward(queryParser.functionBody());
        this.functionBody = fb.<SyntaxElement>cast();

        this.functionDeclaration =
            rule(
                queryParser.functionParameters(),
                fb,
                (p, b) -> (SyntaxElement) new FunctionDeclaration(null, p, b));

        this.queryInput = queryInput;
        this.scriptInput = scriptInput;

        var InputTextTokens = zeroOrMore(AnyTokenButEnd);

        SourceProducer<LexicalToken, SyntaxToken> InputTextBuilder = (source, start, length) ->
        {
            if (length > 0)
            {
                var builder = new StringBuilder();
                var token = source.peek(start);
                var trivia = token.trivia();
                builder.append(source.peek(start).text());

                for (int i = 1; i < length; i++)
                {
                    token = source.peek(start + i);
                    builder.append(token.trivia());
                    builder.append(token.text());
                }

                return SyntaxToken.other(trivia, builder.toString(), SyntaxKind.InputTextToken);
            }
            else
            {
                return SyntaxToken.other("", "", SyntaxKind.InputTextToken);
            }
        };

        this.inputText =
            convert(InputTextTokens, InputTextBuilder).<SyntaxElement>cast();

        var BracketedInputTextTokens =
            zeroOrMore(not(token("]")));

        this.bracketedInputText =
            convert(BracketedInputTextTokens, InputTextBuilder).<SyntaxElement>cast();
    }
}
