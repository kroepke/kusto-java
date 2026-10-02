using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Reflection;
using System.Text.Json;
using Kusto.Language;
using Kusto.Language.Symbols;
using Kusto.Language.Syntax;

namespace Kusto.Oracle
{
    /// <summary>
    /// globals: dumps the built-in catalogs of GlobalState.Default (and the static symbol lists
    /// behind them) as gzip JSONL, one record per symbol, in enumeration order. See README "globals".
    /// </summary>
    public static class Globals
    {
        public static int Run(string outPath, string upstream, string sourcesSha256)
        {
            var g = GlobalState.Default;
            var counts = new List<KeyValuePair<string, int>>();

            using var file = File.Create(outPath);
            using var gz = new GZipStream(file, CompressionLevel.Optimal);
            var nl = new byte[] { (byte)'\n' };
            void Emit(Action<Utf8JsonWriter> write)
            {
                gz.Write(Json.Render(write));
                gz.Write(nl);
            }

            Emit(w => WriteHeader(w, upstream, sourcesSha256, g.ServerKind));

            int Catalog<T>(string catalog, IReadOnlyList<T> list, Action<Utf8JsonWriter, T> body)
            {
                for (int i = 0; i < list.Count; i++)
                {
                    var item = list[i];
                    int index = i;
                    Emit(w =>
                    {
                        w.WriteStartObject();
                        Json.Str(w, "catalog", catalog);
                        w.WriteNumber("index", index);
                        body(w, item);
                        w.WriteEndObject();
                    });
                }
                counts.Add(new KeyValuePair<string, int>(catalog, list.Count));
                return list.Count;
            }

            Catalog("functions", g.Functions, WriteFunction);
            Catalog("aggregates", g.Aggregates, WriteFunction);
            Catalog("plugins", g.PlugIns, WriteFunction);
            Catalog("operators", g.Operators, WriteOperator);
            Catalog("queryOperatorParameters", QueryOperatorParameters.AllParameters, WriteQueryOperatorParameter);

            // Every other public static field of QueryOperatorParameters, in declaration order.
            int setCount = 0;
            foreach (var f in StaticFields(typeof(QueryOperatorParameters)))
            {
                if (f.Name == nameof(QueryOperatorParameters.AllParameters)) continue;
                var value = f.GetValue(null);
                IReadOnlyList<QueryOperatorParameter> items;
                bool isList;
                if (value is QueryOperatorParameter single) { items = new[] { single }; isList = false; }
                else if (value is IReadOnlyList<QueryOperatorParameter> l) { items = l; isList = true; }
                else continue;
                for (int i = 0; i < items.Count; i++)
                {
                    var item = items[i];
                    int index = i;
                    Emit(w =>
                    {
                        w.WriteStartObject();
                        Json.Str(w, "catalog", "queryOperatorParameterFields");
                        Json.Str(w, "field", f.Name);
                        w.WriteBoolean("isList", isList);
                        w.WriteNumber("index", index);
                        WriteQueryOperatorParameter(w, item);
                        w.WriteEndObject();
                    });
                    setCount++;
                }
                if (isList && items.Count == 0)
                {
                    // An empty list still gets a marker record so the field's existence is comparable.
                    Emit(w =>
                    {
                        w.WriteStartObject();
                        Json.Str(w, "catalog", "queryOperatorParameterFields");
                        Json.Str(w, "field", f.Name);
                        w.WriteBoolean("isList", true);
                        w.WriteNumber("index", -1);
                        w.WriteEndObject();
                    });
                    setCount++;
                }
            }
            counts.Add(new KeyValuePair<string, int>("queryOperatorParameterFields", setCount));

            var typeMap = TypeMap();
            Catalog("scalarTypes", ScalarTypes.All, (w, t) => WriteScalarType(w, t, typeMap));

            // Every public static ScalarSymbol field of ScalarTypes, in declaration order.
            var typeFields = StaticFields(typeof(ScalarTypes))
                .Where(f => typeof(ScalarSymbol).IsAssignableFrom(f.FieldType))
                .Select(f => (f.Name, (ScalarSymbol)f.GetValue(null)))
                .ToList();
            Catalog("scalarTypeFields", typeFields, (w, ft) =>
            {
                Json.Str(w, "field", ft.Item1);
                WriteScalarType(w, ft.Item2, typeMap);
            });

            Catalog("options", g.Options, WriteOption);

            gz.Flush();
            foreach (var kv in counts)
                Console.WriteLine($"{kv.Key}\t{kv.Value}");
            Console.Error.WriteLine($"globals: {counts.Sum(c => c.Value)} records -> {outPath}");
            return 0;
        }

