using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Kusto.Language;
using Kusto.Language.Editor;
using Kusto.Language.Parsing;
using Kusto.Language.Symbols;
using Kusto.Language.Syntax;

namespace Kusto.Oracle
{
    /// <summary>One corpus line.</summary>
    public sealed class CorpusRecord
    {
        public string Id;
        public string Text;
        public string Schema;
        public string Source;

        public static IEnumerable<CorpusRecord> Read(string path)
        {
            int lineNo = 0;
            foreach (var line in File.ReadLines(path, new UTF8Encoding(false, true)))
            {
                lineNo++;
                if (line.Length == 0) continue;
                using var doc = JsonDocument.Parse(line);
                var r = doc.RootElement;
                var rec = new CorpusRecord
                {
                    Id = Json.GetString(r, "id"),
                    Text = Json.GetString(r, "text"),
                    Schema = Json.GetString(r, "schema"),
                    Source = Json.GetString(r, "source"),
                };
                if (rec.Id == null || rec.Text == null)
                    throw new FormatException($"{path}:{lineNo}: corpus line needs 'id' and 'text'");
                yield return rec;
            }
        }
    }

    /// <summary>Everything the oracle observes for one input text.</summary>
    public sealed class Observation
    {
        public KustoCode ParseCode;     // KustoCode.Parse result, or null if it threw
        public KustoCode AnalyzedCode;  // KustoCode.ParseAndAnalyze result, or null if it threw
        public string ParseOutcome = "ok";
        public string AnalyzeOutcome = "ok";
        public double ParseMs;
        public double AnalyzeMs;

        /// <summary>The code used for tokens/tree/syntax diagnostics: analysed if available, else parse-only.</summary>
        public KustoCode Code => AnalyzedCode ?? ParseCode;

        public static Observation Run(string text, GlobalState globals)
        {
            var o = new Observation();
            var sw = Stopwatch.StartNew();
            try { o.ParseCode = KustoCode.Parse(text, globals); }
            catch (Exception ex) { o.ParseOutcome = ExceptionMap.Throw(ex); }
            o.ParseMs = sw.Elapsed.TotalMilliseconds;

            sw.Restart();
            try
            {
                o.AnalyzedCode = KustoCode.ParseAndAnalyze(text, globals);
                if (!o.AnalyzedCode.HasSemantics) o.AnalyzeOutcome = "skipped:depth";
            }
            catch (Exception ex) { o.AnalyzeOutcome = ExceptionMap.Throw(ex); }
            o.AnalyzeMs = sw.Elapsed.TotalMilliseconds;
            return o;
        }
    }

    public static class Golden
    {
        public static void Dump(string corpusPath, Schemas schemas, string outPath, string upstream, string sourcesSha256, string serverKind)
        {
            using var file = File.Create(outPath);
            using var gz = new GZipStream(file, CompressionLevel.Optimal);
            var nl = new byte[] { (byte)'\n' };

            gz.Write(Json.Render(w => WriteHeader(w, upstream, sourcesSha256, serverKind)));
            gz.Write(nl);

            int count = 0;
            foreach (var rec in CorpusRecord.Read(corpusPath))
            {
                var globals = schemas.Get(rec.Schema);
                var obs = Observation.Run(rec.Text, globals);
                byte[] line;
                try
                {
                    line = Json.Render(w => WriteRecord(w, rec, obs));
                }
                catch (Exception ex)
                {
                    throw new InvalidOperationException($"oracle failed to serialise record {rec.Id}: {ex}", ex);
                }
                gz.Write(line);
                gz.Write(nl);
                count++;
            }
            Console.Error.WriteLine($"dump: {count} records -> {outPath}");
        }

        public static string Configuration =>
#if DEBUG
            "Debug";
#else
            "Release";
#endif

        public static bool InvariantGlobalization =>
            AppContext.TryGetSwitch("System.Globalization.Invariant", out var on) && on;

        public static string TimeZoneName
        {
            get
            {
                var tz = TimeZoneInfo.Local;
                return tz.BaseUtcOffset == TimeSpan.Zero && !tz.SupportsDaylightSavingTime ? "UTC" : tz.Id;
            }
        }

        public static string Now() => DateTime.UtcNow.ToString("yyyy-MM-dd'T'HH:mm:ss'Z'", CultureInfo.InvariantCulture);

