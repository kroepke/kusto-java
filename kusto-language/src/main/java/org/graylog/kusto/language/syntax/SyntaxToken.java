// Ported from: src/Kusto.Language/Syntax/SyntaxToken.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.syntax;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.KustoFacts;
import org.graylog.kusto.language.parsing.LexicalToken;
import org.graylog.kusto.language.parsing.TextFacts;
import org.graylog.kusto.language.parsing.TokenParser;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.dotnet.DateTime;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetChars;
import org.graylog.kusto.language.utils.dotnet.DotNetDecimal;
import org.graylog.kusto.language.utils.dotnet.DotNetGuid;
import org.graylog.kusto.language.utils.dotnet.DotNetNumber;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.IntRef;
import org.graylog.kusto.language.utils.dotnet.Internal;
import org.graylog.kusto.language.utils.dotnet.Linq;
import org.graylog.kusto.language.utils.dotnet.TimeSpan;

/// <summary>
/// A single token in the syntax grammar.
/// </summary>
public abstract class SyntaxToken extends SyntaxElement
{
    protected SyntaxToken(List<Diagnostic> diagnostics)
    {
        super(diagnostics);
    }

    /// <summary>
    /// Any whitespace or comments preceding this token.
    /// </summary>
    public String trivia() { return ""; }

    /// <summary>
    /// The raw text of the token
    /// </summary>
    public String text() { return ""; }

    /// <summary>
    /// The value of the literal.
    /// </summary>
    public Object value() { return this.text(); }

    /// <summary>
    /// The text of the literal without prefix, parentheses or encoding.
    /// </summary>
    public String valueText() { return this.text(); }

    @Override
    public int childCount() { return 0; }

    /// <summary>
    /// The width (number of characters) of the trivia preceding the token.
    /// </summary>
    @Override
    public int triviaWidth() { return this.trivia().length(); }

    /// <summary>
    /// The width (number of characters) of the token including trivia.
    /// </summary>
    @Override
    public int fullWidth() { return this.trivia().length() + this.text().length(); }

    /// <summary>
    /// The width (number of characters) of the token, not including trivia.
    /// </summary>
    @Override
    public int width() { return this.text().length(); }

    @Override
    public boolean isToken() { return true; }

    /// <summary>
    /// The token is a literal.
    /// </summary>
    public boolean isLiteral() { return false; }

    /// <summary>
    /// The prefix of the literal in the form: prefix(value)
    /// </summary>
    public String prefix() { return ""; }

    /// <summary>
    /// Creates a copy of this <see cref="SyntaxToken"/>
    /// </summary>
    @Override
    public SyntaxToken clone(boolean includeDiagnostics) { return (SyntaxToken)this.cloneCore(includeDiagnostics); } // PORT: §3.10 'new' hiding → covariant override

    @Override
    public SyntaxToken clone() { return clone(true); } // PORT: §3.12

    @Override
    public String toString(IncludeTrivia includeTrivia)
    {
        if (!DotNetStrings.isNullOrEmpty(this.trivia()) && includeTrivia == IncludeTrivia.All)
        {
            return this.trivia() + this.text();
        }
        else
        {
            return this.text();
        }
    }

    @Internal
    public void write(StringBuilder builder, IncludeTrivia includeTrivia, int initialTriviaStart)
    {
        if (!DotNetStrings.isNullOrEmpty(this.trivia()))
        {
            boolean beforeQuery = this.triviaStart() == initialTriviaStart;
            boolean afterQuery = this.kind() == SyntaxKind.EndOfTextToken;

            switch (includeTrivia)
            {
                case All:
                    builder.append(this.trivia());
                    break;

                case Interior:
                    if (!beforeQuery)
                    {
                        builder.append(this.trivia());
                    }
                    break;

                case Minimal:
                    if (!beforeQuery && !afterQuery)
                    {
                        // if there is any trivia replace it with single space or line-break
                        if (this.trivia().contains("\n"))
                        {
                            // trivia had a line feed, so minimal trivia is just one line feed
                            builder.append("\n");
                        }
                        else
                        {
                            // minimal trivia is just a single space
                            builder.append(" ");
                        }
                    }
                    break;

                case SingleLine:
                    if (!beforeQuery && !afterQuery)
                    {
                        // if there was any trivia replace it with a single space.
                        builder.append(" ");
                    }
                    break;
            }
        }

        builder.append(this.text());
    }

