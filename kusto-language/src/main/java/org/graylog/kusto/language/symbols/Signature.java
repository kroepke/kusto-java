// Ported from: src/Kusto.Language/Symbols/Signature.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.symbols;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import org.graylog.kusto.language.GlobalState;
import org.graylog.kusto.language.KustoCode;
import org.graylog.kusto.language.KustoFacts;
import org.graylog.kusto.language.binding.Binder;
import org.graylog.kusto.language.syntax.CommandBlock;
import org.graylog.kusto.language.syntax.Expression;
import org.graylog.kusto.language.syntax.FunctionBody;
import org.graylog.kusto.language.syntax.IncludeTrivia;
import org.graylog.kusto.language.syntax.MacroExpandOperator;
import org.graylog.kusto.language.syntax.QueryBlock;
import org.graylog.kusto.language.syntax.SeparatedElement1;
import org.graylog.kusto.language.syntax.Statement;
import org.graylog.kusto.language.syntax.SyntaxList1;
import org.graylog.kusto.language.utils.ArgumentCheckers;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.ObjectPool;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// The parameter constraints and return type rules of a function or operator symbol.
/// </summary>
public class Signature
{
    @SuppressWarnings("unused")
    private String debugText() // PORT: §3.20 [DebuggerDisplay] target kept as a private method
    {
        return DebugDisplay.getText(this, /*includeSymbolName:*/ true); // PORT: §3.12
    }

    /// <summary>
    /// The symbol this is a signature for.
    /// </summary>
    private Symbol symbol;
    public Symbol symbol() { return this.symbol; }
    @Internal
    public void setSymbol(Symbol symbol) { this.symbol = symbol; } // PORT: §2.6 internal setter

    /// <summary>
    /// The approach by which the return type is determined.
    /// </summary>
    private final ReturnTypeKind returnKind;
    public ReturnTypeKind returnKind() { return this.returnKind; }

    /// <summary>
    /// The declared parameters.
    /// </summary>
    private final List<Parameter> parameters;
    public List<Parameter> parameters() { return this.parameters; }

    /// <summary>
    /// The minimum number of arguments an invocation of this signature can have.
    /// </summary>
    private final int minArgumentCount;
    public int minArgumentCount() { return this.minArgumentCount; }

    /// <summary>
    /// The maximum number of arguments an invocation of this signature can have.
    /// </summary>
    private final int maxArgumentCount;
    public int maxArgumentCount() { return this.maxArgumentCount; }

    /// <summary>
    /// The declaration of the body of the function in source.
    /// </summary>
    private final FunctionBody declaration;
    public FunctionBody declaration() { return this.declaration; }

    /// <summary>
    /// A custom function that evaluates the return type of this signature.
    /// </summary>
    private final CustomReturnType customReturnType;
    public CustomReturnType customReturnType() { return this.customReturnType; }

    /// <summary>
    /// The rule that describes how parameters are associated with arguments
    /// </summary>
    private final ParameterLayout layout;
    public ParameterLayout layout() { return this.layout; }

    /// <summary>
    /// If true, this signature is hidden from intellisense
    /// </summary>
    private final boolean isHidden;
    public boolean isHidden() { return this.isHidden; }

    /// <summary>
    /// The name of an alternative function to use instead of this function/signature
    /// if this function is obsolete/deprecated.
    /// </summary>
    private final String alternative;
    public String alternative() { return this.alternative; }

    /// <summary>
    /// True if this signature is considered obsolete/deprecated.
    /// </summary>
    public boolean isObsolete() { return alternative() != null; }

    /// <summary>
    /// If true, this signature can have a variable number of arguments.
    /// </summary>
    private boolean hasRepeatableParameters; // PORT: §3.1 get-only auto-property assigned in a loop; not final
    public boolean hasRepeatableParameters() { return this.hasRepeatableParameters; }

    /// <summary>
    /// If true, this signature has optional parameters.
    /// </summary>
    private boolean hasOptionalParameters; // PORT: §3.1 get-only auto-property assigned in a loop; not final
    public boolean hasOptionalParameters() { return this.hasOptionalParameters; }