        private static void WriteHeader(Utf8JsonWriter w, string upstream, string sourcesSha256, string serverKind)
        {
            w.WriteStartObject();
            w.WriteNumber("golden", 1);
            Json.Str(w, "upstream", upstream);
            w.WriteStartObject("oracle");
            Json.Str(w, "sourcesSha256", sourcesSha256);
            Json.Str(w, "runtime", "net10.0");
            Json.Str(w, "configuration", Configuration);
            w.WriteBoolean("invariantGlobalization", InvariantGlobalization);
            Json.Str(w, "tz", TimeZoneName);
            Json.Str(w, "serverKind", serverKind);
            w.WriteEndObject();
            Json.Str(w, "generatedAt", Now());
            w.WriteEndObject();
        }

        // ---------------------------------------------------------------- record

        private sealed class TreeEntry
        {
            public SyntaxElement Element;
            public int Parent;
            public string Name;
            public int Depth;
        }

        /// <summary>Pre-order over every element reachable through GetChild (null children skipped).</summary>
        private static List<TreeEntry> PreOrder(SyntaxNode root)
        {
            var result = new List<TreeEntry>();
            if (root == null) return result;
            var stack = new Stack<TreeEntry>();
            stack.Push(new TreeEntry { Element = root, Parent = -1, Name = "", Depth = 0 });
            while (stack.Count > 0)
            {
                var e = stack.Pop();
                int index = result.Count;
                result.Add(e);
                var el = e.Element;
                for (int c = el.ChildCount - 1; c >= 0; c--)
                {
                    var child = el.GetChild(c);
                    if (child != null)
                        stack.Push(new TreeEntry { Element = child, Parent = index, Name = el.GetName(c) ?? "", Depth = e.Depth + 1 });
                }
            }
            return result;
        }

