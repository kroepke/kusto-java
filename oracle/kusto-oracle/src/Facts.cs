using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;
using Kusto.Language.Syntax;

namespace Kusto.Oracle
{
    /// <summary>
    /// dotnet-facts: evaluates .NET BCL behaviour the port must reproduce (PORTING.md 5.1/5.2).
    /// Cases: one "api&lt;TAB&gt;input" per line; '#' comments and blank lines ignored.
    /// An input starting with '[' is a JSON array of arguments, otherwise the raw text is the single argument.
    /// </summary>
    public static class Facts
    {
        private sealed class Fact
        {
            public bool Ok;
            public object Value;               // string, or JsonArray-able (List<object>) or null
            public List<(string, object)> Extras = new List<(string, object)>();
        }

        public static int Run(string casesPath, string outPath)
        {
            var lines = File.ReadAllLines(casesPath, new UTF8Encoding(false, true));
            var parts = new List<byte[]>();
            int lineNo = 0, failures = 0;

            foreach (var rawLine in lines)
            {
                lineNo++;
                var line = rawLine.TrimEnd('\r');
                if (line.Length == 0 || line.StartsWith("#", StringComparison.Ordinal)) continue;
                int tab = line.IndexOf('\t');
                if (tab < 0)
                {
                    Console.Error.WriteLine($"{casesPath}:{lineNo}: missing TAB");
                    failures++;
                    continue;
                }
                var api = line.Substring(0, tab);
                var input = line.Substring(tab + 1);
                List<string> args;
                try { args = ParseArgs(input); }
                catch (Exception ex)
                {
                    Console.Error.WriteLine($"{casesPath}:{lineNo}: bad input: {ex.Message}");
                    failures++;
                    continue;
                }

                Fact fact;
                try
                {
                    fact = Evaluate(api, args);
                }
                catch (UnknownApiException)
                {
                    Console.Error.WriteLine($"{casesPath}:{lineNo}: unknown api '{api}'");
                    failures++;
                    continue;
                }
                catch (Exception ex)
                {
                    fact = new Fact { Ok = false, Value = "!" + ex.GetType().Name };
                }

                parts.Add(Json.Render(w =>
                {
                    w.WriteStartObject();
                    Json.Str(w, "api", api);
                    Json.Str(w, "input", input);
                    w.WriteBoolean("ok", fact.Ok);
                    w.WritePropertyName("value");
                    WriteValue(w, fact.Value);
                    foreach (var (k, v) in fact.Extras)
                    {
                        w.WritePropertyName(k);
                        WriteValue(w, v);
                    }
                    w.WriteEndObject();
                }));
            }

            var header = Json.Render(w =>
            {
                w.WriteStartObject();
                Json.Str(w, "generatedAt", Golden.Now());
                Json.Str(w, "runtime", "net10.0");
                Json.Str(w, "runtimeVersion", Environment.Version.ToString());
                w.WriteBoolean("invariantGlobalization", Golden.InvariantGlobalization);
                Json.Str(w, "tz", Golden.TimeZoneName);
                Json.Str(w, "cases", "porting/dotnet-facts-cases.txt");
                w.WriteEndObject();
            });

            // One fact per line for readable diffs; the whole file is a single JSON object.
            using (var fs = File.Create(outPath))
            {
                fs.Write(header, 0, header.Length - 1); // drop closing '}'
                fs.Write(Encoding.UTF8.GetBytes(",\"facts\":[\n"));
                for (int i = 0; i < parts.Count; i++)
                {
                    fs.Write(parts[i]);
                    fs.Write(Encoding.UTF8.GetBytes(i + 1 < parts.Count ? ",\n" : "\n"));
                }
                fs.Write(Encoding.UTF8.GetBytes("]}\n"));
            }

            Console.Error.WriteLine($"facts: {parts.Count} facts -> {outPath}" + (failures > 0 ? $" ({failures} bad lines)" : ""));
            return failures > 0 ? 1 : 0;
        }

