// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for the GlobalState port (immutability, lazy default, withers, lookups).

package org.graylog.kusto.language;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.graylog.kusto.language.symbols.ClusterSymbol;
import org.graylog.kusto.language.symbols.ColumnSymbol;
import org.graylog.kusto.language.symbols.DatabaseSymbol;
import org.graylog.kusto.language.symbols.FunctionSymbol;
import org.graylog.kusto.language.symbols.OperatorSymbol;
import org.graylog.kusto.language.symbols.ScalarTypes;
import org.graylog.kusto.language.symbols.Symbol;
import org.graylog.kusto.language.symbols.TableSymbol;
import org.junit.jupiter.api.Test;

class GlobalStateTest {
    // PORT: tests build columns directly (all long) rather than via TableSymbol(String, String schema)
    private static TableSymbol table(String name, String... columns) {
        List<ColumnSymbol> cols = new ArrayList<>();
        for (String c : columns) {
            cols.add(new ColumnSymbol(c, ScalarTypes.Long));
        }
        return new TableSymbol(name, cols);
    }

    private static final String DEFAULT_CLASS = "org.graylog.kusto.language.GlobalState";

    @Test
    void defaultIsASingleton() {
        GlobalState a = GlobalState.default_();
        assertNotNull(a);
        assertSame(a, GlobalState.default_());
    }

    @Test
    void defaultIsLazyAndCasPublished() throws Exception {
        // a fresh class loader sees a pristine GlobalState class: loading it must not build the default
        URL location = GlobalState.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader loader = new URLClassLoader(new URL[] {location}, null)) {
            Class<?> cls = Class.forName(DEFAULT_CLASS, true, loader);
            Field field = cls.getDeclaredField("s_default");
            field.setAccessible(true);
            assertNull(field.get(null), "class initialisation must not build the default");

            int threads = 8;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Object>> results = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    results.add(pool.submit(() -> {
                        start.await();
                        return cls.getMethod("default_").invoke(null);
                    }));
                }
                start.countDown();
                Object first = results.get(0).get();
                assertNotNull(first);
                for (Future<Object> f : results) {
                    assertSame(first, f.get());
                }
                assertSame(first, field.get(null));
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void defaultIsBuiltFromTheCatalogs() {
        GlobalState g = GlobalState.default_();
        assertSame(Functions.All, g.functions());
        assertSame(Aggregates.All, g.aggregates());
        assertSame(PlugIns.All, g.plugIns());
        assertSame(Operators.All, g.operators());
        assertSame(Options.All, g.options());
        assertEquals(KustoFacts.KustoWindowsNet, g.domain());
        assertEquals(ServerKinds.Engine, g.serverKind());
        assertSame(ClusterSymbol.Unknown, g.cluster());
        assertSame(DatabaseSymbol.Unknown, g.database());
        assertTrue(g.clusters().isEmpty());
        assertTrue(g.ambientSymbols().isEmpty());
        assertTrue(g.clientSymbols().isEmpty());
        assertNull(g.cache());
        assertSame(ParseOptions.Default, g.parseOptions());
    }

    @Test
    void builtInFunctionLookupByName() {
        GlobalState g = GlobalState.default_();
        for (FunctionSymbol f : Functions.All) {
            FunctionSymbol found = g.getFunction(f.name());
            assertNotNull(found, f.name());
            assertEquals(f.name(), found.name());
            assertTrue(g.isBuiltInFunction(found), f.name());
            assertTrue(g.isBuiltInFunctionName(f.name()), f.name());
        }
        for (FunctionSymbol f : Aggregates.All) {
            FunctionSymbol found = g.getAggregate(f.name());
            assertNotNull(found, f.name());
            assertTrue(g.isAggregateFunction(found), f.name());
            assertTrue(g.isBuiltInFunction(found), f.name());
        }
        for (FunctionSymbol f : PlugIns.All) {
            FunctionSymbol found = g.getPlugIn(f.name());
            assertNotNull(found, f.name());
            assertTrue(g.isBuiltInFunction(found), f.name());
        }
        assertNull(g.getFunction("no_such_function_xyz"));
        assertNull(g.getFunction(null));
        assertFalse(g.isBuiltInFunction(null));
        assertFalse(g.isBuiltInFunctionName(""));
        assertFalse(g.isBuiltInFunctionName(null));
    }

    @Test
    void operatorAndOptionLookup() {
        GlobalState g = GlobalState.default_();
        for (OperatorSymbol op : Operators.All) {
            assertNotNull(g.getOperator(op.operatorKind()));
        }
        assertNotNull(g.getOption(Options.All.get(0).name()));
        assertNull(g.getOption("no_such_option_xyz"));
        assertNull(g.getOption(null));
    }

