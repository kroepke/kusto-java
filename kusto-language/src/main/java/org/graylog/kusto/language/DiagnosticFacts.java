// Ported from: src/Kusto.Language/Diagnostics/DiagnosticFacts.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language;

import java.util.Arrays;
import java.util.List;

import org.graylog.kusto.language.symbols.Symbol;
import org.graylog.kusto.language.symbols.TypeSymbol;
import org.graylog.kusto.language.syntax.SyntaxFacts;
import org.graylog.kusto.language.syntax.SyntaxKind;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.dotnet.DotNet;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Linq;

public final class DiagnosticFacts
{
    private DiagnosticFacts() // PORT: §3.5 static class
    {
    }

    public static Diagnostic getMissingText(String text)
    {
        return new Diagnostic("KS001", "Missing: " + DotNet.str(text)); // PORT: §3.14
    }

    public static Diagnostic getUnexpectedCharacter(String text)
    {
        return new Diagnostic("KS002", "Unexpected: " + DotNet.str(text)); // PORT: §3.14
    }

    public static Diagnostic getMalformedToken(String term)
    {
        return new Diagnostic("KS003", "Malformed " + DotNet.str(term)); // PORT: §3.14
    }

    public static Diagnostic getMalformedLiteral()
    {
        return new Diagnostic("KS004", "Malformed literal");
    }

    public static Diagnostic getTermsExpected(String... terms)
    {
        if (terms.length == 1)
        {
            return new Diagnostic("KS005", "Expected: " + DotNet.str(terms[0])); // PORT: §3.14
        }
        else
        {
            var list = DotNetStrings.join(", ", (Object[]) terms); // PORT: §3.14
            return new Diagnostic("KS005", "Expected one of: " + DotNet.str(list)); // PORT: §3.14
        }
    }

    public static Diagnostic getTokenExpected(SyntaxKind... kinds)
    {
        return getTokenExpected(Arrays.asList(kinds)); // PORT: §3.17 array as IReadOnlyList
    }

    public static Diagnostic getTokenExpected(List<SyntaxKind> kinds)
    {
        return getTokenExpected(Linq.<SyntaxKind, String>select(kinds, k -> SyntaxFacts.getText(k))); // PORT: §3.6, §3.5
    }

    public static Diagnostic getTokenExpected(Iterable<String> texts)
    {
        return getTermsExpected(Linq.toArray(texts, String[]::new)); // PORT: §3.6
    }

    public static Diagnostic getTokenExpected(String... tokens)
    {
        return getTokenExpected((Iterable<String>) Arrays.asList(tokens)); // PORT: §3.17 array as IReadOnlyList; upstream binds the IEnumerable<string> overload
    }

    private static Diagnostic getMissingElement(String term)
    {
        return new Diagnostic("KS006", "Missing " + DotNet.str(term)); // PORT: §3.14
    }

    public static Diagnostic getMissingName()
    {
        return getMissingElement("name");
    }

    public static Diagnostic getMissingNameWithKeyword(String keyword)
    {
        return new Diagnostic("KS006", "Missing name: If the keyword '" + DotNet.str(keyword) + "' is intended be used as the name, it needs to be bracketted as " + DotNet.str(KustoFacts.getBracketedName(keyword)) + "."); // PORT: §3.14
    }

    public static Diagnostic getMissingValue()
    {
        return getMissingElement("value");
    }

    public static Diagnostic getMissingExpression()
    {
        return getMissingElement("expression");
    }

    public static Diagnostic getMissingExpressionWithKeyword(String keyword)
    {
        return new Diagnostic("KS006", "Missing expression: If the keyword '" + DotNet.str(keyword) + "' is intended to be part of an expression it needs to be bracketted as " + DotNet.str(KustoFacts.getBracketedName(keyword)) + "."); // PORT: §3.14
    }

    public static Diagnostic getMissingNumber()
    {
        return getMissingElement("number");
    }

    public static Diagnostic getMissingString()
    {
        return getMissingElement("string");
    }

    public static Diagnostic getMissingBoolean()
    {
        return getMissingElement("boolean");
    }

    public static Diagnostic getMissingTypeOfLiteral()
    {
        return getMissingElement("typeof");
    }

    public static Diagnostic getMissingFunctionCall()
    {
        return getMissingElement("function call");
    }

    public static Diagnostic getMissingFunctionDeclaration()
    {
        return getMissingElement("function declaration");
    }

    public static Diagnostic getMissingTypeName()
    {
        return getMissingElement("type name");
    }

    public static Diagnostic getMissingParameter()
    {
        return getMissingElement("parameter");
    }

    public static Diagnostic getMissingFirstOrLast()
    {
        return getMissingElement("first or last");
    }

    public static Diagnostic getMissingAllLastOrNone()
    {
        return getMissingElement("all, last or none");
    }

    public static Diagnostic getMissingJsonValue()
    {
        return getMissingElement("json value");
    }

    public static Diagnostic getMissingJoinOnClause()
    {
        return getMissingElement("join on condition clause");
    }

    public static Diagnostic getMissingJsonPair()
    {
        return getMissingElement("json key:value pair");
    }

    public static Diagnostic getMissingStatement()
    {
        return getMissingElement("statement");
    }

    public static Diagnostic getMissingPatternMatch()
    {
        return getMissingElement("pattern match clause");
    }

    public static Diagnostic getMissingClause()
    {
        return getMissingElement("clause");
    }

