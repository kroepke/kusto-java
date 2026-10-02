// Ported from: src/Kusto.Language/Symbols/Parameter.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graylog.kusto.language.KustoFacts;
import org.graylog.kusto.language.binding.Binder;
import org.graylog.kusto.language.parsing.QueryParser;
import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.syntax.FunctionParameter;
import org.graylog.kusto.language.syntax.FunctionParameters;
import org.graylog.kusto.language.syntax.IncludeTrivia;
import org.graylog.kusto.language.syntax.SeparatedElement1;
import org.graylog.kusto.language.utils.ArgumentCheckers;
import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Linq;

/// <summary>
/// A parameter declaration for a <see cref="Signature"/>.
/// </summary>
// PORT: §2.3 no equals/hashCode override: reference identity is relied on (Signature/CustomReturnTypeContext IndexOf, ==)
public class Parameter
{
    @SuppressWarnings("unused")
    private String debugText() // PORT: §3.20 [DebuggerDisplay] target kept as a private method
    {
        return DebugDisplay.getText(this);
    }

    /// <summary>
    /// The name of the parameter.
    /// </summary>
    private final String name;
    public String name() { return this.name; }

    /// <summary>
    /// The kind of parameter.
    /// </summary>
    private final ParameterTypeKind typeKind;
    public ParameterTypeKind typeKind() { return this.typeKind; }

    /// <summary>
    /// The possible declared types of the parameter.
    /// </summary>
    private final List<TypeSymbol> declaredTypes;
    public List<TypeSymbol> declaredTypes() { return this.declaredTypes; }

    /// <summary>
    /// The kind of argument this parameter is constrained to.
    /// </summary>
    private final ArgumentKind argumentKind;
    public ArgumentKind argumentKind() { return this.argumentKind; }

    /// <summary>
    /// The minimum number of times the parameter can occur in the signature.
    /// </summary>
    private final int minOccurring;
    public int minOccurring() { return this.minOccurring; }

    /// <summary>
    /// The maximum number of times the parameter can occur in the signature.
    /// </summary>
    private final int maxOccurring;
    public int maxOccurring() { return this.maxOccurring; }

    /// <summary>
    /// True if the parameter is optional.
    /// </summary>
    public boolean isOptional() { return this.minOccurring() == 0; }

    /// <summary>
    /// True if the parameter can be repeated.
    /// </summary>
    public boolean isRepeatable() { return this.maxOccurring() > 1; }

    /// <summary>
    /// The specific values that this parameter is constrained to.
    /// </summary>
    // PORT: §3.7 heterogeneous boxed values (String, Integer, Long, Double, Boolean) kept as given; Java
    // Integer.equals(Long) is false where C# object.Equals of a boxed int and long is also false, so the
    // catalogs must keep the C# literal types (int → Integer, long → Long) for comparisons to match.
    private final List<Object> values;
    public List<Object> values() { return this.values; }

    /// <summary>
    /// Completion list examples.
    /// </summary>
    private final List<String> examples;
    public List<String> examples() { return this.examples; }

    /// <summary>
    /// If true, then text values (string literal or tokens) must be matched case sensitive.
    /// </summary>
    private final boolean isCaseSensitive;
    public boolean isCaseSensitive() { return this.isCaseSensitive; }

    /// <summary>
    /// The string literal that can be used to indicate use of the default value.
    /// </summary>
    private final String defaultValueIndicator;
    public String defaultValueIndicator() { return this.defaultValueIndicator; }

    /// <summary>
    /// The default value specified for user defined functions.
    /// </summary>
    private final Expression defaultValue;
    public Expression defaultValue() { return this.defaultValue; }

    /// <summary>
    /// The descripton of the parameter.
    /// </summary>
    private final String description;
    public String description() { return this.description; }

    private static final List<Object> NoValues = EmptyReadOnlyList.instance(); // PORT: §3.9

    private static final List<String> NoExamples = EmptyReadOnlyList.instance(); // PORT: §3.9

