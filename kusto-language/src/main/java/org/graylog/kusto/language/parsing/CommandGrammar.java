// Ported from: src/Kusto.Language/Parser/CommandGrammar.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import static org.graylog.kusto.language.parsing.LexicalTokenParsers.*; // PORT: §3.10 using static Parsers<LexicalToken>
import static org.graylog.kusto.language.parsing.SyntaxParsers.*;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

import org.graylog.kusto.language.DiagnosticFacts;
import org.graylog.kusto.language.GlobalState;
import org.graylog.kusto.language.ServerKinds;
import org.graylog.kusto.language.editor.CompletionHint;
import org.graylog.kusto.language.editor.CompletionItem;
import org.graylog.kusto.language.editor.CompletionKind;
import org.graylog.kusto.language.syntax.*;
import org.graylog.kusto.language.utils.Interlocked;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Internal;
import org.graylog.kusto.language.utils.dotnet.Linq;
import org.graylog.kusto.language.utils.dotnet.Out;

/// <summary>
/// Parsers for the Kusto command grammar.
/// </summary>
public class CommandGrammar
{
    // PORT-BUG: upstream declares this get-only property but the constructor never assigns it, so it is always null (CommandGrammar.cs:19). Mirrored.
    private final GlobalState globals = null;
    @Internal
    public GlobalState globals() { return this.globals; }

    /// <summary>
    /// The entry point grammar for commands
    /// </summary>
    private final Parser2<LexicalToken, CommandBlock> commandBlock;
    public Parser2<LexicalToken, CommandBlock> commandBlock() { return this.commandBlock; }

    private final PredefinedRuleParsers predefinedRules;
    public PredefinedRuleParsers predefinedRules() { return this.predefinedRules; }

    @SuppressWarnings("unchecked")
    public CommandGrammar(GlobalState globals)
    {
        // PORT: §3.12 captured locals are assigned after the forward lambdas are created; Out<T> holders stand in for the mutated locals
        var queryInputCore = new Out<Parser2<LexicalToken, SyntaxElement>>();
        var scriptInputCore = new Out<Parser2<LexicalToken, SyntaxElement>>();

        var queryInput = forward(() -> queryInputCore.value)
            .withTag("<query-input>");

        var scriptInput = forward(() -> scriptInputCore.value)
            .withTag("<script-input>");

        // include parsers for all command symbols
        var queryParser = QueryGrammar.from(globals);

        // all known commands
        var rules = this.predefinedRules = new PredefinedRuleParsers(queryParser, queryInput, scriptInput);
        var commandParserInfos = this.createCommandParsers(rules);
        // var commandParsers = commandParserInfos.Select(cpi => cpi.Parser.WithTag(cpi.Kind)).ToArray();
        Parser2<LexicalToken, Command>[] commandParsers = new Parser2[commandParserInfos.length]; // PORT: §3.6 plain loop, §3.10 generic array
        for (int i = 0; i < commandParserInfos.length; i++)
        {
            commandParsers[i] = commandParserInfos[i].Parser.withTag(commandParserInfos[i].Kind);
        }

        var bestCommand =
            best(commandParsers, (command1, command2) -> isBetterSyntax(command1, command2));

        // partial parsing of known commands
        Parser2<LexicalToken, Command> partialCommand =
                match(
                    (input, start) ->
                    {
                        // PORT: D10 no command parsers (the stubbed grammars): upstream PartialParser.FindBestPath throws
                        // InvalidOperationException ("Sequence contains no elements") on an empty sequence; no match instead.
                        if (commandParsers.length == 0)
                            return -1;

                        var len = PartialParser.scanPartialBest(Arrays.asList(commandParsers), input, start); // PORT: §3.10 array as IReadOnlyList
                        // fail if only dot (first token). It will become UnknownCommand
                        return (len < 2) ? -1 : len;
                    },
                    (input, start, length) ->
                    {
                        var output = new ArrayList<Object>();
                        var bestParsers = new ArrayList<Parser<LexicalToken>>();
                        var _unused = PartialParser.parsePartialBest(Arrays.asList(commandParsers), input, start, output, 0, bestParsers, CommandGrammar::onFailure);
                        if (output.size() == 1 && output.get(0) instanceof Command successful)
                        {
                            return successful;
                        }
                        else
                        {
                            var elements = Linq.ofType(output, SyntaxElement.class);
                            var dotToken = Linq.firstOrDefault(elements) instanceof SyntaxToken dot ? dot : null; // PORT: §3.15 FirstOrDefault() as SyntaxToken
                            var parts = Linq.skip(elements, 1);
                            var kinds = Linq.select(bestParsers, p -> p.tag());
                            return (Command) new PartialCommand(kinds, dotToken, new SyntaxList1<SyntaxElement>(parts));
                        }
                    });

        var bestCommandOrPartial =
            best(
                bestCommand,
                partialCommand
                );

        var commandSkippedTokens =
            if_(UnknownCommandToken,
                rule(
                    list(UnknownCommandToken),
                    tokens -> new SkippedTokens(tokens)));

        var commandAndSkippedTokens =
            applyOptional(
                bestCommandOrPartial,
                _left ->
                    rule(_left, commandSkippedTokens,
                        (cmd, skippedTokens) ->
                            (Command) new CommandAndSkippedTokens(cmd, skippedTokens)
                        ));
        var command =
            first(
                commandAndSkippedTokens, // pick whichever command will successfully consume most input
                UnknownCommand, // fall back for commands that are not defined
                BadCommand) // otherwise its just bad
            .withTag("<command>");

        var commandPipeExpression =
            applyZeroOrMore(
                command.<Expression>cast(),
                _left ->
                    rule(
                        _left,
                        SyntaxParsers.token(SyntaxKind.BarToken),
                        required(queryParser.followingPipeElementExpression(), QueryGrammar::createMissingQueryOperator),
                        (left, op, right) -> (Expression) new PipeExpression(left, op, right))
                        .withTag("<command-pipe-expression>"));

        queryInputCore.value =
            first(
                if_(SyntaxParsers.token(SyntaxKind.DotToken),
                    commandPipeExpression.<SyntaxElement>cast()),
                    queryParser.statementList().<SyntaxElement>cast());

        var commandStatement =
            rule(
                commandPipeExpression,
                cmd -> (Statement) new ExpressionStatement(cmd));

        var commandBlockSkippedTokens =
            if_(AnyTokenButEnd,
                rule(
                    list(AnyTokenButEnd), // consumes all remaining tokens
                    tokens -> new SkippedTokens(tokens)));

        this.commandBlock =
            rule(
                list(queryParser.directive()),
                separatedList(
                    commandStatement, // first one is a command statement
                    SyntaxKind.SemicolonToken,
                    queryParser.statement(),      // all others elements are query statements
                    CommandGrammar::createMissingCommandStatement,
                    EndOfText,
                    true,
                    true),
                optional(commandBlockSkippedTokens), // consumes all remaining tokens (no diagnostic)
                optional(SyntaxParsers.token(SyntaxKind.EndOfTextToken)),
                (directives, statements, skipped, end) ->
                    new CommandBlock(directives, statements, skipped, end));

        var scriptElement =
            rule(
                commandPipeExpression,
                optional(SyntaxParsers.token(SyntaxKind.SemicolonToken)),
                (cmd, semi) -> (SyntaxElement) new SeparatedElement1<Expression>(cmd, semi));

        scriptInputCore.value =
            oneOrMoreList(
                limit(CommandLimiter, scriptElement),  // limit parsing up until next command starts
                null,
                CommandGrammar::createMissingCommandStatement);
    }

