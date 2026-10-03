// Ported from: src/Kusto.Language/Binder/ScopeKind.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.binding;

import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// Scope kind.
/// </summary>
@Internal
public enum ScopeKind
{
    /// <summary>
    /// Normal lookup in <see cref="Binder"/>
    /// </summary>
    Normal,

    /// <summary>
    /// Only aggregate functions are visible
    /// </summary>
    Aggregate,

    /// <summary>
    /// Only plug-in funtions are visible
    /// </summary>
    PlugIn,

    /// <summary>
    /// Only query options are visible
    /// </summary>
    Option
}