    public static Diagnostic getMissingClause(String clauseName)
    {
        return getMissingElement(DotNet.str(clauseName) + " clause"); // PORT: §3.14
    }        

    public static Diagnostic getMissingSchemaDeclaration()
    {
        return getMissingElement("schema declaration");
    }

    public static Diagnostic getParsePatternMustStartWithColumnNameOrStar()
    {
        return new Diagnostic("KS100", "The pattern must start with a column name or *");
    }

    public static Diagnostic getParsePatternNameDoesNotFollowStringLiteral()
    {
        return new Diagnostic("KS101", "The column name must follow a string literal");
    }

    public static Diagnostic getParsePatternStringLiteralMustFollowStar()
    {
        return new Diagnostic("KS102", "A string literal must follow a *");
    }

    public static Diagnostic getParsePatternUsingStarAfterStringColumnIsAmbiguous()
    {
        return new Diagnostic("KS103", "Using * after parsing a string column is ambiguous.");
    }

    public static Diagnostic getInvalidPatternPart()
    {
        return new Diagnostic("KS104", "Invalid pattern part.");
    }

    public static Diagnostic getIdentifierNameOnly()
    {
        return new Diagnostic("KS105", "The name must be a single identifier only.");
    }

    public static Diagnostic getOperatorNotDefined(String name, TypeSymbol... argumentTypes)
    {
        return getOperatorNotDefined(name, Arrays.asList(argumentTypes)); // PORT: §3.17 array as IReadOnlyList
    }

    public static Diagnostic getOperatorNotDefined(String name, List<TypeSymbol> argumentTypes)
    {
        if (argumentTypes.size() == 1)
        {
            return new Diagnostic("KS106", "The operator '" + DotNet.str(name) + "' is not defined for the operand type " + DotNet.str(argumentTypes.get(0).name()) + "."); // PORT: §3.14
        }
        else
        {
            var list = ListExtensions.join(Linq.toList(Linq.select(argumentTypes, t -> t.name())), ", ", " and "); // PORT: §3.6, §3.5
            return new Diagnostic("KS106", "The operator '" + DotNet.str(name) + "' is not defined for the operand types " + DotNet.str(list) + "."); // PORT: §3.14
        }
    }

    public static Diagnostic getTypeExpected(Symbol type)
    {
        return new Diagnostic("KS107", "A value of type " + DotNet.str(type.name()) + " expected."); // PORT: §3.14
    }

    public static Diagnostic getTypeExpected(List<TypeSymbol> types)
    {
        if (types.size() == 1)
        {
            return getTypeExpected(types.get(0));
        }
        else
        {
            var list = ListExtensions.join(Linq.toList(Linq.select(types, t -> t.name())), ", ", " or "); // PORT: §3.6, §3.5
            return new Diagnostic("KS107", "A value of type " + DotNet.str(list) + " expected."); // PORT: §3.14
        }
    }

    public static Diagnostic getScalarTypeExpected()
    {
        return new Diagnostic("KS108", "Scalar value expected.");
    }

    public static Diagnostic getColumnExpected()
    {
        return new Diagnostic("KS109", "Column name expected.");
    }

    public static Diagnostic getRenameAssignmentExpected()
    {
        return new Diagnostic("KS110", "Column rename assignment expected.");
    }

    public static Diagnostic getTabularValueExpected()
    {
        return new Diagnostic("KS111", "Tabular value expected.");
    }

    public static Diagnostic getTableOrScalarExpected()
    {
        return new Diagnostic("KS112", "A tabular or scalar value expected.");
    }

    public static Diagnostic getSingleColumnTableExpected()
    {
        return new Diagnostic("KS113", "A tabular value with only one column expected.");
    }

    public static Diagnostic getDatabaseExpected()
    {
        return new Diagnostic("KS114", "Database expected.");
    }

    public static Diagnostic getClusterExpected()
    {
        return new Diagnostic("KS115", "Cluster expected.");
    }

    public static Diagnostic getTypeNotAllowed(Symbol type)
    {
        return new Diagnostic("KS116", "The value of type '" + DotNet.str(type.name()) + "' is not allowed in this context."); // PORT: §3.14
    }

    public static Diagnostic getFunctionRequiresArgumentList(String functionName)
    {
        return new Diagnostic("KS117", "The function '" + DotNet.str(functionName) + "' requires an argument list."); // PORT: §3.14
    }

    public static Diagnostic getArgumentCountExpected(int count)
    {
        if (count == 0)
        {
            return new Diagnostic("KS118", "No arguments expected.");
        }
        else if (count == 1)
        {
            return new Diagnostic("KS118", "1 argument expected.");
        }
        else
        {
            return new Diagnostic("KS118", DotNet.str(count) + " arguments expected."); // PORT: §3.14
        }
    }

    public static Diagnostic getFunctionExpectsArgumentCountExact(String functionName, int count)
    {
        if (count == 0)
        {
            return new Diagnostic("KS119", "The function '" + DotNet.str(functionName) + "' expects no arguments."); // PORT: §3.14
        }
        else if (count == 1)
        {
            return new Diagnostic("KS119", "The function '" + DotNet.str(functionName) + "' expects 1 argument."); // PORT: §3.14
        }
        else
        {
            return new Diagnostic("KS119", "The function '" + DotNet.str(functionName) + "' expects " + DotNet.str(count) + " arguments."); // PORT: §3.14
        }
    }

