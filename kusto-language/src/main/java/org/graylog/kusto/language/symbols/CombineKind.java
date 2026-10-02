// Ported from: src/Kusto.Language/Symbols/CombineKind.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

public enum CombineKind
{
    /// <summary>
    /// Multiple columns with same name and type become one column.
    /// Columns with same name and different type are renamed to be unique with a numeric suffix added.
    /// </summary>
    UnifySameNameAndType, // union style

    /// <summary>
    /// Multiple columns with the same name will become one column.
    /// If the types differ, the column type will be dynamic.
    /// </summary>
    UnifySameName, // find style

    /// <summary>
    /// Columns with the same name will be be renamed to be unique with a numeric suffix added.
    /// </summary>
    UniqueNames
}