    /// <summary>
    /// Add diagnostics for missing syntax.
    /// </summary>
    private static void onFailure(Parser<LexicalToken> parser, List<Object> output)
    {
        var tags = new ArrayList<String>();
        parser.accept(TagFinder.Instance, tags);

        if (tags.size() == 0)
            tags.add("<token>");

        var distinctTags = Linq.toArray(Linq.orderBy(Linq.distinct(tags), x -> x), String[]::new); // PORT: §3.6, §5.3 OrderBy -> stable ordinal sort (D12)
        var missingToken = SyntaxToken.missing("", SyntaxKind.IdentifierToken, Arrays.asList(DiagnosticFacts.getTermsExpected(distinctTags)));
        output.add(missingToken);
    }

    private static class TagFinder extends ParserVisitor3<LexicalToken, List<String>, Boolean>
    {
        public static final TagFinder Instance = new TagFinder();

        /// <summary>
        /// Gets the non-empty tag or null.
        /// </summary>
        private static boolean getTag(Parser<LexicalToken> parser, List<String> tags)
        {
            if (parser.annotations().size() > 0)
            {
                var text = Linq.firstOrDefault(Linq.select(Linq.ofType(parser.annotations(), CompletionItem.class), ci -> ci.displayText())); // PORT: §3.6
                if (text != null)
                {
                    addTag(text, tags);
                    return true;
                }
            }

            if (!DotNetStrings.isNullOrEmpty(parser.tag()))
            {
                addTag(parser.tag(), tags);
                return true;
            }

            return false;
        }

        private static void addTag(String tag, List<String> tags)
        {
            if (tag.contains("|"))
            {
                // split the tag by | and add each part separately
                var parts = DotNetStrings.split(tag, '|');
                for (var part : parts)
                {
                    if (!DotNetStrings.isNullOrWhiteSpace(part))
                    {
                        tags.add(DotNetStrings.trim(part));
                    }
                }
            }
            else if (!DotNetStrings.isNullOrWhiteSpace(tag))
            {
                tags.add(DotNetStrings.trim(tag));
            }
        }

        private boolean all(List<? extends Parser<LexicalToken>> parsers, List<String> tags)
        {
            var any = false;

            for (var parser : parsers)
            {
                any |= parser.accept(this, tags);
            }

            return any;
        }

        @Override
        public <TLeft, TOutput> Boolean visitApply(ApplyParser<LexicalToken, TLeft, TOutput> parser, List<String> tags)
        {
            return getTag(parser, tags)
                || parser.leftParser().accept(this, tags);
        }

        @Override
        public <TOutput> Boolean visitBest(BestParser2<LexicalToken, TOutput> parser, List<String> tags)
        {
            return getTag(parser, tags)
                || all(parser.parsers(), tags);
        }

        @Override
        public Boolean visitBest(BestParser<LexicalToken> parser, List<String> tags)
        {
            return getTag(parser, tags)
                || all(parser.parsers(), tags);
        }

        @Override
        public <TOutput> Boolean visitConvert(ConvertParser<LexicalToken, TOutput> parser, List<String> tags)
        {
            return getTag(parser, tags);
        }

        @Override
        public Boolean visitFails(FailsParser<LexicalToken> parser, List<String> tags)
        {
            return getTag(parser, tags)
                || parser.pattern().accept(this, tags);
        }

        @Override
        public <TOutput> Boolean visitFirst(FirstParser2<LexicalToken, TOutput> parser, List<String> tags)
        {
            return getTag(parser, tags)
                || all(parser.parsers(), tags);
        }