    public static Diagnostic getFunctionExpectsArgumentCountRange(String functionName, int min, int max)
    {
        if (min == max)
        {
            return getFunctionExpectsArgumentCountExact(functionName, min);
        }
        else
        {
            return new Diagnostic("KS120", "The function '" + DotNet.str(functionName) + "' expects between " + DotNet.str(min) + " and " + DotNet.str(max) + " arguments."); // PORT: §3.14
        }
    }

    public static Diagnostic getFunctionHasIncorrectNumberOfArguments()
    {
        return new Diagnostic("KS121", "The function call has an incorrect number of arguments.");
    }

    public static Diagnostic getScalarFunctionNotDefined(String name)
    {
        return new Diagnostic("KS122", "The scalar function '" + DotNet.str(name) + "' is not defined."); // PORT: §3.14
    }

    public static Diagnostic getAggregateFunctionNotDefined(String name)
    {
        return new Diagnostic("KS123", "The aggregate function '" + DotNet.str(name) + "' is not defined."); // PORT: §3.14
    }

    public static Diagnostic getPlugInFunctionNotDefined(String name)
    {
        return new Diagnostic("KS124", "The plug-in function '" + DotNet.str(name) + "' is not defined."); // PORT: §3.14
    }

    public static Diagnostic getPlugInFunctionIsNotEnabled(String name)
    {
        return new Diagnostic("KS125", "The plug-in function '" + DotNet.str(name) + "' is not enabled."); // PORT: §3.14
    }

    public static Diagnostic getPluginNotAllowedInThisContext(String name)
    {
        return new Diagnostic("KS126", "The plug-in function '" + DotNet.str(name) + "' is not allowed in this context."); // PORT: §3.14
    }

    public static Diagnostic getFunctionNotDefinedWithMatchingParameters(String name, List<? extends Symbol> argumentTypes) // PORT: §3.10 covariance
    {
        var types = DotNetStrings.join(", ", Linq.select(argumentTypes, p -> p.name())); // PORT: §3.14, §3.6
        return new Diagnostic("KS127", "The function '" + DotNet.str(name) + "' is not compatible with arguments (" + DotNet.str(types) + ")"); // PORT: §3.14
    }

    public static Diagnostic getNameIsNotAFunction(String name)
    {
        return new Diagnostic("KS128", "The name '" + DotNet.str(name) + "' does not refer to a function."); // PORT: §3.14
    }

    public static Diagnostic getExpressionMustBeConstant()
    {
        return new Diagnostic("KS129", "The expression must be a constant.");
    }

    public static Diagnostic getExpressionMustBeConstantOrIdentifier()
    {
        return new Diagnostic("KS130", "The expression must be a constant or identifier.");
    }

    public static Diagnostic getExpressionMustBeLiteral()
    {
        return new Diagnostic("KS131", "The expression must be a literal.");
    }

    public static Diagnostic getExpressionMustBeLiteralScalarValue()
    {
        return new Diagnostic("KS132", "The expression must be a literal scalar value.");
    }

    public static Diagnostic getExpressionMustNotBeEmpty()
    {
        return new Diagnostic("KS133", "The expression value must not be empty.");
    }

    public static Diagnostic getExpressionMustBeInteger()
    {
        return new Diagnostic("KS134", "The expression value must be an integer.");
    }

    public static Diagnostic getExpressionMustBeRealOrDecimal()
    {
        return new Diagnostic("KS135", "The expression value must be an real or decimal number.");
    }

    public static Diagnostic getExpressionMustBeIntegerOrArray()
    {
        return new Diagnostic("KS136", "The expression value must be an integer or a dynamic array of integers.");
    }

    public static Diagnostic getExpressionMustBeNumeric()
    {
        return new Diagnostic("KS137", "The expression value must be a number.");
    }

    public static Diagnostic getExpressionMustBeNumericOrBool()
    {
        return new Diagnostic("KS137", "The expression value must be a number or boolean true/false.");
    }

    public static Diagnostic getExpressionMustBeSummable()
    {
        return new Diagnostic("KS138", "The argument value must be summable: a number, timespan or datetime.");
    }

    public static Diagnostic getMultiValuedExpressionCannotBeAssignedToVariable()
    {
        return new Diagnostic("KS139", "The multi-valued expression cannot be assigned to a variable.");
    }

    public static <T> Diagnostic getExpressionMustHaveValue(List<T> values)
    {
        if (values.size() == 1)
        {
            return new Diagnostic("KS140", "The expression must be the value: " + DotNet.str(values.get(0))); // PORT: §3.14
        }
        else
        {
            var list = ListExtensions.join(Linq.toList(Linq.select(values, v -> DotNet.str(v))), ", ", " or "); // PORT: §3.6, §3.5, §5.2 ToString
            return new Diagnostic("KS140", "The expression must be one of the values: " + DotNet.str(list)); // PORT: §3.14
        }
    }

    public static <T> Diagnostic getExpressionMustNotHaveValue(List<T> values)
    {
        if (values.size() == 1)
        {
            return new Diagnostic("KS140", "The expression must not be the value: " + DotNet.str(values.get(0))); // PORT: §3.14
        }
        else
        {
            var list = ListExtensions.join(Linq.toList(Linq.select(values, v -> DotNet.str(v))), ", ", " or "); // PORT: §3.6, §3.5, §5.2 ToString
            return new Diagnostic("KS140", "The expression must not be one of the values: " + DotNet.str(list)); // PORT: §3.14
        }
    }

