// Ported from: src/Kusto.Language/Parser/ParseOptions.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language;

import java.util.Objects;

public class ParseOptions // PORT: D24 IEquatable<ParseOptions> -> equals(ParseOptions) + equals(Object)/hashCode
{
    /// <summary>
    /// Determines whether the token parser will always include an end token.
    /// </summary>
    private final boolean alwaysProduceEndToken;
    public boolean alwaysProduceEndToken() { return this.alwaysProduceEndToken; }

    /// <summary>
    /// Determines whether parenthesized literals like datetime(...) are allowed to
    /// contain line breaks.
    /// </summary>
    private final boolean allowLiteralsWithLineBreaks;
    public boolean allowLiteralsWithLineBreaks() { return this.allowLiteralsWithLineBreaks; }

    /// <summary>
    /// Determines whether the parser allows parts of wildcarded name to be non-adjacent.
    /// </summary>
    private final boolean allowNonAdjacentWildcardParts;
    public boolean allowNonAdjacentWildcardParts() { return this.allowNonAdjacentWildcardParts; }

    /// <summary>
    /// Determines which kind of parser to use.
    /// </summary>
    private final ParserKind parserKind;
    public ParserKind parserKind() { return this.parserKind; }

    private ParseOptions(
        boolean alwaysProduceEndTokens,
        boolean allowLiteralsWithLineBreaks,
        boolean allowNonAdjacentWildcardParts,
        ParserKind parserKind)
    {
        this.alwaysProduceEndToken = alwaysProduceEndTokens;
        this.allowLiteralsWithLineBreaks = allowLiteralsWithLineBreaks;
        this.allowNonAdjacentWildcardParts = allowNonAdjacentWildcardParts;
        this.parserKind = parserKind;
    }

    // PORT: §3.12 private With(...) keeps only the full signature: every caller names exactly one argument
    private ParseOptions with(
        Boolean alwaysProduceEndTokens, // PORT: §3.7 bool? -> Boolean
        Boolean allowLiteralsWithLineBreaks,
        Boolean allowNonAdjacentWildcardParts,
        ParserKind parserKind) // PORT: §3.7 ParserKind? -> nullable enum
    {
        var newAlwaysProduceEndTokens = alwaysProduceEndTokens != null ? alwaysProduceEndTokens : this.alwaysProduceEndToken(); // PORT: §3.14
        var newAllowLiteralsWithLineBreaks = allowLiteralsWithLineBreaks != null ? allowLiteralsWithLineBreaks : this.allowLiteralsWithLineBreaks(); // PORT: §3.14
        var newAllowNonAdjacentWildcardParts = allowNonAdjacentWildcardParts != null ? allowNonAdjacentWildcardParts : this.allowNonAdjacentWildcardParts(); // PORT: §3.14
        var newParserKind = parserKind != null ? parserKind : this.parserKind(); // PORT: §3.14

        if (newAlwaysProduceEndTokens != this.alwaysProduceEndToken()
            || newAllowLiteralsWithLineBreaks != this.allowLiteralsWithLineBreaks()
            || newAllowNonAdjacentWildcardParts != this.allowNonAdjacentWildcardParts()
            || newParserKind != this.parserKind())
        {
            return new ParseOptions(
                newAlwaysProduceEndTokens,
                newAllowLiteralsWithLineBreaks,
                newAllowNonAdjacentWildcardParts,
                newParserKind);
        }
        else
        {
            return this;
        }
    }

    /// <summary>
    /// Returns <see cref="ParseOptions"/> with the <see cref="AlwaysProduceEndToken"/> property set to specified value.
    /// </summary>
    public ParseOptions withAlwaysProduceEndTokens(boolean alwaysProduceEndTokens)
    {
        return with(alwaysProduceEndTokens, null, null, null); // PORT: §3.12 named argument -> positional
    }

    /// <summary>
    /// Returns <see cref="ParseOptions"/> with the <see cref="AllowLiteralsWithLineBreaks"/> property set to specified value.
    /// </summary>
    public ParseOptions withAllowLiteralsWithLineBreaks(boolean allow)
    {
        return with(null, allow, null, null); // PORT: §3.12 named argument -> positional
    }

    /// <summary>
    /// Returns <see cref="ParseOptions"/> with the <see cref="AllowNonAdjacentWildcardParts"/> property set to specified value.
    /// </summary>
    public ParseOptions withAllowNonAdjacentWildcardParts(boolean allow)
    {
        return with(null, null, allow, null); // PORT: §3.12 named argument -> positional
    }

    /// <summary>
    /// Returns <see cref="ParseOptions"/> with the <see cref="ParserKind"/> property set to specified value.
    /// </summary>
    public ParseOptions withParserKind(ParserKind kind)
    {
        return with(null, null, null, kind); // PORT: §3.12 named argument -> positional
    }

    public boolean equals(ParseOptions other)
    {
        return equalExceptForParseKind(other)
            && other.parserKind() == parserKind();
    }

    public boolean equalExceptForParseKind(ParseOptions other)
    {
        return other.alwaysProduceEndToken() == this.alwaysProduceEndToken()
            && other.allowLiteralsWithLineBreaks() == this.allowLiteralsWithLineBreaks()
            && other.allowNonAdjacentWildcardParts() == this.allowNonAdjacentWildcardParts();
    }

    @Override
    public boolean equals(Object obj) // PORT: D24 collections call equals(Object)
    {
        return obj instanceof ParseOptions other && equals(other);
    }

    @Override
    public int hashCode() // PORT: D24 upstream has no GetHashCode override; Java needs one consistent with equals(Object)
    {
        return Objects.hash(this.alwaysProduceEndToken, this.allowLiteralsWithLineBreaks, this.allowNonAdjacentWildcardParts, this.parserKind);
    }

    /// <summary>
    /// Default parse options.
    /// </summary>
    public static ParseOptions Default = new ParseOptions(
        true,  // alwaysProduceEndTokens // PORT: §3.12 named arguments -> positional
        false, // allowLiteralsWithLineBreaks
        false, // allowNonAdjacentWildcardParts
        ParserKind.Default); // parserKind
}
