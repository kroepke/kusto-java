# Behavioural Traps: C# -> Java 21 Port of Kusto.Language

Scope: `upstream/kusto-query-language/src/Kusto.Language/**/*.cs`, excluding `Editor/`. That is 181 files and 68,474 lines.
Paths are relative to `src/Kusto.Language/`. Line numbers are from upstream commit `9d95a2d`.
All counts come from grep and small scripts over the sources. Each count says what it covers.

Not in this census: generated syntax-node classes. `Syntax/CodeGen/GeneratedSyntaxNodes.tt` and `../Kusto.Language.Generators/SyntaxNodeGenerator.cs` emit them, and they are not checked in. Their `CloneCore` and `Accept` recursion is noted in section 11.

## 0. Cross-cutting: which .NET behaviour is the reference?

- `Package.props:6` multi-targets `net6.0;netcoreapp2.1;net472;netstandard2.0`. `Kusto.Language.csproj:15` defaults to `netstandard2.1`.
- Several traps below differ between those runtimes:
  - `double.TryParse` overflow handling.
  - `double.ToString()` digits.
  - NLS vs ICU culture comparison.
  - `(long)double` saturation.
  - Unicode table version.
- **Decision needed.** Pick one reference runtime for golden tests. Recommendation: net6.0 or later, `CultureInfo.InvariantCulture`, `InvariantGlobalization=true`.
- Under invariant globalization, most culture-sensitive string calls (section 4) become ordinal comparisons. Java can then use ordinal semantics without diverging.
- `Kusto.Language.csproj:8` sets `AllowUnsafeBlocks=true`, but no `unsafe` code exists (section 9).
- `CheckForOverflowUnderflow` is not set, so all arithmetic is unchecked. This matches Java.
- `#if !BRIDGE` branches are behavioural:
  - `Parser/KustoFacts.cs:935`: the `\U` escape.
  - `Syntax/SyntaxToken.cs:641`: `TimeSpan.TryParse`.
  - `Utils/Interlocked.cs:21,41,58`, `Utils/ThreadSafeDictionary.cs:3,11,62`, `Utils/Cancellation.cs:9`.
  - Always port the `!BRIDGE` (.NET) branch.

---

## 1. Character classification

### 1a. BCL `char.*` calls: 21 calls on 20 lines

| Site | Call | What Java must do |
|---|---|---|
| `Parser/TokenParser.cs:180` | `char.IsDigit(ch)` dispatches to the number scanners | `Character.isDigit(char)`. Both test Unicode category Nd. Lexer **accepts non-ASCII digits** such as U+0663. See the 1d note. |
| `Parser/TokenParser.cs:627` | `char.IsDigit` in `ScanIdentifier`, digit-led identifiers | Same. |
| `Parser/TokenParser.cs:661` | `char.IsDigit` in `ScanDigits`, used by long, real, timespan and exponent scanning | Same. Hot path. |
| `Parser/QueryParser.cs:1384`, `:1449`, `:1466` | `char.IsDigit(number.Text[0])` for signed-literal and JSON number detection | `Character.isDigit`. |
| `Parser/QueryGrammar.cs:694` | Same check in the grammar version | `Character.isDigit`. |
| `Parser/KustoFacts.cs:950` | `char.IsDigit(ch2)` picks the octal escape in `DecodeEscapes` | `Character.isDigit`. Accepts `8`, `9` and Unicode digits, unlike the lexer's `ScanOctalCode` (`TokenParser.cs:1054-1066`, `'0'..'7'`). Mirror the mismatch. |
| `Parser/KustoFacts.cs:980` | `char.IsDigit` in `DecodeOctal`, then `text[index]-'0'` | Same. Non-ASCII digits give garbage values. Mirror it. |
| `Parser/KustoFacts.cs:1122` | `char.IsDigit` for the URI port | `Character.isDigit`. |
| `Parser/KustoFacts.cs:1159` | `IsHostNameChar`: `char.IsLetter(ch) \|\| char.IsDigit(ch) \|\| ...` | `Character.isLetter(char)` covers Lu, Ll, Lt, Lm and Lo, same as .NET. Use the `char` overloads, not the code-point ones. |
| `Syntax/SyntaxToken.cs:658` | `char.IsLetter` splits the number from the unit in `GetTimeSpanValue` | `Character.isLetter(char)`. |
| `Utils/StringAndNumberComparer.cs:31`, `:32`, `:93`, `:94`, `:125` | `char.IsDigit` segments numbers for "granny" ordering | `Character.isDigit(char)`. |
| `Parser/Combinators/Parsers.cs:644`, `:645` | `char.ToUpper(ch)`, `char.ToLower(ch)`: **CurrentCulture** (Turkish i) | Java `Character.toUpperCase/LowerCase(char)` is invariant. It matches .NET only under the invariant culture. |
| `Parser/KustoFacts.cs:940` | `char.ConvertFromUtf32(hex)` | Throws `ArgumentOutOfRangeException` when `hex < 0`, `> 0x10FFFF`, **or in D800-DFFF**. `Character.toChars` accepts surrogates, so add an explicit check. See section 10. |

Notes:
- Unicode category tables depend on the runtime version. Java 21 uses Unicode 15.0. Characters assigned in newer Unicode versions are the only divergence.
- `char.IsWhiteSpace` and `char.IsLetterOrDigit` are **never** called directly.
- `char.GetUnicodeCategory` and `CharUnicodeInfo` are not used.

### 1b. String-level case mapping: 6 calls, all culture-sensitive

| Site | Call | What Java must do |
|---|---|---|
| `PlugIns.cs:594`, `:683` | `sne.Name.SimpleName.ToLower()` feeds a `switch` on `"radius"` and similar | Use `toLowerCase(Locale.ROOT)`. Under `tr-TR`, .NET maps `I` to dotless `ı`, so the switch misses. Java ROOT matches invariant. |
| `Parser/SyntaxParsers.cs:99` | `kind.ToString().ToLower()` builds a default tag | Java enum `name()` must equal the C# member name. Then lowercase with ROOT. |
| `Syntax/SyntaxToken.cs:244` | `category.ToString().ToLower()` in an exception message | Same. |
| `Parser/Combinators/Parsers.cs:666`, `:667` | `text.ToLower()` / `ToUpper()`, then index `lower[i]` / `upper[i]` | Java `String.toLowerCase/toUpperCase` use **full** case mapping and can change length (`ß`→`SS`, `İ`→`i̇`). That breaks the index alignment. Map per `char` with `Character.toLowerCase/toUpperCase`. |

`ToLowerInvariant` and `ToUpperInvariant` are not used anywhere.

### 1c. Hand-written predicates (verbatim)

`Parser/TextFacts.cs:12-45`, `IsWhitespace`. This is the lexer's whitespace definition, used at `TokenParser.cs:465,529,549` and `TriviaFacts.cs:46`:
```csharp
case '\t': case ' ': case '\r': case '\n': case '\u000c': case ' ': case ' ':
case '᠎': case ' ': ... case ' ': case '​': case ' ':
case ' ': case '　': case '﻿': return true;  default: return false;
```
This is a fixed set. It includes U+200B and U+FEFF, which are not whitespace in .NET or Java. It excludes U+000B, U+0085, U+2028 and U+2029. Port it as a literal switch. Never substitute `Character.isWhitespace`.

`Parser/TextFacts.cs:117-130`, `IsLineBreakStart`:
```csharp
case '\r': case '\n': case ' ': case ' ': return true;
```
`GetLineBreakLength` (`TextFacts.cs:132-152`) treats `\r\n` as 2 and the others as 1.

`Parser/TextFacts.cs:361-379` (ASCII only):
```csharp
public static bool IsLetter(char ch) { return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z'); }
public static bool IsDigit(char ch) { return (ch >= '0' && ch <= '9'); }
public static bool IsLetterOrDigit(char ch) { return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9'); }
public static bool IsHexDigit(char ch) { return (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F'); }
```

