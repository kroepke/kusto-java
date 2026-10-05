// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: worked example of the binding API (schema, result types, referenced symbols, signatures, scope, semantic diagnostics).

package org.graylog.kusto.language.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.graylog.kusto.language.Diagnostic;
import org.graylog.kusto.language.GlobalState;
import org.graylog.kusto.language.KustoCode;
import org.graylog.kusto.language.symbols.ColumnSymbol;
import org.graylog.kusto.language.symbols.DatabaseSymbol;
import org.graylog.kusto.language.symbols.FunctionSymbol;
import org.graylog.kusto.language.symbols.ScalarTypes;
import org.graylog.kusto.language.symbols.SchemaDisplay;
import org.graylog.kusto.language.symbols.Signature;
import org.graylog.kusto.language.symbols.Symbol;
import org.graylog.kusto.language.symbols.TableSymbol;
import org.graylog.kusto.language.symbols.TypeSymbol;
import org.graylog.kusto.language.syntax.FunctionCallExpression;
import org.graylog.kusto.language.syntax.NameReference;
import org.junit.jupiter.api.Test;

/**
 * A worked example of binding: describe a schema in a {@link GlobalState}, call
 * {@link KustoCode#parseAndAnalyze}, then read the semantic information the binder attached to the
 * syntax tree. Each test is one step; the assertions document what the binder produces.
 */
class BindingExampleTest {
    private static final String QUERY = """
        let threshold = 1000;
        CostlyStorms(threshold)
        | summarize Total = sum(DamageProperty), Last = max(StartTime) by State
        | top 3 by Total""";

    /** The binder resolves names only against what the GlobalState knows. */
    private static GlobalState globals() {
        var storms = new TableSymbol("StormEvents",
            new ColumnSymbol("State", ScalarTypes.String),
            new ColumnSymbol("EventType", ScalarTypes.String),
            new ColumnSymbol("DamageProperty", ScalarTypes.Long),
            new ColumnSymbol("StartTime", ScalarTypes.DateTime));
        // A stored function: parameter list and body are KQL text, bound on demand.
        var costly = new FunctionSymbol("CostlyStorms", "(minDamage: long)",
            "{ StormEvents | where DamageProperty >= minDamage }");
        return GlobalState.default_().withDatabase(new DatabaseSymbol("db", storms, costly));
    }

    private static final KustoCode CODE = KustoCode.parseAndAnalyze(QUERY, globals());

    @Test
    void resultTypeFlowsThroughThePipe() {
        // summarize reshapes the table to its aggregates plus the by-columns; top keeps that shape
        assertEquals("table(State:string, Total:long, Last:datetime)", describe(CODE.resultType()));
        assertEquals(List.of(), CODE.getDiagnostics());
    }

    @Test
    void everyNameReferencesASymbol() {
        List<String> refs = new ArrayList<>();
        for (NameReference ref : CODE.syntax().getDescendants(NameReference.class)) {
            Symbol s = ref.referencedSymbol();
            refs.add(ref.simpleName() + " -> " + s.kind() + " " + describe(ref.resultType()));
        }
        assertEquals(List.of(
            // the stored function's type comes from binding its body text
            "CostlyStorms -> Function table(State:string, EventType:string, DamageProperty:long, StartTime:datetime)",
            "threshold -> Variable long",
            "sum -> Function long",
            "DamageProperty -> Column long",
            "max -> Function datetime",
            "StartTime -> Column datetime",
            "State -> Column string",
            // Total is not in the schema: it is the column summarize created
            "Total -> Column long"), refs);
    }

    @Test
    void functionCallsResolveToSignatures() {
        List<String> calls = new ArrayList<>();
        for (FunctionCallExpression call : CODE.syntax().getDescendants(FunctionCallExpression.class)) {
            calls.add(call.toString().trim() + " -> " + signature(call.referencedSignature())
                + " : " + describe(call.resultType()));
        }
        // built-in parameters carry type kinds (Summable, Orderable); the result type follows the argument
        assertEquals(List.of(
            "CostlyStorms(threshold) -> CostlyStorms(minDamage:Declared)"
                + " : table(State:string, EventType:string, DamageProperty:long, StartTime:datetime)",
            "sum(DamageProperty) -> sum(expr:Summable) : long",
            "max(StartTime) -> max(expr:Orderable) : datetime"), calls);
    }

    @Test
    void symbolsInScopeDependOnPosition() {
        // inside summarize's by-clause the input table's columns are in scope (what an editor would complete)
        int cursor = QUERY.indexOf("by State") + "by ".length();
        List<String> columns = new ArrayList<>();
        for (Symbol s : CODE.getSymbolsInScope(cursor)) {
            if (s instanceof ColumnSymbol) {
                columns.add(s.name());
            }
        }
        assertEquals(List.of("State", "EventType", "DamageProperty", "StartTime"), columns);
    }

    @Test
    void semanticErrorsComeFromBindingNotParsing() {
        String bad = "StormEvents | where Damage > 10 | extend x = strlen(DamageProperty)";
        KustoCode code = KustoCode.parseAndAnalyze(bad, globals());
        assertEquals(List.of(), code.getSyntaxDiagnostics());

        List<String> diagnostics = new ArrayList<>();
        for (Diagnostic d : code.getDiagnostics()) {
            diagnostics.add(d.code() + " '" + bad.substring(d.start(), d.start() + d.length()) + "': " + d.message());
        }
        assertEquals(List.of(
            "KS142 'Damage': The name 'Damage' does not refer to any known column, table, variable or function.",
            "KS141 'DamageProperty': The expression must have one of the types: string or dynamic."), diagnostics);
    }

    private static String signature(Signature sig) {
        List<String> params = new ArrayList<>();
        sig.parameters().forEach(p -> params.add(p.name() + ":" + p.typeKind()));
        return sig.symbol().name() + "(" + String.join(", ", params) + ")";
    }

    private static String describe(TypeSymbol type) {
        if (type instanceof TableSymbol t) {
            List<String> cols = new ArrayList<>();
            t.columns().forEach(c -> cols.add(c.name() + ":" + SchemaDisplay.getScalarTypeText(c.type())));
            return "table(" + String.join(", ", cols) + ")";
        }
        assertNotNull(type);
        return SchemaDisplay.getScalarTypeText(type);
    }
}
