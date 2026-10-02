// Ported from: src/Kusto.Language/Utils/ConnectionInfo.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.utils;

import java.util.LinkedHashMap;

import org.graylog.kusto.language.parsing.TokenParser;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.EqualityComparer;
import org.graylog.kusto.language.utils.dotnet.Internal;

@Internal
public class ConnectionInfo {
    private final String _text;
    // PORT: §3.17 Dictionary(StringComparer.OrdinalIgnoreCase) is a LinkedHashMap keyed by EqualityComparer.Key
    private final LinkedHashMap<EqualityComparer.Key<String>, String> _parts;

    private ConnectionInfo(String text, LinkedHashMap<EqualityComparer.Key<String>, String> parts) {
        _text = text;
        _parts = parts;
    }

    // PORT: §3.17 key wrapper for the case-insensitive dictionary
    private static EqualityComparer.Key<String> key(String name) {
        return EqualityComparer.of(name, EqualityComparer.ORDINAL_IGNORE_CASE);
    }

    public String getPart(String name) {
        // PORT: §3.3 TryGetValue; values are never null here
        String value = _parts.get(key(name));
        return value != null ? value : "";
    }

    public String text() {
        return _text;
    }

    public String dataSource() {
        return getPart("Data Source");
    }

    public static ConnectionInfo parse(String text) {
        var parts = new LinkedHashMap<EqualityComparer.Key<String>, String>(); // PORT: §3.17

        // switch to parsing argument text
        int pos = 0;
        while (pos < text.length()) {
            // get name
            var nameStart = pos;
            char ch = '\0';
            while (pos < text.length() && (ch = text.charAt(pos)) != '=' && ch != ';') {
                pos++;
            }

            if (pos < text.length() && (ch = text.charAt(pos)) == '=') {
                var name = text.substring(nameStart, pos); // PORT: §5.4 Substring(nameStart, pos - nameStart)
                pos++; // skip =

                // skip whitespace
                pos += TokenParser.scanWhitespace(text, pos);

                var valueStart = pos;

                // string literal (no escapes)?
                if (pos < text.length() && ((ch = text.charAt(pos)) == '"' || ch == '\'')) {
                    pos++;
                    while (pos < text.length() && text.charAt(pos) != ch) {
                        pos++;
                    }

                    if (pos < text.length() && text.charAt(pos) == ch) {
                        pos++;
                        var value = text.substring(valueStart + 1, (valueStart + 1) + (pos - valueStart - 2)); // PORT: §5.4 start + length
                        parts.put(key(name), value);
                    } else {
                        var value = text.substring(valueStart + 1, (valueStart + 1) + (pos - valueStart - 1)); // PORT: §5.4 start + length
                        parts.put(key(name), value);
                    }
                } else {
                    // consume to end or semi
                    while (pos < text.length() && (ch = text.charAt(pos)) != ';') {
                        pos++;
                    }
                    var value = DotNetStrings.trim(text.substring(valueStart, valueStart + (pos - valueStart))); // PORT: §5.1 Trim, §5.4 start + length
                    parts.put(key(name), value);
                }

                // skip any whitespace after value
                pos += TokenParser.scanWhitespace(text, pos);
            } else {
                // we have a value without a name
                // assume this is a data source uri
                var value = DotNetStrings.trim(text.substring(nameStart, nameStart + (pos - nameStart))); // PORT: §5.1 Trim, §5.4 start + length
                parts.put(key("Data Source"), value);
            }

            if (pos < text.length() && text.charAt(pos) == ';') {
                pos++;
                continue;
            }

            break;
        }

        return new ConnectionInfo(text, parts);
    }
}
