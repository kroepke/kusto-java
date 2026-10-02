// Ported from: src/Kusto.Language/Utils/UniqueNameTable.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.util.LinkedHashMap;
import java.util.Objects;

import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// A class that maintains a set of unique names.
/// </summary>
@Internal
public class UniqueNameTable {
    private final LinkedHashMap<String, Integer> nameMap // PORT: §3.17 Dictionary -> LinkedHashMap
        = new LinkedHashMap<String, Integer>();

    public UniqueNameTable() {
    }

    /// <summary>
    /// Adds names already known to be unique to the table.
    /// </summary>
    public void addNames(Iterable<String> names) {
        for (var name : names) {
            this.addName(name);
        }
    }

    public void addName(String name) {
        if (!DotNetStrings.isNullOrEmpty(name) && !this.nameMap.containsKey(name)) { // check to avoid exception if input is bad?
            DotNet.dictionaryAdd(this.nameMap, name, 0); // PORT: §3.17 Dictionary.Add
        }
    }

    /// <summary>
    /// Adds a name to the table if it is unique, otherwise creates a new unique name.
    /// Returns either the original unique name or the newly created name.
    /// </summary>
    /// <param name="name">The candidate name that you would prefer to use.</param>
    /// <param name="baseName">An optional base name to use when formulating a new name.</param>
    public String getOrAddName(String name, String baseName) {
        Objects.requireNonNull(name, "key"); // PORT: §3.16 Dictionary.TryGetValue throws ArgumentNullException for a null key
        if (this.nameMap.containsKey(name)) { // PORT: §3.3 TryGetValue
            int lastUsedSuffix = this.nameMap.get(name);
            for (int suffix = lastUsedSuffix + 1; suffix < Integer.MAX_VALUE; suffix++) {
                var newName = (baseName != null ? baseName : name) + suffix; // PORT: §3.14 ??
                if (!this.nameMap.containsKey(newName)) {
                    // remember greatest suffix
                    this.nameMap.put(name, suffix);

                    // record this name too
                    DotNet.dictionaryAdd(this.nameMap, newName, 0); // PORT: §3.17 Dictionary.Add

                    return newName;
                }
            }
        } else {
            // remember name and suffix
            DotNet.dictionaryAdd(this.nameMap, name, 0); // PORT: §3.17 Dictionary.Add
        }

        return name;
    }

    public String getOrAddName(String name) { // PORT: §3.12 optional parameter baseName = null
        return getOrAddName(name, null);
    }

    public void clear() {
        this.nameMap.clear();
    }
}