`Parser/TokenParser.cs:208-214, 591-599`:
```csharp
private static bool IsStringLiteralStartQuote(char ch) { return ch == '\'' || ch == '"' || ch == '`' || ch == '~'; }
private static bool IsIdentifierStartChar(char ch) { return TextFacts.IsLetter(ch) || ch == '_' || ch == '$'; }
private static bool IsIdentifierChar(char ch) { return TextFacts.IsLetterOrDigit(ch) || ch == '_'; }
```

`Parser/TokenParser.cs:1054-1066`, `ScanOctalCode`: `ch1 >= '0' && ch1 <= '7'` for each of up to 3 chars, plus `ch1 <= '3'` for 3-digit codes.

`Parser/KustoFacts.cs:1157-1160`, `IsHostNameChar`: uses BCL `char.IsLetter`/`char.IsDigit`. See 1a.

`Parser/TokenParser.cs:1201-1211`, `Peek` returns `'\0'` past the end. Predicates therefore see NUL at EOF. An embedded NUL in the input behaves like EOF for those `Peek`-driven predicates, but not for `IsAtEnd`. Mirror both.

`Syntax/SyntaxFacts.cs` has **no** character predicates. `IsKeyword` and `CanBeIdentifier` are table lookups (`:895-896`).

### 1d. Mixed ASCII and Unicode digits

The lexer dispatches numbers on Unicode `char.IsDigit` (`TokenParser.cs:180,661`). Identifiers are ASCII (`TextFacts.IsLetter`).

So `٣` (U+0663) lexes as a `LongLiteralToken`. Its value is `Int64.TryParse("٣")`, which **fails** because .NET integer parsing is ASCII-only, and returns `0`.

Java `Long.parseLong("٣")` **returns 3**, because it uses `Character.digit`. See 2b.

### 1e. `string.Trim()`: 11 calls, uses .NET `char.IsWhiteSpace`

Sites:
- `Symbols/EntityGroupSymbol.cs:61`, `:77`
- `Symbols/Signature.cs:680`
- `Binder/Binder_FunctionCalls.cs:2365`
- `Syntax/SyntaxToken.cs:541` (`Destringify`, literal value path)
- `Parser/CommentFacts.cs:56`
- `Utils/ConnectionInfo.cs:77`, `:88`
- `Symbols/TableSymbol.cs:93`
- `Parser/CommandGrammar.cs:217`, `:223`

.NET `Trim()` strips the `char.IsWhiteSpace` set: U+0009–U+000D, U+0020, U+0085, U+00A0, U+1680, U+2000–U+200A, U+2028, U+2029, U+202F, U+205F and U+3000.

- Java `trim()` strips `<= U+0020` only.
- Java `strip()` uses `Character.isWhitespace`. That excludes U+00A0, U+2007 and U+202F, excludes U+0085, and includes U+001C–U+001F.
- Both are wrong. Write a `DotNetChars.isWhiteSpace` and `dotNetTrim`.

**Category 1 totals:** 21 BCL `char.*` calls, 6 string case-mapping calls, 11 `Trim()` calls, and 12 hand-written predicates (TextFacts 7, TokenParser 4, KustoFacts 1).

---

## 2. Literal parsing and formatting

All parsing is in `Syntax/SyntaxToken.cs` (`LiteralToken` value conversion). None of it passes `CultureInfo` or `NumberStyles`, so every call uses **CurrentCulture** and the default styles. Failed TryParses are silently replaced by `default` (0, `TimeSpan.Zero`, `DateTime.MinValue`, `Guid.Empty`).

### 2a. Parse/TryParse sites: 12

| Site | Call (default styles) | .NET behaviour Java must reproduce |
|---|---|---|
| `SyntaxToken.cs:584` | `Int32.TryParse(valueText, out result)` | `NumberStyles.Integer`: leading and trailing white space, leading sign, **ASCII digits only**, no hex, no thousands separators. Fails to 0. Overflow fails. |
| `SyntaxToken.cs:600` | `Int64.TryParse` | Same. `long(0x1F)` and the raw hex `0x1F` both give **0**. The lexer has a hex scanner (`TokenParser.cs:671-692`) but there is no hex value conversion anywhere (grep for `HexNumber` returns nothing). |
| `SyntaxToken.cs:616` | `Double.TryParse` | `NumberStyles.Float \| AllowThousands` with culture separators. Accepts `1,000.5`. Overflow returns ±∞ on .NET Core 3.0+ but **fails to 0** on net472 and netcoreapp2.1. NaN and Infinity symbols are culture-defined. Java `Double.parseDouble` also accepts `0x1p3`, `1.5d` and `1.5f`. Reject those. |
| `SyntaxToken.cs:632` | `Decimal.TryParse` | `NumberStyles.Number`: thousands separators allowed, **exponent not allowed** (`decimal(1e5)` gives 0). It is a 96-bit scaled decimal. `BigDecimal` needs range, scale and rounding clamps. |
| `SyntaxToken.cs:643` | `TimeSpan.TryParse(valueText)` (`#if !BRIDGE`) | Format `[ws][-]{d \| [d.]hh:mm[:ss[.fffffff]]}[ws]`. A bare integer means **days** (`"10"` is 10 days). Needs a hand-written Java parser. |
| `SyntaxToken.cs:667` | `Double.TryParse(numberText)` on the timespan number part | As above. Then `FromSeconds/Minutes/Hours/Days/Milliseconds/FromTicks` (`:675-712`). |
| `SyntaxToken.cs:731` | `DateTime.TryParse(valueText)` | Very lenient, culture-dependent. A `Z` or offset suffix converts to **local time** (`DateTimeKind.Local`). The value depends on the machine time zone. Only visible via the `LiteralValue`/`ConstantValue` API and `ToString` (2c). Decide the policy explicitly. |
| `SyntaxToken.cs:740` | `Guid.TryParse` | Accepts formats N, D, B, P and X. Java `UUID.fromString` accepts malformed short groups (`1-1-1-1-1`) and rejects N, B, P and X. Write a custom parser. |
| `PlugIns.cs:394`, `:425`, `:456`, `:486` | `bool.TryParse(GetConstantValue(...))` | Case-insensitive `True`/`False`, trims white space, fails otherwise. The input comes from `object.ToString()` (2c): .NET gives `"True"`, Java `Boolean.toString` gives `"true"`. Both parse, but emulate TryParse, not `Boolean.parseBoolean`. |
| `Utils/ValueComparer.cs:26`, `:27` | `Convert.ChangeType(x, comparingType)` | Uses CurrentCulture for number→string. **Checked**: throws `OverflowException` for negative→`UInt64`. That branch is unreachable today because there are no ulong literals. |

Related non-numeric parsing:
- `SyntaxToken.cs:552`, `:559`, `:563`: `string.Compare(text, start, "null"/"true"/"false", 0, length[, ignoreCase])`.
  - This compares **min(length, remaining)** prefixes. With length 2, `"nu"` equals `"null"`.
  - Java `regionMatches` gives the same answer only if the length is clamped identically.
  - The call is culture-sensitive (section 4).

### 2b. Java-specific parsing traps

- `Long.parseLong` and `Integer.parseInt` accept Unicode Nd digits. .NET does not (see 1d). Use an ASCII-only parser.
- .NET integer TryParse allows surrounding white space and trailing NULs. Java throws on both.
- `(long)number` at `SyntaxToken.cs:712` (`FromTicks((long)number)`):
  - .NET before 9 on x64 gives `long.MinValue` for NaN or out-of-range values.
  - .NET 9+ saturates.
  - Java saturates and maps NaN to 0.
- `TimeSpan.FromSeconds/FromMinutes/FromHours/FromDays/FromMilliseconds(double)` (`SyntaxToken.cs:675-709`):
  - They throw `OverflowException` when the result is outside the TimeSpan range (about ±10,675,199 days), and `ArgumentException` on NaN. The lexer accepts `100000000d`.
  - Rounding differs by runtime. .NET Framework rounds to whole milliseconds; modern .NET keeps tick precision. Pin to the reference runtime.
  - Java needs a `TimeSpan` value type with 100 ns ticks.
- `FromSeconds(number / 1_000_000.0)` for micro and nano units (`:703`, `:709`): double arithmetic is the same in Java. Keep the exact expression.
- Under BRIDGE, `TimeSpan.TryParse` is skipped (`:641`). `timespan(10)` is then 0 seconds, not 10 days. Port the .NET branch only.

### 2c. Formatting via `object.ToString()` and interpolation

There are no format strings (`"R"`, `"G17"`, `"o"`) and no `string.Format` anywhere. All formatting is implicit `object.ToString()` on boxed literal values, using the current culture and .NET Core 3.0+ shortest round-trip for double.

| Site | Value formatted | Where it shows |
|---|---|---|
| `FunctionHelpers.cs:68` | `GetConstantValue` → `expr.ConstantValue?.ToString()` | `Aggregates.cs:320`: percentile result column name `percentile_<col>_<frag>`. `Functions.cs:1897`: bag_remove_keys. `PlugIns.cs:394-486`: bool. |
| `PlugIns.cs:927`, `:928` | `LiteralValue?.ToString()` for `BinsPerWindow` and `Percentile` | rolling_percentile column name `rolling_{n}_percentile_{col}_{p}` (`:930`). |
| `Binder/Binder_Projection.cs:719` | `be.Expression.LiteralValue.ToString()` for string, long or int | Result column name from `x[0]` and similar. |
| `Binder/Binder_FunctionCalls.cs:238` | `LiteralValue?.ToString()` compared to pattern argument strings | Pattern matching (`PatternMatches`). |
| `Binder/Binder_NodeBinder.cs:183`, `:205` | Pattern parameter and path values | Pattern signatures. |
| `Binder/Binder_Misc.cs:2170` | Non-empty literal check | Diagnostic. |
| `Diagnostics/DiagnosticFacts.cs:453`, `:457-458`, `:466`, `:470-471` | `{values[0]}` and `v.ToString()` for `object` values | KS140 message text. |
| `Binder/CallSiteInfo.cs:44` | `{this.Values[i]}` | Debug display only. |
| `Diagnostics/DiagnosticFacts.cs:316`, `:332` | `{count}` (int) | Messages. Int formatting is identical. |
| `PlugIns.cs:930` | `{binsPerWindow}`, `{percentile}` (already strings) | Column name. |

What Java must do: write one `DotNetFormat.toString(Object)`.

