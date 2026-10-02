// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for SchemaDisplay (Symbols/SchemaDisplay.cs); expected text from upstream and the goldens.
package org.graylog.kusto.language.symbols;

import static org.graylog.kusto.language.symbols.W1bTestSupport.assumeKustoFacts;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The goldens render {@code type}/{@code resultType} with {@code SchemaDisplay.GetText} and the signature parameters
 * with {@code GetParameterTypeText} ({@code oracle/kusto-oracle/src/Golden.cs:311,334,386-395}). Strings marked
 * "golden" below occur verbatim in {@code kusto-language-conformance/src/test/resources/goldens/*.jsonl.gz}.
 * Tests that render a column or parameter name go through {@code KustoFacts.BracketNameIfNecessary} and are skipped
 * until W2 ports it.
 */
class SchemaDisplayTest {
    @Test
    void nullAndNamedSymbols() {
        assertEquals("", SchemaDisplay.getText(null));
        assertEquals("long", SchemaDisplay.getText(ScalarTypes.Long));          // golden
        assertEquals("real", SchemaDisplay.getText(ScalarTypes.Real));          // golden
        assertEquals("string", SchemaDisplay.getText(ScalarTypes.String));      // golden
        assertEquals("unknown", SchemaDisplay.getText(ScalarTypes.Unknown));    // golden
        assertEquals("dynamic", SchemaDisplay.getText(ScalarTypes.Dynamic));    // golden
        assertEquals("error", SchemaDisplay.getText(ErrorSymbol.Instance));     // golden
        assertEquals("void", SchemaDisplay.getText(VoidSymbol.Instance));       // golden
        assertEquals("tuple", SchemaDisplay.getText(new TupleSymbol(new ColumnSymbol("a", ScalarTypes.Long)))); // golden
        assertEquals("group", SchemaDisplay.getText(new GroupSymbol(ScalarTypes.Long)));  // golden
        assertEquals("db", SchemaDisplay.getText(new DatabaseSymbol("db")));    // golden
    }

    @Test
    void dynamicVariantsRenderTheirName() {
        // default branch: symbol.Name, which is "dynamic" for every dynamic variant
        assertEquals("dynamic", SchemaDisplay.getText(new DynamicArraySymbol(ScalarTypes.Long)));
        assertEquals("dynamic", SchemaDisplay.getText(new DynamicBagSymbol(new ColumnSymbol("p", ScalarTypes.Long))));
        assertEquals("dynamic", SchemaDisplay.getText(new DynamicPrimitiveSymbol(ScalarTypes.Long)));
    }

    @Test
    void scalarTypeText() {
        assertEquals("long", SchemaDisplay.getScalarTypeText(ScalarTypes.Long));
        assertEquals("unknown", SchemaDisplay.getScalarTypeText(ScalarTypes.Unknown));
        assertEquals("dynamic", SchemaDisplay.getScalarTypeText(ScalarTypes.Dynamic));
        assertEquals("dynamic", SchemaDisplay.getScalarTypeText(new DynamicArraySymbol(ScalarTypes.Long)));
        assertEquals("dynamic", SchemaDisplay.getScalarTypeText(new DynamicBagSymbol()));
        // non-scalar types and null fall to default ""
        assertEquals("", SchemaDisplay.getScalarTypeText(TableSymbol.Empty));
        assertEquals("", SchemaDisplay.getScalarTypeText(ErrorSymbol.Instance));
        assertEquals("", SchemaDisplay.getScalarTypeText(null));
    }

    @Test
    void emptyTableAndParameterlessFunction() {
        assertEquals("()", SchemaDisplay.getText(TableSymbol.Empty));                         // golden
        assertEquals("()", SchemaDisplay.getText(new FunctionSymbol("f", ScalarTypes.Long)));  // golden
        assertEquals("()", SchemaDisplay.getText(new FunctionSymbol("f", "T | take 1", Tabularity.Tabular)));
    }

    @Test
    void parameterTypeText() {
        assertEquals("long", SchemaDisplay.getParameterTypeText(new Parameter("x", ScalarTypes.Long)));
        assertEquals("dynamic", SchemaDisplay.getParameterTypeText(new Parameter("x", ScalarTypes.Dynamic)));
        assertEquals("(*)", SchemaDisplay.getParameterTypeText(new Parameter("T", TableSymbol.Empty)));
        assertEquals("(*)", SchemaDisplay.getParameterTypeText(new Parameter("T", TableSymbol.Empty.withIsOpen(true))));
        // not a single declared type: dynamic
        assertEquals("dynamic", SchemaDisplay.getParameterTypeText(new Parameter("x", ParameterTypeKind.Scalar)));
        assertEquals("dynamic", SchemaDisplay.getParameterTypeText(new Parameter("T", ParameterTypeKind.Tabular)));
        assertEquals("dynamic", SchemaDisplay.getParameterTypeText(new Parameter("x", new TypeSymbol[] { ScalarTypes.Long, ScalarTypes.Real })));
    }

    @Test
    void tablesWithColumns() {
        assumeKustoFacts();
        assertEquals("(a: long, b: string)", SchemaDisplay.getText(new TableSymbol(
            new ColumnSymbol("a", ScalarTypes.Long), new ColumnSymbol("b", ScalarTypes.String))));
        assertEquals("(a: real)", SchemaDisplay.getText(new TableSymbol(new ColumnSymbol("a", ScalarTypes.Real))));  // golden
        assertEquals("(id: string, width: real)", SchemaDisplay.getText(new TableSymbol(                             // golden
            new ColumnSymbol("id", ScalarTypes.String), new ColumnSymbol("width", ScalarTypes.Real))));
        assertEquals("(Column1: unknown)", SchemaDisplay.getText(new TableSymbol(new ColumnSymbol("Column1", ScalarTypes.Unknown)))); // golden
        assertEquals("(arr: dynamic)", SchemaDisplay.getText(new TableSymbol(new ColumnSymbol("arr", new DynamicArraySymbol(ScalarTypes.Long))))); // golden
        assertEquals("(['a b']: real)", SchemaDisplay.getText(new TableSymbol(new ColumnSymbol("a b", ScalarTypes.Real))));  // golden
        assertEquals("(['where']: long)", SchemaDisplay.getText(new TableSymbol(new ColumnSymbol("where", ScalarTypes.Long)))); // golden
        // a column whose type is not scalar renders an empty type text
        assertEquals("(t: )", SchemaDisplay.getText(new TableSymbol(new ColumnSymbol("t", TableSymbol.Empty))));
        assertEquals("a: long", SchemaDisplay.getText(new ColumnSymbol("a", ScalarTypes.Long)));
        assertEquals("p: real", SchemaDisplay.getText(new ParameterSymbol("p", ScalarTypes.Real)));
    }

    @Test
    void functionsRenderTheirFirstSignature() {
        assumeKustoFacts();
        assertEquals("(T: (*))", SchemaDisplay.getText(new FunctionSymbol("f", ScalarTypes.Long,  // golden
            new Parameter("T", TableSymbol.Empty))));
        assertEquals("(tbl: (*), A_col: string, B_col: string, scope_col: string)", SchemaDisplay.getText(new FunctionSymbol("f", // golden
            "T", Tabularity.Tabular, List.of(new Parameter("tbl", TableSymbol.Empty), new Parameter("A_col", ScalarTypes.String),
                new Parameter("B_col", ScalarTypes.String), new Parameter("scope_col", ScalarTypes.String)))));
        assertEquals("(t: (a: long), x: dynamic)", SchemaDisplay.getText(new FunctionSymbol("f", ScalarTypes.Long,
            new Parameter("t", new TableSymbol(new ColumnSymbol("a", ScalarTypes.Long))), new Parameter("x", ParameterTypeKind.Scalar))));
        assertEquals("(a: long)", SchemaDisplay.getText(new FunctionSymbol("f",
            new Signature(ScalarTypes.Long, new Parameter("a", ScalarTypes.Long)),
            new Signature(ScalarTypes.Long, new Parameter("b", ScalarTypes.Long), new Parameter("c", ScalarTypes.Long)))));
    }
}
