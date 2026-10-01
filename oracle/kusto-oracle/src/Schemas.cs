using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using Kusto.Language;
using Kusto.Language.Symbols;

namespace Kusto.Oracle
{
    /// <summary>
    /// Builds GlobalState from schema files (porting/golden-format.md "Schema format").
    /// Database members are added in this fixed order, each list in file order:
    /// tables, functions, externalTables, materializedViews, entityGroups.
    /// </summary>
    public sealed class Schemas
    {
        private readonly string _dir;
        private readonly string _serverKind;
        private readonly Dictionary<string, GlobalState> _cache = new Dictionary<string, GlobalState>();

        public Schemas(string dir, string serverKind)
        {
            _dir = dir;
            _serverKind = serverKind;
        }

        public GlobalState Get(string schemaId)
        {
            var key = schemaId ?? "\0null";
            if (!_cache.TryGetValue(key, out var globals))
            {
                globals = schemaId == null ? GlobalState.Default : Build(schemaId);
                if (_serverKind != null && globals.ServerKind != _serverKind)
                    globals = globals.WithServerKind(_serverKind);
                _cache[key] = globals;
            }
            return globals;
        }

        private GlobalState Build(string schemaId)
        {
            if (_dir == null)
                throw new InvalidOperationException($"schema '{schemaId}' referenced but no --schemas directory given");
            var path = Path.Combine(_dir, schemaId + ".json");
            using var doc = JsonDocument.Parse(File.ReadAllBytes(path));
            return BuildFrom(doc.RootElement, path);
        }

        public static GlobalState BuildFrom(JsonElement root, string origin)
        {
            var cluster = Json.GetString(root, "cluster") ?? throw new FormatException(origin + ": missing cluster");
            var database = Json.GetString(root, "database") ?? throw new FormatException(origin + ": missing database");
            var members = new List<Symbol>();

            foreach (var t in Array(root, "tables"))
                members.Add(new TableSymbol(Json.GetString(t, "name"), Columns(t, origin), Json.GetString(t, "docstring")));

            foreach (var f in Array(root, "functions"))
            {
                var parameters = Json.GetString(f, "parameters");
                if (string.IsNullOrEmpty(parameters)) parameters = "()";
                members.Add(new FunctionSymbol(Json.GetString(f, "name"), parameters, Json.GetString(f, "body"), Json.GetString(f, "docstring")));
            }

            foreach (var t in Array(root, "externalTables"))
                members.Add(new ExternalTableSymbol(Json.GetString(t, "name"), Columns(t, origin), Json.GetString(t, "docstring")));

            foreach (var t in Array(root, "materializedViews"))
                members.Add(new MaterializedViewSymbol(Json.GetString(t, "name"), Columns(t, origin), Json.GetString(t, "query"), Json.GetString(t, "docstring")));

            foreach (var g in Array(root, "entityGroups"))
                members.Add(new EntityGroupSymbol(Json.GetString(g, "name"), Json.GetString(g, "definition"), Json.GetString(g, "docstring")));

            return GlobalState.Default
                .WithCluster(new ClusterSymbol(cluster, new DatabaseSymbol(database, members)))
                .WithDatabase(database);
        }

        private static IEnumerable<JsonElement> Array(JsonElement root, string name)
        {
            if (root.TryGetProperty(name, out var a) && a.ValueKind == JsonValueKind.Array)
                return a.EnumerateArray().ToList();
            return Enumerable.Empty<JsonElement>();
        }

        private static List<ColumnSymbol> Columns(JsonElement table, string origin)
        {
            var result = new List<ColumnSymbol>();
            foreach (var c in Array(table, "columns"))
            {
                var name = Json.GetString(c, "name");
                var typeName = Json.GetString(c, "type");
                var type = ScalarTypes.GetSymbol(typeName)
                    ?? throw new FormatException($"{origin}: unknown scalar type '{typeName}' for column '{name}'");
                result.Add(new ColumnSymbol(name, type));
            }
            return result;
        }
    }
}