- **double**:
  - .NET Core 3.0+ prints shortest round-trip with no `.0` (`50`), and `E` notation with a sign and at least 2 exponent digits (`1E-05`, `1E+20`).
  - Java prints `50.0`, `1.0E-5` and `1.0E20`.
  - Example: `percentile(x, 50.0)` gives `percentile_x_50` in .NET and `percentile_x_50_0` in Java (`MakeValidNameFragment` maps `.` to `_`).
  - .NET Framework uses 15 significant digits instead.
- **bool**: `True`/`False`.
- **decimal**: keeps its scale (`1.50`).
- **TimeSpan**: `c` format `[-][d.]hh:mm:ss[.fffffff]`.
- **DateTime**: culture default, for example `1/2/2020 12:00:00 AM`.
- **Guid**: `D` format, lowercase.
- Enum `ToString` (`QueryGrammar.cs:1030`, `SyntaxNode*`): member name. Flags enums would print `A, B`, but no flags enum is stringified here.

C# char escapes with no Java equivalent: `'\a'` at `KustoFacts.cs:726`, `:737`, `:903` and `'\v'` at `:927`. Write `'\u0007'` and `'\u000B'`.

**Category 2 totals:** 12 Parse/TryParse calls, 2 `Convert.ChangeType`, 1 `char.ConvertFromUtf32`, 9 `TimeSpan.FromX` calls, 10 implicit-`ToString` sites with visible output, 0 explicit format strings, 0 `CultureInfo`/`NumberStyles` arguments.

---

## 3. Collection ordering

Ground rule:
- .NET `Dictionary<K,V>` and `HashSet<T>` enumerate in **insertion order** when no removals happened. Overwriting `d[k] = v` keeps the slot.
- Java `HashMap`/`HashSet` enumerate in hash order.
- Java `EnumMap`/`EnumSet` enumerate in **ordinal** order.
- So use `LinkedHashMap`/`LinkedHashSet` for every Dictionary/HashSet that is ever enumerated.
- `LinkedHashMap.put` on an existing key also keeps position, which matches.
- No enumerated dictionary is ever `Remove`d before enumeration (checked below), so `LinkedHashMap` is exact.

### 3a. Dictionary/HashSet enumerations where order is visible

| Site | Collection | Effect |
|---|---|---|
| `Parser/QueryGrammar.cs:1429` | `StringOperatorMap.Keys` (`Dictionary<SyntaxKind,SyntaxKind>`, `:4042`) | Order of `First(...)` alternatives for string operators. **Must not become an EnumMap.** |
| `Parser/QueryParser.cs:3481-3483` | `HashSet<string>` from `s_nameToDefaultQueryOperatorParameterMap.Keys` (built `:3449-3471`, overwrite semantics) concatenated with `KustoFacts.KnownQueryOperatorParameterNames` | Enumerated at `:3488-3489` into `s_multiTokenQueryOperatorParameterNames`, which is scanned **first-match-wins** at `:3556-3561` and `:3583-3588`. |
| `Binder/Binder_Misc.cs:169` | `columnMap.Values` (inferred columns of open tables, Dictionary created `:147`) | Column order of open-table schemas. |
| `Binder/ColumnMap.cs:150` | `dict.Keys` (`Dictionary<TypeSymbol,object>`, created `:66`, `:83`) | `GetTypes`. Order and `_type`-suffix naming of union columns (`Binder_TablesAndColumns.cs:220-229`, `:280-281`). |
| `Binder/ColumnMap.cs:188` | `dict.First().Value` | Picks the first-inserted type bucket. |
| `Binder/ColumnMap.cs:237` | `foreach (var kvp in dict)` in iterator `GetColumns` | `originalColumns` order. |
| `Binder/LocalScope.cs:192` | `_symbols.Values` (`:146`, overwrite at `:149`, `:153`) | Symbol order from `GetSymbols` (`KustoCode.GetSymbolsInScope`). Duplicates appear when `AlternateName` is set. |
| `Utils/TextKeyedDictionary.cs:110` | `map.Values.GetEnumerator()` | Public enumeration order. `StringTable.cs:89` enumerates too. |

Enumerations where order does **not** matter (verified):
- `Binder_TablesAndColumns.cs:334`: `nameToIndexMap.Keys` feeds `UniqueNameTable`, a set.
- `TokenParser.cs:1284`: `s_kindToTokenInfoMap` feeds `SubstringMap`. Keys are unique.
- `SubstringMap.cs:69`, `:191`.
- `ListExtensions.cs:268`: equivalence check.
- `LocalScope.cs:69`: `Any`.
- `Binder_NodeBinder.cs:3949`, `:4030`: `Any`.
- All `GlobalState` `ToDictionaryLast` maps (`GlobalState.cs:714`, `743`, `771`, `829`, `920`, `1011`, `1077`): lookup only.
- `PlugIns.cs:1173`: lookup only.

Removal sites: `Binder_TablesAndColumns.cs:246`, `:299`; `TypeFacts.cs:558`, `:575`; `ColumnMap.cs:120`; `ProjectionBuilder.cs:217`; `Binder_FunctionCalls.cs:2123`. None of these collections is enumerated after the removal.

`Distinct()` keeps first-occurrence order in .NET. Use `LinkedHashSet` or `stream().distinct()`. Sites:
- `FunctionBodyFacts.cs:116`, `:162`, `:306`
- `SyntaxNode_Semantics.cs:114` (diagnostics, using `Diagnostic.Equals`)
- `PartialParser.cs:370`, `:1132`, `:1140`
- `ListExtensions.cs:138`
- `CommandGrammar.cs:174`

`ListExtensions.cs:146` (`DistinctLast`) does reverse, then distinct, then reverse.

### 3b. Sorting

| Site | Call | Stable? | Comparer | What Java must do |
|---|---|---|---|---|
| `Syntax/SyntaxFacts.cs:773` | `List.Sort((d1,d2) => string.Compare(d1.Text, d2.Text))` | **Unstable** (introsort) | **Culture** | 287 of the 605 entries have text `""`. `textToKindMap.GetOrAddValue` (`:795`) keeps the **first** entry per text, so the `""` mapping depends on introsort's tie order. Only that key is affected; all other texts are unique (verified). Java: do not register `""`, or pin the winner and test `SyntaxFacts.TryGetKind("")`. |
| `Parser/KustoFacts.cs:89` | `OrderBy(n => n)` | Stable | **Culture** (`Comparer<string>.Default`) | The result feeds a HashSet with the same names already present (`QueryParser.cs:3481-3483`), so the order is not visible. Use any comparator. |
| `Parser/CommandGrammar.cs:174` | `tags.Distinct().OrderBy(x => x)` | Stable | **Culture** | Order of terms in the "expected" diagnostic (commands, out of scope). Needs a culture collator to match exactly. |
| `Parser/ScriptFacts.cs:62`, `:92` | `OrderBy(x => x)` on int | Stable | Numeric | Trivial. |
| `Binder/Binder_Projection.cs:462`, `:464` | `OrderBy/OrderByDescending(c => c.Name, StringComparer.OrdinalIgnoreCase)` | Stable | OrdinalIgnoreCase | `project-reorder asc/desc`. **Do not use `String.CASE_INSENSITIVE_ORDER`.** .NET upper-cases both strings (`'a'`→`0x41` < `'_'`=`0x5F`). Java lower-cases on mismatch (`'a'`=`0x61` > `'_'`), so `a` vs `_b` sorts **in reverse**. Also differs for U+212A, U+0130, U+0131 and U+017F. Implement `OrdinalIgnoreCase` as per-char `Character.toUpperCase` then compare. |
| `Binder/Binder_Projection.cs:466`, `:468` | `OrderBy(..., StringAndNumberComparer.OrdinalIgnoreCase)` | Stable | Custom | `Utils/StringAndNumberComparer.cs:52` uses `_comparison`. `:114` uses **culture** `string.Compare` on digit runs. `:31-125` uses `char.IsDigit`. `CompareNumberSegment` (`:97-115`) never shrinks `xLength`/`yLength` after skipping zeros. That is an upstream quirk; mirror it. |

`Array.Sort` is not used. `List.Sort` appears once, and `OrderBy`/`OrderByDescending` 8 times. Java `List.sort` and `Stream.sorted` are stable, which matches LINQ `OrderBy`.

**Category 3 totals:** 8 visible dictionary/set enumerations, 9 non-visible enumerations, 11 `Distinct` calls (`FunctionBodyFacts` 3, `SyntaxNode_Semantics` 1, `PartialParser` 3, `ListExtensions` 1, `CommandGrammar` 1, plus `ListExtensions.cs:146` reusing `:138`), and 9 sort sites (1 `List.Sort`, 8 `OrderBy*`).

---

## 4. String comparison

C# `==` on strings is ordinal. Java must use `equals`. String `switch` maps cleanly.

### 4a. Culture-sensitive comparisons: 17 `string.Compare` calls without a `StringComparison` argument

With ICU (.NET 5+), culture comparison ignores ignorable code points such as U+00AD and U+200B. So a bracketed name `['Col­1']` would match column `Col1`. Ordinal Java will not.

