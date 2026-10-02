// Ported from: src/Kusto.Language/Parser/Combinators/Parser.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.parsing;

import java.util.List;

import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.dotnet.DotNet;

/// <summary>
/// A parser combinator that can scan, search and parse input.
/// When parsing, may produce zero or more output values.
/// </summary>
// [DebuggerDisplay("{GetType().Name}: {Description}")] dropped // PORT: §3.20
public abstract class Parser<TInput>
{
    /// <summary>
    /// Create a shallow copy of this parser
    /// </summary>
    @Override
    protected abstract Parser<TInput> clone(); // PORT: §2.3 Clone() -> clone(); overrides Object.clone() without Cloneable

    /// <summary>
    /// The name of the parser.
    /// Most parsers have no name.
    /// </summary>
    private String tag = ""; // string.Empty
    public String tag() { return this.tag; }

    /// <summary>
    /// Creates a copy of this <see cref="Parser{TInput}"/> with the tag specified.
    /// </summary>
    public Parser<TInput> withTag(String tag)
    {
        tag = tag != null ? tag : ""; // PORT: §3.14 tag ?? string.Empty

        if (!tag.equals(this.tag())) // PORT: §3.14 string != string
        {
            var clone = this.clone();
            clone.tag = tag;
            clone.annotations = this.annotations();
            clone.isHidden = this.isHidden();
            return clone;
        }
        else
        {
            return this;
        }
    }

    /// <summary>
    /// Annotations on the parser.
    /// </summary>
    private List<Object> annotations = EmptyReadOnlyList.instance(); // PORT: §3.9 EmptyReadOnlyList<object>.Instance
    public List<Object> annotations() { return this.annotations; }

    /// <summary>
    /// Creates a copy of this <see cref="Parser{TInput}"/> with the annotations specified.
    /// </summary>
    @SuppressWarnings("unchecked")
    public Parser<TInput> withAnnotations(Iterable<?> annotations) // PORT: §3.10 IEnumerable<object> is covariant upstream
    {
        var list = ListExtensions.toReadOnly((Iterable<Object>) annotations); // PORT: §3.10

        if (this.annotations() != list) // reference inequality, as upstream
        {
            var clone = this.clone();
            clone.annotations = list;
            clone.tag = this.tag();
            clone.isHidden = this.isHidden();
            return clone;
        }
        else
        {
            return this;
        }
    }

    private String description;

    /// <summary>
    /// A description of the parser.
    /// </summary>
    public String description()
    {
        if (this.description == null)
        {
            if (this.tag() == null || this.tag().isEmpty()) // PORT: §3.14 string.IsNullOrEmpty
            {
                this.description = Describer.describe(this);
            }
            else
            {
                this.description = DotNet.str(this.tag()) + ": " + DotNet.str(Describer.describe(this.withTag(null))); // PORT: §3.14 interpolated string
            }
        }

        return this.description;
    }

    /// <summary>
    /// True if the parser is hidden from searching.
    /// </summary>
    private boolean isHidden = false;
    public boolean isHidden() { return this.isHidden; }

    /// <summary>
    /// Creates a copy of this <see cref="Parser{TInput}"/> with the IsHidden property specified.
    /// </summary>
    public Parser<TInput> withIsHidden(boolean isHidden)
    {
        if (isHidden != this.isHidden())
        {
            var clone = this.clone();
            clone.isHidden = isHidden;
            clone.tag = this.tag();
            clone.annotations = this.annotations();
            return clone;
        }
        else
        {
            return this;
        }
    }

    /// <summary>
    /// Creates a copy of the <see cref="Parser{TInput}"/> that is hidden from searching.
    /// </summary>
    public Parser<TInput> hide() { return this.withIsHidden(true); }

    /// <summary>
    /// True if the parser still succeeds if it does not match anything,
    /// and instead produces nothing.
    /// </summary>
    public boolean isOptional() { return false; }

    /// <summary>
    /// True if the parser still succeeds if it does not match anything,
    /// but produces a fixed value instead.
    /// </summary>
    public boolean isRequired() { return false; }

    /// <summary>
    /// True if the parser succeeds if any one of many child parsers succeed.
    /// </summary>
    public boolean isAlternation() { return false; }

    /// <summary>
    /// True if the parser succeeds if all of many child parsers succeed in sequence.
    /// </summary>
    public boolean isSequence() { return false; }

    /// <summary>
    /// True if the parser produces multiple outputs from repeatedly parsing a child parser.
    /// </summary>
    public boolean isRepetition() { return false; }

    /// <summary>
    /// True if the parser matches input directly.
    /// </summary>
    public boolean isMatch() { return false; }

    /// <summary>
    /// True if this parser forwards to another parser,
    /// meaning it may be involved in a cycle.
    /// </summary>
    public boolean isForward() { return false; }

    public boolean isNegation() { return false; }

    public boolean isConditional() { return false; }

    /// <summary>
    /// The number of child parsers this parser contains.
    /// </summary>
    public abstract int childParserCount();

    /// <summary>
    /// Gets the child parser at the specified index.
    /// </summary>
    public abstract Parser<TInput> getChildParser(int index);

    /// <summary>
    /// Gets the index of the child parser contained by this parser.
    /// </summary>
    public int getChildParserIndex(Parser<TInput> childParser)
    {
        var childCount = this.childParserCount();
        for (int i = 0; i < childCount; i++)
        {
            if (this.getChildParser(i) == childParser)
                return i;
        }

        return -1;
    }

    /// <summary>
    /// Invokes the corresponding <see cref="ParserVisitor{TInput}"/> visit method.
    /// </summary>
    public abstract void accept(ParserVisitor<TInput> visitor);

    /// <summary>
    /// Invokes the corresponding <see cref="ParserVisitor{TInput, TResult}"/> visit method.
    /// </summary>
    public abstract <TResult> TResult accept(ParserVisitor2<TInput, TResult> visitor); // PORT: §2.4

    /// <summary>
    /// Invokes the corresponding <see cref="ParserVisitor{TInput, TArg, TResult}"/> visit method.
    /// </summary>
    public abstract <TArg, TResult> TResult accept(ParserVisitor3<TInput, TArg, TResult> visitor, TArg arg); // PORT: §2.4

    /// <summary>
    /// Parses input source items and produces zero or more output items.
    /// </summary>
    public abstract int parse(Source<TInput> input, int inputStart, List<Object> output, int outputStart); // PORT: §3.17 List<object>

    /// <summary>
    /// Returns the number of source items that are successfully matched by this parser, or a negative number indicating failure.
    /// </summary>
    public abstract int scan(Source<TInput> input, int inputStart);
}
