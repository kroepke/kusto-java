// Ported from: src/Kusto.Language/FunctionBodyFacts.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language;

import java.util.List;

import org.graylog.kusto.language.symbols.Parameter;
import org.graylog.kusto.language.symbols.TypeSymbol;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.Optional;
import org.graylog.kusto.language.utils.dotnet.Linq;

/// <summary>
/// Useful facts about the function body identified during analysis.
/// </summary>
public class FunctionBodyFacts
{
    private final int _flags; // PORT: §3.17 [Flags] enum → int (D23)

    /// <summary>
    /// The return type of the function body when it is not dependent on parameters.
    /// </summary>
    private final TypeSymbol nonVariableReturnType;
    public TypeSymbol nonVariableReturnType() { return this.nonVariableReturnType; }

    /// <summary>
    /// The parameters of the function that causes the return type to be variable.
    /// </summary>
    private final List<Parameter> dependentParameters;
    public List<Parameter> dependentParameters() { return this.dependentParameters; }

    /// <summary>
    /// The names used in unqualified table calls.
    /// </summary>
    private final List<String> unqualifiedTableNames;
    public List<String> unqualifiedTableNames() { return this.unqualifiedTableNames; }

    /// <summary>
    /// True if the function body had syntax or semantic errors.
    /// </summary>
    private final boolean hasSyntaxErrors;
    public boolean hasSyntaxErrors() { return this.hasSyntaxErrors; }

    /// <summary>
    /// The default <see cref="FunctionBodyFacts"/>.
    /// </summary>
    public static final FunctionBodyFacts Default =
        new FunctionBodyFacts(Flags.None, null, false, null, null);

    private FunctionBodyFacts(
        int flags,
        TypeSymbol nonVariableReturnType,
        boolean hasSyntaxErrors,
        Iterable<Parameter> dependentParameters,
        Iterable<String> unqualifiedTableNames)
    {
        _flags = flags;
        this.nonVariableReturnType = nonVariableReturnType;
        this.hasSyntaxErrors = hasSyntaxErrors;
        this.dependentParameters = ListExtensions.toReadOnly(dependentParameters); // PORT: §3.5
        this.unqualifiedTableNames = ListExtensions.toReadOnly(unqualifiedTableNames); // PORT: §3.5
    }

    /// <summary>
    /// Returns a new <see cref="FunctionBodyFacts"/> with the specified values,
    /// if the specified values differ from the current values.
    /// </summary>
    private FunctionBodyFacts with( // PORT: §3.12 every caller names its arguments, so callers pass all five (defaults: null, Optional.NONE())
        Integer flags,
        Optional<TypeSymbol> nonVariableReturnType,
        Boolean hasSyntaxErrors,
        Optional<Iterable<Parameter>> dependentParameters,
        Optional<Iterable<String>> unqualifiedTableNames
        )
    {
        var newFlags = flags != null ? flags.intValue() : _flags; // PORT: §3.7 int? HasValue/Value
        var newNonVariableReturnType = nonVariableReturnType.hasValue() ? nonVariableReturnType.value() : this.nonVariableReturnType();
        var newHasSyntaxErrors = hasSyntaxErrors != null ? hasSyntaxErrors.booleanValue() : this.hasSyntaxErrors(); // PORT: §3.7 bool? HasValue/Value
        Iterable<Parameter> newDependentParameters = dependentParameters.hasValue() ? dependentParameters.value() : this.dependentParameters();
        Iterable<String> newUnqualifiedTableNames = unqualifiedTableNames.hasValue() ? unqualifiedTableNames.value() : this.unqualifiedTableNames();

        if (newFlags != _flags
            || newNonVariableReturnType != this.nonVariableReturnType()
            || newHasSyntaxErrors != this.hasSyntaxErrors()
            || newDependentParameters != this.dependentParameters()
            || newUnqualifiedTableNames != this.unqualifiedTableNames())
        {
            return new FunctionBodyFacts(
                newFlags,
                newNonVariableReturnType,
                newHasSyntaxErrors,
                newDependentParameters,
                newUnqualifiedTableNames
                );
        }

        return this;
    }

