// Ported from: src/Kusto.Language.Generators/SyntaxNodeGenerator.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.generator;

/**
 * Design-time representation of a property.
 */
public class SyntaxNodeProperty {
    /**
     * The name of the property.
     */
    public String Name;

    /**
     * The .NET type of the property.
     */
    public String Type;

    /**
     * The docstring of the property.
     */
    public String Doc;

    /**
     * If true, the setter will be made public.
     */
    public boolean PublicSetter;

    /**
     * If true then the value is optional (can be null)
     */
    public boolean Optional;

    /**
     * If true then the default value specified in the constructor.
     */
    public String DefaultValue;

    /**
     * True if the property is part of the syntax
     */
    public boolean IsSyntax = true;

    /**
     * The completion kind
     */
    public String Completion;

    // PORT: §4.2 fluent builder replacing the C# object initializer (data mirror in SyntaxNodeInfos.java)
    public static SyntaxNodeProperty prop() { return new SyntaxNodeProperty(); }
    public SyntaxNodeProperty name(String value) { this.Name = value; return this; }
    public SyntaxNodeProperty type(String value) { this.Type = value; return this; }
    public SyntaxNodeProperty doc(String value) { this.Doc = value; return this; }
    public SyntaxNodeProperty publicSetter(boolean value) { this.PublicSetter = value; return this; }
    public SyntaxNodeProperty optional(boolean value) { this.Optional = value; return this; }
    public SyntaxNodeProperty defaultValue(String value) { this.DefaultValue = value; return this; }
    public SyntaxNodeProperty isSyntax(boolean value) { this.IsSyntax = value; return this; }
    public SyntaxNodeProperty completion(String value) { this.Completion = value; return this; }
}