    /// <summary>
    /// If true, this signature has parameters that are expected to be aggregates.
    /// </summary>
    private boolean hasAggregateParameters; // PORT: §3.1 get-only auto-property assigned in a loop; not final
    public boolean hasAggregateParameters() { return this.hasAggregateParameters; }

    private final TypeSymbol _returnType;
    private String _body;
    private Tabularity _tabularity;

    private Signature(
        ReturnTypeKind returnKind,
        TypeSymbol returnType,
        String body,
        FunctionBody declaration,
        CustomReturnType customReturnType,
        Tabularity tabularity,
        List<Parameter> parameters,
        ParameterLayout layout,
        boolean isHidden,
        String alternative)
    {
        if (returnKind == ReturnTypeKind.Declared && returnType == null)
            throw new NullPointerException("returnType"); // PORT: §3.16 ArgumentNullException

        if (returnKind == ReturnTypeKind.Computed && !(body != null | declaration != null))
            throw new NullPointerException("body"); // PORT: §3.16 ArgumentNullException

        if (returnKind == ReturnTypeKind.Custom && customReturnType == null)
            throw new NullPointerException("customReturnType"); // PORT: §3.16 ArgumentNullException

        this.returnKind = returnKind;
        this._returnType = returnType;
        this._body = body;
        this.declaration = declaration;
        this.customReturnType = customReturnType;
        this._tabularity = tabularity;
        this.parameters = ArgumentCheckers.checkArgumentNullOrElementNull(ListExtensions.toReadOnly(parameters), "parameters"); // PORT: §3.5
        this.isHidden = isHidden;
        this.alternative = alternative;

        if (returnKind == ReturnTypeKind.Computed
            && returnType != null
            && tabularity == Tabularity.Unspecified)
        {
            this._tabularity = returnType.tabularity();
        }

        int minArgumentCount = 0;
        int maxArgumentCount = 0;

        for (var p : this.parameters())
        {
            if (p.isRepeatable())
            {
                this.hasRepeatableParameters = true;
            }

            if (p.isOptional())
            {
                this.hasOptionalParameters = true;
            }

            if (p.argumentKind() == ArgumentKind.Aggregate)
            {
                this.hasAggregateParameters = true;
            }

            minArgumentCount += p.minOccurring();
            maxArgumentCount += p.maxOccurring();
        }

        this.minArgumentCount = minArgumentCount;
        this.maxArgumentCount = maxArgumentCount;

        if (layout != null)
        {
            this.layout = layout;
        }
        else if (this.hasRepeatableParameters())
        {
            this.layout = ParameterLayouts.Repeating;
        }
        else
        {
            this.layout = ParameterLayouts.Fixed;
        }
    }

    private Signature( // PORT: §3.12 layout = null, isHidden = false, alternative = null
        ReturnTypeKind returnKind,
        TypeSymbol returnType,
        String body,
        FunctionBody declaration,
        CustomReturnType customReturnType,
        Tabularity tabularity,
        List<Parameter> parameters)
    {
        this(returnKind, returnType, body, declaration, customReturnType, tabularity, parameters, null, false, null);
    }

    // PORT: §3.10 params Parameter[] → Parameter...; a null array reaches ToReadOnly as null (→ empty), as upstream.
    private static List<Parameter> asList(Parameter[] parameters)
    {
        return parameters != null ? Arrays.asList(parameters) : null;
    }

    public Signature(ReturnTypeKind returnKind, List<Parameter> parameters)
    {
        this(returnKind, null, null, null, null, Tabularity.Unspecified, parameters);
    }

    public Signature(ReturnTypeKind returnKind, Parameter... parameters)
    {
        this(returnKind, null, null, null, null, Tabularity.Unspecified, asList(parameters)); // PORT: §3.10
    }

    public Signature(TypeSymbol returnType, List<Parameter> parameters)
    {
        this(ReturnTypeKind.Declared, returnType, null, null, null, Tabularity.Unspecified, parameters);
        if (returnType == null)
            throw new NullPointerException("returnType"); // PORT: §3.16 ArgumentNullException
    }