    @SafeVarargs
    public static <T> Diagnostic getExpressionMustHaveValue(T... values) // PORT: §3.10 params T[]
    {
        return getExpressionMustHaveValue(Arrays.asList(values)); // PORT: §3.17 array as IReadOnlyList
    }

    public static <S extends Symbol> Diagnostic getExpressionMustHaveType(List<S> types)
    {
        if (types.size() == 1)
        {
            return new Diagnostic("KS141", "The expression must have the type " + DotNet.str(types.get(0).name()) + "."); // PORT: §3.14
        }
        else
        {
            var list = ListExtensions.join(Linq.toList(Linq.select(types, s -> s.name())), ", ", " or "); // PORT: §3.6, §3.5
            return new Diagnostic("KS141", "The expression must have one of the types: " + DotNet.str(list) + "."); // PORT: §3.14
        }
    }

    @SafeVarargs
    public static <S extends Symbol> Diagnostic getExpressionMustHaveType(S... types) // PORT: §3.10 params T[]
    {
        return getExpressionMustHaveType(Arrays.asList(types)); // PORT: §3.17 array as IReadOnlyList
    }

    public static Diagnostic getNameDoesNotReferToAnyKnownItem(String name, String entity)
    {
        if (entity == null)
            entity = "column, table, variable or function";
        return new Diagnostic("KS142", "The name '" + DotNet.str(name) + "' does not refer to any known " + DotNet.str(entity) + "."); // PORT: §3.14
    }

    public static Diagnostic getNameDoesNotReferToAnyKnownItem(String name) // PORT: §3.12
    {
        return getNameDoesNotReferToAnyKnownItem(name, null);
    }

    public static Diagnostic getFunctionNotDefined(String name)
    {
        return new Diagnostic("KS143", "The function '" + DotNet.str(name) + "' is not defined."); // PORT: §3.14
    }

    public static Diagnostic getAggregateNotAllowedInThisContext(String name)
    {
        return new Diagnostic("KS144", "The aggregate function '" + DotNet.str(name) + "' is not allowed in this context."); // PORT: §3.14
    }

    public static Diagnostic getColumnMustExistOnBothSidesOfJoin(String name)
    {
        return new Diagnostic("KS145", "The column '" + DotNet.str(name) + "' must exist on both sides of the join."); // PORT: §3.14
    }

    public static Diagnostic getNameRefersToMoreThanOneItem(String name)
    {
        return new Diagnostic("KS146", "The name '" + DotNet.str(name) + "' refers to more than one column or variable"); // PORT: §3.14
    }

    public static Diagnostic getTheElementAccessOperatorIsNotAllowedInThisContext()
    {
        return new Diagnostic("KS147", "The element access operator [] is not allowed in this context.");
    }

    public static Diagnostic getTheExpressionHasNoName()
    {
        return new Diagnostic("KS148", "A column name cannot be inferred for this expression.");
    }

    public static Diagnostic getTheExpressionDoesNotHaveMultipleValues()
    {
        return new Diagnostic("KS149", "The expression does not have multiple named values.");
    }

    public static Diagnostic getTheNameDoesNotHaveCorrespondingExpression()
    {
        return new Diagnostic("KS150", "The name does not have a corresponding expression.");
    }

    public static Diagnostic getInvalidTypeName(String name)
    {
        return new Diagnostic("KS160", "The name '" + DotNet.str(name) + "' is not a valid type name that can be used here."); // PORT: §3.14
    }

    public static Diagnostic getInvalidColumnDeclaration()
    {
        return new Diagnostic("KS170", "The syntax is not a valid column declaration.");
    }

    public static Diagnostic getDuplicateColumnDeclaration(String name)
    {
        return new Diagnostic("KS171", "A column with the name '" + DotNet.str(name) + "' is already declared."); // PORT: §3.14
    }

    public static Diagnostic getInvalidTypeExpression()
    {
        return new Diagnostic("KS172", "The syntax is not a valid type expression.");
    }

    public static Diagnostic getIncorrectNumberOfDataValues(int multiple)
    {
        return new Diagnostic("KS173", "Incorrect number of data values. The values should appear in multiples of " + DotNet.str(multiple) + "."); // PORT: §3.14
    }

    public static Diagnostic getQueryOperatorCannotBeFirst()
    {
        return new Diagnostic("KS174", "The operator cannot be the first operator in a query.");
    }

    public static Diagnostic getQueryOperatorMustBeFirst()
    {
        return new Diagnostic("KS175", "The operator must be the first operator in the query.");
    }

    public static Diagnostic getQueryOperatorExpected()
    {
        return new Diagnostic("KS176", "Query operator expected.");
    }

    public static Diagnostic getQueryOperatorNotAllowedInContext(String name)
    {
        return new Diagnostic("KS177", "The query operator '" + DotNet.str(name) + "' is not allowed in the current context."); // PORT: §3.14
    }

    public static Diagnostic getTypeIsNotIntervalType(Symbol intervalType, Symbol rangeType)
    {
        return new Diagnostic("KS178", "The type '" + DotNet.str(intervalType.name()) + "' is not an appropriate interval type for '" + DotNet.str(rangeType.name()) + "'"); // PORT: §3.14
    }