        private static void WriteValue(Utf8JsonWriter w, object v)
        {
            switch (v)
            {
                case null: w.WriteNullValue(); break;
                case string s: Json.Str(w, s); break;
                case bool b: w.WriteBooleanValue(b); break;
                case int i: w.WriteNumberValue(i); break;
                case long l: w.WriteNumberValue(l); break;
                case System.Collections.IEnumerable list:
                    w.WriteStartArray();
                    foreach (var item in list) WriteValue(w, item);
                    w.WriteEndArray();
                    break;
                default: Json.Str(w, Convert.ToString(v, CultureInfo.InvariantCulture)); break;
            }
        }

        private static List<string> ParseArgs(string input)
        {
            if (!input.StartsWith("[", StringComparison.Ordinal))
                return new List<string> { input };
            using var doc = JsonDocument.Parse(input);
            if (doc.RootElement.ValueKind != JsonValueKind.Array) throw new FormatException("not an array");
            return doc.RootElement.EnumerateArray().Select(Json.StringOf).ToList();
        }

        private sealed class UnknownApiException : Exception { }

        // ------------------------------------------------------------ evaluation

        private static Fact Ok(object value) => new Fact { Ok = true, Value = value };

        private static Fact Evaluate(string api, List<string> a)
        {
            string s0 = a.Count > 0 ? a[0] : null;
            switch (api)
            {
                // ---- char classification
                case "char.IsWhiteSpace": return CharPredicate(s0, char.IsWhiteSpace);
                case "char.IsDigit": return CharPredicate(s0, char.IsDigit);
                case "char.IsLetter": return CharPredicate(s0, char.IsLetter);
                case "char.IsLetterOrDigit": return CharPredicate(s0, char.IsLetterOrDigit);
                case "char.IsUpper": return CharPredicate(s0, char.IsUpper);
                case "char.IsLower": return CharPredicate(s0, char.IsLower);
                case "char.ToUpperInvariant": return CharMapping(s0, char.ToUpperInvariant);
                case "char.ToLowerInvariant": return CharMapping(s0, char.ToLowerInvariant);

                // ---- TryParse family (exact upstream overloads: X.TryParse(string, out X))
                case "Int32.TryParse": { var ok = Int32.TryParse(s0, out var r); return new Fact { Ok = ok, Value = r.ToString() }; }
                case "Int64.TryParse": { var ok = Int64.TryParse(s0, out var r); return new Fact { Ok = ok, Value = r.ToString() }; }
                case "Double.TryParse": { var ok = Double.TryParse(s0, out var r); return WithDouble(new Fact { Ok = ok }, r); }
                case "Decimal.TryParse": { var ok = Decimal.TryParse(s0, out var r); return new Fact { Ok = ok, Value = r.ToString() }; }
                case "TimeSpan.TryParse": { var ok = TimeSpan.TryParse(s0, out var r); return WithTimeSpan(new Fact { Ok = ok }, r); }
                case "DateTime.TryParse": { var ok = DateTime.TryParse(s0, out var r); return WithDateTime(new Fact { Ok = ok }, r); }
                case "Guid.TryParse": { var ok = Guid.TryParse(s0, out var r); return new Fact { Ok = ok, Value = r.ToString() }; }
                case "Boolean.TryParse": { var ok = Boolean.TryParse(s0, out var r); return new Fact { Ok = ok, Value = r.ToString() }; }

                // ---- formatting: input parsed invariantly, then default ToString()
                case "Double.ToString": return WithDouble(new Fact { Ok = true }, ParseDouble(s0));
                case "Decimal.ToString": return Ok(Decimal.Parse(s0, NumberStyles.Number | NumberStyles.AllowExponent, CultureInfo.InvariantCulture).ToString());
                case "Single.ToString":
                {
                    var f = Single.Parse(s0, NumberStyles.Float | NumberStyles.AllowThousands, CultureInfo.InvariantCulture);
                    var fact = Ok(f.ToString());
                    fact.Extras.Add(("bits", BitConverter.SingleToInt32Bits(f).ToString(CultureInfo.InvariantCulture)));
                    return fact;
                }
                case "TimeSpan.ToString": return WithTimeSpan(new Fact { Ok = true }, ParseTimeSpanArg(s0));
                case "DateTime.ToString": return WithDateTime(new Fact { Ok = true }, ParseDateTimeArg(s0));
                case "Guid.ToString": return Ok(Guid.Parse(s0).ToString());

                // ---- TimeSpan factories (double overloads, as upstream compiled against netstandard2.1)
                case "TimeSpan.FromSeconds": return WithTimeSpan(new Fact { Ok = true }, TimeSpan.FromSeconds(ParseDouble(s0)));
                case "TimeSpan.FromMinutes": return WithTimeSpan(new Fact { Ok = true }, TimeSpan.FromMinutes(ParseDouble(s0)));
                case "TimeSpan.FromHours": return WithTimeSpan(new Fact { Ok = true }, TimeSpan.FromHours(ParseDouble(s0)));
                case "TimeSpan.FromDays": return WithTimeSpan(new Fact { Ok = true }, TimeSpan.FromDays(ParseDouble(s0)));
                case "TimeSpan.FromMilliseconds": return WithTimeSpan(new Fact { Ok = true }, TimeSpan.FromMilliseconds(ParseDouble(s0)));
                case "TimeSpan.FromTicks": return WithTimeSpan(new Fact { Ok = true }, TimeSpan.FromTicks(Int64.Parse(s0, CultureInfo.InvariantCulture)));
                case "Convert.ToInt64Cast":
                {
                    double d = ParseDouble(s0);
                    long l = (long)d; // .NET 9+: saturating conversion, NaN -> 0
                    return Ok(l.ToString(CultureInfo.InvariantCulture));
                }

                // ---- strings
                case "String.Compare":
                {
                    int r;
                    if (a.Count == 2) r = string.Compare(a[0], a[1]);
                    else if (a.Count == 5) r = string.Compare(a[0], Int(a[1]), a[2], Int(a[3]), Int(a[4]));
                    else if (a.Count == 6) r = string.Compare(a[0], Int(a[1]), a[2], Int(a[3]), Int(a[4]), ignoreCase: Bool(a[5]));
                    else throw new FormatException("String.Compare takes [a,b], [a,ia,b,ib,len] or [a,ia,b,ib,len,ignoreCase]");
                    return WithSign(Ok(r), r);
                }
                case "String.CompareOrdinalIgnoreCase": { var r = string.Compare(a[0], a[1], StringComparison.OrdinalIgnoreCase); return WithSign(Ok(r), r); }
                case "String.EqualsOrdinalIgnoreCase": return Ok(string.Equals(a[0], a[1], StringComparison.OrdinalIgnoreCase));
                case "String.ToUpperInvariant": return Ok(s0.ToUpperInvariant());
                case "String.ToLowerInvariant": return Ok(s0.ToLowerInvariant());
                case "String.Trim": return Ok(s0.Trim());
                case "String.IsNullOrWhiteSpace": return Ok(string.IsNullOrWhiteSpace(s0));
                case "String.Split":
                {
                    if (a.Count != 2 || a[1].Length != 1) throw new FormatException("String.Split takes [s, \"c\"]");
                    return Ok(s0.Split(a[1][0]).ToList());
                }

                // ---- Convert.ChangeType [value, targetType, sourceType?]
                case "Convert.ChangeType":
                {
                    var source = ParseAs(a[0], a.Count > 2 ? a[2] : "String");
                    var target = TypeOf(a[1]);
                    var result = Convert.ChangeType(source, target);
                    var fact = Ok(result?.ToString());
                    fact.Extras.Add(("type", result?.GetType().Name));
                    if (result is double d) fact.Extras.Add(("bits", BitConverter.DoubleToInt64Bits(d).ToString(CultureInfo.InvariantCulture)));
                    return fact;
                }

                // ---- library
                case "SyntaxFacts.TryGetKind": { var ok = SyntaxFacts.TryGetKind(s0, out var kind); return new Fact { Ok = ok, Value = kind.ToString() }; }

                default: throw new UnknownApiException();
            }
        }

