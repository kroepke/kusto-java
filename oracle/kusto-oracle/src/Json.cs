using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Text.Json;

namespace Kusto.Oracle
{
    /// <summary>
    /// JSON helpers that pin the golden string escaping (porting/golden-format.md "Record"):
    /// '"' and '\' are backslash-escaped, every code unit below U+0020 and every lone surrogate
    /// is written as \uXXXX with upper-case hex, everything else (including U+007F, U+2028,
    /// U+2029 and valid surrogate pairs) is written raw as UTF-8. No short escapes (\n, \t, ...).
    /// </summary>
    public static class Json
    {
        public static JsonWriterOptions WriterOptions => new JsonWriterOptions { Indented = false, SkipValidation = false };

        public static string Escape(string s)
        {
            var sb = new StringBuilder(s.Length + 2);
            sb.Append('"');
            for (int i = 0; i < s.Length; i++)
            {
                char c = s[i];
                if (c == '"') sb.Append("\\\"");
                else if (c == '\\') sb.Append("\\\\");
                else if (c < 0x20) AppendU(sb, c);
                else if (char.IsHighSurrogate(c))
                {
                    if (i + 1 < s.Length && char.IsLowSurrogate(s[i + 1]))
                    {
                        sb.Append(c).Append(s[i + 1]);
                        i++;
                    }
                    else AppendU(sb, c);
                }
                else if (char.IsLowSurrogate(c)) AppendU(sb, c);
                else sb.Append(c);
            }
            sb.Append('"');
            return sb.ToString();
        }

        private static void AppendU(StringBuilder sb, char c)
        {
            sb.Append("\\u").Append(((int)c).ToString("X4", System.Globalization.CultureInfo.InvariantCulture));
        }

        /// <summary>Encodes a string that may contain lone surrogates: they become the escape text, the rest UTF-8.</summary>
        private static byte[] ToUtf8(string escaped)
        {
            // Escape() leaves no lone surrogates, so strict UTF-8 is safe.
            return Encoding.UTF8.GetBytes(escaped);
        }

        public static void Str(Utf8JsonWriter w, string value)
        {
            if (value == null) w.WriteNullValue();
            else w.WriteRawValue(ToUtf8(Escape(value)), skipInputValidation: true);
        }

        public static void Str(Utf8JsonWriter w, string name, string value)
        {
            w.WritePropertyName(name);
            Str(w, value);
        }

        /// <summary>
        /// Decodes the raw text of a JSON string token (including quotes) without rejecting lone
        /// surrogates (System.Text.Json's GetString() throws on them).
        /// </summary>
        public static string Unescape(string raw)
        {
            if (raw.Length < 2 || raw[0] != '"' || raw[raw.Length - 1] != '"')
                throw new FormatException("not a JSON string: " + raw);
            var sb = new StringBuilder(raw.Length);
            for (int i = 1; i < raw.Length - 1; i++)
            {
                char c = raw[i];
                if (c != '\\') { sb.Append(c); continue; }
                char e = raw[++i];
                switch (e)
                {
                    case '"': sb.Append('"'); break;
                    case '\\': sb.Append('\\'); break;
                    case '/': sb.Append('/'); break;
                    case 'b': sb.Append('\b'); break;
                    case 'f': sb.Append('\f'); break;
                    case 'n': sb.Append('\n'); break;
                    case 'r': sb.Append('\r'); break;
                    case 't': sb.Append('\t'); break;
                    case 'u':
                        sb.Append((char)Convert.ToInt32(raw.Substring(i + 1, 4), 16));
                        i += 4;
                        break;
                    default: throw new FormatException("bad escape \\" + e);
                }
            }
            return sb.ToString();
        }

        /// <summary>String value of a property, lone-surrogate safe; null for JSON null or absent.</summary>
        public static string GetString(JsonElement obj, string name)
        {
            if (obj.ValueKind != JsonValueKind.Object || !obj.TryGetProperty(name, out var p) || p.ValueKind == JsonValueKind.Null)
                return null;
            if (p.ValueKind != JsonValueKind.String)
                throw new FormatException($"property '{name}' is not a string");
            return Unescape(p.GetRawText());
        }

        public static string StringOf(JsonElement e)
        {
            if (e.ValueKind == JsonValueKind.Null) return null;
            if (e.ValueKind != JsonValueKind.String) return e.GetRawText();
            return Unescape(e.GetRawText());
        }

        public static byte[] Render(Action<Utf8JsonWriter> write)
        {
            using var ms = new MemoryStream();
            using (var w = new Utf8JsonWriter(ms, WriterOptions))
            {
                write(w);
            }
            return ms.ToArray();
        }
    }

    /// <summary>Exception name mapping from porting/exception-map.json ("dotnet" table).</summary>
    public static class ExceptionMap
    {
        private static Dictionary<string, string> s_map = new Dictionary<string, string>();

        public static void Load(string path)
        {
            using var doc = JsonDocument.Parse(File.ReadAllBytes(path));
            var map = new Dictionary<string, string>();
            foreach (var p in doc.RootElement.GetProperty("dotnet").EnumerateObject())
                map[p.Name] = p.Value.GetString();
            s_map = map;
        }

        public static string Map(Exception ex) => Map(ex.GetType().Name);

        public static string Map(string dotnetName) =>
            s_map.TryGetValue(dotnetName, out var j) ? j : dotnetName;

        public static string Throw(Exception ex) => "throw:" + Map(ex);

        public static string FindDefault()
        {
            foreach (var start in new[] { Directory.GetCurrentDirectory(), AppContext.BaseDirectory })
            {
                var dir = new DirectoryInfo(start);
                while (dir != null)
                {
                    var candidate = Path.Combine(dir.FullName, "porting", "exception-map.json");
                    if (File.Exists(candidate)) return candidate;
                    dir = dir.Parent;
                }
            }
            return null;
        }
    }
}