    public static Diagnostic getUnknownQueryOperatorParameterName(String name)
    {
        return new Diagnostic("KS179", "The name '" + DotNet.str(name) + "' is not a recognized parameter for this operator.").withSeverity(DiagnosticSeverity.Warning); // PORT: §3.14
    }

    public static Diagnostic getParameterAlreadySpecified(String name)
    {
        return new Diagnostic("KS180", "The parameter '" + DotNet.str(name) + "' is already specified."); // PORT: §3.14
    }

    public static Diagnostic getNameDoesNotReferToTable(String name)
    {
        return new Diagnostic("KS181", "The name '" + DotNet.str(name) + "' does not refer to a table."); // PORT: §3.14
    }

    public static Diagnostic getInvalidJoinCondition()
    {
        return new Diagnostic("KS182", "The join condition must be either the name of a column common to both tables or in the form $left.<column> == $right.<column>.");
    }
    public static Diagnostic getInvalidJoinConditionOperand(String prefix)
    {
        return new Diagnostic("KS183", "The join condition operand must be: " + DotNet.str(prefix) + ".<column>"); // PORT: §3.14
    }

    public static Diagnostic getTheExpressionRefersToMoreThanOneColumn()
    {
        return new Diagnostic("KS184", "The expression refers to more than one column.");
    }

    public static Diagnostic getPackMustBeLastItemInList()
    {
        return new Diagnostic("KS185", "The pack(*) expression must be the last item in the list.");
    }

    public static Diagnostic getValueCountMustEqualParameterCount()
    {
        return new Diagnostic("KS185", "The number of values must equal the number of parameters.");
    }

    public static Diagnostic getPathValueWithNoPathParameter()
    {
        return new Diagnostic("KS186", "A path value can only be specified when a path name is part of the declaration.");
    }

    public static Diagnostic getPathValueExpected()
    {
        return new Diagnostic("KS187", "A path value is expected.");
    }

    public static Diagnostic getNoPatternMatchesArguments()
    {
        return new Diagnostic("KS188", "No pattern matches the specified arguments.");
    }

    public static Diagnostic getDefaultValueExpected()
    {
        return new Diagnostic("KS189", "Default value expected.");
    }

    public static Diagnostic getTableHasNoColumns()
    {
        return new Diagnostic("KS190", "The table has no columns");
    }

    public static Diagnostic getStarExpressionNotAllowed()
    {
        return new Diagnostic("KS191", "The * syntax is not allowed here.");
    }

    public static Diagnostic getStarExpressionMustBeLastArgument()
    {
        return new Diagnostic("KS192", "The * syntax must be the last argument.");
    }

    public static Diagnostic getNamedArgumentsNotSupported()
    {
        return new Diagnostic("KS193", "Named arguments are not supported for this function.");
    }

    public static Diagnostic getCompoundNamedArgumentsNotSupported()
    {
        return new Diagnostic("KS194", "Compound named arguments are not supported.");
    }

    public static Diagnostic getUnnamedArgumentAfterOutofOrderNamedArgument()
    {
        return new Diagnostic("KS195", "All arguments after an unordered named argument must be named.");
    }

    public static Diagnostic getUnknownArgumentName()
    {
        return new Diagnostic("KS196", "The argument name does not refer to a declared parameter.");
    }

    public static Diagnostic getMissingArgumentForParameter(String parameterName)
    {
        return new Diagnostic("KS197", "The argument for parameter '" + DotNet.str(parameterName) + "' is missing."); // PORT: §3.14
    }

    public static Diagnostic getIncompleteFragment()
    {
        return new Diagnostic("KS198", "The incomplete fragment is unexpected.");
    }

    public static Diagnostic getNoColumnsInScope()
    {
        return new Diagnostic("KS199", "No columns are currently in scope.");
    }

    public static Diagnostic getErrorInExpansion(String name, String errors)
    {
        return new Diagnostic("KS200", "Failure in expansion of '" + DotNet.str(name) + "': " + DotNet.str(errors)); // PORT: §3.14
    }

    public static Diagnostic getVariableAlreadyDeclared(String name)
    {
        return new Diagnostic("KS201", "A variable with the name '" + DotNet.str(name) + "' has already been declared."); // PORT: §3.14
    }

    public static Diagnostic getMaterializedViewNameMustBeStringLiteral()
    {
        return new Diagnostic("KS202", "Materialized view name must be a string literal");
    }

    public static Diagnostic getAnalyzerFailure(String analyzerName, String message)
    {
        return new Diagnostic("KS203", "Failure in analysis '" + DotNet.str(analyzerName) + "': " + DotNet.str(message)); // PORT: §3.14
    }

    public static Diagnostic getNameDoesNotReferToAnyKnownTable(String name)
    {
        return new Diagnostic("KS204", "The name '" + DotNet.str(name) + "' does not refer to any known table, tabular variable or function."); // PORT: §3.14
    }

    public static Diagnostic getFuzzyEntityNotDefined(String name, String kind)
    {
        if (DotNetStrings.isNullOrEmpty(name))
        {
            return new Diagnostic("KS205",
                "The fuzzy expression does not refer to any known or currently accessible " + DotNet.str(kind) + ".") // PORT: §3.14
                .withSeverity(DiagnosticSeverity.Warning);
        }
        else
        {
            return new Diagnostic("KS205",
                "The fuzzy name '" + DotNet.str(name) + "' does not refer to any known or currently accessible " + DotNet.str(kind) + ".") // PORT: §3.14
                .withSeverity(DiagnosticSeverity.Warning);
        }
    }

