// Original to kusto-java (no upstream file). SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Graylog, Inc. Purpose: unit tests for Options (Options.cs); every option name, description hash, type and example per upstream.
package org.graylog.kusto.language.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.graylog.kusto.language.Options;
import org.junit.jupiter.api.Test;

class OptionsTest {
    // field, option name, SHA-256 of the UTF-8 description (verbatim from Options.cs), ScalarTypes members, examples joined by '|'
    private static final String[][] EXPECTED = {
        {"BestEffort", "best_effort", "85a57d3289c99267d6ac6fb0774cc064db7a654489a890d473eebd061b45c601", "Bool", ""},
        {"DebugPython", "query_python_debug", "2d9d9c390c14bbfce2ca1f35d96cb21f90f7cece693ac83f2af3db84c3aec403", "Bool,Int", ""},
        {"DeferPartialQueryFailures", "deferpartialqueryfailures", "040afb71913879fbffed7284b05455d7ceacbe56082d6bb812e58080d532339b", "Bool", ""},
        {"DoNotImpersonate", "request_impersonation_disabled", "b354e6f0329cb8d1d0adaa59534b5891d4b093c44406a1dc33a8c1bd3a0b5b78", "Bool", ""},
        {"ErrorReportingPlacement", "results_error_reporting_placement", "e3320d588c6eefd7b86e00ae4eb3b802ce67a1eedf2820a2601d7e86a140c6a1", "String", "'in_data'|'end_of_table'|'end_of_dataset'"},
        {"MaterializedViewShuffleQuery", "materialized_view_shuffle", "cec2a4c6ee91d527ebadda965651e0b9d4549ccafd20b1d13129027fc74c8552", "Dynamic", ""},
        {"MaxEntitiesToUnion", "query_max_entities_in_union", "ea49f0a573e40e5adce81b03b38bf3215d3f4e342dc3bcfe921db4dab7ea4eb0", "Long", ""},
        {"MaxMemoryConsumptionPerIterator", "maxmemoryconsumptionperiterator", "9384b30ee0b81c3af6ad27ddadec310112fc4ee051cd5c69a5c480e0c8f1aadd", "Long", ""},
        {"MaxMemoryConsumptionPerQueryPerNode", "max_memory_consumption_per_query_per_node", "478e19d2ac87fbdcf217d070a002f739a2fafa1087419df72c5311491196055e", "Long", ""},
        {"MaxOutputColumns", "maxoutputcolumns", "ea49f0a573e40e5adce81b03b38bf3215d3f4e342dc3bcfe921db4dab7ea4eb0", "Long", ""},
        {"NoRequestTimeout", "norequesttimeout", "52a85647e4e018267156632557729ff63510f0ba77849a327a29240c291b0a63", "Bool", ""},
        {"NoTruncation", "notruncation", "f73e4851228c043f019cf051f2b443458a3b7bf1b3a770ae16db2aa2c47cbb3a", "Bool", ""},
        {"ProgressiveProgressReportPeriod", "query_results_progressive_update_period", "1a151510ba6ee256a04a11500756cb6667a7a74e4d1c59c029968bba345f938f", "", ""},
        {"ProgressiveQueryMinRowCountPerUpdate", "query_results_progressive_row_count", "5aa0581b48ee067236851d41e81cd6ec8b5d259683c549e4e4e8d998ea1f210b", "", ""},
        {"PushSelectionThroughAggregation", "push_selection_through_aggregation", "b771ee08437a066f1954a794fddc9ca4a75d6d4a8fdc1a671a12c88201b77c1c", "Bool", ""},
        {"QueryBinAutoAt", "query_bin_auto_at", "015d7f9e33032305f8bd5919d800db944d73883530a32f2077034ef6ed26cb63", "", ""},
        {"QueryBinAutoSize", "query_bin_auto_size", "51946ab6ede50ac270059a7ce0c05d929d36d57284521e964ebc951e63d93982", "", ""},
        {"QueryCursorAfterDefault", "query_cursor_after_default", "feeeece849c519f3f19837b23293da9e57b6dc3164232012a1998a01d0e2ca65", "String", ""},
        {"QueryCursorBeforeOrAtDefault", "query_cursor_before_or_at_default", "c16b8dc1bd1f01411e6da06b5828c5eb3a5c61263057c8c46f46af37a9cd629d", "String", ""},
        {"QueryCursorCurrent", "query_cursor_current", "9811148fe2ede8246805958124894ea625972d2a0db80130d15b896798052d7b", "String", ""},
        {"QueryCursorDisabled", "query_cursor_disabled", "b49ee0edd4d48d43282d43af49c71fcf7f8a78d6af833dc7136e8a0742b6f7b0", "Bool", ""},
        {"QueryCursorScopedTables", "query_cursor_scoped_tables", "a0bc71bff9b235f6d942ac20c57be363c754b2baf6112a0e5849fcea3b0469be", "Dynamic", ""},
        {"QueryDataScope", "query_datascope", "256ab227b765833e3733b9006612f127f325a61e1b4e2f27509740106a0f7b03", "String", "'default'|'all'|'hotcache'"},
        {"QueryDateTimeScopeColumn", "query_datetimescope_column", "83be61bcca7743d2df496ac9581097f9c5f9b4901e8d12033df2c77be11514a8", "String", ""},
        {"QueryDateTimeScopeFrom", "query_datetimescope_from", "5e34b857ce1bb352dfe1a681eac3dd2ae7d24e4a9fe549364eaf657e9a6e06dc", "DateTime", ""},
        {"QueryDateTimeScopeTo", "query_datetimescope_to", "61160b5bf583254bb42e66437c6b851959432ba96c5dbddfc7e7cc1dabc4a10b", "DateTime", ""},
        {"QueryDistributionNodesSpanSize", "query_distribution_nodes_span", "126f9b042d253668d4e94e9bf6a6416d31d87746a64a138630dd8e989027b5f4", "Int", ""},
        {"QueryFanoutNodesPercent", "query_fanout_nodes_percent", "a6234cb284643aafc13847c7844d6529bba1bea214dd9803f9a8ce600c747471", "Int", ""},
        {"QueryFanoutThreadsPercent", "query_fanout_threads_percent", "ac33a55863b3aeedd48be3e43f31c8a13dfdcda305dfad377d69a16dca97aacf", "Int", ""},
        {"QueryForceRowLevelSecurity", "query_force_row_level_security", "b483676c7224525b3107698754bdf565ee0dcf77910467fb1b92b0caec40612f", "Bool", ""},
        {"QueryLanguage", "query_language", "52e469aec11ef7ec76743f2832c8c876903f635cfe8571e40c054c25810b8625", "String", "'csl'|'kql'|'sql'"},
        {"QueryLogQueryParameters", "query_log_query_parameters", "fc79cd0546aa3437e700d1f605b19e19b21e6285940646fcbdcda9161bf8c6b5", "Bool", ""},
        {"QueryNow", "query_now", "2585993441271827720c414ad736be86ee70383d851baf04e9f7bd99086c150a", "DateTime", ""},
        {"QueryResultsApplyGetSchema", "query_results_apply_getschema", "ef8b08a641d8f647b5573961b204255b60495c53b792e03c5b7e692fbaee5585", "Bool", ""},
        {"QueryResultsCacheForceRefresh", "query_results_cache_force_refresh", "064b8bd5e970fb03c799e0151529d6337ed2dd33fe1346048a004d5dec8b14b5", "Bool", ""},
        {"QueryResultsCacheMaxAge", "query_results_cache_max_age", "7726c17bf98c84141862a33725204a06c0e39f1356a157f602e9df48ff378847", "TimeSpan", ""},
        {"QueryResultsCachePerShardEnabled", "query_results_cache_per_shard", "d9af45942a757f90db3a5f7ecdd9415643479d086badeb11f0375228b3666b9e", "Bool", ""},
        {"QueryWeakConsistencySessionId", "query_weakconsistency_session_id", "6b23a6e922fdbf746bbffee650cb8cd28c5dbe777e89c48931589badbc1f2e51", "String", ""},
        {"RequestAppName", "request_app_name", "7af6d6a059a196da806c7b084f9504813ac6435fc8ee6d262ad7c2d3f5647537", "String", ""},
        {"RequestBlockRowLevelSecurity", "request_block_row_level_security", "e4f30a33cffaf2b3e9763daec81cc8665c7c47260c9f80b0d713efa756d77e27", "Bool", ""},
        {"RequestCalloutDisabled", "request_callout_disabled", "d3a8e007897df7d517ba63ffcaedcf199c2c4fd20d769b2a043d493bc2963a16", "Bool", ""},
        {"RequestCrossClusterToken", "request_cross_cluster_token", "5f246870b510b0a999bf000f6473572d9ede44cd69a8c760fffb9c6438cc3e4e", "String", ""},
        {"RequestDescription", "request_description", "b82c84905569cc5bc73eb83e5c2d6bef79d109ff861acc1da3e1389a0996899c", "String", ""},
        {"RequestExternalDataDisabled", "request_external_data_disabled", "1324763e63418ad20c394b4495bb768c068a790df80f93673d68014c80bb5cdd", "Bool", ""},
        {"RequestExternalTableDisabled", "request_external_table_disabled", "7dac69dfbbba528f572fd54d06ed61a3285924b6b5912438c05e54e554d39e0e", "Bool", ""},
        {"RequestReadOnly", "request_readonly", "052c4273a479478f27618762566544c9afec0c32d84a2529b535fc8946ecc088", "Bool", ""},
        {"RequestReadOnlyHardline", "request_readonly_hardline", "575d4961f29653d8177696970f713a576a65727c92d33d1b6540bc829cd664f8", "Bool", ""},
        {"RequestRemoteEntitiesDisabled", "request_remote_entities_disabled", "abee2fdee0fc0e942f42c78a56102531c07c31194ac8a45c92fc2148115c7cc9", "Bool", ""},
        {"RequestSandboxedExecutionDisabled", "request_sandboxed_execution_disabled", "0f867f4912d8c09f1fc4a55ab0b799f94778e9c1afa1a8d22270e19b39b9bd12", "Bool", ""},
        {"RequestUser", "request_user", "64baf618106e32c54285b61bdda6c7bde19f7cf10f579dadaad496579611de6c", "String", ""},
        {"ResultsProgressiveEnabled", "results_progressive_enabled", "3c4e1d4728be1dcdab4d200b5737457e8a48a20756c991ac5da4fda20533ecf3", "", ""},
        {"ResultsV2NewlinesBetweenFrames", "results_v2_newlines_between_frames", "4de3b050e355c9a61f5dbab42958a61683d7ff53e04654f9123a5ab676a6b96c", "Bool", ""},
        {"ServerTimeout", "servertimeout", "9a7b80c4025d50546ae2f0771fe7eaa5b25699c34fc4421d8de7bdbab9a6f880", "TimeSpan", ""},
        {"TakeMaxRecords", "query_take_max_records", "6a9d19cd9480f984566a6d0c4fd48161f753919884f4360259feaf0ab2656d8b", "Long", ""},
        {"TruncationMaxRecords", "truncationmaxrecords", "b70aee64bc37689d43939c0a8b5b2e0cb40e2b4f106bd922eca787df4bdaf4d8", "Long", ""},
        {"TruncationMaxSize", "truncationmaxsize", "f8448a13527cddca426e22e1a71fb4cc88ef5613431a4b9a3110ff77a51b8d97", "Long", ""},
        {"V2FragmentPrimaryTables", "results_v2_fragment_primary_tables", "76ef383a83f103b30e6eebaae22e84f794c40fe564a3e9c67e4169a6c3d98134", "Bool", ""},
        {"ValidatePermissions", "validate_permissions", "41e57aa79af95ec9a8272ee85e013d3dba559914fe98615e867641e5dd7b8ed1", "Bool", ""},
    };

