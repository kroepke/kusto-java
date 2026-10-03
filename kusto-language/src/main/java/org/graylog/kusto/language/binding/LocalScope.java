// Ported from: src/Kusto.Language/Binder/LocalScope.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language.binding;

import java.util.LinkedHashMap;
import java.util.List;

import org.graylog.kusto.language.symbols.Symbol;
import org.graylog.kusto.language.symbols.SymbolMatch;
import org.graylog.kusto.language.symbols.SymbolMatchExtensions;
import org.graylog.kusto.language.utils.dotnet.DotNetStrings;
import org.graylog.kusto.language.utils.dotnet.Internal;

/// <summary>
/// Models nested scoping for let variables and function parameters.
/// </summary>
@Internal
public class LocalScope
{
    private final LocalScope _outerScope;
    private LinkedHashMap<String, Symbol> _symbols; // PORT: §3.17 Dictionary -> LinkedHashMap
    private LocalScope _sharedSymbols;

    private LocalScope(LinkedHashMap<String, Symbol> symbols, LocalScope outerScope, LocalScope sharedScope)
    {
        _symbols = symbols;
        _outerScope = getMinimalOuterScope(outerScope);
        _sharedSymbols = sharedScope;
    }

    private static LocalScope getMinimalOuterScope(LocalScope outerScope)
    {
        while (outerScope != null
            && outerScope._symbols == null
            && outerScope._sharedSymbols == null)
        {
            outerScope = outerScope._outerScope;
        }

        return outerScope;
    }

    /// <summary>
    /// Create a new instance of a <see cref="LocalScope"/>
    /// </summary>
    /// <param name="outerScope">An optional outer scope.</param>
    public LocalScope(LocalScope outerScope)
    {
        this(null, outerScope, null);
    }

    public LocalScope() // PORT: §3.12 outerScope = null
    {
        this((LocalScope) null);
    }

    /// <summary>
    /// Returns true if this scope has a matching symbol.
    /// </summary>
    public boolean hasSymbol(String name)
    {
        return (_symbols != null && _symbols.containsKey(name))
            || (_sharedSymbols != null && _sharedSymbols.hasSymbol(name));
    }

    /// <summary>
    /// Returns true if this scope has a matching symbol.
    /// </summary>
    public boolean hasSymbol(String name, int match) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        Symbol symbol = _symbols != null ? _symbols.get(name) : null; // PORT: §3.3 TryGetValue
        return (symbol != null && SymbolMatchExtensions.matches(symbol, match))
            || (_sharedSymbols != null && _sharedSymbols.hasSymbol(name, match));
    }

    /// <summary>
    /// Returns true if this scope has a matching symbol.
    /// </summary>
    public boolean hasSymbol(int match) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        // PORT: §3.6 _symbols.Values.Any(s => s.Matches(match))
        boolean any = false;
        if (_symbols != null)
        {
            for (var s : _symbols.values())
            {
                if (SymbolMatchExtensions.matches(s, match))
                {
                    any = true;
                    break;
                }
            }
        }

        return any
            || (_sharedSymbols != null && _sharedSymbols.hasSymbol(match));
    }

    /// <summary>
    /// Returns true if this scope or any outer scope has a matching symbol.
    /// </summary>
    public boolean containsSymbol(String name)
    {
        var scope = this;
        while (scope != null)
        {
            if (scope.hasSymbol(name))
                return true;
            scope = scope._outerScope;
        }

        return false;
    }

    /// <summary>
    /// Returns true if this scope or any outer scope has a matching symbol.
    /// </summary>
    public boolean containsSymbol(String name, int match) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        var scope = this;
        while (scope != null)
        {
            if (scope.hasSymbol(name, match))
                return true;
            scope = scope._outerScope;
        }

        return false;
    }

    /// <summary>
    /// Returns true if this scope or any outer scope has a matching symbol.
    /// </summary>
    public boolean containsSymbol(int match) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        var scope = this;
        while (scope != null)
        {
            if (scope.hasSymbol(match))
                return true;
            scope = scope._outerScope;
        }

        return false;
    }

    /// <summary>
    /// Makes a copy of this <see cref="LocalScope"/>.
    /// </summary>
    public LocalScope copy()
    {
        // if we have any non-shared symbols, then move them into shared chain of symbols that
        // can be shared w/o fear of modification (basically copy-on-fear-of-writing).
        if (_symbols != null && _symbols.size() > 0)
        {
            _sharedSymbols = new LocalScope(_symbols, null, _sharedSymbols);
            _symbols = null;
        }

        return new LocalScope(null, _outerScope, _sharedSymbols);
    }

    /// <summary>
    /// Add a <see cref="Symbol"/> to the <see cref="LocalScope"/>
    /// </summary>
    public boolean addSymbol(Symbol symbol)
    {
        if (symbol != null)
        {
            if (_symbols == null)
            {
                _symbols = new LinkedHashMap<String, Symbol>();
            }

            _symbols.put(symbol.name(), symbol);

            if (!DotNetStrings.isNullOrEmpty(symbol.alternateName()))
            {
                _symbols.put(symbol.alternateName(), symbol);
            }

            return true;
        }

        return false;
    }

    /// <summary>
    /// Adds a collection of <see cref="Symbol"/> to the <see cref="LocalScope"/>.
    /// </summary>
    public void addSymbols(Iterable<? extends Symbol> symbols)
    {
        for (var symbol : symbols)
        {
            addSymbol(symbol);
        }
    }

    /// <summary>
    /// Gets all the matching symbols in this scope and then any outer scopes.
    /// If any named matches are found in this scope, all other named matches from outer scopes are ignored.
    /// </summary>
    public void getSymbols(String name, int match, List<Symbol> symbols) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        var originalCount = symbols.size();

        if (_symbols != null)
        {
            if (name != null)
            {
                Symbol decl = _symbols.get(name); // PORT: §3.3 TryGetValue
                if (decl != null && SymbolMatchExtensions.matches(decl, name, match))
                {
                    symbols.add(decl);
                }
            }
            else
            {
                for (var symbol : _symbols.values())
                {
                    if (SymbolMatchExtensions.matches(symbol, match))
                    {
                        symbols.add(symbol);
                    }
                }
            }
        }

        if (_sharedSymbols != null
            && (name == null || symbols.size() == originalCount))
        {
            _sharedSymbols.getSymbols(name, match, symbols);
        }

        if (_outerScope != null
            && (name == null || symbols.size() == originalCount))
        {
            _outerScope.getSymbols(name, match, symbols);
        }
    }

    /// <summary>
    /// Gets all the matching symbols in this scope and then any outer scopes.
    /// If any named matches are found in this scope, all other named matches from outer scopes are ignored.
    /// </summary>
    public void getSymbols(String name, List<Symbol> symbols)
    {
        getSymbols(name, SymbolMatch.Any, symbols);
    }

    /// <summary>
    /// Gets all the matching symbols in the scope, and then from any outer scopes.
    /// If any named matches are found in this scope, all other named matches from outer scopes are ignored.
    /// </summary>
    public void getSymbols(int match, List<Symbol> symbols) // PORT: §3.17 SymbolMatch is an int holder (D23)
    {
        getSymbols(null, match, symbols);
    }
}