    public static Diagnostic getFuzzyEntityNotDefined(String name) // PORT: §3.12
    {
        return getFuzzyEntityNotDefined(name, "entity");
    }

    public static Diagnostic getFuzzyEntityNotDefined() // PORT: §3.12
    {
        return getFuzzyEntityNotDefined(null);
    }

    public static Diagnostic getFuzzyClusterNotDefined(String name)
    {
        return getFuzzyEntityNotDefined(name, "cluster");
    }

    public static Diagnostic getFuzzyClusterNotDefined() // PORT: §3.12
    {
        return getFuzzyClusterNotDefined(null);
    }

    public static Diagnostic getFuzzyDatabaseNotDefined(String name)
    {
        return getFuzzyEntityNotDefined(name, "database");
    }

    public static Diagnostic getFuzzyDatabaseNotDefined() // PORT: §3.12
    {
        return getFuzzyDatabaseNotDefined(null);
    }

    public static Diagnostic getFuzzyTableNotDefined(String name)
    {
        return getFuzzyEntityNotDefined(name, "table");
    }

    public static Diagnostic getFuzzyTableNotDefined() // PORT: §3.12
    {
        return getFuzzyTableNotDefined(null);
    }

    public static Diagnostic getFuzzyExternalTableNotDefined(String name)
    {
        return getFuzzyEntityNotDefined(name, "external table");
    }

    public static Diagnostic getFuzzyExternalTableNotDefined() // PORT: §3.12
    {
        return getFuzzyExternalTableNotDefined(null);
    }

    public static Diagnostic getFuzzyMaterializedViewNotDefined(String name)
    {
        return getFuzzyEntityNotDefined(name, "materialized view");
    }

    public static Diagnostic getFuzzyMaterializedViewNotDefined() // PORT: §3.12
    {
        return getFuzzyMaterializedViewNotDefined(null);
    }

    public static Diagnostic getFuzzyEntityGroupNotDefined(String name)
    {
        return getFuzzyEntityNotDefined(name, "entity group");
    }

    public static Diagnostic getFuzzyEntityGroupNotDefined() // PORT: §3.12
    {
        return getFuzzyEntityGroupNotDefined(null);
    }

    public static Diagnostic getFuzzyFunctionNotDefined(String name)
    {
        return getFuzzyEntityNotDefined(name, "function");
    }

    public static Diagnostic getFuzzyFunctionNotDefined() // PORT: §3.12
    {
        return getFuzzyFunctionNotDefined(null);
    }

    public static Diagnostic getFuzzyStoredQueryResultNotDefined(String name)
    {
        return getFuzzyEntityNotDefined(name, "stored query result");
    }

    public static Diagnostic getFuzzyStoredQueryResultNotDefined() // PORT: §3.12
    {
        return getFuzzyStoredQueryResultNotDefined(null);
    }

    public static Diagnostic getExpressionMustBeOrderable()
    {
        return new Diagnostic("KS206", "The argument value must be orderable: a number, timespan, datetime, string or boolean.");
    }

    public static Diagnostic getNameDoesNotReferToAnyKnownCluster(String name)
    {
        return new Diagnostic("KS207", "The name '" + DotNet.str(name) + "' either does not refer to a reachable cluster or no schema from it is currently available.") // PORT: §3.14
            .withSeverity(DiagnosticSeverity.Warning);
    }

    public static Diagnostic getNameDoesNotReferToAnyKnownDatabase(String name)
    {
        return new Diagnostic("KS208", "The name '" + DotNet.str(name) + "' does not refer to any known database."); // PORT: §3.14
    }

    public static Diagnostic getNameDoesNotReferToAnyKnownExternalTable(String name)
    {
        return new Diagnostic("KS209", "The name '" + DotNet.str(name) + "' does not refer to any known external table."); // PORT: §3.14
    }

    public static Diagnostic getNameDoesNotReferToAnyKnownMaterializedView(String name)
    {
        return new Diagnostic("KS210", "The name '" + DotNet.str(name) + "' does not refer to any known materialized view."); // PORT: §3.14
    }

    public static Diagnostic getNameDoesNotReferToAnyKnownFunction(String name)
    {
        return new Diagnostic("KS211", "The name '" + DotNet.str(name) + "' does not refer to any known function."); // PORT: §3.14
    }

    public static Diagnostic getClientParametersNotSupported()
    {
        return new Diagnostic("KS213", "Client parameters are not supported or enabled.");
    }

    public static Diagnostic getRawGuidLiteralNotAllowed()
    {
        return new Diagnostic("KS214", "Raw guid literals are not allowed in this context, use guid(...) instead.");
    }

    public static Diagnostic getDecimalInDynamic()
    {
        return new Diagnostic("KS215", "Decimal values are not supported in dynamic objects.");
    }

    public static Diagnostic getClusterDatabaseOrTableExpected()
    {
        return new Diagnostic("KS216", "Cluster, Database or Table expected.");
    }

    public static Diagnostic getMissingGraphMatchPattern()
    {
        return new Diagnostic("KS217", "Missing graph-match pattern.");
    }

    public static Diagnostic getGraphExpected()
    {
        return new Diagnostic("KS218", "Graph expected.");
    }

