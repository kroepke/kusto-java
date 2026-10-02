// Ported from: src/Kusto.Language/Parser/Combinators/SafeScan.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.ArrayList;

import org.graylog.kusto.language.utils.dotnet.Internal;

@Internal
public class StackSafeScanner<TInput> extends ParserVisitor2<TInput, Parser<TInput>>
{
    private Source<TInput> source;
    private ArrayList<ScanState> stack;
    private int stackPosition;
    private ScanState state;

    public StackSafeScanner(Source<TInput> source)
    {
        this.stack = new ArrayList<ScanState>();
        this.stackPosition = -1;
        initialize(source);
    }

    public void initialize(Source<TInput> source)
    {
        this.source = source;
    }

    public void clear()
    {
        this.source = null;
    }

    private class ScanState
    {
        private Parser<TInput> parser;
        public Parser<TInput> parser() { return parser; }

        /// <summary>
        /// The input start
        /// </summary>
        private int inputStart;
        public int inputStart() { return inputStart; }

        /// <summary>
        /// The accumulated input length consumed by this parser
        /// </summary>
        public int InputLength;

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
        /// The previous input source
        /// </summary>
        public Source<TInput> PreviousSource;

        public void init(Parser<TInput> parser, int inputStart)
        {
            this.parser = parser;
            this.inputStart = inputStart;
            this.InputLength = 0;
            this.State = 0;
            this.LastResult = 0;
            this.BestFailedResult = 0;
            this.BestSuccessResult = 0;
        }
    }

