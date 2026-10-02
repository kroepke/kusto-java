// Ported from: src/Kusto.Language/Utils/ArgumentCheckers.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.util.List;

import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.Internal;

@Internal
public final class ArgumentCheckers {
    private ArgumentCheckers() {
    }

    /// <summary>
    /// Throws an exception if the argument is null
    /// </summary>
    public static <T> T checkArgumentNull(T value, String parameterName) {
        if (value == null)
            throw new NullPointerException(parameterName); // PORT: §3.16 ArgumentNullException
        return value;
    }

    /// <summary>
    /// Throws an exception if the argument is null or empty.
    /// </summary>
    public static <T> List<T> checkArgumentNullOrEmpty(List<T> list, String parameterName) {
        if (list == null)
            throw new NullPointerException(parameterName); // PORT: §3.16 ArgumentNullException

        if (list.size() == 0)
            throw new IllegalArgumentException("Parameter '" + DotNet.str(parameterName) + "' must have at least one element."); // PORT: §3.16 ArgumentException

        return list;
    }

    /// <summary>
    /// Throws an exception if the argument is null or an element is null.
    /// </summary>
    public static <T> List<T> checkArgumentNullOrElementNull(List<T> list, String parameterName) {
        if (list == null)
            throw new NullPointerException(parameterName); // PORT: §3.16 ArgumentNullException

        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == null)
                throw new IllegalArgumentException("Element " + i + " of parameter '" + DotNet.str(parameterName) + "' is null."); // PORT: §3.16 ArgumentException
        }

        return list;
    }

    /// <summary>
    /// Throws an exception if the argument is null, empty or an element is null.
    /// </summary>
    public static <T> List<T> checkArgumentNullOrEmptyOrElementNull(List<T> list, String parameterName) {
        ArgumentCheckers.checkArgumentNullOrElementNull(list, parameterName); // PORT: §3.5
        ArgumentCheckers.checkArgumentNullOrEmpty(list, parameterName); // PORT: §3.5
        return list;
    }
}