| Site | Use | Hot? |
|---|---|---|
| `Symbols/SymbolMatch.cs:128` | `string.Compare(symbol.Name, name, ignoreCase)` | **Binder name lookup** |
| `Symbols/SymbolMatch.cs:138` | `string.Compare(sn, name)` (after a first-char ordinal check) | **Binder name lookup** |
| `Parser/SyntaxParsers.cs:188` | `MatchesText` token-text match | Parser |
| `Parser/Combinators/TextSource.cs:63`, `:68` | `Matches(start, text[, ignoreCase])` | Char-level parsers |
| `Parser/KustoFacts.cs:1304`, `:1310`, `:1322`, `:1345` | Wildcard `Matches` (table, column and database patterns: `Binder_Names.cs:803`, `Binder_FunctionCalls.cs:906-907`, `:1071`, `TableSymbol.cs:390`) | Binder |
| `Binder/Binder_FunctionCalls.cs:824`, `:825` | `string.Compare(db.Name, nameOrPattern, ignoreCase: true)` | Binder |
| `Utils/ValueComparer.cs:31` | `string.Compare(x, y, !caseSensitive)` | Binder value checks |
| `Syntax/SyntaxToken.cs:552`, `:559`, `:563` | null, true and false literal detection | Literal values |
| `Utils/StringAndNumberComparer.cs:114` | Digit-run compare | Sorting |
| `Syntax/SyntaxFacts.cs:773` | Static sort | Static init |

Ordinal-equivalent uses that are fine as `regionMatches`:
- `TokenParser.cs:1236`, `KustoFacts.cs:857`: `Ordinal`.
- `KustoFacts.cs:1045`: `OrdinalIgnoreCase`.
- `StringAndNumberComparer.cs:52`: Ordinal or OrdinalIgnoreCase.

The overloads `Compare(a, ia, b, ib, len)` compare **min(len, remaining)** of each side. They are not exact-length equality. Mirror that clamp.

### 4b. `StartsWith`/`EndsWith(string)` without a comparison: 10 calls, culture-sensitive in .NET

Sites:
- `Parser/TokenParser.cs:137`: `gooText.EndsWith(")")`
- `Syntax/SyntaxToken.cs:512`: `EndsWith(")")`
- `Symbols/EntityGroupSymbol.cs:64`: `"entity_group"`
- `Symbols/EntityGroupSymbol.cs:72`: `"["`, `"]"`
- `Symbols/EntityGroupSymbol.cs:80`: `"\""`, `"'"`
- `Binder/Binder_Misc.cs:736`: `"cluster"`
- `Binder/Binder_Misc.cs:747`: `"@"`
- `Binder/Binder_Names.cs:785`: `"__"`

The ICU result can be true when trailing or leading ignorable characters are present. Java `startsWith`/`endsWith` is ordinal. Accept the divergence under the invariant-mode recommendation.

Explicitly ordinal (fine): `SyntaxParsers.cs:406`, `Symbol.cs:41`, `KustoFacts.cs:860`, `:1187`, `:1205`, `Signature.cs:681`, `:684`, `Binder_FunctionCalls.cs:2367`, `:2370`.

### 4c. `IndexOf`

- `IndexOf(string)` is culture-sensitive: 2 calls.
  - `Parser/KustoFacts.cs:1094`: `uri.IndexOf("://")`.
  - `Binder/Binder_Misc.cs:764`: `text.IndexOf(prefix, start)`.
  - Known ICU quirk: `"\r\n".IndexOf("\n") == -1`.
- `IndexOf(char)` is ordinal: `KustoFacts.cs:819`, `:878`, `:1271`, `:1341`.
- `KustoFacts.cs:878` (`HasInteriorQuote`) ignores its `start` argument. That is an upstream bug; mirror it.
- `string.Contains(string)` is **ordinal** in .NET: `KustoFacts.cs:688`, `TableSymbol.cs:379`, `Binder_FunctionCalls.cs:740`, `SyntaxToken.cs:112`, `CommentFacts.cs:18`, `CommandGrammar.cs:209`.
- `Functions.cs:1966-1969` uses `Contains(char)`.

### 4d. Explicit comparers: 23 uses

- 10 `StringComparison.Ordinal`, 7 `StringComparison.OrdinalIgnoreCase`, 6 `StringComparer.OrdinalIgnoreCase`.
- No `CurrentCulture` or `InvariantCulture` values anywhere.
- `Dictionary(StringComparer.OrdinalIgnoreCase)` at `Utils/ConnectionInfo.cs:25`.
- `StringComparer.OrdinalIgnoreCase.Equals` at `PlugIns.cs:562`, `:650`, `:724`.
- `string.Equals(..., OrdinalIgnoreCase)` at `QueryGrammar.cs:1735`, `QueryParser.cs:2278`, `Binder_NodeBinder.cs:1877`.

Java: `equalsIgnoreCase` and `regionMatches(true, ...)` test **both** upper- and lower-case per char. That is a superset of .NET `OrdinalIgnoreCase`; for example `"K".equalsIgnoreCase("K")` is true in Java and false in .NET. Write `DotNetStrings.equalsOrdinalIgnoreCase` using upper-case only.

Also `Parser/CommandGrammar.cs:212`: `tag.Split('|')`. Java `split` takes a regex, so quote it.

**Category 4 totals:** 17 culture `string.Compare` calls, 10 culture `StartsWith`/`EndsWith`, 2 culture `IndexOf(string)`, 7 culture case mappings (`Parsers.cs` ×4, `PlugIns.cs` ×2, `SyntaxToken.cs:244`), 2 culture `OrderBy`, and 23 explicit ordinal comparers.

---

## 5. Structs and copy semantics

There are 14 struct types in scope, 3 of them nested padding helpers. Framework structs are listed after the table.

| Struct | Mutable? | Copy or `default` hazard | Java |
|---|---|---|---|
| `Parser/Combinators/OffsetValue.cs:6` `OffsetValue<TValue>` | No (readonly fields) | None | `record` |
| `Parser/Combinators/RightParser.cs:8` `RightParser<TInput,TOutput>` | No | `default` has a null `Parser` | `record` |
| `Utils/SafeList.cs:102` `SafeList<T>.Enumerator` | **Yes** (`_index`) | A copied enumerator advances independently. Only used by `foreach`. | `Iterator` class |
| `Binder/FunctionCallResult.cs:17` | No | Implicit conversion from `TypeSymbol` (`:33-36`). Every `return someType;` in a method returning `FunctionCallResult` must be wrapped explicitly. | `record` plus factory |
| `Parser/Combinators/PartialParser.cs:73` `BestPathKey` | **Yes** (public `Parsers`, `InputStart`; `_hc` set in the constructor) | Dictionary key with value equality (`:98-117`). Hash is the sum of `parser.GetHashCode()`, which are identity hashes. | final-field class with `equals`/`hashCode` |
| `PartialParser.cs:922` `ScanInput` | No | Implicit conversion to `ScanOutput` (`:959-960`) | `record` plus a static method |
| `PartialParser.cs:966` `ScanOutput` | **Yes**: the `Paths` getter lazily assigns `_paths` (`:986-997`, assignment `:993`) | The cache is lost on copies (for example `output.Paths` at `:362` on a local copy). A Java class keeps it. Only allocation and identity differ, not results. | class |
| `Parser/Combinators/ParseResult.cs:3` `ParseResult<TOutput>` | No | **`default(ParseResult)` has `Length==0`, so `Succeeded==true`.** Java `null` must not be treated as success. No `default(ParseResult...)` is in the code today; keep it that way. | `record` |
| `Parser/Combinators/Parsers.cs:1348` `LeftValue<TLeft>` | Empty marker | Used only for generic inference | Marker class or `Class<T>` |
| `Parsers.cs:1506` `ElementAndSeparator<TElement,TSeparator>` | No | Constructor throws on a null element | `record` |
| `Utils/Interlocked.cs:75` `CacheLineSeparated<T>`, `:102` `PaddingFor32`, `:108` `PaddedReference` | `m_value` public mutable | **Dead code.** No references anywhere in `src/`. | Skip |
| `Utils/TextKeyedDictionary.cs:120` `Key` | No | FNV hash (section 10) | `record` with a custom hash |
| `Utils/Cancellation.cs:7` `CancellationToken` | No | `default` means never cancelled | Final class plus a `NONE` constant |
| `Utils/Optional.cs:5` `Optional<T>` | No | `default` means `HasValue=false`. **`new Optional<T>(null)` means "set to null".** `GlobalState.With(...)` (`GlobalState.cs:256-271`), `FunctionSymbol.With` (`FunctionSymbol.cs:212-220`) and `FunctionBodyFacts.With` (`:63-66`) depend on it. `java.util.Optional` cannot hold null, so write a custom class. | custom `Opt<T>` |

