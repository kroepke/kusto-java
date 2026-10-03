// Ported from: src/Kusto.Language/Binder/ColumnMap.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.binding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import org.graylog.kusto.language.symbols.ColumnSymbol;
import org.graylog.kusto.language.symbols.DynamicSymbol;
import org.graylog.kusto.language.symbols.ScalarTypes;
import org.graylog.kusto.language.symbols.TypeSymbol;
import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.dotnet.Internal;

// PORT: §3.17 the map values are a ColumnSymbol, a List<ColumnSymbol> (always an ArrayList here) or a Dictionary<TypeSymbol, object> (always a LinkedHashMap here); type tests use ArrayList/LinkedHashMap
@Internal
public class ColumnMap
{
    /// <summary>
    /// map of column names to columns, column lists or dictionaries of type to columns/column lists.
    /// </summary>
    private final LinkedHashMap<String, Object> _nameMap;
    private final boolean _unifyDynamicTypes;

    public ColumnMap(boolean unifyDynamicTypes)
    {
        _nameMap = new LinkedHashMap<String, Object>();
        _unifyDynamicTypes = unifyDynamicTypes;
    }

    public ColumnMap() // PORT: §3.12 unifyDynamicTypes = true
    {
        this(true);
    }

    public ColumnMap(Iterable<ColumnSymbol> columns, boolean unifyDynamicTypes)
    {
        this(unifyDynamicTypes);
        addRange(columns);
    }

    public ColumnMap(Iterable<ColumnSymbol> columns) // PORT: §3.12 unifyDynamicTypes = true
    {
        this(columns, true);
    }

    /// <summary>
    /// Adds a list of columns to the map.
    /// </summary>
    public void addRange(Iterable<ColumnSymbol> columns)
    {
        for (var col : columns)
        {
            add(col);
        }
    }

    private TypeSymbol getTypeKey(TypeSymbol type)
    {
        if (_unifyDynamicTypes && type instanceof DynamicSymbol)
            return ScalarTypes.Dynamic;
        return type;
    }

    /// <summary>
    /// Adds a column to the map.
    /// </summary>
    @SuppressWarnings("unchecked")
    public void add(ColumnSymbol column)
    {
        var columnListOrDictionary = _nameMap.get(column.name()); // PORT: §3.3 TryGetValue; no null values are ever stored
        if (columnListOrDictionary != null)
        {
            if (columnListOrDictionary instanceof ColumnSymbol c)
            {
                var cKey = getTypeKey(c.type());
                var columnKey = getTypeKey(column.type());
                if (cKey == columnKey)
                {
                    var list = new ArrayList<ColumnSymbol>();
                    list.add(c);
                    list.add(column);
                    _nameMap.put(column.name(), list);
                }
                else
                {
                    var dict = new LinkedHashMap<TypeSymbol, Object>();
                    dict.put(cKey, c);
                    dict.put(columnKey, column);
                    _nameMap.put(column.name(), dict);
                }
            }
            else if (columnListOrDictionary instanceof ArrayList<?> listObj)
            {
                var list = (ArrayList<ColumnSymbol>) listObj;
                var listTypeKey = getTypeKey(list.get(0).type());
                var columnTypeKey = getTypeKey(column.type());

                if (listTypeKey == columnTypeKey)
                {
                    list.add(column);
                }
                else
                {
                    // PORT-BUG: upstream builds this dictionary but never stores it in _nameMap, so the column is dropped; mirrored verbatim
                    var dict = new LinkedHashMap<TypeSymbol, Object>();
                    dict.put(listTypeKey, list);
                    dict.put(columnTypeKey, column);
                }
            }
            else if (columnListOrDictionary instanceof LinkedHashMap<?, ?> dictObj)
            {
                var dict = (LinkedHashMap<TypeSymbol, Object>) dictObj;
                var columnTypeKey = getTypeKey(column.type());
                var columnOrList = dict.get(columnTypeKey); // PORT: §3.3 TryGetValue
                if (columnOrList != null)
                {
                    if (columnOrList instanceof ColumnSymbol col)
                    {
                        var newList = new ArrayList<ColumnSymbol>();
                        newList.add(col);
                        newList.add(column);
                        dict.put(columnTypeKey, newList);
                    }
                    else if (columnOrList instanceof ArrayList<?> existingColumnsObj)
                    {
                        var existingColumns = (ArrayList<ColumnSymbol>) existingColumnsObj;
                        existingColumns.add(column);
                    }
                }
                else
                {
                    dict.put(columnTypeKey, column);
                }
            }
        }
        else
        {
            _nameMap.put(column.name(), column); // PORT: §3.17 Dictionary.Add; absence was just checked
        }
    }

    /// <summary>
    /// Removes all the columns with the specified name from the map.
    /// </summary>
    public void remove(String name)
    {
        _nameMap.remove(name);
    }

    /// <summary>
    /// Returns true if there is at least one column with the specified name in the map.
    /// </summary>
    public boolean hasColumns(String name)
    {
        return _nameMap.containsKey(name);
    }

    /// <summary>
    /// Returns true if the columns with the specified name have more than one type.
    /// </summary>
    public boolean hasMultipleTypes(String name)
    {
        var columnListOrDictionary = _nameMap.get(name); // PORT: §3.3 TryGetValue
        return columnListOrDictionary != null
            && columnListOrDictionary instanceof LinkedHashMap<?, ?> dict
            && dict.size() > 1;
    }

