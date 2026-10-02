// Ported from: src/Kusto.Language/Parser/Combinators/SafeParse.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.dotnet.Internal;

@Internal
public class StackSafeParser<TInput> extends ParserVisitor2<TInput, Parser<TInput>>
{
    private Source<TInput> source;
    private List<Object> output;
    private ArrayList<ParseState> stack;
    private int stackPosition;
    private ParseState state;
    private StackSafeScanner<TInput> scanner;

    public StackSafeParser(Source<TInput> source, List<Object> output)
    {
        this.stack = new ArrayList<ParseState>();
        this.stackPosition = -1;
        this.scanner = new StackSafeScanner<TInput>(source);

        initialize(source, output);
    }

    public void initialize(Source<TInput> source, List<Object> output)
    {
        this.source = source;
        this.output = output;
        this.scanner.initialize(source);
    }

    public void clear()
    {
        this.source = null;
        this.output = null;
        this.scanner.clear();
    }

    private class ParseState
    {
        private Parser<TInput> parser;
        public Parser<TInput> parser() { return parser; }

        /// <summary>
        /// The input start
        /// </summary>
        private int inputStart;
        public int inputStart() { return inputStart; }

        /// <summary>
        /// The true start of the output for the production
        /// </summary>
        private int outputStart;
        public int outputStart() { return outputStart; }

        /// <summary>
        /// The output count at the beginning of the parse. 
        /// This may occur after the OutputStart in right-side parsers
        /// </summary>
        private int originalOutputCount;
        public int originalOutputCount() { return originalOutputCount; }

        /// <summary>
        /// The accumulated input length consumed by this parser
        /// </summary>
        public int InputLength;

        /// <summary>
        /// The output start for the next parser.
        /// </summary>
        public int NextOutputStart;

        /// <summary>
        /// The parser execution state
        /// </summary>
        public int State;

        /// <summary>
        /// The result of the last sub-parser
        /// </summary>
        public int LastResult;

        /// <summary>
        /// The result length of the best failed parser
        /// </summary>
        public int BestFailedResult;

        /// <summary>
        /// The result length of the best successful parser
        /// </summary>
        public int BestSuccessResult;

        /// <summary>
        /// The source that existed prior to the current operation
        /// </summary>
        public Source<TInput> PreviousSource;

        public void init(Parser<TInput> parser, int inputStart, int outputStart, int outputCount)
        {
            this.parser = parser;
            this.inputStart = inputStart;
            this.outputStart = outputStart;
            this.originalOutputCount = outputCount;
            this.InputLength = 0;
            this.State = 0;
            this.LastResult = 0;
            this.NextOutputStart = outputCount;
            this.BestFailedResult = 0;
            this.BestSuccessResult = 0;
        }
    }

    private void push(Parser<TInput> parser, int inputStart, int outputStart)
    {
        this.stackPosition++;

        if (this.stackPosition == this.stack.size())
        {
            this.stack.add(new ParseState());
        }

        this.state = this.stack.get(this.stackPosition);
        this.state.init(parser, inputStart, outputStart, this.output.size());
    }

    private void pop()
    {
        this.stackPosition--;

        if (this.stackPosition >= 0)
        {
            this.state = this.stack.get(this.stackPosition);
        }
    }

    /// <summary>
    /// Parse using private stack, does not use the call stack.
    /// </summary>
    public int parse(Parser<TInput> parser, int inputStart, int outputStart)
    {
        this.state = null; // PORT: §3.10 default(ParseState) of a class is null
        this.stackPosition = -1;

        push(parser, inputStart, outputStart);

        while (true)
        {
            var nextParser = this.state.parser().accept(this);

            if (nextParser != null)
            {
                push(nextParser, this.state.inputStart() + this.state.InputLength, this.state.NextOutputStart); // PORT: §3.12 named arguments
            }
            else
            {
                var result = this.state.InputLength;

                if (this.stackPosition == 0)
                {
                    return result;
                }
                else
                {
                    pop();
                    this.state.LastResult = result;
                }
            }
        }
    }