        private static int Int(string s) => Int32.Parse(s, CultureInfo.InvariantCulture);
        private static bool Bool(string s) => Boolean.Parse(s);

        private static double ParseDouble(string s) =>
            Double.Parse(s, NumberStyles.Float | NumberStyles.AllowThousands, CultureInfo.InvariantCulture);

        /// <summary>Integer text = ticks; otherwise TimeSpan.Parse (invariant).</summary>
        private static TimeSpan ParseTimeSpanArg(string s) =>
            Int64.TryParse(s, NumberStyles.AllowLeadingSign, CultureInfo.InvariantCulture, out var ticks)
                ? TimeSpan.FromTicks(ticks)
                : TimeSpan.Parse(s, CultureInfo.InvariantCulture);

        /// <summary>Integer text = ticks (Kind Unspecified); otherwise DateTime.Parse (default overload, as upstream TryParse).</summary>
        private static DateTime ParseDateTimeArg(string s) =>
            Int64.TryParse(s, NumberStyles.AllowLeadingSign, CultureInfo.InvariantCulture, out var ticks)
                ? new DateTime(ticks)
                : DateTime.Parse(s);

        private static object ParseAs(string s, string typeName)
        {
            switch (typeName)
            {
                case "String": return s;
                case "Int32": return Int32.Parse(s, CultureInfo.InvariantCulture);
                case "Int64": return Int64.Parse(s, NumberStyles.AllowLeadingSign, CultureInfo.InvariantCulture);
                case "Double": return ParseDouble(s);
                case "Decimal": return Decimal.Parse(s, NumberStyles.Number | NumberStyles.AllowExponent, CultureInfo.InvariantCulture);
                case "Boolean": return Boolean.Parse(s);
                case "TimeSpan": return ParseTimeSpanArg(s);
                case "DateTime": return ParseDateTimeArg(s);
                case "Guid": return Guid.Parse(s);
                case "Null": return null;
                default: throw new FormatException("unknown source type " + typeName);
            }
        }

