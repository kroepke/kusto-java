// Ported from: src/Kusto.Language/Parser/Combinators/ParserVisitors.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import org.graylog.kusto.language.utils.dotnet.Linq;

public class IsParentVisitor extends ParserVisitor3<LexicalToken, Parser<LexicalToken>, Boolean> // PORT: §2.4 ParserVisitor<LexicalToken, Parser<LexicalToken>, bool>
{
    public static IsParentVisitor Instance = new IsParentVisitor();

    @Override
    public <TLeft, TOutput> Boolean visitApply(ApplyParser<LexicalToken, TLeft, TOutput> parser, Parser<LexicalToken> child)
    {
        return parser.leftParser() == child || parser.rightParser() == child;
    }

    @Override
    public <TOutput> Boolean visitBest(BestParser2<LexicalToken, TOutput> parser, Parser<LexicalToken> child)
    {
        return Linq.any(parser.parsers(), p -> p == child); // PORT: §3.6
    }

    @Override
    public Boolean visitBest(BestParser<LexicalToken> parser, Parser<LexicalToken> child)
    {
        return Linq.any(parser.parsers(), p -> p == child); // PORT: §3.6
    }

    @Override
    public <TOutput> Boolean visitConvert(ConvertParser<LexicalToken, TOutput> parser, Parser<LexicalToken> child)
    {
        return parser.pattern() == child;
    }

    @Override
    public Boolean visitFails(FailsParser<LexicalToken> parser, Parser<LexicalToken> child)
    {
        return parser.pattern() != child; // PORT-BUG: `!=` where every sibling uses `==`; mirrored verbatim (D20)
    }

    @Override
    public <TOutput> Boolean visitFirst(FirstParser2<LexicalToken, TOutput> parser, Parser<LexicalToken> child)
    {
        return Linq.any(parser.parsers(), p -> p == child); // PORT: §3.6
    }

    @Override
    public Boolean visitFirst(FirstParser<LexicalToken> parser, Parser<LexicalToken> child)
    {
        return Linq.any(parser.parsers(), p -> p == child); // PORT: §3.6
    }

    @Override
    public <TOutput> Boolean visitForward(ForwardParser<LexicalToken, TOutput> parser, Parser<LexicalToken> child)
    {
        return parser.deferredParser().get() == child; // PORT: §3.8 Func<T> invoke -> get()
    }

    @Override
    public <TOutput> Boolean visitIf(IfParser2<LexicalToken, TOutput> parser, Parser<LexicalToken> child)
    {
        return parser.test() == child || parser.parser() == child;
    }

    @Override
    public Boolean visitIf(IfParser<LexicalToken> parser, Parser<LexicalToken> child)
    {
        return parser.test() == child || parser.parser() == child;
    }

    @Override
    public <TOutput> Boolean visitMap(MapParser<LexicalToken, TOutput> parser, Parser<LexicalToken> child)
    {
        return false;
    }

    @Override
    public Boolean visitMatch(MatchParser<LexicalToken> parser, Parser<LexicalToken> child)
    {
        return false;
    }

    @Override
    public <TOutput> Boolean visitMatch(MatchParser2<LexicalToken, TOutput> parser, Parser<LexicalToken> child)
    {
        return false;
    }

    @Override
    public Boolean visitNot(NotParser<LexicalToken> parser, Parser<LexicalToken> child)
    {
        return parser.pattern() == child;
    }

    @Override
    public Boolean visitOneOrMore(OneOrMoreParser<LexicalToken> parser, Parser<LexicalToken> child)
    {
        return parser.parser() == child;
    }

    @Override
    public <TOutput> Boolean visitOptional(OptionalParser<LexicalToken, TOutput> parser, Parser<LexicalToken> child)
    {
        return parser.parser() == child;
    }

    @Override
    public <TOutput> Boolean visitProduce(ProduceParser<LexicalToken, TOutput> parser, Parser<LexicalToken> child)
    {
        return parser.parser() == child;
    }

    @Override
    public <TOutput> Boolean visitRequired(RequiredParser<LexicalToken, TOutput> parser, Parser<LexicalToken> child)
    {
        return parser.parser() == child;
    }

    @Override
    public <TOutput> Boolean visitRule(RuleParser<LexicalToken, TOutput> parser, Parser<LexicalToken> child)
    {
        return Linq.any(parser.parsers(), p -> p == child); // PORT: §3.6
    }

    @Override
    public Boolean visitSequence(SequenceParser<LexicalToken> parser, Parser<LexicalToken> child)
    {
        return Linq.any(parser.parsers(), p -> p == child); // PORT: §3.6
    }

    @Override
    public Boolean visitZeroOrMore(ZeroOrMoreParser<LexicalToken> parser, Parser<LexicalToken> child)
    {
        return parser.parser() == child;
    }

    @Override
    public <TOutput> Boolean visitLimit(LimitParser<LexicalToken, TOutput> parser, Parser<LexicalToken> child)
    {
        return parser.Limiter == child || parser.Limited == child;
    }
}