    @SuppressWarnings("unchecked")
    private Parameter(
            String name,
            ParameterTypeKind typeKind,
            TypeSymbol[] types,
            ArgumentKind argumentKind,
            Iterable<?> values,
            Iterable<String> examples,
            boolean isCaseSensitive,
            String defaultValueIndicator,
            int minOccurring,
            int maxOccurring,
            Expression defaultValue,
            String description)
    {
        if (typeKind == ParameterTypeKind.Declared)
        {
            this.declaredTypes = ListExtensions.toReadOnly(ArgumentCheckers.checkArgumentNullOrEmptyOrElementNull(types != null ? Arrays.asList(types) : null, "types")); // PORT: §3.5; §3.10 array → List
        }
        else
        {
            this.declaredTypes = EmptyReadOnlyList.instance(); // PORT: §3.9
        }

        this.name = name;
        this.typeKind = typeKind;
        this.argumentKind = argumentKind;
        this.values = values != null ? ListExtensions.toReadOnly((Iterable<Object>) values) : NoValues; // PORT: §3.10 IEnumerable<object> receives any element type
        this.examples = examples != null ? ListExtensions.toReadOnly(examples) : NoExamples; // PORT: §3.5
        this.isCaseSensitive = isCaseSensitive;
        this.minOccurring = defaultValue != null ? 0 : minOccurring;
        this.maxOccurring = defaultValue != null ? 1 : maxOccurring;
        this.defaultValueIndicator = defaultValueIndicator;
        this.defaultValue = defaultValue;
        this.description = description != null ? description : ""; // PORT: §3.14 ??
    }

    public Parameter(
            String name,
            ParameterTypeKind typeKind,
            ArgumentKind argumentKind,
            List<?> values,
            List<String> examples,
            boolean isCaseSensitive,
            String defaultValueIndicator,
            int minOccurring,
            int maxOccurring,
            Expression defaultValue,
            String description)
    {
        this(
                name, typeKind, null,
                argumentKind,
                values,
                examples,
                isCaseSensitive,
                defaultValueIndicator,
                minOccurring,
                maxOccurring,
                defaultValue,
                description);
    }

    public Parameter(String name, ParameterTypeKind typeKind, ArgumentKind argumentKind, List<?> values, List<String> examples, boolean isCaseSensitive, String defaultValueIndicator, int minOccurring, int maxOccurring, Expression defaultValue) // PORT: §3.12 description = null
    {
        this(name, typeKind, argumentKind, values, examples, isCaseSensitive, defaultValueIndicator, minOccurring, maxOccurring, defaultValue, null);
    }

    public Parameter(String name, ParameterTypeKind typeKind, ArgumentKind argumentKind, List<?> values, List<String> examples, boolean isCaseSensitive, String defaultValueIndicator, int minOccurring, int maxOccurring) // PORT: §3.12 defaultValue = null, description = null
    {
        this(name, typeKind, argumentKind, values, examples, isCaseSensitive, defaultValueIndicator, minOccurring, maxOccurring, null, null);
    }