    public static Diagnostic getQueryOperatorExpectsGraph()
    {
        return new Diagnostic("KS219", "The query operator requires an input graph.");
    }

    public static Diagnostic getColumnDeclarationExpected()
    {
        return new Diagnostic("KS220", "Column declaration expected");
    }

    public static Diagnostic getIntegerLiteralExpected()
    {
        return new Diagnostic("KS221", "Integer literal expected.");
    }

    public static Diagnostic getStringLiteralExpected()
    {
        return new Diagnostic("KS222", "String literal expected.");
    }

    public static Diagnostic getBooleanLiteralExpected()
    {
        return new Diagnostic("KS223", "Boolean literal expected.");
    }

    public static Diagnostic getSummableLiteralExpected()
    {
        return new Diagnostic("KS224", "Summable literal expected.");
    }

    public static Diagnostic getNumericLiteralExpected()
    {
        return new Diagnostic("KS225", "Numeric literal expected.");
    }

    public static Diagnostic getScalarLiteralExpected()
    {
        return new Diagnostic("KS226", "Scalar literal expected.");
    }

    public static Diagnostic getRealLiteralExpected()
    {
        return new Diagnostic("KS225", "Real literal expected.");
    }

    public static Diagnostic getTabularParametersMustBeDeclaredFirst()
    {
        return new Diagnostic("KS226", "Tabular parameters must be declared first.");
    }

    public static Diagnostic getCommonJoinColumnsMustHaveSameType(String name)
    {
        return new Diagnostic("KS227", "The common column '" + DotNet.str(name) + "' must have the same type on both sides of the join."); // PORT: §3.14
    }

    public static Diagnostic getNameRequiresBrackets(String name)
    {
        return new Diagnostic("KS228", "The name '" + DotNet.str(name) + "' needs to be bracketed as " + DotNet.str(KustoFacts.getBracketedName(name)) + " to be used in this context."); // PORT: §3.14
    }

    public static Diagnostic getMissingGraphEntityType()
    {
        return new Diagnostic("KS229", "Missing graph entity type, Expected values [nodes, edges].");
    }

    public static Diagnostic getIncorrectNumberOfOutputGraphToTableEntities()
    {
        return new Diagnostic("KS230", "The graph-to-table operator requires 1-2 arguments (nodes, edges).");
    }

    public static Diagnostic getTableOrGraphExpected()
    {
        return new Diagnostic("KS231", "Table or graph expected.");
    }

    public static Diagnostic getNoCommonArgumentType()
    {
        return new Diagnostic("KS232", "No common type can be determined from the arguments.");
    }

    public static Diagnostic getExpressionMustBeStringOrArray()
    {
        return new Diagnostic("KS233", "The expression value must be a string or a dynamic array of strings.");
    }

    public static Diagnostic getExpressionMustBeDynamicArray()
    {
        return new Diagnostic("KS234", "The expression value must be a dynamic array.");
    }

    public static Diagnostic getExpressionMustBeDynamicBag()
    {
        return new Diagnostic("KS235", "The expression must be a dynamic bag.");
    }

    public static Diagnostic getInvalidNameInAggregateContext(String name)
    {
        return new Diagnostic("KS236", "The name '" + DotNet.str(name) + "' is not an aggregate or scalar function."); // PORT: §3.14
    }

    public static Diagnostic getInvalidNameInPlugInContext(String name)
    {
        return new Diagnostic("KS237", "The name '" + DotNet.str(name) + "' is not a plug-in function."); // PORT: §3.14
    }

    public static Diagnostic getMissingGraphMatchPatternElement()
    {
        return new Diagnostic("KS238", "Missing graph-match pattern element.");
    }

    public static Diagnostic getGraphMatchPatternSyntaxError(String expectedElementType, String actualElementType)
    {
        return new Diagnostic("KS239", "graph-match element expected type is '" + DotNet.str(expectedElementType) + "', but received '" + DotNet.str(actualElementType) + "' type."); // PORT: §3.14
    }

    public static Diagnostic getMakeGraphDynamicNodeIdColumnNotSupported()
    {
        return new Diagnostic("KS240", "source, target and node id columns can't be of type dynamic, consider using explicit cast");
    }

    public static Diagnostic getJoinKeyCannotBeDynamic()
    {
        return new Diagnostic("KS241", "Join keys cannot be dynamic");
    }

    public static Diagnostic getJoinKeysNotComparable(String leftType, String rightType)
    {
        return new Diagnostic("KS242", "Inconsistent data types for join keys: (" + DotNet.str(leftType) + ", " + DotNet.str(rightType) + ")"); // PORT: §3.14
    }

    public static Diagnostic getMakeGraphImplicityIdShouldNotBeEmpty()
    {
        return new Diagnostic("KS243", "Implicit node ID should not be empty.");
    }

    public static Diagnostic getQueryTextSizeExceeded()
    {
        return new Diagnostic("KS244", "Query text size exceeds maximum safe limit. Parsing not performed.");
    }

    public static Diagnostic getQuerySyntaxDepthExceeded()
    {
        return new Diagnostic("KS245", "Query syntax depth exceeds maximum safe limit. Semantic analysis not performed.");
    }

    public static Diagnostic getInternalFailure()
    {
        return new Diagnostic("KS246", "An internal failure occurred during parsing.");
    }

