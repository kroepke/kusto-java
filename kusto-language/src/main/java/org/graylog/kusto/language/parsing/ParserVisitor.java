// Ported from: src/Kusto.Language/Parser/Combinators/ParserVisitors.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

public abstract class ParserVisitor<TInput>
{
    public abstract <TLeft, TOutput> void visitApply(ApplyParser<TInput, TLeft, TOutput> parser);
    public abstract <TOutput> void visitBest(BestParser2<TInput, TOutput> parser);
    public abstract void visitBest(BestParser<TInput> parser);
    public abstract <TOutput> void visitConvert(ConvertParser<TInput, TOutput> parser);
    public abstract void visitFails(FailsParser<TInput> parser);
    public abstract <TOutput> void visitFirst(FirstParser2<TInput, TOutput> parser);
    public abstract void visitFirst(FirstParser<TInput> parser);
    public abstract <TOutput> void visitForward(ForwardParser<TInput, TOutput> parser);
    public abstract <TOutput> void visitIf(IfParser2<TInput, TOutput> parser);
    public abstract void visitIf(IfParser<TInput> parser);
    public abstract <TOutput> void visitMap(MapParser<TInput, TOutput> parser);
    public abstract void visitMatch(MatchParser<TInput> parser);
    public abstract <TOutput> void visitMatch(MatchParser2<TInput, TOutput> parser);
    public abstract void visitNot(NotParser<TInput> parser);
    public abstract void visitOneOrMore(OneOrMoreParser<TInput> parser);
    public abstract <TOutput> void visitOptional(OptionalParser<TInput, TOutput> parser);
    public abstract <TOutput> void visitProduce(ProduceParser<TInput, TOutput> parser);
    public abstract <TOutput> void visitRequired(RequiredParser<TInput, TOutput> parser);
    public abstract <TOutput> void visitRule(RuleParser<TInput, TOutput> parser);
    public abstract void visitSequence(SequenceParser<TInput> parser);
    public abstract void visitZeroOrMore(ZeroOrMoreParser<TInput> parser);
    public abstract <TOutput> void visitLimit(LimitParser<TInput, TOutput> parser);
}
