// Ported from: src/Kusto.Language/Functions.Convert.cs
// Ported from: src/Kusto.Language/Functions.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".
package org.graylog.kusto.language;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.graylog.kusto.language.symbols.*;
import org.graylog.kusto.language.syntax.*;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Linq;
import org.graylog.kusto.language.utils.ListExtensions;

/// <summary>
/// Well known scalar and special functions.
/// </summary>
public class Functions
{
    // ===== upstream part: Functions.Convert.cs =====
    // region convert functions

    private static final List<String> s_angleLiteralValues = Arrays.asList( // PORT: §3.17 string[] → List<String>
        "Arcminute",
        "Arcsecond",
        "Centiradian",
        "Deciradian",
        "Degree",
        "Gradian",
        "Microdegree",
        "Microradian",
        "Millidegree",
        "Milliradian",
        "Nanodegree",
        "Nanoradian",
        "NatoMil",
        "Radian",
        "Revolution",
        "Tilt"
        );

    public static final FunctionSymbol ConvertAngle =
        new FunctionSymbol("convert_angle", ScalarTypes.Real,
            new Parameter("value", ScalarTypes.Real),
            new Parameter("from", ScalarTypes.String, ArgumentKind.Expression, s_angleLiteralValues),
            new Parameter("to", ScalarTypes.String, ArgumentKind.Expression, s_angleLiteralValues))
        .constantFoldable()
        .withResultNameKind(ResultNameKind.None);


    private static final List<String> s_energyLiteralValues = Arrays.asList( // PORT: §3.17 string[] → List<String>
        "BritishThermalUnit",
        "Calorie",
        "DecathermEc",
        "DecathermImperial",
        "DecathermUs",
        "ElectronVolt",
        "Erg",
        "FootPound",
        "GigabritishThermalUnit",
        "GigaelectronVolt",
        "Gigajoule",
        "GigawattDay",
        "GigawattHour",
        "HorsepowerHour",
        "Joule",
        "KilobritishThermalUnit",
        "Kilocalorie",
        "KiloelectronVolt",
        "Kilojoule",
        "KilowattDay",
        "KilowattHour",
        "MegabritishThermalUnit",
        "Megacalorie",
        "MegaelectronVolt",
        "Megajoule",
        "MegawattDay",
        "MegawattHour",
        "Millijoule",
        "TeraelectronVolt",
        "TerawattDay",
        "TerawattHour",
        "ThermEc",
        "ThermImperial",
        "ThermUs",
        "WattDay",
        "WattHour"
        );

    public static final FunctionSymbol ConvertEnergy =
        new FunctionSymbol("convert_energy", ScalarTypes.Real,
            new Parameter("value", ScalarTypes.Real),
            new Parameter("from", ScalarTypes.String, ArgumentKind.Expression, s_energyLiteralValues),
            new Parameter("to", ScalarTypes.String, ArgumentKind.Expression, s_energyLiteralValues))
        .constantFoldable()
        .withResultNameKind(ResultNameKind.None);


    private static final List<String> s_forceLiteralValues = Arrays.asList( // PORT: §3.17 string[] → List<String>
        "Decanewton",
        "Dyn",
        "KilogramForce",
        "Kilonewton",
        "KiloPond",
        "KilopoundForce",
        "Meganewton",
        "Micronewton",
        "Millinewton",
        "Newton",
        "OunceForce",
        "Poundal",
        "PoundForce",
        "ShortTonForce",
        "TonneForce"
        );

    public static final FunctionSymbol ConvertForce =
        new FunctionSymbol("convert_force", ScalarTypes.Real,
            new Parameter("value", ScalarTypes.Real),
            new Parameter("from", ScalarTypes.String, ArgumentKind.Expression, s_forceLiteralValues),
            new Parameter("to", ScalarTypes.String, ArgumentKind.Expression, s_forceLiteralValues))
        .constantFoldable()
        .withResultNameKind(ResultNameKind.None);


    private static final List<String> s_lengthLiteralValues = Arrays.asList( // PORT: §3.17 string[] → List<String>
        "Angstrom",
        "AstronomicalUnit",
        "Centimeter",
        "Chain",
        "DataMile",
        "Decameter",
        "Decimeter",
        "DtpPica",
        "DtpPoint",
        "Fathom",
        "Foot",
        "Hand",
        "Hectometer",
        "Inch",
        "KilolightYear",
        "Kilometer",
        "Kiloparsec",
        "LightYear",
        "MegalightYear",
        "Megaparsec",
        "Meter",
        "Microinch",
        "Micrometer",
        "Mil",
        "Mile",
        "Millimeter",
        "Nanometer",
        "NauticalMile",
        "Parsec",
        "PrinterPica",
        "PrinterPoint",
        "Shackle",
        "SolarRadius",
        "Twip",
        "UsSurveyFoot",
        "Yard"
        );

    public static final FunctionSymbol ConvertLength =
        new FunctionSymbol("convert_length", ScalarTypes.Real,
            new Parameter("value", ScalarTypes.Real),
            new Parameter("from", ScalarTypes.String, ArgumentKind.Expression, s_lengthLiteralValues),
            new Parameter("to", ScalarTypes.String, ArgumentKind.Expression, s_lengthLiteralValues))
        .constantFoldable()
        .withResultNameKind(ResultNameKind.None);


    private static final List<String> s_massLiteralValues = Arrays.asList( // PORT: §3.17 string[] → List<String>
        "Centigram",
        "Decagram",
        "Decigram",
        "EarthMass",
        "Grain",
        "Gram",
        "Hectogram",
        "Kilogram",
        "Kilopound",
        "Kilotonne",
        "LongHundredweight",
        "LongTon",
        "Megapound",
        "Megatonne",
        "Microgram",
        "Milligram",
        "Nanogram",
        "Ounce",
        "Pound",
        "ShortHundredweight",
        "ShortTon",
        "Slug",
        "SolarMass",
        "Stone",
        "Tonne"
        );

    public static final FunctionSymbol ConvertMass =
        new FunctionSymbol("convert_mass", ScalarTypes.Real,
            new Parameter("value", ScalarTypes.Real),
            new Parameter("from", ScalarTypes.String, ArgumentKind.Expression, s_massLiteralValues),
            new Parameter("to", ScalarTypes.String, ArgumentKind.Expression, s_massLiteralValues))
        .constantFoldable()
        .withResultNameKind(ResultNameKind.None);


    private static final List<String> s_speedLiteralValues = Arrays.asList( // PORT: §3.17 string[] → List<String>
        "CentimeterPerHour",
        "CentimeterPerMinute",
        "CentimeterPerSecond",
        "DecimeterPerMinute",
        "DecimeterPerSecond",
        "FootPerHour",
        "FootPerMinute",
        "FootPerSecond",
        "InchPerHour",
        "InchPerMinute",
        "InchPerSecond",
        "KilometerPerHour",
        "KilometerPerMinute",
        "KilometerPerSecond",
        "Knot",
        "MeterPerHour",
        "MeterPerMinute",
        "MeterPerSecond",
        "MicrometerPerMinute",
        "MicrometerPerSecond",
        "MilePerHour",
        "MillimeterPerHour",
        "MillimeterPerMinute",
        "MillimeterPerSecond",
        "NanometerPerMinute",
        "NanometerPerSecond",
        "UsSurveyFootPerHour",
        "UsSurveyFootPerMinute",
        "UsSurveyFootPerSecond",
        "YardPerHour",
        "YardPerMinute",
        "YardPerSecond"
        );

    public static final FunctionSymbol ConvertSpeed =
        new FunctionSymbol("convert_speed", ScalarTypes.Real,
            new Parameter("value", ScalarTypes.Real),
            new Parameter("from", ScalarTypes.String, ArgumentKind.Expression, s_speedLiteralValues),
            new Parameter("to", ScalarTypes.String, ArgumentKind.Expression, s_speedLiteralValues))
        .constantFoldable()
        .withResultNameKind(ResultNameKind.None);


    private static final List<String> s_temperatureLiteralValues = Arrays.asList( // PORT: §3.17 string[] → List<String>
        "DegreeCelsius",
        "DegreeDelisle",
        "DegreeFahrenheit",
        "DegreeNewton",
        "DegreeRankine",
        "DegreeReaumur",
        "DegreeRoemer",
        "Kelvin",
        "MillidegreeCelsius",
        "SolarTemperature"
        );

    public static final FunctionSymbol ConvertTemperature =
        new FunctionSymbol("convert_temperature", ScalarTypes.Real,
            new Parameter("value", ScalarTypes.Real),
            new Parameter("from", ScalarTypes.String, ArgumentKind.Expression, s_temperatureLiteralValues),
            new Parameter("to", ScalarTypes.String, ArgumentKind.Expression, s_temperatureLiteralValues))
        .constantFoldable()
        .withResultNameKind(ResultNameKind.None);


    private static final List<String> s_volumeLiteralValues = Arrays.asList( // PORT: §3.17 string[] → List<String>
        "AcreFoot",
        "AuTablespoon",
        "BoardFoot",
        "Centiliter",
        "CubicCentimeter",
        "CubicDecimeter",
        "CubicFoot",
        "CubicHectometer",
        "CubicInch",
        "CubicKilometer",
        "CubicMeter",
        "CubicMicrometer",
        "CubicMile",
        "CubicMillimeter",
        "CubicYard",
        "Decaliter",
        "DecausGallon",
        "Deciliter",
        "DeciusGallon",
        "HectocubicFoot",
        "HectocubicMeter",
        "Hectoliter",
        "HectousGallon",
        "ImperialBeerBarrel",
        "ImperialGallon",
        "ImperialOunce",
        "ImperialPint",
        "KilocubicFoot",
        "KilocubicMeter",
        "KiloimperialGallon",
        "Kiloliter",
        "KilousGallon",
        "Liter",
        "MegacubicFoot",
        "MegaimperialGallon",
        "Megaliter",
        "MegausGallon",
        "MetricCup",
        "MetricTeaspoon",
        "Microliter",
        "Milliliter",
        "OilBarrel",
        "UkTablespoon",
        "UsBeerBarrel",
        "UsCustomaryCup",
        "UsGallon",
        "UsLegalCup",
        "UsOunce",
        "UsPint",
        "UsQuart",
        "UsTablespoon",
        "UsTeaspoon"
        );

    public static final FunctionSymbol ConvertVolume =
        new FunctionSymbol("convert_volume", ScalarTypes.Real,
            new Parameter("value", ScalarTypes.Real),
            new Parameter("from", ScalarTypes.String, ArgumentKind.Expression, s_volumeLiteralValues),
            new Parameter("to", ScalarTypes.String, ArgumentKind.Expression, s_volumeLiteralValues))
        .constantFoldable()
        .withResultNameKind(ResultNameKind.None);

    // endregion
    // ===== upstream part: Functions.cs =====
    // region cluster / database / table / etc
    public static final FunctionSymbol Cluster =
        new FunctionSymbol("cluster",
            ReturnTypeKind.Parameter0Cluster,
            new Parameter("name", ScalarTypes.String));

    public static final FunctionSymbol Database =
        new FunctionSymbol("database",
            ReturnTypeKind.Parameter0Database,
            new Parameter("name", ScalarTypes.String, P.minOccurring(0)));

    public static final FunctionSymbol Table =
        new FunctionSymbol("table",
            ReturnTypeKind.Parameter0Table,
            new Parameter("name", ScalarTypes.String),
            new Parameter("query_data_scope", ScalarTypes.String, P.minOccurring(0)));

    public static final FunctionSymbol ExternalTable =
        new FunctionSymbol("external_table",
            new Signature(
                ReturnTypeKind.Parameter0ExternalTable,
                new Parameter("name", ScalarTypes.String),
                new Parameter("mapping", ScalarTypes.String, P.minOccurring(0))
                ),
            new Signature(
                ReturnTypeKind.Parameter0ExternalTable,
                new Parameter("name", ScalarTypes.String),
                new Parameter("max_age", ScalarTypes.TimeSpan)
                ));

    public static final FunctionSymbol MaterializedView =
        new FunctionSymbol("materialized_view",
            ReturnTypeKind.Parameter0MaterializedView,
            new Parameter("name", ScalarTypes.String),
            new Parameter("max_age", ScalarTypes.TimeSpan, P.minOccurring(0)));

    public static final FunctionSymbol EntityGroup =
        new FunctionSymbol("entity_group",
            ReturnTypeKind.Parameter0EntityGroup,
            new Parameter("name", ScalarTypes.String));

    public static final FunctionSymbol StoredQueryResult =
        new FunctionSymbol("stored_query_result",
            new Signature(
                ReturnTypeKind.Parameter0StoredQueryResult,
                new Parameter("name", ScalarTypes.String)),
            new Signature(
                ReturnTypeKind.Parameter0StoredQueryResult,
                new Parameter("name", ScalarTypes.String),
                new Parameter("index", ScalarTypes.Long)),
            new Signature(
                ReturnTypeKind.Parameter0StoredQueryResult,
                new Parameter("name", ScalarTypes.String),
                new Parameter("table_name", ScalarTypes.String)));

    public static final FunctionSymbol Graph =
        new FunctionSymbol("graph",
            new Signature(
                ReturnTypeKind.Parameter0Graph,
                new Parameter("name", ScalarTypes.String),
                new Parameter("snapshot", ScalarTypes.String, P.minOccurring(0), P.maxOccurring(1))
                ),
            new Signature(
                ReturnTypeKind.Parameter0Graph,
                new Parameter("name", ScalarTypes.String),
                new Parameter("volatile", ScalarTypes.Bool)
                ));
    // endregion