    @Test
    void commandLookupUsesTheServerKind() {
        GlobalState g = GlobalState.default_();
        assertNull(g.getCommand(null));
        assertNull(g.getCommand(".no-such-command"));
        assertNull(g.withServerKind("Unknown").getCommand(".show"));
    }

    @Test
    void withDatabaseAddsAClusterAndMakesBothCurrent() {
        TableSymbol t = table("T", "a");
        DatabaseSymbol db = new DatabaseSymbol("db", t);
        GlobalState base = GlobalState.default_();
        GlobalState g = base.withDatabase(db);

        assertNotSame(base, g);
        assertSame(db, g.database());
        assertNotSame(ClusterSymbol.Unknown, g.cluster());
        assertEquals(1, g.clusters().size());
        assertSame(g.cluster(), g.clusters().get(0));
        assertSame(g.cluster(), g.getCluster(db));
        assertSame(db, g.getDatabase(t));
        assertSame(db, g.getDatabase((Symbol) t));
        assertSame(t, g.getTable(t.getColumn("a")));
        assertTrue(g.isDatabaseTable(t));
        assertTrue(g.isDatabaseSymbol(t));
        assertFalse(g.isDatabaseFunction(Functions.All.get(0)));

        // the original is untouched
        assertSame(DatabaseSymbol.Unknown, base.database());
        assertTrue(base.clusters().isEmpty());

        // setting the same value returns the same instance
        assertSame(g, g.withDatabase(db));
        assertSame(g, g.withCluster(g.cluster()));
        assertSame(g, g.withDatabase("db"));

        // null means unknown
        assertSame(DatabaseSymbol.Unknown, g.withDatabase((DatabaseSymbol) null).database());
        assertNull(g.getDatabase((Symbol) null));
        assertNull(g.getCluster((DatabaseSymbol) null));
        assertNull(g.getTable(null));
    }

    @Test
    void withClusterSelectsMatchingDatabaseByName() {
        DatabaseSymbol db1 = new DatabaseSymbol("db1", table("A", "x"));
        DatabaseSymbol db2 = new DatabaseSymbol("db2", table("B", "y"));
        ClusterSymbol c1 = new ClusterSymbol("c1", db1);
        ClusterSymbol c2 = new ClusterSymbol("c2", db1, db2);

        GlobalState g = GlobalState.default_().withClusterList(c1, c2).withCluster(c1);
        assertSame(c1, g.cluster());
        assertEquals(2, g.clusters().size());

        // switching cluster keeps the database with the same name, if the new cluster has it
        g = g.withDatabase(db1);
        assertSame(db1, g.database());
        GlobalState g2 = g.withCluster(c2);
        assertSame(c2, g2.cluster());
        assertEquals("db1", g2.database().name());

        // by name
        assertSame(c2, g.withCluster("c2").cluster());
        assertSame(DatabaseSymbol.Unknown, g.withCluster((ClusterSymbol) null).database());
        assertSame(ClusterSymbol.Unknown, g.withCluster((ClusterSymbol) null).cluster());
        assertSame(db2, g2.withDatabase("db2").database());
        assertSame(DatabaseSymbol.Unknown, g2.withDatabase("nope").database());
    }

    @Test
    void addOrReplaceCluster() {
        DatabaseSymbol db1 = new DatabaseSymbol("db1", table("A", "x"));
        DatabaseSymbol db1b = new DatabaseSymbol("db1", table("A", "x", "z"));
        ClusterSymbol c1 = new ClusterSymbol("c1", db1);
        ClusterSymbol c1b = new ClusterSymbol("c1", db1b);

        GlobalState g = GlobalState.default_().addOrReplaceCluster(c1);
        assertEquals(1, g.clusters().size());
        assertSame(g, g.addOrReplaceCluster(c1));
        assertSame(g, g.addOrReplaceCluster(ClusterSymbol.Unknown));

        GlobalState replaced = g.addOrReplaceCluster(c1b);
        assertEquals(1, replaced.clusters().size());
        assertSame(c1b, replaced.clusters().get(0));
        assertSame(c1, g.clusters().get(0));
    }

    @Test
    void getClusterByName() {
        ClusterSymbol c = new ClusterSymbol("mycluster.kusto.windows.net", new DatabaseSymbol("db"));
        GlobalState g = GlobalState.default_().withClusterList(c);
        assertSame(c, g.getCluster("mycluster.kusto.windows.net"));
        assertSame(c, g.getCluster("mycluster"));
        assertNull(g.getCluster("other"));
        assertNull(g.getCluster((String) null));
    }

    @Test
    void withDomain() {
        GlobalState g = GlobalState.default_();
        assertEquals(".example.net", g.withDomain("example.net").domain());
        assertEquals(".example.net", g.withDomain(".example.net").domain());
        assertSame(g, g.withDomain(KustoFacts.KustoWindowsNet));
    }

