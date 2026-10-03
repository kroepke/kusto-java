// Ported from: src/Kusto.Language/Binder/ProjectionBuilder.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.binding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.DiagnosticFacts;
import org.graylog.kusto.language.symbols.ColumnSymbol;
import org.graylog.kusto.language.syntax.SyntaxNode;
import org.graylog.kusto.language.utils.UniqueNameTable;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// A class that manages building a list of columns in a projection
/// </summary>
@Internal
public class ProjectionBuilder
{
    /// <summary>
    /// The current projection list
    /// </summary>
    private final ArrayList<ColumnSymbol> _projection = new ArrayList<ColumnSymbol>();

    /// <summary>
    /// A list of columns that should not be added to the projection.
    /// </summary>
    private final LinkedHashSet<ColumnSymbol> _doNotAdd = new LinkedHashSet<ColumnSymbol>(); // PORT: §3.17 HashSet -> LinkedHashSet

    /// <summary>
    /// A map between column names in the projection and their current index.
    /// </summary>
    private final LinkedHashMap<String, Integer> _columnIndexMap = new LinkedHashMap<String, Integer>(); // PORT: §3.17 Dictionary -> LinkedHashMap

    /// <summary>
    /// Column names that were explicitly declared in the projection: Name = e
    /// </summary>
    private final LinkedHashSet<String> _declaredNames = new LinkedHashSet<String>(); // PORT: §3.17

    /// <summary>
    /// Names already in use.
    /// </summary>
    private final UniqueNameTable _uniqueNames = new UniqueNameTable();

    public ProjectionBuilder()
    {
    }

    /// <summary>
    /// Clears the <see cref="ProjectionBuilder"/> so it can be used again.
    /// </summary>
    public void clear()
    {
        _projection.clear();
        _doNotAdd.clear();
        _columnIndexMap.clear();
        _uniqueNames.clear();
        _declaredNames.clear();
    }

    /// <summary>
    /// Gets the projected columns.
    /// </summary>
    public List<ColumnSymbol> getProjection()
    {
        return _projection;
    }

    /// <summary>
    /// Adds one or more columns to the projection list, renaming them if necessary.
    /// </summary>
    /// <param name="columns">The list of columns to add.</param>
    /// <param name="declare">If true, then consider these columns to have been explicitly declared, so further declarations cannot use their name.</param>
    /// <param name="doNotRepeat">If true then ignore any further attempt to add these columns.</param>
    public void addRange(Iterable<ColumnSymbol> columns, boolean declare, boolean doNotRepeat)
    {
        for (var column : columns)
        {
            var c = add(column, null, false, doNotRepeat); // PORT: §3.12 named argument doNotRepeat, baseName = null, replace = false

            if (declare && c != null)
            {
                _declaredNames.add(c.name());
            }
        }
    }

    public void addRange(Iterable<ColumnSymbol> columns, boolean declare) // PORT: §3.12 doNotRepeat = false
    {
        addRange(columns, declare, false);
    }

    public void addRange(Iterable<ColumnSymbol> columns) // PORT: §3.12 declare = false
    {
        addRange(columns, false);
    }

    /// <summary>
    /// Ignore any further attempt to add this column to the projection.
    /// </summary>
    public void doNotAdd(ColumnSymbol column)
    {
        _doNotAdd.add(column);
    }

    /// <summary>
    /// Ignore any further attempt to add any of these columns to the projection.
    /// </summary>
    public void doNotAddAny(Iterable<ColumnSymbol> columns)
    {
        for (var col : columns)
        {
            doNotAdd(col);
        }
    }

    /// <summary>
    /// True if an attempt to add the column will succeed.
    /// </summary>
    public boolean canAdd(ColumnSymbol column)
    {
        return !_doNotAdd.contains(column);
    }