        @Override
        public Boolean visitFirst(FirstParser<LexicalToken> parser, List<String> tags)
        {
            return getTag(parser, tags)
                || all(parser.parsers(), tags);
        }

        @Override
        public <TOutput> Boolean visitForward(ForwardParser<LexicalToken, TOutput> parser, List<String> tags)
        {
            return getTag(parser, tags)
                || parser.deferredParser().get().accept(this, tags); // PORT: §3.8 DeferredParser()
        }

        @Override
        public <TOutput> Boolean visitIf(IfParser2<LexicalToken, TOutput> parser, List<String> tags)
        {
            return getTag(parser, tags)
                || parser.parser().accept(this, tags);
        }

        @Override
        public Boolean visitIf(IfParser<LexicalToken> parser, List<String> tags)
        {
            return getTag(parser, tags)
                || parser.test().accept(this, tags);
        }

        @Override
        public <TOutput> Boolean visitLimit(LimitParser<LexicalToken, TOutput> parser, List<String> tags)
        {
            return getTag(parser, tags)
                || parser.Limited.accept(this, tags);
        }

        @Override
        public <TOutput> Boolean visitMap(MapParser<LexicalToken, TOutput> parser, List<String> tags)
        {
            return getTag(parser, tags);
        }

        @Override
        public Boolean visitMatch(MatchParser<LexicalToken> parser, List<String> tags)
        {
            return getTag(parser, tags);
        }

        @Override
        public <TOutput> Boolean visitMatch(MatchParser2<LexicalToken, TOutput> parser, List<String> tags)
        {
            return getTag(parser, tags);
        }

        @Override
        public Boolean visitNot(NotParser<LexicalToken> parser, List<String> tags)
        {
            return getTag(parser, tags);
        }

        @Override
        public Boolean visitOneOrMore(OneOrMoreParser<LexicalToken> parser, List<String> tags)
        {
            return getTag(parser, tags)
                || parser.parser().accept(this, tags);
        }

        @Override
        public <TOutput> Boolean visitOptional(OptionalParser<LexicalToken, TOutput> parser, List<String> tags)
        {
            return getTag(parser, tags)
                || parser.parser().accept(this, tags);
        }

        @Override
        public <TOutput> Boolean visitProduce(ProduceParser<LexicalToken, TOutput> parser, List<String> tags)
        {
            return getTag(parser, tags)
                || parser.parser().accept(this, tags);
        }

        @Override
        public <TOutput> Boolean visitRequired(RequiredParser<LexicalToken, TOutput> parser, List<String> tags)
        {
            return getTag(parser, tags)
                || parser.parser().accept(this, tags);
        }

        @Override
        public <TOutput> Boolean visitRule(RuleParser<LexicalToken, TOutput> parser, List<String> tags)
        {
            return getTag(parser, tags)
                || parser.parsers().get(0).accept(this, tags);
        }

        @Override
        public Boolean visitSequence(SequenceParser<LexicalToken> parser, List<String> tags)
        {
            return getTag(parser, tags)
                || parser.parsers().get(0).accept(this, tags);
        }

        @Override
        public Boolean visitZeroOrMore(ZeroOrMoreParser<LexicalToken> parser, List<String> tags)
        {
            return getTag(parser, tags)
                || parser.parser().accept(this, tags);
        }
    }


    /// <summary>
    /// Gets or creates the <see cref="CommandGrammar"/> corresponding to the <see cref="GlobalState"/>
    /// </summary>
    public static CommandGrammar from(GlobalState globals)
    {
        // if this is same set of commands as the default set, then just return the default grammar
        if (Objects.equals(globals.serverKind(), GlobalState.default_().serverKind()))
        {
            return getDefaultCommandGrammar();
        }

        var grammarOut = new Out<CommandGrammar>(); // PORT: §3.3 out parameter
        CommandGrammar grammar = null;
        boolean foundInCache = false;

        // if the globals has a cache, look in the cache
        if (globals.cache() != null)
        {
            foundInCache = globals.cache().tryGetValue(CommandGrammar.class, grammarOut);
            grammar = grammarOut.value;
        }

        // if we still don't have a grammar check the recently created grammar
        // or create a new one
        if (grammar == null)
        {
            var recent = s_recentGrammar;

            if (recent != null && Objects.equals(recent.serverKind(), globals.serverKind()))
            {
                grammar = recent.grammar();
            }
            else
            {
                grammar = createCommandGrammar(globals);

                // remember this grammar for next time
                var newRecent = new RecentGrammar(globals.serverKind(), grammar);
                Interlocked.compareExchange(S_RECENT_GRAMMAR, newRecent, recent); // PORT: §3.13 non-null comparand
            }
        }

        // if the grammar did not come from the cache, put it into the cache if there is one.
        if (globals.cache() != null && !foundInCache)
        {
            final CommandGrammar created = grammar; // PORT: §3.12 lambda captures an effectively final copy
            grammar = globals.cache().getOrCreate(CommandGrammar.class, () -> created);
        }

        return grammar;
    }

    private static volatile RecentGrammar s_recentGrammar; // PORT: §3.13 CAS-published fields are volatile
    private static final VarHandle S_RECENT_GRAMMAR = Interlocked.staticHandle(CommandGrammar.class, "s_recentGrammar", RecentGrammar.class);

    private static class RecentGrammar
    {
        private final String serverKind;
        public String serverKind() { return this.serverKind; }
        private final CommandGrammar grammar;
        public CommandGrammar grammar() { return this.grammar; }

