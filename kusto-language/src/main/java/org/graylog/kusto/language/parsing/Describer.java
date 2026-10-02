// Ported from: src/Kusto.Language/Parser/Combinators/Describer.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.graylog.kusto.language.utils.dotnet.DotNetStrings;

/// <summary>
/// Builds a textual representation of a parser's grammar.
/// </summary>
public final class Describer
{
    private Describer() // PORT: §3.5 static class
    {
    }

    /// <summary>
    /// Returns a textual representation of a parser's grammar.
    /// </summary>
    public static <TInput> String describe(Parser<TInput> parser, boolean showRequired)
    {
        var builder = new Writer<TInput>(showRequired);
        builder.visit(parser);
        return builder.toString();
    }

    public static <TInput> String describe(Parser<TInput> parser) // PORT: §3.12 optional parameter showRequired = true
    {
        return describe(parser, true);
    }

    private static class Writer<TInput> extends ParserVisitor<TInput>
    {
        private StringBuilder builder;
        private String separator;
        private boolean showRequired;

        public Writer(boolean showRequired)
        {
            this.builder = new StringBuilder();
            this.showRequired = showRequired;
        }

        @Override
        public String toString()
        {
            return this.builder.toString();
        }

        public void visit(Parser<TInput> parser)
        {
            if (parser != null && !parser.isHidden())
            {
                if (!DotNetStrings.isNullOrEmpty(parser.tag()))
                {
                    writeTerm(parser.tag());
                }
                else
                {
                    parser.accept(this);
                }
            }
        }

        private void writeTerm(String term)
        {
            if (!DotNetStrings.isNullOrEmpty(term))
            {
                builder.append(term);
            }
        }

        private void writeAlternation(List<? extends Parser<TInput>> parsers) // PORT: §3.10 covariance
        {
            this.writeSeparated(" | ", parsers);
        }

        private void writeSequence(List<? extends Parser<TInput>> parsers) // PORT: §3.10 covariance
        {
            this.writeSeparated(" ", parsers);
        }

        private void writeOptional(Parser<TInput> parser)
        {
            this.writeBracketed("[", "]", parser);
        }

        private void writeRequired(Parser<TInput> parser)
        {
            var nested = new Writer<TInput>(this.showRequired);
            nested.visit(parser);

            if (nested.separator != null)
            {
                this.builder.append("(");
                this.builder.append(nested.toString());
                this.builder.append(")");
            }
            else
            {
                this.builder.append(nested.toString());
            }

            this.builder.append("!");
        }

        private void writeZeroOrMore(Parser<TInput> parser)
        {
            this.writeBracketed("{", "}", parser);
        }

        private void writeOneOrMore(Parser<TInput> parser)
        {
            this.writeBracketed("{", "}+", parser);
        }

        private void writeSeparated(String separator, List<? extends Parser<TInput>> parsers) // PORT: §3.10 covariance
        {
            var builders = new ArrayList<Writer<TInput>>();

            for (int i = 0, n = parsers.size(); i < n; i++)
            {
                var parser = parsers.get(i);
                if (!parser.isHidden())
                {
                    var nestedBuilder = new Writer<TInput>(this.showRequired);

                    nestedBuilder.visit(parser);

                    if (nestedBuilder.builder.length() > 0)
                    {
                        builders.add(nestedBuilder);
                    }
                }
            }

            if (builders.size() > 1)
            {
                this.separator = separator;

                for (int i = 0; i < builders.size(); i++)
                {
                    if (i > 0)
                        this.builder.append(separator);

                    var grammarBuilder = builders.get(i);
                    if (grammarBuilder.separator != null && !Objects.equals(grammarBuilder.separator, separator)) // PORT: §3.14 string !=
                    {
                        // if item is separated, but using a different separator, add parens
                        this.builder.append("(");
                        this.builder.append(grammarBuilder.builder.toString());
                        this.builder.append(")");
                    }
                    else
                    {
                        this.builder.append(grammarBuilder.builder.toString());
                    }
                }
            }
            else if (builders.size() == 1)
            {
                this.builder = builders.get(0).builder;
                this.separator = builders.get(0).separator;
            }
        }

        private void writeBracketed(String startBracket, String endBracket, Parser<TInput> parser, BiConsumer<Parser<TInput>, Writer<TInput>> action)
        {
            var nestedBuilder = new Writer<TInput>(this.showRequired);
            nestedBuilder.visit(parser);

            var text = nestedBuilder.toString();
            if (text.length() > 0)
            {
                builder.append(startBracket);
                builder.append(text);
                builder.append(endBracket);
            }
        }