    /// <summary>
    /// Gets the next <see cref="SyntaxToken"/> in lexical order.
    /// </summary>
    public SyntaxToken getNextToken(boolean includeZeroWidthTokens)
    {
        return getNextToken(null, this, includeZeroWidthTokens);
    }

    public SyntaxToken getNextToken() // PORT: §3.12
    {
        return getNextToken(false);
    }

    /// <summary>
    /// Gets the previous <see cref="SyntaxToken"/> in lexical orer.
    /// </summary>
    public SyntaxToken getPreviousToken(boolean includeZeroWidthTokens)
    {
        return getPreviousToken(null, this, includeZeroWidthTokens);
    }

    public SyntaxToken getPreviousToken() // PORT: §3.12
    {
        return getPreviousToken(false);
    }

    public static SyntaxToken from(LexicalToken token, Diagnostic diagnostic)
    {
        var dx = token.diagnostics();
        if (diagnostic != null)
        {
            dx = ListExtensions.toReadOnly(Linq.concat(dx, Arrays.asList(new Diagnostic[] { diagnostic }))); // PORT: §3.6, §3.5
        }

        switch (SyntaxFacts.getCategory(token.kind())) // PORT: §3.5
        {
            case Identifier:
                return SyntaxToken.identifier(token.trivia(), token.text(), null, dx); // PORT: §3.12
            case Keyword:
                return SyntaxToken.keyword(token.trivia(), token.kind(), null, dx); // PORT: §3.12
            case Literal:
                return SyntaxToken.literal(token.trivia(), token.text(), token.kind(), dx);
            case Punctuation:
                return SyntaxToken.punctuation(token.trivia(), token.kind(), dx);
            case Operator:
                return SyntaxToken.operator(token.trivia(), token.kind(), dx);
            case Other:
            default:
                return SyntaxToken.other(token.trivia(), token.text(), token.kind(), dx);
        }
    }

    public static SyntaxToken from(LexicalToken token) // PORT: §3.12
    {
        return from(token, null);
    }

    public static SyntaxToken keyword(String trivia, SyntaxKind keyword, String valueText, List<Diagnostic> diagnostics)
    {           
        checkCategory(keyword, SyntaxCategory.Keyword);

        if (valueText != null && !Objects.equals(valueText, SyntaxFacts.getText(keyword))) // PORT: §3.14, §3.5
        {
            return new TextAndKindToken(trivia, SyntaxFacts.getText(keyword), valueText, keyword, diagnostics); // PORT: §3.5
        }
        else
        {
            return new KindToken(trivia, keyword, diagnostics);
        }
    }

    public static SyntaxToken keyword(String trivia, SyntaxKind keyword, String valueText) // PORT: §3.12
    {
        return keyword(trivia, keyword, valueText, null);
    }

    public static SyntaxToken keyword(String trivia, SyntaxKind keyword) // PORT: §3.12
    {
        return keyword(trivia, keyword, null);
    }

    public static SyntaxToken identifier(String trivia, String text, String valueText, List<Diagnostic> diagnostics)
    {
        if (valueText != null && !Objects.equals(valueText, text)) // PORT: §3.14
        {
            return new TextAndKindToken(trivia, text, valueText, SyntaxKind.IdentifierToken, diagnostics);
        }
        else
        {
            return new IdentifierToken(trivia, text, diagnostics);
        }
    }

    public static SyntaxToken identifier(String trivia, String text, String valueText) // PORT: §3.12
    {
        return identifier(trivia, text, valueText, null);
    }

    public static SyntaxToken identifier(String trivia, String text) // PORT: §3.12
    {
        return identifier(trivia, text, null);
    }

    public static SyntaxToken punctuation(String trivia, SyntaxKind kind, List<Diagnostic> diagnostics)
    {
        checkCategory(kind, SyntaxCategory.Punctuation);
        return new KindToken(trivia, kind, diagnostics);
    }

    public static SyntaxToken punctuation(String trivia, SyntaxKind kind) // PORT: §3.12
    {
        return punctuation(trivia, kind, null);
    }

    public static SyntaxToken operator(String trivia, SyntaxKind kind, List<Diagnostic> diagnostics)
    {
        checkCategory(kind, SyntaxCategory.Operator);
        return new KindToken(trivia, kind, diagnostics);
    }

    public static SyntaxToken operator(String trivia, SyntaxKind kind) // PORT: §3.12
    {
        return operator(trivia, kind, null);
    }

