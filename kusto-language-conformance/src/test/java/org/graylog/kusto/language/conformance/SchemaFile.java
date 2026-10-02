// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: model of schemas/<id>.json (golden-format.md "Schema format").

package org.graylog.kusto.language.conformance;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * A schema file. Database members are applied in field order (tables, functions, externalTables,
 * materializedViews, entityGroups), each list in file order (oracle/README.md "Schema files").
 * {@code notes} is free-form JSON for humans (a string or an object).
 */
public record SchemaFile(
        String id,
        String cluster,
        String database,
        List<Table> tables,
        List<Function> functions,
        List<Table> externalTables,
        List<MaterializedView> materializedViews,
        List<EntityGroup> entityGroups,
        JsonNode notes) {

    public SchemaFile {
        tables = tables == null ? List.of() : List.copyOf(tables);
        functions = functions == null ? List.of() : List.copyOf(functions);
        externalTables = externalTables == null ? List.of() : List.copyOf(externalTables);
        materializedViews = materializedViews == null ? List.of() : List.copyOf(materializedViews);
        entityGroups = entityGroups == null ? List.of() : List.copyOf(entityGroups);
    }

    /** Column; {@code type} is a KQL scalar type name accepted by {@code ScalarTypes.GetSymbol}. */
    public record Column(String name, String type) {
    }

    /** Table or external table. */
    public record Table(String name, List<Column> columns, String docstring) {
        public Table {
            columns = columns == null ? List.of() : List.copyOf(columns);
        }
    }

    /** Function; {@code parameters} like {@code "(a: long)"}, null or empty meaning {@code "()"}. */
    public record Function(String name, String parameters, String body, String docstring) {
    }

    public record MaterializedView(String name, List<Column> columns, String query, String docstring) {
        public MaterializedView {
            columns = columns == null ? List.of() : List.copyOf(columns);
        }
    }

    public record EntityGroup(String name, String definition, String docstring) {
    }
}
