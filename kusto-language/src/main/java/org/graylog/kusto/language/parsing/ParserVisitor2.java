// Ported from: src/Kusto.Language/Parser/Combinators/ParserVisitors.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

public abstract class ParserVisitor2<TInput, TResult> // PORT: §2.4 ParserVisitor<TInput, TResult>
{
    public abstract <TLeft, TOutput> TResult visitApply(ApplyParser<TInput, TLeft, TOutput> parser);
    public abstract <TOutput> TResult visitBest(BestParser2<TInput, TOutput> parser);
    public abstract TResult visitBest(BestParser<TInput> parser);
    public abstract <TOutput> TResult visitConvert(ConvertParser<TInput, TOutput> parser);
    public abstract TResult visitFails(FailsParser<TInput> parser);
    public abstract <TOutput> TResult visitFirst(FirstParser2<TInput, TOutput> parser);
    public abstract TResult visitFirst(FirstParser<TInput> parser);
    public abstract <TOutput> TResult visitForward(ForwardParser<TInput, TOutput> parser);
    public abstract <TOutput> TResult visitIf(IfParser2<TInput, TOutput> parser);
    public abstract TResult visitIf(IfParser<TInput> parser);
    public abstract <TOutput> TResult visitMap(MapParser<TInput, TOutput> parser);
    public abstract TResult visitMatch(MatchParser<TInput> parser);
    public abstract <TOutput> TResult visitMatch(MatchParser2<TInput, TOutput> parser);
    public abstract TResult visitNot(NotParser<TInput> parser);
    public abstract TResult visitOneOrMore(OneOrMoreParser<TInput> parser);
    public abstract <TOutput> TResult visitOptional(OptionalParser<TInput, TOutput> parser);
    public abstract <TOutput> TResult visitProduce(ProduceParser<TInput, TOutput> parser);
    public abstract <TOutput> TResult visitRequired(RequiredParser<TInput, TOutput> parser);
    public abstract <TOutput> TResult visitRule(RuleParser<TInput, TOutput> parser);
    public abstract TResult visitSequence(SequenceParser<TInput> parser);
    public abstract TResult visitZeroOrMore(ZeroOrMoreParser<TInput> parser);
    public abstract <TOutput> TResult visitLimit(LimitParser<TInput, TOutput> parser);
}