    @Override
    public <TLeft, TOutput> Parser<TInput> visitApply(ApplyParser<TInput, TLeft, TOutput> parser)
    {
        if (state.State == 0)
        {
            state.State = 1;
            state.NextOutputStart = state.outputStart();
            return parser.leftParser();
        }
        else if (state.State == 1)
        {
            if (state.LastResult < 0)
            {
                if (parser.applyKind() == ApplyKind.One)
                {
                    state.InputLength = -state.InputLength + state.LastResult;
                }
                else
                {
                    state.InputLength = state.LastResult;
                }

                return null;
            }
            else
            {
                state.InputLength = state.LastResult;
                state.State = 2;
                state.NextOutputStart = state.outputStart();
                return parser.rightParser();
            }
        }
        else
        {
            if (state.LastResult > 0)
            {
                state.InputLength += state.LastResult;

                if (parser.applyKind() == ApplyKind.ZeroOrMore)
                {
                    return parser.rightParser();
                }
            }

            return null;
        }
    }

    @Override
    public Parser<TInput> visitBest(BestParser<TInput> parser)
    {
        if (state.State == 0)
        {
            int minLength = -1;
            int maxLength = -1;
            int bestParser = -1;

            // figure out which parser will consume most input
            for (int i = 0; i < parser.parsers().size(); i++)
            {
                var p = parser.parsers().get(i);
                var length = scanner.scan(p, state.inputStart());

                if (length > maxLength)
                {
                    maxLength = length;
                    bestParser = i;
                }
                else if (length < minLength)
                {
                    minLength = length;
                }
            }

            if (maxLength >= 0)
            {
                state.State = 1;
                state.NextOutputStart = state.outputStart();
                return parser.parsers().get(bestParser);
            }
            else
            {
                state.InputLength = -1;
                return null;
            }
        }
        else
        {
            state.InputLength = state.LastResult;
            return null;
        }
    }

    @Override
    public <TOutput> Parser<TInput> visitBest(BestParser2<TInput, TOutput> parser)
    {
        if (state.State == 0)
        {
            int minLength = -1;
            int maxLength = -1;
            int bestParser = -1;

            // figure out which parser will consume most input
            for (int i = 0; i < parser.parsers().size(); i++)
            {
                var p = parser.parsers().get(i);
                var length = scanner.scan(p, state.inputStart());

                if (length > maxLength)
                {
                    maxLength = length;
                    bestParser = i;
                }
                else if (length < minLength)
                {
                    minLength = length;
                }
            }

            if (maxLength >= 0)
            {
                state.State = 1;
                state.NextOutputStart = state.outputStart();
                return parser.parsers().get(bestParser);
            }
            else
            {
                state.InputLength = -1;
                return null;
            }
        }
        else
        {
            state.InputLength = state.LastResult;
            return null;
        }
    }

    @Override
    public <TOutput> Parser<TInput> visitConvert(ConvertParser<TInput, TOutput> parser)
    {
        state.InputLength = parser.parse(source, state.inputStart(), output, output.size());
        return null;
    }

    @Override
    public Parser<TInput> visitFails(FailsParser<TInput> parser)
    {
        throw new UnsupportedOperationException(); // PORT: §3.16 NotImplementedException
    }

    @Override
    public <TOutput> Parser<TInput> visitFirst(FirstParser2<TInput, TOutput> parser)
    {
        if (state.State == 0)
        {
            state.State = 1;
            state.NextOutputStart = state.outputStart();
            return parser.parsers().get(0);
        }
        else if (state.LastResult < 0)
        {
            if (state.LastResult < state.BestFailedResult)
            {
                state.BestFailedResult = state.LastResult;
            }

            ListExtensions.setCount(output, state.originalOutputCount()); // PORT: §3.5

            if (state.State < parser.parsers().size())
            {
                var next = parser.parsers().get(state.State);
                state.State++;
                state.NextOutputStart = state.outputStart();
                return next;
            }
            else
            {
                state.InputLength = state.BestFailedResult;
                return null;
            }
        }
        else
        {
            state.InputLength = state.LastResult;
            return null;
        }
    }

