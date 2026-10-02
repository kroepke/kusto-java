// Ported from: src/Kusto.Language.Generators/SyntaxNodeGenerator.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language.generator;

/**
 * Various options that control how {@link SyntaxNodeGenerator} will create the Clone() method.
 */
public enum SyntaxNodeCloneOptions {
    /**
     * Default: Clone() uses the constructor that takes all properties, in order.
     */
    Default,

    /**
     * Similar to Default, but adds a last argument to the constructor: Result.Tuple.Clone(),
     */
    AppendExpressionResultTuple,

    /**
     * Similar to Default, but adds a last argument to the constructor: Result.Clone(), SymbolicName
     */
    AppendExpressionResultAndSymbolicName,

    /**
     * The Clone() method is not generated -- the class will use customer cloning.
     */
    Custom,
}
