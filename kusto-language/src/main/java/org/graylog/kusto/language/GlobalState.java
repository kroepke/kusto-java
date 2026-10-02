// Ported from: src/Kusto.Language/GlobalState.cs
// Upstream: microsoft/Kusto-Query-Language @ 9d95a2d5bb085d151f14e88e07b703755fd914e1
// SPDX-License-Identifier: Apache-2.0
// Upstream license: Apache-2.0, Copyright (c) 2019 Microsoft Corporation.
// This file is a derived work; see NOTICE. Modifications are marked "// PORT:".

package org.graylog.kusto.language;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.graylog.kusto.language.symbols.ClusterSymbol;
import org.graylog.kusto.language.symbols.ColumnSymbol;
import org.graylog.kusto.language.symbols.CommandSymbol;
import org.graylog.kusto.language.symbols.DatabaseSymbol;
import org.graylog.kusto.language.symbols.EntityGroupSymbol;
import org.graylog.kusto.language.symbols.FunctionSymbol;
import org.graylog.kusto.language.symbols.OperatorKind;
import org.graylog.kusto.language.symbols.OperatorSymbol;
import org.graylog.kusto.language.symbols.OptionSymbol;
import org.graylog.kusto.language.symbols.ParameterSymbol;
import org.graylog.kusto.language.symbols.Symbol;
import org.graylog.kusto.language.symbols.TableSymbol;
import org.graylog.kusto.language.utils.EmptyReadOnlyList;
import org.graylog.kusto.language.utils.Interlocked;
import org.graylog.kusto.language.utils.ListExtensions;
import org.graylog.kusto.language.utils.Optional;

/// <summary>
/// The global state that a kusto query is associated with.
/// </summary>
public final class GlobalState
{
    // PORT: §3.13 VarHandles stand in for `ref this.field` of the CAS-published lazy maps
    private static final VarHandle REVERSE_CLUSTER_MAP = Interlocked.handle(GlobalState.class, "reverseClusterMap", Map.class);
    private static final VarHandle REVERSE_DATABASE_MAP = Interlocked.handle(GlobalState.class, "reverseDatabaseMap", Map.class);
    private static final VarHandle REVERSE_TABLE_MAP = Interlocked.handle(GlobalState.class, "reverseTableMap", Map.class);
    private static final VarHandle FUNCTIONS_MAP = Interlocked.handle(GlobalState.class, "functionsMap", Map.class);
    private static final VarHandle AGGREGATES_MAP = Interlocked.handle(GlobalState.class, "aggregatesMap", Map.class);
    private static final VarHandle PLUGIN_MAP = Interlocked.handle(GlobalState.class, "pluginMap", Map.class);
    private static final VarHandle COMMAND_MAP = Interlocked.handle(GlobalState.class, "commandMap", Map.class);
    private static final VarHandle AMBIENT_SYMBOLS_MAP = Interlocked.handle(GlobalState.class, "ambientSymbolsMap", Map.class);
    private static final VarHandle CLIENT_SYMBOLS_MAP = Interlocked.handle(GlobalState.class, "clientSymbolsMap", Map.class);
    private static final VarHandle OPTION_MAP = Interlocked.handle(GlobalState.class, "optionMap", Map.class);
    private static final VarHandle PROPERTY_MAP = Interlocked.handle(GlobalState.class, "propertyMap", Map.class);
    private static final VarHandle AMBIENT_PARAMETERS = Interlocked.handle(GlobalState.class, "ambientParameters", List.class);
    private static final VarHandle S_DEFAULT = Interlocked.staticHandle(GlobalState.class, "s_default", GlobalState.class);

    /// <summary>
    /// Known clusters
    /// </summary>
    private final List<ClusterSymbol> clusters;
    public List<ClusterSymbol> clusters() { return this.clusters; }

    /// <summary>
    /// The default cluster.
    /// </summary>
    private final ClusterSymbol cluster;
    public ClusterSymbol cluster() { return this.cluster; }

    /// <summary>
    /// The default database.
    /// </summary>
    private final DatabaseSymbol database;
    public DatabaseSymbol database() { return this.database; }

    /// <summary>
    /// The default domain suffix
    /// </summary>
    private final String domain;
    public String domain() { return this.domain; }

    /// <summary>
    /// Known functions.
    /// </summary>
    private final List<FunctionSymbol> functions;
    public List<FunctionSymbol> functions() { return this.functions; }

    /// <summary>
    /// Known aggregates.
    /// </summary>
    private final List<FunctionSymbol> aggregates;
    public List<FunctionSymbol> aggregates() { return this.aggregates; }

    /// <summary>
    /// Known plug-ins.
    /// </summary>
    private final List<FunctionSymbol> plugIns;
    public List<FunctionSymbol> plugIns() { return this.plugIns; }

    /// <summary>
    /// Scalar operators
    /// </summary>
    private final List<OperatorSymbol> operators;
    public List<OperatorSymbol> operators() { return this.operators; }

    /// <summary>
    /// The kind of server that determines what set of control commands are available.
    /// </summary>
    private final String serverKind;
    public String serverKind() { return this.serverKind; }

    /// <summary>
    /// Symbols that are in scope but defined outside the query.
    /// </summary>
    private final List<Symbol> ambientSymbols;
    public List<Symbol> ambientSymbols() { return this.ambientSymbols; }

    /// <summary>
    /// Symbols for client parameters that appear as braced names in a query
    /// and are textually substituted by the client before execution.
    /// Client parameters do not require declared symbols to function.
    /// These symbols provide information for better semantic analysis.
    /// </summary>
    private final List<Symbol> clientSymbols;
    public List<Symbol> clientSymbols() { return this.clientSymbols; }

    /// <summary>
    /// Known query options
    /// </summary>
    private final List<OptionSymbol> options;
    public List<OptionSymbol> options() { return this.options; }