        public RecentGrammar(String serverKind, CommandGrammar grammar)
        {
            this.serverKind = serverKind;
            this.grammar = grammar;
        }
    }

    private static volatile CommandGrammar s_defaultCommandGrammar; // PORT: §3.13 CAS-published fields are volatile
    private static final VarHandle S_DEFAULT_COMMAND_GRAMMAR = Interlocked.staticHandle(CommandGrammar.class, "s_defaultCommandGrammar", CommandGrammar.class);

    private static CommandGrammar getDefaultCommandGrammar()
    {
        if (s_defaultCommandGrammar == null)
        {
            var grammar = createCommandGrammar(GlobalState.default_());
            Interlocked.compareExchange(S_DEFAULT_COMMAND_GRAMMAR, grammar, (CommandGrammar) null); // PORT: §3.13
        }

        return s_defaultCommandGrammar;
    }

    /// <summary>
    /// Creates the correct kind of command grammar given the global state.
    /// </summary>
    public static CommandGrammar createCommandGrammar(GlobalState globals)
    {
        // PORT: §3.15 switch on a null ServerKind falls to the default case in C#; Java switch(null) would throw
        switch (globals.serverKind() == null ? "" : globals.serverKind())
        {
            case ServerKinds.Engine:
                return new EngineCommandGrammar(globals);
            case ServerKinds.DataManager:
                return new DataManagerCommandGrammar(globals);
            case ServerKinds.ClusterManager:
                return new ClusterManagerCommandGrammar(globals);
            case ServerKinds.AriaBridge:
                return new AriaBridgeCommandGrammar(globals);
            default:
                // no defined commands
                return new CommandGrammar(globals);
        }
    }

    private static boolean isBetterSyntax(SyntaxElement command1, SyntaxElement command2)
    {
        // neither command has diagnostics, neither is better
        if (!command1.containsSyntaxDiagnostics() && !command2.containsSyntaxDiagnostics())
            return false;

        // command1 has diagnostics, command1 is not better than command2
        if (command1.containsSyntaxDiagnostics() && !command2.containsSyntaxDiagnostics())
            return false;

        // command2 has diagnostics, command1 is better
        if (!command1.containsSyntaxDiagnostics() && command2.containsSyntaxDiagnostics())
            return true;

        var dx1 = command1.getContainedSyntaxDiagnostics();
        var dx2 = command2.getContainedSyntaxDiagnostics();

        // command1 first diagnostic occurs lexically after command2 first diagnostics, command1 is better
        if (dx1.get(0).start() > dx2.get(0).start())
            return true;

        // command1 first diagnostic occurs lexically before command2 first diagnostic, command1 is not better
        if (dx1.get(0).start() < dx2.get(0).start())
            return false;

        // don't compare number of diagnostics, since we want to favor what happens early rather than later

        // otherwise neither is better
        return false;
    }

    /// <summary>
    /// Derived command parser's override this method to create the set of individual command parsers.
    /// </summary>
    @Internal
    public CommandParserInfo[] createCommandParsers(PredefinedRuleParsers rules)
    {
        // no commands
        return new CommandParserInfo[0];
    }

    @Internal
    public static class CommandParserInfo
    {
        public final String Kind;
        public final Parser2<LexicalToken, Command> Parser;

        public CommandParserInfo(String kind, Parser2<LexicalToken, Command> parser)
        {
            this.Kind = kind;
            this.Parser = parser;
        }
    }

    @Internal
    public static final Parser2<LexicalToken, SyntaxToken> UnknownCommandToken =
        if_(not(first(
            SyntaxParsers.token(SyntaxKind.BarToken),
            SyntaxParsers.token(SyntaxKind.LessThanBarToken),
            SyntaxParsers.token(SyntaxKind.EndOfTextToken),
            SyntaxParsers.token(SyntaxKind.SemicolonToken)
            )),
            AnyToken);

    @Internal
    public static final Parser2<LexicalToken, Command> UnknownCommand =
        rule(
            SyntaxParsers.token(SyntaxKind.DotToken),
            oneOrMore(UnknownCommandToken, tokens -> new SyntaxList1<SyntaxElement>(tokens)),
            (dot, parts) -> (Command) new UnknownCommand(dot, parts))
            .withTag("<unknown-command>");

    @Internal
    public static boolean isPossibleStartOfCommand(LexicalToken token, boolean isFirstToken)
    {
        if (token.kind() == SyntaxKind.DotToken)
        {
            if (TextFacts.hasLineBreaks(token.trivia()))
            {
                var lastLineStart = TextFacts.getLastLineBreakEnd(token.trivia());
                if (lastLineStart >= 0 && TextFacts.isWhitespaceOnly(token.trivia(), lastLineStart, token.trivia().length() - lastLineStart))
                {
                    // first non-whitespace on line is a dot token
                    return true;
                }
            }
            else if (isFirstToken && (token.trivia().length() == 0 || TextFacts.isWhitespaceOnly(token.trivia())))
            {
                return true;
            }
        }

        return false;
    }

    @Internal
    public static boolean isPossibleStartOfCommand(LexicalToken token) // PORT: §3.12 optional parameter isFirstToken = false
    {
        return isPossibleStartOfCommand(token, false);
    }

    @Internal
    public static Parser<LexicalToken> CommandLimiter =
        match((source, start) ->
        {
            // skip first token since it is assumed to be the start of the current command
            var pos = start + 1;

            // allow any token up until end of input or when the next command starts
            while (!source.isEnd(pos) && !isPossibleStartOfCommand(source.peek(pos)))
            {
                pos++;
            }

            // return number of tokens allowed
            return pos - start;
        });