    public Signature(TypeSymbol returnType, Parameter... parameters)
    {
        this(ReturnTypeKind.Declared, returnType, null, null, null, Tabularity.Unspecified, asList(parameters)); // PORT: §3.10
        if (returnType == null)
            throw new NullPointerException("returnType"); // PORT: §3.16 ArgumentNullException
    }

    public Signature(CustomReturnType customReturnType, Tabularity tabularity, List<Parameter> parameters)
    {
        this(ReturnTypeKind.Custom, null, null, null, customReturnType, tabularity, parameters);
        if (customReturnType == null)
            throw new NullPointerException("customReturnType"); // PORT: §3.16 ArgumentNullException
    }

    public Signature(CustomReturnType customReturnType, Tabularity tabularity, Parameter... parameters)
    {
        this(ReturnTypeKind.Custom, null, null, null, customReturnType, tabularity, asList(parameters)); // PORT: §3.10
        if (customReturnType == null)
            throw new NullPointerException("customReturnType"); // PORT: §3.16 ArgumentNullException
    }

    public Signature(String body, Tabularity tabularity, List<Parameter> parameters)
    {
        this(ReturnTypeKind.Computed, null, body, null, null, tabularity, parameters);
    }

    public Signature(String body, Tabularity tabularity, Parameter... parameters)
    {
        this(ReturnTypeKind.Computed, null, body, null, null, tabularity, asList(parameters)); // PORT: §3.10
    }

    public Signature(FunctionBody declaration, List<Parameter> parameters)
    {
        this(ReturnTypeKind.Computed, getDeclarationResultType(declaration), null, declaration, null, Tabularity.Unspecified, parameters); // PORT: §3.11
    }

    public Signature(FunctionBody declaration, Parameter... parameters)
    {
        this(ReturnTypeKind.Computed, getDeclarationResultType(declaration), null, declaration, null, Tabularity.Unspecified, asList(parameters)); // PORT: §3.11; §3.10
    }

    // PORT: §3.11 computed base argument `declaration.Expression?.ResultType as TypeSymbol`
    private static TypeSymbol getDeclarationResultType(FunctionBody declaration)
    {
        var expression = declaration.expression();
        return expression != null ? expression.resultType() : null;
    }

    /// <summary>
    /// Creates a new <see cref="Signature"/> just like this one, but with a custom function that
    /// builds a list of parameter associated with each argument.
    /// </summary>
    public Signature withLayout(ParameterLayoutBuilder customBuilder)
    {
        return withLayout(ParameterLayouts.custom(customBuilder));
    }

    /// <summary>
    /// Creates a new <see cref="Signature"/> just like this one, but with a custom function that
    /// builds a list of parameter associated with each argument.
    /// </summary>
    public Signature withLayout(ParameterLayout layout)
    {
        return new Signature(this.returnKind(), this._returnType, this._body, this.declaration(), this.customReturnType(), this._tabularity, this.parameters(), layout, this.isHidden(), this.alternative());
    }

    /// <summary>
    /// Creates a <see cref="Signature"/> just like this one, but with IsHidden property changed.
    /// </summary>
    public Signature withIsHidden(boolean isHidden)
    {
        return new Signature(this.returnKind(), this._returnType, this._body, this.declaration(), this.customReturnType(), this._tabularity, this.parameters(), this.layout(), isHidden, this.alternative());
    }

    /// <summary>
    /// Creates a <see cref="Signature"/> just like this one, but is hidden from intellisense.
    /// </summary>
    public Signature hide()
    {
        return withIsHidden(true);
    }

    /// <summary>
    /// Creates a <see cref="Signature"/> just like this one, but with Alternative property changed.
    /// </summary>
    public Signature withAlternative(String alternative)
    {
        return new Signature(this.returnKind(), this._returnType, this._body, this.declaration(), this.customReturnType(), this._tabularity, this.parameters(), this.layout(), this.isHidden(), alternative);
    }

    /// <summary>
    /// Creates a <see cref="Signature"/> just like this one, but with Alternative property set.
    /// </summary>
    public Signature obsolete(String alternative)
    {
        return withAlternative(alternative);
    }