        private static void WriteHeader(Utf8JsonWriter w, string upstream, string sourcesSha256, string serverKind)
        {
            w.WriteStartObject();
            w.WriteNumber("globals", 1);
            Json.Str(w, "upstream", upstream);
            w.WriteStartObject("oracle");
            Json.Str(w, "sourcesSha256", sourcesSha256);
            Json.Str(w, "runtime", "net10.0");
            Json.Str(w, "configuration", Golden.Configuration);
            w.WriteBoolean("invariantGlobalization", Golden.InvariantGlobalization);
            Json.Str(w, "tz", Golden.TimeZoneName);
            Json.Str(w, "serverKind", serverKind);
            w.WriteEndObject();
            Json.Str(w, "generatedAt", Golden.Now());
            w.WriteEndObject();
        }

        /// <summary>Public static fields in metadata (= declaration) order.</summary>
        private static IEnumerable<FieldInfo> StaticFields(Type t) =>
            t.GetFields(BindingFlags.Public | BindingFlags.Static).OrderBy(f => f.MetadataToken);

        private static Dictionary<ScalarSymbol, List<string>> TypeMap()
        {
            var field = typeof(ScalarTypes).GetField("s_typeMap", BindingFlags.NonPublic | BindingFlags.Static)
                ?? throw new InvalidOperationException("ScalarTypes.s_typeMap not found");
            var map = (Dictionary<string, ScalarSymbol>)field.GetValue(null);
            var result = new Dictionary<ScalarSymbol, List<string>>(ReferenceEqualityComparer.Instance);
            foreach (var kv in map)
            {
                if (!result.TryGetValue(kv.Value, out var keys)) result[kv.Value] = keys = new List<string>();
                keys.Add(kv.Key);
            }
            return result;
        }

        // ---------------------------------------------------------------- helpers

        private static void Enum<T>(Utf8JsonWriter w, string name, T value) where T : struct, System.Enum =>
            Json.Str(w, name, value.ToString());

        private static void StrList(Utf8JsonWriter w, string name, IEnumerable<string> values)
        {
            w.WritePropertyName(name);
            if (values == null) { w.WriteNullValue(); return; }
            w.WriteStartArray();
            foreach (var v in values) Json.Str(w, v);
            w.WriteEndArray();
        }

        private static string TypeText(TypeSymbol t) => t == null ? null : SchemaDisplay.GetText(t);
        private static string TypeDebug(Symbol t) => t == null ? null : DebugDisplay.GetText(t);

        private static string LayoutName(ParameterLayout layout)
        {
            if (layout == null) return null;
            if (ReferenceEquals(layout, ParameterLayouts.Fixed)) return "Fixed";
            if (ReferenceEquals(layout, ParameterLayouts.Repeating)) return "Repeating";
            if (ReferenceEquals(layout, ParameterLayouts.RepeatingSkipping)) return "RepeatingSkipping";
            if (ReferenceEquals(layout, ParameterLayouts.BlockRepeating)) return "BlockRepeating";
            return "Custom";
        }

