// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: named-argument builder for the defaulted Parameter constructor arguments (PORTING.md 3.12, D16).
package org.graylog.kusto.language.symbols;

import java.util.List;

import org.graylog.kusto.language.syntax.Expression;

/**
 * Named arguments for the three 11-parameter {@link Parameter} constructors ({@code Parameter.cs:129,157,185}).
 *
 * <p>One factory per defaulted upstream parameter, spelled like the C# parameter, so a catalog line stays
 * one Java line:
 * <pre>
 * new Parameter("name", ScalarTypes.String, minOccurring: 0)                               // C#
 * new Parameter("name", ScalarTypes.String, P.minOccurring(0))                             // Java
 * new Parameter("kind", ScalarTypes.String, ArgumentKind.Literal, s_values, isCaseSensitive: true, minOccurring: 0)
 * new Parameter("kind", ScalarTypes.String, ArgumentKind.Literal, s_values, P.isCaseSensitive(true), P.minOccurring(0))
 * </pre>
 * {@code Parameter} accepts a trailing {@code P...} after 2, 3 ({@code argumentKind}) or 4 ({@code values})
 * positional arguments, for each of the three second-argument types. Named arguments may come in any order.
 * Naming a parameter twice, or naming one that was also given positionally, is a C# compile error
 * (CS1740/CS1744); here it throws {@link IllegalArgumentException}.
 */
public final class P
{
    // slot = zero-based position of the parameter in the upstream constructor
    static final int ArgumentKindSlot = 2;
    static final int ValuesSlot = 3;
    static final int ExamplesSlot = 4;
    static final int IsCaseSensitiveSlot = 5;
    static final int DefaultValueIndicatorSlot = 6;
    static final int MinOccurringSlot = 7;
    static final int MaxOccurringSlot = 8;
    static final int DefaultValueSlot = 9;
    static final int DescriptionSlot = 10;

    private final int slot;
    private final Object value;

    private P(int slot, Object value)
    {
        this.slot = slot;
        this.value = value;
    }

    /** {@code argumentKind: ...} (default {@link ArgumentKind#Expression}). */
    public static P argumentKind(ArgumentKind argumentKind) { return new P(ArgumentKindSlot, argumentKind); }

    /** {@code values: ...} (default {@code null}). Boxed values keep their C# literal types: {@code int} → {@link Integer}, {@code long} → {@link Long}. */
    public static P values(List<?> values) { return new P(ValuesSlot, values); }

    /** {@code examples: ...} (default {@code null}). */
    public static P examples(List<String> examples) { return new P(ExamplesSlot, examples); }

    /** {@code isCaseSensitive: ...} (default {@code false}). */
    public static P isCaseSensitive(boolean isCaseSensitive) { return new P(IsCaseSensitiveSlot, isCaseSensitive); }

    /** {@code defaultValueIndicator: ...} (default {@code null}). */
    public static P defaultValueIndicator(String defaultValueIndicator) { return new P(DefaultValueIndicatorSlot, defaultValueIndicator); }

    /** {@code minOccurring: ...} (default {@code 1}). */
    public static P minOccurring(int minOccurring) { return new P(MinOccurringSlot, minOccurring); }

    /** {@code maxOccurring: ...} (default {@code 1}). */
    public static P maxOccurring(int maxOccurring) { return new P(MaxOccurringSlot, maxOccurring); }

    /** {@code defaultValue: ...} (default {@code null}). */
    public static P defaultValue(Expression defaultValue) { return new P(DefaultValueSlot, defaultValue); }

    /** {@code description: ...} (default {@code null}). */
    public static P description(String description) { return new P(DescriptionSlot, description); }

    /** The resolved values of the nine defaulted arguments. */
    static final class Args
    {
        ArgumentKind argumentKind = ArgumentKind.Expression;
        List<?> values = null;
        List<String> examples = null;
        boolean isCaseSensitive = false;
        String defaultValueIndicator = null;
        int minOccurring = 1;
        int maxOccurring = 1;
        Expression defaultValue = null;
        String description = null;
    }

    /**
     * Applies the upstream defaults, then the positional arguments ({@code positionalCount} counts the name and
     * the type argument), then the named arguments.
     */
    @SuppressWarnings("unchecked")
    static Args resolve(int positionalCount, ArgumentKind argumentKind, List<?> values, P[] named)
    {
        var args = new Args();
        if (positionalCount > ArgumentKindSlot)
            args.argumentKind = argumentKind;
        if (positionalCount > ValuesSlot)
            args.values = values;

        if (named == null)
            return args;

        var seen = new boolean[DescriptionSlot + 1];
        for (P p : named)
        {
            if (p == null)
                throw new NullPointerException("named");
            if (p.slot < positionalCount || seen[p.slot])
                throw new IllegalArgumentException("Parameter argument at position " + p.slot + " is specified more than once.");
            seen[p.slot] = true;

            switch (p.slot)
            {
                case ArgumentKindSlot: args.argumentKind = (ArgumentKind) p.value; break;
                case ValuesSlot: args.values = (List<?>) p.value; break;
                case ExamplesSlot: args.examples = (List<String>) p.value; break;
                case IsCaseSensitiveSlot: args.isCaseSensitive = (Boolean) p.value; break;
                case DefaultValueIndicatorSlot: args.defaultValueIndicator = (String) p.value; break;
                case MinOccurringSlot: args.minOccurring = (Integer) p.value; break;
                case MaxOccurringSlot: args.maxOccurring = (Integer) p.value; break;
                case DefaultValueSlot: args.defaultValue = (Expression) p.value; break;
                case DescriptionSlot: args.description = (String) p.value; break;
                default: throw new IllegalStateException();
            }
        }

        return args;
    }
}