    public Parameter(String name, ParameterTypeKind typeKind, ArgumentKind argumentKind, List<?> values, List<String> examples, boolean isCaseSensitive, String defaultValueIndicator, int minOccurring) // PORT: §3.12 maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, typeKind, argumentKind, values, examples, isCaseSensitive, defaultValueIndicator, minOccurring, 1, null, null);
    }

    public Parameter(String name, ParameterTypeKind typeKind, ArgumentKind argumentKind, List<?> values, List<String> examples, boolean isCaseSensitive, String defaultValueIndicator) // PORT: §3.12 minOccurring = 1, maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, typeKind, argumentKind, values, examples, isCaseSensitive, defaultValueIndicator, 1, 1, null, null);
    }

    public Parameter(String name, ParameterTypeKind typeKind, ArgumentKind argumentKind, List<?> values, List<String> examples, boolean isCaseSensitive) // PORT: §3.12 defaultValueIndicator = null, minOccurring = 1, maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, typeKind, argumentKind, values, examples, isCaseSensitive, null, 1, 1, null, null);
    }

    public Parameter(String name, ParameterTypeKind typeKind, ArgumentKind argumentKind, List<?> values, List<String> examples) // PORT: §3.12 isCaseSensitive = false, defaultValueIndicator = null, minOccurring = 1, maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, typeKind, argumentKind, values, examples, false, null, 1, 1, null, null);
    }

    public Parameter(String name, ParameterTypeKind typeKind, ArgumentKind argumentKind, List<?> values) // PORT: §3.12 examples = null, isCaseSensitive = false, defaultValueIndicator = null, minOccurring = 1, maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, typeKind, argumentKind, values, null, false, null, 1, 1, null, null);
    }

    public Parameter(String name, ParameterTypeKind typeKind, ArgumentKind argumentKind) // PORT: §3.12 values = null, examples = null, isCaseSensitive = false, defaultValueIndicator = null, minOccurring = 1, maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, typeKind, argumentKind, null, null, false, null, 1, 1, null, null);
    }

    public Parameter(String name, ParameterTypeKind typeKind) // PORT: §3.12 argumentKind = ArgumentKind.Expression, values = null, examples = null, isCaseSensitive = false, defaultValueIndicator = null, minOccurring = 1, maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, typeKind, ArgumentKind.Expression, null, null, false, null, 1, 1, null, null);
    }

    public Parameter(String name, ParameterTypeKind typeKind, P... named) // PORT: §3.12 named arguments (D16)
    {
        this(name, typeKind, P.resolve(2, ArgumentKind.Expression, null, named));
    }

    public Parameter(String name, ParameterTypeKind typeKind, ArgumentKind argumentKind, P... named) // PORT: §3.12 named arguments (D16)
    {
        this(name, typeKind, P.resolve(3, argumentKind, null, named));
    }

    public Parameter(String name, ParameterTypeKind typeKind, ArgumentKind argumentKind, List<?> values, P... named) // PORT: §3.12 named arguments (D16)
    {
        this(name, typeKind, P.resolve(4, argumentKind, values, named));
    }

    private Parameter(String name, ParameterTypeKind typeKind, P.Args a) // PORT: §3.12 named arguments (D16)
    {
        this(name, typeKind, a.argumentKind, a.values, a.examples, a.isCaseSensitive, a.defaultValueIndicator, a.minOccurring, a.maxOccurring, a.defaultValue, a.description);
    }

    public Parameter(
            String name,
            TypeSymbol type,
            ArgumentKind argumentKind,
            List<?> values,
            List<String> examples,
            boolean isCaseSensitive,
            String defaultValueIndicator,
            int minOccurring,
            int maxOccurring,
            Expression defaultValue,
            String description)
    {
        this(
                name, ParameterTypeKind.Declared, new TypeSymbol[] { type != null ? type : ScalarTypes.Unknown },
                argumentKind,
                values,
                examples,
                isCaseSensitive,
                defaultValueIndicator,
                minOccurring,
                maxOccurring,
                defaultValue,
                description); // PORT: §3.14 type ?? ScalarTypes.Unknown
    }

    public Parameter(String name, TypeSymbol type, ArgumentKind argumentKind, List<?> values, List<String> examples, boolean isCaseSensitive, String defaultValueIndicator, int minOccurring, int maxOccurring, Expression defaultValue) // PORT: §3.12 description = null
    {
        this(name, type, argumentKind, values, examples, isCaseSensitive, defaultValueIndicator, minOccurring, maxOccurring, defaultValue, null);
    }

    public Parameter(String name, TypeSymbol type, ArgumentKind argumentKind, List<?> values, List<String> examples, boolean isCaseSensitive, String defaultValueIndicator, int minOccurring, int maxOccurring) // PORT: §3.12 defaultValue = null, description = null
    {
        this(name, type, argumentKind, values, examples, isCaseSensitive, defaultValueIndicator, minOccurring, maxOccurring, null, null);
    }

    public Parameter(String name, TypeSymbol type, ArgumentKind argumentKind, List<?> values, List<String> examples, boolean isCaseSensitive, String defaultValueIndicator, int minOccurring) // PORT: §3.12 maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, type, argumentKind, values, examples, isCaseSensitive, defaultValueIndicator, minOccurring, 1, null, null);
    }

    public Parameter(String name, TypeSymbol type, ArgumentKind argumentKind, List<?> values, List<String> examples, boolean isCaseSensitive, String defaultValueIndicator) // PORT: §3.12 minOccurring = 1, maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, type, argumentKind, values, examples, isCaseSensitive, defaultValueIndicator, 1, 1, null, null);
    }

    public Parameter(String name, TypeSymbol type, ArgumentKind argumentKind, List<?> values, List<String> examples, boolean isCaseSensitive) // PORT: §3.12 defaultValueIndicator = null, minOccurring = 1, maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, type, argumentKind, values, examples, isCaseSensitive, null, 1, 1, null, null);
    }

    public Parameter(String name, TypeSymbol type, ArgumentKind argumentKind, List<?> values, List<String> examples) // PORT: §3.12 isCaseSensitive = false, defaultValueIndicator = null, minOccurring = 1, maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, type, argumentKind, values, examples, false, null, 1, 1, null, null);
    }

    public Parameter(String name, TypeSymbol type, ArgumentKind argumentKind, List<?> values) // PORT: §3.12 examples = null, isCaseSensitive = false, defaultValueIndicator = null, minOccurring = 1, maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, type, argumentKind, values, null, false, null, 1, 1, null, null);
    }

    public Parameter(String name, TypeSymbol type, ArgumentKind argumentKind) // PORT: §3.12 values = null, examples = null, isCaseSensitive = false, defaultValueIndicator = null, minOccurring = 1, maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, type, argumentKind, null, null, false, null, 1, 1, null, null);
    }

    public Parameter(String name, TypeSymbol type, P... named) // PORT: §3.12 named arguments (D16)
    {
        this(name, type, P.resolve(2, ArgumentKind.Expression, null, named));
    }

    public Parameter(String name, TypeSymbol type, ArgumentKind argumentKind, P... named) // PORT: §3.12 named arguments (D16)
    {
        this(name, type, P.resolve(3, argumentKind, null, named));
    }

    public Parameter(String name, TypeSymbol type, ArgumentKind argumentKind, List<?> values, P... named) // PORT: §3.12 named arguments (D16)
    {
        this(name, type, P.resolve(4, argumentKind, values, named));
    }

    private Parameter(String name, TypeSymbol type, P.Args a) // PORT: §3.12 named arguments (D16)
    {
        this(name, type, a.argumentKind, a.values, a.examples, a.isCaseSensitive, a.defaultValueIndicator, a.minOccurring, a.maxOccurring, a.defaultValue, a.description);
    }

    public Parameter(
            String name,
            TypeSymbol[] types,
            ArgumentKind argumentKind,
            List<?> values,
            List<String> examples,
            boolean isCaseSensitive,
            String defaultValueIndicator,
            int minOccurring,
            int maxOccurring,
            Expression defaultValue,
            String description)
    {
        this(
                name, ParameterTypeKind.Declared, types,
                argumentKind,
                values,
                examples,
                isCaseSensitive,
                defaultValueIndicator,
                minOccurring,
                maxOccurring,
                defaultValue,
                description);
    }

    public Parameter(String name, TypeSymbol[] types, ArgumentKind argumentKind, List<?> values, List<String> examples, boolean isCaseSensitive, String defaultValueIndicator, int minOccurring, int maxOccurring, Expression defaultValue) // PORT: §3.12 description = null
    {
        this(name, types, argumentKind, values, examples, isCaseSensitive, defaultValueIndicator, minOccurring, maxOccurring, defaultValue, null);
    }

    public Parameter(String name, TypeSymbol[] types, ArgumentKind argumentKind, List<?> values, List<String> examples, boolean isCaseSensitive, String defaultValueIndicator, int minOccurring, int maxOccurring) // PORT: §3.12 defaultValue = null, description = null
    {
        this(name, types, argumentKind, values, examples, isCaseSensitive, defaultValueIndicator, minOccurring, maxOccurring, null, null);
    }

    public Parameter(String name, TypeSymbol[] types, ArgumentKind argumentKind, List<?> values, List<String> examples, boolean isCaseSensitive, String defaultValueIndicator, int minOccurring) // PORT: §3.12 maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, types, argumentKind, values, examples, isCaseSensitive, defaultValueIndicator, minOccurring, 1, null, null);
    }

    public Parameter(String name, TypeSymbol[] types, ArgumentKind argumentKind, List<?> values, List<String> examples, boolean isCaseSensitive, String defaultValueIndicator) // PORT: §3.12 minOccurring = 1, maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, types, argumentKind, values, examples, isCaseSensitive, defaultValueIndicator, 1, 1, null, null);
    }

    public Parameter(String name, TypeSymbol[] types, ArgumentKind argumentKind, List<?> values, List<String> examples, boolean isCaseSensitive) // PORT: §3.12 defaultValueIndicator = null, minOccurring = 1, maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, types, argumentKind, values, examples, isCaseSensitive, null, 1, 1, null, null);
    }

    public Parameter(String name, TypeSymbol[] types, ArgumentKind argumentKind, List<?> values, List<String> examples) // PORT: §3.12 isCaseSensitive = false, defaultValueIndicator = null, minOccurring = 1, maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, types, argumentKind, values, examples, false, null, 1, 1, null, null);
    }

    public Parameter(String name, TypeSymbol[] types, ArgumentKind argumentKind, List<?> values) // PORT: §3.12 examples = null, isCaseSensitive = false, defaultValueIndicator = null, minOccurring = 1, maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, types, argumentKind, values, null, false, null, 1, 1, null, null);
    }

    public Parameter(String name, TypeSymbol[] types, ArgumentKind argumentKind) // PORT: §3.12 values = null, examples = null, isCaseSensitive = false, defaultValueIndicator = null, minOccurring = 1, maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, types, argumentKind, null, null, false, null, 1, 1, null, null);
    }

    public Parameter(String name, TypeSymbol[] types) // PORT: §3.12 argumentKind = ArgumentKind.Expression, values = null, examples = null, isCaseSensitive = false, defaultValueIndicator = null, minOccurring = 1, maxOccurring = 1, defaultValue = null, description = null
    {
        this(name, types, ArgumentKind.Expression, null, null, false, null, 1, 1, null, null);
    }

    public Parameter(String name, TypeSymbol[] types, P... named) // PORT: §3.12 named arguments (D16)
    {
        this(name, types, P.resolve(2, ArgumentKind.Expression, null, named));
    }

    public Parameter(String name, TypeSymbol[] types, ArgumentKind argumentKind, P... named) // PORT: §3.12 named arguments (D16)
    {
        this(name, types, P.resolve(3, argumentKind, null, named));
    }

    public Parameter(String name, TypeSymbol[] types, ArgumentKind argumentKind, List<?> values, P... named) // PORT: §3.12 named arguments (D16)
    {
        this(name, types, P.resolve(4, argumentKind, values, named));
    }

    private Parameter(String name, TypeSymbol[] types, P.Args a) // PORT: §3.12 named arguments (D16)
    {
        this(name, types, a.argumentKind, a.values, a.examples, a.isCaseSensitive, a.defaultValueIndicator, a.minOccurring, a.maxOccurring, a.defaultValue, a.description);
    }

    public Parameter(String name, TypeSymbol type)
    {
        this(name, ParameterTypeKind.Declared, new TypeSymbol[] { type != null ? type : ScalarTypes.Unknown }, ArgumentKind.Expression, null, null, false, null, 1, 1, null, null); // PORT: §3.14 type ?? ScalarTypes.Unknown
    }

    /// <summary>
    /// Convert a <see cref="ParameterSymbol"/> into a <see cref="Parameter"/> instance.
    /// </summary>
    public static Parameter from(ParameterSymbol parameter, boolean isOptional, Expression defaultValue)
    {
        return new Parameter(parameter.name(), parameter.type(), ArgumentKind.Expression, null, null, false, null, isOptional ? 0 : 1, 1, defaultValue, parameter.description()); // PORT: §3.12 named arguments → positional with upstream defaults
    }

    public static Parameter from(ParameterSymbol parameter, boolean isOptional) // PORT: §3.12 defaultValue = null
    {
        return from(parameter, isOptional, null);
    }

    public static Parameter from(ParameterSymbol parameter) // PORT: §3.12 isOptional = false, defaultValue = null
    {
        return from(parameter, false, null);
    }

    /// <summary>
    /// Convert a parsed <see cref="FunctionParameter"/> syntax into a <see cref="Parameter"/>.
    /// </summary>
    public static Parameter from(FunctionParameter declaration)
    {
        var name = declaration.nameAndType().name().simpleName();
        var type = Binder.getDeclaredType(declaration.nameAndType().type());
        var defvalue = declaration.defaultValue() != null ? declaration.defaultValue().value() : null; // PORT: §3.14 ?. and ?? null
        return new Parameter(name, type, ArgumentKind.Expression, null, null, false, null, 1, 1, defvalue, null); // PORT: §3.12 named argument → positional with upstream defaults
    }

    /// <summary>
    /// Convert parsed <see cref="FunctionParameters"/> syntax into a list of <see cref="Parameter"/>
    /// </summary>
    public static List<Parameter> from(FunctionParameters declaration)
    {
        var list = new ArrayList<Parameter>();

        for (SeparatedElement1<FunctionParameter> pelem : declaration.parameters())
        {
            list.add(from(pelem.element()));
        }

        return list;
    }

    /// <summary>
    /// Parse the parameter list of a function declaration
    /// </summary>
    public static List<Parameter> parseList(String parameters)
    {
        var fp = QueryParser.parseFunctionParameters(parameters);
        return (fp != null)
            ? from(fp)
            : EmptyReadOnlyList.instance(); // PORT: §3.9
    }

    /// <summary>
    /// Gets the text of the parameter as it would appear in a function declaration.
    /// </summary>
    public static String getDeclaration(Parameter parameter)
    {
        var name = KustoFacts.bracketNameIfNecessary(parameter.name());

        if (parameter.typeKind() == ParameterTypeKind.Declared && parameter.declaredTypes().size() == 1)
        {
            var type = parameter.declaredTypes().get(0);
            if (type instanceof TableSymbol table)
            {
                return DotNet.str(name) + ": " + DotNet.str(getTableSchemaDeclaration(table)); // PORT: §3.14 interpolation
            }
            else if (parameter.defaultValue() != null)
            {
                return DotNet.str(name) + ": " + DotNet.str(parameter.declaredTypes().get(0).name()) + " = " + DotNet.str(parameter.defaultValue().toString(IncludeTrivia.Interior)); // PORT: §3.14 interpolation
            }
            else
            {
                return DotNet.str(KustoFacts.bracketNameIfNecessary(parameter.name())) + ": " + DotNet.str(parameter.declaredTypes().get(0).name()); // PORT: §3.14 interpolation
            }
        }
        else if (parameter.typeKind() == ParameterTypeKind.Tabular)
        {
            return DotNet.str(name) + ": (*)"; // PORT: §3.14 interpolation
        }
        else
        {
            // not sure what to do here if this is called for other kinds of parameters
            return DotNet.str(name) + ": dynamic"; // PORT: §3.14 interpolation
        }
    }

    /// <summary>
    /// Gets the text of a parameter list of a function declaration for the specified parameters.
    /// </summary>
    public static String getParameterListDeclaration(List<Parameter> parameters)
    {
        return "(" + DotNetStrings.join(", ", Linq.select(parameters, p -> getDeclaration(p))) + ")"; // PORT: §3.6; §3.14
    }

    /// <summary>
    /// Gets the text of a table schema as it would appear as a type of a parameter in a function declaration.
    /// </summary>
    private static String getTableSchemaDeclaration(TableSymbol table)
    {
        if (table.columns().size() == 0)
        {
            return "(*)";
        }
        else
        {
            return "(" + DotNetStrings.join(", ", Linq.select(table.columns(), c -> getColumnDeclaration(c))) + ")"; // PORT: §3.6; §3.14
        }
    }

    /// <summary>
    /// Gets the text of a column as it would appear in the table type of a parameter of a function declaration.
    /// </summary>
    private static String getColumnDeclaration(ColumnSymbol column)
    {
        return DotNet.str(KustoFacts.bracketNameIfNecessary(column.name())) + ": " + DotNet.str(column.type().name()); // PORT: §3.14 interpolation
    }

    /// <summary>
    /// True if the type of the parameter is dependant on arguments.
    /// </summary>
    public boolean typeDependsOnArguments()
    {
        return this.typeKind() != ParameterTypeKind.Declared || this.declaredTypes().get(0).isTabular();
    }

    public Tabularity tabularity()
    {
        switch (this.typeKind())
        {
            case Declared:
                return this.declaredTypes().get(0).tabularity();
            case Tabular:
            case Database:
            case Cluster:
                return Tabularity.Tabular;
            default:
                return Tabularity.Scalar;
        }
    }

    public boolean isScalar() { return this.tabularity() == Tabularity.Scalar; }

    public boolean isTabular() { return this.tabularity() == Tabularity.Tabular; }
}