        private static void WriteSymbolBase(Utf8JsonWriter w, Symbol s)
        {
            Enum(w, "kind", s.Kind);
            Json.Str(w, "name", s.Name);
            Json.Str(w, "alternateName", s.AlternateName);
            w.WriteBoolean("isHidden", s.IsHidden);
            Enum(w, "tabularity", s.Tabularity);
        }

        // ---------------------------------------------------------------- functions

        private static void WriteFunction(Utf8JsonWriter w, FunctionSymbol f)
        {
            WriteSymbolBase(w, f);
            Json.Str(w, "description", f.Description);
            w.WriteBoolean("isObsolete", f.IsObsolete);
            Json.Str(w, "alternative", f.Alternative);
            Json.Str(w, "optimizedAlternative", f.OptimizedAlternative);
            w.WriteBoolean("isConstantFoldable", f.IsConstantFoldable);
            w.WriteBoolean("isView", f.IsView);
            w.WriteBoolean("hasCustomAvailability", f.CustomAvailability != null);
            Enum(w, "resultNameKind", f.ResultNameKind);
            Json.Str(w, "resultNamePrefix", f.ResultNamePrefix);
            w.WriteNumber("minArgumentCount", f.MinArgumentCount);
            w.WriteNumber("maxArgumentCount", f.MaxArgumentCount);
            Json.Str(w, "display", SchemaDisplay.GetText(f));
            Json.Str(w, "debugDisplay", DebugDisplay.GetText(f));
            WriteSignatures(w, f.Signatures);
        }

        private static void WriteOperator(Utf8JsonWriter w, OperatorSymbol o)
        {
            WriteSymbolBase(w, o);
            Enum(w, "operatorKind", o.OperatorKind);
            Json.Str(w, "result", TypeDebug(o.Result));
            WriteSignatures(w, o.Signatures);
        }

        private static void WriteSignatures(Utf8JsonWriter w, IReadOnlyList<Signature> signatures)
        {
            w.WriteStartArray("signatures");
            foreach (var s in signatures)
                WriteSignature(w, s);
            w.WriteEndArray();
        }

        private static void WriteSignature(Utf8JsonWriter w, Signature s)
        {
            w.WriteStartObject();
            Enum(w, "returnKind", s.ReturnKind);
            Json.Str(w, "returnType", TypeText(s.DeclaredReturnType));
            Json.Str(w, "returnTypeDebug", TypeDebug(s.DeclaredReturnType));
            string defaultReturnType;
            try { defaultReturnType = TypeDebug(s.GetReturnType(GlobalState.Default)); }
            catch (Exception ex) { defaultReturnType = "!" + ex.GetType().Name; }
            Json.Str(w, "defaultReturnType", defaultReturnType);
            w.WriteBoolean("hasCustomReturnType", s.CustomReturnType != null);
            Json.Str(w, "body", s.Body);
            Json.Str(w, "declaration", s.Declaration?.ToString());
            Json.Str(w, "layout", LayoutName(s.Layout));
            Json.Str(w, "layoutType", s.Layout?.GetType().Name);
            w.WriteNumber("minArgumentCount", s.MinArgumentCount);
            w.WriteNumber("maxArgumentCount", s.MaxArgumentCount);
            w.WriteBoolean("isHidden", s.IsHidden);
            w.WriteBoolean("isObsolete", s.IsObsolete);
            Json.Str(w, "alternative", s.Alternative);
            w.WriteBoolean("hasRepeatableParameters", s.HasRepeatableParameters);
            w.WriteBoolean("hasOptionalParameters", s.HasOptionalParameters);
            w.WriteBoolean("hasAggregateParameters", s.HasAggregateParameters);
            Enum(w, "tabularity", s.Tabularity);
            w.WriteBoolean("isScalar", s.IsScalar);
            w.WriteBoolean("isTabular", s.IsTabular);
            w.WriteBoolean("allowsNamedArguments", s.AllowsNamedArguments);
            Json.Str(w, "parameterListDeclaration", Parameter.GetParameterListDeclaration(s.Parameters));
            w.WriteStartArray("parameters");
            foreach (var p in s.Parameters)
                WriteParameter(w, p);
            w.WriteEndArray();
            w.WriteEndObject();
        }