Framework structs in use:
- `KeyValuePair<,>`: 22 occurrences. `SubstringMap.cs:45` `NoValue` has key `""`, not null. Callers depend on `.Key.Length` (`TokenParser.cs:782` (`suffixMatch.Key.Length`)).
- `ValueTuple`: `PartialParser.cs:362` (`(parser, path)`).
- `DateTime`, `TimeSpan`, `Guid`, `decimal`: value types without Java equivalents. `TimeSpan` needs a custom class.
- `Nullable<T>`: for example `GetParameterLiteralValue<bool?>` at `Binder_TreeBinder.cs:109`, `:233`.
- Boxing identity: `lit.ConstantValue is TValue` (`SyntaxExtensions.cs:21`) and `valueInfo?.Value is T` (`Binder_FunctionCalls.cs:699`) are reified generic type tests. Java erasure needs a `Class<T>` parameter.

**Category 5 totals:** 14 struct types: 3 mutable (`SafeList.Enumerator`, `BestPathKey`, `ScanOutput`), 3 with implicit conversions or default-value semantics, 3 dead padding helpers.

---

## 6. `ref` and `out` parameters

There are 59 method declarations with `ref` or `out` parameters. A regex scan of declarations found 58. A typed-parameter grep added `TryGetExpansionFromCache`, which has no access modifier.

`ref`-taking declarations (8):
- `TextFacts.cs:557`, `:567`, `:575`, `:584`, `:594`, `:603`
- `KustoFacts.cs:975`, `:988`
- `ParameterLayouts.cs:129`
- `Utils/Interlocked.cs:18`, `:39`, `:55`, which take field references.

All others are `out`.

### 6a. Parser and binder: full list

- `Binder/Binder_FunctionCalls.cs` (10):
  - `:355` `TryGetInvokeOperatorExpression(…, out Expression)`
  - `:682` `TryGetLiteralStringValue(…, out string)`
  - `:696` `TryGetLiteralValue<T>(…, out T)`
  - `:714` `TryGetLiteralValueInfo(…, out ValueInfo)`
  - `:1895` `TryGetResultTypeCallSite(…, out CallSiteInfo)`
  - `:1946` `TryGetCallSiteArgumentValue(…, out object)`
  - `:1968` `TryGetResultTypeFromCache(…, out TypeSymbol)`
  - `:2130` `TryGetExpansionFromCache(…, out FunctionCallExpansion)`
  - `:2229` `TryGetExpansionCallSiteInfo(…, out CallSiteInfo)`
  - `:2436` `TryGetFunctionBodyFacts(…, out FunctionBodyFacts)`
- `Binder/Binder_NodeBinder.cs` (4):
  - `:833` `TryGetIntValue(object, out int)`
  - `:2827` `CheckCommonColumn(…, out ColumnSymbol, out ColumnSymbol)`
  - `:2857` `CheckJoinOnEquality(…, out ColumnSymbol, out ColumnSymbol)`
  - `:2920` `CheckJoinOnEqualityOperand(…, out ColumnSymbol)`
- `Binder/Binder_Misc.cs` (3):
  - `:198` `TryGetDeclaredOrInferredColumn(…, out ColumnSymbol)`
  - `:701` `TryGetDirectiveClusterAndDatabase(…, out string, out string)`
  - `:762` `GetStringValueAfterPrefix(…, out int end)`
- `Parser/KustoFacts.cs` (6):
  - `:847`, `:853` `TryParseMultiLineStringLiteral(…, out string)`
  - `:975` `DecodeOctal(…, ref int index)`
  - `:988` `DecodeHex(…, ref int index)`
  - `:1067` `GetHostAndPath(…, out string, out string)`
  - `:1079` `GetUriParts(…, out ×5)`
- `Parser/TokenParser.cs:1150` `ScanClientParameter(…, out ×4)`
- `Parser/QueryParser.cs:3491` `TryGetSpecificQueryOperatorParameter(…, out QueryOperatorParameter)`
- `Parser/TriviaFacts.cs:15` `TryGetCommentSpan(…, out int, out int)`
- `Parser/TextFacts.cs` (13):
  - `:216`, `:247`, `:419`, `:463`, `:480`, `:496`, `:517`: out ints
  - `:557`, `:567`, `:575`, `:584`, `:594`, `:603`: ref int start/length
- `Parser/Combinators/ParserExtensions.cs:27` `TryParse(…, out TOutput)`
- `Parser/Combinators/Source.cs:73` `TryGetValue<T>(out T)`
- `Parser/Combinators/Parsers/MapParser.cs:178` `TryGetValueNode(…, out Node)`
- `Syntax/SyntaxFacts.cs:851`, `:859` `TryGetKind(…, out SyntaxKind)`
- `Syntax/SyntaxToken.cs:498` `GetValueSpan(string, out int, out int)`

### 6b. Symbols, utils and API (rest)

- `KustoCode.cs:446` `TryGetLineAndOffset`
- `KustoCache.cs:62` `TryGetValue<T>`
- `Symbols/DynamicSymbol.cs:102`
- `Symbols/TypeFacts.cs:193` `TryGetCommonType`
- `Symbols/GraphModelSymbol.cs:93`
- `Symbols/ParameterLayouts.cs:129` `GetArgumentParameters(…, ref int, ref int)`, called recursively at `:114`, `:126`, `:244`
- `Symbols/TableSymbol.cs:369`
- `Utils/ThreadSafeDictionary.cs:44`
- `Utils/ValueComparer.cs:43`
- `Utils/MostRecentlyUsedCache.cs:30`
- `Utils/TextKeyedDictionary.cs:30`
- `Utils/Interlocked.cs:18`, `:39`, `:55`

### 6c. Occurrences of `out `/`ref ` tokens per hot file (declarations plus call sites)

| File | Count |
|---|---|
| `Binder_FunctionCalls.cs` | 53 |
| `KustoFacts.cs` | 30 |
| `TextFacts.cs` | 30 |
| `Binder_Misc.cs` | 28 |
| `GlobalState.cs` | 26 (call sites only, mostly `Interlocked` refs) |
| `Binder_NodeBinder.cs` | 18 |
| `SyntaxToken.cs` | 16 |
| `TypeFacts.cs` | 16 |
| `ColumnMap.cs` | 10 |
| `ParameterLayouts.cs` | 9 |
| `TokenParser.cs` | 8 |
| `KustoCode.cs` | 7 |
| `QueryParser.cs` | 6 |
| `SyntaxFacts.cs` | 6 |

Java pattern:
- Single `out` plus bool: return a nullable value or a small result record.
- `ref int` cursors (`DecodeHex`/`DecodeOctal`, `TextFacts.*Range*`, `GetArgumentParameters`): pass an `int[]` or a mutable cursor object. Keep the same mutation order; `DecodeEscapes` reads `i` after the call.
- `ref` to fields in `Interlocked.CompareExchange(ref field, …)`: `VarHandle` or `AtomicReference` (section 9).

**Category 6 totals:** 59 declarations (8 with `ref`, 51 with only `out`) plus 31 `Interlocked` call sites passing `ref field`.

---

## 7. Iterators (`yield`)

There are 4 iterator methods and 7 `yield` statements. None is on a per-token or per-node hot path.

| Method | Yields | Hot? |
|---|---|---|
| `Parser/SyntaxParsers.cs:616` `ParseAll<TParser>` | `:628` | No. Utility API; lazy, so it re-parses if enumerated twice. |
| `Syntax/SyntaxFacts.cs:867` `GetKinds(SyntaxCategory)` | `:873` | No. Used by the `Keywords`, `Punctuation` and `Operators` properties (`:915-928`). |
| `Syntax/SyntaxFacts.cs:881` `GetKindsWithFixedText()` | `:887` | No. **Static init** of `TokenParser.cs:1281` and `KustoFacts.cs:427`. |
| `Binder/ColumnMap.cs:231` `GetColumns(string)` | `:243`, `:248`, `:256`, `:261` | Binder, per union or column-unification. `Binder_TablesAndColumns.cs:282` materialises it with `ToList()` before `map.Remove` (`:299`). Keep materialisation before mutation. |

Larger related risk: LINQ deferred execution. Counts:

| Operator | Count |
|---|---|
| `Select` | 83 |
| `Where` | 39 |
| `Concat` | 33 |
| `SelectMany` | 5 |
| `Distinct` | 9 |
| `Any` | 37 |
| `FirstOrDefault` | 30 |
| `ToList` | 37 |
| `ToArray` | 33 |
| `ToReadOnly` (custom) | 99 |
| `Append` (LINQ and SafeList) | 56 |

`IEnumerable` can be enumerated many times. A Java `Stream` cannot. A deferred query over a pooled list (section 9) that is enumerated after `ReturnToPool` would see cleared data. Upstream materialises via `ToReadOnly()` (`ListExtensions.cs:13-32`, which copies into a `SafeList`). Keep every materialisation point.

`Max` throws on empty sequences: `PartialParser.cs:309`, `:365`, `:1068`. Java `Stream.max` returns an `Optional`, so throw explicitly.

---

## 8. Static initialisation order

Only 2 explicit static constructors exist: `Symbols/ScalarTypes.cs:365` and `Syntax/SyntaxFacts.cs:43`. All other classes are `beforefieldinit` with field initialisers.

A forward-reference detector over the static fields of each class reported **0 eager forward references** in:
- Functions (+Convert)
- KustoFacts (+_Keywords)
- Aggregates, PlugIns, Operators, Options, QueryOperatorParameters
- ScalarTypes, TokenParser, QueryParser, QueryGrammar
- Properties, Signature, ParameterLayouts, TextFacts
- All `Binder_*.cs` merged in either order
- `SyntaxNode`/`SyntaxElement` partials