    /// <summary>
    /// Adds a new undeclared column to the projection list.
    /// The name will be changed if it conflicts with a previously added column.
    /// </summary>
    /// <param name="column">The column to add.</param>
    /// <param name="baseName">The base name to use when generating an alternate unique name for this column.</param>
    /// <param name="replace">If true, allow this column to replace any previously added column with the same name.</param>
    /// <param name="doNotRepeat">If true, ignore any further attempts to add this column.</param>
    public ColumnSymbol add(ColumnSymbol column, String baseName, boolean replace, boolean doNotRepeat)
    {
        // do not accept columns without names
        if (DotNetStrings.isNullOrEmpty(column.name()))
        {
            return column;
        }

        if (_doNotAdd.contains(column))
        {
            // this column is ignored when attempting to add it.
            return column;
        }

        Integer index = replace ? _columnIndexMap.get(column.name()) : null; // PORT: §3.3 TryGetValue
        if (replace && index != null)
        {
            _projection.set(index, column);
        }
        else
        {
            // make sure the column name is unique.
            var uniqueName = _uniqueNames.getOrAddName(column.name(), baseName);
            if (!java.util.Objects.equals(uniqueName, column.name())) // PORT: §3.14 string !=
            {
                // include knowledge of the original column before it got renamed
                column = new ColumnSymbol(uniqueName, column.type(), column.description(), List.of(column)); // PORT: §3.12 named argument originalColumns
            }

            _projection.add(column);
            DotNet.dictionaryAdd(_columnIndexMap, column.name(), _projection.size() - 1); // PORT: §3.17 Dictionary.Add
        }

        if (doNotRepeat)
        {
            _doNotAdd.add(column);
        }

        return column;
    }

    public ColumnSymbol add(ColumnSymbol column, String baseName, boolean replace) // PORT: §3.12 doNotRepeat = false
    {
        return add(column, baseName, replace, false);
    }

    public ColumnSymbol add(ColumnSymbol column, String baseName) // PORT: §3.12 replace = false
    {
        return add(column, baseName, false);
    }

    public ColumnSymbol add(ColumnSymbol column) // PORT: §3.12 baseName = null
    {
        return add(column, null);
    }

    /// <summary>
    /// The column is added if a column with the same name is not already declared.
    /// </summary>
    /// <param name="column">The column to declare.</param>
    /// <param name="diagnostics">The diagnostics list to add diagnostics to if the column's name has already been declared.</param>
    /// <param name="location">The syntax location used to associate with diagnostics.</param>
    /// <param name="replace">If true, allow this column to replace any previously added column with the same name, but not specifically declared.</param>
    public void declare(ColumnSymbol column, List<Diagnostic> diagnostics, SyntaxNode location, boolean replace)
    {
        // is this name already explicitly declared elsewhere?
        if (_declaredNames.contains(column.name()))
        {
            diagnostics.add(DiagnosticFacts.getDuplicateColumnDeclaration(column.name()).withLocation(location));
            return;
        }

        Integer index = replace ? _columnIndexMap.get(column.name()) : null; // PORT: §3.3 TryGetValue
        if (replace && index != null)
        {
            _projection.set(index, column);
            _declaredNames.add(column.name());
        }
        else
        {
            var added = add(column);

            if (added != null)
            {
                _declaredNames.add(added.name());
            }
        }
    }

    public void declare(ColumnSymbol column, List<Diagnostic> diagnostics, SyntaxNode location) // PORT: §3.12 replace = false
    {
        declare(column, diagnostics, location, false);
    }

    /// <summary>
    /// Rename a column that exists from a previous query source.
    /// </summary>
    /// <param name="oldName">The name of a column already in the projection</param>
    /// <param name="newName">The new name for the column.</param>
    /// <param name="diagnostics">The diagnostics list to add diagnostics to if the column's name has already been declared.</param>
    /// <param name="location">The syntax location used to associate with any diagnostics added.</param>
    public ColumnSymbol rename(String oldName, String newName, List<Diagnostic> diagnostics, SyntaxNode location)
    {
        // find existing column index
        Integer index = _columnIndexMap.get(oldName); // PORT: §3.3 TryGetValue
        if (index == null)
        {
            // cannot find column... should this be an error?
            return null;
        }

        // is this name already explicitly declared elsewhere?
        if (_declaredNames.contains(newName))
        {
            diagnostics.add(DiagnosticFacts.getDuplicateColumnDeclaration(newName).withLocation(location));
            return null;
        }

        var oldColumn = _projection.get(index);
        var newColumn = new ColumnSymbol(newName, oldColumn.type(), oldColumn.description(), List.of(oldColumn)); // PORT: §3.12 named argument originalColumns

        _projection.set(index, newColumn);
        _columnIndexMap.remove(oldName);
        DotNet.dictionaryAdd(_columnIndexMap, newName, index); // PORT: §3.17 Dictionary.Add
        _declaredNames.add(newName);
        _uniqueNames.addName(newName);

        return newColumn;
    }
}