        public static void WriteRecord(Utf8JsonWriter w, CorpusRecord rec, Observation obs)
        {
            var code = obs.Code;
            var root = code?.Syntax;
            string tokenValuesOutcome = "ok";

            w.WriteStartObject();
            Json.Str(w, "id", rec.Id);
            string kind;
            try { kind = code?.Kind ?? KustoCode.GetKind(rec.Text); }
            catch (Exception) { kind = "Unknown"; }
            Json.Str(w, "kind", kind);

            // tokens
            w.WriteStartArray("tokens");
            if (code != null)
            {
                int pos = 0;
                foreach (var lt in code.GetLexicalTokens())
                {
                    int triviaStart = pos;
                    int start = pos + lt.Trivia.Length;
                    int end = start + lt.Text.Length;
                    pos = end;

                    w.WriteStartObject();
                    Json.Str(w, "kind", lt.Kind.ToString());
                    w.WriteNumber("triviaStart", triviaStart);
                    w.WriteNumber("start", start);
                    w.WriteNumber("end", end);
                    Json.Str(w, "trivia", lt.Trivia);
                    Json.Str(w, "text", lt.Text);
                    string value = null;
                    try
                    {
                        var st = SyntaxToken.From(lt);
                        if (st.IsLiteral)
                            value = DotNetStr(st.Value);
                    }
                    catch (Exception ex)
                    {
                        value = "!" + ex.GetType().Name;
                        if (tokenValuesOutcome == "ok") tokenValuesOutcome = ExceptionMap.Throw(ex);
                    }
                    Json.Str(w, "value", value);
                    w.WriteStartArray("diagnostics");
                    foreach (var d in lt.Diagnostics)
                    {
                        int dStart, dLength;
                        switch (d.LocationKind)
                        {
                            case DiagnosticLocationKind.Absolute: dStart = d.Start; dLength = d.Length; break;
                            case DiagnosticLocationKind.RelativeEnd: dStart = end; dLength = 0; break;
                            default: dStart = start; dLength = end - start; break;
                        }
                        WriteDiagnostic(w, d, dStart, dLength);
                    }
                    w.WriteEndArray();
                    w.WriteEndObject();
                }
            }
            w.WriteEndArray();

            // fidelity
            w.WriteStartObject("fidelity");
            w.WriteBoolean("roundTrip", root != null && root.ToString(IncludeTrivia.All) == rec.Text);
            w.WriteNumber("fullWidth", root?.FullWidth ?? 0);
            w.WriteEndObject();

            // tree
            var tree = PreOrder(root);
            w.WriteStartArray("tree");
            for (int i = 0; i < tree.Count; i++)
            {
                var e = tree[i];
                w.WriteStartObject();
                w.WriteNumber("i", i);
                Json.Str(w, "kind", e.Element.Kind.ToString());
                w.WriteNumber("depth", e.Depth);
                w.WriteNumber("parent", e.Parent);
                Json.Str(w, "name", e.Name);
                w.WriteNumber("start", e.Element.TextStart);
                w.WriteNumber("end", e.Element.End);
                w.WriteBoolean("missing", e.Element.IsMissing);
                w.WriteEndObject();
            }
            w.WriteEndArray();

            // diagnostics
            var syntaxDx = code != null ? code.GetSyntaxDiagnostics() : (IReadOnlyList<Diagnostic>)Array.Empty<Diagnostic>();
            w.WriteStartArray("syntaxDiagnostics");
            foreach (var d in syntaxDx) WriteDiagnostic(w, d, d.Start, d.Length);
            w.WriteEndArray();

            w.WriteStartArray("semanticDiagnostics");
            var analyzed = obs.AnalyzedCode;
            if (analyzed != null)
            {
                var syntaxSet = new List<Diagnostic>(syntaxDx);
                foreach (var d in analyzed.GetDiagnostics())
                {
                    if (syntaxSet.Any(s => ReferenceEquals(s, d) || s.Equals(d))) continue;
                    WriteDiagnostic(w, d, d.Start, d.Length);
                }
            }
            w.WriteEndArray();

            // bind
            w.WriteStartArray("bind");
            if (analyzed != null && analyzed.HasSemantics)
            {
                var globals = analyzed.Globals;
                for (int i = 0; i < tree.Count; i++)
                {
                    if (!(tree[i].Element is SyntaxNode node)) continue;
                    var sym = node.ReferencedSymbol;
                    var expr = node as Expression;
                    var type = expr?.ResultType;
                    if (sym == null && type == null) continue;

                    w.WriteStartObject();
                    w.WriteNumber("i", i);
                    Json.Str(w, "symbolKind", sym?.Kind.ToString());
                    Json.Str(w, "symbol", sym?.Name);
                    Json.Str(w, "symbolOwner", OwnerOf(sym, globals));
                    Json.Str(w, "type", type != null ? SchemaDisplay.GetText(type) : null);
                    Json.Str(w, "signature", RenderSignature(node.ReferencedSignature));
                    bool isConstant = expr != null && expr.IsConstant;
                    w.WriteBoolean("isConstant", isConstant);
                    Json.Str(w, "constantValue", isConstant ? DotNetStr(expr.ConstantValue) : null);
                    var body = node.GetCalledFunctionBody();
                    Json.Str(w, "calledBody", body != null ? Sha16(body.ToString()) : null);
                    w.WriteNumber("alternates", node.Alternates?.Count ?? 0);
                    w.WriteEndObject();
                }
            }
            w.WriteEndArray();

            // resultType
            var rt = analyzed != null && analyzed.HasSemantics ? analyzed.ResultType : null;
            Json.Str(w, "resultType", rt != null ? SchemaDisplay.GetText(rt) : null);

            // outcome
            w.WriteStartObject("outcome");
            Json.Str(w, "parse", obs.ParseOutcome);
            Json.Str(w, "analyze", obs.AnalyzeOutcome);
            Json.Str(w, "tokenValues", tokenValuesOutcome);
            w.WriteEndObject();

            // timing (ignored by the comparator)
            w.WriteStartObject("timing");
            w.WriteNumber("parseMs", Math.Round(obs.ParseMs, 3));
            w.WriteNumber("analyzeMs", Math.Round(obs.AnalyzeMs, 3));
            w.WriteEndObject();

            w.WriteEndObject();
        }

        private static void WriteDiagnostic(Utf8JsonWriter w, Diagnostic d, int start, int length)
        {
            w.WriteStartObject();
            Json.Str(w, "code", d.Code);
            Json.Str(w, "severity", d.Severity);
            w.WriteNumber("start", start);
            w.WriteNumber("length", length);
            Json.Str(w, "message", d.Message);
            w.WriteEndObject();
        }

        /// <summary>.NET default ToString() under invariant globalization; null stays null.</summary>
        public static string DotNetStr(object value) => value?.ToString();

        /// <summary>
        /// Column -> GetTable(column); Database -> GetCluster(database); any other symbol -> GetDatabase(symbol).
        /// Null when GlobalState does not know the owner.
        /// </summary>
        public static string OwnerOf(Symbol sym, GlobalState globals)
        {
            switch (sym)
            {
                case null: return null;
                case ColumnSymbol c: return globals.GetTable(c)?.Name;
                case DatabaseSymbol d: return globals.GetCluster(d)?.Name;
                default: return globals.GetDatabase(sym)?.Name;
            }
        }