    public static SyntaxToken literal(String trivia, String text, SyntaxKind kind, List<Diagnostic> diagnostics)
    {
        checkCategory(kind, SyntaxCategory.Literal);
        return new LiteralToken(trivia, text, kind, diagnostics);
    }

    public static SyntaxToken literal(String trivia, String text, SyntaxKind kind) // PORT: §3.12
    {
        return literal(trivia, text, kind, null);
    }

    public static SyntaxToken other(String trivia, String text, SyntaxKind kind, List<Diagnostic> diagnostics)
    {
        checkCategory(kind, SyntaxCategory.Other);
        return new TextAndKindToken(trivia, text, text, kind, diagnostics);
    }

    public static SyntaxToken other(String trivia, String text, SyntaxKind kind) // PORT: §3.12
    {
        return other(trivia, text, kind, null);
    }

    public static SyntaxToken missing(String trivia, SyntaxKind kind, List<Diagnostic> diagnostics)
    {
        return new MissingToken(trivia, kind, diagnostics);
    }

    public static SyntaxToken missing(SyntaxKind kind, Diagnostic diagnostic)
    {
        return new MissingToken("", kind, diagnostic != null ? Arrays.asList(new Diagnostic[] { diagnostic }) : null); // PORT: §3.17 array as IReadOnlyList
    }

    public static SyntaxToken missing(SyntaxKind kind) // PORT: §3.12
    {
        return missing(kind, null);
    }

    private static void checkCategory(SyntaxKind kind, SyntaxCategory category)
    {
        if (SyntaxFacts.getCategory(kind) != category) // PORT: §3.5
        {
            throw new IllegalArgumentException("The kind " + DotNet.str(kind) + " is not a " + category.toString().toLowerCase(Locale.ROOT)); // PORT: §3.16, §3.14, §5.1
        }
    }

    /// <summary>
    /// A <see cref="SyntaxToken"/> for identifiers.
    /// </summary>
    private static final class IdentifierToken extends SyntaxToken
    {
        private final String trivia;
        private final String text;
        private final int fullWidth;

        @Override public String trivia() { return this.trivia; }
        @Override public String text() { return this.text; }
        @Override public int fullWidth() { return this.fullWidth; }

        public IdentifierToken(String trivia, String text, List<Diagnostic> diagnostics)
        {
            super(diagnostics);
            this.trivia = trivia != null ? trivia : ""; // PORT: §3.14
            this.text = text;
            this.fullWidth = this.trivia.length() + this.text.length();
        }

        @Override public SyntaxKind kind() { return SyntaxKind.IdentifierToken; }

        @Override public Object value() { return this.text(); }

        @Override
        protected SyntaxElement cloneCore(boolean includeDiagnostics)
        {
            return new IdentifierToken(this.trivia(), this.text(), includeDiagnostics ? this.syntaxDiagnostics() : null);
        }
    }

    /// <summary>
    /// A <see cref="SyntaxToken"/> for keywords.
    /// </summary>
    private static final class KindToken extends SyntaxToken
    {
        private final String trivia;
        private final SyntaxKind kind;
        private final int fullWidth;

        @Override public String trivia() { return this.trivia; }
        @Override public SyntaxKind kind() { return this.kind; }
        @Override public int fullWidth() { return this.fullWidth; }

        public KindToken(String trivia, SyntaxKind kind, List<Diagnostic> diagnostics)
        {
            super(diagnostics);
            this.trivia = trivia != null ? trivia : ""; // PORT: §3.14
            this.kind = kind;
            this.fullWidth = this.trivia.length() + SyntaxFacts.getText(this.kind()).length();
        }

        @Override public String text() { return SyntaxFacts.getText(this.kind()); }

        @Override
        protected SyntaxElement cloneCore(boolean includeDiagnostics)
        {
            return new KindToken(this.trivia(), this.kind(), includeDiagnostics ? this.syntaxDiagnostics() : null);
        }
    }

    /// <summary>
    /// A <see cref="SyntaxToken"/> that encodes the text and kind
    /// </summary>
    private static final class TextAndKindToken extends SyntaxToken
    {
        private final String trivia;
        private final String text;
        private final String valueText;
        private final SyntaxKind kind;
        private final int fullWidth;