        private void writeBracketed(String startBracket, String endBracket, Parser<TInput> parser) // PORT: §3.12 optional parameter action = null
        {
            writeBracketed(startBracket, endBracket, parser, null);
        }

        @Override
        public <TLeft, TOutput> void visitApply(ApplyParser<TInput, TLeft, TOutput> parser)
        {
            switch (parser.applyKind())
            {
                case ZeroOrMore:
                    writeSequence(Arrays.<Parser<TInput>>asList(parser.leftParser(), Parsers.zeroOrMore(parser.rightParser()))); // PORT: §3.10 new Parser<TInput>[] -> list
                    break;

                case ZeroOrOne:
                    writeSequence(Arrays.<Parser<TInput>>asList(parser.leftParser(), Parsers.zeroOrOne(parser.rightParser()))); // PORT: §3.10 new Parser<TInput>[] -> list
                    break;

                default:
                    writeSequence(Arrays.<Parser<TInput>>asList(parser.leftParser(), parser.rightParser())); // PORT: §3.10 new Parser<TInput>[] -> list
                    break;
            }
        }

        @Override
        public <TOutput> void visitBest(BestParser2<TInput, TOutput> parser)
        {
            writeAlternation(parser.parsers());
        }

        @Override
        public void visitBest(BestParser<TInput> parser)
        {
            writeAlternation(parser.parsers());
        }

        @Override
        public <TOutput> void visitConvert(ConvertParser<TInput, TOutput> parser)
        {
            this.visit(parser.pattern());
        }

        @Override
        public void visitFails(FailsParser<TInput> parser)
        {
            writeBracketed("fails(", ")", parser.pattern());
        }

        @Override
        public <TOutput> void visitFirst(FirstParser2<TInput, TOutput> parser)
        {
            writeAlternation(parser.parsers());
        }

        @Override
        public void visitFirst(FirstParser<TInput> parser)
        {
            writeAlternation(parser.parsers());
        }

        @Override
        public <TOutput> void visitForward(ForwardParser<TInput, TOutput> parser)
        {
            writeTerm("forward()");
        }

        @Override
        public <TOutput> void visitIf(IfParser2<TInput, TOutput> parser)
        {
            this.visit(parser.parser());
        }

        @Override
        public void visitIf(IfParser<TInput> parser)
        {
            this.visit(parser.parser());
        }

        @Override
        public <TOutput> void visitLimit(LimitParser<TInput, TOutput> parser)
        {
            this.visit(parser.Limited);
        }

        @Override
        public <TOutput> void visitMap(MapParser<TInput, TOutput> parser)
        {
            writeTerm("map()");
        }

        @Override
        public void visitMatch(MatchParser<TInput> parser)
        {
            writeTerm("match()");
        }

        @Override
        public <TOutput> void visitMatch(MatchParser2<TInput, TOutput> parser)
        {
            writeTerm("match()");
        }

        @Override
        public void visitNot(NotParser<TInput> parser)
        {
            writeBracketed("not(", ")", parser.pattern());
        }

        @Override
        public void visitOneOrMore(OneOrMoreParser<TInput> parser)
        {
            writeOneOrMore(parser.parser());
        }

        @Override
        public <TOutput> void visitOptional(OptionalParser<TInput, TOutput> parser)
        {
            writeOptional(parser.parser());
        }

        @Override
        public <TOutput> void visitProduce(ProduceParser<TInput, TOutput> parser)
        {
            this.visit(parser.parser());
        }

        @Override
        public <TOutput> void visitRequired(RequiredParser<TInput, TOutput> parser)
        {
            if (this.showRequired)
            {
                writeRequired(parser.parser());
            }
            else
            {
                this.visit(parser.parser());
            }
        }

        @Override
        public <TOutput> void visitRule(RuleParser<TInput, TOutput> parser)
        {
            writeSequence(parser.parsers());
        }

        @Override
        public void visitSequence(SequenceParser<TInput> parser)
        {
            writeSequence(parser.parsers());
        }

        @Override
        public void visitZeroOrMore(ZeroOrMoreParser<TInput> parser)
        {
            if (parser.zeroOrOne())
            {
                writeOptional(parser.parser());
            }
            else
            {
                writeZeroOrMore(parser.parser());
            }
        }
    }
}