        private static void WriteParameter(Utf8JsonWriter w, Parameter p)
        {
            w.WriteStartObject();
            Json.Str(w, "name", p.Name);
            Enum(w, "typeKind", p.TypeKind);
            StrList(w, "declaredTypes", p.DeclaredTypes.Select(TypeText));
            StrList(w, "declaredTypesDebug", p.DeclaredTypes.Select(TypeDebug));
            Json.Str(w, "typeText", SchemaDisplay.GetParameterTypeText(p));
            Enum(w, "argumentKind", p.ArgumentKind);
            w.WriteNumber("minOccurring", p.MinOccurring);
            w.WriteNumber("maxOccurring", p.MaxOccurring);
            w.WriteBoolean("isOptional", p.IsOptional);
            w.WriteBoolean("isRepeatable", p.IsRepeatable);
            w.WriteBoolean("isCaseSensitive", p.IsCaseSensitive);
            StrList(w, "values", p.Values?.Select(Golden.DotNetStr));
            StrList(w, "valueTypes", p.Values?.Select(v => v?.GetType().Name));
            StrList(w, "examples", p.Examples);
            Json.Str(w, "defaultValueIndicator", p.DefaultValueIndicator);
            Json.Str(w, "defaultValue", p.DefaultValue?.ToString());
            Json.Str(w, "description", p.Description);
            Enum(w, "tabularity", p.Tabularity);
            w.WriteBoolean("typeDependsOnArguments", p.TypeDependsOnArguments);
            Json.Str(w, "declaration", Parameter.GetDeclaration(p));
            w.WriteEndObject();
        }

        // ---------------------------------------------------------------- query operator parameters

        private static void WriteQueryOperatorParameter(Utf8JsonWriter w, QueryOperatorParameter q)
        {
            Enum(w, "kind", q.Kind);
            Json.Str(w, "name", q.Name);
            StrList(w, "aliases", q.Aliases);
            Enum(w, "valueKind", q.ValueKind);
            w.WriteBoolean("isRepeatable", q.IsRepeatable);
            w.WriteBoolean("isCaseSensitive", q.IsCaseSensitive);
            StrList(w, "values", q.Values);
            w.WriteBoolean("isHidden", q.IsHidden);
            w.WriteBoolean("hasNoEquals", q.HasNoEquals);
        }

        // ---------------------------------------------------------------- scalar types

        private static void WriteScalarType(Utf8JsonWriter w, ScalarSymbol t, Dictionary<ScalarSymbol, List<string>> typeMap)
        {
            WriteSymbolBase(w, t);
            Json.Str(w, "display", SchemaDisplay.GetText(t));
            Json.Str(w, "debugDisplay", DebugDisplay.GetText(t));
            StrList(w, "aliases", t.Aliases);
            StrList(w, "typeMapKeys", typeMap.TryGetValue(t, out var keys) ? keys : new List<string>());
            w.WriteBoolean("isInteger", t.IsInteger);
            w.WriteBoolean("isNumeric", t.IsNumeric);
            w.WriteBoolean("isInterval", t.IsInterval);
            w.WriteBoolean("isSummable", t.IsSummable);
            w.WriteBoolean("isOrderable", t.IsOrderable);
            w.WriteBoolean("isMultiValue", t.IsMultiValue);
            StrList(w, "widerThan", ScalarTypes.All.Where(o => t.IsWiderThan(o)).Select(o => o.Name));
        }

        // ---------------------------------------------------------------- options

        private static void WriteOption(Utf8JsonWriter w, OptionSymbol o)
        {
            WriteSymbolBase(w, o);
            Json.Str(w, "description", o.Description);
            StrList(w, "types", o.Types.Select(TypeText));
            StrList(w, "examples", o.Examples);
        }
    }
}
