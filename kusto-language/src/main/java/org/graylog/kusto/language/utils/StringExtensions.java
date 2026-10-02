// Ported from: src/Kusto.Language/Utils/StringExtensions.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.util.function.Predicate;

import org.graylog.kusto.language.utils.dotnet.Internal;

@Internal
public final class StringExtensions {
    private StringExtensions() {
    }

    /// <summary>
    /// Returns a new string with the characters filtered to only characters matching the predicate.
    /// </summary>
    public static String filter(String text, Predicate<Character> fnPredicate) { // PORT: §3.8 Func<char,bool>
        // PORT: §3.6 text.All(fnPredicate) as a plain loop over UTF-16 code units
        boolean all = true;
        for (int i = 0; i < text.length(); i++) {
            if (!fnPredicate.test(text.charAt(i))) {
                all = false;
                break;
            }
        }
        if (all)
            return text;

        var builder = new StringBuilder();

        for (int i = 0; i < text.length(); i++) { // PORT: §3.18 foreach (var ch in text)
            char ch = text.charAt(i);
            if (fnPredicate.test(ch))
                builder.append(ch);
        }

        return builder.toString();
    }
}