    /// <summary>
    /// The body of the function, if declared within the query or database.
    /// </summary>
    public String body()
    {
        if (this._body == null && this.declaration() != null)
        {
            this._body = this.declaration().toString(IncludeTrivia.Interior);
        }

        return this._body;
    }

    /// <summary>
    /// The return type if specified as part of the signature.
    /// </summary>
    public TypeSymbol declaredReturnType()
    {
        return this.returnKind() == ReturnTypeKind.Declared ? this._returnType : null;
    }

    private LinkedHashMap<String, Parameter> nameToParameterMap;

    /// <summary>
    /// Gets the parameter given the parameter name.
    /// </summary>
    public Parameter getParameter(String name)
    {
        if (this.nameToParameterMap == null)
        {
            var map = new LinkedHashMap<String, Parameter>(this.parameters().size()); // PORT: §3.17

            for (var p : this.parameters())
            {
                map.put(p.name(), p);
            }

            this.nameToParameterMap = map;
        }

        var parameter = this.nameToParameterMap.get(name); // PORT: §3.3 TryGetValue; values are never null
        return parameter;
    }

    /// <summary>
    /// A parameter used to associate with an argument when no declared parameter matches.
    /// </summary>
    public static final Parameter UnknownParameter = new Parameter("", ScalarTypes.Unknown);

    /// <summary>
    /// True if the function allows named arguments
    /// </summary>
    public boolean allowsNamedArguments()
    {
        return !(this.symbol() instanceof FunctionSymbol fn && GlobalState.default_().isBuiltInFunction(fn)); // PORT: §2.3 Default → default_(); §3.9 lazy singleton
    }

    /// <summary>
    /// Builds a list of parameters as associated with the specified arguments.
    /// </summary>
    public List<Parameter> getArgumentParameters(List<Expression> arguments)
    {
        if (arguments == null)
            throw new NullPointerException("arguments"); // PORT: §3.16 ArgumentNullException

        var argumentParameters = new ArrayList<Parameter>();
        getArgumentParameters(arguments, argumentParameters);
        return argumentParameters;
    }

    /// <summary>
    /// Builds a list of parameters associated with the specified arguments.
    /// </summary>
    public void getArgumentParameters(List<Expression> arguments, List<Parameter> argumentParameters)
    {
        if (arguments == null)
            throw new NullPointerException("arguments"); // PORT: §3.16 ArgumentNullException

        if (argumentParameters == null)
            throw new NullPointerException("argumentParameters"); // PORT: §3.16 ArgumentNullException

        this.layout().getArgumentParameters(this, arguments, argumentParameters);
    }

    /// <summary>
    /// Builds a list of parameters associated with the specified argument types.
    /// </summary>
    public void getArgumentParametersForTypes(List<TypeSymbol> argumentTypes, List<Parameter> argumentParameters) // PORT: §2.5 GetArgumentParameters(IReadOnlyList<TypeSymbol>, …)
    {
        if (argumentTypes == null)
            throw new NullPointerException("argumentTypes"); // PORT: §3.16 ArgumentNullException

        if (argumentParameters == null)
            throw new NullPointerException("argumentParameters"); // PORT: §3.16 ArgumentNullException

        this.layout().getArgumentParametersForTypes(this, argumentTypes, argumentParameters); // PORT: §2.5
    }

    /// <summary>
    /// Gets the set of possible parameters that could occur after the specified arguments
    /// </summary>
    public void getNextPossibleParameters(List<Expression> arguments, List<Parameter> possibleParameters)
    {
        if (arguments == null)
            throw new NullPointerException("arguments"); // PORT: §3.16 ArgumentNullException

        if (possibleParameters == null)
            throw new NullPointerException("possibleParameters"); // PORT: §3.16 ArgumentNullException

        this.layout().getNextPossibleParameters(this, arguments, possibleParameters);
    }

    /// <summary>
    /// True if the argument count represents a valid number of arguments.
    /// </summary>
    public boolean isValidArgumentCount(int argumentCount)
    {
        return this.layout().isValidArgumentCount(this, argumentCount);
    }