    /// <summary>
    /// Returns a <see cref="FunctionBodyFacts"/> with <see cref="NonVariableReturnType"/> assigned.
    /// </summary>
    public FunctionBodyFacts withNonVariableReturnType(TypeSymbol type)
    {
        return with(null, Optional.of(type), null, Optional.NONE(), Optional.NONE()); // PORT: §3.12 named argument; §3.8 implicit Optional conversion
    }

    /// <summary>
    /// Returns a <see cref="FunctionBodyFacts"/> with <see cref="HasSyntaxErrors"/> assigned.
    /// </summary>
    public FunctionBodyFacts withHasSyntaxErrors(boolean value)
    {
        return with(null, Optional.NONE(), value, Optional.NONE(), Optional.NONE()); // PORT: §3.12 named argument
    }

    /// <summary>
    /// Returns a <see cref="FunctionBodyFacts"/> with <see cref="DependentParameters"/> assigned.
    /// </summary>
    public FunctionBodyFacts withDependentParameters(Iterable<Parameter> list)
    {
        if (this.dependentParameters() == list)
        {
            return this;
        }
        else
        {
            return with(null, Optional.NONE(), null, new Optional<Iterable<Parameter>>(Linq.distinct(list)), Optional.NONE()); // PORT: §3.12 named argument; §3.6
        }
    }

    /// <summary>
    /// Returns a <see cref="FunctionBodyFacts"/> with an additional dependent parameter.
    /// </summary>
    public FunctionBodyFacts addDependentParameter(Parameter parameter)
    {
        if (!this.dependentParameters().contains(parameter))
        {
            return with(null, Optional.NONE(), null, Optional.of(ListExtensions.toSafeList(this.dependentParameters()).addItem(parameter)), Optional.NONE()); // PORT: §3.12 named argument; §3.8 implicit Optional conversion; §3.5
        }
        else
        {
            return this;
        }
    }

    /// <summary>
    /// Returns a <see cref="FunctionBodyFacts"/> with an additional dependent parameter.
    /// </summary>
    public FunctionBodyFacts addDependentParameters(Iterable<Parameter> parameters)
    {
        var adding = Linq.where(parameters, p -> !this.dependentParameters().contains(p)); // PORT: §3.6 eager; the predicate reads only immutable state
        if (Linq.any(adding))
        {
            return with(null, Optional.NONE(), null, Optional.of(ListExtensions.toSafeList(this.dependentParameters()).addItems(adding)), Optional.NONE()); // PORT: §3.12 named argument; §3.8 implicit Optional conversion; §3.5
        }
        else
        {
            return this;
        }
    }

    /// <summary>
    /// Returns a <see cref="FunctionBodyFacts"/> with <see cref="UnqualifiedTableNames"/> assigned.
    /// </summary>
    public FunctionBodyFacts withUnqualifiedTableNames(Iterable<String> names)
    {
        if (this.unqualifiedTableNames() == names)
        {
            return this;
        }
        else
        {
            return with(null, Optional.NONE(), null, Optional.NONE(), new Optional<Iterable<String>>(Linq.distinct(names))); // PORT: §3.12 named argument; §3.6
        }
    }

    /// <summary>
    /// Returns a <see cref="FunctionBodyFacts"/> with an additional unqualified table name.
    /// </summary>
    public FunctionBodyFacts addUnqualifiedTableName(String name)
    {
        if (!this.unqualifiedTableNames().contains(name))
        {
            return with(null, Optional.NONE(), null, Optional.NONE(), Optional.of(ListExtensions.toSafeList(this.unqualifiedTableNames()).addItem(name))); // PORT: §3.12 named argument; §3.8 implicit Optional conversion; §3.5
        }
        else
        {
            return this;
        }
    }

    /// <summary>
    /// True if the function's return type is dependent on argument values at call site.
    /// </summary>
    public boolean hasVariableReturnType()
    {
        return this.dependentParameters().size() > 0
            || this.hasUnqualifiedTableCall();
    }

    /// <summary>
    /// True if the function body has a call to the cluster method, or a function invoked within it does
    /// </summary>
    public boolean hasClusterCall()
    {
        return (_flags & Flags.Cluster) != 0;
    }

    /// <summary>
    /// Returns a new <see cref="FunctionBodyFacts"/> with <see cref="HasClusterCall"/> assigned.
    /// </summary>
    public FunctionBodyFacts withHasClusterCall(boolean value)
    {
        return with(value ? _flags | Flags.Cluster : _flags & ~Flags.Cluster, Optional.NONE(), null, Optional.NONE(), Optional.NONE()); // PORT: §3.12 named argument
    }

