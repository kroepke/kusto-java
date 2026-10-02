// Ported from: src/Kusto.Language.Generators/SyntaxNodeGenerator.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.generator;

/**
 * Various options that control how {@link SyntaxNodeGenerator} will create the constructors.
 */
public enum ConstructorGenerationOptions {
    /**
     * Default: Create all constructors
     */
    Default,

    /**
     * Do not create the constructor that takes all properties
     */
    SuppressAllPropertiesConstructor,
}