    @Override
    public Parser<TInput> visitFirst(FirstParser<TInput> parser)
    {
        if (state.State == 0)
        {
            state.State = 1;
            state.NextOutputStart = state.outputStart();
            return parser.parsers().get(0);
        }
        else if (state.LastResult < 0)
        {
            if (state.LastResult < state.BestFailedResult)
            {
                state.BestFailedResult = state.LastResult;
            }

            ListExtensions.setCount(output, state.originalOutputCount()); // PORT: §3.5

            if (state.State < parser.parsers().size())
            {
                var next = parser.parsers().get(state.State);
                state.State++;
                state.NextOutputStart = state.outputStart();
                return next;
            }
            else
            {
                state.InputLength = state.BestFailedResult;
                return null;
            }
        }
        else
        {
            state.InputLength = state.LastResult;
            return null;
        }
    }

    @Override
    public <TOutput> Parser<TInput> visitForward(ForwardParser<TInput, TOutput> parser)
    {
        if (state.State == 0)
        {
            state.State = 1;
            state.NextOutputStart = state.outputStart();
            return parser.deferredParser().get(); // PORT: §3.8 Func<T> -> Supplier<T>
        }
        else
        {
            state.InputLength = state.LastResult;
            return null;
        }
    }

    @Override
    public <TOutput> Parser<TInput> visitIf(IfParser2<TInput, TOutput> parser)
    {
        if (state.State == 0)
        {
            var length = scan(parser.test(), source, state.inputStart());

            if (length < 0)
            {
                state.InputLength = length;
                return null;
            }
            else
            {
                state.State = 1;
                state.NextOutputStart = state.outputStart();
                return parser.parser();
            }
        }
        else
        {
            state.InputLength = state.LastResult;
            return null;
        }
    }

    @Override
    public Parser<TInput> visitIf(IfParser<TInput> parser)
    {
        if (state.State == 0)
        {
            var length = scan(parser.test(), source, state.inputStart());

            if (length < 0)
            {
                state.InputLength = length;
                return null;
            }
            else
            {
                state.State = 1;
                state.NextOutputStart = state.outputStart();
                return parser.parser();
            }
        }
        else
        {
            state.InputLength = state.LastResult;
            return null;
        }
    }

    @Override
    public <TOutput> Parser<TInput> visitLimit(LimitParser<TInput, TOutput> parser)
    {
        if (state.State == 0)
        {
            var len = parser.Limiter.scan(source, state.inputStart());
            if (len > 0)
            {
                state.PreviousSource = this.source;
                this.source = new LimitSource<TInput>(this.source, state.inputStart() + len);
                state.State = 1;
                return parser.Limited;
            }
            else
            {
                state.InputLength = -1;
                return null;
            }
        }
        else
        {
            this.source = state.PreviousSource;
            state.InputLength = state.LastResult;
            return null;
        }
    }

    private static <TInput> int scan(Parser<TInput> parser, Source<TInput> source, int start) // PORT: §3.10 static member of generic class gets its own TInput
    {
        return SafeScanner.scanSafe(parser, source, start);
    }

    @Override
    public <TOutput> Parser<TInput> visitMap(MapParser<TInput, TOutput> parser)
    {
        state.InputLength = parser.parse(source, state.inputStart(), output, output.size());
        return null;
    }

    @Override
    public Parser<TInput> visitMatch(MatchParser<TInput> parser)
    {
        throw new UnsupportedOperationException(); // PORT: §3.16 NotImplementedException
    }

    @Override
    public <TOutput> Parser<TInput> visitMatch(MatchParser2<TInput, TOutput> parser)
    {
        state.InputLength = parser.parse(source, state.inputStart(), output, output.size());
        return null;
    }

    @Override
    public Parser<TInput> visitNot(NotParser<TInput> parser)
    {
        throw new UnsupportedOperationException(); // PORT: §3.16 NotImplementedException
    }

    @Override
    public Parser<TInput> visitOneOrMore(OneOrMoreParser<TInput> parser)
    {
        if (state.State == 0)
        {
            state.State = 1;
            state.NextOutputStart = output.size();
            return parser.parser();
        }
        else if (state.State == 1 && state.LastResult < 0)
        {
            // first parse did not succeed, fail
            state.InputLength = state.LastResult;
            return null;
        }
        else if (state.LastResult > 0)
        {
            // keep going as long as parsing is successful and consumed input
            state.State++;
            state.InputLength += state.LastResult;
            state.NextOutputStart = output.size();
            return parser.parser();
        }
        else
        {
            return null;
        }
    }

