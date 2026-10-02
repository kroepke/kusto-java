// Ported from: src/Kusto.Language/Symbols/ColumnSymbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.graylog.kusto.language.binding.Binder;
import org.graylog.kusto.language.syntax.SyntaxNode;
import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.Optional;
import org.graylog.kusto.language.utils.dotnet.Linq;

/// <summary>
/// A symbol representing a column.
/// </summary>
public final class ColumnSymbol extends Symbol
{
    /// <summary>
    /// The type of the column.
    /// </summary>
    private final TypeSymbol type;
    public TypeSymbol type() { return type; }

    /// <summary>
    /// The description of the column.
    /// </summary>
    private final String description;
    public String description() { return description; }

    /// <summary>
    /// One or more columns that this column is based on
    /// due to either an automatic rename like with join operator
    /// or unification of multiple columns like with union operator.
    /// </summary>
    private final List<ColumnSymbol> originalColumns;
    public List<ColumnSymbol> originalColumns() { return originalColumns; }

    /// <summary>
    /// The expression the column is computed from or
    /// the location where the column is first introduced.
    /// </summary>
    private final SyntaxNode source;
    public SyntaxNode source() { return source; }

    /// <summary>
    /// Example values used in intellisense completion lists.
    /// </summary>
    private final List<String> examples;
    public List<String> examples() { return examples; }

    @Override
    public SymbolKind kind() { return SymbolKind.Column; }

    public ColumnSymbol(
        String name, 
        TypeSymbol type, 
        String description, 
        List<ColumnSymbol> originalColumns,
        SyntaxNode source,
        List<String> examples)
    {
        super(name);
        this.type = (type == null || type.isError()) ? ScalarTypes.Unknown : type;
        this.description = description != null ? description : ""; // PORT: §3.14

        if (originalColumns != null && originalColumns.size() > 0)
        {
            this.originalColumns = ListExtensions.toReadOnly(getTrulyOriginalColumns(originalColumns)); // PORT: §3.5
        }
        else
        {
            this.originalColumns = EmptyReadOnlyList.instance(); // PORT: §3.9 EmptyReadOnlyList<ColumnSymbol>.Instance
        }

        this.source = source;

        this.examples = ListExtensions.toReadOnly(examples); // PORT: §3.5
    }

    public ColumnSymbol(String name, TypeSymbol type, String description, List<ColumnSymbol> originalColumns, SyntaxNode source) // PORT: §3.12 optional parameter examples = null
    {
        this(name, type, description, originalColumns, source, null);
    }

    public ColumnSymbol(String name, TypeSymbol type, String description, List<ColumnSymbol> originalColumns) // PORT: §3.12 optional parameter source = null
    {
        this(name, type, description, originalColumns, null);
    }

    public ColumnSymbol(String name, TypeSymbol type, String description) // PORT: §3.12 optional parameter originalColumns = null
    {
        this(name, type, description, null);
    }

    public ColumnSymbol(String name, TypeSymbol type) // PORT: §3.12 optional parameter description = null
    {
        this(name, type, null);
    }

    /// <summary>
    /// Gets the set of columns that do not declare other original columns.
    /// </summary>
    private static List<ColumnSymbol> getTrulyOriginalColumns(List<ColumnSymbol> columns)
    {
        if (Linq.all(columns, c -> c.originalColumns().size() == 0)) // PORT: §3.6
            return columns;

        var mostOriginal = new ArrayList<ColumnSymbol>();
        for (var col : columns)
        {
            if (col.originalColumns().size() > 0)
            {
                mostOriginal.addAll(col.originalColumns());
            }
            else
            {
                mostOriginal.add(col);
            }
        }

        return mostOriginal;
    }

    @Override
    public Tabularity tabularity() { return Tabularity.Scalar; }