        @Override public String trivia() { return this.trivia; }
        @Override public String text() { return text; }
        @Override public String valueText() { return valueText; }
        @Override public Object value() { return valueText; }
        @Override public SyntaxKind kind() { return this.kind; }
        @Override public int fullWidth() { return this.fullWidth; }

        public TextAndKindToken(String trivia, String text, String valueText, SyntaxKind kind, List<Diagnostic> diagnostics)
        {
            super(diagnostics);
            this.trivia = trivia != null ? trivia : ""; // PORT: §3.14
            this.text = text;
            this.valueText = valueText != null ? valueText : text; // PORT: §3.14
            this.kind = kind;
            this.fullWidth = this.trivia.length() + this.text.length();
        }

        @Override
        protected SyntaxElement cloneCore(boolean includeDiagnostics)
        {
            return new TextAndKindToken(this.trivia(), this.text(), this.valueText(), this.kind(), includeDiagnostics ? this.syntaxDiagnostics() : null);
        }
    }

    /// <summary>
    /// A <see cref="SyntaxToken"/> for a literal value.
    /// </summary>
    private static final class LiteralToken extends SyntaxToken
    {
        private final String trivia;
        private final String text;
        private final SyntaxKind kind;
        private final int fullWidth;

        @Override public String trivia() { return this.trivia; }
        @Override public String text() { return text; }
        @Override public SyntaxKind kind() { return this.kind; }
        @Override public int fullWidth() { return this.fullWidth; }

        public LiteralToken(String trivia, String text, SyntaxKind kind, List<Diagnostic> diagnostics)
        {
            super(diagnostics);
            this.trivia = trivia != null ? trivia : ""; // PORT: §3.14
            this.text = text;
            this.kind = kind;
            this.fullWidth = this.trivia.length() + this.text.length();
        }

        @Override
        protected SyntaxElement cloneCore(boolean includeDiagnostics)
        {
            return new LiteralToken(this.trivia(), this.text(), this.kind(), includeDiagnostics ? this.syntaxDiagnostics() : null);
        }

        @Override public boolean isLiteral() { return true; }

        private Object value;

        @Override
        public Object value()
        {
            if (this.value == null)
            {
                this.value = this.getValue();
            }

            return this.value;
        }

        private Object getValue()
        {
            if (this.kind() != SyntaxKind.StringLiteralToken
                && isNull(this.text()))
            {
                return null;
            }

            switch (this.kind())
            {
                case BooleanLiteralToken:
                    return getBooleanValue(this.text());
                case IntLiteralToken:
                    return getIntValue(this.text());
                case LongLiteralToken:
                    return getLongValue(this.text());
                case RealLiteralToken:
                    return getRealValue(this.text());
                case DecimalLiteralToken:
                    return getDecimalValue(this.text());
                case TimespanLiteralToken:
                    return getTimeSpanValue(this.text());
                case DateTimeLiteralToken:
                    return getDateTimeValue(this.text());
                case GuidLiteralToken:
                case RawGuidLiteralToken:
                    return getGuidValue(this.text());
                case StringLiteralToken:
                    return KustoFacts.getStringLiteralValue(this.text());
                default:
                    throw new IllegalStateException("Unhandled literal syntax kind: " + DotNet.str(this.kind())); // PORT: §3.16, §3.14
            }
        }

        private String valueText;

        @Override
        public String valueText()
        {
            if (this.valueText == null)
            {
                this.valueText = getValueText();
            }

            return valueText;
        }

        private String getValueText()
        {
            switch (this.kind())
            {
                case BooleanLiteralToken:
                case IntLiteralToken:
                case LongLiteralToken:
                case RealLiteralToken:
                case DecimalLiteralToken:
                    return getInteriorText(text);
                case TimespanLiteralToken:
                case DateTimeLiteralToken:
                case GuidLiteralToken:
                case RawGuidLiteralToken:
                    return destringify(getInteriorText(text));
                case StringLiteralToken:
                    return (String)this.value();
                default:
                    throw new IllegalStateException("Unhandled literal syntax kind: " + DotNet.str(this.kind())); // PORT: §3.16, §3.14
            }
        }

        @Override
        public String prefix()
        {
            var prefixLen = getPrefixLength(this.text());
            if (prefixLen > 0)
            {
                return this.text().substring(0, 0 + prefixLen); // PORT: §5.4 Substring(start, length)
            }
            else
            {
                return "";
            }
        }