    @Override
    public <TOutput> Parser<TInput> visitOptional(OptionalParser<TInput, TOutput> parser)
    {
        if (state.State == 0)
        {
            state.State = 1;
            state.NextOutputStart = output.size();
            return parser.parser();
        }
        else if (state.LastResult < 0)
        {
            ListExtensions.setCount(output, state.originalOutputCount()); // PORT: §3.5
            output.add(parser.producer().get()); // PORT: §3.8 Func<T> -> Supplier<T>
            state.InputLength = 0;
            return null;
        }
        else
        {
            state.InputLength = state.LastResult;
            return null;
        }
    }

    @Override
    public <TOutput> Parser<TInput> visitProduce(ProduceParser<TInput, TOutput> parser)
    {
        if (state.State == 0)
        {
            state.State = 1;
            state.NextOutputStart = state.outputStart();
            return parser.parser();
        }
        else
        {
            if (state.LastResult >= 0)
            {
                var value = parser.producer().apply(output, state.outputStart()); // PORT: §3.8 Func<A,B,R> -> BiFunction
                ListExtensions.setCount(output, state.outputStart()); // PORT: §3.5
                output.add(value);
            }
            else
            {
                ListExtensions.setCount(output, state.originalOutputCount()); // PORT: §3.5
            }

            state.InputLength = state.LastResult;
            return null;
        }
    }

    @Override
    public <TOutput> Parser<TInput> visitRequired(RequiredParser<TInput, TOutput> parser)
    {
        if (state.State == 0)
        {
            state.State = 1;
            state.NextOutputStart = output.size();
            return parser.parser();
        }
        else if (state.LastResult < 0)
        {
            ListExtensions.setCount(output, state.originalOutputCount()); // PORT: §3.5
            output.add(parser.producer().apply(this.source, state.inputStart())); // PORT: §3.8 Func<A,B,R> -> BiFunction
            state.InputLength = 0;
            return null;
        }
        else
        {
            state.InputLength = state.LastResult;
            return null;
        }
    }

    @Override
    public <TOutput> Parser<TInput> visitRule(RuleParser<TInput, TOutput> parser)
    {
        if (state.LastResult < 0)
        {
            // last parser failed, so fail the whole rule
            state.InputLength = -state.InputLength + state.LastResult;
            ListExtensions.setCount(output, state.originalOutputCount()); // PORT: §3.5
            return null;
        }
        else
        {
            state.InputLength += state.LastResult;

            if (state.State >= parser.parsers().size())
            {
                var value = parser.listProducer().apply(output, state.outputStart()); // PORT: §3.8 Func<A,B,R> -> BiFunction
                ListExtensions.setCount(output, state.outputStart()); // PORT: §3.5
                output.add(value);
                return null;
            }
            else
            {
                var next = parser.parsers().get(state.State);
                state.State++;
                state.NextOutputStart = output.size();
                return next;
            }
        }
    }

    @Override
    public Parser<TInput> visitSequence(SequenceParser<TInput> parser)
    {
        if (state.LastResult < 0)
        {
            // last parser failed, so fail the whole rule
            state.InputLength = -state.InputLength + state.LastResult;
            ListExtensions.setCount(output, state.originalOutputCount()); // PORT: §3.5
            return null;
        }
        else
        {
            state.InputLength += state.LastResult;

            if (state.State >= parser.parsers().size())
            {
                return null;
            }
            else
            {
                var next = parser.parsers().get(state.State);
                state.State++;
                state.NextOutputStart = output.size();
                return next;
            }
        }
    }

    @Override
    public Parser<TInput> visitZeroOrMore(ZeroOrMoreParser<TInput> parser)
    {
        // PORT-BUG: for ZeroOrOne a successful item is not added to InputLength (the stop branch drops LastResult),
        // so the safe path reports 0 where the direct ZeroOrMoreParser reports the item length; mirrored.
        if (state.State == 0 || (state.LastResult > 0 && !parser.zeroOrOne()))
        {
            // keep going as long as parsing is successful and consumed input
            state.State++;
            state.InputLength += state.LastResult;
            state.NextOutputStart = output.size();
            return parser.parser();
        }
        else
        {
            return null;
        }
    }
}