        private static Type TypeOf(string typeName)
        {
            switch (typeName)
            {
                case "String": return typeof(string);
                case "Int32": return typeof(int);
                case "Int64": return typeof(long);
                case "Double": return typeof(double);
                case "Decimal": return typeof(decimal);
                case "Boolean": return typeof(bool);
                case "TimeSpan": return typeof(TimeSpan);
                case "DateTime": return typeof(DateTime);
                case "Guid": return typeof(Guid);
                default: throw new FormatException("unknown target type " + typeName);
            }
        }

        private static Fact WithSign(Fact f, int r)
        {
            f.Extras.Add(("sign", Math.Sign(r)));
            return f;
        }

        private static Fact WithDouble(Fact f, double d)
        {
            f.Value = d.ToString();
            f.Extras.Add(("bits", BitConverter.DoubleToInt64Bits(d).ToString(CultureInfo.InvariantCulture)));
            return f;
        }

        private static Fact WithTimeSpan(Fact f, TimeSpan t)
        {
            f.Value = t.ToString();
            f.Extras.Add(("ticks", t.Ticks));
            return f;
        }

        private static Fact WithDateTime(Fact f, DateTime t)
        {
            f.Value = t.ToString();
            f.Extras.Add(("ticks", t.Ticks));
            f.Extras.Add(("kind", t.Kind.ToString()));
            return f;
        }

        /// <summary>Input "table": inclusive [start,end] ranges over U+0000..U+FFFF where true. Otherwise: the predicate on the single char.</summary>
        private static Fact CharPredicate(string input, Func<char, bool> pred)
        {
            if (input == "table")
            {
                var ranges = new List<object>();
                int start = -1;
                for (int cp = 0; cp <= 0x10000; cp++)
                {
                    bool v = cp <= 0xFFFF && pred((char)cp);
                    if (v && start < 0) start = cp;
                    else if (!v && start >= 0) { ranges.Add(new List<object> { start, cp - 1 }); start = -1; }
                }
                return Ok(ranges);
            }
            if (input.Length != 1) throw new FormatException("expected 'table' or a single UTF-16 code unit");
            return Ok(pred(input[0]));
        }

        /// <summary>Input "table": [cp, mapped] for every code unit whose mapping differs. Otherwise: the mapped single char.</summary>
        private static Fact CharMapping(string input, Func<char, char> map)
        {
            if (input == "table")
            {
                var pairs = new List<object>();
                for (int cp = 0; cp <= 0xFFFF; cp++)
                {
                    var m = map((char)cp);
                    if (m != cp) pairs.Add(new List<object> { cp, (int)m });
                }
                return Ok(pairs);
            }
            if (input.Length != 1) throw new FormatException("expected 'table' or a single UTF-16 code unit");
            return Ok(map(input[0]).ToString());
        }
    }
}