    /// <summary>
    /// All the properties and their values
    /// </summary>
    private final List<PropertyAndValue> properties;
    private List<PropertyAndValue> properties() { return this.properties; }

    /// <summary>
    /// The <see cref="KustoCache"/> used to store additional accumulated global state.
    /// If caching is not enabled for this <see cref="GlobalState"/> this property will return null.
    /// </summary>
    private final KustoCache cache;
    public KustoCache cache() { return this.cache; }

    /// <summary>
    /// Options to determine parsing behavior.
    /// </summary>
    private final ParseOptions parseOptions;
    public ParseOptions parseOptions() { return this.parseOptions; }

    // PORT: §3.13 the lazily built lookup maps are CAS-published, hence volatile
    /// <summary>
    /// Name to aggregate lookup map
    /// </summary>
    private volatile Map<String, FunctionSymbol> aggregatesMap;

    /// <summary>
    /// Name to function lookup map
    /// </summary>
    private volatile Map<String, FunctionSymbol> functionsMap;

    /// <summary>
    /// Name to plugin lookup map
    /// </summary>
    private volatile Map<String, FunctionSymbol> pluginMap;

    /// <summary>
    /// Name to operator lookup map
    /// </summary>
    private volatile Map<OperatorKind, OperatorSymbol> operatorMap;

    /// <summary>
    /// Name to command lookup map
    /// </summary>
    private volatile Map<String, CommandSymbol> commandMap;

    /// <summary>
    /// Name to <see cref="OptionSymbol"/> lookup map
    /// </summary>
    private volatile Map<String, OptionSymbol> optionMap;

    /// <summary>
    /// Name to ambient <see cref="Symbol"/> lookup map.
    /// </summary>
    private volatile Map<String, Symbol> ambientSymbolsMap;

    /// <summary>
    /// Name to client <see cref="Symbol"/> lookup map.
    /// </summary>
    private volatile Map<String, Symbol> clientSymbolsMap;

    /// <summary>
    /// Name to <see cref="GlobalState"/> lookup map
    /// </summary>
    private volatile Map<GlobalStateProperty, Object> propertyMap;

    /// <summary>
    /// Symbol (database) to <see cref="ClusterSymbol"/> reverse lookup map
    /// </summary>
    private volatile Map<Symbol, ClusterSymbol> reverseClusterMap;

    /// <summary>
    /// Symbol to <see cref="DatabaseSymbol"/> reverse lookup map
    /// </summary>
    private volatile Map<Symbol, DatabaseSymbol> reverseDatabaseMap;

    /// <summary>
    /// Column to <see cref="TableSymbol"/> reverse lookup map
    /// </summary>
    private volatile Map<Symbol, TableSymbol> reverseTableMap;

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> instance.
    /// </summary>
    private GlobalState(
        String domain,
        List<ClusterSymbol> clusters,
        ClusterSymbol cluster,
        DatabaseSymbol database,
        List<FunctionSymbol> functions,
        List<FunctionSymbol> aggregates,
        List<FunctionSymbol> plugins,
        List<OperatorSymbol> operators,
        String serverKind,
        List<Symbol> ambientSymbols,
        List<Symbol> clientSymbols,
        List<OptionSymbol> options,
        List<PropertyAndValue> properties,
        KustoCache cache,
        ParseOptions parseOptions,
        Map<Symbol, ClusterSymbol> reverseClusterMap,
        Map<Symbol, DatabaseSymbol> reverseDatabaseMap,
        Map<Symbol, TableSymbol> reverseTableMap,
        Map<String, FunctionSymbol> functionsMap,
        Map<String, FunctionSymbol> aggregatesMap,
        Map<String, FunctionSymbol> pluginMap,
        Map<OperatorKind, OperatorSymbol> operatorMap,
        Map<String, CommandSymbol> commandMap,
        Map<String, Symbol> ambientSymbolsMap,
        Map<String, Symbol> clientSymbolsMap,
        Map<String, OptionSymbol> optionMap,
        Map<GlobalStateProperty, Object> propertyMap)
    {
        this.domain = domain != null ? domain : KustoFacts.KustoWindowsNet; // PORT: §3.14 ??
        this.clusters = clusters != null ? clusters : EmptyReadOnlyList.<ClusterSymbol>instance();
        this.cluster = cluster != null ? cluster : ClusterSymbol.Unknown;
        this.database = database != null ? database : DatabaseSymbol.Unknown;
        this.functions = functions != null ? functions : EmptyReadOnlyList.<FunctionSymbol>instance();
        this.aggregates = aggregates != null ? aggregates : EmptyReadOnlyList.<FunctionSymbol>instance();
        this.plugIns = plugins != null ? plugins : EmptyReadOnlyList.<FunctionSymbol>instance();
        this.operators = operators != null ? operators : EmptyReadOnlyList.<OperatorSymbol>instance();
        this.serverKind = serverKind != null ? serverKind : ServerKinds.Engine;
        this.ambientSymbols = ambientSymbols != null ? ambientSymbols : EmptyReadOnlyList.<Symbol>instance();
        this.clientSymbols = clientSymbols != null ? clientSymbols : EmptyReadOnlyList.<Symbol>instance();
        this.options = options != null ? options : EmptyReadOnlyList.<OptionSymbol>instance();
        this.properties = properties != null ? properties : EmptyReadOnlyList.<PropertyAndValue>instance();
        this.cache = cache != null ? cache.withGlobals(this) : null;
        this.parseOptions = parseOptions != null ? parseOptions : ParseOptions.Default;
        this.reverseClusterMap = reverseClusterMap;
        this.reverseDatabaseMap = reverseDatabaseMap;
        this.reverseTableMap = reverseTableMap;
        this.functionsMap = functionsMap;
        this.aggregatesMap = aggregatesMap;
        this.pluginMap = pluginMap;
        this.operatorMap = operatorMap;
        this.commandMap = commandMap;
        this.ambientSymbolsMap = ambientSymbolsMap;
        this.clientSymbolsMap = clientSymbolsMap;
        this.optionMap = optionMap;
        this.propertyMap = propertyMap;
    }