    @Test
    void functionAggregateAndPlugInWithers() {
        GlobalState g = GlobalState.default_();
        assertSame(g, g.withFunctions(g.functions()));
        GlobalState none = g.withFunctions(new ArrayList<FunctionSymbol>());
        assertNotSame(g, none);
        assertNull(none.getFunction("ago"));
        assertNotNull(g.getFunction("ago"));
        assertNull(g.withAggregates(new ArrayList<FunctionSymbol>()).getAggregate("count"));
        assertNull(g.withPlugIns(new ArrayList<FunctionSymbol>()).getPlugIn(PlugIns.All.get(0).name()));
        assertEquals(0, g.withOperators(new ArrayList<OperatorSymbol>()).operators().size());
    }

    @Test
    void ambientAndClientSymbols() {
        TableSymbol t = table("T1", "a");
        TableSymbol t2 = table("T2", "b");
        TableSymbol t1b = table("T1", "c");
        GlobalState g = GlobalState.default_().addOrUpdateAmbientSymbols(t, t2);
        assertEquals(2, g.ambientSymbols().size());
        assertSame(t, g.getAmbientSymbol("T1"));
        assertNull(g.getAmbientSymbol("nope"));
        assertNull(g.getAmbientSymbol(null));

        GlobalState g2 = g.addOrUpdateAmbientSymbols(t1b);
        assertEquals(2, g2.ambientSymbols().size());
        assertSame(t1b, g2.getAmbientSymbol("T1"));
        assertSame(t, g.getAmbientSymbol("T1"));

        GlobalState c = GlobalState.default_().addOrUpdateClientSymbols(t);
        assertSame(t, c.getClientSymbol("T1"));
        assertNull(c.getClientSymbol("T2"));
        assertTrue(c.ambientSymbols().isEmpty());
    }

    @Test
    void propertyGetAndWith() {
        GlobalState g = GlobalState.default_();
        assertEquals(500, g.getProperty(Properties.MaxAnalysisDepth));
        assertEquals(Boolean.FALSE, g.getProperty(Properties.AllowClientParameters));
        assertNull(g.getProperty(null));

        GlobalState g2 = g.withProperty(Properties.MaxAnalysisDepth, 7);
        assertNotSame(g, g2);
        assertEquals(7, g2.getProperty(Properties.MaxAnalysisDepth));
        assertEquals(500, g.getProperty(Properties.MaxAnalysisDepth));
        assertEquals(10, g2.getProperty(Properties.MaxCachedExpansions));

        // same value: same instance; new value: replaced in place
        assertSame(g2, g2.withProperty(Properties.MaxAnalysisDepth, 7));
        GlobalState g3 = g2.withProperty(Properties.MaxAnalysisDepth, 9);
        assertEquals(9, g3.getProperty(Properties.MaxAnalysisDepth));
        assertEquals(7, g2.getProperty(Properties.MaxAnalysisDepth));

        // a second property is appended, the first is kept
        GlobalState g4 = g3.withProperty(Properties.AllowClientParameters, Boolean.TRUE);
        assertEquals(9, g4.getProperty(Properties.MaxAnalysisDepth));
        assertEquals(Boolean.TRUE, g4.getProperty(Properties.AllowClientParameters));

        // lookups after the map was populated
        assertEquals(9, g4.getProperty(Properties.MaxAnalysisDepth));
        GlobalState g5 = g4.withProperty(Properties.AllowClientParameters, Boolean.FALSE);
        assertEquals(Boolean.FALSE, g5.getProperty(Properties.AllowClientParameters));
        assertEquals(9, g5.getProperty(Properties.MaxAnalysisDepth));

        // a custom property with a null default
        GlobalStateProperty1<String> custom = new GlobalStateProperty1<>("custom");
        assertNull(g.getProperty(custom));
        assertEquals("x", g.withProperty(custom, "x").getProperty(custom));
        assertEquals("custom", custom.name());
    }

    @Test
    void parseOptionsWither() {
        GlobalState g = GlobalState.default_();
        assertSame(g, g.withParseOptions(g.parseOptions()));

        ParseOptions opts = g.parseOptions().withAllowLiteralsWithLineBreaks(!g.parseOptions().allowLiteralsWithLineBreaks());
        assertNotSame(g.parseOptions(), opts);
        GlobalState g2 = g.withParseOptions(opts);
        assertNotSame(g, g2);
        assertSame(opts, g2.parseOptions());
        assertSame(g2, g2.withParseOptions(opts));
        // everything else is carried over
        assertSame(g.functions(), g2.functions());
        assertSame(g.getFunction("ago"), g2.getFunction("ago"));
    }

    @Test
    void copyKeepsContent() {
        GlobalState g = GlobalState.default_().withDomain("x.net");
        GlobalState c = g.copy();
        assertNotSame(g, c);
        assertEquals(g.domain(), c.domain());
        assertSame(g.functions(), c.functions());
        assertSame(g.parseOptions(), c.parseOptions());
    }
}