    public Tabularity tabularity()
    {
        if (this._tabularity == Tabularity.Unspecified)
        {
            switch (this.returnKind())
            {
                case Declared:
                    return this._returnType.tabularity();
                case Computed:
                    return Tabularity.Unknown;
                case Parameter0:
                    return this.parameters().size() > 0 ? this.parameters().get(0).tabularity() : Tabularity.Unknown;
                case Parameter1:
                    return this.parameters().size() > 1 ? this.parameters().get(1).tabularity() : Tabularity.Unknown;
                case Parameter2:
                    return this.parameters().size() > 2 ? this.parameters().get(2).tabularity() : Tabularity.Unknown;
                case ParameterN:
                    return this.parameters().size() > 0 ? this.parameters().get(this.parameters().size() - 1).tabularity() : Tabularity.Unknown;
                case Custom:
                    return Tabularity.Unknown;
                case Parameter0Table:
                case Parameter0ExternalTable:
                case Parameter0MaterializedView:
                case Parameter0Database:
                case Parameter0Cluster:
                case Parameter0EntityGroup:
                case Parameter0StoredQueryResult:
                    return Tabularity.Tabular;
                case Parameter0Graph:
                    return Tabularity.Other;
                default:
                    return Tabularity.Scalar;
            }
        }

        return this._tabularity;
    }

    public boolean isScalar()
    {
        switch (this.tabularity())
        {
            case Scalar:
            case Unknown:
                return true;
            default:
                return false;
        }
    }

    public boolean isTabular()
    {
        switch (this.tabularity())
        {
            case Tabular:
            case Unknown:
                return true;
            default:
                return false;
        }
    }

    /// <summary>
    /// Gets the return type for the function as best as can be determined without specific call site arguments.
    /// </summary>
    /// <param name="globals">The <see cref="GlobalState"/> in context for any computations made in determining the return type.</param>
    public TypeSymbol getReturnType(GlobalState globals)
    {
        if (globals == null)
            throw new NullPointerException("globals"); // PORT: §3.16 ArgumentNullException

        switch (this.returnKind())
        {
            case Declared:
                return this.declaredReturnType();

            case Computed:
                return Binder.getComputedReturnType(this, globals);

            case Parameter0Cluster:
                return new ClusterSymbol("", (Iterable<DatabaseSymbol>) null, /*isOpen:*/ true); // PORT: §3.12 named argument; cast resolves the null overload as C# does

            case Parameter0Database:
                return new DatabaseSymbol("", (Iterable<Symbol>) null, /*isOpen:*/ true); // PORT: §3.12 named argument; cast resolves the null overload as C# does

            case Parameter0Table:
                return TableSymbol.Empty.withIsOpen(true);

            case Parameter0ExternalTable:
                return TableSymbol.Empty.withIsOpen(true);

            case Parameter0MaterializedView:
                return TableSymbol.Empty.withIsOpen(true);

            case Parameter0EntityGroup:
                return new EntityGroupSymbol();

            case Parameter0StoredQueryResult:
                return StoredQueryResultSymbol.Empty; // the inherited static TableSymbol.Empty, as in C#

            default:
                return this.tabularity() == Tabularity.Tabular
                    ? TableSymbol.Empty.withIsOpen(true)
                    : (TypeSymbol) ScalarTypes.Unknown;
        }
    }

    private static final ObjectPool<List<Parameter>> s_parameterListPool =
        new ObjectPool<List<Parameter>>(() -> new ArrayList<Parameter>(), list -> list.clear());

