// Ported from: src/Kusto.Language.Generators/SyntaxNodeGenerator.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.generator;

/**
 * Design-time description of a node in the CSL tree (something that derives Kusto.DataNode.Csl.CslNode).
 */
public class SyntaxNodeInfo {
    /**
     * The name of the generated class.
     */
    public String Name;

    /**
     * Docstring for the generated class (summary section).
     */
    public String Doc;

    /**
     * Docstring for the generated class (remarks section).
     */
    public String Remarks;

    /**
     * The name of the base class.
     */
    public String Base;

    /**
     * Is this class abstract?
     */
    public boolean Abstract;

    /**
     * Is this class sealed?
     */
    public boolean Sealed;

    /**
     * How to generate the constructor methods
     */
    public ConstructorGenerationOptions ConstructionOptions = ConstructorGenerationOptions.Default;

    /**
     * How to generate the Clone method
     */
    public SyntaxNodeCloneOptions CloneOptions = SyntaxNodeCloneOptions.Default;

    /**
     * The type's properties.
     */
    public SyntaxNodeProperty[] Properties;

    /**
     * The kind of the syntax node.
     * If not specified, then a property is added
     */
    public String Kind;

    // PORT: §4.2 fluent builder replacing the C# object initializer (data mirror in SyntaxNodeInfos.java)
    public static SyntaxNodeInfo node() { return new SyntaxNodeInfo(); }
    public SyntaxNodeInfo name(String value) { this.Name = value; return this; }
    public SyntaxNodeInfo doc(String value) { this.Doc = value; return this; }
    public SyntaxNodeInfo remarks(String value) { this.Remarks = value; return this; }
    public SyntaxNodeInfo base(String value) { this.Base = value; return this; }
    public SyntaxNodeInfo abstract_(boolean value) { this.Abstract = value; return this; }
    public SyntaxNodeInfo sealed_(boolean value) { this.Sealed = value; return this; }
    public SyntaxNodeInfo constructionOptions(ConstructorGenerationOptions value) { this.ConstructionOptions = value; return this; }
    public SyntaxNodeInfo cloneOptions(SyntaxNodeCloneOptions value) { this.CloneOptions = value; return this; }
    public SyntaxNodeInfo properties(SyntaxNodeProperty[] value) { this.Properties = value; return this; }
    public SyntaxNodeInfo kind(String value) { this.Kind = value; return this; }
    public SyntaxNodeInfo end() { return this; }
}