    private static String sha256(String text) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        var sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Test
    void thereAre58Options() {
        assertEquals(58, EXPECTED.length);
        assertEquals(58, Options.All.size());
    }

    @Test
    void everyOptionMatchesUpstream() throws Exception {
        for (String[] row : EXPECTED) {
            Field field = Options.class.getField(row[0]);
            var option = (OptionSymbol) field.get(null);
            assertEquals(row[1], option.name(), row[0]);
            assertEquals(row[2], sha256(option.description()), row[0] + " description");
            var types = new ArrayList<ScalarSymbol>();
            if (!row[3].isEmpty()) {
                for (String typeName : row[3].split(",")) {
                    types.add((ScalarSymbol) ScalarTypes.class.getField(typeName).get(null));
                }
            }
            assertEquals(types.size(), option.types().size(), row[0] + " types");
            for (int i = 0; i < types.size(); i++) {
                assertSame(types.get(i), option.types().get(i), row[0] + " type " + i);
            }
            var examples = row[4].isEmpty() ? List.<String>of() : List.of(row[4].split("\\|"));
            assertEquals(examples, option.examples(), row[0] + " examples");
            assertEquals(SymbolKind.Option, option.kind());
            assertEquals(Tabularity.Scalar, option.tabularity());
        }
    }

    @Test
    void allListsEveryOptionInDeclarationOrder() throws Exception {
        for (int i = 0; i < EXPECTED.length; i++) {
            assertSame(Options.class.getField(EXPECTED[i][0]).get(null), Options.All.get(i), EXPECTED[i][0]);
        }
    }

    @Test
    void optionNamesAreUnique() {
        var names = new HashSet<String>();
        for (OptionSymbol option : Options.All) {
            assertTrue(names.add(option.name()), option.name());
        }
    }

    @Test
    void allIsImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> Options.All.add(Options.BestEffort));
    }

    @Test
    void multiLineDescriptionsKeepLfNewlines() {
        assertTrue(Options.BestEffort.description().contains("time.\nIf at least one"));
        assertTrue(!Options.BestEffort.description().contains("\r"));
    }
}
