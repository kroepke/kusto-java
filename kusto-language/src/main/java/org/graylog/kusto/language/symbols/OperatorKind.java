// Ported from: src/Kusto.Language/Symbols/OperatorKind.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

/// <summary>
/// The kind of operator for a <see cref="OperatorSymbol"/>
/// </summary>
public enum OperatorKind
{
    None, // not an operator

    // unary operators
    UnaryMinus, // negate
    UnaryPlus,

    // binary operators
    Add,
    Subtract,
    Multiply,
    Divide,
    Modulo,

    // string binary operators
    EqualTilde, // TODO: need better name
    BangTilde,  // TODO: need better name
    Has,
    HasCs,
    NotHas,
    NotHasCs,
    HasPrefix,
    HasPrefixCs,
    NotHasPrefix,
    NotHasPrefixCs,
    HasSuffix,
    HasSuffixCs,
    NotHasSuffix,
    NotHasSuffixCs,
    Like,
    LikeCs,
    NotLike,
    NotLikeCs,
    Contains,
    ContainsCs,
    NotContains,
    NotContainsCs,
    StartsWith,
    StartsWithCs,
    NotStartsWith,
    NotStartsWithCs,
    EndsWith,
    EndsWithCs,
    NotEndsWith,
    NotEndsWithCs,
    MatchRegex,
    Search,

    // relational (equalities, inequalities...)
    LessThan,
    LessThanOrEqual,
    GreaterThan,
    GreaterThanOrEqual,
    Equal,
    NotEqual,
    And,
    Or,

    In,
    InCs,
    NotIn,
    NotInCs,
    Between,
    NotBetween,
    HasAny,
    HasAll; // PORT: §3.17 trailing comma → ;
}
