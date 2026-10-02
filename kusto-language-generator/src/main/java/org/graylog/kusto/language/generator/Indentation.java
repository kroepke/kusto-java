// Ported from: src/Kusto.Language.Generators/CodeGenerator.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.generator;

class Indentation { // PORT: §2.6 internal; package-private
    private IndentedTextWriter writer;
    private int indent;
    private String s;

    Indentation(IndentedTextWriter writer, int indent) {
        this.writer = writer;
        this.indent = indent;
        s = null;
    }

    String indentationString() {
        if (s == null) {
            String tabString = writer.tabString();
            StringBuilder sb = new StringBuilder(indent * tabString.length());
            for (int i = 0; i < indent; i++) {
                sb.append(tabString);
            }
            s = sb.toString();
        }
        return s;
    }
}