        private static int getPrefixLength(String text)
        {
            int i = 0;
            while (i < text.length())
            {
                var ch = text.charAt(i);
                if (ch == '(' && i > 0)
                {
                    return i;
                }
                else if (!TextFacts.isLetter(ch))
                {
                    return 0;
                }
                else
                {
                    i++;
                }
            }

            return 0;
        }

        private static void getValueSpan(String text, IntRef start, IntRef length) // PORT: §3.3 out int → IntRef
        {
            start.value = 0;
            int end = text.length();

            var prefixLen = getPrefixLength(text);
            if (prefixLen > 0)
            {
                start.value = prefixLen + 1; // include (

                // trim leading whitespace
                while (start.value < text.length() && TextFacts.isWhitespace(text.charAt(start.value)))
                    start.value++;

                if (text.endsWith(")")) // PORT: D12 ordinal
                {
                    end = text.length() - 1;
                }

                // trim trailing whitespace
                while (end > start.value + 1 && TextFacts.isWhitespace(text.charAt(end - 1)))
                    end--;
            }

            length.value = end - start.value;
        }

        private static String getInteriorText(String text)
        {
            var start = new IntRef(); // PORT: §3.3
            var length = new IntRef();
            getValueSpan(text, start, length);

            return (start.value == 0 && length.value == text.length())
                ? text
                : destringify(text.substring(start.value, start.value + length.value)); // PORT: §5.4 Substring(start, length)
        }

        /// <summary>
        /// Returns the string literal value if the text is a string literal, otherwise just the text itself.
        /// </summary>
        private static String destringify(String text)
        {
            var trimmed = DotNetStrings.trim(text); // PORT: §5.1

            // if the interior text is a string literal, then decode it.
            return (trimmed.length() >= 2 && TokenParser.scanStringLiteral(trimmed) == trimmed.length())
                ? KustoFacts.getStringLiteralValue(trimmed)
                : text;
        }

        private static boolean isNull(String text)
        {
            var start = new IntRef(); // PORT: §3.3
            var length = new IntRef();
            getValueSpan(text, start, length);
            return DotNetStrings.compare(text, start.value, "null", 0, length.value) == 0; // PORT: §5.4, D12
        }

        private static Object getBooleanValue(String text)
        {
            var start = new IntRef(); // PORT: §3.3
            var length = new IntRef();
            getValueSpan(text, start, length);

            if (DotNetStrings.compare(text, start.value, "true", 0, length.value, true) == 0) // PORT: §5.4, D12
            {
                return true;
            }
            else if (DotNetStrings.compare(text, start.value, "false", 0, length.value, true) == 0) // PORT: §5.4, D12
            {
                return false;
            }
            else
            {
                return false;
            }
        }

        private static Integer getIntValue(String text) // PORT: §3.7 int? → Integer
        {
            var valueText = getInteriorText(text);
            switch (valueText)
            {
                case "min":
                    return Integer.MIN_VALUE;
                case "max":
                    return Integer.MAX_VALUE;
                default:
                    int result;
                    result = DotNetNumber.parseIntOrZero(valueText); // PORT: §5.2 Int32.TryParse, result 0 on failure
                    return result;
            }
        }

        private static long getLongValue(String text)
        {
            var valueText = getInteriorText(text);
            switch (valueText)
            {
                case "min":
                    return Long.MIN_VALUE;
                case "max":
                    return Long.MAX_VALUE;
                default:
                    long result;
                    result = DotNetNumber.parseLongOrZero(valueText); // PORT: §5.2 Int64.TryParse, result 0 on failure
                    return result;
            }
        }

        private static double getRealValue(String text)
        {
            var valueText = getInteriorText(text);
            switch (valueText)
            {
                case "min":
                    return -Double.MAX_VALUE; // PORT: §5.2 Double.MinValue is the most negative double
                case "max":
                    return Double.MAX_VALUE;
                default:
                    double result;
                    result = DotNetNumber.parseDoubleOrZero(valueText); // PORT: §5.2 Double.TryParse, result 0 on failure
                    return result;
            }
        }

        private static BigDecimal getDecimalValue(String text) // PORT: §3.7 decimal → BigDecimal
        {
            var valueText = getInteriorText(text);
            switch (valueText)
            {
                case "min":
                    return DotNetDecimal.MIN_VALUE; // PORT: §3.7
                case "max":
                    return DotNetDecimal.MAX_VALUE; // PORT: §3.7
                default:
                    BigDecimal result;
                    result = DotNetDecimal.parseOrZero(valueText); // PORT: §5.2 Decimal.TryParse, result 0 on failure
                    return result;
            }
        }

