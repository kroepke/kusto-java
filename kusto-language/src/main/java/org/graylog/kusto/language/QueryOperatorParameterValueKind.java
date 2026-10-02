// Ported from: src/Kusto.Language/QueryOperatorParameters.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language;

public enum QueryOperatorParameterValueKind
{
    /// <summary>
    /// Any value
    /// </summary>
    Any,

    /// <summary>
    /// Any scalar literal value
    /// </summary>
    ScalarLiteral,

    /// <summary>
    /// Any integer literal value
    /// </summary>
    IntegerLiteral,

    /// <summary>
    /// Any numeric literal value
    /// </summary>
    NumericLiteral,

    /// <summary>
    /// Any numeric literal, but represented as a double for whatever reason.
    /// </summary>
    ForcedRealLiteral,

    /// <summary>
    /// Any scalar string literal
    /// </summary>
    StringLiteral,

    /// <summary>
    /// Any boolean literal
    /// </summary>
    BoolLiteral,

    /// <summary>
    /// Any summable literal value
    /// </summary>
    SummableLiteral,

    /// <summary>
    /// Any string expression
    /// </summary>
    String,

    /// <summary>
    /// The parameter is an word token (identifier or keyword)
    /// </summary>
    Word,

    /// <summary>
    /// The parameter is an word token (identifier or keyword) or a numeric literal
    /// </summary>
    WordOrNumber,

    /// <summary>
    /// The parameter is a name declaration
    /// </summary>
    NameDeclaration,

    /// <summary>
    /// The parameter is a column reference
    /// </summary>
    Column,

    /// <summary>
    /// The parameter is a list of column references
    /// </summary>
    ColumnList; // PORT: §3.17 trailing comma → ;
}
