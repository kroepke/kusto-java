// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: generates the LexicalTokenParsers facade (TInput = LexicalToken) from the Parsers.java source (PORTING.md 3.10, D7).
package org.graylog.kusto.language.generator;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emits {@code parsing/LexicalTokenParsers.java}: the non-generic facade over {@code Parsers} with
 * {@code TInput} bound to {@code LexicalToken}, so grammar files can {@code import static
 * …LexicalTokenParsers.*} in place of upstream's {@code using static Parsers<LexicalToken>} (PORTING.md 3.10, D7).
 *
 * <p>The member list is derived by <b>parsing the source text</b> of {@code parsing/Parsers.java}, not by
 * reflection: {@code kusto-language} depends on this module at build time (profile {@code regenerate}), so
 * this module cannot depend on it. The parser is deliberately small. It relies on the regular layout of
 * {@code Parsers.java}: every class member starts on a line indented by exactly four spaces with
 * {@code public static}, its signature ends at the first <code>{</code> outside comments, and type
 * parameters carry no bounds containing parentheses. Every public static generic method whose type
 * parameters include {@code TInput} gets one delegating method; the others ({@code Character}-only members
 * such as {@code match(char)}, {@code convertText}, {@code text}, {@code textAndOffset}; the raw field
 * {@code Any}; nested types) are skipped. The facade's own {@code Any} field is the typed
 * {@code Parsers.any()}.
 */
public final class LexicalTokenParsersGenerator {
    private LexicalTokenParsersGenerator() {
    }

    public static final String PACKAGE = "org.graylog.kusto.language.parsing";
    public static final String SOURCE_FILE = "Parsers.java";
    public static final String OUTPUT_FILE = "LexicalTokenParsers.java";
    public static final String CLASS_NAME = "LexicalTokenParsers";

    private static final String TYPE_PARAMETER = "TInput";
    private static final String TYPE_ARGUMENT = "LexicalToken";

    private static final Pattern MEMBER = Pattern.compile("^    public static (.*)$");
    private static final Pattern NESTED_TYPE = Pattern.compile(
            "^    public static (?:final |abstract )*(?:class|interface|record|enum) (\\w+)");
    private static final Pattern IMPORT = Pattern.compile("^import ([\\w.]+)\\.(\\w+);$");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");

    /** One parameter of a {@code Parsers} method, as spelled in the source. */
    public record Parameter(String type, String name, boolean varargs) {
    }

    /** One public static generic method of {@code Parsers}, as spelled in the source. */
    public record Method(String name, List<String> typeParameters, String returnType, List<Parameter> parameters,
            int line) {
        /** True when {@code TInput} is one of the method's type parameters. */
        public boolean hasInputTypeParameter() {
            return typeParameters.stream().anyMatch(t -> typeParameterName(t).equals(TYPE_PARAMETER));
        }
    }

    /** The parsed contents of {@code Parsers.java} needed by the facade. */
    public record ParsersSource(List<Method> methods, List<String> skipped, Set<String> nestedTypes,
            List<String> imports) {
        /** The methods that become facade members. */
        public List<Method> facadeMethods() {
            return methods.stream().filter(Method::hasInputTypeParameter).toList();
        }
    }

    /** The package directory of {@code Parsers.java} and the facade below a source root. */
    public static Path parsingDirectory(Path sourceRoot) {
        return sourceRoot.resolve(PACKAGE.replace('.', '/'));
    }

    /**
     * Generates the facade into (or, with {@code check}, compares it against) the parsing package below
     * {@code sourceRoot}.
     *
     * @return the exit status: 0 when written or up to date, 1 when {@code check} found a difference
     * @throws UncheckedIOException when {@code Parsers.java} cannot be read
     * @throws IllegalStateException when {@code Parsers.java} does not have the expected layout
     */
    public static int run(Path sourceRoot, boolean check, PrintStream out) {
        Path dir = parsingDirectory(sourceRoot);
        String source = read(dir.resolve(SOURCE_FILE));
        if (source == null) {
            throw new UncheckedIOException(new NoSuchFileException(dir.resolve(SOURCE_FILE).toString()));
        }
        ParsersSource parsed = parse(source);
        String generated = generate(parsed);
        List<Method> facade = parsed.facadeMethods();
        long names = facade.stream().map(Method::name).distinct().count();

        Path file = dir.resolve(OUTPUT_FILE);
        String current = read(file);
        boolean upToDate = generated.equals(current);
        if (check) {
            if (!upToDate) {
                out.println((current == null ? "missing: " : "differs: ") + OUTPUT_FILE);
            }
            out.printf("checked %s (%d methods, %d names, %d skipped): %s%n", OUTPUT_FILE, facade.size(), names,
                    parsed.skipped().size(), upToDate ? "up to date" : "out of date");
            if (!upToDate) {
                out.println(GenerateSyntaxNodes.REGENERATE_HINT);
                return 1;
            }
            return 0;
        }
        if (!upToDate) {
            try {
                Files.writeString(file, generated, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        out.printf("generated %s (%d methods, %d names, %d skipped): %s%n", file, facade.size(), names,
                parsed.skipped().size(), upToDate ? "unchanged" : "written");
        return 0;
    }

    // ---------------------------------------------------------------------------------------------
    // Source parsing
    // ---------------------------------------------------------------------------------------------

    /** Parses the public static members of the {@code Parsers.java} source text. */
    public static ParsersSource parse(String source) {
        String[] lines = source.split("\n", -1);
        var methods = new ArrayList<Method>();
        var skipped = new ArrayList<String>();
        var nested = new LinkedHashSet<String>();
        var imports = new ArrayList<String>();

        for (int i = 0; i < lines.length; i++) {
            String line = stripCr(lines[i]);
            Matcher imp = IMPORT.matcher(line);
            if (imp.matches()) {
                imports.add(imp.group(1) + "." + imp.group(2));
                continue;
            }
            Matcher nt = NESTED_TYPE.matcher(line);
            if (nt.find()) {
                nested.add(nt.group(1));
                skipped.add(nt.group(1) + " (nested type)");
                continue;
            }
            Matcher m = MEMBER.matcher(line);
            if (!m.matches()) {
                continue;
            }
            // collect the signature up to the opening brace (methods) or '=' (fields), comments removed
            var signature = new StringBuilder();
            int j = i;
            boolean isMethod = false;
            for (; j < lines.length; j++) {
                String text = stripComments(stripCr(lines[j]));
                int brace = text.indexOf('{');
                if (brace >= 0) {
                    signature.append(text, 0, brace);
                    isMethod = true;
                    break;
                }
                int assign = text.indexOf('=');
                if (assign >= 0 && text.indexOf('(') < 0 && signature.indexOf("(") < 0) {
                    signature.append(text, 0, assign);
                    break;
                }
                if (text.trim().endsWith(";")) {
                    signature.append(text);
                    break;
                }
                signature.append(text).append(' ');
            }
            if (j == lines.length) {
                throw new IllegalStateException("Parsers.java:" + (i + 1) + ": unterminated member declaration");
            }
            String decl = signature.toString().trim().replaceAll("\\s+", " ");
            if (!decl.startsWith("public static ")) {
                throw new IllegalStateException("Parsers.java:" + (i + 1) + ": unexpected member: " + decl);
            }
            String rest = decl.substring("public static ".length()).trim();
            if (!isMethod) {
                skipped.add(lastIdentifier(rest) + " (field)");
                i = j;
                continue;
            }
            Method method = parseMethod(rest, i + 1);
            if (method.typeParameters().isEmpty()) {
                skipped.add(describe(method) + " (not generic)");
            } else if (!method.hasInputTypeParameter()) {
                skipped.add(describe(method) + " (TInput is not a type parameter)");
            }
            methods.add(method);
            i = j;
        }
        if (methods.isEmpty()) {
            throw new IllegalStateException("Parsers.java: no public static methods found");
        }
        return new ParsersSource(List.copyOf(methods), List.copyOf(skipped), Set.copyOf(nested), List.copyOf(imports));
    }

    private static Method parseMethod(String rest, int line) {
        List<String> typeParameters = List.of();
        if (rest.startsWith("<")) {
            int close = matching(rest, 0, '<', '>');
            typeParameters = splitTopLevel(rest.substring(1, close));
            rest = rest.substring(close + 1).trim();
        }
        int open = rest.indexOf('(');
        int close = rest.lastIndexOf(')');
        if (open < 0 || close < open || !rest.substring(close + 1).isBlank()) {
            throw new IllegalStateException("Parsers.java:" + line + ": cannot parse method signature: " + rest);
        }
        String head = rest.substring(0, open).trim();
        String name = lastIdentifier(head);
        String returnType = head.substring(0, head.length() - name.length()).trim();
        if (returnType.isEmpty()) {
            throw new IllegalStateException("Parsers.java:" + line + ": missing return type: " + rest);
        }
        var parameters = new ArrayList<Parameter>();
        for (String p : splitTopLevel(rest.substring(open + 1, close))) {
            String text = p.replaceAll("^(final |@\\w+ )+", "").trim();
            String pname = lastIdentifier(text);
            String type = text.substring(0, text.length() - pname.length()).trim();
            boolean varargs = type.endsWith("...");
            if (varargs) {
                type = type.substring(0, type.length() - 3).trim();
            }
            if (type.isEmpty()) {
                throw new IllegalStateException("Parsers.java:" + line + ": cannot parse parameter: " + p);
            }
            parameters.add(new Parameter(type, pname, varargs));
        }
        return new Method(name, typeParameters, returnType, List.copyOf(parameters), line);
    }

    private static String describe(Method m) {
        var sb = new StringBuilder(m.name()).append('(');
        for (int k = 0; k < m.parameters().size(); k++) {
            Parameter p = m.parameters().get(k);
            sb.append(k == 0 ? "" : ", ").append(p.type()).append(p.varargs() ? "..." : "");
        }
        return sb.append(')').toString();
    }

    private static String stripCr(String s) {
        return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
    }

    /** Removes block comments and a trailing line comment (signatures hold no string literals). */
    private static String stripComments(String s) {
        s = s.replaceAll("/\\*.*?\\*/", " ");
        int slash = s.indexOf("//");
        return slash >= 0 ? s.substring(0, slash) : s;
    }

    private static int matching(String s, int from, char open, char close) {
        int depth = 0;
        for (int k = from; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == open) {
                depth++;
            } else if (c == close && --depth == 0) {
                return k;
            }
        }
        throw new IllegalStateException("unbalanced '" + open + "' in: " + s);
    }

    /** Splits on commas outside angle brackets and parentheses. */
    private static List<String> splitTopLevel(String s) {
        var parts = new ArrayList<String>();
        int depth = 0;
        int start = 0;
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '<' || c == '(') {
                depth++;
            } else if (c == '>' || c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(s.substring(start, k).trim());
                start = k + 1;
            }
        }
        String last = s.substring(start).trim();
        if (!last.isEmpty() || !parts.isEmpty()) {
            parts.add(last);
        }
        return parts;
    }

    private static String lastIdentifier(String s) {
        Matcher m = Pattern.compile("([A-Za-z_$][\\w$]*)\\s*$").matcher(s);
        if (!m.find()) {
            throw new IllegalStateException("no identifier at the end of: " + s);
        }
        return m.group(1);
    }

    private static String typeParameterName(String typeParameter) {
        Matcher m = IDENTIFIER.matcher(typeParameter);
        if (!m.find()) {
            throw new IllegalStateException("bad type parameter: " + typeParameter);
        }
        return m.group();
    }

    // ---------------------------------------------------------------------------------------------
    // Emission
    // ---------------------------------------------------------------------------------------------

    /** Generates the facade source text. */
    public static String generate(ParsersSource parsed) {
        List<Method> facade = parsed.facadeMethods();
        var members = new StringBuilder();
        var used = new TreeSet<String>();

        for (Method m : facade) {
            var typeParameters = new ArrayList<String>();
            var typeArguments = new ArrayList<String>();
            for (String tp : m.typeParameters()) {
                String n = typeParameterName(tp);
                if (n.equals(TYPE_PARAMETER)) {
                    typeArguments.add(TYPE_ARGUMENT);
                } else {
                    typeParameters.add(bind(tp, parsed));
                    typeArguments.add(n);
                }
            }
            Set<String> variables = new LinkedHashSet<>();
            for (String tp : typeParameters) {
                variables.add(typeParameterName(tp));
            }

            String returnType = bind(m.returnType(), parsed);
            collectIdentifiers(returnType, used);
            var params = new StringBuilder();
            var args = new StringBuilder();
            boolean safeVarargs = false;
            for (int k = 0; k < m.parameters().size(); k++) {
                Parameter p = m.parameters().get(k);
                String type = bind(p.type(), parsed);
                collectIdentifiers(type, used);
                if (k > 0) {
                    params.append(", ");
                    args.append(", ");
                }
                params.append(type).append(p.varargs() ? "... " : " ").append(p.name());
                args.append(p.name());
                if (p.varargs() && (type.contains("<") || variables.contains(type))) {
                    safeVarargs = true;
                }
            }

            members.append('\n');
            if (safeVarargs) {
                // forwarding the generic varargs array is safe; "varargs" silences javac's -Xlint warning for it
                members.append("    @SafeVarargs\n");
                members.append("    @SuppressWarnings(\"varargs\")\n");
            }
            members.append("    public static ");
            if (!typeParameters.isEmpty()) {
                members.append('<').append(String.join(", ", typeParameters)).append("> ");
            }
            members.append(returnType).append(' ').append(m.name()).append('(').append(params).append(")\n");
            members.append("    {\n");
            members.append("        return Parsers.<").append(String.join(", ", typeArguments)).append('>')
                    .append(m.name()).append('(').append(args).append(");\n");
            members.append("    }\n");
        }

        var imports = new ArrayList<String>();
        for (String imp : parsed.imports()) {
            String simple = imp.substring(imp.lastIndexOf('.') + 1);
            String pkg = imp.substring(0, imp.lastIndexOf('.'));
            if (used.contains(simple) && !pkg.equals(PACKAGE)) {
                imports.add(imp);
            }
        }
        var java = new ArrayList<String>();
        var other = new ArrayList<String>();
        for (String imp : imports) {
            (imp.startsWith("java.") ? java : other).add(imp);
        }

        var sb = new StringBuilder();
        sb.append("// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0\n");
        sb.append("// Copyright (c) 2026 Graylog, Inc. Purpose: non-generic facade over Parsers<LexicalToken>, "
                + "generated by kusto-language-generator (D7)\n");
        sb.append("// GENERATED by kusto-language-generator (LexicalTokenParsersGenerator) from Parsers.java; do not edit.\n");
        sb.append('\n');
        sb.append("package ").append(PACKAGE).append(";\n");
        if (!java.isEmpty()) {
            sb.append('\n');
            java.stream().sorted().forEach(i -> sb.append("import ").append(i).append(";\n"));
        }
        if (!other.isEmpty()) {
            sb.append('\n');
            other.stream().sorted().forEach(i -> sb.append("import ").append(i).append(";\n"));
        }
        sb.append('\n');
        sb.append("/**\n");
        sb.append(" * The members of {@link Parsers} with {@code TInput} bound to {@link LexicalToken}, so grammar files can\n");
        sb.append(" * {@code import static org.graylog.kusto.language.parsing.LexicalTokenParsers.*} in place of upstream's\n");
        sb.append(" * {@code using static Parsers<LexicalToken>} (PORTING.md 3.10, D7). Every method delegates to the\n");
        sb.append(" * {@code Parsers} method of the same name and parameters.\n");
        sb.append(" */\n");
        sb.append("public final class ").append(CLASS_NAME).append('\n');
        sb.append("{\n");
        sb.append("    private ").append(CLASS_NAME).append("()\n");
        sb.append("    {\n");
        sb.append("    }\n");
        sb.append('\n');
        sb.append("    /** A parser that always consumes one input item, but produces nothing. */\n");
        sb.append("    public static final Parser<").append(TYPE_ARGUMENT).append("> Any = Parsers.any();\n");
        sb.append(members);
        sb.append("}\n");
        return sb.toString();
    }

    /** Binds {@code TInput} to {@code LexicalToken} and qualifies the nested types of {@code Parsers}. */
    private static String bind(String type, ParsersSource parsed) {
        String result = type.replaceAll("\\b" + TYPE_PARAMETER + "\\b", TYPE_ARGUMENT);
        for (String nested : parsed.nestedTypes()) {
            result = result.replaceAll("(?<![\\w.$])" + Pattern.quote(nested) + "\\b", "Parsers." + nested);
        }
        return result;
    }

    private static void collectIdentifiers(String type, Set<String> into) {
        Matcher m = IDENTIFIER.matcher(type);
        while (m.find()) {
            into.add(m.group());
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