    /// <summary>
    /// Makes a new instance of this <see cref="GlobalState"/> that contains the same content,
    /// but possibly clears the cache.
    /// </summary>
    public GlobalState copy()
    {
        return new GlobalState(
            this.domain(),
            this.clusters(),
            this.cluster(),
            this.database(),
            this.functions(),
            this.aggregates(),
            this.plugIns(),
            this.operators(),
            this.serverKind(),
            this.ambientSymbols(),
            this.clientSymbols(),
            this.options(),
            this.properties(),
            this.cache(),
            this.parseOptions(),
            this.reverseClusterMap,
            this.reverseDatabaseMap,
            this.reverseTableMap,
            this.functionsMap,
            this.aggregatesMap,
            this.pluginMap,
            this.operatorMap,
            this.commandMap,
            this.ambientSymbolsMap,
            this.clientSymbolsMap,
            this.optionMap,
            this.propertyMap);
    }

    /// <summary>
    /// Conditionally creates a new instance of a <see cref="GlobalState"/> if one of the 
    /// optional arguments is different than the current corresponding value.
    /// </summary>
    // PORT: §3.12 only the full signature is kept: every caller names its arguments; skipped ones pass Optional.NONE()
    private GlobalState with(
        Optional<String> domain,
        Optional<List<ClusterSymbol>> clusters,
        Optional<ClusterSymbol> cluster,
        Optional<DatabaseSymbol> database,
        Optional<List<FunctionSymbol>> functions,
        Optional<List<FunctionSymbol>> aggregates,
        Optional<List<FunctionSymbol>> plugins,
        Optional<List<OperatorSymbol>> operators,
        Optional<String> serverKind,
        Optional<List<Symbol>> ambientSymbols,
        Optional<List<Symbol>> clientSymbols,
        Optional<List<OptionSymbol>> options,
        Optional<List<PropertyAndValue>> properties,
        Optional<KustoCache> cache,
        Optional<ParseOptions> parseOptions)
    {
        var useDomain = domain.hasValue() ? domain.value() : this.domain();
        var useClusters = clusters.hasValue() ? clusters.value() : this.clusters();
        var useCluster = cluster.hasValue() ? cluster.value() : this.cluster();
        var useDatabase = database.hasValue() ? database.value() : this.database();
        var useFunctions = functions.hasValue() ? functions.value() : this.functions();
        var useAggregates = aggregates.hasValue() ? aggregates.value() : this.aggregates();
        var usePlugins = plugins.hasValue() ? plugins.value() : this.plugIns();
        var useOperators = operators.hasValue() ? operators.value() : this.operators();
        var useServerKind = serverKind.hasValue() ? serverKind.value() : this.serverKind();
        var useAmbientSymbols = ambientSymbols.hasValue() ? ambientSymbols.value() : this.ambientSymbols();
        var useClientSymbols = clientSymbols.hasValue() ? clientSymbols.value() : this.clientSymbols();
        var useOptions = options.hasValue() ? options.value() : this.options();
        var useProperties = properties.hasValue() ? properties.value() : this.properties();
        var useCache = cache.hasValue() ? cache.value() : this.cache();
        var useParseOptions = parseOptions.hasValue() ? parseOptions.value() : this.parseOptions();

        if (!Objects.equals(useDomain, this.domain()) // PORT: §3.14 string != string is a value comparison
            || useClusters != this.clusters()
            || useCluster != this.cluster()
            || useDatabase != this.database()
            || useFunctions != this.functions()
            || useAggregates != this.aggregates()
            || usePlugins != this.plugIns()
            || useOperators != this.operators()
            || !Objects.equals(useServerKind, this.serverKind()) // PORT: §3.14
            || useAmbientSymbols != this.ambientSymbols()
            || useClientSymbols != this.clientSymbols()
            || useOptions != this.options()
            || useProperties != this.properties()
            || useCache != this.cache()
            || useParseOptions != this.parseOptions())
        {
            return new GlobalState(
                useDomain,
                useClusters,
                useCluster,
                useDatabase,
                useFunctions,
                useAggregates,
                usePlugins,
                useOperators,
                useServerKind,
                useAmbientSymbols,
                useClientSymbols,
                useOptions,
                useProperties,
                useCache,
                useParseOptions,
                useClusters == this.clusters() ? this.reverseClusterMap : null,
                useClusters == this.clusters() ? this.reverseDatabaseMap : null,
                useClusters == this.clusters() ? this.reverseTableMap : null,
                useFunctions == this.functions() ? this.functionsMap : null,
                useAggregates == this.aggregates() ? this.aggregatesMap : null,
                usePlugins == this.plugIns() ? this.pluginMap : null,
                useOperators == this.operators() ? this.operatorMap : null,
                Objects.equals(useServerKind, this.serverKind()) ? this.commandMap : null, // PORT: §3.14
                useAmbientSymbols == this.ambientSymbols() ? this.ambientSymbolsMap : null,
                useClientSymbols == this.clientSymbols() ? this.clientSymbolsMap : null,
                useOptions == this.options() ? this.optionMap : null,
                useProperties == this.properties() ? this.propertyMap : null);
        }
        else
        {
            return this;
        }
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with caching enabled.
    /// </summary>
    public GlobalState withCache()
    {
        if (this.cache() != null)
        {
            return this;
        }
        else
        {
            return with(Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(),
                Optional.of(new KustoCache(this)), // PORT: §3.12 cache: new KustoCache(this)
                Optional.NONE());
        }
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the specified <see cref="ParseOptions"/>.
    /// </summary>
    public GlobalState withParseOptions(ParseOptions parseOptions)
    {
        if (this.parseOptions() == parseOptions)
        {
            return this;
        }
        else
        {
            return with(Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(),
                Optional.of(parseOptions)); // PORT: §3.12 parseOptions: parseOptions
        }
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the specified cluster list.
    /// </summary>
    public GlobalState withClusterList(List<ClusterSymbol> clusters)
    {
        if (this.clusters() == clusters)
        {
            return this;
        }
        else if (clusters == null)
        {
            return with(Optional.NONE(), optional(clusters), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE()).withCluster(ClusterSymbol.Unknown); // PORT: §3.12 clusters: Optional(clusters)
        }
        else
        {
            // change the set of clusters and update current cluster in case its symbol was updated
            // PORT: §3.6 clusters.FirstOrDefault(c => c.Name == this.Cluster.Name) ?? ClusterSymbol.Unknown
            ClusterSymbol newCluster = null;
            for (ClusterSymbol c : clusters)
            {
                if (Objects.equals(c.name(), this.cluster().name())) // PORT: §3.14
                {
                    newCluster = c;
                    break;
                }
            }

            if (newCluster == null)
            {
                newCluster = ClusterSymbol.Unknown;
            }

            return with(Optional.NONE(), optional(clusters), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE()).withCluster(newCluster); // PORT: §3.12 clusters: Optional(clusters)
        }
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the specified cluster list.
    /// </summary>
    public GlobalState withClusterList(ClusterSymbol... clusters)
    {
        return withClusterList(Arrays.asList(clusters)); // PORT: §3.17 (IReadOnlyList<ClusterSymbol>)clusters
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with either
    /// the cluster with the same name replaced with the new cluster
    /// or the new cluster added.
    /// </summary>
    public GlobalState addOrReplaceCluster(ClusterSymbol cluster)
    {
        if (cluster == ClusterSymbol.Unknown
            || cluster == this.cluster()
            || this.clusters().contains(cluster))
        {
            return this;
        }
        else
        {
            var newClusters = addOrReplace(this.clusters(), cluster);
            return withClusterList(newClusters);
        }
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the specified default cluster.
    /// </summary>
    public GlobalState withCluster(ClusterSymbol cluster)
    {
        if (this.cluster() == cluster)
        {
            return this;
        }
        else if (cluster == ClusterSymbol.Unknown || cluster == null)
        {
            return with(Optional.NONE(), Optional.NONE(), Optional.of(ClusterSymbol.Unknown), Optional.of(DatabaseSymbol.Unknown), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE()); // PORT: §3.12 cluster: ..., database: ...
        }
        else if (this.clusters().contains(cluster))
        {
            // this is a known cluster, so change current and try to set current database to one with same name
            var newDb = cluster.getDatabase(this.database().name());
            if (newDb == null)
            {
                newDb = DatabaseSymbol.Unknown; // PORT: §3.14 ??
            }

            return with(Optional.NONE(), Optional.NONE(), Optional.of(cluster), Optional.of(newDb), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE()); // PORT: §3.12 cluster: cluster, database: newDb
        }
        else
        {
            // add new cluster or replace existing cluster with same name
            var newClusters = addOrReplace(this.clusters(), cluster);
            return withClusterList(newClusters).withCluster(cluster);
        }
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the specified default cluster.
    /// </summary>
    public GlobalState withCluster(String clusterName)
    {
        var found = getCluster(clusterName);
        return withCluster(found != null ? found : ClusterSymbol.Unknown); // PORT: §3.14 ??
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the specified default domain suffix.
    /// </summary>
    public GlobalState withDomain(String domain)
    {
        if (domain != null && domain.length() > 0 // PORT: §3.14 !string.IsNullOrEmpty(domain)
            && domain.charAt(0) != '.')
        {
            domain = "." + domain;
        }

        return with(Optional.of(domain), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE()); // PORT: §3.12 domain: domain
    }

    private static <T extends Symbol> List<T> addOrReplace(List<T> list, T newElement)
    {
        // PORT: §3.6 list.FirstOrDefault(s => s.Name == newElement.Name)
        T existingElement = null;
        for (T s : list)
        {
            if (Objects.equals(s.name(), newElement.name())) // PORT: §3.14
            {
                existingElement = s;
                break;
            }
        }

        if (existingElement == newElement)
        {
            return list;
        }
        else
        {
            var newList = new ArrayList<T>(list);
            if (existingElement != null)
            {
                var index = newList.indexOf(existingElement);
                if (index >= 0)
                {
                    newList.set(index, newElement);
                }
                else
                {
                    newList.add(newElement);
                }
            }
            else
            {
                newList.add(newElement);
            }

            return newList;
        }
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the specified default database.
    /// </summary>
    public GlobalState withDatabase(DatabaseSymbol database)
    {
        database = database != null ? database : DatabaseSymbol.Unknown; // PORT: §3.14 ??

        if (this.database() == database)
        {
            return this;
        }
        else if (database == DatabaseSymbol.Unknown
            || this.cluster().databases().contains(database))
        {
            // same cluster, just change database
            return with(Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.of(database), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE()); // PORT: §3.12 database: database
        }
        else
        {
            // check if it is a database of some other known cluster
            var knownCluster = getCluster(database);
            if (knownCluster != null)
            {
                // changing the current database changes the current cluster too
                return with(Optional.NONE(), Optional.NONE(), Optional.of(knownCluster), Optional.of(database), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE()); // PORT: §3.12 cluster: knownCluster, database: database
            }
            else
            {
                // the database must be part of a known cluster, so add a cluster for it to be part of
                var cluster = new ClusterSymbol(database.name() + ":cluster", database);
                return withCluster(cluster).withDatabase(database);
            }
        }
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the specified default database.
    /// </summary>
    public GlobalState withDatabase(String databaseName)
    {
        var found = this.cluster().getDatabase(databaseName);
        return withDatabase(found != null ? found : DatabaseSymbol.Unknown); // PORT: §3.14 ??
    }

    /// <summary>
    /// True if the <see cref="TableSymbol"/> is part of one of the known databases.
    /// </summary>
    public boolean isDatabaseTable(TableSymbol table)
    {
        return getDatabase(table) != null;
    }

    /// <summary>
    /// True if the <see cref="FunctionSymbol"/> is part of one of the known databases.
    /// </summary>
    public boolean isDatabaseFunction(FunctionSymbol function)
    {
        return getDatabase(function) != null;
    }

    /// <summary>
    /// True if the <see cref="Symbol"/> is contained by one of the known databases.
    /// </summary>
    public boolean isDatabaseSymbol(Symbol symbol)
    {
        return getDatabase(symbol) != null;
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the specified functions.
    /// </summary>
    public GlobalState withFunctions(List<FunctionSymbol> functions)
    {
        return with(Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), optional(functions), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE()); // PORT: §3.12 functions: Optional(functions)
    }

    /// <summary>
    /// Gets the cluster given the short name or host name.
    /// </summary>
    public ClusterSymbol getCluster(String name)
    {
        if (name == null)
            return null;

        var hostName = KustoFacts.getHostName(name);
        name = hostName != null ? hostName : name; // PORT: §3.14 ??

        if (this.cluster() != ClusterSymbol.Unknown
            && (KustoFacts.isHostName(name, this.cluster().name())
                || (KustoFacts.isShortHostName(name, this.cluster().name(), this.domain()))))
        {
            return this.cluster();
        }

        // PORT: §3.6 this.Clusters.FirstOrDefault(c => IsHostName(...)) ?? this.Clusters.FirstOrDefault(c => IsShortHostName(...))
        for (ClusterSymbol c : this.clusters())
        {
            if (KustoFacts.isHostName(name, c.name()))
                return c;
        }

        for (ClusterSymbol c : this.clusters())
        {
            if (KustoFacts.isShortHostName(name, c.name(), this.domain()))
                return c;
        }

        return null;
    }

    /// <summary>
    /// Gets the <see cref="ClusterSymbol"/> that contains the <see cref="DatabaseSymbol"/>.
    /// </summary>
    public ClusterSymbol getCluster(DatabaseSymbol database)
    {
        if (database == null)
            return null;

        if (this.reverseClusterMap == null)
        {
            var map = new LinkedHashMap<Symbol, ClusterSymbol>(); // PORT: §3.17

            for (var cluster : this.clusters())
            {
                for (var member : cluster.members())
                {
                    map.put(member, cluster);
                }
            }

            Interlocked.compareExchange(REVERSE_CLUSTER_MAP, this, (Map<Symbol, ClusterSymbol>) map, null); // PORT: §3.13
        }

        return this.reverseClusterMap.get(database); // PORT: §3.3 TryGetValue; values never null
    }

    /// <summary>
    /// Gets the <see cref="DatabaseSymbol"/> that contains the <see cref="TableSymbol"/>.
    /// </summary>
    public DatabaseSymbol getDatabase(TableSymbol table)
    {
        return getDatabase((Symbol)table);
    }

    /// <summary>
    /// Gets the <see cref="DatabaseSymbol"/> that contains the <see cref="FunctionSymbol"/>.
    /// </summary>
    public DatabaseSymbol getDatabase(FunctionSymbol function)
    {
        return getDatabase((Symbol)function);
    }

    /// <summary>
    /// Gets the <see cref="DatabaseSymbol"/> that contains the <see cref="EntityGroupSymbol"/>.
    /// </summary>
    public DatabaseSymbol getDatabase(EntityGroupSymbol entityGroup)
    {
        return getDatabase((Symbol)entityGroup);
    }

    /// <summary>
    /// Gets the <see cref="DatabaseSymbol"/> that contains this <see cref="Symbol"/>
    /// </summary>
    public DatabaseSymbol getDatabase(Symbol symbol)
    {
        if (symbol == null)
            return null;

        if (this.reverseDatabaseMap == null)
        {
            var map = new LinkedHashMap<Symbol, DatabaseSymbol>(); // PORT: §3.17

            // PORT: §3.6 this.Clusters.SelectMany(c => c.Databases)
            for (var cluster : this.clusters())
            {
                for (var database : cluster.databases())
                {
                    for (var member : database.members())
                    {
                        map.put(member, database);
                    }
                }
            }

            Interlocked.compareExchange(REVERSE_DATABASE_MAP, this, (Map<Symbol, DatabaseSymbol>) map, null); // PORT: §3.13
        }

        return this.reverseDatabaseMap.get(symbol); // PORT: §3.3 TryGetValue; values never null
    }

    /// <summary>
    /// Gets the known database's <see cref="TableSymbol"/> that contains the <see cref="ColumnSymbol"/>.
    /// </summary>
    public TableSymbol getTable(ColumnSymbol column)
    {
        if (column == null)
            return null;

        if (this.reverseTableMap == null)
        {
            var map = new LinkedHashMap<Symbol, TableSymbol>(); // PORT: §3.17

            // PORT: §3.6 this.Clusters.SelectMany(c => c.Databases).SelectMany(d => d.Tables)
            for (var cluster : this.clusters())
            {
                for (var database : cluster.databases())
                {
                    for (var table : database.tables())
                    {
                        for (var col : table.columns())
                        {
                            map.put(col, table);
                        }
                    }
                }
            }

            Interlocked.compareExchange(REVERSE_TABLE_MAP, this, (Map<Symbol, TableSymbol>) map, null); // PORT: §3.13
        }

        return this.reverseTableMap.get(column); // PORT: §3.3 TryGetValue; values never null
    }

    /// <summary>
    /// Gets the function with the specified name, or null
    /// </summary>
    public FunctionSymbol getFunction(String name)
    {
        if (name == null)
            return null;

        if (this.functions().size() == 0)
            return null;

        if (this.functionsMap == null)
        {
            Map<String, FunctionSymbol> map = ListExtensions.toDictionaryLast(this.functions(), f -> f.name());
            Interlocked.compareExchange(FUNCTIONS_MAP, this, map, null); // PORT: §3.13
        }

        return this.functionsMap.get(name); // PORT: §3.3 TryGetValue; values never null
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the specified aggregates.
    /// </summary>
    public GlobalState withAggregates(List<FunctionSymbol> aggregates)
    {
        return with(Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), optional(aggregates), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE()); // PORT: §3.12 aggregates: Optional(aggregates)
    }

    /// <summary>
    /// Gets the aggregate with the specified name, or null.
    /// </summary>
    public FunctionSymbol getAggregate(String name)
    {
        if (name == null)
            return null;

        if (this.aggregates().size() == 0)
            return null;

        if (this.aggregatesMap == null)
        {
            Map<String, FunctionSymbol> map = ListExtensions.toDictionaryLast(this.aggregates(), f -> f.name());
            Interlocked.compareExchange(AGGREGATES_MAP, this, map, null); // PORT: §3.13
        }

        return this.aggregatesMap.get(name); // PORT: §3.3 TryGetValue; values never null
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the specified plug-ins.
    /// </summary>
    public GlobalState withPlugIns(List<FunctionSymbol> plugins)
    {
        return with(Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), optional(plugins), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE()); // PORT: §3.12 plugins: Optional(plugins)
    }

    /// <summary>
    /// Gets the plug-in with the specified name, or null.
    /// </summary>
    /// <param name="name"></param>
    /// <returns></returns>
    public FunctionSymbol getPlugIn(String name)
    {
        if (name == null || this.plugIns().size() == 0)
            return null;

        if (this.pluginMap == null)
        {
            Map<String, FunctionSymbol> map = ListExtensions.toDictionaryLast(this.plugIns(), f -> f.name());
            Interlocked.compareExchange(PLUGIN_MAP, this, map, null); // PORT: §3.13
        }

        return this.pluginMap.get(name); // PORT: §3.3 TryGetValue; values never null
    }

    /// <summary>
    /// True if the function is a known aggregate.
    /// </summary>
    public boolean isAggregateFunction(FunctionSymbol fn)
    {
        return fn != null 
            && getAggregate(fn.name()) == fn;
    }

    /// <summary>
    /// True if the function is a known built-in function.
    /// </summary>
    public boolean isBuiltInFunction(FunctionSymbol fn)
    {
        if (fn == null)
            return false;

        return getFunction(fn.name()) == fn
            || getAggregate(fn.name()) == fn
            || getPlugIn(fn.name()) == fn;
    }

    public boolean isBuiltInFunctionName(String functionName)
    {
        if (functionName == null || functionName.length() == 0) // PORT: §3.14 string.IsNullOrEmpty
            return false;

        // PORT: §3.14 GetFunction(functionName)?.Name == functionName
        var fn = getFunction(functionName);
        if (fn != null && Objects.equals(fn.name(), functionName))
            return true;

        var agg = getAggregate(functionName);
        if (agg != null && Objects.equals(agg.name(), functionName))
            return true;

        var plugIn = getPlugIn(functionName);
        return plugIn != null && Objects.equals(plugIn.name(), functionName);
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the specified operators.
    /// </summary>
    public GlobalState withOperators(List<OperatorSymbol> operators)
    {
        return with(Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), optional(operators), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE()); // PORT: §3.12 operators: Optional(operators)
    }

    /// <summary>
    /// Gets the built-in operator symbol for the corresponding argument types.
    /// </summary>
    public OperatorSymbol getOperator(OperatorKind kind)
    {
        if (this.operators().size() == 0)
            return null;

        if (this.operatorMap == null)
        {
            this.operatorMap = ListExtensions.toDictionaryLast(this.operators(), o -> o.operatorKind());
        }

        return this.operatorMap.get(kind); // PORT: §3.3 TryGetValue; values never null
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the specified server kind <see cref="ServerKinds"/>.
    /// </summary>
    public GlobalState withServerKind(String serverKind)
    {
        return with(Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.of(serverKind), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE()); // PORT: §3.12 serverKind: serverKind (implicit Optional conversion, §3.8)
    }

    /// <summary>
    /// Gets a <see cref="CommandSymbol"/> given its name.
    /// </summary>
    public CommandSymbol getCommand(String name)
    {
        if (name == null)
            return null;

        if (this.commandMap == null)
        {
            var commands = getCommands(this.serverKind());

            var map = new LinkedHashMap<String, CommandSymbol>(commands.size()); // PORT: §3.17
            for (var c : commands)
            {
                map.put(c.name(), c);
            }

            Interlocked.compareExchange(COMMAND_MAP, this, (Map<String, CommandSymbol>) map, null); // PORT: §3.13
        }

        return this.commandMap.get(name); // PORT: §3.3 TryGetValue; values never null
    }

    private static List<CommandSymbol> getCommands(String serverKind)
    {
        switch (serverKind)
        {
            case ServerKinds.Engine:
                return EngineCommands.All;
            case ServerKinds.DataManager:
                return DataManagerCommands.All;
            case ServerKinds.ClusterManager:
                return ClusterManagerCommands.All;
            case ServerKinds.AriaBridge:
                return AriaBridgeCommands.All;
            default:
                return EmptyReadOnlyList.<CommandSymbol>instance();
        }
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the specified ambient symbols.
    /// </summary>
    public GlobalState withAmbientSymbols(List<Symbol> symbols)
    {
        return with(Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), optional(symbols), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE()); // PORT: §3.12 ambientSymbols: Optional(symbols)
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the additional ambient symbols.
    /// </summary>
    @SuppressWarnings("unchecked")
    public GlobalState addOrUpdateAmbientSymbols(List<? extends Symbol> symbols) // PORT: §3.10 covariance: List<? extends Symbol>
    {
        return withAmbientSymbols(ListExtensions.addOrUpdate(this.ambientSymbols(), (List<Symbol>)symbols, s -> s.name()));
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the additional ambient symbols.
    /// </summary>
    public GlobalState addOrUpdateAmbientSymbols(Symbol... symbols)
    {
        return addOrUpdateAmbientSymbols(Arrays.asList(symbols)); // PORT: §3.17 (IReadOnlyList<Symbol>)symbols
    }

    /// <summary>
    /// Gets the ambient <see cref="Symbol"/> given its name.
    /// </summary>
    public Symbol getAmbientSymbol(String name)
    {
        if (name == null || this.ambientSymbols().size() == 0)
            return null;

        if (this.ambientSymbolsMap == null)
        {
            Map<String, Symbol> map = ListExtensions.toDictionaryLast(this.ambientSymbols(), p -> p.name());
            Interlocked.compareExchange(AMBIENT_SYMBOLS_MAP, this, map, null); // PORT: §3.13
        }

        return this.ambientSymbolsMap.get(name); // PORT: §3.3 TryGetValue; values never null
    }

    /// <summary>
    /// Ambient parameters
    /// </summary>
    @Deprecated // [Obsolete("Use AmbientSymbols")]
    public List<ParameterSymbol> parameters()
    {
        if (this.ambientParameters == null)
        {
            // PORT: §3.6 this.AmbientSymbols.OfType<ParameterSymbol>().ToReadOnly()
            var list = new ArrayList<ParameterSymbol>();
            for (Symbol s : this.ambientSymbols())
            {
                if (s instanceof ParameterSymbol ps)
                {
                    list.add(ps);
                }
            }

            List<ParameterSymbol> parameters = ListExtensions.toReadOnly(list);
            Interlocked.compareExchange(AMBIENT_PARAMETERS, this, parameters, null); // PORT: §3.13
        }

        return this.ambientParameters;
    }

    private volatile List<ParameterSymbol> ambientParameters;

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the specified ambient parameters.
    /// </summary>
    @Deprecated // [Obsolete("Use WithAmbientSymbols")]
    public GlobalState withParameters(List<ParameterSymbol> parameters)
    {
        // PORT: §3.6 this.AmbientSymbols.Where(s => !(s is ParameterSymbol)).Concat(parameters).Where(s => s != null).ToReadOnly()
        var newList = new ArrayList<Symbol>();
        for (Symbol s : this.ambientSymbols())
        {
            if (!(s instanceof ParameterSymbol) && s != null)
            {
                newList.add(s);
            }
        }

        for (ParameterSymbol p : parameters)
        {
            if (p != null)
            {
                newList.add(p);
            }
        }

        return withAmbientSymbols(ListExtensions.toReadOnly(newList));
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the additional ambient parameters.
    /// </summary>
    @Deprecated // [Obsolete("Use AddOrUpdateAmbientSymbols")]
    public GlobalState addParameters(List<ParameterSymbol> parameters)
    {
        return addOrUpdateAmbientSymbols(parameters);
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the additional ambient parameters.
    /// </summary>
    @Deprecated // [Obsolete("Use AddOrUpdateAmbientSymbols")]
    public GlobalState addParameters(ParameterSymbol... parameters)
    {
        return addOrUpdateAmbientSymbols(Arrays.asList(parameters)); // PORT: §3.17 params array as IReadOnlyList
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the specified client parameter symbols.
    /// </summary>
    public GlobalState withClientSymbols(List<Symbol> symbols)
    {
        return with(Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), optional(symbols), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE()); // PORT: §3.12 clientSymbols: Optional(symbols)
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the specified client symbols added or updated.
    /// </summary>
    @SuppressWarnings("unchecked")
    public GlobalState addOrUpdateClientSymbols(List<? extends Symbol> symbols) // PORT: §3.10 covariance: List<? extends Symbol>
    {
        return withClientSymbols(ListExtensions.addOrUpdate(this.clientSymbols(), (List<Symbol>)symbols, s -> s.name()));
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the specified client symbols added or updated.
    /// </summary>
    public GlobalState addOrUpdateClientSymbols(Symbol... symbols)
    {
        return addOrUpdateClientSymbols(Arrays.asList(symbols)); // PORT: §3.17 (IReadOnlyList<Symbol>)symbols
    }

    /// <summary>
    /// Gets the client parameter <see cref="Symbol"/> given its name.
    /// </summary>
    public Symbol getClientSymbol(String name)
    {
        if (name == null || this.clientSymbols().size() == 0)
            return null;

        if (this.clientSymbolsMap == null)
        {
            Map<String, Symbol> map = ListExtensions.toDictionaryLast(this.clientSymbols(), p -> p.name());
            Interlocked.compareExchange(CLIENT_SYMBOLS_MAP, this, map, null); // PORT: §3.13
        }

        return this.clientSymbolsMap.get(name); // PORT: §3.3 TryGetValue; values never null
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> with the specified options.
    /// </summary>
    public GlobalState withOptions(List<OptionSymbol> options)
    {
        return with(Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), optional(options), Optional.NONE(), Optional.NONE(), Optional.NONE()); // PORT: §3.12 options: Optional(options)
    }

    /// <summary>
    /// Gets the <see cref="OptionSymbol"/> with the specified name, or null if none match.
    /// </summary>
    public OptionSymbol getOption(String name)
    {
        if (name == null)
            return null;

        if (this.optionMap == null)
        {
            var map = new LinkedHashMap<String, OptionSymbol>(); // PORT: §3.17
            for (var opt : this.options())
            {
                map.put(opt.name(), opt);
            }

            Interlocked.compareExchange(OPTION_MAP, this, (Map<String, OptionSymbol>) map, null); // PORT: §3.13
        }

        return this.optionMap.get(name); // PORT: §3.3 TryGetValue; values never null
    }

    private static class PropertyAndValue
    {
        private final GlobalStateProperty property;
        public GlobalStateProperty property() { return this.property; }

        private final Object value;
        public Object value() { return this.value; }

        public PropertyAndValue(GlobalStateProperty property, Object value)
        {
            this.property = property;
            this.value = value;
        }
    }

    /// <summary>
    /// Gets the value for the specified property
    /// </summary>
    @SuppressWarnings("unchecked")
    public <T> T getProperty(GlobalStateProperty1<T> property) // PORT: §2.4 GlobalStateProperty<T>
    {
        if (property == null)
            return null; // PORT: §3.10 default(T): null (a value-type T would be 0/false, but the caller cannot pass a null property meaningfully)

        if (this.properties().size() == 0)
        {
            return property.defaultValue();
        }

        if (this.propertyMap == null)
        {
            Map<GlobalStateProperty, Object> map = ListExtensions.toDictionaryLast(this.properties(), pv -> pv.property(), pv -> pv.value());
            Interlocked.compareExchange(PROPERTY_MAP, this, map, null); // PORT: §3.13
        }

        // PORT: §3.3 TryGetValue with a possibly-null stored value
        if (this.propertyMap.containsKey(property))
        {
            return (T)this.propertyMap.get(property);
        }
        else
        {
            return property.defaultValue();
        }
    }

    /// <summary>
    /// Constructs a new <see cref="GlobalState"/> instance with the property added or replaced.
    /// </summary>
    public <T> GlobalState withProperty(GlobalStateProperty1<T> property, T value) // PORT: §2.4 GlobalStateProperty<T>
    {
        if (property == null)
            return this;

        List<PropertyAndValue> list = null;

        boolean hasCurrentValue = false;
        Object currentValue = null; // PORT: §3.14 default(object)

        // look for existing property (w/o forcing map to be populated)
        var currentMap = this.propertyMap;
        if (currentMap != null)
        {
            hasCurrentValue = currentMap.containsKey(property); // PORT: §3.3 TryGetValue with a possibly-null stored value
            currentValue = currentMap.get(property);
        }
        else
        {
            // PORT: §3.6 this.Properties.FirstOrDefault(p => p.Property == property)
            PropertyAndValue currentPropAndValue = null;
            for (PropertyAndValue p : this.properties())
            {
                if (p.property() == property)
                {
                    currentPropAndValue = p;
                    break;
                }
            }

            if (currentPropAndValue != null)
            {
                hasCurrentValue = true;
                currentValue = currentPropAndValue.value();
            }
        }

        // if it already exists, replace it
        if (hasCurrentValue)
        {
            if (Objects.equals(currentValue, value)) // PORT: §3.14 object.Equals
            {
                // the same value already exists
                return this;
            }

            list = new ArrayList<PropertyAndValue>(this.properties());

            // PORT: §3.6 list.FindIndex(p => p.Property == property)
            var index = -1;
            for (int i = 0; i < list.size(); i++)
            {
                if (list.get(i).property() == property)
                {
                    index = i;
                    break;
                }
            }

            if (index >= 0)
            {
                list.set(index, new PropertyAndValue(property, value));
                return with(Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.of(Collections.unmodifiableList(list)), Optional.NONE(), Optional.NONE()); // PORT: §3.12 properties: list.AsReadOnly()
            }
        }

        // otherwise add it to the end
        if (list == null)
        {
            list = new ArrayList<PropertyAndValue>(this.properties());
        }

        list.add(new PropertyAndValue(property, value));
        return with(Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.NONE(), Optional.of(Collections.unmodifiableList(list)), Optional.NONE(), Optional.NONE()); // PORT: §3.12 properties: list.AsReadOnly()
    }

    private static <T> Optional<T> optional(T value) { return new Optional<T>(value); }

    private static volatile GlobalState s_default; // PORT: §3.13 CAS-published

    /// <summary>
    /// The default <see cref="GlobalState"/>
    /// </summary>
    // PORT: §2.3 property Default → default_() (keyword); §3.9 CAS-published lazy singleton, never a static initialiser
    public static GlobalState default_()
    {
        // initialize lazy, because other symbols may reference this default instance
        if (s_default == null)
        {
            var globals =
                new GlobalState(
                    KustoFacts.KustoWindowsNet,
                    EmptyReadOnlyList.<ClusterSymbol>instance(),
                    ClusterSymbol.Unknown,
                    DatabaseSymbol.Unknown,
                    Functions.All,
                    Aggregates.All,
                    PlugIns.All,
                    Operators.All,
                    ServerKinds.Engine,
                    EmptyReadOnlyList.<Symbol>instance(), // ambient parameters
                    EmptyReadOnlyList.<Symbol>instance(), // client parameters
                    Options.All,
                    EmptyReadOnlyList.<PropertyAndValue>instance(),
                    null, // cache
                    null, // parseOptions
                    null, // reverseClusterMap
                    null, // reverseDatabaseMap
                    null, // reverseTableMap
                    null, // functionsMap
                    null, // aggregatesMap
                    null, // pluginMap
                    null, // operatorMap
                    null, // commandMap
                    null, // ambientSymbolsMap
                    null, // clientSymbolsMap
                    null, // optionMap
                    null); // propertyMap
            Interlocked.compareExchange(S_DEFAULT, globals, (GlobalState)null); // PORT: §3.13
        }

        return s_default;
    }
}