    /// <summary>
    /// Gets the return type for the function as best as can be determined with a set of hypothetical argument types.
    /// </summary>
    /// <param name="globals">The <see cref="GlobalState"/> in context for any computations made in determining the return type.</param>
    /// <param name="argumentTypes">A list of types for hypothetical arguments.</param>
    public TypeSymbol getReturnType(GlobalState globals, List<TypeSymbol> argumentTypes)
    {
        if (globals == null)
            throw new NullPointerException("globals"); // PORT: §3.16 ArgumentNullException

        if (argumentTypes == null)
            throw new NullPointerException("argumentTypes"); // PORT: §3.16 ArgumentNullException

        var argumentParameters = s_parameterListPool.allocateFromPool();
        try
        {
            this.getArgumentParametersForTypes(argumentTypes, argumentParameters); // PORT: §2.5
            return getReturnType(globals, argumentTypes, argumentParameters);
        }
        finally
        {
            s_parameterListPool.returnToPool(argumentParameters);
        }
    }

    /// <summary>
    /// Gets the return type for the function as best as can be determined with a set of hypothetical argument types
    /// and corresponding their parameters.
    /// </summary>
    /// <param name="globals">The <see cref="GlobalState"/> in context for any computations made in determining the return type.</param>
    /// <param name="argumentTypes">A list of types for hypothetical arguments.</param>
    /// <param name="argumentParameters">A list of the parameters associated with each hypothetical argument.</param>
    public TypeSymbol getReturnType(
        GlobalState globals,
        List<TypeSymbol> argumentTypes,
        List<Parameter> argumentParameters)
    {
        if (globals == null)
            throw new NullPointerException("globals"); // PORT: §3.16 ArgumentNullException

        if (argumentParameters == null)
            throw new NullPointerException("argumentParameters"); // PORT: §3.16 ArgumentNullException

        if (argumentTypes == null)
            throw new NullPointerException("argumentTypes"); // PORT: §3.16 ArgumentNullException

        int iArg;
        switch (this.returnKind())
        {
            case Declared:
                return this.declaredReturnType();

            case Computed:
                return Binder.getComputedReturnType(this, globals, argumentTypes);

            case Parameter0:
                iArg = ListExtensions.indexOf(argumentParameters, this.parameters().get(0)); // PORT: §3.5 IndexOf extension, reference equality
                return iArg >= 0 && iArg < argumentTypes.size() ? argumentTypes.get(iArg) : ErrorSymbol.Instance;

            case Parameter1:
                iArg = ListExtensions.indexOf(argumentParameters, this.parameters().get(1)); // PORT: §3.5
                return iArg >= 0 && iArg < argumentTypes.size() ? argumentTypes.get(iArg) : ErrorSymbol.Instance;

            case Parameter2:
                iArg = ListExtensions.indexOf(argumentParameters, this.parameters().get(2)); // PORT: §3.5
                return iArg >= 0 && iArg < argumentTypes.size() ? argumentTypes.get(iArg) : ErrorSymbol.Instance;

            case ParameterN:
                iArg = ListExtensions.indexOf(argumentParameters, this.parameters().get(this.parameters().size() - 1)); // PORT: §3.5
                return iArg >= 0 && iArg < argumentTypes.size() ? argumentTypes.get(iArg) : ErrorSymbol.Instance;

            case Parameter0Promoted:
                iArg = ListExtensions.indexOf(argumentParameters, this.parameters().get(0)); // PORT: §3.5
                return iArg >= 0 && iArg < argumentTypes.size() ? TypeFacts.promoteToLong(argumentTypes.get(iArg)) : ErrorSymbol.Instance; // PORT: §3.5

            case Common:
            {
                var type = TypeFacts.getCommonArgumentType(argumentParameters, argumentTypes);
                return type != null ? type : ErrorSymbol.Instance; // PORT: §3.14 ??
            }

            case CommonNonDynamic:
            {
                var type = TypeFacts.getCommonArgumentType(argumentParameters, argumentTypes, /*defaultType:*/ null, /*ignoreDynamic:*/ true); // PORT: §3.12
                return type != null ? type : ErrorSymbol.Instance; // PORT: §3.14 ??
            }

            case Widest:
            {
                var type = TypeFacts.promoteToLong(TypeFacts.getWidestScalarType(argumentTypes)); // PORT: §3.5 extension tolerates a null receiver
                return type != null ? type : ErrorSymbol.Instance; // PORT: §3.14 ??
            }

            case Parameter0Cluster:
                return new ClusterSymbol("", (Iterable<DatabaseSymbol>) null, /*isOpen:*/ true); // PORT: §3.12 named argument; cast resolves the null overload as C# does

            case Parameter0Database:
                return new DatabaseSymbol("", (Iterable<Symbol>) null, /*isOpen:*/ true); // PORT: §3.12 named argument; cast resolves the null overload as C# does

            case Parameter0Table:
                return TableSymbol.Empty.withIsOpen(true);

            case Parameter0ExternalTable:
                return TableSymbol.Empty.withIsOpen(true);

            case Parameter0MaterializedView:
                return TableSymbol.Empty.withIsOpen(true);

            case Parameter0EntityGroup:
                return new EntityGroupSymbol();

            case Parameter0StoredQueryResult:
                return StoredQueryResultSymbol.Empty; // the inherited static TableSymbol.Empty, as in C#

            default:
                return this.tabularity() == Tabularity.Tabular
                    ? TableSymbol.Empty.withIsOpen(true)
                    : (TypeSymbol) ScalarTypes.Unknown;
        }
    }