    /// <summary>
    /// Gets all the types for all the columns with the specified name.
    /// </summary>
    @SuppressWarnings("unchecked")
    public List<TypeSymbol> getTypes(String name)
    {
        var columnListOrDictionary = _nameMap.get(name); // PORT: §3.3 TryGetValue
        if (columnListOrDictionary != null)
        {
            if (columnListOrDictionary instanceof LinkedHashMap<?, ?> dict)
            {
                return ListExtensions.toReadOnly(((LinkedHashMap<TypeSymbol, Object>) dict).keySet());
            }
            else if (columnListOrDictionary instanceof ArrayList<?> listObj)
            {
                var list = (ArrayList<ColumnSymbol>) listObj;
                return Collections.singletonList(list.get(0).type()); // PORT: §3.17 new[] { ... }
            }
            else if (columnListOrDictionary instanceof ColumnSymbol col)
            {
                return Collections.singletonList(col.type()); // PORT: §3.17 new[] { ... }
            }
        }

        return EmptyReadOnlyList.<TypeSymbol>instance();
    }

    /// <summary>
    /// Returns true if there are more than one column with the given name in the map.
    /// </summary>
    public boolean hasMutipleColumns(String name)
    {
        var columnListOrDictionary = _nameMap.get(name); // PORT: §3.3 TryGetValue
        if (columnListOrDictionary != null)
        {
            if (columnListOrDictionary instanceof ColumnSymbol col)
            {
                return false;
            }
            else if (columnListOrDictionary instanceof ArrayList<?> columns)
            {
                return columns.size() > 1;
            }
            else if (columnListOrDictionary instanceof LinkedHashMap<?, ?> dict)
            {
                if (dict.size() > 1)
                {
                    return true;
                }
                else if (dict.size() == 1)
                {
                    var columnOrList = dict.values().iterator().next(); // PORT: §3.6 dict.First().Value
                    if (columnOrList instanceof ColumnSymbol)
                    {
                        return true;
                    }
                    else if (columnOrList instanceof ArrayList<?> cols)
                    {
                        return cols.size() > 1;
                    }
                }
            }
        }

        return false;
    }

    /// <summary>
    /// Returns true if there is more than one columns with the specified name and type.
    /// </summary>
    @SuppressWarnings("unchecked")
    public boolean hasMultipleColumns(String name, TypeSymbol type)
    {
        var columnListOrDictionary = _nameMap.get(name); // PORT: §3.3 TryGetValue
        if (columnListOrDictionary != null)
        {
            var typeKey = getTypeKey(type);
            if (columnListOrDictionary instanceof LinkedHashMap<?, ?> dict)
            {
                var columnOrList = ((LinkedHashMap<TypeSymbol, Object>) dict).get(typeKey); // PORT: §3.3 TryGetValue
                return columnOrList != null
                    && columnOrList instanceof ArrayList<?> cols
                    && cols.size() > 0;
            }
            else if (columnListOrDictionary instanceof ArrayList<?> columnsObj
                && getTypeKey(((ArrayList<ColumnSymbol>) columnsObj).get(0).type()) == typeKey)
            {
                return columnsObj.size() > 1;
            }
        }

        return false;
    }

    /// <summary>
    /// Gets all the columns with the specified name.
    /// </summary>
    @SuppressWarnings("unchecked")
    public List<ColumnSymbol> getColumns(String name) // PORT: §3.4 yield -> eager list
    {
        var result = new ArrayList<ColumnSymbol>();
        var columnListOrDictionary = _nameMap.get(name); // PORT: §3.3 TryGetValue
        if (columnListOrDictionary != null)
        {
            if (columnListOrDictionary instanceof LinkedHashMap<?, ?> dict)
            {
                for (var kvp : ((LinkedHashMap<TypeSymbol, Object>) dict).entrySet())
                {
                    if (kvp.getValue() instanceof ArrayList<?> colsObj)
                    {
                        for (var c : (ArrayList<ColumnSymbol>) colsObj)
                        {
                            result.add(c);
                        }
                    }
                    else if (kvp.getValue() instanceof ColumnSymbol col)
                    {
                        result.add(col);
                    }
                }
            }
            else if (columnListOrDictionary instanceof ArrayList<?> columnsObj)
            {
                for (var c : (ArrayList<ColumnSymbol>) columnsObj)
                {
                    result.add(c);
                }
            }
            else if (columnListOrDictionary instanceof ColumnSymbol col)
            {
                result.add(col);
            }
        }

        return result;
    }

    /// <summary>
    /// Gets all the columns with the specified name and type.
    /// </summary>
    @SuppressWarnings("unchecked")
    public List<ColumnSymbol> getColumns(String name, TypeSymbol type)
    {
        var columnListOrDictionary = _nameMap.get(name); // PORT: §3.3 TryGetValue
        if (columnListOrDictionary != null)
        {
            var typeKey = getTypeKey(type);
            if (columnListOrDictionary instanceof LinkedHashMap<?, ?> dict)
            {
                var columnOrList = ((LinkedHashMap<TypeSymbol, Object>) dict).get(typeKey); // PORT: §3.3 TryGetValue
                if (columnOrList != null)
                {
                    if (columnOrList instanceof ArrayList<?> cols)
                    {
                        return (ArrayList<ColumnSymbol>) cols;
                    }
                    else if (columnOrList instanceof ColumnSymbol col)
                    {
                        return Collections.singletonList(col); // PORT: §3.17 new[] { col }
                    }
                }
            }
            else if (columnListOrDictionary instanceof ArrayList<?> columns)
            {
                return (ArrayList<ColumnSymbol>) columns;
            }
            else if (columnListOrDictionary instanceof ColumnSymbol col)
            {
                return Collections.singletonList(col); // PORT: §3.17 new[] { col }
            }
        }

        return EmptyReadOnlyList.<ColumnSymbol>instance();
    }
}