    public static Diagnostic getNameDoesNotReferToAnyKnownEntityGroup(String name)
    {
        return new Diagnostic("KS247", "The name '" + DotNet.str(name) + "' does not refer to any known entity group."); // PORT: §3.14
    }

    public static Diagnostic getNameDoesNotReferToAnyKnownStoredQueryResult(String name)
    {
        return new Diagnostic("KS248", "The name '" + DotNet.str(name) + "' does not refer to any known stored query result."); // PORT: §3.14
    }

    public static Diagnostic getTabularValueDoesNotHaveRequiredColumns()
    {
        return new Diagnostic("KS249", "The tabular value does not have the required columns.");
    }

    public static Diagnostic getUnknownDirective(String name)
    {
        return new Diagnostic("KS250", "Unknown directive '" + DotNet.str(name) + "'."); // PORT: §3.14
    }

    /// <summary>
    /// This is for expecting exactly a table reference, and not any other tabular expression.
    /// </summary>
    public static Diagnostic getTableExpected()
    {
        return new Diagnostic("KS251", "Table expected.");
    }

    public static Diagnostic getMissingPartitionColumnDeclaration()
    {
        return new Diagnostic("KS252", "Partition column definition is expected.");
    }

    public static Diagnostic getMissingDataFormat()
    {
        return new Diagnostic("KS253", "DataFormat definition is expected.");
    }

    public static Diagnostic getMissingExternalTableKind()
    {
        return new Diagnostic("KS254", "External Table Kind definition is expected.");
    }

    public static Diagnostic getMissingPathFormatTokens()
    {
        return new Diagnostic("KS255", "External Table Path Format is empty.");
    }

    public static Diagnostic getMissingConnectionStrings()
    {
        return new Diagnostic("KS256", "Connection strings definition is expected.");
    }

    public static Diagnostic getUnknownTokenInPathFormatDefinition()
    {
        return new Diagnostic("KS257", "External Table Path Format syntax has unsupported expresion.");
    }

    public static Diagnostic getWrongPartitionColumnType()
    {
        return new Diagnostic("KS258", "External Table Partition Column type is not supported.");
    }

    public static Diagnostic getWrongPartitionColumnFunction()
    {
        return new Diagnostic("KS259", "External Table Partition Column does not support this function.");
    }

    public static Diagnostic getNameDoesNotReferToAnyKnownGraphModel(String name)
    {
        return new Diagnostic("KS260", "The name '" + DotNet.str(name) + "' does not refer to any known graph model."); // PORT: §3.14
    }

    public static Diagnostic getNameDoesNotReferToAnyKnownGraphSnapshot(String name, String graphModelName)
    {
        return new Diagnostic("KS261", "The name '" + DotNet.str(name) + "' does not refer to any known graph snapshot of graph model '" + DotNet.str(graphModelName) + "'."); // PORT: §3.14
    }

    public static Diagnostic getLatestSnapshotUnknown(String graphModelName)
    {
        return new Diagnostic("KS262", "The lastest snapshot of graph model '" + DotNet.str(graphModelName) + "' is unknown."); // PORT: §3.14
    }

    public static Diagnostic getVolatileGraphUnknown(String graphModelName)
    {
        return new Diagnostic("KS263", "The volatile graph of model '" + DotNet.str(graphModelName) + "' is unknown."); // PORT: §3.14
    }

    public static Diagnostic getPartitionColumnNotUsedInPathFormat(String partitionName)
    {
        return new Diagnostic("KS264", "External Table Partition " + DotNet.str(partitionName) + " is not used in pathformat."); // PORT: §3.14
    }

    public static Diagnostic getPartitionColumnCanNotBeUsedBothDirectlyAndPattern(String partitionName)
    {
        return new Diagnostic("KS265", "External Table Partition " + DotNet.str(partitionName) + " can be used with single pattern."); // PORT: §3.14
    }

    public static Diagnostic getWrongVirtualPartitionColumnType()
    {
        return new Diagnostic("KS266", "Virtual column could be either string or datetime.");
    }

    public static Diagnostic getWrongDataStreamType(String type)
    {
        return new Diagnostic("KS267", "Data stream type " + DotNet.str(type) + " is not supported by external table "); // PORT: §3.14
    }

    public static Diagnostic getWildcardedNamesNotAllowedInThisContext()
    {
        return new Diagnostic("KS268", "Wildcarded names not allowed in this context.");
    }

    public static Diagnostic getInlineExternalTableDeltaDataFormatNotSupported()
    {
        return new Diagnostic("KS269", "Inline external table kind=delta does not support dataformat.");
    }

    public static Diagnostic getInlineExternalTableDeltaPartitionByNotSupported()
    {
        return new Diagnostic("KS270", "Inline external table kind=delta does not support partition by.");
    }

    public static Diagnostic getInlineExternalTableDeltaPathFormatNotSupported()
    {
        return new Diagnostic("KS271", "Inline external table kind=delta does not support pathformat.");
    }

    public static Diagnostic getInlineExternalTableDeltaRequiresSingleRootUri()
    {
        return new Diagnostic("KS272", "Inline external table kind=delta requires exactly one root URI.");
    }

    public static Diagnostic getMultipleGraphOutputsRequireAliases()
    {
        return new Diagnostic("KS273", "Multiple graph outputs require aliases.");
    }

    // region command diagnostics
    public static Diagnostic getMissingCommand()
    {
        return new Diagnostic("KS300", "Missing command.");
    }
    // endregion
}