        /// <summary>
        /// "(" + join(", ", name: type) + ") -> " + return. Parameter name via KustoFacts.BracketNameIfNecessary,
        /// type via SchemaDisplay.GetParameterTypeText (same as SchemaDisplay.GetText(FunctionSymbol)).
        /// Return = SchemaDisplay.GetText(DeclaredReturnType) when ReturnKind is Declared, else the ReturnKind name.
        /// </summary>
        public static string RenderSignature(Signature sig)
        {
            if (sig == null) return null;
            var ps = string.Join(", ", sig.Parameters.Select(p =>
                KustoFacts.BracketNameIfNecessary(p.Name) + ": " + SchemaDisplay.GetParameterTypeText(p)));
            var ret = sig.ReturnKind == ReturnTypeKind.Declared && sig.DeclaredReturnType != null
                ? SchemaDisplay.GetText(sig.DeclaredReturnType)
                : sig.ReturnKind.ToString();
            return "(" + ps + ") -> " + ret;
        }

        public static string Sha16(string text)
        {
            var hash = SHA256.HashData(Encoding.UTF8.GetBytes(text));
            return Convert.ToHexString(hash).ToLowerInvariant().Substring(0, 16);
        }

        // ---------------------------------------------------------------- census

        public static void Census(string corpusPath, Schemas schemas)
        {
            int records = 0, parseThrows = 0, analyzeThrows = 0, analyzeSkippedDepth = 0, tokenValueThrows = 0, roundTripFalse = 0, commands = 0;
            var exceptionCounts = new SortedDictionary<string, int>(StringComparer.Ordinal);
            var examples = new List<string>();

            foreach (var rec in CorpusRecord.Read(corpusPath))
            {
                records++;
                var obs = Observation.Run(rec.Text, schemas.Get(rec.Schema));
                var code = obs.Code;
                bool interesting = false;

                if (obs.ParseOutcome != "ok") { parseThrows++; Bump(exceptionCounts, "parse:" + obs.ParseOutcome); interesting = true; }
                if (obs.AnalyzeOutcome.StartsWith("throw:", StringComparison.Ordinal)) { analyzeThrows++; Bump(exceptionCounts, "analyze:" + obs.AnalyzeOutcome); interesting = true; }
                if (obs.AnalyzeOutcome == "skipped:depth") analyzeSkippedDepth++;
                if (code != null)
                {
                    if (code.Kind == CodeKinds.Command) commands++;
                    bool valueThrew = false;
                    foreach (var lt in code.GetLexicalTokens())
                    {
                        try
                        {
                            var st = SyntaxToken.From(lt);
                            if (st.IsLiteral) { var _ = st.Value; }
                        }
                        catch (Exception ex)
                        {
                            if (!valueThrew) Bump(exceptionCounts, "tokenValue:" + ExceptionMap.Throw(ex));
                            valueThrew = true;
                        }
                    }
                    if (valueThrew) { tokenValueThrows++; interesting = true; }
                    if (code.Syntax.ToString(IncludeTrivia.All) != rec.Text) { roundTripFalse++; interesting = true; }
                }
                else
                {
                    roundTripFalse++;
                }
                if (interesting && examples.Count < 20) examples.Add(rec.Id);
            }

            var json = Json.Render(w =>
            {
                w.WriteStartObject();
                Json.Str(w, "corpus", corpusPath);
                w.WriteNumber("records", records);
                w.WriteNumber("commands", commands);
                w.WriteNumber("parseThrows", parseThrows);
                w.WriteNumber("analyzeThrows", analyzeThrows);
                w.WriteNumber("analyzeSkippedDepth", analyzeSkippedDepth);
                w.WriteNumber("tokenValueThrows", tokenValueThrows);
                w.WriteNumber("roundTripFalse", roundTripFalse);
                w.WriteStartObject("exceptions");
                foreach (var kv in exceptionCounts) w.WriteNumber(kv.Key, kv.Value);
                w.WriteEndObject();
                w.WriteStartArray("exampleIds");
                foreach (var id in examples) Json.Str(w, id);
                w.WriteEndArray();
                w.WriteEndObject();
            });
            Console.WriteLine(Encoding.UTF8.GetString(json));
        }

        private static void Bump(IDictionary<string, int> d, string key)
        {
            d.TryGetValue(key, out var n);
            d[key] = n + 1;
        }
    }
}