    /// <summary>
    /// Create a new instance of <see cref="ColumnSymbol"/> if any of the specified values
    /// differs from current values.
    /// </summary>
    // PORT: §3.12 every optional parameter defaults to default(Optional<T>); callers pass Optional.NONE() for the ones they skip
    private ColumnSymbol with(
        Optional<String> name,
        Optional<TypeSymbol> type,
        Optional<String> description,
        Optional<List<ColumnSymbol>> originalColumns,
        Optional<SyntaxNode> source,
        Optional<List<String>> examples)
    {
        var newName = name.hasValue() ? name.value() : this.name();
        var newType = type.hasValue() ? type.value() : this.type();
        var newDesc = description.hasValue() ? description.value() : this.description();
        var newOC = originalColumns.hasValue() ? originalColumns.value() : this.originalColumns();
        var newSource = source.hasValue() ? source.value() : this.source();
        var newExamples = examples.hasValue() ? examples.value() : this.examples();

        if (!Objects.equals(newName, this.name()) // PORT: §3.14
            || newType != this.type()
            || !Objects.equals(newDesc, this.description()) // PORT: §3.14
            || newOC != this.originalColumns()
            || newSource != this.source()
            || newExamples != this.examples())
        {
            return new ColumnSymbol(newName, newType, newDesc, newOC, newSource, newExamples);
        }

        return this;
    }

    /// <summary>
    /// Returns a <see cref="ColumnSymbol"/> with the name specified.
    /// </summary>
    public ColumnSymbol withName(String name)
    {
        return with(Optional.of(name), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE()); // PORT: §3.12 named argument; §3.8 implicit Optional conversion
    }

    /// <summary>
    /// Returns a <see cref="ColumnSymbol"/> with the type specified.
    /// </summary>
    public ColumnSymbol withType(TypeSymbol type)
    {
        return with(Optional.NONE(), Optional.of(type), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE()); // PORT: §3.12 named argument; §3.8 implicit Optional conversion
    }

    /// <summary>
    /// Returns a <see cref="ColumnSymbol"/> with the specified description.
    /// </summary>
    public ColumnSymbol withDescription(String description)
    {
        return with(Optional.NONE(), Optional.NONE(), Optional.of(description), Optional.NONE(), Optional.NONE(), Optional.NONE()); // PORT: §3.12 named argument; §3.8 implicit Optional conversion
    }

    /// <summary>
    /// Returns a <see cref="ColumnSymbol"/> with the specified list of original columns.
    /// </summary>
    public ColumnSymbol withOriginalColumns(List<ColumnSymbol> originalColumns)
    {
        return with(Optional.NONE(), Optional.NONE(), Optional.NONE(), new Optional<List<ColumnSymbol>>(originalColumns), Optional.NONE(), Optional.NONE()); // PORT: §3.12 named argument
    }

    /// <summary>
    /// Returns a <see cref="ColumnSymbol"/> with the specified list of original columns.
    /// </summary>
    public ColumnSymbol withOriginalColumns(ColumnSymbol... originalColumns)
    {
        return withOriginalColumns(originalColumns != null ? Arrays.asList(originalColumns) : (List<ColumnSymbol>)null); // PORT: §3.17 array as IReadOnlyList
    }

    /// <summary>
    /// Returns a <see cref="ColumnSymbol"/> with the specified source expression.
    /// </summary>
    public ColumnSymbol withSource(SyntaxNode source)
    {
        return with(Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.of(source), Optional.NONE()); // PORT: §3.12 named argument; §3.8 implicit Optional conversion
    }

    /// <summary>
    /// Returns a <see cref="ColumnSymbol"/> with the specified examples.
    /// </summary>
    public ColumnSymbol withExamples(List<String> examples)
    {
        return with(Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), new Optional<List<String>>( examples )); // PORT: §3.12 named argument
    }

    /// <summary>
    /// Combines multiple sets of columns into a single set of columns.
    /// </summary>
    public static List<ColumnSymbol> combine(CombineKind kind, Iterable<? extends List<ColumnSymbol>> columnSets) // PORT: §3.10 covariance
    {
        var columns = new ArrayList<ColumnSymbol>();

        for (var list : columnSets)
        {
            columns.addAll(list);
        }

        switch (kind)
        {
            case UnifySameNameAndType:
                Binder.unifyColumnsWithSameNameAndType(columns);
                break;

            case UnifySameName:
                Binder.unifyColumnsWithSameName(columns);
                break;

            case UniqueNames:
                Binder.makeColumnNamesUnique(columns);
                break;
        }

        return columns;
    }

    /// <summary>
    /// Combines multiple sets of columns into a single set of columns.
    /// </summary>
    @SafeVarargs
    public static List<ColumnSymbol> combine(CombineKind kind, List<ColumnSymbol>... columnSets) // PORT: §3.10 params T[]
    {
        return combine(kind, columnSets != null ? Arrays.asList(columnSets) : (Iterable<List<ColumnSymbol>>)null); // PORT: §3.17 array as IEnumerable
    }
}
