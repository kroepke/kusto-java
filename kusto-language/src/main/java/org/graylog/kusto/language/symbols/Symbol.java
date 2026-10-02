// Ported from: src/Kusto.Language/Symbols/Symbol.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.symbols;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.graylog.kusto.language.utils.dotnet.Out;

// PORT: §3.20 [DebuggerDisplay] dropped
// PORT: §2.3 equality is reference identity upstream (no Equals/GetHashCode overrides); equals/hashCode are deliberately not overridden
public abstract class Symbol
{
    /// <summary>
    /// The text used to designate the symbol in the debugger
    /// </summary>
    @SuppressWarnings("unused")
    private String debugText() // PORT: §3.20 only read by the dropped [DebuggerDisplay]
    {
        return DebugDisplay.getText(this);
    }

    /// <summary>
    /// The name of the symbol.
    /// </summary>
    private final String name;
    public String name() { return name; }

    /// <summary>
    /// An alternate name of the symbol.
    /// </summary>
    public String alternateName() { return ""; }

    /// <summary>
    /// The <see cref="SymbolKind"/> of the symbol.
    /// </summary>
    public SymbolKind kind() { return SymbolKind.None; }

    protected Symbol(String name)
    {
        this.name = name != null ? name : ""; // PORT: §3.14
    }

    /// <summary>
    /// If true, the symbol is hidden from Intellisense.
    /// </summary>
    public boolean isHidden() { return this.name().startsWith("__"); } // symbols that start with __ are internal only.

    /// <summary>
    /// True if the symbol is an error symbol.
    /// </summary>
    public boolean isError() { return false; }

    /// <summary>
    /// Identifies whether the symbol is scalar or tabular.
    /// </summary>
    public Tabularity tabularity() { return Tabularity.Unknown; }

    /// <summary>
    /// True if the symbol is scalar or unknown.
    /// </summary>
    public boolean isScalar()
    {
        switch (this.tabularity())
        {
            case Scalar:
            case Unknown:
                return true;
            default:
                return false;
        }
    }

    /// <summary>
    /// True if the symbol is tabular or unknown.
    /// </summary>
    public boolean isTabular()
    {
        switch (this.tabularity())
        {
            case Tabular:
            case Unknown:
                return true;
            default:
                return false;
        }
    }

    /// <summary>
    /// All the symbols contained by this symbol.
    /// </summary>
    public List<Symbol> members() 
    {
        return EmptyReadOnlyList.instance(); // PORT: §3.9 EmptyReadOnlyList<Symbol>.Instance
    }

