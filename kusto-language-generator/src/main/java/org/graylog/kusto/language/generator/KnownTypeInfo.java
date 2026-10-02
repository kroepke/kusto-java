// Ported from: src/Kusto.Language.Generators/SyntaxNodeGenerator.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.generator;

public class KnownTypeInfo {
    /**
     * The name of the type.
     */
    public String Name;

    /**
     * The namespace in which the type "lives".
     */
    public String Namespace;

    /**
     * Whether the type is immutable (once created, no modifications).
     */
    public boolean Immutable;

    /**
     * Whether the type is copy-by-value (no need to clone).
     */
    public boolean CopyByValue;

    // PORT: §4.2 fluent builder replacing the C# object initializer (data mirror in SyntaxNodeInfos.java)
    public static KnownTypeInfo known() { return new KnownTypeInfo(); }
    public KnownTypeInfo name(String value) { this.Name = value; return this; }
    public KnownTypeInfo namespace(String value) { this.Namespace = value; return this; }
    public KnownTypeInfo immutable(boolean value) { this.Immutable = value; return this; }
    public KnownTypeInfo copyByValue(boolean value) { this.CopyByValue = value; return this; }
    public KnownTypeInfo end() { return this; }
}