C# allows simple-name forward references in static initialisers; they yield null. Java rejects them at compile time unless qualified.

### 8a. Partial-class trap: order matters

`Functions.cs:3734` (`All`) eagerly lists `ConvertAngle`…`ConvertVolume` (`Functions.cs:4177-4184`). Those are declared in `Functions.Convert.cs:35-332`.

C# runs field initialisers of partial parts in an unspecified part order. Upstream is correct only because `Functions.Convert.cs` is compiled first, which holds under name ordering. When the detector was rerun with `Functions.cs` first, it flagged 8 eager forward references.

**In the merged Java `Functions.java`, the Convert section must come before `All`.** Recommendation: put the `Functions.Convert.cs` members at the top of the file, with a comment.

`KustoFacts_Keywords.cs:14` (`s_engineCommandKeywordsThatNeedBrackets`) is used only inside methods (`KustoFacts.cs:634`), so its position does not matter.

### 8b. Dependency graph (eager edges `A ──▶ B` mean A's initialiser needs B initialised)

```
Level 0 (leaves)
  SyntaxFacts (cctor :43; builds kindToDataMap/textToKindMap; uses only SyntaxData, TextKeyedDictionary, Ensure, Enum.GetValues)
  EmptyReadOnlyList<T>, SafeList<T>.Empty, Diagnostic.NoDiagnostics, ParseOptions.Default (ParseOptions.cs:108)
  Properties (Properties.cs:14-43) ──▶ GlobalStateProperty<T>
  ObjectPool holders (36 pools, section 9)

Level 1
  ScalarTypes (cctor :365; fields :11-:362) ──▶ ScalarSymbol/PrimitiveSymbol/DynamicPrimitiveSymbol/
             DynamicArraySymbol/DynamicBagSymbol ctors, ColumnSymbol ctor (GeoShape :209-212)
             (ColumnSymbol.cs:54 reads ScalarTypes.Unknown only if type==null: re-entrant read is safe today)
             cctor body: s_typeMap.Add (throws on duplicate alias -> TypeInitializationException)
  KustoFacts ──▶ SyntaxFacts          (KustoFacts.cs:426-427 KeywordsAsIdentifiers via GetKindsWithFixedText/IsKeyword/CanBeIdentifier)
             ··▶ QueryOperatorParameters  (LAZY: KnownQueryOperatorParameterNames :79-93, comment :82 "avoid cycle")
  TokenParser ──▶ SyntaxFacts         (TokenParser.cs:1280-1284; :1287-1317 index the dict -> KeyNotFound if a kind lacks text)
             (TokenParser.cs:29 Default = new TokenParser(null) precedes other statics; ctor uses none of them)
  ParameterLayouts (:26-47) ──▶ NonRepeating/Repeating/BlockRepeating layouts ──▶ ParameterLayout base pools (ParameterLayout.cs:94-98)

Level 2
  Parameter (Parameter.cs:89,91) ──▶ EmptyReadOnlyList
  Signature (Signature.cs:330 UnknownParameter) ──▶ Parameter, ScalarTypes ; ctor ──▶ ParameterLayouts (:166,:170)
  TableSymbol.Empty (:426, after InheritableState :307), TupleSymbol.Empty (:62), DynamicBagSymbol.Empty (DynamicSymbol.cs:167),
  DynamicAnySymbol.Instance (:34), ClusterSymbol.Unknown (:179), DatabaseSymbol.Unknown (:316), ErrorSymbol/VoidSymbol.Instance
  QueryOperatorParameters (:13-…) ──▶ KustoFacts (31 field reads)
  Options (Options.cs:16-…) ──▶ ScalarTypes, OptionSymbol

Level 3 (catalog)
  Functions (Convert part first!) ──▶ ScalarTypes, KustoFacts (Functions.cs:923,931,1052,1060,1073),
             ParameterLayouts (:1245,:1713,:1725,:1737), Signature, Parameter, FunctionSymbol, TupleSymbol, DynamicBagSymbol
  Aggregates ──▶ ScalarTypes, Signature, FunctionSymbol, TupleSymbol, ColumnSymbol
  PlugIns    ──▶ ScalarTypes, Signature, TableSymbol, ColumnSymbol, GroupSymbol
  Operators  ──▶ ScalarTypes, Signature, OperatorSymbol
  QueryGrammar statics ──▶ KustoFacts (QueryGrammar.cs:108); StringOperatorMap (:4042) is self-contained

Level 4
  QueryParser statics ──▶ KustoFacts (:448, :2440), Functions.All + Aggregates.All + SyntaxFacts + KustoFacts.CanBeIdentifier→TokenParser (:2696-2700),
                          QueryOperatorParameters.AllParameters (:3449), KustoFacts.KnownQueryOperatorParameterNames (:3483, lazy ──▶ QueryOperatorParameters)

Lazy singleton (not class init)
  GlobalState.Default (GlobalState.cs:1154-1193, CAS publish :1190) ──▶ KustoFacts, ClusterSymbol.Unknown, DatabaseSymbol.Unknown,
                          Functions.All, Aggregates.All, PlugIns.All, Operators.All, ServerKinds, Options.All, ParseOptions.Default (ctor :201)
```

Cycles:
- **None on eager edges.** The graph is a DAG, so Java has no multi-threaded class-init deadlock today.
- There is one **intentionally broken** cycle: `KustoFacts` ⇄ `QueryOperatorParameters`. It is broken by lazy init at `KustoFacts.cs:79-93`. Keep it lazy in Java.

Latent cycles, which become live if catalog code changes:
- `FunctionSymbol(string name, string parameterList, string body…)` (`FunctionSymbol.cs:187-195`) calls `Parameter.ParseList` (`Parameter.cs:255-257`), which calls `QueryParser`. QueryParser's static init then needs **`Functions.All` and `Aggregates.All`** (`QueryParser.cs:2696-2698`).
  - If any catalog entry used that constructor, `Functions` init would recurse into `QueryParser` and read `Functions.All == null`.
  - Verified unused in `Functions/Aggregates/PlugIns/Operators` (no `"(`-prefixed parameter strings).
- `Signature.AllowsNamedArguments` (`Signature.cs:335`) calls `GlobalState.Default`, which needs `Functions.All`. It is runtime-only.
- `Signature.GetSyntaxTabularity` (`:670-691`) calls `KustoCode.Parse` (`:687`). It is runtime-only.

Java specifics:
- Java initialises a class on its first static *method* call too. .NET beforefieldinit timing differs, but no initialiser here has side effects, so only timing changes.
- `ParseOptions.Default`, `EmptyReadOnlyList<T>.Instance`, `Diagnostic.NoDiagnostics`, `DiagnosticCategory.General/Correctness/Performance` (`Diagnostic.cs:189-191`), `DynamicBagSymbol.Empty`, `StringAndNumberComparer.Ordinal/OrdinalIgnoreCase` and `CustomElementDescriptor.Default` (`CustomNode.cs:233`) are **non-readonly** public statics in C#. Making them `final` is safe unless someone reassigns them; nothing in scope does.
- `SyntaxFacts.cs:776-791` sizes `kindToDataMap` from `Enum.GetValues<SyntaxKind>().Length` and indexes by `(int)kind`. Only `SyntaxKind.None = 0` is explicit (`SyntaxKind.cs:13`), so ordinals are dense. Java `ordinal()` must equal the C# value, so keep declaration order identical.

### 8c. Static fields in generic types: per-closed-type in C#, shared under Java erasure

| Site | Field | Effect of sharing in Java |
|---|---|---|
| `Parser/Combinators/Parsers/ForwardParser.cs:48-50` | `[ThreadStatic] static int s_callDepth` in `ForwardParser<TInput,TOutput>`, `MaxCallDepth = 30` | **Behavioural.** C# keeps a separate depth counter per `(TInput,TOutput)` pair. One Java static would count all forward parsers together and fall back to `SafeParser`/`SafeScanner` earlier. To mirror, key a `ThreadLocal<Map<Object,int[]>>` by the type pair, or give each `ForwardParser` a type token. |
| `Parsers.cs:25` | `Parsers<TInput>.Any` | Shareable (stateless). |
| `Parsers.cs:530`, `:799`; `ConvertParser.cs:108`; `ListPrimaryParser.cs:15`; `PartialParser.cs:205`, `:211`; `SafeParse.cs:26`; `SafeScan.cs:26` | Object pools | Sharing changes only contention and capacity. |
| `PartialParser.cs:879` | `Path.Empty` | Shareable. |
| `ListExtensions.cs:386`; `SafeList.cs:133`; `SubstringMap.cs:45`; `EmptyReadOnlyList.cs:8` | Immutable singletons | Shareable via an unchecked cast. `ToReadOnly` compares by **reference** against `EmptyReadOnlyList<T>.Instance` (`ListExtensions.cs:16`), and one shared instance keeps that working. |