    /// <summary>
    /// Gets all the matching members.
    /// </summary>
    public void getMembers(String name, int match, List<Symbol> symbols, boolean ignoreCase) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        for (var symbol : this.members())
        {
            if (SymbolMatchExtensions.matches(symbol, name, match, ignoreCase)) // PORT: §3.5
            {
                symbols.add(symbol);
            }
        }
    }

    public void getMembers(String name, int match, List<Symbol> symbols) // PORT: §3.12 optional parameter ignoreCase = false
    {
        this.getMembers(name, match, symbols, false);
    }

    /// <summary>
    /// Gets all the matching members.
    /// </summary>
    public void getMembers(int match, List<Symbol> symbols, boolean ignoreCase) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        this.getMembers(null, match, symbols, ignoreCase);
    }

    public void getMembers(int match, List<Symbol> symbols) // PORT: §3.12 optional parameter ignoreCase = false
    {
        this.getMembers(match, symbols, false);
    }

    /// <summary>
    /// Returns the first member that matches or null.
    /// </summary>
    public Symbol getFirstMember(String name, int match, boolean ignoreCase) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        for (var symbol : this.members())
        {
            if (SymbolMatchExtensions.matches(symbol, name, match, ignoreCase)) // PORT: §3.5
            {
                return symbol;
            }
        }

        return null;
    }

    public Symbol getFirstMember(String name, int match) // PORT: §3.12 optional parameter ignoreCase = false
    {
        return this.getFirstMember(name, match, false);
    }

    public Symbol getFirstMember(String name) // PORT: §3.12 optional parameter match = SymbolMatch.Any
    {
        return this.getFirstMember(name, SymbolMatch.Any);
    }

    /// <summary>
    /// Determines the result type of an expression that references the specified symbol
    /// </summary>
    public static TypeSymbol getResultType(Symbol symbol)
    {
        // PORT: §3.15 switch on type patterns → instanceof chain in the same order (null falls to default)
        if (symbol instanceof ColumnSymbol c)
        {
            return c.type();
        }
        else if (symbol instanceof VariableSymbol v)
        {
            return getResultType(v.type());
        }
        else if (symbol instanceof EntityGroupElementSymbol e)
        {
            return getResultType(e.underlyingSymbol());
        }
        else if (symbol instanceof ParameterSymbol p)
        {
            return p.type();
        }
        else if (symbol instanceof GroupSymbol g)
        {
            var resultSymbols = new ArrayList<Symbol>();

            for (var m : g.members())
            {
                var rs = getResultType(m);
                if (rs != null)
                {
                    resultSymbols.add(rs);
                }
            }

            if (resultSymbols.size() == 1)
            {
                return resultSymbols.get(0) instanceof TypeSymbol ts ? ts : null; // PORT: §3.15 `as TypeSymbol`
            }
            else if (resultSymbols.size() > 1)
            {
                return new GroupSymbol(resultSymbols);
            }
            else
            {
                return null;
            }
        }
        else if (symbol instanceof TypeSymbol t)
        {
            return t;
        }
        else
        {
            return null;
        }
    }

    /// <summary>
    /// True if this symbol can be assigned to the specified type.
    /// </summary>
    public boolean isAssignableTo(Symbol targetType, Conversion allowedConversion)
    {
        return areAssignable(targetType, this, allowedConversion);
    }

    public boolean isAssignableTo(Symbol targetType) // PORT: §3.12 optional parameter allowedConversion = Conversion.None
    {
        return this.isAssignableTo(targetType, Conversion.None);
    }

    /// <summary>
    /// True if this symbol can be assigned to any of the specified types.
    /// </summary>
    public boolean isAssignableToAny(List<? extends TypeSymbol> targetTypes, Conversion allowedConversion) // PORT: §3.10 covariance
    {
        return areAssignable(targetTypes, this, allowedConversion);
    }

    public boolean isAssignableToAny(List<? extends TypeSymbol> targetTypes) // PORT: §3.12 optional parameter allowedConversion = Conversion.None
    {
        return this.isAssignableToAny(targetTypes, Conversion.None);
    }

    /// <summary>
    /// True if a value of type <see cref="P:sourceType"/> can be assigned to any types in <see cref="P:targetTypes"/>
    /// </summary>
    private static boolean areAssignable(List<? extends TypeSymbol> targetTypes, Symbol sourceType, Conversion allowedConversion) // PORT: §3.10 covariance
    {
        for (int i = 0; i < targetTypes.size(); i++)
        {
            if (areAssignable(targetTypes.get(i), sourceType, allowedConversion))
                return true;
        }

        return false;
    }

    @SuppressWarnings("unused")
    private static boolean areAssignable(List<? extends TypeSymbol> targetTypes, Symbol sourceType) // PORT: §3.12 optional parameter allowedConversion = Conversion.None
    {
        return areAssignable(targetTypes, sourceType, Conversion.None);
    }

    /// <summary>
    /// True if a value of type <see cref="P:sourceType"/> can be assigned to of type <see cref="P:targetType"/>
    /// </summary>
    private static boolean areAssignable(Symbol targetType, Symbol sourceType, Conversion allowedConversion)
    {
        if (targetType == sourceType)
            return true;

        if (targetType == null || sourceType == null)
            return false;

        if (sourceType == ScalarTypes.Unknown && targetType.isScalar())
            return true;

        if (targetType == ScalarTypes.Unknown && sourceType.isScalar())
            return true;

        // a single column tuple is assignable to a scalar
        if (targetType.isScalar()
            && sourceType.kind() == SymbolKind.Tuple 
            && sourceType instanceof TupleSymbol stt
            && stt.columns().size() == 1)
            return areAssignable(targetType, stt.columns().get(0).type());

        if (targetType == ScalarTypes.Dynamic)
        {
            if (sourceType instanceof DynamicSymbol)
                return true;

            if (sourceType instanceof ScalarSymbol
                && allowedConversion.compareTo(Conversion.Dynamic) >= 0) // PORT: §3.17 enum `>=` → compareTo (declaration order)
                return true;
        }

        if (targetType == ScalarTypes.DynamicArray
            && sourceType instanceof DynamicArraySymbol
            && allowedConversion != Conversion.None)
            return true;

        if (targetType == ScalarTypes.DynamicBag
            && sourceType instanceof DynamicBagSymbol
            && allowedConversion != Conversion.None)
            return true;

        if (targetType instanceof DynamicPrimitiveSymbol tp
            && sourceType instanceof DynamicPrimitiveSymbol sp)
            return areAssignable(tp.underlyingType(), sp.underlyingType(), allowedConversion);

        if (targetType.kind() != sourceType.kind())
            return false;

        // PORT: §3.15 switch on type patterns → instanceof chain in the same order
        if (targetType instanceof ColumnSymbol tarCol)
        {
            var srcCol = (ColumnSymbol)sourceType;
            return Objects.equals(tarCol.name(), srcCol.name()) && areAssignable(tarCol.type(), srcCol.type(), allowedConversion); // PORT: §3.14
        }
        else if (targetType instanceof TupleSymbol
            || targetType instanceof GroupSymbol) // PORT: §3.15 `case Type _:` fall-through stack
        {
            return areMembersEqual(targetType, sourceType);
        }
        else if (targetType instanceof TableSymbol tarTable)
        {
            return areTablesAssignable(tarTable, (TableSymbol)sourceType);
        }
        else if (targetType instanceof PrimitiveSymbol tarPrim)
        {
            var scrPrim = (ScalarSymbol)sourceType;

            switch (allowedConversion)
            {
                case Promotable:
                    return TypeFacts.isPromotableTo(scrPrim, tarPrim); // PORT: §3.5
                case Compatible:
                    return TypeFacts.isPromotableTo(scrPrim, tarPrim) // PORT: §3.5
                        || TypeFacts.isPromotableTo(tarPrim, scrPrim); // PORT: §3.5
                case Any:
                    return true;
                default:
                    return false;
            }
        }
        else if (targetType instanceof DynamicArraySymbol tarArray)
        {
            var srcArray = (DynamicArraySymbol)sourceType;
            return areAssignable(srcArray.elementType(), tarArray.elementType(), allowedConversion); // PORT-BUG: arguments are (source, target), reversed relative to every other recursive call (Symbol.cs:297); mirrored verbatim
        }
        else if (targetType instanceof DynamicBagSymbol tarBag)
        {
            var srcBag = (DynamicBagSymbol)sourceType;
            return areBagsAssignable(tarBag, srcBag);
        }

        return false;
    }

    private static boolean areAssignable(Symbol targetType, Symbol sourceType) // PORT: §3.12 optional parameter allowedConversion = Conversion.None
    {
        return areAssignable(targetType, sourceType, Conversion.None);
    }

    /// <summary>
    /// True if the members of the source type are assignable to the members of the target type.
    /// </summary>
    private static boolean areMembersEqual(Symbol target, Symbol source)
    {
        if (target.members().size() != source.members().size())
            return false;

        for (int i = 0, n = target.members().size(); i < n; i++)
        {
            if (!areAssignable(target.members().get(i), source.members().get(i)))
                return false;
        }

        return true;
    }

    /// <summary>
    /// True if a bag value can be assigned to a parameter of specific bag type.
    /// </summary>
    private static boolean areBagsAssignable(DynamicBagSymbol targetBag, DynamicBagSymbol sourceBag)
    {
        // ensure that the source bag has at least the properties specified of the target bag.

        for (var targetProperty : targetBag.properties())
        {
            Out<ColumnSymbol> sourceProperty = new Out<>(); // PORT: §3.3
            if (!sourceBag.tryGetProperty(targetProperty.name(), sourceProperty)
                || !areAssignable(targetProperty.type(), sourceProperty.value.type(), Conversion.Any))
            {
                return false;
            }
        }

        return true;
    }

    /// <summary>
    /// True if a table value can be assigned to a parameter of a specific table type.
    /// </summary>
    private static boolean areTablesAssignable(TableSymbol target, TableSymbol source)
    {
        // ensure that the value table has at least the columns specified for the parameter table.

        for (var targetColumn : target.columns())
        {
            Out<ColumnSymbol> sourceColumn = new Out<>(); // PORT: §3.3
            if (!source.tryGetColumn(targetColumn.name(), sourceColumn)
                || !areAssignable(targetColumn.type(), sourceColumn.value.type()))
            {
                return false;
            }
        }

        return true;
    }
}