    /// <summary>
    /// Determines the tabularity of signatures with computed return types.
    /// </summary>
    /// <param name="globals">The <see cref="GlobalState"/> in context for any computations made in determining the tabularity.</param>
    public void computeTabularity(GlobalState globals)
    {
        if (this.returnKind() == ReturnTypeKind.Computed
            && _tabularity == Tabularity.Unspecified)
        {
            // pre-assign to hopefully avoid duplicate computation work
            _tabularity = Tabularity.Unknown;

            // first try to deduce tabularity from syntax alone
            var syntaxTabularity = getSyntaxTabularity(globals);
            if (syntaxTabularity != Tabularity.Unknown)
            {
                _tabularity = syntaxTabularity;
            }
            else
            {
                // otherwise try to fully bind and base tabularity on return type
                var type = Binder.getComputedReturnType(this, globals);
                if (type != null)
                {
                    if (type.isTabular())
                    {
                        _tabularity = Tabularity.Tabular;
                    }
                    else if (type.isScalar())
                    {
                        _tabularity = Tabularity.Scalar;
                    }
                }
            }
        }
    }

    /// <summary>
    /// Determine's the tabularity of this function signature from the syntax of the body.
    /// </summary>
    private Tabularity getSyntaxTabularity(GlobalState globals)
    {
        SyntaxList1<SeparatedElement1<Statement>> statements = null;

        if (this.declaration() != null)
        {
            statements = this.declaration().statements();
        }
        else if (_body != null)
        {
            var body = DotNetStrings.trim(_body); // PORT: §5.1
            if (body.startsWith("{")) // PORT: §5.4 StringComparison.Ordinal
            {
                body = body.substring(1);
                if (body.endsWith("}")) // PORT: §5.4 StringComparison.Ordinal
                    body = body.substring(0, body.length() - 1);
            }
            var code = KustoCode.parse(body);
            statements = getFirstStatementList(code);
        }

        return KustoFacts.getSyntaxTabularity(statements, globals);
    }

    // PORT: §3.10 GetFirstDescendantOrSelf<SyntaxList<SeparatedElement<Statement>>>() is a reified generic type test;
    // the element type is erased in Java, so the test is the identity of the four node properties declared with that
    // type (QueryBlock/CommandBlock/FunctionBody .Statements, MacroExpandOperator .StatementList).
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static SyntaxList1<SeparatedElement1<Statement>> getFirstStatementList(KustoCode code)
    {
        return (SyntaxList1<SeparatedElement1<Statement>>) code.syntax().getFirstDescendantOrSelf(SyntaxList1.class, (SyntaxList1 list) -> isStatementList(list));
    }

    private static boolean isStatementList(SyntaxList1<?> list) // PORT: §3.10
    {
        var parent = list.parent();
        return (parent instanceof QueryBlock qb && qb.statements() == list)
            || (parent instanceof CommandBlock cb && cb.statements() == list)
            || (parent instanceof FunctionBody fb && fb.statements() == list)
            || (parent instanceof MacroExpandOperator me && me.statementList() == list);
    }
}