`KustoCache` (`KustoCache.cs:35-64`) is keyed by `typeof(T)`. Java needs `Class<T>`. Only non-generic `T` are used: `GlobalBindingCache`, `CachedGrammar`, `CommandGrammar`.

**Category 8 totals:** 2 explicit static constructors, about 1,000 static fields (Functions 445, QueryParser 144, PlugIns 87, QueryOperatorParameters 87, Aggregates 64, KustoFacts 64, Operators 59, Options 59, ScalarTypes 36, TokenParser 39, Binder 37, others), 0 eager cycles, 1 lazy-broken cycle, 2 latent runtime cycles, 1 partial-order hazard, and 15 statics in generic types (1 behavioural).

---

## 9. Unsafe code, threading, lazy publication and pooling

- `unsafe`, `fixed`, `stackalloc`, `Span<T>`, `Memory<T>` and `ArrayPool`: **0 uses**. The padding structs in `Utils/Interlocked.cs:69-117` are dead code.
- `Lazy<T>`, `ThreadLocal<T>`, `async`/`await`, `Task`, `Parallel`, `volatile` and `ConditionalWeakTable`: **0 uses**.

`[ThreadStatic]`: 1, at `ForwardParser.cs:48`. Use `ThreadLocal` (see 8c).

`lock`: 8.
- `Utils/MostRecentlyUsedCache.cs:32`, `:59`: `lock(this)`. Use `synchronized(this)`.
  - Upstream bug: `AddOrUpdate` at `:64` sets `value = pair.Value`, so an update keeps the **old** value. Mirror it.
- `Binder/Binder_API.cs:170`, `:289`, `:316`, `:351`, `:385`: `lock (bindingCache)`.
- `Binder/Binder_FunctionCalls.cs:2030`: `lock (this._globalBindingCache)`.
- Monitor and `synchronized` are both reentrant, which matches.

`ConcurrentDictionary`: 1 wrapper, `Utils/ThreadSafeDictionary.cs:12-21`. It is used by `KustoCache.cs:12` and `GlobalBindingCache.cs:16-31`.
- `GetOrAdd(key, factory)` (`KustoCache.cs:53`, `Binder_FunctionCalls.cs:1991`, `:2200`) **can run the factory more than once** and never blocks.
- Java `ConcurrentHashMap.computeIfAbsent` runs it once, holds a bin lock, and throws `IllegalStateException("Recursive update")` if the factory touches the same map.
- The factories are trivial (`new T()`, a new MRU cache), but prefer a `get`-then-`putIfAbsent` sequence to mirror .NET.
- The non-concurrent fallback at `ThreadSafeDictionary.cs:62-69` (net472-) is a different `AddOrUpdate` overload. Same semantics.

`Interlocked` lazy publication (CAS from null): 31 call sites.
- `GlobalState.cs:616`, `667`, `694`, `715`, `744`, `772`, `862`, `921`, `939`, `1012`, `1043`, `1078`, `1190`
- `KustoCode.cs:378`, `394`, `452`
- `SyntaxElement.cs:196`
- `SyntaxNode.cs:289`
- `PatternSymbol.cs:111`
- `DynamicSymbol.cs:113`
- `GraphModelSymbol.cs:98`
- `ClusterSymbol.cs:109`
- `TableSymbol.cs:343`
- `QueryGrammar.cs:58`
- `CommandGrammar.cs:404`, `438`
- `Parser/Combinators/Source.cs:28`
- `ObjectPool.cs:29` (`Exchange` on an array slot), `:52`
- `SafeList.cs:52`, `:72` (int CAS for list ownership)

Java needs `VarHandle` (`compareAndSet`) or `AtomicReference(Array)`.

**Java memory model:** upstream reads the field with a plain load (`if (this.x == null)`). Several published objects are mutable `Dictionary`/`HashMap` instances (`GlobalState` maps, `TableSymbol.lazyColumnMap`, `DynamicSymbol`, `ClusterSymbol`). Make the fields `volatile`, or read them with `getAcquire`, to get safe publication.

`SafeList<T>` (`Utils/SafeList.cs`) shares one backing `List<T>` between versions. The owner appends while non-owners read indices below their `_length`. That is safe in .NET because `List<T>` replaces its array atomically. Java `ArrayList` is similar, but the read is a data race under the JMM. Document it, or copy on hand-off.

Object pooling (`Utils/ObjectPool.cs`, lock-free, 10 slots): 36 pools and 243 `AllocateFromPool` sites.
- Pool declarations: `Binder/Binder_Misc.cs:378-426` (17 pools), `TypeFacts.cs:506`, `:511`, `ParameterLayout.cs:95`, `:98`, `ParameterLayouts.cs:229`, `:232`, `Signature.cs:517`, `TextFacts.cs:458`, `ScannerExtensions.cs:39`, `SafeParse.cs:27`, `SafeScan.cs:27`, `ListPrimaryParser.cs:16`, `ConvertParser.cs:109`, `PartialParser.cs:206`, `:212`, `Parsers.cs:531`, `:800`, `Binder_NodeBinder.cs:2595`, `SyntaxNode_Semantics.cs:199`.
- Top allocation sites: `Binder_NodeBinder.cs` 139, `Binder_FunctionCalls.cs` 27, `Binder_TablesAndColumns.cs` 13, `Binder_Misc.cs` 9.
- Every pooled object is returned in a `finally` block and reset by the pool's resetter (`list.Clear()`). Java must keep these "materialise before return" points (section 7).

Cancellation:
- `Utils/Cancellation.cs:7` wraps `System.Threading.CancellationToken`. Checks happen at `Binder_TreeBinder.cs:53` (every node), `KustoCode.cs:240` and `SyntaxNode_Semantics.cs:123`.
- `KustoCode.cs:172`: `ParseAndAnalyze` passes `default(CancellationToken)` and ignores its own parameter. Upstream bug; mirror it.

**Category 9 totals:** 0 unsafe constructs, 1 `ThreadStatic`, 8 `lock` statements, 1 concurrent dictionary type (6 maps), 31 `Interlocked` sites, 36 object pools with 243 allocations, 0 `Lazy` or `ThreadLocal`.

---

## 10. Integer overflow, unsigned arithmetic, bit operations and hashing

- **checked/unchecked:** the project never enables checked arithmetic. There are no `checked` blocks. Explicit `unchecked` appears at `TextKeyedDictionary.cs:153`, `:172` and `ListExtensions.cs:425`. Java int arithmetic wraps the same way.
- **FNV-1a** (`TextKeyedDictionary.cs:153-176`):
  - `FnvOffsetBias = unchecked((int)2166136261)` becomes `-2128831035` in Java.
  - The hash is `(hash ^ text[i]) * 16777619`. Java `char` widens to `int` with zero-extension, same as .NET, so the results are identical.
- **Shifts:**
  - `KustoFacts.cs:982`: `value << 3` for octal.
  - `KustoFacts.cs:999`, `1003`, `1007`: `value << 4` for hex.
  - Eight-digit `\U` escapes can overflow to negative (`\UFFFFFFFF` gives -1). `char.ConvertFromUtf32` (`:940`) then throws. The lexer accepts any 8 hex digits (`TokenParser.cs:958-962`), so the exception fires lazily on `Token.Value`.
  - `Character.toChars` throws for negative and `> 0x10FFFF` but **accepts U+D800–DFFF**. .NET throws for those. Add the check.
- **Narrowing:**
  - `(char)DecodeHex(...)` and `(char)DecodeOctal(...)` at `KustoFacts.cs:933`, `947`, `954` truncate the same way in Java.
  - `(int)longVal` at `Binder/Binder_NodeBinder.cs:843` wraps the same way.
  - `(long)number` at `SyntaxToken.cs:712` differs (see 2b).
- **Unsigned:** only `UInt64` in `ValueComparer.cs:69-71` via the checked `Convert.ChangeType`. It is unreachable because no ulong literals exist.
- **Hash codes:**
  - `string.GetHashCode` is randomised per process in .NET Core.
  - It feeds `Diagnostic.GetHashCode` (`Diagnostic.cs:181-184`), `KeyComparer.GetHashCode` (`ListExtensions.cs:166-168`), `ReadOnlyListComparer.GetHashCode` (`ListExtensions.cs:415-428`) and every `Dictionary<string,…>`.
  - Identity hashes are used by `CallSiteInfo.cs:82-84` (`Signature` reference hash) and `PartialParser.cs:88-96` (sum of `parser.GetHashCode()`).
  - **No hash is observable.** No hash value is printed, sorted or compared, and .NET collection enumeration order does not depend on hashes (section 3). Java only has to keep `Linked*` collections. `String.hashCode` being deterministic is irrelevant.
- **Equality of boxed numbers:**
  - `object.Equals(boxed)` at `ValueComparer.cs:35` and `CallSiteInfo.cs:74`.
  - .NET `0.0.Equals(-0.0)` is **true**; Java `Double.equals` is **false**. NaN equals NaN in both.
  - `Long.equals(Integer)` and .NET `Equals(long, int)` are both false.

**Category 10 totals:** 3 `unchecked` sites, 0 `checked`, 4 shift sites, 4 narrowing-cast sites (`KustoFacts.cs:933`, `947`, `954`; `Binder_NodeBinder.cs:843`) plus the double→long cast at `SyntaxToken.cs:712` (see 2b), 1 unsigned site (dead), 6 custom `GetHashCode` implementations, 0 observable hashes.