        private static TimeSpan getTimeSpanValue(String text)
        {
            var valueText = destringify(getInteriorText(text));

            // #if !BRIDGE
            TimeSpan result;
            if ((result = TimeSpan.tryParse(valueText)) != null) // PORT: §5.2, §3.3 TryParse → null on failure
            {
                return result;
            }
            // #endif
            switch (valueText)
            {
                case "min":
                    return TimeSpan.MIN_VALUE; // PORT: §3.7
                case "max":
                    return TimeSpan.MAX_VALUE; // PORT: §3.7
            }

            // find number/word split
            int split = 0;
            while (split < valueText.length() && !DotNetChars.isLetter(valueText.charAt(split))) // PORT: §5.1 char.IsLetter
            {
                split++;
            }

            var numberText = valueText.substring(0, 0 + split); // PORT: §5.4 Substring(start, length)
            var wordText = valueText.substring(split);

            Double number;
            if ((number = DotNetNumber.tryParseDouble(numberText)) != null) // PORT: §5.2, §3.3 TryParse → null on failure
            {
                switch (wordText)
                {
                    case "s":
                    case "sec":
                    case "second":
                    case "seconds":
                        return TimeSpan.fromSeconds(number);
                    case "m":
                    case "min":
                    case "minute":
                    case "minutes":
                        return TimeSpan.fromMinutes(number);
                    case "h":
                    case "hr":
                    case "hrs":
                    case "hour":
                    case "hours":
                        return TimeSpan.fromHours(number);
                    case "d":
                    case "day":
                    case "days":
                        return TimeSpan.fromDays(number);
                    case "ms":
                    case "milli":
                    case "millis":
                    case "millisec":
                    case "millisecond":
                    case "milliseconds":
                        return TimeSpan.fromMilliseconds(number);
                    case "micro":
                    case "micros":
                    case "microsec":
                    case "microsecond":
                    case "microseconds":
                        return TimeSpan.fromSeconds(number / 1_000_000.0);
                    case "nano":
                    case "nanos":
                    case "nanosec":
                    case "nanosecond":
                    case "nanoseconds":
                        return TimeSpan.fromSeconds(number / 1_000_000_000.0);
                    case "tick":
                    case "ticks":
                        return TimeSpan.fromTicks(DotNet.longCast(number)); // PORT: §5.2 (long)number
                }
            }

            // bad timespan literal?
            return TimeSpan.fromSeconds(0.0);
        }

        private static DateTime getDateTimeValue(String text)
        {
            var valueText = destringify(getInteriorText(text));
            switch (valueText)
            {
                case "min":
                    return DateTime.MIN_VALUE; // PORT: §3.7
                case "max":
                    return DateTime.MAX_VALUE; // PORT: §3.7
                default:
                    DateTime result;
                    result = DateTime.tryParse(valueText); // PORT: §5.2 DateTime.TryParse
                    return result != null ? result : DateTime.MIN_VALUE; // PORT: §5.2 failure leaves default(DateTime)
            }
        }

        private static UUID getGuidValue(String text) // PORT: §3.7 Guid → UUID
        {
            var valueText = destringify(getInteriorText(text));
            UUID result;
            result = DotNetGuid.tryParse(valueText); // PORT: §5.2 Guid.TryParse
            return result != null ? result : DotNetGuid.EMPTY; // PORT: §5.2 failure leaves Guid.Empty
        }
    }

    public static class MissingToken extends SyntaxToken
    {
        private final String trivia;
        private final SyntaxKind kind;

        @Override public String trivia() { return this.trivia; }
        @Override public SyntaxKind kind() { return this.kind; }
        @Override public int fullWidth() { return this.trivia.length(); }

        public MissingToken(String trivia, SyntaxKind kind, List<Diagnostic> diagnostics)
        {
            super(diagnostics);
            this.trivia = trivia;
            this.kind = kind;
        }

        @Override public String text() { return ""; }

        @Override public boolean isMissing() { return true; }

        @Override
        protected SyntaxElement cloneCore(boolean includeDiagnostics)
        {
            return new MissingToken(this.trivia(), this.kind(), includeDiagnostics ? this.syntaxDiagnostics() : null);
        }
    }
}