    @Internal
    public static final Parser2<LexicalToken, Command> BadCommand =
        rule(
            SyntaxParsers.token(SyntaxKind.DotToken),
            dot -> (Command) new BadCommand(dot, Arrays.asList(DiagnosticFacts.getMissingCommand())))
            .withTag("<bad-command>");

    @Internal
    public static Statement createMissingCommandStatement(Source<LexicalToken> source, int start)
    {
        return new ExpressionStatement(new BadCommand(SyntaxToken.missing(SyntaxKind.DotToken), Arrays.asList(DiagnosticFacts.getMissingCommand())));
    }

    /// <summary>
    /// Constructs a <see cref="CustomNode"/> parser.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxElement> custom(
        List<? extends Parser<LexicalToken>> parsers,
        List<CustomElementDescriptor> shape)
    {
        if (shape == null)
        {
            shape = CustomNode.getDefaultShape(parsers.size());
        }

        final List<CustomElementDescriptor> finalShape = shape; // PORT: §3.12 lambda captures an effectively final copy
        return produce(
            sequence(parsers),
            (List<Object> items) ->
                (SyntaxElement) new CustomNode(finalShape, Linq.toArray(Linq.cast(items, SyntaxElement.class), SyntaxElement[]::new)));
    }

    public static Parser2<LexicalToken, SyntaxElement> custom(List<? extends Parser<LexicalToken>> parsers) // PORT: §3.12 optional parameter shape = null
    {
        return custom(parsers, (List<CustomElementDescriptor>) null);
    }

    /// <summary>
    /// Constructs a <see cref="CustomNode"/> parser.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxElement> custom(
        Parser<LexicalToken> parser,
        CustomElementDescriptor shape)
    {
        return custom(Arrays.asList(parser), shape != null ? Arrays.asList(shape) : null);
    }

    public static Parser2<LexicalToken, SyntaxElement> custom(Parser<LexicalToken> parser) // PORT: §3.12 optional parameter shape = null
    {
        return custom(parser, (CustomElementDescriptor) null);
    }

    /// <summary>
    /// Constructs a <see cref="CustomNode"/> parser.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxElement> custom(
        Parser<LexicalToken> parser1,
        Parser<LexicalToken> parser2,
        List<CustomElementDescriptor> shape)
    {
        return custom(Arrays.asList(parser1, parser2), shape);
    }

    public static Parser2<LexicalToken, SyntaxElement> custom(Parser<LexicalToken> parser1, Parser<LexicalToken> parser2) // PORT: §3.12 optional parameter shape = null
    {
        return custom(parser1, parser2, (List<CustomElementDescriptor>) null);
    }

    /// <summary>
    /// Constructs a <see cref="CustomNode"/> parser.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxElement> custom(
        Parser<LexicalToken> parser1,
        Parser<LexicalToken> parser2,
        Parser<LexicalToken> parser3,
        List<CustomElementDescriptor> shape)
    {
        return custom(Arrays.asList(parser1, parser2, parser3), shape);
    }

    public static Parser2<LexicalToken, SyntaxElement> custom(Parser<LexicalToken> parser1, Parser<LexicalToken> parser2, Parser<LexicalToken> parser3) // PORT: §3.12 optional parameter shape = null
    {
        return custom(parser1, parser2, parser3, (List<CustomElementDescriptor>) null);
    }

    /// <summary>
    /// Constructs a <see cref="CustomNode"/> parser.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxElement> custom(
        Parser<LexicalToken> parser1,
        Parser<LexicalToken> parser2,
        Parser<LexicalToken> parser3,
        Parser<LexicalToken> parser4,
        List<CustomElementDescriptor> shape)
    {
        return custom(Arrays.asList(parser1, parser2, parser3, parser4), shape);
    }

    public static Parser2<LexicalToken, SyntaxElement> custom(Parser<LexicalToken> parser1, Parser<LexicalToken> parser2, Parser<LexicalToken> parser3, Parser<LexicalToken> parser4) // PORT: §3.12 optional parameter shape = null
    {
        return custom(parser1, parser2, parser3, parser4, (List<CustomElementDescriptor>) null);
    }

    /// <summary>
    /// Constructs a <see cref="CustomNode"/> parser.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxElement> custom(
        Parser<LexicalToken> parser1,
        Parser<LexicalToken> parser2,
        Parser<LexicalToken> parser3,
        Parser<LexicalToken> parser4,
        Parser<LexicalToken> parser5,
        List<CustomElementDescriptor> shape)
    {
        return custom(Arrays.asList(parser1, parser2, parser3, parser4, parser5), shape);
    }

    public static Parser2<LexicalToken, SyntaxElement> custom(Parser<LexicalToken> parser1, Parser<LexicalToken> parser2, Parser<LexicalToken> parser3, Parser<LexicalToken> parser4, Parser<LexicalToken> parser5) // PORT: §3.12 optional parameter shape = null
    {
        return custom(parser1, parser2, parser3, parser4, parser5, (List<CustomElementDescriptor>) null);
    }


    /// <summary>
    /// Constructs a <see cref="CustomNode"/> parser.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxElement> custom(
        Parser<LexicalToken> parser1,
        Parser<LexicalToken> parser2,
        Parser<LexicalToken> parser3,
        Parser<LexicalToken> parser4,
        Parser<LexicalToken> parser5,
        Parser<LexicalToken> parser6,
        List<CustomElementDescriptor> shape)
    {
        return custom(Arrays.asList(parser1, parser2, parser3, parser4, parser5, parser6), shape);
    }

    public static Parser2<LexicalToken, SyntaxElement> custom(Parser<LexicalToken> parser1, Parser<LexicalToken> parser2, Parser<LexicalToken> parser3, Parser<LexicalToken> parser4, Parser<LexicalToken> parser5, Parser<LexicalToken> parser6) // PORT: §3.12 optional parameter shape = null
    {
        return custom(parser1, parser2, parser3, parser4, parser5, parser6, (List<CustomElementDescriptor>) null);
    }

    /// <summary>
    /// Constructs a <see cref="CustomNode"/> parser.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxElement> custom(
        Parser<LexicalToken> parser1,
        Parser<LexicalToken> parser2,
        Parser<LexicalToken> parser3,
        Parser<LexicalToken> parser4,
        Parser<LexicalToken> parser5,
        Parser<LexicalToken> parser6,
        Parser<LexicalToken> parser7,
        List<CustomElementDescriptor> shape)
    {
        return custom(Arrays.asList(parser1, parser2, parser3, parser4, parser5, parser6, parser7), shape);
    }

    public static Parser2<LexicalToken, SyntaxElement> custom(Parser<LexicalToken> parser1, Parser<LexicalToken> parser2, Parser<LexicalToken> parser3, Parser<LexicalToken> parser4, Parser<LexicalToken> parser5, Parser<LexicalToken> parser6, Parser<LexicalToken> parser7) // PORT: §3.12 optional parameter shape = null
    {
        return custom(parser1, parser2, parser3, parser4, parser5, parser6, parser7, (List<CustomElementDescriptor>) null);
    }

    /// <summary>
    /// Constructs a <see cref="CustomNode"/> parser.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxElement> custom(
        Parser<LexicalToken> parser1,
        Parser<LexicalToken> parser2,
        Parser<LexicalToken> parser3,
        Parser<LexicalToken> parser4,
        Parser<LexicalToken> parser5,
        Parser<LexicalToken> parser6,
        Parser<LexicalToken> parser7,
        Parser<LexicalToken> parser8,
        List<CustomElementDescriptor> shape)
    {
        return custom(Arrays.asList(parser1, parser2, parser3, parser4, parser5, parser6, parser7, parser8), shape);
    }

    public static Parser2<LexicalToken, SyntaxElement> custom(Parser<LexicalToken> parser1, Parser<LexicalToken> parser2, Parser<LexicalToken> parser3, Parser<LexicalToken> parser4, Parser<LexicalToken> parser5, Parser<LexicalToken> parser6, Parser<LexicalToken> parser7, Parser<LexicalToken> parser8) // PORT: §3.12 optional parameter shape = null
    {
        return custom(parser1, parser2, parser3, parser4, parser5, parser6, parser7, parser8, (List<CustomElementDescriptor>) null);
    }

    /// <summary>
    /// Constructs a <see cref="CustomNode"/> parser.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxElement> custom(
        Parser<LexicalToken> parser1,
        Parser<LexicalToken> parser2,
        Parser<LexicalToken> parser3,
        Parser<LexicalToken> parser4,
        Parser<LexicalToken> parser5,
        Parser<LexicalToken> parser6,
        Parser<LexicalToken> parser7,
        Parser<LexicalToken> parser8,
        Parser<LexicalToken> parser9,
        List<CustomElementDescriptor> shape)
    {
        return custom(Arrays.asList(parser1, parser2, parser3, parser4, parser5, parser6, parser7, parser8, parser9), shape);
    }

    public static Parser2<LexicalToken, SyntaxElement> custom(Parser<LexicalToken> parser1, Parser<LexicalToken> parser2, Parser<LexicalToken> parser3, Parser<LexicalToken> parser4, Parser<LexicalToken> parser5, Parser<LexicalToken> parser6, Parser<LexicalToken> parser7, Parser<LexicalToken> parser8, Parser<LexicalToken> parser9) // PORT: §3.12 optional parameter shape = null
    {
        return custom(parser1, parser2, parser3, parser4, parser5, parser6, parser7, parser8, parser9, (List<CustomElementDescriptor>) null);
    }

    /// <summary>
    /// Constructs a <see cref="CustomElementDescriptor"/>
    /// </summary>
    public static CustomElementDescriptor cd(String name, int hint, boolean isOptional) // PORT: CompletionHint is an int constants holder (§3.17)
    {
        return CustomElementDescriptor.from(name, hint, isOptional);
    }

    public static CustomElementDescriptor cd(String name, int hint) // PORT: §3.12 optional parameter isOptional = false
    {
        return cd(name, hint, false);
    }

    public static CustomElementDescriptor cd(String name) // PORT: §3.12 optional parameter hint = CompletionHint.Syntax
    {
        return cd(name, CompletionHint.Syntax, false);
    }

    /// <summary>
    /// Constructs a <see cref="CustomElementDescriptor"/>
    /// </summary>
    public static CustomElementDescriptor cd(int hint, boolean isOptional)
    {
        return CustomElementDescriptor.from(hint, isOptional);
    }

    public static CustomElementDescriptor cd(int hint) // PORT: §3.12 optional parameter isOptional = false
    {
        return cd(hint, false);
    }

    public static CustomElementDescriptor cd() // PORT: §3.12 optional parameter hint = CompletionHint.Syntax
    {
        return cd(CompletionHint.Syntax, false);
    }

    /// <summary>
    /// Constructs a custom command parser.
    /// </summary>
    public static Parser2<LexicalToken, Command> command(String commandName, Parser2<LexicalToken, SyntaxElement> contentParser)
    {
        return rule(
            SyntaxParsers.token(SyntaxKind.DotToken),
            contentParser,
            (dot, custom) -> (Command) new CustomCommand(commandName, dot, custom))
            .withTag("<" + commandName + ">");
    }

    public static SyntaxElement createMissingToken(String text)
    {
        return SyntaxParsers.createMissingToken(text);
    }

    public static SyntaxElement createMissingToken(List<String> texts)
    {
        return SyntaxParsers.createMissingTokenText(texts); // PORT: §2.5
    }

    public static SyntaxElement createMissingToken(String... texts)
    {
        return SyntaxParsers.createMissingTokenText(Arrays.asList(texts)); // PORT: §2.5; string[] binds to IReadOnlyList<string>
    }

    /// <summary>
    /// A parser that consumes the next <see cref="LexicalToken"/> (or series of adjacent tokens) if it has the specified text, producing a single <see cref="SyntaxToken"/>.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxElement> token(String text, SyntaxKind asKind, CompletionKind ckind)
    {
        // the default completion kind won't be known for most command keywords since they are not encoded in the SyntaxFacts table,
        // so change the default to Keyword to handle this common case.
        return SyntaxParsers.token(text, asKind, ckind != null ? ckind : SyntaxParsers.getCompletionKind(text, CompletionKind.Keyword)).<SyntaxElement>cast();
    }

    public static Parser2<LexicalToken, SyntaxElement> token(String text, SyntaxKind asKind) // PORT: §3.12 optional parameter ckind = null
    {
        return token(text, asKind, (CompletionKind) null);
    }

    public static Parser2<LexicalToken, SyntaxElement> token(String text) // PORT: §3.12 optional parameter asKind = null
    {
        return token(text, (SyntaxKind) null, (CompletionKind) null);
    }

    /// <summary>
    /// A parser that consumes the next <see cref="LexicalToken"/> (or series of adjacent tokens) if it has the specified text, producing a single <see cref="SyntaxToken"/>.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxElement> token(String text, CompletionKind ckind)
    {
        // the default completion kind won't be known for most command keywords since they are not encoded in the SyntaxFacts table,
        // so change the default to Keyword to handle this common case.
        return SyntaxParsers.token(text, (SyntaxKind) null, ckind).<SyntaxElement>cast();
    }

    /// <summary>
    /// A parser that consumes the next <see cref="LexicalToken"/> (or series of adjacent tokens) if it has one of the specified texts, producing a single <see cref="SyntaxToken"/>.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxElement> token(String... texts)
    {
        // the default completion kind won't be known for most command keywords since they are not encoded in the SyntaxFacts table,
        // so change the default to Keyword to handle this common case.
        return SyntaxParsers.tokenText(Arrays.asList(texts), CompletionKind.Keyword).<SyntaxElement>cast(); // PORT: §2.5
    }

    /// <summary>
    /// A parser that consumes the next <see cref="LexicalToken"/> (or series of adjacent tokens) if it has the specified text, producing a corresponding <see cref="SyntaxToken"/> or an equivalent missing token otherwise.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxElement> requiredToken(String text, SyntaxKind asKind, CompletionKind ckind)
    {
        return required(token(text, asKind, ckind), () -> (SyntaxElement) createMissingToken(text));
    }

    public static Parser2<LexicalToken, SyntaxElement> requiredToken(String text, SyntaxKind asKind) // PORT: §3.12 optional parameter ckind = null
    {
        return requiredToken(text, asKind, (CompletionKind) null);
    }

    public static Parser2<LexicalToken, SyntaxElement> requiredToken(String text) // PORT: §3.12 optional parameter asKind = null
    {
        return requiredToken(text, (SyntaxKind) null, (CompletionKind) null);
    }

    /// <summary>
    /// A parser that consumes the next <see cref="LexicalToken"/> (or series of adjacent tokens) if it has one of the specified texts, producing a corresponding <see cref="SyntaxToken"/> or an equivalent missing token otherwise.
    /// </summary>
    public static Parser2<LexicalToken, SyntaxElement> requiredToken(String... texts)
    {
        return required(token(texts), () -> (SyntaxElement) createMissingToken(texts));
    }

    public static Parser2<LexicalToken, SyntaxElement> zeroOrMoreList(
        Parser2<LexicalToken, SyntaxElement> elementParser,
        Parser2<LexicalToken, SyntaxElement> separatorParser,
        BiFunction<Source<LexicalToken>, Integer, SyntaxElement> fnMissingElement,
        boolean allowTrailingSeparator)
    {
        if (separatorParser != null)
        {
            return oList(
                elementParser,
                separatorParser,
                (Parser2<LexicalToken, SyntaxElement>) null,
                (BiFunction<Source<LexicalToken>, Integer, SyntaxElement>) null,
                (BiFunction<Source<LexicalToken>, Integer, SyntaxElement>) null,
                fnMissingElement,
                (Parser<LexicalToken>) null,
                false,
                allowTrailingSeparator,
                (List<Object> list) -> (SyntaxElement) SyntaxParsers.<SyntaxElement>makeSeparatedList(list)
                );
        }
        else
        {
            return list(
                elementParser,
                (BiFunction<Source<LexicalToken>, Integer, SyntaxElement>) null,
                false,
                (List<SyntaxElement> elements) -> (SyntaxElement) new SyntaxList1<SyntaxElement>(Linq.toArray(Linq.ofType(elements, SyntaxElement.class), SyntaxElement[]::new))
                );
        }
    }

    public static Parser2<LexicalToken, SyntaxElement> zeroOrMoreList(Parser2<LexicalToken, SyntaxElement> elementParser, Parser2<LexicalToken, SyntaxElement> separatorParser, BiFunction<Source<LexicalToken>, Integer, SyntaxElement> fnMissingElement) // PORT: §3.12 optional parameter allowTrailingSeparator = false
    {
        return zeroOrMoreList(elementParser, separatorParser, fnMissingElement, false);
    }

    public static Parser2<LexicalToken, SyntaxElement> zeroOrMoreList(Parser2<LexicalToken, SyntaxElement> elementParser, Parser2<LexicalToken, SyntaxElement> separatorParser) // PORT: §3.12 optional parameter fnMissingElement = null
    {
        return zeroOrMoreList(elementParser, separatorParser, null, false);
    }

    public static Parser2<LexicalToken, SyntaxElement> zeroOrMoreList(Parser2<LexicalToken, SyntaxElement> elementParser) // PORT: §3.12 optional parameter separatorParser = null
    {
        return zeroOrMoreList(elementParser, null, null, false);
    }

    public static Parser2<LexicalToken, SyntaxElement> zeroOrMoreCommaList(
        Parser2<LexicalToken, SyntaxElement> elementParser,
        BiFunction<Source<LexicalToken>, Integer, SyntaxElement> fnMissingElement,
        boolean allowTrailingSeparator)
    {
        return zeroOrMoreList(elementParser, token(","), fnMissingElement, allowTrailingSeparator);
    }

    public static Parser2<LexicalToken, SyntaxElement> zeroOrMoreCommaList(Parser2<LexicalToken, SyntaxElement> elementParser, BiFunction<Source<LexicalToken>, Integer, SyntaxElement> fnMissingElement) // PORT: §3.12 optional parameter allowTrailingSeparator = false
    {
        return zeroOrMoreCommaList(elementParser, fnMissingElement, false);
    }

    public static Parser2<LexicalToken, SyntaxElement> zeroOrMoreCommaList(Parser2<LexicalToken, SyntaxElement> elementParser) // PORT: §3.12 optional parameter fnMissingElement = null
    {
        return zeroOrMoreCommaList(elementParser, null, false);
    }

    public static Parser2<LexicalToken, SyntaxElement> oneOrMoreList(
        Parser2<LexicalToken, SyntaxElement> elementParser,
        Parser2<LexicalToken, SyntaxElement> separatorParser,
        BiFunction<Source<LexicalToken>, Integer, SyntaxElement> fnMissingElement,
        boolean allowTrailingSeparator)
    {
        if (separatorParser != null)
        {
            return oList(
                elementParser,
                separatorParser,
                (Parser2<LexicalToken, SyntaxElement>) null,
                (BiFunction<Source<LexicalToken>, Integer, SyntaxElement>) null,
                (BiFunction<Source<LexicalToken>, Integer, SyntaxElement>) null,
                fnMissingElement,
                (Parser<LexicalToken>) null,
                true,
                allowTrailingSeparator,
                (List<Object> list) -> (SyntaxElement) SyntaxParsers.<SyntaxElement>makeSeparatedList(list)
                );
        }
        else
        {
            return list(
                elementParser,
                (BiFunction<Source<LexicalToken>, Integer, SyntaxElement>) null,
                true,
                (List<SyntaxElement> elements) -> (SyntaxElement) new SyntaxList1<SyntaxElement>(Linq.toArray(Linq.ofType(elements, SyntaxElement.class), SyntaxElement[]::new))
                );
        }
    }

    public static Parser2<LexicalToken, SyntaxElement> oneOrMoreList(Parser2<LexicalToken, SyntaxElement> elementParser, Parser2<LexicalToken, SyntaxElement> separatorParser, BiFunction<Source<LexicalToken>, Integer, SyntaxElement> fnMissingElement) // PORT: §3.12 optional parameter allowTrailingSeparator = false
    {
        return oneOrMoreList(elementParser, separatorParser, fnMissingElement, false);
    }

    public static Parser2<LexicalToken, SyntaxElement> oneOrMoreList(Parser2<LexicalToken, SyntaxElement> elementParser, Parser2<LexicalToken, SyntaxElement> separatorParser) // PORT: §3.12 optional parameter fnMissingElement = null
    {
        return oneOrMoreList(elementParser, separatorParser, null, false);
    }

    public static Parser2<LexicalToken, SyntaxElement> oneOrMoreList(Parser2<LexicalToken, SyntaxElement> elementParser) // PORT: §3.12 optional parameter separatorParser = null
    {
        return oneOrMoreList(elementParser, null, null, false);
    }

    public static Parser2<LexicalToken, SyntaxElement> oneOrMoreCommaList(
        Parser2<LexicalToken, SyntaxElement> elementParser,
        BiFunction<Source<LexicalToken>, Integer, SyntaxElement> fnMissingElement,
        boolean allowTrailingSeparator)
    {
        return oneOrMoreList(elementParser, token(","), fnMissingElement, allowTrailingSeparator);
    }

    public static Parser2<LexicalToken, SyntaxElement> oneOrMoreCommaList(Parser2<LexicalToken, SyntaxElement> elementParser, BiFunction<Source<LexicalToken>, Integer, SyntaxElement> fnMissingElement) // PORT: §3.12 optional parameter allowTrailingSeparator = false
    {
        return oneOrMoreCommaList(elementParser, fnMissingElement, false);
    }

    public static Parser2<LexicalToken, SyntaxElement> oneOrMoreCommaList(Parser2<LexicalToken, SyntaxElement> elementParser) // PORT: §3.12 optional parameter fnMissingElement = null
    {
        return oneOrMoreCommaList(elementParser, null, false);
    }
}