    private void push(Parser<TInput> parser, int inputStart)
    {
        this.stackPosition++;

        if (this.stackPosition == this.stack.size())
        {
            this.stack.add(new ScanState());
        }

        this.state = this.stack.get(this.stackPosition);
        this.state.init(parser, inputStart);
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
    /// Parse using a state machine, does not use the call stack.
    /// </summary>
    public int scan(Parser<TInput> parser, int start)
    {
        this.state = null; // PORT: §3.10 default(ScanState) of a class is null
        this.stackPosition = -1;

        push(parser, start);

        while (true)
        {
            var nextParser = this.state.parser().accept(this);

            if (nextParser != null)
            {
                push(nextParser, this.state.inputStart() + this.state.InputLength); // PORT: §3.12 named arguments
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
            state.BestFailedResult = -1;
            state.BestSuccessResult = -1;
            state.State = 1;
            return parser.parsers().get(0);
        }
        else
        {
            if (state.LastResult > state.BestSuccessResult)
            {
                state.BestSuccessResult = state.LastResult;
            }
            else if (state.LastResult < state.BestFailedResult)
            {
                state.BestFailedResult = state.LastResult;
            }

            if (state.State >= parser.parsers().size())
            {
                if (state.BestSuccessResult >= 0)
                {
                    state.InputLength = state.BestSuccessResult;
                    return null;
                }
                else
                {
                    state.InputLength = state.BestFailedResult;
                    return null;
                }
            }
            else
            {
                state.InputLength = 0;
                var next = parser.parsers().get(state.State);
                state.State++;
                return next;
            }
        }
    }

    @Override
    public <TOutput> Parser<TInput> visitBest(BestParser2<TInput, TOutput> parser)
    {
        if (state.State == 0)
        {
            state.BestFailedResult = -1;
            state.BestSuccessResult = -1;
            state.State = 1;
            return parser.parsers().get(0);
        }
        else
        {
            if (state.LastResult > state.BestSuccessResult)
            {
                state.BestSuccessResult = state.LastResult;
            }
            else if (state.LastResult < state.BestFailedResult)
            {
                state.BestFailedResult = state.LastResult;
            }

            if (state.State >= parser.parsers().size())
            {
                if (state.BestSuccessResult >= 0)
                {
                    state.InputLength = state.BestSuccessResult;
                    return null;
                }
                else
                {
                    state.InputLength = state.BestFailedResult;
                    return null;
                }
            }
            else
            {
                state.InputLength = 0;
                var next = parser.parsers().get(state.State);
                state.State++;
                return next;
            }
        }
    }

    @Override
    public <TOutput> Parser<TInput> visitConvert(ConvertParser<TInput, TOutput> parser)
    {
        if (state.State == 0)
        {
            state.State = 1;
            return parser.pattern();
        }
        else
        {
            state.InputLength = state.LastResult;
            return null;
        }
    }

    @Override
    public Parser<TInput> visitFails(FailsParser<TInput> parser)
    {
        if (state.State == 0)
        {
            if (source.isEnd(state.inputStart()))
            {
                state.InputLength = 0;
                return null;
            }
            else
            {
                state.State++;
                return parser.pattern();
            }
        }
        else
        {
            if (state.LastResult >= 0)
            {
                state.InputLength = -1;
            }
            else
            {
                state.InputLength = 0;
            }

            return null;
        }
    }

    @Override
    public <TOutput> Parser<TInput> visitFirst(FirstParser2<TInput, TOutput> parser)
    {
        if (state.State == 0)
        {
            state.State = 1;
            return parser.parsers().get(0);
        }
        else if (state.LastResult < 0)
        {
            if (state.LastResult < state.BestFailedResult)
            {
                state.BestFailedResult = state.LastResult;
            }

            if (state.State < parser.parsers().size())
            {
                var next = parser.parsers().get(state.State);
                state.State++;
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
            return parser.parsers().get(0);
        }
        else if (state.LastResult < 0)
        {
            if (state.LastResult < state.BestFailedResult)
            {
                state.BestFailedResult = state.LastResult;
            }

            if (state.State < parser.parsers().size())
            {
                var next = parser.parsers().get(state.State);
                state.State++;
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
            state.State = 1;
            return parser.test();
        }
        else if (state.State == 1)
        {
            var length = state.LastResult;
            if (length < 0)
            {
                state.InputLength = length;
                return null;
            }
            else
            {
                state.State = 2;
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
            state.State = 1;
            return parser.test();
        }
        else if (state.State == 1)
        {
            var length = state.LastResult;
            if (length < 0)
            {
                state.InputLength = length;
                return null;
            }
            else
            {
                state.State = 2;
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

    @Override
    public <TOutput> Parser<TInput> visitMap(MapParser<TInput, TOutput> parser)
    {
        // safe to call scan here because map is limited.
        state.InputLength = parser.scan(source, state.inputStart());
        return null;
    }

    @Override
    public Parser<TInput> visitMatch(MatchParser<TInput> parser)
    {
        // not proven safe here because scan of MatchParser is user defined
        state.InputLength = parser.scan(source, state.inputStart());
        return null;
    }

    @Override
    public <TOutput> Parser<TInput> visitMatch(MatchParser2<TInput, TOutput> parser)
    {
        // not proven safe here because scan of MatchParser is user defined
        state.InputLength = parser.scan(source, state.inputStart());
        return null;
    }

    @Override
    public Parser<TInput> visitNot(NotParser<TInput> parser)
    {
        if (state.State == 0)
        {
            if (source.isEnd(state.inputStart()))
            {
                state.InputLength = -1;
                return null;
            }
            else
            {
                state.State++;
                return parser.pattern();
            }
        }
        else
        {
            if (state.LastResult >= 0)
            {
                state.InputLength = -1;
                return null;
            }
            else
            {
                state.InputLength = 1;
                return null;
            }
        }
    }

    @Override
    public Parser<TInput> visitOneOrMore(OneOrMoreParser<TInput> parser)
    {
        if (state.State == 0)
        {
            state.State = 1;
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
            // keep going as long as scanning is successful and consumed input
            state.State++;
            state.InputLength += state.LastResult;
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
            return parser.parser();
        }
        else if (state.LastResult < 0)
        {
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
            return parser.parser();
        }
        else
        {
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
            return parser.parser();
        }
        else if (state.LastResult < 0)
        {
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
            // keep going as long as scanning is successful and consumed input
            state.State++;
            state.InputLength += state.LastResult;
            return parser.parser();
        }
        else
        {
            return null;
        }
    }
}
