// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: header, banned-API and marker rules for port sources (PORTING.md 2.1, 2.7, 5.4).

package org.graylog.kusto.language.conformance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Source rules for {@code kusto-language} (mirrored by {@code porting/tools/check_headers.py} and
 * {@code check_pending.py}):
 * <ol type="a">
 *   <li>Header: one of the two PORTING.md 2.1 forms. Ported form: one or more
 *       {@code // Ported from: src/....cs} lines ({@code .tt}, optionally followed by a
 *       parenthesised note, for generated files, PORTING.md 4.3), the {@code // Upstream:} line
 *       whose sha equals the manifest {@code upstreamCommit}, the SPDX line, an optional
 *       {@code // Copyright (c) Microsoft Corporation...} line, then either the upstream-license
 *       and derived-work lines or the generated-file license line.</li>
 *   <li>Banned APIs outside {@code utils/dotnet/}, matched on code only (comments, string, char
 *       and text-block contents blanked), see {@link #BANNED}; {@code substring(a, b)} needs a
 *       second argument of the form {@code x + y} (PORTING.md 5.4).</li>
 *   <li>Every {@code // PORT:} line comment cites a rule ({@code §3.6}) or a D-row ({@code D12}).</li>
 *   <li>{@code // PORT-PENDING: W<n>} and {@code // PORT-SKELETON: W<n>} markers are collected
 *       per wave; a marker without a wave is a violation.</li>
 * </ol>
 */
public final class SourceRules {
    public static final String ORIGINAL_1 = "// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0";
    static final Pattern ORIGINAL_2 = Pattern.compile("// Copyright \\(c\\) \\d{4} Graylog, Inc\\. Purpose: \\S.*");
    static final Pattern PORTED_FROM = Pattern.compile("// Ported from: src/\\S+\\.(cs|tt)( \\(.+\\))?");
    static final Pattern UPSTREAM = Pattern.compile("// Upstream: microsoft/Kusto-Query-Language @ ([0-9a-f]{40})");
    public static final String SPDX = "// SPDX-License-Identifier: Apache-2.0";
    static final Pattern MS_COPYRIGHT = Pattern.compile("// Copyright \\(c\\) Microsoft Corporation\\..*");
    public static final String UPSTREAM_LICENSE = "// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.";
    public static final String DERIVED = "// This file is a derived work; see NOTICE. Modifications are marked \"// PORT:\".";
    static final Pattern GENERATED_LICENSE = Pattern.compile("// Upstream license: Apache-2\\.0\\. This file is GENERATED .*");

    /** Banned tokens (label to regex), applied per line of blanked code. */
    public static final Map<String, Pattern> BANNED = new LinkedHashMap<>();

    static {
        banned("java.util.stream", "\\bjava\\s*\\.\\s*util\\s*\\.\\s*stream\\b");
        banned("HashMap<", "\\bHashMap\\s*<");
        banned("new HashMap", "\\bnew\\s+HashMap\\b");
        banned("HashSet<", "\\bHashSet\\s*<");
        banned("new HashSet", "\\bnew\\s+HashSet\\b");
        banned("EnumMap", "\\bEnumMap\\b");
        banned("CASE_INSENSITIVE_ORDER", "\\bCASE_INSENSITIVE_ORDER\\b");
        banned("equalsIgnoreCase(", "\\bequalsIgnoreCase\\s*\\(");
        banned(".trim()", "\\.\\s*trim\\s*\\(\\s*\\)");
        banned(".strip()", "\\.\\s*strip\\s*\\(\\s*\\)");
        banned(".isBlank()", "\\.\\s*isBlank\\s*\\(\\s*\\)");
        banned(".split(", "\\.\\s*split\\s*\\(");
        banned("String.join(", "\\bString\\s*\\.\\s*join\\s*\\(");
        banned("Double.parseDouble(", "\\bDouble\\s*\\.\\s*parseDouble\\s*\\(");
        banned("Long.parseLong(", "\\bLong\\s*\\.\\s*parseLong\\s*\\(");
        banned("Integer.parseInt(", "\\bInteger\\s*\\.\\s*parseInt\\s*\\(");
        banned("UUID.fromString(", "\\bUUID\\s*\\.\\s*fromString\\s*\\(");
        banned("ThreadLocalRandom", "\\bThreadLocalRandom\\b");
        banned("assert", "^\\s*assert\\b");
    }

    private static void banned(String label, String regex) {
        BANNED.put(label, Pattern.compile(regex));
    }

    static final Pattern SUBSTRING = Pattern.compile("\\.\\s*substring\\s*\\(");
    static final Pattern PORT_MARKER = Pattern.compile("^//\\s*PORT:");
    static final Pattern PORT_CITATION = Pattern.compile("§\\d+(\\.\\d+)?|\\bD\\d+\\b");
    static final Pattern WAVE_MARKER = Pattern.compile("^//\\s*PORT-(PENDING|SKELETON)\\b:?\\s*(W\\d+[a-z]?\\b)?");

    /** A rule violation. */
    public record Violation(String file, int line, String rule, String detail) {
        @Override
        public String toString() {
            return file + ":" + line + ": [" + rule + "] " + detail;
        }
    }

    /** A {@code PORT-PENDING}/{@code PORT-SKELETON} marker. */
    public record Marker(String file, int line, String kind, String wave) {
    }

    /** Scan result. */
    public record Scan(List<Violation> violations, List<Marker> markers) {
        /** Markers grouped by wave ({@code W2}), waves sorted. */
        public Map<String, List<Marker>> byWave() {
            Map<String, List<Marker>> m = new TreeMap<>();
            for (Marker k : markers) {
                m.computeIfAbsent(k.wave(), w -> new ArrayList<>()).add(k);
            }
            return m;
        }
    }

    /** Source with comment/literal contents blanked, plus its line comments. */
    record Lexed(String code, List<int[]> lineComments, String raw) {
    }

    private SourceRules() {
    }

    /** Scans every {@code .java} file under {@code root}; banned APIs only when {@code checkBanned}. */
    public static Scan scanTree(Path root, String upstream, boolean checkBanned) {
        List<Violation> v = new ArrayList<>();
        List<Marker> m = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Path p : files) {
            String rel = root.relativize(p).toString().replace('\\', '/');
            String src;
            try {
                src = Files.readString(p, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            Scan s = scanFile(rel, src, upstream, checkBanned);
            v.addAll(s.violations());
            m.addAll(s.markers());
        }
        return new Scan(v, m);
    }

    public static Scan scanFile(String rel, String src, String upstream, boolean checkBanned) {
        List<Violation> v = new ArrayList<>(header(rel, src, upstream));
        Lexed lx = lex(src);
        if (checkBanned && !rel.contains("utils/dotnet/")) {
            v.addAll(banned(rel, lx));
        }
        List<Marker> markers = new ArrayList<>();
        v.addAll(markers(rel, src, lx, markers));
        return new Scan(v, markers);
    }

    /** (a) header forms. */
    public static List<Violation> header(String rel, String src, String upstream) {
        String[] lines = src.split("\r?\n", -1);
        List<Violation> v = new ArrayList<>();
        if (lines.length > 0 && lines[0].equals(ORIGINAL_1)) {
            if (lines.length < 2 || !ORIGINAL_2.matcher(lines[1]).matches()) {
                v.add(new Violation(rel, 2, "header", "expected '// Copyright (c) <year> Graylog, Inc. Purpose: <one line>'"));
            }
            return v;
        }
        int i = 0;
        while (i < lines.length && PORTED_FROM.matcher(lines[i]).matches()) {
            i++;
        }
        if (i == 0) {
            v.add(new Violation(rel, 1, "header", "first line is neither '" + ORIGINAL_1 + "' nor '// Ported from: src/<path>.cs'"));
            return v;
        }
        Matcher up = i < lines.length ? UPSTREAM.matcher(lines[i]) : null;
        if (up == null || !up.matches()) {
            v.add(new Violation(rel, i + 1, "header", "expected '// Upstream: microsoft/Kusto-Query-Language @ <40 hex>'"));
            return v;
        }
        if (!up.group(1).equals(upstream)) {
            v.add(new Violation(rel, i + 1, "header", "upstream " + up.group(1) + " != manifest upstreamCommit " + upstream));
        }
        i++;
        if (i >= lines.length || !lines[i].equals(SPDX)) {
            v.add(new Violation(rel, i + 1, "header", "expected '" + SPDX + "'"));
            return v;
        }
        i++;
        if (i < lines.length && MS_COPYRIGHT.matcher(lines[i]).matches()) {
            i++;
        }
        if (i < lines.length && GENERATED_LICENSE.matcher(lines[i]).matches()) {
            return v;
        }
        if (i >= lines.length || !lines[i].equals(UPSTREAM_LICENSE)) {
            v.add(new Violation(rel, i + 1, "header", "expected '" + UPSTREAM_LICENSE + "'"));
            return v;
        }
        i++;
        if (i >= lines.length || !lines[i].equals(DERIVED)) {
            v.add(new Violation(rel, i + 1, "header", "expected '" + DERIVED + "'"));
        }
        return v;
    }

    /** (b) banned APIs and non-additive substring end arguments. */
    static List<Violation> banned(String rel, Lexed lx) {
        List<Violation> v = new ArrayList<>();
        String[] lines = lx.code().split("\n", -1);
        for (int n = 0; n < lines.length; n++) {
            for (Map.Entry<String, Pattern> e : BANNED.entrySet()) {
                if (e.getValue().matcher(lines[n]).find()) {
                    v.add(new Violation(rel, n + 1, "banned", e.getKey()));
                }
            }
        }
        String code = lx.code();
        Matcher m = SUBSTRING.matcher(code);
        while (m.find()) {
            List<String> args = arguments(code, m.end());
            // Exempt: a literal 0 start (0 + len == len) and sites marked "// PORT: §5.4", which
            // are EditString.substring(start, length) calls that mirror upstream by design.
            if (args != null && args.size() == 2 && !additive(args.get(1))
                    && !"0".equals(args.get(0).strip()) && !lineHasMarker(lx, code, m.start(), "\u00a75.4")) {
                v.add(new Violation(rel, lineOf(code, m.start()), "banned",
                        "substring end argument '" + args.get(1).strip() + "' is not '<expr> + <expr>' (PORTING.md 5.4)"));
            }
        }
        return v;
    }

    /** True when the line containing {@code offset} ends in a line comment containing {@code text}. */
    static boolean lineHasMarker(Lexed lx, String code, int offset, String text) {
        int line = lineOf(code, offset);
        for (int[] c : lx.lineComments()) {
            if (lineOf(code, c[0]) == line && lx.raw().substring(c[0], c[1]).contains(text)) {
                return true;
            }
        }
        return false;
    }

    /** Top-level arguments of the call whose '(' ends just before {@code from}; null if unbalanced. */
    static List<String> arguments(String code, int from) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        int start = from;
        for (int i = from; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                if (depth == 0) {
                    String last = code.substring(start, i);
                    if (!args.isEmpty() || !last.isBlank()) {
                        args.add(last);
                    }
                    return args;
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                args.add(code.substring(start, i));
                start = i + 1;
            }
        }
        return null;
    }

    /** Whether {@code expr} is a binary {@code +} at top level (after removing wrapping parens). */
    static boolean additive(String expr) {
        String e = expr.strip();
        while (e.startsWith("(") && closingParen(e, 0) == e.length() - 1) {
            e = e.substring(1, e.length() - 1).strip();
        }
        int depth = 0;
        for (int i = 0; i < e.length(); i++) {
            char c = e.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == '+' && depth == 0) {
                boolean increment = (i + 1 < e.length() && e.charAt(i + 1) == '+') || (i > 0 && e.charAt(i - 1) == '+');
                String left = e.substring(0, i).strip();
                if (!increment && !left.isEmpty() && "+-*/%(,=<>!&|^?:".indexOf(left.charAt(left.length() - 1)) < 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int closingParen(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** (c) and (d): {@code // PORT:} citations and wave markers. */
    static List<Violation> markers(String rel, String src, Lexed lx, List<Marker> out) {
        List<Violation> v = new ArrayList<>();
        for (int[] c : lx.lineComments()) {
            String text = src.substring(c[0], c[1]);
            int line = lineOf(src, c[0]);
            if (PORT_MARKER.matcher(text).find()) {
                if (!PORT_CITATION.matcher(text).find()) {
                    v.add(new Violation(rel, line, "marker", "'// PORT:' cites neither a rule (§n.n) nor a D-row (Dn): " + text.strip()));
                }
                continue;
            }
            Matcher w = WAVE_MARKER.matcher(text);
            if (w.find()) {
                if (w.group(2) == null) {
                    v.add(new Violation(rel, line, "marker", "PORT-" + w.group(1) + " without a wave (W<n>): " + text.strip()));
                } else {
                    out.add(new Marker(rel, line, "PORT-" + w.group(1), w.group(2)));
                }
            }
        }
        return v;
    }

    static int lineOf(String s, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (s.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** Blanks comments and literal contents (newlines kept) and records line-comment spans. */
    static Lexed lex(String src) {
        StringBuilder code = new StringBuilder(src);
        List<int[]> comments = new ArrayList<>();
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                int end = src.indexOf('\n', i);
                end = end < 0 ? n : end;
                if (end > i && src.charAt(end - 1) == '\r') {
                    end--;
                }
                comments.add(new int[] {i, end});
                blank(code, i, end);
                i = end;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int end = src.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                blank(code, i, end);
                i = end;
            } else if (c == '"' && src.startsWith("\"\"\"", i)) {
                int end = i + 3;
                while (end < n && !src.startsWith("\"\"\"", end)) {
                    end += src.charAt(end) == '\\' ? 2 : 1;
                }
                end = Math.min(n, end);
                blank(code, i + 3, end);
                i = Math.min(n, end + 3);
            } else if (c == '"' || c == '\'') {
                int end = i + 1;
                while (end < n && src.charAt(end) != c && src.charAt(end) != '\n') {
                    end += src.charAt(end) == '\\' ? 2 : 1;
                }
                end = Math.min(n, end);
                blank(code, i + 1, end);
                i = end + 1;
            } else {
                i++;
            }
        }
        return new Lexed(code.toString(), comments, src);
    }

    private static void blank(StringBuilder b, int from, int to) {
        for (int k = from; k < to; k++) {
            if (b.charAt(k) != '\n' && b.charAt(k) != '\r') {
                b.setCharAt(k, ' ');
            }
        }
    }
}