    /// <summary>
    /// True if the function body has a call to the database method, or a function invoked within it does
    /// </summary>
    public boolean hasDatabaseCall()
    {
        return (_flags & Flags.Database) != 0;
    }

    /// <summary>
    /// Returns a new <see cref="FunctionBodyFacts"/> with <see cref="HasDatabaseCall"/> assigned.
    /// </summary>
    public FunctionBodyFacts withHasDatabaseCall(boolean value)
    {
        return with(value ? _flags | Flags.Database : _flags & ~Flags.Database, Optional.NONE(), null, Optional.NONE(), Optional.NONE()); // PORT: §3.12 named argument
    }

    /// <summary>
    /// True if the function body has a call to the unqualified table method, or a function invoked within it does.
    /// </summary>
    public boolean hasUnqualifiedTableCall()
    {
        return (_flags & Flags.UnqualifiedTable) != 0;
    }

    /// <summary>
    /// Returns a new <see cref="FunctionBodyFacts"/> with <see cref="HasUnqualifiedTableCall"/> assigned.
    /// </summary>
    public FunctionBodyFacts withHasUnqualifiedTableCall(boolean value)
    {
        return with(value ? _flags | Flags.UnqualifiedTable : _flags & ~Flags.UnqualifiedTable, Optional.NONE(), null, Optional.NONE(), Optional.NONE()); // PORT: §3.12 named argument
    }

    /// <summary>
    /// True if the function body has a call to the qualified database().table method, or a function invoked within it does.
    /// </summary>
    public boolean hasQualifiedTableCall()
    {
        return (_flags & Flags.QualifiedTable) != 0;
    }

    /// <summary>
    /// Returns a new <see cref="FunctionBodyFacts"/> with <see cref="HasQualifiedTableCall"/> assigned.
    /// </summary>
    public FunctionBodyFacts withHasQualifiedTableCall(boolean value)
    {
        return with(value ? _flags | Flags.QualifiedTable : _flags & ~Flags.QualifiedTable, Optional.NONE(), null, Optional.NONE(), Optional.NONE()); // PORT: §3.12 named argument
    }

    /// <summary>
    /// True if the function body has a call to the externaltable method, or a function invoked within it does.
    /// </summary>
    public boolean hasExternalTableCall()
    {
        return (_flags & Flags.ExternalTable) != 0;
    }

    /// <summary>
    /// Returns a new <see cref="FunctionBodyFacts"/> with <see cref="HasExternalTableCall"/> assigned.
    /// </summary>
    public FunctionBodyFacts withHasExternalTableCall(boolean value)
    {
        return with(value ? _flags | Flags.ExternalTable : _flags & ~Flags.ExternalTable, Optional.NONE(), null, Optional.NONE(), Optional.NONE()); // PORT: §3.12 named argument
    }

    /// <summary>
    /// True if the function body has a call to the materializedview method, or a function invoked within it does.
    /// </summary>
    public boolean hasMaterializedViewCall()
    {
        return (_flags & Flags.MaterializedView) != 0;
    }

    /// <summary>
    /// Returns a new <see cref="FunctionBodyFacts"/> with <see cref="HasMaterializedViewCall"/> assigned.
    /// </summary>
    public FunctionBodyFacts withHasMaterializedViewCall(boolean value)
    {
        return with(value ? _flags | Flags.MaterializedView : _flags & ~Flags.MaterializedView, Optional.NONE(), null, Optional.NONE(), Optional.NONE()); // PORT: §3.12 named argument
    }

    /// <summary>
    /// True if the function body has a call to the stored_query_result() method, or a function invoked within it does.
    /// </summary>
    public boolean hasStoredQueryResultCall()
    {
        return (_flags & Flags.StoredQueryResult) != 0;
    }

    /// <summary>
    /// Returns a new <see cref="FunctionBodyFacts"/> with <see cref="HasStoredQueryResultCall"/> assigned.
    /// </summary>
    public FunctionBodyFacts withHasStoredQueryResultCall(boolean value)
    {
        return with(value ? _flags | Flags.StoredQueryResult : _flags & ~Flags.StoredQueryResult, Optional.NONE(), null, Optional.NONE(), Optional.NONE()); // PORT: §3.12 named argument
    }

