// Ported from: src/Kusto.Language/FunctionHelpers.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graylog.kusto.language.binding.Binder;
import org.graylog.kusto.language.parsing.TextFacts;
import org.graylog.kusto.language.symbols.ColumnSymbol;
import org.graylog.kusto.language.symbols.CustomReturnTypeContext;
import org.graylog.kusto.language.symbols.FunctionSymbol;
import org.graylog.kusto.language.symbols.ScalarTypes;
import org.graylog.kusto.language.symbols.TupleSymbol;
import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.syntax.LiteralExpression;
import org.graylog.kusto.language.syntax.SeparatedElement1;
import org.graylog.kusto.language.syntax.SummarizeOperator;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.graylog.kusto.language.syntax.SyntaxList1;
import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Linq;

/// <summary>
/// A class full of helper API's used when building custom return types for functions.
/// </summary>
public final class FunctionHelpers // PORT: §3.5 static class → final class with a private constructor
{
    private FunctionHelpers() { }

    /// <summary>
    /// The largest number of ocurrances for a repeatable parameter
    /// </summary>
    public static final int MaxRepeat = Short.MAX_VALUE;

    public static TupleSymbol makePrefixedTuple(CustomReturnTypeContext context, String parameterName, TupleSymbol baseTuple)
    {
        var resultNamePrefix = ((FunctionSymbol)context.signature().symbol()).resultNamePrefix(); // PORT: §3.14 ??
        var functionPrefix = resultNamePrefix != null ? resultNamePrefix : context.signature().symbol().name();
        var argumentPrefix = context.getResultName(context.getArgument(parameterName));
        return new TupleSymbol(Linq.select(baseTuple.columns(), c -> c.withName(makeColumnName(functionPrefix, argumentPrefix, c.name())).withOriginalColumns(c))); // PORT: §3.6
    }

    /// <summary>
    /// Makes a column name by joining multiple parts using underscores.
    /// </summary>
    public static String makeColumnName(String... nameParts)
    {
        if (nameParts.length == 1)
        {
            return nameParts[0];
        }
        else
        {
            return DotNetStrings.join("_", Linq.where(Arrays.asList(nameParts), p -> p != null)); // PORT: §3.6, §3.14
        }
    }

    /// <summary>
    /// Adds the column referenced by the argument corresponding to the specified parameter to the list.
    /// </summary>
    public static void addReferencedColumn(List<ColumnSymbol> columns, CustomReturnTypeContext context, String parameterName)
    {
        var arg = context.getArgument(parameterName);
        if (arg != null && arg.referencedSymbol() instanceof ColumnSymbol cs)
        {
            columns.add(cs);
        }
    }

    /// <summary>
    /// Adds the columns referenced by the arguments corresponding to the specified parameter to the list.
    /// </summary>
    public static void addReferencedColumns(List<ColumnSymbol> columns, CustomReturnTypeContext context, String parameterName)
    {
        columns.addAll(Linq.ofType(Linq.select(context.getArguments(parameterName), a -> a.referencedSymbol()), ColumnSymbol.class)); // PORT: §3.6, §3.10
    }

    /// <summary>
    /// Gets the value of the constant expression.
    /// </summary>
    public static String getConstantValue(Expression expr)
    {
        var value = expr.constantValue(); // PORT: §3.14 ?. and ??; §5.2 ToString via DotNet.str
        return value != null ? DotNet.str(value) : "";
    }

    /// <summary>
    /// Gets the values for all the constant expressions.
    /// </summary>
    public static List<String> getConstantValues(SyntaxList1<SeparatedElement1<Expression>> expressions)
    {
        var list = new ArrayList<String>();

        for (int i = 0; i < expressions.size(); i++)
        {
            var expr = expressions.get(i).element();
            list.add(getConstantValue(expr));
        }

        return list;
    }

    /// <summary>
    /// Converts all non letters and digits into underscores.
    /// </summary>
    public static String makeValidNameFragment(String text)
    {
        var all = true; for (int i = 0; i < text.length() && all; i++) { all = TextFacts.isLetterOrDigit(text.charAt(i)); } // PORT: §3.6 text.All(c => TextFacts.IsLetterOrDigit(c)), UTF-16 code units
        if (!all)
        {
            var chars = new char[text.length()]; // PORT: §3.6 new string(text.Select(c => TextFacts.IsLetterOrDigit(c) ? c : '_').ToArray())
            for (int i = 0; i < text.length(); i++) { var c = text.charAt(i); chars[i] = TextFacts.isLetterOrDigit(c) ? c : '_'; }
            return new String(chars);
        }
        else
        {
            return text;
        }
    }

    /// <summary>
    /// Gets the <see cref="ColumnSymbol"/>s referenced in the by clause of the summarize operator
    /// given the args to an invocation of an aggregate.
    /// </summary>
    public static List<ColumnSymbol> getSummarizeByColumns(List<Expression> args)
    {
        if (args.size() > 0)
        {
            var op = args.get(0).getFirstAncestorOrSelf(SummarizeOperator.class); // PORT: §3.10
            if (op != null && op.byClause() != null)
            {
                return getColumnSymbols(op.byClause().expressions());
            }
        }

        return EmptyReadOnlyList.instance(); // PORT: §3.9
    }

    /// <summary>
    /// Get the list of <see cref="ColumnSymbol"/> referenced by the expressions in a list of expressions.
    /// </summary>
    private static List<ColumnSymbol> getColumnSymbols(SyntaxList1<SeparatedElement1<Expression>> exprs)
    {
        List<ColumnSymbol> symbols = null;

        for (var i = 0; i < exprs.size(); i++)
        {
            var expr = exprs.get(i).element();

            expr = Binder.getUnderlyingExpression(expr);

            if (Binder.getResultColumn(expr) instanceof ColumnSymbol c)
            {
                if (symbols == null)
                {
                    symbols = new ArrayList<ColumnSymbol>();
                }

                symbols.add(c);
            }
        }

        return symbols != null ? symbols : EmptyReadOnlyList.instance(); // PORT: §3.14 ??; §3.9
    }

    public static boolean isBoolean(Expression expr)
    {
        return expr.resultType() == ScalarTypes.Bool
            || (expr instanceof LiteralExpression lit && lit.kind() == SyntaxKind.BooleanLiteralExpression);
    }
}
