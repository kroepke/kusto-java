using System;
using System.Linq;
using System.Reflection;
using System.Text;
using System.Text.Json;
using Kusto.Language;
using Kusto.Language.Parsing;
using Kusto.Language.Syntax;

namespace Kusto.Oracle
{
    /// <summary>Answers the T0 questions (PLAN.md 5.1, PORTING.md 6/8, D10, D13).</summary>
    public static class Probe
    {
        public static void Run()
        {
            var json = Json.Render(w =>
            {
                w.WriteStartObject();

                // (a) D10: command text with no / full command parsers
                w.WriteStartObject("commandWithServerKind");
                foreach (var kind in new[] { "Unknown", ServerKinds.Engine })
                {
                    var globals = GlobalState.Default.WithServerKind(kind);
                    w.WriteStartObject(kind);
                    WriteAttempt(w, "parse", () => KustoCode.Parse(".foo", globals));
                    WriteAttempt(w, "parseAndAnalyze", () => KustoCode.ParseAndAnalyze(".foo", globals));
                    w.WriteEndObject();
                }
                w.WriteEndObject();

                // (b) command with a query tail on the default globals
                w.WriteStartObject("showTablesWhere");
                WriteAttempt(w, "parse", () => KustoCode.Parse(".show tables | where x == 1"));
                WriteAttempt(w, "parseAndAnalyze", () => KustoCode.ParseAndAnalyze(".show tables | where x == 1"));
                w.WriteEndObject();

                // (c) D13: SyntaxFacts.TryGetKind("")
                w.WriteStartObject("tryGetKindEmpty");
                try
                {
                    var ok = SyntaxFacts.TryGetKind("", out var k);
                    w.WriteBoolean("result", ok);
                    Json.Str(w, "kind", k.ToString());
                }
                catch (Exception ex) { Json.Str(w, "throws", ex.GetType().Name); }
                w.WriteEndObject();

                // (d) depth constants (private consts read by reflection)
                w.WriteStartObject("depthConstants");
                WriteConst(w, "QueryParser.MaxDepth", typeof(QueryParser), "MaxDepth");
                WriteConst(w, "ForwardParser.MaxCallDepth", typeof(ForwardParser<,>), "MaxCallDepth");
                w.WriteNumber("Properties.MaxAnalysisDepth", Properties.MaxAnalysisDepth.DefaultValue);
                w.WriteEndObject();

                // (e) nesting
                w.WriteStartObject("nesting");
                foreach (var n in new[] { 299, 300, 301, 498, 499, 500, 501, 1000 })
                {
                    var text = "print " + new string('(', n) + "1" + new string(')', n);
                    w.WriteStartObject(n.ToString());
                    WriteAttempt(w, "parse", () => KustoCode.Parse(text));
                    WriteAttempt(w, "parseAndAnalyze", () => KustoCode.ParseAndAnalyze(text));
                    var grammarGlobals = GlobalState.Default.WithParseOptions(GlobalState.Default.ParseOptions.WithParserKind(ParserKind.Grammar));
                    WriteAttempt(w, "parseGrammarParserKind", () => KustoCode.Parse(text, grammarGlobals));
                    w.WriteEndObject();
                }
                w.WriteEndObject();

                w.WriteEndObject();
            });
            Console.WriteLine(Encoding.UTF8.GetString(json));
        }

        private static void WriteConst(Utf8JsonWriter w, string label, Type type, string field)
        {
            var f = type.GetField(field, BindingFlags.Static | BindingFlags.NonPublic | BindingFlags.Public);
            if (f == null) { w.WriteNull(label); return; }
            var v = f.IsLiteral ? f.GetRawConstantValue() : f.GetValue(null);
            w.WriteNumber(label, Convert.ToInt32(v));
        }

        private static void WriteAttempt(Utf8JsonWriter w, string label, Func<KustoCode> fn)
        {
            w.WriteStartObject(label);
            try
            {
                var code = fn();
                w.WriteBoolean("throws", false);
                Json.Str(w, "kind", code.Kind);
                w.WriteBoolean("hasSemantics", code.HasSemantics);
                w.WriteNumber("treeDepth", code.MaxDepth);
                Json.Str(w, "rootKind", code.Syntax.Kind.ToString());
                w.WriteBoolean("roundTrip", code.Syntax.ToString(IncludeTrivia.All) == code.Text);
                w.WriteStartArray("diagnostics");
                foreach (var d in code.GetDiagnostics().Take(5))
                    Json.Str(w, d.Code + " " + d.Message);
                w.WriteEndArray();
            }
            catch (Exception ex)
            {
                w.WriteBoolean("throws", true);
                Json.Str(w, "exception", ex.GetType().Name);
                Json.Str(w, "message", ex.Message);
                var frames = (ex.StackTrace ?? "").Split('\n').Select(f => { f = f.Trim(); var k = f.IndexOf(" in /", StringComparison.Ordinal); if (k < 0) return f; var file = f.Substring(k + 4); var u = file.IndexOf("/src/Kusto.Language/", StringComparison.Ordinal); return f.Substring(0, k) + " in " + (u >= 0 ? file.Substring(u + 5) : System.IO.Path.GetFileName(file)); }).Where(f => f.Length > 0).ToList();
                Json.Str(w, "at", frames.FirstOrDefault());
                Json.Str(w, "firstUpstreamFrame", frames.FirstOrDefault(f => f.StartsWith("at Kusto.Language", StringComparison.Ordinal)));
            }
            w.WriteEndObject();
        }
    }
}