---

## 11. Exceptions as control flow, and recursion depth

### 11a. Exceptions

- **`catch`: 1 site.** `Binder/Binder_FunctionCalls.cs:2103`, `catch (Exception) { expansion = null; }`, wraps function-body expansion binding (`:2069-2106`).
  - It swallows `OperationCanceledException`, so **cancellation is lost during expansion**.
  - It also swallows `Ensure.*` failures (`InvalidOperationException`) and implicit BCL throws.
  - Java: `catch (RuntimeException e)`. Decide whether `StackOverflowError` should also be caught. .NET cannot catch stack overflow; the process dies.
- **`try`/`finally`:** 186 `try` blocks. Nearly all return pooled objects or restore binder state (`_pathScope`, `_isFuzzy`, `_dynamicDepth`, `_localBindingCache.SignaturesComputingExpansion`).
- **`throw new`:** 79 sites.

| Exception | Count |
|---|---|
| `ArgumentNullException` | 44 |
| `InvalidOperationException` | 17 |
| `ArgumentOutOfRangeException` | 6 |
| `NotImplementedException` | 5 (`SafeParse.cs:334`, `515`, `526`; `Binder_FunctionCalls.cs:621`; `Binder_NodeBinder.cs:580`) |
| `ArgumentException` | 3 |
| `IndexOutOfRangeException` | 2 |
| `OperationCanceledException` | 1 |

None of these is caught except by the catch above. **No exception is used for normal control flow.** Absence is signalled with TryParse and Try* patterns (section 6).

Implicit BCL throws Java must reproduce:
- `char.ConvertFromUtf32` at `KustoFacts.cs:940`.
- `TimeSpan.FromX` overflow and NaN (`SyntaxToken.cs:675-712`).
- `Convert.ChangeType` (`ValueComparer.cs:26-27`).
- `Enumerable.Max` on empty input (`PartialParser.cs:309`, `365`, `1068`).
- `Dictionary.Add` on a duplicate key (`ScalarTypes.cs:369`, `373` inside the static constructor, which becomes `TypeInitializationException`).
- `ToDictionary` on duplicates (`PlugIns.cs:1173`, `TokenParser.cs:1281`).
- The dictionary indexer (`TokenParser.cs:1287-1317`).
- The `SafeList` indexer (`SafeList.cs:38`).

### 11b. Recursion guards

| Guard | Where | What |
|---|---|---|
| `MaxDepth = 300` | `Parser/QueryParser.cs:141`; `_depth` at `:146`; `StackSafeParse` at `:168-199` | Hand-written recursive-descent parser. Applies only at **2 entry points**: `ParseJsonValue` (`:1514`) and `ParseUnnamedExpression` (`:3236`). Past depth 300 it switches to the stack-safe combinator grammar (`QueryGrammar.From(GlobalState.Default…)`, `:176`), interpreted by the explicit-stack `StackSafeParser` (`SafeParse.cs:31-42`, `List<ParseState>`). `_depth++/--` is not in `try/finally` (`:195-197`). Between two counted levels the parser descends through every precedence level, so one unit of `_depth` is roughly 10–20 Java frames. |
| `MaxCallDepth = 30` | `Parser/Combinators/Parsers/ForwardParser.cs:47-106` | Combinator `Forward` recursion, counted per closed generic type (section 8c). Falls back to `SafeParser.ParseSafe` and `SafeScanner.ScanSafe`. |
| `MaxAnalysisDepth = 500` | `Properties.cs:31-32`; `SyntaxTree.IsSafeToRecurse` at `SyntaxTree.cs:53-54` | Checked before binding at `Binder_API.cs:165`, `225`, `312`, `347`, `381`. If the tree is deeper, binding is skipped: `AnalysisState.NotSafe` (`KustoCode.cs:252`) and diagnostic KS245 (`KustoCode.cs:375`, `DiagnosticFacts.cs:972-975`). Tree depth comes from the **iterative** `ComputeMaxDepth` (`SyntaxTree.cs:59-75`). |
| Expansion cycle guard | `Binder_FunctionCalls.cs:2052-2056`, `:2121-2124` | `SignaturesComputingExpansion` blocks recursive expansion of the same signature. It is a cycle guard, **not a depth guard**. Nested distinct functions each start a new `Binder` on the same stack (`TryBindCalledFunctionBody`), so total stack is about the sum of nested depths, each up to 500. |

Iterative, stack-safe walkers: `SyntaxElement.WalkElements` (`SyntaxElement.cs:754-801`) and `WalkNodes` (`:811-860`).

Unguarded recursion:
- `TreeBinder.DefaultVisit` → `VisitChildren` → `Accept` (`Binder_TreeBinder.cs:26-47`): about 3 frames per tree level, pre-gated by the 500 check.
- `NodeBinder` visits and `ContextBuilder`.
- `SyntaxElement.HasMissingChildren` (`SyntaxElement.cs:316-329`, `// TODO: redo this to not be recursive`).
- `GatherDiagnostics` recursion into called function bodies (`SyntaxNode_Semantics.cs:153`, `:180`).
- `ParameterLayouts.GetArgumentParameters` (`ParameterLayouts.cs:114`, `126`, `244`), which is shallow.
- `KustoFacts.Matches` wildcard recursion, one level per `*` (`KustoFacts.cs:1269-1333`, the private `Matches` overload).
- Generated `CloneCore`/`Accept`/`Visit*` (not in repo).

Java risk:
- The thresholds were tuned for .NET stacks: 1 MB per thread on Windows; on Linux the main thread follows `ulimit` (typically 8 MB) and secondary threads default to 1.5 MB.
- Java's default `-Xss` is usually 1 MB on Linux x64. Java frames for visitor double-dispatch are often larger.
- Add tests at depth 299/301 (parser), 30/31 (forward), and 499/501 (binder), with nested function expansions.
- Consider running parse and analyze on a thread with an explicit stack size, for example `new Thread(null, r, "kql", 64L << 20)`.

**Category 11 totals:** 1 `catch`, 186 `try`, 79 `throw new`, 3 cancellation points, 3 depth guards plus 1 cycle guard, 2 iterative walkers, and at least 6 unguarded recursive paths.

---

## Summary of totals

| # | Category | Total |
|---|---|---|
| 1 | Character classification | 21 BCL char calls, 6 string case maps, 11 `Trim()`, 12 hand-written predicates |
| 2 | Literal parse/format | 12 Parse/TryParse, 0 culture or format-string arguments, 10 visible implicit-`ToString` sites |
| 3 | Ordering | 8 visible dictionary/set enumerations; 9 sort sites (1 unstable culture sort) |
| 4 | String comparison | 17 culture `Compare`, 10 culture `StartsWith`/`EndsWith`, 2 culture `IndexOf`, 23 explicit ordinal |
| 5 | Structs | 14 types (3 mutable, 3 dead) |
| 6 | ref/out | 59 declarations, 31 `Interlocked` `ref field` sites |
| 7 | Iterators | 4 methods, 7 `yield`; none per-token or per-node |
| 8 | Static init | 2 static constructors, about 1,000 static fields, 0 eager cycles, 1 partial-order hazard, 1 generic-static behavioural trap |
| 9 | Threading/pooling | 0 unsafe, 1 `ThreadStatic`, 8 `lock`, 31 CAS, 36 pools with 243 allocations |
| 10 | Overflow/hash | 3 `unchecked`, 0 `checked`, 0 observable hashes; `-0.0` equality and `(long)double` differ |
| 11 | Exceptions/recursion | 1 `catch`, 79 `throw`; parser guards 300/30, binder guard 500 |

## Top 12 must-fix items (ranked)

1. Use `LinkedHashMap`/`LinkedHashSet` for every enumerated Dictionary/HashSet. Never use `EnumMap` for `QueryGrammar.StringOperatorMap` (section 3a).
2. Merged `Functions.java`: put the `Functions.Convert.cs` members before `All` (section 8a).
3. `OrdinalIgnoreCase`: compare upper-case per char. Never use `CASE_INSENSITIVE_ORDER` or `equalsIgnoreCase` (sections 3b, 4d).
4. ASCII-only integer parsing. .NET TryParse semantics for long/int/double/decimal, defaulting to 0 (sections 2a, 2b).
5. A .NET-compatible `toString` for double, bool, decimal and TimeSpan in generated column names (section 2c).
6. A custom `Opt<T>` that can carry null. Not `java.util.Optional` (section 5).
7. Per-closed-generic `ForwardParser` call depth (section 8c).
8. A `.NET Trim()` whitespace set and the literal `TextFacts.IsWhitespace` switch (sections 1c, 1e).
9. Pick the culture policy, ideally invariant/ordinal, and generate golden tests under it (sections 0, 4a).
10. Safe publication for CAS-published mutable maps (section 9).
11. `ConvertFromUtf32` surrogate check, `TimeSpan` overflow throws, and `(long)double` semantics (sections 2b, 10).
12. Stack-depth tests at guard boundaries, and a larger thread stack for parse and analyze (section 11b).