    // region string functions
    public static final FunctionSymbol Strcat =
        new FunctionSymbol("strcat", ScalarTypes.String,
                new Parameter("arg", ParameterTypeKind.Scalar, P.maxOccurring(64)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol StrcatArray =
        new FunctionSymbol("strcat_array", ScalarTypes.String,
                new Parameter("array", ParameterTypeKind.DynamicArray),
                new Parameter("delimiter", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ArrayStrcat =
        new FunctionSymbol("array_strcat", ScalarTypes.String,
                new Parameter("array", ParameterTypeKind.DynamicArray),
                new Parameter("delimiter", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol StrcatDelim =
        new FunctionSymbol("strcat_delim", ScalarTypes.String,
                new Parameter("delimiter", ParameterTypeKind.Scalar),
                new Parameter("arg", ParameterTypeKind.Scalar, P.minOccurring(2), P.maxOccurring(64)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Strcmp =
        new FunctionSymbol("strcmp", ScalarTypes.Long,
                new Parameter("s1", ParameterTypeKind.Scalar),
                new Parameter("s2", ParameterTypeKind.Scalar))
            .constantFoldable();

    public static final FunctionSymbol Strrep =
        new FunctionSymbol("strrep", ScalarTypes.String,
                new Parameter("value", ParameterTypeKind.Scalar),
                new Parameter("multiplier", ScalarTypes.Long),
                new Parameter("delimiter", ParameterTypeKind.Scalar, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Strlen =
        new FunctionSymbol("strlen", ScalarTypes.Long,
                new Parameter("string", ParameterTypeKind.StringOrDynamic))
            .withResultNameKind(ResultNameKind.NameAndFirstArgument)
            .constantFoldable();

    public static final FunctionSymbol StringSize =
        new FunctionSymbol("string_size", ScalarTypes.Long,
                new Parameter("string", ParameterTypeKind.StringOrDynamic))
            .withResultNameKind(ResultNameKind.NameAndFirstArgument)
            .constantFoldable();

    public static final FunctionSymbol ToUpper =
        new FunctionSymbol("toupper", ScalarTypes.String,
                new Parameter("value", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ToLower =
        new FunctionSymbol("tolower", ScalarTypes.String,
                new Parameter("value", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ToUtf8_Deprecated =
        new FunctionSymbol("to_utf8",
                ScalarTypes.DynamicArray,
                new Parameter("value", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable()
            .obsolete("unicode_codepoints_from_string");

    public static final FunctionSymbol UnicodeCodepointsFromString =
        new FunctionSymbol("unicode_codepoints_from_string",
                ScalarTypes.DynamicArray,
                new Parameter("value", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Substring =
        new FunctionSymbol("substring", ScalarTypes.String,
                new Parameter("string", ParameterTypeKind.Scalar),
                new Parameter("start", ParameterTypeKind.Integer),
                new Parameter("length", ParameterTypeKind.Integer, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol RegexQuote =
        new FunctionSymbol("regex_quote", ScalarTypes.String,
                new Parameter("string", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol IndexOf =
        new FunctionSymbol("indexof", ScalarTypes.Long,
                new Parameter("string", ParameterTypeKind.Scalar),
                new Parameter("match", ParameterTypeKind.Scalar),
                new Parameter("start", ParameterTypeKind.Integer, P.minOccurring(0)),
                new Parameter("length", ParameterTypeKind.Integer, P.minOccurring(0)),
                new Parameter("occurrence", ParameterTypeKind.Integer, ArgumentKind.Constant, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol IndexOfRegex =
        new FunctionSymbol("indexof_regex", ScalarTypes.Long,
                new Parameter("string", ParameterTypeKind.Scalar),
                new Parameter("match", ParameterTypeKind.Scalar, ArgumentKind.Constant),
                new Parameter("start", ParameterTypeKind.Integer, P.minOccurring(0)),
                new Parameter("length", ParameterTypeKind.Integer, P.minOccurring(0)),
                new Parameter("occurrence", ParameterTypeKind.Integer, ArgumentKind.Constant, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol HasAnyIndex =
        new FunctionSymbol("has_any_index", ScalarTypes.Long,
                new Parameter("source", ParameterTypeKind.StringOrDynamic),
                new Parameter("values", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Reverse =
        new FunctionSymbol("reverse", ScalarTypes.String,
                new Parameter("value", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Split =
        new FunctionSymbol("split",
                ScalarTypes.DynamicArrayOfString,
                new Parameter("source", ParameterTypeKind.Scalar),
                new Parameter("delimiter", ScalarTypes.String),
                new Parameter("requestedIndex", ParameterTypeKind.Integer, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol ParseCommandLine =
        new FunctionSymbol("parse_command_line",
                ScalarTypes.DynamicArrayOfString,
                new Parameter("command", ParameterTypeKind.Scalar),
                new Parameter("parser", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Extract =
        new FunctionSymbol("extract",
                new Signature(ScalarTypes.String,
                    new Parameter("regex", ScalarTypes.String, ArgumentKind.Constant),
                    new Parameter("captureGroup", ScalarTypes.Long),
                    new Parameter("source", ScalarTypes.String)),
                new Signature(ReturnTypeKind.ParameterNLiteral,
                    new Parameter("regex", ScalarTypes.String, ArgumentKind.Constant),
                    new Parameter("captureGroup", ScalarTypes.Long),
                    new Parameter("source", ScalarTypes.String),
                    new Parameter("typeLiteral", ScalarTypes.Type, ArgumentKind.Literal)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ExtractAll_Deprecated =
        new FunctionSymbol("extractall",
                new Signature(
                    ScalarTypes.DynamicArray,
                    new Parameter("regex", ScalarTypes.String, ArgumentKind.Constant),
                    new Parameter("source", ScalarTypes.String)),
                new Signature(
                    ScalarTypes.DynamicArray,
                    new Parameter("regex", ScalarTypes.String, ArgumentKind.Constant),
                    new Parameter("captureGroups", ParameterTypeKind.DynamicArray),
                    new Parameter("source", ScalarTypes.String)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable()
            .obsolete("extract_all");

    public static final FunctionSymbol ExtractAll =
        new FunctionSymbol("extract_all",
                new Signature(
                    ScalarTypes.DynamicArray,
                    new Parameter("regex", ScalarTypes.String, ArgumentKind.Constant),
                    new Parameter("source", ScalarTypes.String)),
                new Signature(
                    ScalarTypes.DynamicArray,
                    new Parameter("regex", ScalarTypes.String, ArgumentKind.Constant),
                    new Parameter("captureGroups", ParameterTypeKind.DynamicArray),
                    new Parameter("source", ScalarTypes.String)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ExtractJson_Deprecated =
        new FunctionSymbol("extractjson",
                new Signature(ScalarTypes.String,
                    new Parameter("jsonPath", ScalarTypes.String, ArgumentKind.Constant),
                    new Parameter("jsonText", ScalarTypes.String)),
                new Signature(ReturnTypeKind.ParameterNLiteral,
                    new Parameter("jsonPath", ScalarTypes.String, ArgumentKind.Constant),
                    new Parameter("jsonText", ScalarTypes.String),
                    new Parameter("type", ScalarTypes.Type, ArgumentKind.Literal)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ExtractJson =
        new FunctionSymbol("extract_json",
                new Signature(ScalarTypes.String,
                    new Parameter("jsonPath", ScalarTypes.String, ArgumentKind.Constant),
                    new Parameter("jsonText", ScalarTypes.String)),
                new Signature(ReturnTypeKind.ParameterNLiteral,
                    new Parameter("jsonPath", ScalarTypes.String, ArgumentKind.Constant),
                    new Parameter("jsonText", ScalarTypes.String),
                    new Parameter("type", ScalarTypes.Type, ArgumentKind.Literal)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Replace =
        new FunctionSymbol("replace", ScalarTypes.String,
                new Parameter("regex", ScalarTypes.String, ArgumentKind.Constant),
                new Parameter("rewrite", ScalarTypes.String, ArgumentKind.Constant),
                new Parameter("source", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable()
            .obsolete("replace_regex' or 'replace_string"); // added ' for better message formatting

    public static final FunctionSymbol ReplaceRegex =
        new FunctionSymbol("replace_regex", ScalarTypes.String,
                new Parameter("source", ScalarTypes.String),
                new Parameter("lookup_regex", ScalarTypes.String, ArgumentKind.Constant),
                new Parameter("rewrite_pattern", ScalarTypes.String, ArgumentKind.Constant))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ReplaceString =
        new FunctionSymbol("replace_string", ScalarTypes.String,
                new Parameter("text", ScalarTypes.String),
                new Parameter("lookup", ScalarTypes.String),
                new Parameter("rewrite", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ReplaceStrings =
        new FunctionSymbol("replace_strings", ScalarTypes.String,
                new Parameter("text", ScalarTypes.String),
                new Parameter("lookups", ParameterTypeKind.DynamicArray),
                new Parameter("rewrites", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol TrimStart =
        new FunctionSymbol("trim_start", ScalarTypes.String,
                new Parameter("regex", ScalarTypes.String, ArgumentKind.Constant),
                new Parameter("source", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol TrimEnd =
        new FunctionSymbol("trim_end", ScalarTypes.String,
                new Parameter("regex", ScalarTypes.String, ArgumentKind.Constant),
                new Parameter("source", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Trim =
        new FunctionSymbol("trim", ScalarTypes.String,
                new Parameter("regex", ScalarTypes.String, ArgumentKind.Constant),
                new Parameter("source", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol CountOf =
        new FunctionSymbol("countof", ScalarTypes.Long,
                new Parameter("source", ScalarTypes.String),
                new Parameter("search", ScalarTypes.String),
                new Parameter("kind", ScalarTypes.String, ArgumentKind.Literal, Arrays.asList("normal", "regex"), P.isCaseSensitive(true), P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Translate =
        new FunctionSymbol("translate", ScalarTypes.String,
                new Parameter("searchList", ScalarTypes.String),
                new Parameter("replacementList", ScalarTypes.String),
                new Parameter("source", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol MakeString_Deprecated =
        new FunctionSymbol("make_string",
                ScalarTypes.String,
                new Parameter("value", ParameterTypeKind.IntegerOrArray, P.maxOccurring(FunctionHelpers.MaxRepeat)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable()
            .obsolete("unicode_codepoints_to_string");

    public static final FunctionSymbol UnicodeCodepointsToString =
        new FunctionSymbol("unicode_codepoints_to_string",
                ScalarTypes.String,
                new Parameter("value", ParameterTypeKind.IntegerOrArray, P.maxOccurring(FunctionHelpers.MaxRepeat)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();
    // endregion

    // region type conversion functions
    public static final FunctionSymbol ToString =
        new FunctionSymbol("tostring", ScalarTypes.String,
                new Parameter("value", ParameterTypeKind.Scalar))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol ToHex =
        new FunctionSymbol("tohex", ScalarTypes.String,
                new Parameter("value", ParameterTypeKind.Integer),
                new Parameter("minLength", ParameterTypeKind.Integer, P.minOccurring(0)))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol ToDynamic_ =  // use _ because build fails claiming ToDynamic exists on object.
            new FunctionSymbol("todynamic",
                    ScalarTypes.Dynamic,
                    new Parameter("value", ScalarTypes.String))
                .constantFoldable()
                .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol ToObject_Deprecated =
        new FunctionSymbol("toobject",
                ScalarTypes.Dynamic,
                new Parameter("value", ScalarTypes.String))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.FirstArgument)
            .obsolete("todynamic");

    public static final FunctionSymbol ToLong =
        new FunctionSymbol("tolong",
                ScalarTypes.Long,
                new Parameter("value", ParameterTypeKind.Scalar))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol ToInt =
        new FunctionSymbol("toint",
                ScalarTypes.Int,
                new Parameter("value", ParameterTypeKind.Scalar))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol ToReal =
        new FunctionSymbol("toreal",
                ScalarTypes.Real,
                new Parameter("value", ParameterTypeKind.Scalar))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol ToDouble =
        new FunctionSymbol("todouble",
                ScalarTypes.Real,
                new Parameter("value", ParameterTypeKind.Scalar))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol ToDateTime =
        new FunctionSymbol("todatetime",
                ScalarTypes.DateTime,
                new Parameter("value", ParameterTypeKind.Scalar))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol ToTimespan =
        new FunctionSymbol("totimespan",
                ScalarTypes.TimeSpan,
                new Parameter("value", ParameterTypeKind.Scalar))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol ToTime =
        new FunctionSymbol("totime",
                ScalarTypes.TimeSpan,
                new Parameter("value", ParameterTypeKind.Scalar))
            .obsolete("totimespan")
            .constantFoldable()
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol ToBool =
        new FunctionSymbol("tobool",
                ScalarTypes.Bool,
                new Parameter("value", ParameterTypeKind.Scalar))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol ToBoolean =
        new FunctionSymbol("toboolean",
                ScalarTypes.Bool,
                new Parameter("value", ParameterTypeKind.Scalar))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol ToDecimal =
        new FunctionSymbol("todecimal",
                ScalarTypes.Decimal,
                new Parameter("value", ParameterTypeKind.Scalar))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol ToGuid =
        new FunctionSymbol("toguid",
                ScalarTypes.Guid,
                new Parameter("value", ParameterTypeKind.StringOrDynamic))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol GetType =
        new FunctionSymbol("gettype",
                ScalarTypes.String,
                new Parameter("value", ParameterTypeKind.Scalar))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
            .withResultNamePrefix("type");
    // endregion

    // region encoding/decoding functions
    public static final FunctionSymbol UrlEncode =
        new FunctionSymbol("url_encode", ScalarTypes.String,
                new Parameter("url", ScalarTypes.String))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol UrlEncode_Component =
        new FunctionSymbol("url_encode_component", ScalarTypes.String,
                new Parameter("url", ScalarTypes.String))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol UrlDecode =
        new FunctionSymbol("url_decode", ScalarTypes.String,
                new Parameter("encoded_url", ScalarTypes.String))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol Base64EncodeString =
        new FunctionSymbol("base64_encodestring", ScalarTypes.String,
                new Parameter("string", ScalarTypes.String))
            .constantFoldable()
            .obsolete("base64_encode_tostring")
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol Base64EncodeToString =
        new FunctionSymbol("base64_encode_tostring", ScalarTypes.String,
                new Parameter("string", ScalarTypes.String))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol Base64DecodeString =
        new FunctionSymbol("base64_decodestring", ScalarTypes.String,
                new Parameter("base64_string", ScalarTypes.String))
            .constantFoldable()
            .obsolete("base64_decode_tostring")
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol Base64DecodeToString =
        new FunctionSymbol("base64_decode_tostring",
                ScalarTypes.String,
                new Parameter("base64_string", ScalarTypes.String))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol Base64DecodeToArray =
        new FunctionSymbol("base64_decode_toarray",
                ScalarTypes.DynamicArray,
                new Parameter("base64_string", ScalarTypes.String))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol Base64DecodeToGuid =
        new FunctionSymbol("base64_decode_toguid",
                ScalarTypes.Guid,
                new Parameter("base64_string", ScalarTypes.String))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol Base64EncodeFromGuid =
        new FunctionSymbol("base64_encode_fromguid",
                ScalarTypes.String,
                new Parameter("guid", ScalarTypes.Guid))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol Base64EncodeFromArray =
        new FunctionSymbol("base64_encode_fromarray",
                ScalarTypes.String,
                new Parameter("base64_string_decoded_as_array", ParameterTypeKind.DynamicArray))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol ZlibDecompressString =
        new FunctionSymbol("zlib_decompress_from_base64_string",
                ScalarTypes.String,
                new Parameter("string", ScalarTypes.String))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol ZlibCompressString =
        new FunctionSymbol("zlib_compress_to_base64_string",
                ScalarTypes.String,
                new Parameter("string", ScalarTypes.String))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol GzipDecompressString =
        new FunctionSymbol("gzip_decompress_from_base64_string",
                ScalarTypes.String,
                new Parameter("string", ScalarTypes.String))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol GzipCompressString =
        new FunctionSymbol("gzip_compress_to_base64_string",
                ScalarTypes.String,
                new Parameter("string", ScalarTypes.String))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol Lz4CompressDynamicArray =
        new FunctionSymbol("__lz4_compress_dynamic_array_to_base64_string",
                ScalarTypes.String,
                new Parameter("dynamic", ParameterTypeKind.DynamicArray))
            .constantFoldable()
            .hide()
            .withResultNameKind(ResultNameKind.None);
    // endregion

    // region parsing functions
    public static final FunctionSymbol ParseCsv =
        new FunctionSymbol("parse_csv",
                ScalarTypes.DynamicArray,
                new Parameter("csv_text", ParameterTypeKind.StringOrDynamic))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol ParseJson_Deprecated =
        new FunctionSymbol("parsejson",
                ScalarTypes.Dynamic,
                new Parameter("json_text", ParameterTypeKind.StringOrDynamic))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable()
            .obsolete("parse_json");

    public static final FunctionSymbol ParseJson =
        new FunctionSymbol("parse_json",
                ScalarTypes.Dynamic,
                new Parameter("json_text", ParameterTypeKind.StringOrDynamic))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol ParseXml =
        new FunctionSymbol("parse_xml",
                ScalarTypes.DynamicBag,
                new Parameter("xml_text", ParameterTypeKind.StringOrDynamic))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    private static TypeSymbol ParseUrlResult =
        ScalarTypes.getDynamicBag(
            new ColumnSymbol("Scheme", ScalarTypes.String),
            new ColumnSymbol("Host", ScalarTypes.String),
            new ColumnSymbol("Port", ScalarTypes.String),
            new ColumnSymbol("Path", ScalarTypes.String),
            new ColumnSymbol("Username", ScalarTypes.String),
            new ColumnSymbol("Password", ScalarTypes.String),
            new ColumnSymbol("Query Parameters", ScalarTypes.Dynamic),
            new ColumnSymbol("Fragment", ScalarTypes.String));

    public static final FunctionSymbol ParseUrl_Deprecated =
        new FunctionSymbol("parseurl",
                ParseUrlResult,
                new Parameter("url", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable()
            .obsolete("parse_url");

    public static final FunctionSymbol ParseUrl =
        new FunctionSymbol("parse_url",
                ParseUrlResult,
                new Parameter("url", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    private static final TypeSymbol ParseUrlQueryResult =
        ScalarTypes.getDynamicBag(
            new ColumnSymbol("Query Parameters", ScalarTypes.Dynamic));

    public static final FunctionSymbol ParseUrlQuery_Deprecated =
        new FunctionSymbol("parseurlquery",
                ParseUrlQueryResult,
                new Parameter("query", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable()
            .obsolete("parse_urlquery");

    public static final FunctionSymbol ParseUrlQuery =
        new FunctionSymbol("parse_urlquery",
                ParseUrlQueryResult,
                new Parameter("query", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    // region IPv4 functions
    public static final FunctionSymbol ParseIPV4 =
        new FunctionSymbol("parse_ipv4", ScalarTypes.Long,
                new Parameter("ip", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ParseIPV4Mask =
        new FunctionSymbol("parse_ipv4_mask", ScalarTypes.Long,
                new Parameter("ip", ScalarTypes.String),
                new Parameter("prefix", ScalarTypes.Long))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol FormatIPV4 =
        new FunctionSymbol("format_ipv4", ScalarTypes.String,
                new Parameter("ip", ParameterTypeKind.Scalar), // TODO: restrict to String, Int or Long
                new Parameter("prefix", ScalarTypes.Long, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol FormatIPV4Mask =
        new FunctionSymbol("format_ipv4_mask", ScalarTypes.String,
                new Parameter("ip", ParameterTypeKind.Scalar), // TODO: restrict to String, Int or Long
                new Parameter("prefix", ScalarTypes.Long))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Ipv4Compare =
        new FunctionSymbol("ipv4_compare", ScalarTypes.Long,
                new Parameter("ip1", ScalarTypes.String),
                new Parameter("ip2", ScalarTypes.String),
                new Parameter("prefix", ScalarTypes.Long, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Ipv4IsMatch =
        new FunctionSymbol("ipv4_is_match", ScalarTypes.Bool,
                new Parameter("ip1", ScalarTypes.String),
                new Parameter("ip2", ScalarTypes.String),
                new Parameter("prefix", ScalarTypes.Long, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Ipv4IsInRange =
        new FunctionSymbol("ipv4_is_in_range", ScalarTypes.Bool,
                new Parameter("ip", ScalarTypes.String),
                new Parameter("ip_range", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Ipv4IsInAnyRange =
        new FunctionSymbol("ipv4_is_in_any_range",
                new Signature(ScalarTypes.Bool,
                    new Parameter("ip", ParameterTypeKind.StringOrDynamic),
                    new Parameter("ranges", ScalarTypes.String, P.maxOccurring(FunctionHelpers.MaxRepeat))),
                new Signature(ScalarTypes.Bool,
                    new Parameter("ip", ParameterTypeKind.StringOrDynamic),
                    new Parameter("ranges", ParameterTypeKind.DynamicArray)))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol Ipv4NetmaskSuffix =
        new FunctionSymbol("ipv4_netmask_suffix", ScalarTypes.Long,
                new Parameter("ip", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Ipv4IsPrivate =
        new FunctionSymbol("ipv4_is_private", ScalarTypes.Bool,
                new Parameter("ip", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Ipv4RangeToCidrList =
        new FunctionSymbol("ipv4_range_to_cidr_list",
                ScalarTypes.DynamicArray,
                new Parameter("start_ip", ScalarTypes.String),
                new Parameter("end_ip", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();
    // endregion

    // region IPv6 functions
    public static final FunctionSymbol ParseIPV6 =
        new FunctionSymbol("parse_ipv6", ScalarTypes.String,
                new Parameter("ip", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ParseIPV6Mask =
        new FunctionSymbol("parse_ipv6_mask", ScalarTypes.String,
                new Parameter("ip", ScalarTypes.String),
                new Parameter("prefix", ScalarTypes.Long))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Ipv6Compare =
        new FunctionSymbol("ipv6_compare", ScalarTypes.Long,
                new Parameter("ip1", ScalarTypes.String),
                new Parameter("ip2", ScalarTypes.String),
                new Parameter("prefix", ScalarTypes.Long, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Ipv6IsMatch =
        new FunctionSymbol("ipv6_is_match", ScalarTypes.Bool,
                new Parameter("ip1", ScalarTypes.String),
                new Parameter("ip2", ScalarTypes.String),
                new Parameter("prefix", ScalarTypes.Long, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Ipv6IsInRange =
        new FunctionSymbol("ipv6_is_in_range", ScalarTypes.Bool,
                new Parameter("ip", ScalarTypes.String),
                new Parameter("ip_range", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Ipv6IsInAnyRange =
        new FunctionSymbol("ipv6_is_in_any_range",
                new Signature(ScalarTypes.Bool,
                    new Parameter("ip", ParameterTypeKind.StringOrDynamic),
                    new Parameter("ranges", ScalarTypes.String, P.maxOccurring(FunctionHelpers.MaxRepeat))),
                new Signature(ScalarTypes.Bool,
                    new Parameter("ip", ParameterTypeKind.StringOrDynamic),
                    new Parameter("ranges", ParameterTypeKind.DynamicArray)))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol Ipv6LookupRanges =
        new FunctionSymbol("__ipv6_lookup_ranges",
                new Signature(ScalarTypes.DynamicArray,
                    new Parameter("ip", ParameterTypeKind.StringOrDynamic),
                    new Parameter("ranges", ParameterTypeKind.DynamicArray)))
            .withResultNameKind(ResultNameKind.None).hide();
    // endregion

    private static final TypeSymbol ParsePathResult =
        ScalarTypes.getDynamicBag(
            new ColumnSymbol("Scheme", ScalarTypes.String),
            new ColumnSymbol("RootPath", ScalarTypes.String),
            new ColumnSymbol("DirectoryPath", ScalarTypes.String),
            new ColumnSymbol("DirectoryName", ScalarTypes.String),
            new ColumnSymbol("Filename", ScalarTypes.String),
            new ColumnSymbol("Extension", ScalarTypes.String),
            new ColumnSymbol("AlternateDataStreamName", ScalarTypes.String));

    public static final FunctionSymbol ParsePath =
        new FunctionSymbol("parse_path",
                ParsePathResult,
                new Parameter("path", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    private static final TypeSymbol ParseUserAgentResult =
        ScalarTypes.getDynamicBag(
            new ColumnSymbol("Browser",
                ScalarTypes.getDynamicBag(
                    new ColumnSymbol("Family", ScalarTypes.String),
                    new ColumnSymbol("MajorVersion", ScalarTypes.String),
                    new ColumnSymbol("MinorVersion", ScalarTypes.String),
                    new ColumnSymbol("Patch", ScalarTypes.String))),
            new ColumnSymbol("OperatingSystem",
                ScalarTypes.getDynamicBag(
                    new ColumnSymbol("Family", ScalarTypes.String),
                    new ColumnSymbol("MajorVersion", ScalarTypes.String),
                    new ColumnSymbol("MinorVersion", ScalarTypes.String),
                    new ColumnSymbol("Patch", ScalarTypes.String),
                    new ColumnSymbol("PatchMinor", ScalarTypes.String))),
            new ColumnSymbol("Device",
                ScalarTypes.getDynamicBag(
                    new ColumnSymbol("Family", ScalarTypes.String),
                    new ColumnSymbol("Brand", ScalarTypes.String),
                    new ColumnSymbol("Model", ScalarTypes.String))));

    public static final FunctionSymbol ParseUserAgent =
        new FunctionSymbol("parse_user_agent",
                ParseUserAgentResult,
                new Parameter("user_agent", ScalarTypes.String),
                new Parameter("look_for", ParameterTypeKind.StringOrArray, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ParseVersion =
        new FunctionSymbol("parse_version",
                ScalarTypes.Decimal,
                new Parameter("version", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();
    // endregion

    // region date and time functions
    public static final FunctionSymbol FormatDatetime =
        new FunctionSymbol("format_datetime", ScalarTypes.String,
                new Parameter("date", ScalarTypes.DateTime),
                new Parameter("format", ScalarTypes.String, ArgumentKind.LiteralNotEmpty))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol FormatTimespan =
        new FunctionSymbol("format_timespan", ScalarTypes.String,
                new Parameter("timespan", ScalarTypes.TimeSpan),
                new Parameter("format", ScalarTypes.String, ArgumentKind.LiteralNotEmpty))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol MakeDatetime =
        new FunctionSymbol("make_datetime",
                new Signature(ScalarTypes.DateTime,
                    new Parameter("year", ParameterTypeKind.Number),
                    new Parameter("month", ParameterTypeKind.Number),
                    new Parameter("day", ParameterTypeKind.Number),
                    new Parameter("hour", ParameterTypeKind.Number, P.minOccurring(0)),
                    new Parameter("minute", ParameterTypeKind.Number, P.minOccurring(0)),
                    new Parameter("second", ParameterTypeKind.Number, P.minOccurring(0))),
                new Signature(ScalarTypes.DateTime,
                    new Parameter("value", ParameterTypeKind.Scalar)))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.OnlyArgument);

    public static final FunctionSymbol MakeTimespan =
        new FunctionSymbol("make_timespan",
                new Signature(ScalarTypes.TimeSpan,
                    new Parameter("days", ParameterTypeKind.Integer),
                    new Parameter("hours", ParameterTypeKind.Integer),
                    new Parameter("minutes", ParameterTypeKind.Integer),
                    new Parameter("seconds", ParameterTypeKind.Integer)),
                new Signature(ScalarTypes.TimeSpan,
                    new Parameter("hours", ParameterTypeKind.Integer),
                    new Parameter("minutes", ParameterTypeKind.Integer),
                    new Parameter("seconds", ParameterTypeKind.Integer, P.minOccurring(0))),
                new Signature(ScalarTypes.TimeSpan,
                    new Parameter("value", ParameterTypeKind.Scalar)))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.OnlyArgument);

    public static final FunctionSymbol DatetimeAdd =
        new FunctionSymbol("datetime_add", ScalarTypes.DateTime,
                new Parameter("part", ScalarTypes.String, ArgumentKind.Literal, KustoFacts.DateDiffParts),
                new Parameter("value", ParameterTypeKind.Integer),
                new Parameter("datetime", ScalarTypes.DateTime))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol DatetimeDiff =
        new FunctionSymbol("datetime_diff", ScalarTypes.Long,
                new Parameter("part", ScalarTypes.String, ArgumentKind.Literal, KustoFacts.DateDiffParts),
                new Parameter("datetime1", ScalarTypes.DateTime),
                new Parameter("datetime2", ScalarTypes.DateTime))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol DayOfWeek =
        new FunctionSymbol("dayofweek", ScalarTypes.TimeSpan,
                new Parameter("date", ScalarTypes.DateTime))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol DayOfMonth =
        new FunctionSymbol("dayofmonth", ScalarTypes.Int,
                new Parameter("date", ScalarTypes.DateTime))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol DayOfYear =
        new FunctionSymbol("dayofyear", ScalarTypes.Int,
                new Parameter("date", ScalarTypes.DateTime))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol HourOfDay =
        new FunctionSymbol("hourofday", ScalarTypes.Int,
                new Parameter("date", ScalarTypes.DateTime))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    // To be deprecated as current implementation isn't ISO 8601 compliant.
    // A new function, week_of_year, that is ISO 8601 compliant has been added.
    public static final FunctionSymbol WeekOfYear =
        new FunctionSymbol("weekofyear", ScalarTypes.Int,
                new Parameter("date", ScalarTypes.DateTime))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None)
            .obsolete("week_of_year");

    public static final FunctionSymbol WeekOfYearISO =
        new FunctionSymbol("week_of_year", ScalarTypes.Int,
                new Parameter("date", ScalarTypes.DateTime))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol MonthOfYear =
        new FunctionSymbol("monthofyear", ScalarTypes.Int,
                new Parameter("date", ScalarTypes.DateTime))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol StartOfDay =
        new FunctionSymbol("startofday", ScalarTypes.DateTime,
                new Parameter("date", ScalarTypes.DateTime),
                new Parameter("offset", ScalarTypes.Long, P.minOccurring(0)))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol StartOfWeek =
        new FunctionSymbol("startofweek", ScalarTypes.DateTime,
                new Parameter("date", ScalarTypes.DateTime),
                new Parameter("offset", ScalarTypes.Long, P.minOccurring(0)))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol StartOfMonth =
        new FunctionSymbol("startofmonth", ScalarTypes.DateTime,
                new Parameter("date", ScalarTypes.DateTime),
                new Parameter("offset", ScalarTypes.Long, P.minOccurring(0)))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol StartOfYear =
        new FunctionSymbol("startofyear", ScalarTypes.DateTime,
                new Parameter("date", ScalarTypes.DateTime),
                new Parameter("offset", ScalarTypes.Long, P.minOccurring(0)))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol EndOfDay =
        new FunctionSymbol("endofday", ScalarTypes.DateTime,
                new Parameter("date", ScalarTypes.DateTime),
                new Parameter("offset", ScalarTypes.Long, P.minOccurring(0)))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol EndOfWeek =
        new FunctionSymbol("endofweek", ScalarTypes.DateTime,
                new Parameter("date", ScalarTypes.DateTime),
                new Parameter("offset", ScalarTypes.Long, P.minOccurring(0)))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol EndOfMonth =
        new FunctionSymbol("endofmonth", ScalarTypes.DateTime,
                new Parameter("date", ScalarTypes.DateTime),
                new Parameter("offset", ScalarTypes.Long, P.minOccurring(0)))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol EndOfYear =
        new FunctionSymbol("endofyear", ScalarTypes.DateTime,
                new Parameter("date", ScalarTypes.DateTime),
                new Parameter("offset", ScalarTypes.Long, P.minOccurring(0)))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol GetYear =
        new FunctionSymbol("getyear", ScalarTypes.Int,
                new Parameter("date", ScalarTypes.DateTime))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol GetMonth =
        new FunctionSymbol("getmonth", ScalarTypes.Int,
                new Parameter("date", ScalarTypes.DateTime))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol DatePart =
        new FunctionSymbol("datepart", ScalarTypes.Int,
                new Parameter("part", ScalarTypes.String, ArgumentKind.Literal, KustoFacts.DateTimeParts),
                new Parameter("date", ScalarTypes.DateTime))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None)
            .obsolete("datetime_part");

    public static final FunctionSymbol DatetimePart =
        new FunctionSymbol("datetime_part", ScalarTypes.Int,
                new Parameter("part", ScalarTypes.String, ArgumentKind.Literal, KustoFacts.DateTimeParts),
                new Parameter("date", ScalarTypes.DateTime))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol Now =
        new FunctionSymbol("now", ScalarTypes.DateTime,
                new Parameter("offset", ScalarTypes.TimeSpan, P.minOccurring(0)))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol Ago =
        new FunctionSymbol("ago", ScalarTypes.DateTime,
                new Parameter("timespan", ScalarTypes.TimeSpan, P.examples(KustoFacts.AgoExamples)))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol UnixTimeSecondsToDateTime =
        new FunctionSymbol("unixtime_seconds_todatetime", ScalarTypes.DateTime,
                new Parameter("number", ParameterTypeKind.Number))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol UnixTimeMillisecondsToDateTime =
        new FunctionSymbol("unixtime_milliseconds_todatetime", ScalarTypes.DateTime,
                new Parameter("number", ParameterTypeKind.Number))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol UnixTimeMicrosecondsToDateTime =
        new FunctionSymbol("unixtime_microseconds_todatetime", ScalarTypes.DateTime,
                new Parameter("number", ParameterTypeKind.Number))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol UnixTimeNanosecondsToDateTime =
        new FunctionSymbol("unixtime_nanoseconds_todatetime", ScalarTypes.DateTime,
                new Parameter("number", ParameterTypeKind.Number))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol DatetimeLocalToUtc =
        new FunctionSymbol("datetime_local_to_utc", ScalarTypes.DateTime,
                new Parameter("from", ScalarTypes.DateTime),
                new Parameter("timezone", ScalarTypes.String))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol DatetimeUtcToLocal =
        new FunctionSymbol("datetime_utc_to_local", ScalarTypes.DateTime,
                new Parameter("from", ScalarTypes.DateTime),
                new Parameter("timezone", ScalarTypes.String))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol DateTimeListTimezones =
        new FunctionSymbol("datetime_list_timezones",
                ScalarTypes.DynamicArray)
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol PunycodeDecode =
        new FunctionSymbol("punycode_to_string", ScalarTypes.String,
                new Parameter("string", ScalarTypes.String))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol PunycodeEncode =
        new FunctionSymbol("punycode_from_string", ScalarTypes.String,
                new Parameter("string", ScalarTypes.String))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol PunycodeDomainDecode =
        new FunctionSymbol("punycode_domain_from_string", ScalarTypes.String,
                new Parameter("string", ScalarTypes.String))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol PunycodeDomainEncode =
        new FunctionSymbol("punycode_domain_to_string", ScalarTypes.String,
                new Parameter("string", ScalarTypes.String))
            .constantFoldable()
            .withResultNameKind(ResultNameKind.None);

    // endregion

    // region hash functions
    public static final FunctionSymbol HashCrc32 =
        new FunctionSymbol("__hash_crc32", ScalarTypes.Long,
                new Parameter("source", ParameterTypeKind.NotDynamic),
                new Parameter("mod", ParameterTypeKind.Integer),
                new Parameter("seed", ParameterTypeKind.Integer))
            .constantFoldable()
            .hide();

    public static final FunctionSymbol HashManyCrc32 =
        new FunctionSymbol("__hash_many_crc32", ScalarTypes.Long,
                new Parameter("arg", ParameterTypeKind.Scalar, P.maxOccurring(2)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable()
            .hide();

    public static final FunctionSymbol HashDjb2 =
        new FunctionSymbol("__hash_djb2", ScalarTypes.Long,
                new Parameter("source", ParameterTypeKind.NotDynamic),
                new Parameter("mod", ParameterTypeKind.Integer, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable()
            .hide();

    public static final FunctionSymbol InternalHashXXH64 =
        new FunctionSymbol("__hash_xxh64", ScalarTypes.Long,
                new Parameter("source", ParameterTypeKind.NotDynamic),
                new Parameter("mod", ParameterTypeKind.Integer),
                new Parameter("seed", ParameterTypeKind.Integer))
            .constantFoldable()
            .hide();

    public static final FunctionSymbol Hash =
        new FunctionSymbol("hash", ScalarTypes.Long,
                new Parameter("source", ParameterTypeKind.NotDynamic),
                new Parameter("mod", ParameterTypeKind.Integer, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol HashXXH64 =
        new FunctionSymbol("hash_xxhash64", ScalarTypes.Long,
                new Parameter("source", ParameterTypeKind.NotDynamic),
                new Parameter("mod", ParameterTypeKind.Integer, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol HashSha256 =
        new FunctionSymbol("hash_sha256", ScalarTypes.String,
                new Parameter("source", ParameterTypeKind.NotDynamic))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol HashMd5 =
        new FunctionSymbol("hash_md5", ScalarTypes.String,
                new Parameter("source", ParameterTypeKind.NotDynamic))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol HashSha1 =
        new FunctionSymbol("hash_sha1", ScalarTypes.String,
                new Parameter("source", ParameterTypeKind.NotDynamic))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol HashCombine =
        new FunctionSymbol("hash_combine", ScalarTypes.Long,
                new Parameter("source", ParameterTypeKind.Scalar, P.minOccurring(2), P.maxOccurring(FunctionHelpers.MaxRepeat)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol HashMany =
        new FunctionSymbol("hash_many", ScalarTypes.Long,
                new Parameter("source", ParameterTypeKind.NotDynamic, P.minOccurring(1), P.maxOccurring(FunctionHelpers.MaxRepeat)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();
    // endregion

    // region iif / case
    public static final FunctionSymbol Iif =
        new FunctionSymbol("iif", ReturnTypeKind.CommonNonDynamic,
                new Parameter("if", ScalarTypes.Bool),
                new Parameter("then", ParameterTypeKind.CommonScalarOrDynamic),
                new Parameter("else", ParameterTypeKind.CommonScalarOrDynamic))
            .constantFoldable();

    public static final FunctionSymbol Iff =
        new FunctionSymbol("iff", ReturnTypeKind.CommonNonDynamic,
                new Parameter("if", ScalarTypes.Bool),
                new Parameter("then", ParameterTypeKind.CommonScalarOrDynamic),
                new Parameter("else", ParameterTypeKind.CommonScalarOrDynamic))
            .constantFoldable();

    public static final FunctionSymbol Case =
        new FunctionSymbol("case",
                new Signature(ReturnTypeKind.CommonNonDynamic,
                        new Parameter("predicate", ScalarTypes.Bool, P.maxOccurring(FunctionHelpers.MaxRepeat)),
                        new Parameter("then", ParameterTypeKind.CommonScalarOrDynamic, P.maxOccurring(FunctionHelpers.MaxRepeat)),
                        new Parameter("else", ParameterTypeKind.CommonScalarOrDynamic))
                    .withLayout(ParameterLayouts.BlockRepeating))
            .constantFoldable();

    public static final FunctionSymbol Assert =
        new FunctionSymbol("assert", ScalarTypes.Bool,
            new Parameter("predicate", ScalarTypes.Bool),
            new Parameter("message", ScalarTypes.String));
    // endregion

    // region bin / floor
    public static final FunctionSymbol Bin =
        new FunctionSymbol("bin",
                new Signature(ReturnTypeKind.Widest,
                    new Parameter("value", ParameterTypeKind.Number),
                    new Parameter("roundTo", ParameterTypeKind.Number)),
                new Signature(ScalarTypes.TimeSpan,
                    new Parameter("value", ScalarTypes.TimeSpan),
                    new Parameter("roundTo", ScalarTypes.TimeSpan)),
                new Signature(ScalarTypes.DateTime,
                    new Parameter("value", ScalarTypes.DateTime),
                    new Parameter("roundTo", ScalarTypes.DateTime)),
                new Signature(ScalarTypes.DateTime,
                    new Parameter("value", ScalarTypes.DateTime),
                    new Parameter("roundTo", ScalarTypes.TimeSpan)))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Floor =
        new FunctionSymbol("floor",
                new Signature(ReturnTypeKind.Widest,
                    new Parameter("value", ParameterTypeKind.Number),
                    new Parameter("roundTo", ParameterTypeKind.Number)),
                new Signature(ScalarTypes.TimeSpan,
                    new Parameter("value", ScalarTypes.TimeSpan),
                    new Parameter("roundTo", ScalarTypes.TimeSpan)),
                new Signature(ScalarTypes.DateTime,
                    new Parameter("value", ScalarTypes.DateTime),
                    new Parameter("roundTo", ScalarTypes.DateTime)),
                new Signature(ScalarTypes.DateTime,
                    new Parameter("value", ScalarTypes.DateTime),
                    new Parameter("roundTo", ScalarTypes.TimeSpan)))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol BinAt =
        new FunctionSymbol("bin_at",
                new Signature(ReturnTypeKind.Widest,
                    new Parameter("value", ParameterTypeKind.Number),
                    new Parameter("bin_size", ParameterTypeKind.Number),
                    new Parameter("fixed_point", ParameterTypeKind.Number)),
                new Signature(ScalarTypes.TimeSpan,
                    new Parameter("value", ScalarTypes.TimeSpan),
                    new Parameter("bin_size", ParameterTypeKind.Number),
                    new Parameter("fixed_point", ScalarTypes.TimeSpan)),
                new Signature(ScalarTypes.TimeSpan,
                    new Parameter("value", ScalarTypes.TimeSpan),
                    new Parameter("bin_size", ScalarTypes.TimeSpan),
                    new Parameter("fixed_point", ScalarTypes.TimeSpan)),
                new Signature(ScalarTypes.DateTime,
                    new Parameter("value", ScalarTypes.DateTime),
                    new Parameter("bin_size", ParameterTypeKind.Summable),
                    new Parameter("fixed_point", ScalarTypes.DateTime)))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol BinAuto =
        new FunctionSymbol("bin_auto", //"bin_at(value, query_bin_auto_size, query_bin_auto_at)", 
                context ->
                {
                    if (context.getArgument("value") != null && context.getArgument("value").resultType() instanceof ScalarSymbol valueType) // PORT: §3.14 ?.
                    {
                        if (valueType.isNumeric())
                        {
                            return
                                TypeFacts.promoteToLong( // PORT: §3.5
                                    TypeFacts.getCommonScalarType(
                                        valueType,
                                        context.getResultType("query_bin_auto_size"),
                                        context.getResultType("query_bin_auto_at")));
                        }
                        else
                        {
                            return TypeFacts.promoteToLong(valueType); // PORT: §3.5
                        }
                    }
                    else
                    {
                        return ScalarTypes.Unknown;
                    }
                },
                Tabularity.Scalar,
                new Parameter("value", ParameterTypeKind.Summable))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable()
            .hide();
    // endregion

    // region bool functions  (test state, return bool)
    public static final FunctionSymbol Not =
        new FunctionSymbol("not", ScalarTypes.Bool,
                new Parameter("expression", ScalarTypes.Bool))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol NotNull_Deprecated =
        new FunctionSymbol("notnull", ScalarTypes.Bool,
                new Parameter("expression", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None)
            .obsolete("isnotnull");

    public static final FunctionSymbol IsNotNull =
        new FunctionSymbol("isnotnull", ScalarTypes.Bool,
                new Parameter("expression", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol IsNull =
        new FunctionSymbol("isnull", ScalarTypes.Bool,
                new Parameter("expression", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol NotEmpty_Deprecated =
        new FunctionSymbol("notempty", ScalarTypes.Bool,
                new Parameter("value", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None)
            .obsolete("isnotempty");

    public static final FunctionSymbol IsNotEmpty =
        new FunctionSymbol("isnotempty", ScalarTypes.Bool,
                new Parameter("value", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol IsEmpty =
        new FunctionSymbol("isempty", ScalarTypes.Bool,
                new Parameter("value", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol IsAscii =
        new FunctionSymbol("isascii", ScalarTypes.Bool,
                new Parameter("value", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol IsUtf8 =
        new FunctionSymbol("isutf8", ScalarTypes.Bool,
                new Parameter("value", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol IsColumnExists =
        new FunctionSymbol("iscolumnexists", ScalarTypes.Bool,
                new Parameter("column_name", ScalarTypes.String, ArgumentKind.Constant))
            .withResultNameKind(ResultNameKind.None)
            .hide();

    public static final FunctionSymbol ColumnIfExists_Deprecated =
        new FunctionSymbol("columnifexists", ReturnTypeKind.Parameter1,
                new Parameter("column_name", ScalarTypes.String, ArgumentKind.Constant),
                new Parameter("defaultValue", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.FirstArgumentValueIfColumn)
            .obsolete("column_ifexists");

    public static final FunctionSymbol ColumnIfExists =
        new FunctionSymbol("column_ifexists", ReturnTypeKind.Parameter1,
                new Parameter("column_name", ScalarTypes.String, ArgumentKind.Constant),
                new Parameter("defaultValue", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.FirstArgumentValueIfColumn);

    public static final FunctionSymbol Around = new FunctionSymbol("around", ScalarTypes.Bool,
            new Parameter("value", ParameterTypeKind.Scalar),
            new Parameter("center", ParameterTypeKind.Scalar),
            new Parameter("delta", ParameterTypeKind.Scalar))
        // TODO: Check how is it possible to define different type combinations:
        // (datetime, datetime, timespan)
        // (numeric, numeric, numeric)
        .withResultNameKind(ResultNameKind.None);
    // endregion

    // region bitwise functions
    public static final FunctionSymbol BinaryAnd =
        new FunctionSymbol("binary_and", ScalarTypes.Long,
                new Parameter("value1", ParameterTypeKind.Integer),
                new Parameter("value2", ParameterTypeKind.Integer))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol BinaryOr =
        new FunctionSymbol("binary_or", ScalarTypes.Long,
                new Parameter("value1", ParameterTypeKind.Integer),
                new Parameter("value2", ParameterTypeKind.Integer))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol BinaryXor =
        new FunctionSymbol("binary_xor", ScalarTypes.Long,
                new Parameter("value1", ParameterTypeKind.Integer),
                new Parameter("value2", ParameterTypeKind.Integer))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol BinaryNot =
        new FunctionSymbol("binary_not", ScalarTypes.Long,
                new Parameter("value", ParameterTypeKind.Integer))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol BinaryShiftRight =
        new FunctionSymbol("binary_shift_right", ScalarTypes.Long,
                new Parameter("value", ParameterTypeKind.Integer),
                new Parameter("shift", ParameterTypeKind.Integer))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol BinaryShiftLeft =
        new FunctionSymbol("binary_shift_left", ScalarTypes.Long,
                new Parameter("value", ParameterTypeKind.Integer),
                new Parameter("shift", ParameterTypeKind.Integer))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol BitsetCountOnes =
        new FunctionSymbol("bitset_count_ones", ScalarTypes.Long,
                new Parameter("value", ParameterTypeKind.Integer))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();
    // endregion

    // region dynamic array/object functions
    public static final FunctionSymbol TreePath =
        new FunctionSymbol("treepath",
                ScalarTypes.DynamicArrayOfString,
                new Parameter("object", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
            .withResultNamePrefix("tree")
            .constantFoldable();

    public static final FunctionSymbol Repeat =
        new FunctionSymbol("repeat",
                ReturnTypeKind.Parameter0Array,
                new Parameter("value", ParameterTypeKind.Scalar),
                new Parameter("count", ScalarTypes.Long))
            .withResultNameKind(ResultNameKind.PrefixAndFirstArgument)
            .withResultNamePrefix("repeat")
            .constantFoldable();

    public static final FunctionSymbol Arraylength_Deprecated =
        new FunctionSymbol("arraylength", ScalarTypes.Long,
                new Parameter("array", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable()
            .obsolete("array_length");

    public static final FunctionSymbol ArrayLength =
        new FunctionSymbol("array_length", ScalarTypes.Long,
                new Parameter("array", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ArrayReverse =
        new FunctionSymbol("array_reverse",
                ReturnTypeKind.Parameter0,
                new Parameter("value", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Range =
        new FunctionSymbol("range",
                ReturnTypeKind.Parameter0Array,
                new Parameter("start", ParameterTypeKind.Summable),
                new Parameter("stop", ParameterTypeKind.Summable),
                new Parameter("step", ParameterTypeKind.Summable, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ArrayConcat =
        new FunctionSymbol("array_concat",
                context ->
                {
                    var arrays = context.getArguments("array");
                    var commonType = TypeFacts.getCommonResultType(arrays, Conversion.None); // PORT: §3.14 ??
                    return commonType != null ? commonType : ScalarTypes.DynamicArray;
                },
                Tabularity.Scalar,
                new Parameter("array", ParameterTypeKind.DynamicArray, P.maxOccurring(64)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ArrayIff =
        new FunctionSymbol("array_iff",
                context ->
                {
                    var whenTrue = context.getArgument("when_true");
                    var whenFalse = context.getArgument("when_true");
                    var commonResult = TypeFacts.getCommonResultType(whenTrue, whenFalse);
                    if (!(commonResult instanceof DynamicSymbol))
                        commonResult = ScalarTypes.getDynamicArray(commonResult);
                    return commonResult;
                },
                Tabularity.Scalar,
                new Parameter("condition_array", ParameterTypeKind.DynamicArray),
                new Parameter("when_true", ParameterTypeKind.Scalar),
                new Parameter("when_false", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ArrayIif =
        new FunctionSymbol("array_iif",
                context ->
                {
                    var whenTrue = context.getArgument("when_true");
                    var whenFalse = context.getArgument("when_true");
                    var commonResult = TypeFacts.getCommonResultType(whenTrue, whenFalse);
                    if (!(commonResult instanceof DynamicSymbol))
                        commonResult = ScalarTypes.getDynamicArray(commonResult);
                    return commonResult;
                },
                Tabularity.Scalar,
                new Parameter("condition_array", ParameterTypeKind.DynamicArray),
                new Parameter("when_true", ParameterTypeKind.Scalar),
                new Parameter("when_false", ParameterTypeKind.Scalar))
            .constantFoldable()
            .hide();

    public static final FunctionSymbol ArrayIndexOf =
        new FunctionSymbol("array_index_of",
                ScalarTypes.Long,
                new Parameter("array", ParameterTypeKind.DynamicArray),
                new Parameter("value", ParameterTypeKind.Scalar),
                new Parameter("start", ParameterTypeKind.Integer, P.minOccurring(0)),
                new Parameter("length", ParameterTypeKind.Integer, P.minOccurring(0)),
                new Parameter("occurrence", ParameterTypeKind.Integer, ArgumentKind.Constant, P.minOccurring(0)))
            .constantFoldable();

    public static final FunctionSymbol SetHasElement =
        new FunctionSymbol("set_has_element",
                ScalarTypes.Bool,
                new Parameter("set", ParameterTypeKind.DynamicArray),
                new Parameter("value", ParameterTypeKind.Scalar))
            .constantFoldable();

    public static final FunctionSymbol ArraySlice =
        new FunctionSymbol("array_slice",
                ReturnTypeKind.Parameter0,
                new Parameter("array", ParameterTypeKind.DynamicArray),
                new Parameter("start", ParameterTypeKind.Integer),
                new Parameter("end", ParameterTypeKind.Integer))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ArraySplit =
        new FunctionSymbol("array_split",
                new Signature(
                    ReturnTypeKind.Parameter0,
                    new Parameter("array", ParameterTypeKind.DynamicArray),
                    new Parameter("index", ParameterTypeKind.Integer)),
                new Signature(
                    ReturnTypeKind.Parameter0,
                    new Parameter("array", ParameterTypeKind.DynamicArray),
                    new Parameter("indices", ParameterTypeKind.DynamicArray)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ArrayShiftLeft =
        new FunctionSymbol("array_shift_left",
                ReturnTypeKind.Parameter0,
                new Parameter("array", ParameterTypeKind.DynamicArray),
                new Parameter("shift_count", ParameterTypeKind.Integer),
                new Parameter("default_value", ParameterTypeKind.Scalar, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ArrayShiftRight =
        new FunctionSymbol("array_shift_right",
                ReturnTypeKind.Parameter0,
                new Parameter("array", ParameterTypeKind.DynamicArray),
                new Parameter("shift_count", ParameterTypeKind.Integer),
                new Parameter("default_value", ParameterTypeKind.Scalar, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ArrayRotateLeft =
        new FunctionSymbol("array_rotate_left",
                ReturnTypeKind.Parameter0,
                new Parameter("array", ParameterTypeKind.DynamicArray),
                new Parameter("rotate_count", ParameterTypeKind.Integer))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol ArrayRotateRight =
        new FunctionSymbol("array_rotate_right",
                ReturnTypeKind.Parameter0,
                new Parameter("array", ParameterTypeKind.DynamicArray),
                new Parameter("rotate_count", ParameterTypeKind.Integer))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    private static final Parameter m_ArraySort_ArraysArg =
        new Parameter("arrays", ParameterTypeKind.DynamicArray, P.minOccurring(1), P.maxOccurring(64));

    private static final Parameter m_ArraySort_NullsLastArg =
        new Parameter("nulls_last", ScalarTypes.Bool, ArgumentKind.Constant, P.minOccurring(0), P.maxOccurring(1));

    public static final FunctionSymbol ArraySortAsc =
        new FunctionSymbol("array_sort_asc",
            new Signature(
                    Functions::GetArraySortResult, // PORT: §3.8 method group
                    Tabularity.Scalar,
                    m_ArraySort_ArraysArg,
                    m_ArraySort_NullsLastArg)
                .withLayout(Functions::ValidateArgumentsForArraySort));

    public static final FunctionSymbol ArraySortDesc =
        new FunctionSymbol("array_sort_desc",
            new Signature(
                    Functions::GetArraySortResult, // PORT: §3.8 method group
                    Tabularity.Scalar,
                    m_ArraySort_ArraysArg,
                    m_ArraySort_NullsLastArg)
                .withLayout(Functions::ValidateArgumentsForArraySort));

    private static void ValidateArgumentsForArraySort(Signature signature, List<Expression> arguments, List<Parameter> argumentParameters)
    {
        for (var i = 0; i < arguments.size(); i++)
        {
            if ((i < arguments.size() - 1) || !FunctionHelpers.isBoolean(arguments.get(i)))
            {
                argumentParameters.add(m_ArraySort_ArraysArg);
            }
            else
            {
                argumentParameters.add(m_ArraySort_NullsLastArg);
            }
        }
    }

    private static TypeSymbol GetArraySortResult(CustomReturnTypeContext context)
    {
        var result = new ArrayList<ColumnSymbol>();

        for (int i = 0;
             (i < context.arguments().size()) && (TypeFacts.isDynamicArray(context.arguments().get(i).resultType())); i++) // PORT: §3.5
        {
            var argument = context.arguments().get(i);
            var argumentExpressionName = context.getResultName(argument);
            var resultColumnName = DotNetStrings.isNullOrEmpty(argumentExpressionName) ? "array" + i + "_sorted" : DotNet.str(argumentExpressionName) + "_sorted"; // PORT: §3.14 string interpolation
            var resultColumn = new ColumnSymbol(resultColumnName, argument.resultType(), null, null, argument);
            result.add(resultColumn);
        }

        return new TupleSymbol(result);
    }

    public static final FunctionSymbol BagKeys =
        new FunctionSymbol("bag_keys",
                ScalarTypes.DynamicArrayOfString,
                new Parameter("object", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Zip =
        new FunctionSymbol("zip",
                ScalarTypes.DynamicArrayOfArray,
                new Parameter("array", ParameterTypeKind.DynamicArray, P.minOccurring(2), P.maxOccurring(64)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Pack =
        new FunctionSymbol("pack",
                new Signature(
                        Functions::GetBagPackReturnType, // PORT: §3.8 method group
                        Tabularity.Scalar,
                        new Parameter("key", ScalarTypes.String, P.maxOccurring(FunctionHelpers.MaxRepeat)),
                        new Parameter("value", ParameterTypeKind.Scalar, P.maxOccurring(FunctionHelpers.MaxRepeat)))
                    .withLayout(ParameterLayouts.BlockRepeating))
            .withResultNameKind(ResultNameKind.None)
            .obsolete("bag_pack")
            .constantFoldable();

    public static final FunctionSymbol PackDictionary =
        new FunctionSymbol("pack_dictionary",
                new Signature(
                        Functions::GetBagPackReturnType, // PORT: §3.8 method group
                        Tabularity.Scalar,
                        new Parameter("key", ScalarTypes.String, P.maxOccurring(FunctionHelpers.MaxRepeat)),
                        new Parameter("value", ParameterTypeKind.Scalar, P.maxOccurring(FunctionHelpers.MaxRepeat)))
                    .withLayout(ParameterLayouts.BlockRepeating))
            .withResultNameKind(ResultNameKind.None)
            .obsolete("bag_pack")
            .constantFoldable();

    public static final FunctionSymbol BagPack =
        new FunctionSymbol("bag_pack",
                new Signature(
                        Functions::GetBagPackReturnType, // PORT: §3.8 method group
                        Tabularity.Scalar,
                        new Parameter("key", ScalarTypes.String, P.maxOccurring(FunctionHelpers.MaxRepeat)),
                        new Parameter("value", ParameterTypeKind.Scalar, P.maxOccurring(FunctionHelpers.MaxRepeat)))
                    .withLayout(ParameterLayouts.BlockRepeating))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    private static TypeSymbol GetBagPackReturnType(CustomReturnTypeContext context)
    {
        var keys = context.getArguments("key");
        var values = context.getArguments("value");
        var columns = new ArrayList<ColumnSymbol>(keys.size());

        for (int i = 0; i < keys.size(); i++)
        {
            if (keys.get(i).constantValue() instanceof String name)
            {
                var type = (i < values.size() ? values.get(i).resultType() : null); // PORT: §3.14 ??
                if (type == null) type = ScalarTypes.Unknown;
                columns.add(new ColumnSymbol(name, type));
            }
        }

        return ScalarTypes.getDynamicBag(columns);
    }

    public static final FunctionSymbol BagPackColumns =
        new FunctionSymbol("bag_pack_columns",
                Functions::GetBagPackColumnsReturnType, // PORT: §3.8 method group
                Tabularity.Scalar,
                new Parameter("value", ParameterTypeKind.Scalar, P.maxOccurring(FunctionHelpers.MaxRepeat), P.argumentKind(ArgumentKind.Column)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    private static TypeSymbol GetBagPackColumnsReturnType(CustomReturnTypeContext context)
    {
        var values = context.getArguments("value");
        var columns = new ArrayList<ColumnSymbol>(values.size());

        for (int i = 0; i < values.size(); i++)
        {
            if (values.get(i).referencedSymbol() instanceof ColumnSymbol col)
            {
                columns.add(col);
            }
        }

        return ScalarTypes.getDynamicBag(columns);
    }

    public static final FunctionSymbol PackAll =
        new FunctionSymbol("pack_all",
                context -> ScalarTypes.getDynamicBag(context.rowScope() != null ? context.rowScope().columns() : null), // PORT: §3.14 ?.
                Tabularity.Scalar,
            new Parameter("ignore_null_empty", ParameterTypeKind.Scalar, ArgumentKind.Literal, P.minOccurring(0), P.maxOccurring(1)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol PackArray =
        new FunctionSymbol("pack_array",
                new Signature(
                    context ->
                    {
                        var values = context.getArguments("value");
                        var type = TypeFacts.getCommonResultType(values);
                        return ScalarTypes.getDynamicArray(type);
                    },
                    Tabularity.Scalar,
                    new Parameter("value", ParameterTypeKind.Scalar, P.maxOccurring(FunctionHelpers.MaxRepeat))),
                new Signature(
                    context ->
                    {
                        return context.rowScope() != null
                            ? ScalarTypes.getDynamicArray(TypeFacts.getCommonColumnType(context.rowScope().columns()))
                            : ScalarTypes.DynamicArray;
                    },
                    Tabularity.Scalar,
                    new Parameter("value", ParameterTypeKind.Scalar, ArgumentKind.StarOnly)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    private static TypeSymbol GetSetType(CustomReturnTypeContext context)
    {
        var sets = context.getArguments("set");
        var commonType = TypeFacts.getCommonResultType(sets);
        return commonType instanceof DynamicArraySymbol ? commonType : ScalarTypes.DynamicArray;
    }

    public static final FunctionSymbol SetUnion =
        new FunctionSymbol("set_union",
                Functions::GetSetType, // PORT: §3.8 method group
                Tabularity.Scalar,
                new Parameter("set", ParameterTypeKind.DynamicArray, P.minOccurring(2), P.maxOccurring(64)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol SetIntersect =
        new FunctionSymbol("set_intersect",
                Functions::GetSetType, // PORT: §3.8 method group
                Tabularity.Scalar,
                new Parameter("set", ParameterTypeKind.DynamicArray, P.minOccurring(2), P.maxOccurring(64)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol SetDifference =
        new FunctionSymbol("set_difference",
                Functions::GetSetType, // PORT: §3.8 method group
                Tabularity.Scalar,
                new Parameter("set", ParameterTypeKind.DynamicArray, P.minOccurring(2), P.maxOccurring(64)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol SetEquals =
        new FunctionSymbol("set_equals",
                ScalarTypes.Bool,
                new Parameter("set1", ParameterTypeKind.DynamicArray),
                new Parameter("set2", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol BagMerge =
        new FunctionSymbol("bag_merge",
                context ->
                {
                    var bags = context.getArguments("bag");
                    if (bags.size() > 0
                        && bags.get(0).resultType() instanceof DynamicBagSymbol merged)
                    {
                        for (int i = 1; i < bags.size(); i++)
                        {
                            if (bags.get(i).resultType() instanceof DynamicBagSymbol obj)
                            {
                                merged = ScalarTypes.getDynamicBag(TypeFacts.union(merged.properties(), obj.properties()));
                            }
                        }

                        return merged;
                    }

                    return ScalarTypes.DynamicBag;
                },
                Tabularity.Scalar,
                new Parameter("bag", ParameterTypeKind.DynamicBag, P.minOccurring(2), P.maxOccurring(64)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol DynamicToJson =
        new FunctionSymbol("dynamic_to_json",
                ScalarTypes.String,
                new Parameter("dynamic", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol BagRemoveKeys =
        new FunctionSymbol("bag_remove_keys",
                context ->
                {
                    var bag = context.getArgument("bag");
                    var keys = context.getArgument("keys");

                    if (bag != null &&
                        bag.resultType() instanceof DynamicBagSymbol bs
                        && keys instanceof JsonArrayExpression ja)
                    {
                        var namesToRemove = ListExtensions.toHashSetEx(FunctionHelpers.getConstantValues(ja.values())); // PORT: §3.5
                        var newProps = Linq.toList(Linq.where(bs.properties(), p -> !namesToRemove.contains(p.name()))); // PORT: §3.6
                        return ScalarTypes.getDynamicBag(newProps);
                    }

                    return ScalarTypes.DynamicBag;
                },
                Tabularity.Scalar,
                new Parameter("bag", ParameterTypeKind.DynamicBag),
                new Parameter("keys", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol BagZip =
        new FunctionSymbol("bag_zip",
                ScalarTypes.DynamicBag,
                new Parameter("keys", ParameterTypeKind.DynamicArray),
                new Parameter("values", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol JaccardIndex =
        new FunctionSymbol("jaccard_index",
                ScalarTypes.DynamicArrayOfReal,
                new Parameter("set", ParameterTypeKind.DynamicArray, P.minOccurring(2), P.maxOccurring(2)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol BagHasKey =
        new FunctionSymbol("bag_has_key",
                ScalarTypes.Bool,
                new Parameter("bag", ParameterTypeKind.DynamicBag),
                new Parameter("key", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol BagSetKey =
        new FunctionSymbol("bag_set_key",
                context ->
                {
                    var bag = context.getArgument("bag");
                    var key = context.getArgument("key");
                    var value = context.getArgument("value");

                    if (bag != null
                        && key != null
                        && key.constantValue() instanceof String name
                        && !DotNetStrings.isNullOrWhiteSpace(name)
                        && !IsJsonPath(name) // maybe someday
                        && value != null
                        && value.resultType() instanceof ScalarSymbol type)
                    {
                        var currentBag = bag.resultType() instanceof DynamicBagSymbol dbs ? dbs : ScalarTypes.DynamicBag; // PORT: §3.14 as/??
                        var newProperty = new ColumnSymbol(name, type);
                        return currentBag.addOrUpdateProperty(newProperty);
                    }

                    var bagType = bag != null ? bag.resultType() : null; // PORT: §3.14 ?. ??
                    return bagType != null ? bagType : ScalarTypes.DynamicBag;
                },
                Tabularity.Scalar,
                new Parameter("bag", ParameterTypeKind.DynamicBag),
                new Parameter("key", ScalarTypes.String, ArgumentKind.Constant),
                new Parameter("value", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();


    private static boolean IsJsonPath(String key)
    {
        return key.contains("$") // PORT: §3.17 Contains(char) → contains(String)
               || key.contains(".")
               || key.contains("[")
               || key.contains("]");
    }
    // endregion

    // region digest / series functions
    public static final FunctionSymbol PercentileTDigest =
        new FunctionSymbol("percentile_tdigest",
                new Signature(
                    ScalarTypes.Dynamic,
                    new Parameter("tdigest", ParameterTypeKind.DynamicArray),
                    new Parameter("percentile1", ScalarTypes.Real)),
                new Signature(
                    ReturnTypeKind.ParameterNLiteral,
                    new Parameter("tdigest", ParameterTypeKind.DynamicArray),
                    new Parameter("percentile1", ScalarTypes.Real),
                    new Parameter("type", ScalarTypes.Type, ArgumentKind.Literal)))
            .withResultNameKind(ResultNameKind.NameAndFirstArgument);

    public static final FunctionSymbol PercentileArrayTDigest =
        new FunctionSymbol("percentiles_array_tdigest",
                new Signature(
                    ScalarTypes.DynamicArray,
                    new Parameter("tdigest", ParameterTypeKind.DynamicArray),
                    new Parameter("percentile", ScalarTypes.Real, P.maxOccurring(FunctionHelpers.MaxRepeat))),
                new Signature(
                    ScalarTypes.DynamicArray,
                    new Parameter("tdigest", ParameterTypeKind.DynamicArray),
                    new Parameter("percentiles", ParameterTypeKind.DynamicArray)))
            .withResultNamePrefix("percentile_tdigest")
            .withResultNameKind(ResultNameKind.PrefixAndFirstArgument);

    public static final FunctionSymbol PercentRankTDigest =
        new FunctionSymbol("percentrank_tdigest",
                ScalarTypes.Real,
                new Parameter("digest", ParameterTypeKind.DynamicArray),
                new Parameter("value", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol RankTDigest =
        new FunctionSymbol("rank_tdigest",
                ScalarTypes.Real,
                new Parameter("digest", ParameterTypeKind.DynamicArray),
                new Parameter("value", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol TdigestIsValid =
        new FunctionSymbol("tdigest_isvalid",
                ScalarTypes.Bool,
                new Parameter("digest", ParameterTypeKind.DynamicArray),
                new Parameter("value", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None)
            .hide();

    public static final FunctionSymbol HllIsValid =
        new FunctionSymbol("hll_isvalid",
                ScalarTypes.Bool,
                new Parameter("hll", ParameterTypeKind.DynamicArray),
                new Parameter("value", ParameterTypeKind.Scalar))
            .withResultNameKind(ResultNameKind.None)
            .hide();

    public static final FunctionSymbol TDigestMerge =
        new FunctionSymbol("tdigest_merge",
                ScalarTypes.DynamicArray,
                new Parameter("tdigest", ParameterTypeKind.DynamicArray, P.minOccurring(2), P.maxOccurring(16)))
            .withResultNameKind(ResultNameKind.PrefixOnly)
            .withResultNamePrefix("tdigests_merge_result");

    public static final FunctionSymbol MergeTDigest =
        new FunctionSymbol("merge_tdigest",
                ScalarTypes.DynamicArray,
                new Parameter("tdigest", ParameterTypeKind.DynamicArray, P.minOccurring(2), P.maxOccurring(16)))
            .withResultNameKind(ResultNameKind.PrefixOnly)
            .withResultNamePrefix("tdigests_merge_result");

    public static final FunctionSymbol HllMerge =
        new FunctionSymbol("hll_merge",
                ScalarTypes.DynamicArray,
                new Parameter("hll", ParameterTypeKind.DynamicArray, P.minOccurring(2), P.maxOccurring(16)))
            .withResultNameKind(ResultNameKind.PrefixOnly)
            .withResultNamePrefix("hll_merged_result");

    public static final FunctionSymbol DCountHll =
        new FunctionSymbol("dcount_hll",
                ScalarTypes.Long,
                new Parameter("hll", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.NameAndFirstArgument);

    public static final FunctionSymbol HllNormalize =
        new FunctionSymbol("__hll_normalize", ScalarTypes.Dynamic,
                new Parameter("hll", ScalarTypes.Dynamic, P.minOccurring(2), P.maxOccurring(16)))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .hide();

    public static final FunctionSymbol SeriesFir =
        new FunctionSymbol("series_fir",
                ScalarTypes.DynamicArrayOfReal,
                new Parameter("series", ParameterTypeKind.DynamicArray),
                new Parameter("filter", ParameterTypeKind.DynamicArray),
                new Parameter("normalize", ScalarTypes.Bool, P.minOccurring(0)),
                new Parameter("center", ScalarTypes.Bool, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol SeriesStats =
        new FunctionSymbol("series_stats",
            context ->
            {
                var source = context.getArgument("series");
                return FunctionHelpers.makePrefixedTuple(context, "series",
                    new TupleSymbol(
                        new ColumnSymbol("min", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("min_idx", ScalarTypes.Long, null, null, source),
                        new ColumnSymbol("max", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("max_idx", ScalarTypes.Long, null, null, source),
                        new ColumnSymbol("avg", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("stdev", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("variance", ScalarTypes.Real, null, null, source)));
            },
            Tabularity.Scalar,
            new Parameter("series", ParameterTypeKind.DynamicArray),
            new Parameter("ignore_nonfinite", ScalarTypes.Bool, ArgumentKind.Constant, P.minOccurring(0)));

    public static final FunctionSymbol SeriesStatsDynamic =
        new FunctionSymbol("series_stats_dynamic",
                context ->
                {
                    var source = context.getArgument("series");
                    return new DynamicBagSymbol(
                        new ColumnSymbol("min", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("min_idx", ScalarTypes.Long, null, null, source),
                        new ColumnSymbol("max", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("max_idx", ScalarTypes.Long, null, null, source),
                        new ColumnSymbol("avg", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("stdev", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("variance", ScalarTypes.Real, null, null, source));
                },
                Tabularity.Scalar,
                new Parameter("series", ParameterTypeKind.DynamicArray),
                new Parameter("ignore_nonfinite", ScalarTypes.Bool, ArgumentKind.Constant, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol ArraySum =
        new FunctionSymbol("array_sum",
                ScalarTypes.Real,
                new Parameter("array", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None)
            .obsolete("series_sum");

    public static final FunctionSymbol SeriesFft =
        new FunctionSymbol("series_fft",
            context ->
            {
                var source = context.getArgument("series");
                return FunctionHelpers.makePrefixedTuple(context, "series",
                    new TupleSymbol(
                        new ColumnSymbol("real", ScalarTypes.DynamicArrayOfReal, null, null, source),
                        new ColumnSymbol("imag", ScalarTypes.DynamicArrayOfReal, null, null, source)));
            },
            Tabularity.Scalar,
            new Parameter("series", ParameterTypeKind.DynamicArray),
            new Parameter("series_imaginary", ParameterTypeKind.DynamicArray, P.minOccurring(0)));

    public static final FunctionSymbol SeriesIfft =
        new FunctionSymbol("series_ifft",
            context ->
            {
                var source = context.getArgument("series");
                return FunctionHelpers.makePrefixedTuple(context, "series",
                    new TupleSymbol(
                        new ColumnSymbol("real", ScalarTypes.DynamicArrayOfReal, null, null, source),
                        new ColumnSymbol("imag", ScalarTypes.DynamicArrayOfReal, null, null, source)));
            },
            Tabularity.Scalar,
            new Parameter("series", ParameterTypeKind.DynamicArray),
            new Parameter("series_imaginary", ParameterTypeKind.DynamicArray, P.minOccurring(0)));

    public static final FunctionSymbol SeriesFitPoly =
        new FunctionSymbol("series_fit_poly",
            context ->
            {
                var source = context.getArgument("y_series");
                return FunctionHelpers.makePrefixedTuple(context, "y_series",
                    new TupleSymbol(
                        new ColumnSymbol("rsquare", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("coefficients", ScalarTypes.DynamicArrayOfReal, null, null, source),
                        new ColumnSymbol("variance", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("rvariance", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("poly_fit", ScalarTypes.DynamicArrayOfReal, null, null, source)));
            },
            Tabularity.Scalar,
            new Parameter("y_series", ParameterTypeKind.DynamicArray),
            new Parameter("x_series", ParameterTypeKind.DynamicArray, P.minOccurring(0)),
            new Parameter("degree", ScalarTypes.Int, P.minOccurring(0)));

    public static final FunctionSymbol SeriesFitLine =
        new FunctionSymbol("series_fit_line",
            context ->
            {
                var source = context.getArgument("series");
                return FunctionHelpers.makePrefixedTuple(context, "series",
                    new TupleSymbol(
                        new ColumnSymbol("rsquare", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("slope", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("variance", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("rvariance", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("interception", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("line_fit", ScalarTypes.DynamicArrayOfReal, null, null, source)));
            },
            Tabularity.Scalar,
            new Parameter("series", ParameterTypeKind.DynamicArray));

    public static final FunctionSymbol SeriesFitLineDynamic =
        new FunctionSymbol("series_fit_line_dynamic",
                context ->
                {
                    var source = context.getArgument("series");
                    return ScalarTypes.getDynamicBag(
                        new ColumnSymbol("rsquare", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("slope", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("variance", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("rvariance", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("interception", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("line_fit", ScalarTypes.DynamicArrayOfReal, null, null, source));
                },
                Tabularity.Scalar,
                new Parameter("series", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesFit2Lines =
        new FunctionSymbol("series_fit_2lines",
            context ->
            {
                var source = context.getArgument("array");
                return FunctionHelpers.makePrefixedTuple(context, "array",
                    new TupleSymbol(
                        new ColumnSymbol("rsquare", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("split_idx", ScalarTypes.Long, null, null, source),
                        new ColumnSymbol("variance", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("rvariance", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("line_fit", ScalarTypes.DynamicArrayOfReal, null, null, source),
                        new ColumnSymbol("right_rsquare", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("right_slope", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("right_interception", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("right_variance", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("right_rvariance", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("left_rsquare", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("left_slope", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("left_interception", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("left_variance", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("left_rvariance", ScalarTypes.Real, null, null, source)));
            },
            Tabularity.Scalar,
            new Parameter("array", ParameterTypeKind.DynamicArray));


    public static final FunctionSymbol SeriesFit2LinesDynamic =
        new FunctionSymbol("series_fit_2lines_dynamic",
                context ->
                {
                    var source = context.getArgument("array");
                    return new DynamicBagSymbol(
                        new ColumnSymbol("rsquare", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("split_idx", ScalarTypes.Long, null, null, source),
                        new ColumnSymbol("variance", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("rvariance", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("line_fit", ScalarTypes.DynamicArrayOfReal, null, null, source),
                        new ColumnSymbol("right_rsquare", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("right_slope", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("right_interception", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("right_variance", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("right_rvariance", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("left_rsquare", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("left_slope", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("left_interception", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("left_variance", ScalarTypes.Real, null, null, source),
                        new ColumnSymbol("left_rvariance", ScalarTypes.Real, null, null, source));
                },
                Tabularity.Scalar,
                new Parameter("series", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesOutliers =
        new FunctionSymbol("series_outliers",
                new Signature(ReturnTypeKind.Parameter0,
                    new Parameter("series", ParameterTypeKind.DynamicArray),
                    new Parameter("kind", ScalarTypes.String, P.minOccurring(0)),
                    new Parameter("ignore_val", ParameterTypeKind.Number, P.minOccurring(0))),
                new Signature(ReturnTypeKind.Parameter0,
                    new Parameter("series", ParameterTypeKind.DynamicArray),
                    new Parameter("kind", ScalarTypes.String),
                    new Parameter("ignore_val", ScalarTypes.Real),
                    new Parameter("min_percentile", ScalarTypes.Real),
                    new Parameter("max_percentile", ScalarTypes.Real, P.minOccurring(0)),
                    new Parameter("test_points", ParameterTypeKind.Integer, P.minOccurring(0))))
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol SeriesIIR =
        new FunctionSymbol("series_iir",
                ScalarTypes.DynamicArrayOfReal,
                new Parameter("series", ParameterTypeKind.DynamicArray),
                new Parameter("numerators", ParameterTypeKind.DynamicArray),
                new Parameter("denominators", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol SeriesPeriodsDetect =
        new FunctionSymbol("series_periods_detect",
            context ->
            {
                var source = context.getArgument("series");
                return FunctionHelpers.makePrefixedTuple(context, "series",
                    new TupleSymbol(
                        new ColumnSymbol("periods", ScalarTypes.DynamicArrayOfReal, null, null, source),
                        new ColumnSymbol("scores", ScalarTypes.DynamicArrayOfReal, null, null, source)));
            },
            Tabularity.Scalar,
            new Parameter("series", ParameterTypeKind.DynamicArray),
            new Parameter("min_period", ParameterTypeKind.Number),
            new Parameter("max_period", ParameterTypeKind.Number),
            new Parameter("num_periods", ScalarTypes.Long));

    public static final FunctionSymbol SeriesPeriodsValidate =
        new FunctionSymbol("series_periods_validate",
            context ->
            {
                var source = context.getArgument("series");
                return FunctionHelpers.makePrefixedTuple(context, "series",
                    new TupleSymbol(
                        new ColumnSymbol("periods", ScalarTypes.DynamicArrayOfReal, null, null, source),
                        new ColumnSymbol("scores", ScalarTypes.DynamicArrayOfReal, null, null, source)));
            },
            Tabularity.Scalar,
            new Parameter("series", ParameterTypeKind.DynamicArray),
            new Parameter("period", ParameterTypeKind.Number, P.maxOccurring(16)));

    public static final FunctionSymbol SeriesFillBackwards =
        new FunctionSymbol("series_fill_backward",
                ReturnTypeKind.Parameter0,
                new Parameter("series", ParameterTypeKind.DynamicArray),
                new Parameter("missing_value_placeholder", ParameterTypeKind.Number, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol SeriesFillForward =
        new FunctionSymbol("series_fill_forward",
                ReturnTypeKind.Parameter0,
                new Parameter("series", ParameterTypeKind.DynamicArray),
                new Parameter("missing_value_placeholder", ParameterTypeKind.Number, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol SeriesFillConst =
        new FunctionSymbol("series_fill_const",
                ReturnTypeKind.Parameter0,
                new Parameter("series", ParameterTypeKind.DynamicArray),
                new Parameter("constant_value", ParameterTypeKind.Number, P.minOccurring(0)),
                new Parameter("missing_value_placeholder", ParameterTypeKind.Number, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol SeriesFillLinear =
        new FunctionSymbol("series_fill_linear",
                ReturnTypeKind.Parameter0,
                new Parameter("series", ParameterTypeKind.DynamicArray),
                new Parameter("missing_value_placeholder", ParameterTypeKind.Number, P.minOccurring(0)),
                new Parameter("fill_edges", ScalarTypes.Bool, P.minOccurring(0)),
                new Parameter("constant_value", ParameterTypeKind.Number, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol SeriesAdd =
        new FunctionSymbol("series_add",
                new Signature(
                    ScalarTypes.DynamicArray,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)),
                new Signature(
                    ScalarTypes.DynamicArray,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.Number)),
                new Signature(
                    ScalarTypes.DynamicArray,
                    new Parameter("series1", ParameterTypeKind.Number),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesSubtract =
        new FunctionSymbol("series_subtract",
                new Signature(ScalarTypes.DynamicArray,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)),
                new Signature(ScalarTypes.DynamicArray,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.Number)),
                new Signature(ScalarTypes.DynamicArray,
                    new Parameter("series1", ParameterTypeKind.Number),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesMultiply =
        new FunctionSymbol("series_multiply",
                new Signature(ScalarTypes.DynamicArray,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)),
                new Signature(ScalarTypes.DynamicArray,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.Number)),
                new Signature(ScalarTypes.DynamicArray,
                    new Parameter("series1", ParameterTypeKind.Number),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesDivide =
        new FunctionSymbol("series_divide",
                new Signature(
                    ScalarTypes.DynamicArrayOfReal,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)),
                new Signature(
                    ScalarTypes.DynamicArrayOfReal,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.Number)),
                new Signature(
                    ScalarTypes.DynamicArrayOfReal,
                    new Parameter("series1", ParameterTypeKind.Number),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesPow =
        new FunctionSymbol("series_pow",
                new Signature(
                    ScalarTypes.DynamicArrayOfReal,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)),
                new Signature(
                    ScalarTypes.DynamicArrayOfReal,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.Number)),
                new Signature(
                    ScalarTypes.DynamicArrayOfReal,
                    new Parameter("series1", ParameterTypeKind.Number),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesGreater =
        new FunctionSymbol("series_greater",
                new Signature(
                    ScalarTypes.DynamicArrayOfBool,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)),
                new Signature(
                    ScalarTypes.DynamicArrayOfBool,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.Number)),
                new Signature(
                    ScalarTypes.DynamicArrayOfBool,
                    new Parameter("series1", ParameterTypeKind.Number),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesGreaterEquals =
        new FunctionSymbol("series_greater_equals",
                new Signature(
                    ScalarTypes.DynamicArrayOfBool,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)),
                new Signature(
                    ScalarTypes.DynamicArrayOfBool,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.Number)),
                new Signature(
                    ScalarTypes.DynamicArrayOfBool,
                    new Parameter("series1", ParameterTypeKind.Number),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesLess =
        new FunctionSymbol("series_less",
                new Signature(
                    ScalarTypes.DynamicArrayOfBool,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)),
                new Signature(
                    ScalarTypes.DynamicArrayOfBool,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.Number)),
                new Signature(
                    ScalarTypes.DynamicArrayOfBool,
                    new Parameter("series1", ParameterTypeKind.Number),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesLessEquals =
        new FunctionSymbol("series_less_equals",
                new Signature(
                    ScalarTypes.DynamicArrayOfBool,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)),
                new Signature(
                    ScalarTypes.DynamicArrayOfBool,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.Number)),
                new Signature(
                    ScalarTypes.DynamicArrayOfBool,
                    new Parameter("series1", ParameterTypeKind.Number),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesEquals =
        new FunctionSymbol("series_equals",
                new Signature(
                    ScalarTypes.DynamicArrayOfBool,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)),
                new Signature(
                    ScalarTypes.DynamicArrayOfBool,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.Number)),
                new Signature(
                    ScalarTypes.DynamicArrayOfBool,
                    new Parameter("series1", ParameterTypeKind.Number),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesNotEquals =
        new FunctionSymbol("series_not_equals",
                new Signature(
                    ScalarTypes.DynamicArrayOfBool,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)),
                new Signature(
                    ScalarTypes.DynamicArrayOfBool,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.Number)),
                new Signature(
                    ScalarTypes.DynamicArrayOfBool,
                    new Parameter("series1", ParameterTypeKind.Number),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesSeasonal =
        new FunctionSymbol("series_seasonal",
                new Signature(
                    ScalarTypes.DynamicArrayOfReal,
                    new Parameter("series", ParameterTypeKind.DynamicArray)),
                new Signature(
                    ScalarTypes.DynamicArrayOfReal,
                    new Parameter("series", ParameterTypeKind.DynamicArray),
                    new Parameter("period", ParameterTypeKind.Integer)),
                new Signature(
                    ScalarTypes.DynamicArrayOfReal,
                    new Parameter("series", ParameterTypeKind.DynamicArray),
                    new Parameter("period", ParameterTypeKind.Integer),
                    new Parameter("test_points", ParameterTypeKind.Integer)))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesExp =
        new FunctionSymbol("series_exp",
                ScalarTypes.DynamicArrayOfReal,
                new Parameter("series", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesSign =
        new FunctionSymbol("series_sign",
                ScalarTypes.DynamicArrayOfLong,
                new Parameter("series", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesAbs =
        new FunctionSymbol("series_abs",
                ReturnTypeKind.Parameter0,
                new Parameter("series", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesSin =
        new FunctionSymbol("series_sin",
                ScalarTypes.DynamicArrayOfReal,
                new Parameter("series", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesAsin =
        new FunctionSymbol("series_asin",
                ScalarTypes.DynamicArrayOfReal,
                new Parameter("series", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesCos =
        new FunctionSymbol("series_cos",
                ScalarTypes.DynamicArrayOfReal,
                new Parameter("series", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesAcos =
        new FunctionSymbol("series_acos",
                ScalarTypes.DynamicArrayOfReal,
                new Parameter("series", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesTan =
        new FunctionSymbol("series_tan",
                ScalarTypes.DynamicArrayOfReal,
                new Parameter("series", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesAtan =
        new FunctionSymbol("series_atan",
                ScalarTypes.DynamicArrayOfReal,
                new Parameter("series", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesLog =
        new FunctionSymbol("series_log",
                ScalarTypes.DynamicArrayOfReal,
                new Parameter("series", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesFloor =
        new FunctionSymbol("series_floor",
                ScalarTypes.DynamicArray,
                new Parameter("series", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesCeiling =
        new FunctionSymbol("series_ceiling",
                ReturnTypeKind.Parameter0,
                new Parameter("series", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None);

    private static TypeSymbol SeriesDecomposeResult(CustomReturnTypeContext context)
    {
        var source = context.getArgument("series");
        return FunctionHelpers.makePrefixedTuple(context, "series",
            new TupleSymbol(
                new ColumnSymbol("baseline", ScalarTypes.DynamicArray, null, null, source),
                new ColumnSymbol("seasonal", ScalarTypes.DynamicArray, null, null, source),
                new ColumnSymbol("trend", ScalarTypes.DynamicArray, null, null, source),
                new ColumnSymbol("residual", ScalarTypes.DynamicArray, null, null, source)));
    }

    private static TypeSymbol SeriesDecomposeAnomaliesResult(CustomReturnTypeContext context)
    {
        var source = context.getArgument("series");
        return FunctionHelpers.makePrefixedTuple(context, "series",
            new TupleSymbol(
                new ColumnSymbol("ad_flag", ScalarTypes.DynamicArray, null, null, source),
                new ColumnSymbol("ad_score", ScalarTypes.DynamicArray, null, null, source),
                new ColumnSymbol("baseline", ScalarTypes.DynamicArray, null, null, source)));
    }

    public static final FunctionSymbol SeriesDecompose =
        new FunctionSymbol("series_decompose",
            Functions::SeriesDecomposeResult, // PORT: §3.8 method group
            Tabularity.Scalar,
            new Parameter("series", ParameterTypeKind.DynamicArray),
            new Parameter("period", ParameterTypeKind.Integer, P.minOccurring(0)),
            new Parameter("trend", ScalarTypes.String, P.minOccurring(0)),
            new Parameter("test_points", ParameterTypeKind.Integer, P.minOccurring(0)),
            new Parameter("seasonality_threshold", ParameterTypeKind.Number, P.minOccurring(0)));

    public static final FunctionSymbol SeriesDecomposeForecast =
        new FunctionSymbol("series_decompose_forecast",
            Functions::SeriesDecomposeResult, // PORT: §3.8 method group
            Tabularity.Scalar,
            new Parameter("series", ParameterTypeKind.DynamicArray),
            new Parameter("test_points", ParameterTypeKind.Integer),
            new Parameter("period", ParameterTypeKind.Integer, P.minOccurring(0)),
            new Parameter("trend", ScalarTypes.String, P.minOccurring(0)),
            new Parameter("seasonality_threshold", ParameterTypeKind.Number, P.minOccurring(0)));

    public static final FunctionSymbol SeriesDecomposeAnomalies =
        new FunctionSymbol("series_decompose_anomalies",
            Functions::SeriesDecomposeAnomaliesResult, // PORT: §3.8 method group
            Tabularity.Scalar,
            new Parameter("series", ParameterTypeKind.DynamicArray),
            new Parameter("threshold", ParameterTypeKind.Number, P.minOccurring(0)),
            new Parameter("period", ParameterTypeKind.Integer, P.minOccurring(0)),
            new Parameter("trend", ScalarTypes.String, P.minOccurring(0)),
            new Parameter("test_points", ParameterTypeKind.Integer, P.minOccurring(0)),
            new Parameter("method", ScalarTypes.String, P.minOccurring(0)),
            new Parameter("seasonality_threshold", ParameterTypeKind.Number, P.minOccurring(0)));

    public static final FunctionSymbol SeriesPearsonCorrelation =
        new FunctionSymbol("series_pearson_correlation",
                ScalarTypes.Real,
                new Parameter("series1", ParameterTypeKind.DynamicArray),
                new Parameter("series2", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesDotProduct =
        new FunctionSymbol("series_dot_product",
                new Signature(
                    ScalarTypes.Real,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)),
                new Signature(
                    ScalarTypes.Real,
                    new Parameter("series1", ParameterTypeKind.DynamicArray),
                    new Parameter("series2", ParameterTypeKind.Number)),
                new Signature(
                    ScalarTypes.Real,
                    new Parameter("series1", ParameterTypeKind.Number),
                    new Parameter("series2", ParameterTypeKind.DynamicArray)))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesMagnitude =
        new FunctionSymbol("series_magnitude",
                ScalarTypes.Real,
                new Parameter("series", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesSum =
        new FunctionSymbol("series_sum",
                ScalarTypes.Real,
                new Parameter("series", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesProduct =
        new FunctionSymbol("series_product",
                ScalarTypes.Real,
                new Parameter("series", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol SeriesCosineSimilarity =
        new FunctionSymbol("series_cosine_similarity",
                ScalarTypes.Real,
                new Parameter("series1", ParameterTypeKind.DynamicArray),
                new Parameter("series2", ParameterTypeKind.DynamicArray),
                new Parameter("series1_magnitude", ScalarTypes.Real, P.minOccurring(0)),
                new Parameter("series2_magnitude", ScalarTypes.Real, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None);
    // endregion

    // region math functions
    public static final FunctionSymbol Round =
        new FunctionSymbol("round", ReturnTypeKind.Parameter0,
                new Parameter("number", ParameterTypeKind.Number),
                new Parameter("precision", ScalarTypes.Long, ArgumentKind.Constant, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Ceiling =
        new FunctionSymbol("ceiling", ReturnTypeKind.Parameter0,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Pow =
        new FunctionSymbol("pow", ScalarTypes.Real,
                new Parameter("base", ParameterTypeKind.Number),
                new Parameter("exponent", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Sqrt =
        new FunctionSymbol("sqrt", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Log =
        new FunctionSymbol("log", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Log2 =
        new FunctionSymbol("log2", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Log10 =
        new FunctionSymbol("log10", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Exp =
        new FunctionSymbol("exp", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Exp2 =
        new FunctionSymbol("exp2", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Exp10 =
        new FunctionSymbol("exp10", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol PI =
        new FunctionSymbol("pi", ScalarTypes.Real)
            .constantFoldable();

    public static final FunctionSymbol Cos =
        new FunctionSymbol("cos", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Sin =
        new FunctionSymbol("sin", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Tan =
        new FunctionSymbol("tan", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Acos =
        new FunctionSymbol("acos", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Asin =
        new FunctionSymbol("asin", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Atan =
        new FunctionSymbol("atan", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Atan2 =
        new FunctionSymbol("atan2", ScalarTypes.Real,
                new Parameter("y", ParameterTypeKind.Number),
                new Parameter("x", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Abs =
        new FunctionSymbol("abs",
                new Signature(ScalarTypes.Long,
                    new Parameter("number", ParameterTypeKind.Integer)),
                new Signature(ReturnTypeKind.Parameter0,
                    new Parameter("number", ParameterTypeKind.Number)),
                new Signature(ScalarTypes.TimeSpan,
                    new Parameter("number", ScalarTypes.TimeSpan)))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Cot =
        new FunctionSymbol("cot", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Degrees =
        new FunctionSymbol("degrees", ScalarTypes.Real,
                new Parameter("radians", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Radians =
        new FunctionSymbol("radians", ScalarTypes.Real,
                new Parameter("degrees", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Sign =
        new FunctionSymbol("sign", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Rand =
        new FunctionSymbol("rand", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Integer, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol BetaCdf =
        new FunctionSymbol("beta_cdf", ScalarTypes.Real,
            new Parameter("x", ParameterTypeKind.Number),
            new Parameter("alpha", ParameterTypeKind.Number),
            new Parameter("beta", ParameterTypeKind.Number)).constantFoldable();

    public static final FunctionSymbol BetaInv =
        new FunctionSymbol("beta_inv", ScalarTypes.Real,
            new Parameter("probability", ParameterTypeKind.Number),
            new Parameter("alpha", ParameterTypeKind.Number),
            new Parameter("beta", ParameterTypeKind.Number)).constantFoldable();

    public static final FunctionSymbol BetaPdf =
        new FunctionSymbol("beta_pdf", ScalarTypes.Real,
            new Parameter("x", ParameterTypeKind.Number),
            new Parameter("alpha", ParameterTypeKind.Number),
            new Parameter("beta", ParameterTypeKind.Number)).constantFoldable();

    public static final FunctionSymbol Gamma =
        new FunctionSymbol("gamma", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol LogGamma =
        new FunctionSymbol("loggamma", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Erf =
        new FunctionSymbol("erf", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol Erfc =
        new FunctionSymbol("erfc", ScalarTypes.Real,
                new Parameter("number", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.FirstArgument)
            .constantFoldable();

    public static final FunctionSymbol IsNan =
        new FunctionSymbol("isnan", ScalarTypes.Bool,
                new Parameter("number", ScalarTypes.Real))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol IsInf =
        new FunctionSymbol("isinf", ScalarTypes.Bool,
                new Parameter("number", ScalarTypes.Real))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol IsFinite =
        new FunctionSymbol("isfinite", ScalarTypes.Bool,
                new Parameter("number", ScalarTypes.Real))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol Coalesce =
        new FunctionSymbol("coalesce", ReturnTypeKind.Common,
                new Parameter("arg", ParameterTypeKind.CommonScalar, P.minOccurring(2), P.maxOccurring(64)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol MaxOf =
        new FunctionSymbol("max_of", ReturnTypeKind.Common,
                new Parameter("arg", ParameterTypeKind.CommonOrderable, P.minOccurring(2), P.maxOccurring(64)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol MinOf =
        new FunctionSymbol("min_of", ReturnTypeKind.Common,
                new Parameter("arg", ParameterTypeKind.CommonOrderable, P.minOccurring(2), P.maxOccurring(64)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol WelchTest =
        new FunctionSymbol("welch_test", ScalarTypes.Real,
                new Parameter("mean1", ParameterTypeKind.Number),
                new Parameter("variance1", ParameterTypeKind.Number),
                new Parameter("count1", ParameterTypeKind.Number),
                new Parameter("mean2", ParameterTypeKind.Number),
                new Parameter("variance2", ParameterTypeKind.Number),
                new Parameter("count2", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.None);
    // endregion

    // region geospatial functions
    public static final FunctionSymbol GeoFromWkt =
        new FunctionSymbol("geo_from_wkt", ScalarTypes.GeoShape,
                new Parameter("wkt", ParameterTypeKind.StringOrArray))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoAngle =
        new FunctionSymbol("geo_angle",
                ScalarTypes.Real,
                new Parameter("p1_longitude", ParameterTypeKind.Number),
                new Parameter("p1_latitude", ParameterTypeKind.Number),
                new Parameter("p2_longitude", ParameterTypeKind.Number),
                new Parameter("p2_latitude", ParameterTypeKind.Number),
                new Parameter("p3_longitude", ParameterTypeKind.Number),
                new Parameter("p3_latitude", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoAzimuth =
        new FunctionSymbol("geo_azimuth",
                ScalarTypes.Real,
                new Parameter("p1_longitude", ParameterTypeKind.Number),
                new Parameter("p1_latitude", ParameterTypeKind.Number),
                new Parameter("p2_longitude", ParameterTypeKind.Number),
                new Parameter("p2_latitude", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoDistance2Points =
        new FunctionSymbol("geo_distance_2points",
                ScalarTypes.Real,
                new Parameter("p1_longitude", ParameterTypeKind.Number),
                new Parameter("p1_latitude", ParameterTypeKind.Number),
                new Parameter("p2_longitude", ParameterTypeKind.Number),
                new Parameter("p2_latitude", ParameterTypeKind.Number),
                new Parameter("use_spheroid", ParameterTypeKind.NumberOrBool, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoDistancePointToLine =
        new FunctionSymbol("geo_distance_point_to_line",
                ScalarTypes.Real,
                new Parameter("longitude", ParameterTypeKind.Number),
                new Parameter("latitude", ParameterTypeKind.Number),
                new Parameter("lineString", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoDistancePointToPolygon =
        new FunctionSymbol("geo_distance_point_to_polygon",
                ScalarTypes.Real,
                new Parameter("longitude", ParameterTypeKind.Number),
                new Parameter("latitude", ParameterTypeKind.Number),
                new Parameter("polygon", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoPointInCircle =
        new FunctionSymbol("geo_point_in_circle",
                ScalarTypes.Bool,
                new Parameter("p_longitude", ParameterTypeKind.Number),
                new Parameter("p_latitude", ParameterTypeKind.Number),
                new Parameter("pc_longitude", ParameterTypeKind.Number),
                new Parameter("pc_latitude", ParameterTypeKind.Number),
                new Parameter("c_radius", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoPointInPolygon =
        new FunctionSymbol("geo_point_in_polygon",
                ScalarTypes.Bool,
                new Parameter("longitude", ParameterTypeKind.Number),
                new Parameter("latitude", ParameterTypeKind.Number),
                new Parameter("polygon", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoPointBuffer =
        new FunctionSymbol("geo_point_buffer",
                ScalarTypes.GeoShape,
                new Parameter("longitude", ParameterTypeKind.Number),
                new Parameter("latitude", ParameterTypeKind.Number),
                new Parameter("radius", ParameterTypeKind.Number),
                new Parameter("tolerance", ParameterTypeKind.Number, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoLineBuffer =
        new FunctionSymbol("geo_line_buffer",
                ScalarTypes.GeoShape,
                new Parameter("lineString", ParameterTypeKind.DynamicBag),
                new Parameter("radius", ParameterTypeKind.Number),
                new Parameter("tolerance", ParameterTypeKind.Number, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoPolygonBuffer =
        new FunctionSymbol("geo_polygon_buffer",
                ScalarTypes.GeoShape,
                new Parameter("polygon", ParameterTypeKind.DynamicBag),
                new Parameter("radius", ParameterTypeKind.Number),
                new Parameter("tolerance", ParameterTypeKind.Number, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoIntersects2Lines =
        new FunctionSymbol("geo_intersects_2lines",
                ScalarTypes.Bool,
                new Parameter("lineString1", ParameterTypeKind.DynamicBag),
                new Parameter("lineString2", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoIntersection2Lines =
        new FunctionSymbol("geo_intersection_2lines",
                ScalarTypes.GeoShape,
                new Parameter("lineString1", ParameterTypeKind.DynamicBag),
                new Parameter("lineString2", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoIntersectsLineWithPolygon =
        new FunctionSymbol("geo_intersects_line_with_polygon",
                ScalarTypes.Bool,
                new Parameter("lineString", ParameterTypeKind.DynamicBag),
                new Parameter("polygon", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoIntersectionLineWithPolygon =
        new FunctionSymbol("geo_intersection_line_with_polygon",
                ScalarTypes.GeoShape,
                new Parameter("lineString", ParameterTypeKind.DynamicBag),
                new Parameter("polygon", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoIntersects2Polygons =
        new FunctionSymbol("geo_intersects_2polygons",
                ScalarTypes.Bool,
                new Parameter("polygon1", ParameterTypeKind.DynamicBag),
                new Parameter("polygon2", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoIntersection2Polygons =
        new FunctionSymbol("geo_intersection_2polygons",
                ScalarTypes.GeoShape,
                new Parameter("polygon1", ParameterTypeKind.DynamicBag),
                new Parameter("polygon2", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoPolygonsUnion =
        new FunctionSymbol("geo_union_polygons_array",
                ScalarTypes.GeoShape,
                new Parameter("polygons", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoLinesUnion =
        new FunctionSymbol("geo_union_lines_array",
                ScalarTypes.GeoShape,
                new Parameter("lineStrings", ParameterTypeKind.DynamicArray))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoPolygonToS2Cells =
        new FunctionSymbol("geo_polygon_to_s2cells",
                ScalarTypes.DynamicArrayOfString,
                new Parameter("polygon", ParameterTypeKind.DynamicBag),
                new Parameter("level", ParameterTypeKind.Number, P.minOccurring(0)),
                new Parameter("radius", ParameterTypeKind.Number, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoLineToS2Cells =
        new FunctionSymbol("geo_line_to_s2cells",
                ScalarTypes.DynamicArrayOfString,
                new Parameter("lineString", ParameterTypeKind.DynamicBag),
                new Parameter("level", ParameterTypeKind.Number, P.minOccurring(0)),
                new Parameter("radius", ParameterTypeKind.Number, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoPolygonS2CellCoveringLevel =
        new FunctionSymbol("__geo_polygon_s2cell_covering_level",
                ScalarTypes.Int,
                new Parameter("polygon", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable()
            .hide();

    public static final FunctionSymbol GeoLineS2CellCoveringLevel =
        new FunctionSymbol("__geo_line_s2cell_covering_level",
                ScalarTypes.Int,
                new Parameter("lineString", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable()
            .hide();

    public static final FunctionSymbol GeoLengthToS2CellLevel =
        new FunctionSymbol("__geo_length_to_s2cell_level",
                ScalarTypes.Int,
                new Parameter("length", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable()
            .hide();

    public static final FunctionSymbol GeoPolygonDensify =
        new FunctionSymbol("geo_polygon_densify",
                ScalarTypes.GeoShape,
                new Parameter("polygon", ParameterTypeKind.DynamicBag),
                new Parameter("tolerance", ParameterTypeKind.Number, P.minOccurring(0)),
                new Parameter("preserve_crossing", ParameterTypeKind.NumberOrBool, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoPolygonArea =
        new FunctionSymbol("geo_polygon_area",
                ScalarTypes.Real,
                new Parameter("polygon", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoPolygonCentroid =
        new FunctionSymbol("geo_polygon_centroid",
                ScalarTypes.GeoShape,
                new Parameter("polygon", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoPolygonPerimeter =
        new FunctionSymbol("geo_polygon_perimeter",
                ScalarTypes.Real,
                new Parameter("polygon", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoLineLength =
        new FunctionSymbol("geo_line_length",
                ScalarTypes.Real,
                new Parameter("lineString", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoLineCentroid =
        new FunctionSymbol("geo_line_centroid",
                ScalarTypes.GeoShape,
                new Parameter("lineString", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoLineDensify =
        new FunctionSymbol("geo_line_densify",
                ScalarTypes.GeoShape,
                new Parameter("lineString", ParameterTypeKind.DynamicBag),
                new Parameter("tolerance", ParameterTypeKind.Number, P.minOccurring(0)),
                new Parameter("preserve_crossing", ParameterTypeKind.NumberOrBool, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoLineSimplify =
        new FunctionSymbol("geo_line_simplify",
                ScalarTypes.GeoShape,
                new Parameter("lineString", ParameterTypeKind.DynamicBag),
                new Parameter("tolerance", ParameterTypeKind.Number, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoLineLocatePoint =
        new FunctionSymbol("geo_line_locate_point",
                ScalarTypes.Real,
                new Parameter("lineString", ParameterTypeKind.DynamicBag),
                new Parameter("longitude", ParameterTypeKind.Number),
                new Parameter("latitude", ParameterTypeKind.Number),
                new Parameter("use_spheroid", ParameterTypeKind.NumberOrBool, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoLineInterpolatePoint =
        new FunctionSymbol("geo_line_interpolate_point",
                ScalarTypes.GeoShape,
                new Parameter("lineString", ParameterTypeKind.DynamicBag),
                new Parameter("fraction", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoClosestPointOnLine =
        new FunctionSymbol("geo_closest_point_on_line",
                ScalarTypes.GeoShape,
                new Parameter("longitude", ParameterTypeKind.Number),
                new Parameter("latitude", ParameterTypeKind.Number),
                new Parameter("lineString", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoClosestPointOnPolygon =
        new FunctionSymbol("geo_closest_point_on_polygon",
                ScalarTypes.GeoShape,
                new Parameter("longitude", ParameterTypeKind.Number),
                new Parameter("latitude", ParameterTypeKind.Number),
                new Parameter("polygon", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoPolygonSimplify =
        new FunctionSymbol("geo_polygon_simplify",
                ScalarTypes.GeoShape,
                new Parameter("polygon", ParameterTypeKind.DynamicBag),
                new Parameter("tolerance", ParameterTypeKind.Number, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoSimplifyPolygonsArray =
        new FunctionSymbol("geo_simplify_polygons_array",
                ScalarTypes.GeoShape,
                new Parameter("polygons", ParameterTypeKind.DynamicArray),
                new Parameter("tolerance", ParameterTypeKind.Number, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoLineValidate =
        new FunctionSymbol("__geo_line_validate", ScalarTypes.String,
                new Parameter("lineString", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable()
            .hide();

    public static final FunctionSymbol GeoPolygonValidate =
        new FunctionSymbol("__geo_polygon_validate", ScalarTypes.String,
                new Parameter("polygon", ParameterTypeKind.DynamicBag))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable()
            .hide();

    public static final FunctionSymbol GeoPointToGeohash =
        new FunctionSymbol("geo_point_to_geohash", ScalarTypes.String,
                new Parameter("longitude", ParameterTypeKind.Number),
                new Parameter("latitude", ParameterTypeKind.Number),
                new Parameter("accuracy", ParameterTypeKind.Number, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeohashToCentralPoint =
        new FunctionSymbol("geo_geohash_to_central_point",
                ScalarTypes.GeoShape,
                new Parameter("geohash", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeohashToPolygon =
        new FunctionSymbol("geo_geohash_to_polygon",
                ScalarTypes.GeoShape,
                new Parameter("geohash", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeohashNeighbors =
        new FunctionSymbol("geo_geohash_neighbors",
                ScalarTypes.DynamicArrayOfString,
                new Parameter("geohash", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoPointToS2Cell =
        new FunctionSymbol("geo_point_to_s2cell", ScalarTypes.String,
                new Parameter("longitude", ParameterTypeKind.Number),
                new Parameter("latitude", ParameterTypeKind.Number),
                new Parameter("level", ParameterTypeKind.Number, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoS2CellToCentralPoint =
        new FunctionSymbol("geo_s2cell_to_central_point",
                ScalarTypes.GeoShape,
                new Parameter("s2cell", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoS2CellNeighbors =
        new FunctionSymbol("geo_s2cell_neighbors",
                ScalarTypes.DynamicArrayOfString,
                new Parameter("s2cell", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoS2CellToPolygon =
        new FunctionSymbol("geo_s2cell_to_polygon",
                ScalarTypes.GeoShape,
                new Parameter("s2cell", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoPointToH3Cell =
        new FunctionSymbol("geo_point_to_h3cell", ScalarTypes.String,
                new Parameter("longitude", ParameterTypeKind.Number),
                new Parameter("latitude", ParameterTypeKind.Number),
                new Parameter("resolution", ParameterTypeKind.Number, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoH3CellToCentralPoint =
        new FunctionSymbol("geo_h3cell_to_central_point",
                ScalarTypes.GeoShape,
                new Parameter("h3cell", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoH3CellToPolygon =
        new FunctionSymbol("geo_h3cell_to_polygon",
                ScalarTypes.GeoShape,
                new Parameter("h3cell", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoH3CellNeighbors =
        new FunctionSymbol("geo_h3cell_neighbors",
                ScalarTypes.DynamicArrayOfString,
                new Parameter("h3cell", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoH3CellChildren =
        new FunctionSymbol("geo_h3cell_children",
                ScalarTypes.DynamicArrayOfString,
                new Parameter("h3cell", ScalarTypes.String),
                new Parameter("resolution", ParameterTypeKind.Number, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoH3CellParent =
        new FunctionSymbol("geo_h3cell_parent", ScalarTypes.String,
                new Parameter("h3cell", ScalarTypes.String),
                new Parameter("resolution", ParameterTypeKind.Number, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoH3CellRings =
        new FunctionSymbol("geo_h3cell_rings",
                ScalarTypes.DynamicArrayOfArrayOfString,
                new Parameter("h3cell", ScalarTypes.String),
                new Parameter("distance", ParameterTypeKind.Number))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoH3CellLevel =
        new FunctionSymbol("geo_h3cell_level", ScalarTypes.Int,
                new Parameter("h3cell", ScalarTypes.String))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();

    public static final FunctionSymbol GeoPolygonToH3Cells =
        new FunctionSymbol("geo_polygon_to_h3cells",
                ScalarTypes.DynamicArrayOfString,
                new Parameter("polygon", ParameterTypeKind.DynamicBag),
                new Parameter("resolution", ParameterTypeKind.Number, P.minOccurring(0)),
                new Parameter("radius", ParameterTypeKind.Number, P.minOccurring(0)))
            .withResultNameKind(ResultNameKind.None)
            .constantFoldable();
    // endregion

    // region Graph functions
    public static final FunctionSymbol _All =
        new FunctionSymbol("all",
            ScalarTypes.Bool,
            new Parameter("pattern_element", ParameterTypeKind.Scalar),
            new Parameter("expression", ScalarTypes.Bool, ArgumentKind.Expression_Parameter0_Element))
            .withCustomAvailability(Functions::InGraphWhereOrProjectClause); // PORT: §3.8 method group

    public static final FunctionSymbol Any =
        new FunctionSymbol("any",
                ScalarTypes.Bool,
                new Parameter("pattern_element", ParameterTypeKind.Scalar),
                new Parameter("expression", ScalarTypes.Bool, ArgumentKind.Expression_Parameter0_Element))
            .withCustomAvailability(Functions::InGraphWhereOrProjectClause); // PORT: §3.8 method group

    public static final FunctionSymbol Map =
        new FunctionSymbol("map",
            context ->
            {
                if (context.arguments().size() > 1)
                {
                    var expr = context.arguments().get(1);
                    return ScalarTypes.getDynamicArray(expr.resultType());
                }

                return ScalarTypes.DynamicArray;
            },
            Tabularity.Scalar,
            new Parameter("pattern_element", ParameterTypeKind.Scalar),
            new Parameter("expression", ParameterTypeKind.Scalar, ArgumentKind.Expression_Parameter0_Element))
            .withCustomAvailability(Functions::InGraphWhereOrProjectClause); // PORT: §3.8 method group

    public static final FunctionSymbol InnerNodes =
        new FunctionSymbol("inner_nodes",
                context ->
                {
                    if (context.arguments().size() > 0
                        && context.arguments().get(0) instanceof Expression arg)
                    {
                        if (arg.getFirstAncestor(GraphMatchOperator.class) instanceof GraphMatchOperator graphMatch
                                && graphMatch.parent() instanceof PipeExpression pGM
                                && pGM.expression().resultType() instanceof GraphSymbol gsGM)
                        {
                            return new TupleSymbol(gsGM.nodeShape() != null ? gsGM.nodeShape().columns() : new ArrayList<ColumnSymbol>()); // PORT: §3.14 ?. ??
                        }

                        if (arg.getFirstAncestor(GraphShortestPathsOperator.class) instanceof GraphShortestPathsOperator graphShortestPaths
                                && graphShortestPaths.parent() instanceof PipeExpression pSP
                                && pSP.expression().resultType() instanceof GraphSymbol gsSP)
                        {
                            return new TupleSymbol(gsSP.nodeShape() != null ? gsSP.nodeShape().columns() : new ArrayList<ColumnSymbol>()); // PORT: §3.14 ?. ??
                        }
                    }

                    // could not find node schema
                    return ScalarTypes.DynamicArrayOfBag;
                },
                Tabularity.Scalar,
                new Parameter("edge", ParameterTypeKind.Scalar))
            .withCustomAvailability(context ->
            {
                var inGM = context.location().getFirstAncestor(GraphMatchOperator.class) != null;
                var inSP = context.location().getFirstAncestor(GraphShortestPathsOperator.class) != null;

                // can only occur in first argument of all/any/map functions
                var inGraphLambda =
                    context.location().getFirstAncestor(FunctionCallExpression.class, fce ->
                        !Objects.equals(fce.name().simpleName(), Functions.InnerNodes.name())) instanceof FunctionCallExpression fc
                    && (Objects.equals(fc.name().simpleName(), Functions._All.name())
                        || Objects.equals(fc.name().simpleName(), Functions.Any.name())
                        || Objects.equals(fc.name().simpleName(), Functions.Map.name()))
                    && (context.location() == fc.argumentList()
                        || (fc.argumentList().expressions().size() > 0
                            && fc.argumentList().expressions().get(0).element().isAncestorOf(context.location())));
                return (inSP || inGM) && inGraphLambda;
            });

    public static final FunctionSymbol NodeDegreeIn =
        new FunctionSymbol("node_degree_in",
            ScalarTypes.Long,
            new Parameter("node", ParameterTypeKind.Scalar, P.minOccurring(0)))
            .withCustomAvailability(Functions::InGraphWhereOrProjectClause); // PORT: §3.8 method group

    public static final FunctionSymbol NodeDegreeOut =
        new FunctionSymbol("node_degree_out",
            ScalarTypes.Long,
            new Parameter("node", ParameterTypeKind.Scalar, P.minOccurring(0)))
            .withCustomAvailability(Functions::InGraphWhereOrProjectClause); // PORT: §3.8 method group

    public static final FunctionSymbol NodeId =
        new FunctionSymbol("node_id",
                ScalarTypes.String,
                new Parameter("node", ParameterTypeKind.Scalar, P.minOccurring(0)))
            .withCustomAvailability(Functions::InGraphWhereOrProjectClause); // PORT: §3.8 method group

    public static final FunctionSymbol Labels =
        new FunctionSymbol("labels",
                ScalarTypes.DynamicArrayOfString,
                new Parameter("pattern_element", ParameterTypeKind.Scalar, P.minOccurring(0)))
            .withCustomAvailability(Functions::InGraphWhereOrProjectClause); // PORT: §3.8 method group

    private static boolean InGraphWhereOrProjectClause(CustomAvailabilityContext context)
    {
        // PORT: §3.12 local function used before declaration -> lambda declared before use
        Predicate<SyntaxNode> InClause = clause ->
            clause != null &&
            (clause == context.location() || clause.isAncestorOf(context.location()));

        var gmMatch = (context.location().getFirstAncestor(GraphMatchOperator.class) instanceof GraphMatchOperator gm &&
               (InClause.test(gm.whereClause()) || InClause.test(gm.projectClause())));
        
        var spMatch = (context.location().getFirstAncestor(GraphShortestPathsOperator.class) instanceof GraphShortestPathsOperator sp &&
               (InClause.test(sp.whereClause()) || InClause.test(sp.projectClause())));

        return gmMatch || spMatch;
    }

    // endregion

    // region other
    public static final FunctionSymbol CurrentClusterEndpoint =
        new FunctionSymbol("current_cluster_endpoint", ScalarTypes.String);

    public static final FunctionSymbol CurrentDatabase =
        new FunctionSymbol("current_database", ScalarTypes.String);

    public static final FunctionSymbol CurrentPrincipal =
        new FunctionSymbol("current_principal", ScalarTypes.String);
    // result column name is dependent on some guid?

    private static final TypeSymbol CurrentPrincipalDetailsResult =
        ScalarTypes.getDynamicBag(
            new ColumnSymbol("UserPrincipalName", ScalarTypes.String),
            new ColumnSymbol("IdentityProvider", ScalarTypes.String),
            new ColumnSymbol("Authority", ScalarTypes.String),
            new ColumnSymbol("Mfa", ScalarTypes.String),
            new ColumnSymbol("Type", ScalarTypes.String),
            new ColumnSymbol("DisplayName", ScalarTypes.String),
            new ColumnSymbol("ObjectId", ScalarTypes.String),
            new ColumnSymbol("FQN", ScalarTypes.String),
            new ColumnSymbol("Notes", ScalarTypes.String));

    public static final FunctionSymbol CurrentPrincipalDetails =
        new FunctionSymbol("current_principal_details",
            CurrentPrincipalDetailsResult)
        .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol CurrentPrincipalIsMemberOf =
      new FunctionSymbol("current_principal_is_member_of",
          ScalarTypes.Bool,
          new Parameter("group", ParameterTypeKind.StringOrArray, P.minOccurring(1), P.maxOccurring(64)))
        .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol ExtentId =
        new FunctionSymbol("extent_id", ScalarTypes.Guid)
        .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol ExtentId2 =
        new FunctionSymbol("extentid", ScalarTypes.Guid)
        .obsolete("extent_id");

    public static final FunctionSymbol ExtentTags =
        new FunctionSymbol("extent_tags",
            ScalarTypes.DynamicArrayOfString)
        .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol CurrentNodeId =
        new FunctionSymbol("current_node_id", ScalarTypes.String)
        .withResultNameKind(ResultNameKind.None)
        .hide();

    public static final FunctionSymbol IngestionTime =
        new FunctionSymbol("ingestion_time", ScalarTypes.DateTime)
        .withResultNameKind(ResultNameKind.PrefixOnly)
        .withResultNamePrefix("$IngestionTime");

    public static final FunctionSymbol RowId =
        new FunctionSymbol("row_id", ScalarTypes.String)
        .withResultNameKind(ResultNameKind.PrefixOnly)
        .withResultNamePrefix("$RowId");

    public static final FunctionSymbol CursorAfter =
        new FunctionSymbol("cursor_after", ScalarTypes.Bool,
            new Parameter("cursor", ScalarTypes.String));

    public static final FunctionSymbol CursorBeforeOrAt =
        new FunctionSymbol("cursor_before_or_at", ScalarTypes.Bool,
            new Parameter("cursor", ScalarTypes.String));

    public static final FunctionSymbol CursorCurrent =
        new FunctionSymbol("cursor_current", ScalarTypes.String);

    public static final FunctionSymbol CursorCurrent2 =
        new FunctionSymbol("current_cursor", ScalarTypes.String)
        .obsolete("cursor_current");

    public static final FunctionSymbol FormatBytes =
        new FunctionSymbol("format_bytes", ScalarTypes.String,
            new Parameter("size", ParameterTypeKind.Number),
            new Parameter("precision", ParameterTypeKind.Number, ArgumentKind.Constant, P.minOccurring(0)),
            new Parameter("format", ScalarTypes.String, ArgumentKind.Constant, P.minOccurring(0)))
        .withResultNameKind(ResultNameKind.FirstArgument);

    public static final FunctionSymbol RowNumber =
        new FunctionSymbol("row_number", ScalarTypes.Long,
            new Parameter("startingIndex", ScalarTypes.Long, P.minOccurring(0)),
            new Parameter("restart", ScalarTypes.Bool, P.minOccurring(0)))
        .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol RowCumSum =
        new FunctionSymbol("row_cumsum", ReturnTypeKind.Parameter0,
            new Parameter("term", ParameterTypeKind.Number),
            new Parameter("restart", ScalarTypes.Bool, P.minOccurring(0)))
        .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol RowRank =
        new FunctionSymbol("row_rank", ScalarTypes.Long,
            new Parameter("column", ParameterTypeKind.NotDynamic),
            new Parameter("dense", ScalarTypes.Bool, ArgumentKind.Constant, P.minOccurring(0)))
        .withResultNameKind(ResultNameKind.None)
        .obsolete("row_rank_dense");

    public static final FunctionSymbol RowRankMin =
        new FunctionSymbol("row_rank_min", ScalarTypes.Long,
            new Parameter("term", ParameterTypeKind.NotDynamic),
            new Parameter("restart", ScalarTypes.Bool, P.minOccurring(0)))
        .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol RowRankDense =
        new FunctionSymbol("row_rank_dense", ScalarTypes.Long,
            new Parameter("term", ParameterTypeKind.NotDynamic),
            new Parameter("restart", ScalarTypes.Bool, P.minOccurring(0)))
        .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol RowWindowSession =
        new FunctionSymbol("row_window_session", ReturnTypeKind.Parameter0,
            new Parameter("expr", ScalarTypes.DateTime),
            new Parameter("maxDistanceFromFirst", ScalarTypes.TimeSpan),
            new Parameter("maxDistanceBetweenNeighbors", ScalarTypes.TimeSpan),
            new Parameter("restart", ScalarTypes.Bool, P.minOccurring(0)))
        .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol Prev =
        new FunctionSymbol("prev", ReturnTypeKind.Parameter0,
            new Parameter("column", ParameterTypeKind.Scalar, ArgumentKind.Column),
            new Parameter("offset", ScalarTypes.Long, P.minOccurring(0)),
            new Parameter("default_value", ParameterTypeKind.Scalar, ArgumentKind.Constant, P.minOccurring(0)))
        .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol Next =
        new FunctionSymbol("next", ReturnTypeKind.Parameter0,
            new Parameter("column", ParameterTypeKind.Scalar, ArgumentKind.Column),
            new Parameter("offset", ScalarTypes.Long, P.minOccurring(0)),
            new Parameter("default_value", ParameterTypeKind.Scalar, ArgumentKind.Constant, P.minOccurring(0)))
       .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol RowstoreOrdinalRange =
        new FunctionSymbol("rowstore_ordinal_range",
            ScalarTypes.Dynamic)
        .withResultNameKind(ResultNameKind.None)
        .hide();

    public static final FunctionSymbol EstimateDataSize =
        new FunctionSymbol("estimate_data_size",
            new Signature(ScalarTypes.Long,
                new Parameter("column", ParameterTypeKind.Scalar, ArgumentKind.Column, P.minOccurring(1), P.maxOccurring(FunctionHelpers.MaxRepeat))),
            new Signature(ScalarTypes.Long,
                new Parameter("column", ParameterTypeKind.Scalar, ArgumentKind.StarOnly)))
        .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol NewGuid = new FunctionSymbol("new_guid", ScalarTypes.Guid)
        .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol HasIpv4 =
        new FunctionSymbol("has_ipv4", ScalarTypes.Bool,
            new Parameter("source", ParameterTypeKind.StringOrDynamic),
            new Parameter("ip", ScalarTypes.String))
        .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol HasIpv4Prefix =
        new FunctionSymbol("has_ipv4_prefix", ScalarTypes.Bool,
            new Parameter("source", ParameterTypeKind.StringOrDynamic),
            new Parameter("ip_prefix", ScalarTypes.String))
        .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol HasAnyIpv4 =
        new FunctionSymbol("has_any_ipv4",
            new Signature(ScalarTypes.Bool,
                new Parameter("source", ParameterTypeKind.StringOrDynamic),
                new Parameter("ips", ScalarTypes.String, P.maxOccurring(FunctionHelpers.MaxRepeat))),
            new Signature(ScalarTypes.Bool,
                new Parameter("source", ParameterTypeKind.StringOrDynamic),
                new Parameter("ips", ParameterTypeKind.DynamicArray)))
        .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol HasAnyIpv4Prefix =
        new FunctionSymbol("has_any_ipv4_prefix",
            new Signature(ScalarTypes.Bool,
                new Parameter("source", ParameterTypeKind.StringOrDynamic),
                new Parameter("ip_prefixes", ScalarTypes.String, P.maxOccurring(FunctionHelpers.MaxRepeat))),
            new Signature(ScalarTypes.Bool,
                new Parameter("source", ParameterTypeKind.StringOrDynamic),
                new Parameter("ip_prefixes", ParameterTypeKind.DynamicArray)))
        .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol Invoke =
        new FunctionSymbol("__invoke",
            ReturnTypeKind.Parameter1Literal,
            new Parameter("name", ScalarTypes.String, ArgumentKind.Literal),
            new Parameter("type", ScalarTypes.Type, ArgumentKind.Literal),
            new Parameter("arg", ParameterTypeKind.Any, P.minOccurring(0), P.maxOccurring(FunctionHelpers.MaxRepeat)))
        .constantFoldable()
        .hide();

    public static final FunctionSymbol Cast =
        new FunctionSymbol("__cast",
            ReturnTypeKind.Parameter0Literal,
            new Parameter("type", ScalarTypes.Type, ArgumentKind.Literal),
            new Parameter("value", ParameterTypeKind.Scalar))
        .constantFoldable()
        .hide();

    public static final FunctionSymbol IpGeoLocation =
        new FunctionSymbol("geo_info_from_ip_address", ScalarTypes.Dynamic,
            new Parameter("ip", ScalarTypes.String))
        .constantFoldable()
        .withResultNameKind(ResultNameKind.None);

    public static final FunctionSymbol ColumnNamesOf =
        new FunctionSymbol("column_names_of", ScalarTypes.DynamicArrayOfString,
            new Parameter("table", ParameterTypeKind.Tabular))
        .constantFoldable()
        .withResultNameKind(ResultNameKind.None);
    // endregion

    // region All
    public static final List<FunctionSymbol> All = List.of( // PORT: §3.17 IReadOnlyList → List; List.of rejects null entries
// region cluster / database / table / etc
        Cluster,
        Database,
        Table,
        ExternalTable,
        MaterializedView,
        EntityGroup,
        StoredQueryResult,
        Graph,
// endregion

// region string functions
        Strcat,
        StrcatArray,
        ArrayStrcat,
        StrcatDelim,
        Strcmp,
        Strrep,
        Strlen,
        StringSize,
        ToUpper,
        ToLower,
        ToUtf8_Deprecated,
        UnicodeCodepointsFromString,
        Substring,
        RegexQuote,
        IndexOf,
        IndexOfRegex,
        HasAnyIndex,
        Reverse,
        Split,
        ParseCommandLine,
        Extract,
        ExtractAll_Deprecated,
        ExtractAll,
        ExtractJson_Deprecated,
        ExtractJson,
        Replace,
        ReplaceRegex,
        ReplaceString,
        ReplaceStrings,
        TrimStart,
        TrimEnd,
        Trim,
        CountOf,
        Translate,
        MakeString_Deprecated,
        UnicodeCodepointsToString,
        DatetimeLocalToUtc,
        DatetimeUtcToLocal,
        DateTimeListTimezones,
// endregion

// region type conversion functions
        ToString,
        ToHex,
        ToDynamic_,
        ToObject_Deprecated,
        ToLong,
        ToInt,
        ToReal,
        ToDouble,
        ToDateTime,
        ToTimespan,
        ToTime,
        ToBool,
        ToBoolean,
        ToDecimal,
        ToGuid,
        GetType,
// endregion

// region encoding/decoding functions
        UrlEncode,
        UrlEncode_Component,
        UrlDecode,
        Base64EncodeString,
        Base64EncodeToString,
        Base64DecodeToArray,
        Base64EncodeFromArray,
        Base64DecodeString,
        Base64DecodeToString,
        Base64DecodeToGuid,
        Base64EncodeFromGuid,
        ZlibDecompressString,
        ZlibCompressString,
        GzipDecompressString,
        GzipCompressString,
        Lz4CompressDynamicArray,
        // endregion

// region parsing functions
        ParseCsv,
        ParseJson_Deprecated,
        ParseJson,
        ParseXml,
        ParseUrl_Deprecated,
        ParseUrl,
        ParseUrlQuery_Deprecated,
        ParseUrlQuery,
        ParseIPV4,
        ParseIPV4Mask,
        ParseIPV6,
        ParseIPV6Mask,
        ParsePath,
        ParseUserAgent,
        ParseVersion,
        // endregion

// region date and time functions
        FormatDatetime,
        FormatTimespan,
        MakeDatetime,
        MakeTimespan,
        DatetimeAdd,
        DatetimeDiff,
        DayOfWeek,
        DayOfMonth,
        DayOfYear,
        HourOfDay,
        WeekOfYear,
        WeekOfYearISO,
        MonthOfYear,
        StartOfDay,
        StartOfWeek,
        StartOfMonth,
        StartOfYear,
        EndOfDay,
        EndOfWeek,
        EndOfMonth,
        EndOfYear,
        GetYear,
        GetMonth,
        DatePart,
        DatetimePart,
        Now,
        Ago,
        UnixTimeSecondsToDateTime,
        UnixTimeMillisecondsToDateTime,
        UnixTimeMicrosecondsToDateTime,
        UnixTimeNanosecondsToDateTime,
// endregion

// region hash functions
        HashCrc32,
        HashDjb2,
        Hash,
        HashSha256,
        HashMd5,
        HashSha1,
        HashXXH64,
        InternalHashXXH64,
        HashCombine,
        HashMany,
        HashManyCrc32,
        // endregion

// region iif / case
        Iif,
        Iff,
        Case,
        Assert,
// endregion

// region bin / floor
        Bin,
        Floor,
        BinAt,
        BinAuto,
// endregion

// region bool functions  (test state, return bool)
        Not,
        NotNull_Deprecated,
        IsNotNull,
        IsNull,
        NotEmpty_Deprecated,
        IsColumnExists,
        IsAscii,
        IsUtf8,
        IsNotEmpty,
        IsEmpty,
        ColumnIfExists_Deprecated,
        ColumnIfExists,
        Around,
// endregion

// region bitwise functions
        BinaryAnd,
        BinaryOr,
        BinaryXor,
        BinaryNot,
        BinaryShiftRight,
        BinaryShiftLeft,
        BitsetCountOnes,
// endregion

// region dynamic array/object functions
        TreePath,
        Repeat,
        Arraylength_Deprecated,
        ArrayLength,
        Range,
        ArrayConcat,
        ArrayIif,
        ArrayIff,
        ArrayIndexOf,
        ArraySlice,
        ArraySplit,
        ArrayShiftLeft,
        ArrayShiftRight,
        ArrayReverse,
        ArrayRotateLeft,
        ArrayRotateRight,
        ArraySortAsc,
        ArraySortDesc,
        BagKeys,
        Zip,
        Pack,
        PackDictionary,
        BagPack,
        BagPackColumns,
        PackAll,
        PackArray,
        SetHasElement,
        SetUnion,
        SetIntersect,
        SetDifference,
        SetEquals,
        BagMerge,
        DynamicToJson,
        BagRemoveKeys,
        BagHasKey,
        JaccardIndex,
        BagSetKey,
        BagZip,
        PunycodeDecode,
        PunycodeEncode,
        PunycodeDomainDecode,
        PunycodeDomainEncode,
// endregion

// region digest / series functions
        PercentileTDigest,
        PercentileArrayTDigest,
        PercentRankTDigest,
        RankTDigest,
        TdigestIsValid,
        HllIsValid,
        TDigestMerge,
        MergeTDigest,
        HllMerge,
        DCountHll,
        HllNormalize,
        SeriesFir,
        SeriesStats,
        SeriesStatsDynamic,
        SeriesFft,
        SeriesIfft,
        SeriesFitLine,
        SeriesFitLineDynamic,
        SeriesFit2Lines,
        SeriesFit2LinesDynamic,
        SeriesOutliers,
        SeriesIIR,
        SeriesPeriodsDetect,
        SeriesPeriodsValidate,
        SeriesFillBackwards,
        SeriesFillForward,
        SeriesFillConst,
        SeriesFillLinear,
        SeriesFitPoly,
        SeriesAdd,
        SeriesSubtract,
        SeriesMultiply,
        SeriesDivide,
        SeriesPow,
        SeriesGreater,
        SeriesGreaterEquals,
        SeriesLess,
        SeriesLessEquals,
        SeriesEquals,
        SeriesNotEquals,
        SeriesExp,
        SeriesSign,
        SeriesAbs,
        SeriesSin,
        SeriesAsin,
        SeriesCos,
        SeriesAcos,
        SeriesTan,
        SeriesAtan,
        SeriesMagnitude,
        SeriesSum,
        SeriesProduct,
        SeriesLog,
        SeriesFloor,
        SeriesCeiling,
        ArraySum,
        SeriesSeasonal,
        SeriesDecompose,
        SeriesDecomposeForecast,
        SeriesDecomposeAnomalies,
        SeriesPearsonCorrelation,
        SeriesDotProduct,
        SeriesCosineSimilarity,
// endregion

// region math functions
        Round,
        Ceiling,
        Pow,
        Sqrt,
        Log,
        Log2,
        Log10,
        Exp,
        Exp2,
        Exp10,
        PI,
        Cos,
        Sin,
        Tan,
        Acos,
        Asin,
        Atan,
        Atan2,
        Abs,
        Cot,
        Degrees,
        Radians,
        Sign,
        Rand,
        BetaCdf,
        BetaInv,
        BetaPdf,
        Gamma,
        LogGamma,
        Erf,
        Erfc,
        IsNan,
        IsInf,
        IsFinite,
        Coalesce,
        MaxOf,
        MinOf,
        WelchTest,
// endregion

// region geospatial functions
        GeoFromWkt,
        GeoAngle,
        GeoAzimuth,
        GeoClosestPointOnLine,
        GeoClosestPointOnPolygon,
        GeoDistance2Points,
        GeoDistancePointToLine,
        GeoDistancePointToPolygon,
        GeoPointInCircle,
        GeoPointInPolygon,
        GeoIntersects2Lines,
        GeoIntersectsLineWithPolygon,
        GeoIntersects2Polygons,
        GeoIntersection2Lines,
        GeoIntersectionLineWithPolygon,
        GeoIntersection2Polygons,
        GeoPolygonsUnion,
        GeoSimplifyPolygonsArray,
        GeoLinesUnion,
        GeoPolygonToH3Cells,
        GeoPolygonToS2Cells,
        GeoPolygonS2CellCoveringLevel,
        GeoPolygonDensify,
        GeoPolygonArea,
        GeoPolygonBuffer,
        GeoPolygonCentroid,
        GeoPolygonValidate,
        GeoPolygonPerimeter,
        GeoPolygonSimplify,
        GeoLineS2CellCoveringLevel,
        GeoLineLength,
        GeoLineBuffer,
        GeoLineCentroid,
        GeoLineDensify,
        GeoLineSimplify,
        GeoLineLocatePoint,
        GeoLineInterpolatePoint,
        GeoLineValidate,
        GeoLineToS2Cells,
        GeoLengthToS2CellLevel,
        GeoPointToGeohash,
        GeohashToCentralPoint,
        GeohashToPolygon,
        GeohashNeighbors,
        GeoPointBuffer,
        GeoPointToS2Cell,
        GeoS2CellToCentralPoint,
        GeoS2CellToPolygon,
        GeoS2CellNeighbors,
        GeoPointToH3Cell,
        GeoH3CellToCentralPoint,
        GeoH3CellToPolygon,
        GeoH3CellNeighbors,
        GeoH3CellChildren,
        GeoH3CellParent,
        GeoH3CellRings,
        GeoH3CellLevel,
        // endregion

        // region graph functions
        _All,
        Any,
        Map,
        InnerNodes,
        NodeId,
        NodeDegreeIn,
        NodeDegreeOut,
        Labels,
        // endregion

        // region ip-matching functions
        Ipv4Compare,
        Ipv4IsMatch,
        Ipv6Compare,
        Ipv4IsPrivate,
        Ipv6IsMatch,
        Ipv6IsInRange,
        Ipv6IsInAnyRange,
        Ipv4IsInRange,
        Ipv4IsInAnyRange,
        Ipv4NetmaskSuffix,
        Ipv6LookupRanges,
        // endregion

        // region formatting functions
        FormatIPV4,
        FormatIPV4Mask,
        FormatBytes,
        // endregion

        // region convert functions
        ConvertAngle,
        ConvertEnergy,
        ConvertForce,
        ConvertLength,
        ConvertMass,
        ConvertSpeed,
        ConvertTemperature,
        ConvertVolume,
        // endregion

        // region other
        CurrentClusterEndpoint,
        CurrentDatabase,
        CurrentPrincipal,
        CurrentPrincipalDetails,
        CurrentPrincipalIsMemberOf,
        ExtentId,
        ExtentId2,
        ExtentTags,
        CurrentNodeId,
        IngestionTime,
        RowId,
        CursorAfter,
        CursorBeforeOrAt,
        CursorCurrent,
        CursorCurrent2,
        HasIpv4,
        HasIpv4Prefix,
        HasAnyIpv4,
        HasAnyIpv4Prefix,
        Ipv4RangeToCidrList,
        RowNumber,
        RowCumSum,
        RowRank,
        RowRankDense,
        RowRankMin,
        RowWindowSession,
        Prev,
        Next,
        RowstoreOrdinalRange,
        EstimateDataSize,
        NewGuid,
        Invoke,
        Cast,
        IpGeoLocation,
        ColumnNamesOf
// endregion
    );
    // endregion
}