    /// <summary>
    /// True if the function body has a call to the graph() method, or a function invoked within it does.
    /// </summary>
    public boolean hasGraphCall()
    {
        return (_flags & Flags.Graph) != 0;
    }

    /// <summary>
    /// Returns a new <see cref="FunctionBodyFacts"/> with <see cref="HasGraphCall"/> assigned.
    /// </summary>
    public FunctionBodyFacts withHasGraphCall(boolean value)
    {
        return with(value ? _flags | Flags.Graph : _flags & ~Flags.Graph, Optional.NONE(), null, Optional.NONE(), Optional.NONE()); // PORT: §3.12 named argument
    }

    /// <summary>
    /// True if the function body has any interesting aspects.
    /// </summary>
    public boolean isInteresting()
    {
        return _flags != Flags.None;
    }

    /// <summary>
    /// Returns a new <see cref="FunctionBodyFacts"/> with <see cref="IsInteresting"/> assigned.
    /// </summary>
    public FunctionBodyFacts withIsInteresting(boolean value)
    {
        return with(value ? _flags | Flags.None : _flags & ~Flags.None, Optional.NONE(), null, Optional.NONE(), Optional.NONE()); // PORT: §3.12 named argument; PORT-BUG: OR/AND-NOT with None (0) never changes the flags, so this always returns this
    }

    /// <summary>
    /// Returns a <see cref="FunctionBodyFacts"/> with the properties of the called function's facts combined with one.
    /// </summary>
    public FunctionBodyFacts combineCalledFunction(FunctionBodyFacts facts)
    {
        var newFlags = _flags | facts._flags;
        var newUnqualifiedTableNames =
            this.unqualifiedTableNames().size() == 0 && facts.unqualifiedTableNames().size() == 0 ? (Iterable<String>)this.unqualifiedTableNames()
                    : this.unqualifiedTableNames().size() > 0 && facts.unqualifiedTableNames().size() == 0 ? (Iterable<String>)this.unqualifiedTableNames()
                    : this.unqualifiedTableNames().size() == 0 && facts.unqualifiedTableNames().size() > 0 ? (Iterable<String>)facts.unqualifiedTableNames()
                    : Linq.distinct(Linq.concat(this.unqualifiedTableNames(), facts.unqualifiedTableNames())); // PORT: §3.6

        return with(
            newFlags, Optional.NONE(), null, Optional.NONE(), // PORT: §3.12 named arguments
            new Optional<Iterable<String>>(newUnqualifiedTableNames)
            );
    }

    // PORT: §3.17 [Flags] private enum → int constants holder (D23)
    private static final class Flags
    {
        /// <summary>
        /// The function body does not have any known special conditions.
        /// </summary>
        static final int None = 0b0000_0000; // PORT: §3.17 C# 0b_ prefix separator

        /// <summary>
        /// The function body or any of its dependencies includes a call to the cluster() function.
        /// </summary>
        static final int Cluster = 0b0000_0001;

        /// <summary>
        /// The function body or any of its dependencies includes a call to the database() function.
        /// </summary>
        static final int Database = 0b0000_0010;

        /// <summary>
        /// The function body or any of its dependencies includes an unqualified call to the table() function.
        /// </summary>
        static final int UnqualifiedTable = 0b0000_0100;

        /// <summary>
        /// The function body or any of its dependencies includes a qualified call to the table() function.
        /// </summary>
        static final int QualifiedTable = 0b0000_1000;

        /// <summary>
        /// The function body or any of its dependencies includes a call to the external_table() function.
        /// </summary>
        static final int ExternalTable = 0b0001_0000;

        /// <summary>
        /// The function body or any of its dependencies includes a call to the materialized_view() function.
        /// </summary>
        static final int MaterializedView = 0b0100_0000;

        /// <summary>
        /// The function body or any of its dependencies includes a call to the stored_query_result() function
        /// </summary>
        static final int StoredQueryResult = 0b1000_0000;

        /// <summary>
        /// The function body or any of its dependencies includes a call to the graph() function
        /// </summary>
        static final int Graph = 0b0001_0000_0000;
    }
}
