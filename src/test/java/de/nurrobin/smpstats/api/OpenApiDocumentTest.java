package de.nurrobin.smpstats.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.health.HealthThresholds;
import de.nurrobin.smpstats.skills.SkillWeights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for OpenApiDocument to validate the generated OpenAPI 3.1 specification.
 */
class OpenApiDocumentTest {

    private Settings settings;
    private OpenApiDocument document;
    private JsonObject openApiJson;

    @BeforeEach
    void setUp() {
        settings = new Settings(
                true, true, true, true, true, true, true,
                true, "0.0.0.0", 8080, "test-api-key", 1,
                new SkillWeights(
                        new SkillWeights.MiningWeights(0),
                        new SkillWeights.CombatWeights(0, 0, 0),
                        new SkillWeights.ExplorationWeights(0, 0),
                        new SkillWeights.BuilderWeights(0),
                        new SkillWeights.FarmerWeights(0, 0)
                ),
                true, 0L, 0L, true, 1, 1.0, List.of(), List.of(),
                true, 1, 1, true, true, true, 1, 1, true, 1, 0, 0, 0, 0,
                HealthThresholds.defaults(), true, 1, 1, "", 1, 1,
                Settings.DashboardSettings.defaults(), true
        );
        document = new OpenApiDocument(settings, "1.0.0");
        openApiJson = JsonParser.parseString(document.toJson()).getAsJsonObject();
    }

    @Nested
    class OpenApiStructure {

        @Test
        void hasValidOpenApiVersion() {
            assertTrue(openApiJson.has("openapi"));
            assertEquals("3.1.0", openApiJson.get("openapi").getAsString());
        }

        @Test
        void hasRequiredTopLevelFields() {
            assertTrue(openApiJson.has("info"), "Missing 'info' field");
            assertTrue(openApiJson.has("servers"), "Missing 'servers' field");
            assertTrue(openApiJson.has("paths"), "Missing 'paths' field");
            assertTrue(openApiJson.has("components"), "Missing 'components' field");
            assertTrue(openApiJson.has("security"), "Missing 'security' field");
        }

        @Test
        void infoSectionContainsRequiredFields() {
            JsonObject info = openApiJson.getAsJsonObject("info");
            assertNotNull(info);
            assertTrue(info.has("title"));
            assertTrue(info.has("version"));
            assertTrue(info.has("description"));

            assertEquals("SMPStats API", info.get("title").getAsString());
            assertEquals("1.0.0", info.get("version").getAsString());
        }

        @Test
        void serverVariablesUseSettingsValues() {
            JsonArray servers = openApiJson.getAsJsonArray("servers");
            assertFalse(servers.isEmpty());

            JsonObject server = servers.get(0).getAsJsonObject();
            JsonObject variables = server.getAsJsonObject("variables");

            assertEquals("0.0.0.0", variables.getAsJsonObject("host").get("default").getAsString());
            assertEquals("8080", variables.getAsJsonObject("port").get("default").getAsString());
        }
    }

    @Nested
    class SecurityDefinitions {

        @Test
        void definesApiKeySecurityScheme() {
            JsonObject components = openApiJson.getAsJsonObject("components");
            JsonObject securitySchemes = components.getAsJsonObject("securitySchemes");

            assertTrue(securitySchemes.has("ApiKeyAuth"));
            JsonObject apiKeyAuth = securitySchemes.getAsJsonObject("ApiKeyAuth");
            assertEquals("apiKey", apiKeyAuth.get("type").getAsString());
            assertEquals("header", apiKeyAuth.get("in").getAsString());
            assertEquals("X-API-Key", apiKeyAuth.get("name").getAsString());
        }

        @Test
        void globalSecurityRequiresApiKey() {
            JsonArray security = openApiJson.getAsJsonArray("security");
            assertFalse(security.isEmpty());

            JsonObject securityRequirement = security.get(0).getAsJsonObject();
            assertTrue(securityRequirement.has("ApiKeyAuth"));
        }

        @Test
        void openApiEndpointIsPublic() {
            JsonObject paths = openApiJson.getAsJsonObject("paths");
            JsonObject openApiPath = paths.getAsJsonObject("/openapi.json");
            JsonObject getOp = openApiPath.getAsJsonObject("get");

            assertTrue(getOp.has("security"));
            JsonArray security = getOp.getAsJsonArray("security");
            assertTrue(security.isEmpty(), "OpenAPI endpoint should have empty security array");
        }

        @Test
        void protectedEndpointsHaveSecurityDefined() {
            JsonObject paths = openApiJson.getAsJsonObject("paths");

            Set<String> publicEndpoints = Set.of("/openapi.json");
            for (String pathKey : paths.keySet()) {
                if (publicEndpoints.contains(pathKey)) {
                    continue;
                }
                JsonObject pathItem = paths.getAsJsonObject(pathKey);
                for (String method : pathItem.keySet()) {
                    JsonObject operation = pathItem.getAsJsonObject(method);
                    assertTrue(operation.has("security"),
                            "Endpoint " + pathKey + " (" + method + ") should have security defined");
                }
            }
        }
    }

    @Nested
    class EndpointPaths {

        private static final Set<String> EXPECTED_PATHS = Set.of(
                "/openapi.json",
                "/stats/{playerId}",
                "/stats/all",
                "/online",
                "/moments/recent",
                "/moments/query",
                "/moments/stream",
                "/heatmap/{type}",
                "/heatmap/hotspots/{type}",
                "/timeline/{playerId}",
                "/timeline/range/{playerId}",
                "/timeline/leaderboard",
                "/social/top",
                "/death/replay",
                "/health"
        );

        @Test
        void allExpectedPathsAreDefined() {
            JsonObject paths = openApiJson.getAsJsonObject("paths");

            for (String expectedPath : EXPECTED_PATHS) {
                assertTrue(paths.has(expectedPath),
                        "Missing expected path: " + expectedPath);
            }
        }

        @Test
        void noUnexpectedPathsExist() {
            JsonObject paths = openApiJson.getAsJsonObject("paths");

            for (String pathKey : paths.keySet()) {
                assertTrue(EXPECTED_PATHS.contains(pathKey),
                        "Unexpected path found: " + pathKey);
            }
        }

        @Test
        void allPathsHaveGetMethod() {
            JsonObject paths = openApiJson.getAsJsonObject("paths");

            for (String pathKey : paths.keySet()) {
                JsonObject pathItem = paths.getAsJsonObject(pathKey);
                assertTrue(pathItem.has("get"),
                        "Path " + pathKey + " should have GET method");
            }
        }

        @Test
        void allPathsHaveSummary() {
            JsonObject paths = openApiJson.getAsJsonObject("paths");

            for (String pathKey : paths.keySet()) {
                JsonObject pathItem = paths.getAsJsonObject(pathKey);
                JsonObject getOp = pathItem.getAsJsonObject("get");
                assertTrue(getOp.has("summary"),
                        "Path " + pathKey + " should have a summary");
                assertFalse(getOp.get("summary").getAsString().isBlank(),
                        "Path " + pathKey + " summary should not be blank");
            }
        }

        @Test
        void allPathsHaveResponses() {
            JsonObject paths = openApiJson.getAsJsonObject("paths");

            for (String pathKey : paths.keySet()) {
                JsonObject pathItem = paths.getAsJsonObject(pathKey);
                JsonObject getOp = pathItem.getAsJsonObject("get");
                assertTrue(getOp.has("responses"),
                        "Path " + pathKey + " should have responses defined");
                assertFalse(getOp.getAsJsonObject("responses").keySet().isEmpty(),
                        "Path " + pathKey + " should have at least one response");
            }
        }
    }

    @Nested
    class ParameterDefinitions {

        @Test
        void statsPlayerIdPathParameterIsCorrect() {
            JsonObject paths = openApiJson.getAsJsonObject("paths");
            JsonObject statsPath = paths.getAsJsonObject("/stats/{playerId}");
            JsonArray parameters = statsPath.getAsJsonObject("get").getAsJsonArray("parameters");

            assertTrue(parameters.size() >= 1);
            JsonObject playerIdParam = parameters.get(0).getAsJsonObject();
            assertEquals("playerId", playerIdParam.get("name").getAsString());
            assertEquals("path", playerIdParam.get("in").getAsString());
            assertTrue(playerIdParam.get("required").getAsBoolean());
            assertEquals("uuid", playerIdParam.getAsJsonObject("schema").get("format").getAsString());
        }

        @Test
        void heatmapTypePathParameterIsDefined() {
            JsonObject paths = openApiJson.getAsJsonObject("paths");
            JsonObject heatmapPath = paths.getAsJsonObject("/heatmap/{type}");
            JsonArray parameters = heatmapPath.getAsJsonObject("get").getAsJsonArray("parameters");

            boolean hasTypeParam = false;
            for (JsonElement param : parameters) {
                JsonObject p = param.getAsJsonObject();
                if ("type".equals(p.get("name").getAsString()) && "path".equals(p.get("in").getAsString())) {
                    hasTypeParam = true;
                    assertTrue(p.get("required").getAsBoolean());
                    break;
                }
            }
            assertTrue(hasTypeParam, "Heatmap endpoint should have 'type' path parameter");
        }

        @Test
        void momentsQueryHasExpectedQueryParameters() {
            JsonObject paths = openApiJson.getAsJsonObject("paths");
            JsonObject momentsQuery = paths.getAsJsonObject("/moments/query");
            JsonArray parameters = momentsQuery.getAsJsonObject("get").getAsJsonArray("parameters");

            Set<String> expectedParams = Set.of("limit", "from", "since", "type", "player");
            Set<String> foundParams = new HashSet<>();

            for (JsonElement param : parameters) {
                JsonObject p = param.getAsJsonObject();
                foundParams.add(p.get("name").getAsString());
                assertEquals("query", p.get("in").getAsString());
                assertFalse(p.get("required").getAsBoolean(), "Query parameters should not be required");
            }

            assertEquals(expectedParams, foundParams);
        }

        @Test
        void heatmapEndpointHasAllQueryParameters() {
            JsonObject paths = openApiJson.getAsJsonObject("paths");
            JsonObject heatmapPath = paths.getAsJsonObject("/heatmap/{type}");
            JsonArray parameters = heatmapPath.getAsJsonObject("get").getAsJsonArray("parameters");

            Set<String> expectedQueryParams = Set.of("world", "from", "to", "since", "until", "decay", "grid");
            Set<String> foundQueryParams = new HashSet<>();

            for (JsonElement param : parameters) {
                JsonObject p = param.getAsJsonObject();
                if ("query".equals(p.get("in").getAsString())) {
                    foundQueryParams.add(p.get("name").getAsString());
                }
            }

            assertEquals(expectedQueryParams, foundQueryParams);
        }

        @Test
        void timelineLeaderboardHasFromAndDaysParameters() {
            JsonObject paths = openApiJson.getAsJsonObject("paths");
            JsonObject leaderboard = paths.getAsJsonObject("/timeline/leaderboard");
            JsonArray parameters = leaderboard.getAsJsonObject("get").getAsJsonArray("parameters");

            Set<String> foundParams = new HashSet<>();
            for (JsonElement param : parameters) {
                JsonObject p = param.getAsJsonObject();
                foundParams.add(p.get("name").getAsString());
            }

            assertTrue(foundParams.contains("from"), "Should have 'from' parameter");
            assertTrue(foundParams.contains("days"), "Should have 'days' parameter");
            assertTrue(foundParams.contains("limit"), "Should have 'limit' parameter");
        }
    }

    @Nested
    class SchemaValidation {

        @Test
        void statsRecordSchemaMatchesJavaClass() {
            JsonObject schemas = openApiJson.getAsJsonObject("components").getAsJsonObject("schemas");
            JsonObject statsRecordSchema = schemas.getAsJsonObject("StatsRecord");
            JsonObject properties = statsRecordSchema.getAsJsonObject("properties");

            // Get all fields from StatsRecord class
            Set<String> expectedFields = new HashSet<>();
            for (Field field : de.nurrobin.smpstats.StatsRecord.class.getDeclaredFields()) {
                expectedFields.add(field.getName());
            }

            Set<String> schemaFields = properties.keySet();

            assertEquals(expectedFields, schemaFields,
                    "StatsRecord schema fields should match Java class fields");
        }

        @Test
        void momentEntrySchemaMatchesJavaClass() {
            JsonObject schemas = openApiJson.getAsJsonObject("components").getAsJsonObject("schemas");
            JsonObject momentEntrySchema = schemas.getAsJsonObject("MomentEntry");
            JsonObject properties = momentEntrySchema.getAsJsonObject("properties");

            Set<String> expectedFields = new HashSet<>();
            for (Field field : de.nurrobin.smpstats.moments.MomentEntry.class.getDeclaredFields()) {
                expectedFields.add(field.getName());
            }

            Set<String> schemaFields = properties.keySet();

            assertEquals(expectedFields, schemaFields,
                    "MomentEntry schema fields should match Java class fields");
        }

        @Test
        void heatmapBinSchemaMatchesJavaClass() {
            JsonObject schemas = openApiJson.getAsJsonObject("components").getAsJsonObject("schemas");
            JsonObject heatmapBinSchema = schemas.getAsJsonObject("HeatmapBin");
            JsonObject properties = heatmapBinSchema.getAsJsonObject("properties");

            Set<String> expectedFields = Set.of("type", "world", "x", "z", "gridSize", "count");
            Set<String> schemaFields = properties.keySet();

            assertEquals(expectedFields, schemaFields,
                    "HeatmapBin schema fields should match Java class fields");
        }

        @Test
        void healthSnapshotSchemaMatchesRecord() {
            JsonObject schemas = openApiJson.getAsJsonObject("components").getAsJsonObject("schemas");
            JsonObject healthSnapshotSchema = schemas.getAsJsonObject("HealthSnapshot");
            JsonObject properties = healthSnapshotSchema.getAsJsonObject("properties");

            Set<String> expectedFields = new HashSet<>();
            for (RecordComponent component : de.nurrobin.smpstats.health.HealthSnapshot.class.getRecordComponents()) {
                expectedFields.add(component.getName());
            }

            Set<String> schemaFields = properties.keySet();

            assertEquals(expectedFields, schemaFields,
                    "HealthSnapshot schema fields should match record components");
        }

        @Test
        void socialPairSchemaHasAllFields() {
            JsonObject schemas = openApiJson.getAsJsonObject("components").getAsJsonObject("schemas");
            JsonObject socialPairSchema = schemas.getAsJsonObject("SocialPair");
            JsonObject properties = socialPairSchema.getAsJsonObject("properties");

            // API response adds name_a and name_b, original record has uuidA, uuidB
            Set<String> expectedApiFields = Set.of("a", "b", "name_a", "name_b", "seconds",
                    "shared_kills", "shared_player_kills", "shared_mob_kills");
            Set<String> schemaFields = properties.keySet();

            assertEquals(expectedApiFields, schemaFields,
                    "SocialPair schema should have all expected API response fields");
        }

        @Test
        void allReferencedSchemasExist() {
            JsonObject schemas = openApiJson.getAsJsonObject("components").getAsJsonObject("schemas");
            JsonObject paths = openApiJson.getAsJsonObject("paths");

            Set<String> referencedSchemas = new HashSet<>();
            collectSchemaReferences(paths, referencedSchemas);

            for (String ref : referencedSchemas) {
                assertTrue(schemas.has(ref),
                        "Referenced schema '" + ref + "' should exist in components/schemas");
            }
        }

        @Test
        void nestedHealthSchemasExist() {
            JsonObject schemas = openApiJson.getAsJsonObject("components").getAsJsonObject("schemas");

            assertTrue(schemas.has("HealthWorldBreakdown"), "Should have HealthWorldBreakdown schema");
            assertTrue(schemas.has("HealthHotChunk"), "Should have HealthHotChunk schema");
        }

        @Test
        void healthWorldBreakdownSchemaMatchesRecord() {
            JsonObject schemas = openApiJson.getAsJsonObject("components").getAsJsonObject("schemas");
            JsonObject schema = schemas.getAsJsonObject("HealthWorldBreakdown");
            JsonObject properties = schema.getAsJsonObject("properties");

            Set<String> expectedFields = Set.of("chunks", "entities", "hoppers", "redstone");
            assertEquals(expectedFields, properties.keySet());
        }

        @Test
        void healthHotChunkSchemaMatchesRecord() {
            JsonObject schemas = openApiJson.getAsJsonObject("components").getAsJsonObject("schemas");
            JsonObject schema = schemas.getAsJsonObject("HealthHotChunk");
            JsonObject properties = schema.getAsJsonObject("properties");

            Set<String> expectedFields = Set.of("world", "x", "z", "entityCount", "tileEntityCount", "topOwner");
            assertEquals(expectedFields, properties.keySet());
        }

        private void collectSchemaReferences(JsonElement element, Set<String> refs) {
            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                if (obj.has("$ref")) {
                    String ref = obj.get("$ref").getAsString();
                    if (ref.startsWith("#/components/schemas/")) {
                        refs.add(ref.substring("#/components/schemas/".length()));
                    }
                }
                for (String key : obj.keySet()) {
                    collectSchemaReferences(obj.get(key), refs);
                }
            } else if (element.isJsonArray()) {
                for (JsonElement e : element.getAsJsonArray()) {
                    collectSchemaReferences(e, refs);
                }
            }
        }
    }

    @Nested
    class ResponseSchemas {

        @Test
        void statsPlayerIdReturnsStatsRecordSchema() {
            JsonObject paths = openApiJson.getAsJsonObject("paths");
            JsonObject statsPath = paths.getAsJsonObject("/stats/{playerId}");
            JsonObject responses = statsPath.getAsJsonObject("get").getAsJsonObject("responses");
            JsonObject ok = responses.getAsJsonObject("200");

            String ref = ok.getAsJsonObject("content")
                    .getAsJsonObject("application/json")
                    .getAsJsonObject("schema")
                    .get("$ref").getAsString();

            assertEquals("#/components/schemas/StatsRecord", ref);
        }

        @Test
        void statsAllReturnsArrayOfStatsRecord() {
            JsonObject paths = openApiJson.getAsJsonObject("paths");
            JsonObject statsAll = paths.getAsJsonObject("/stats/all");
            JsonObject responses = statsAll.getAsJsonObject("get").getAsJsonObject("responses");
            JsonObject ok = responses.getAsJsonObject("200");
            JsonObject schema = ok.getAsJsonObject("content")
                    .getAsJsonObject("application/json")
                    .getAsJsonObject("schema");

            assertEquals("array", schema.get("type").getAsString());
            assertEquals("#/components/schemas/StatsRecord",
                    schema.getAsJsonObject("items").get("$ref").getAsString());
        }

        @Test
        void onlineReturnsArrayOfStrings() {
            JsonObject paths = openApiJson.getAsJsonObject("paths");
            JsonObject online = paths.getAsJsonObject("/online");
            JsonObject schema = online.getAsJsonObject("get")
                    .getAsJsonObject("responses")
                    .getAsJsonObject("200")
                    .getAsJsonObject("content")
                    .getAsJsonObject("application/json")
                    .getAsJsonObject("schema");

            assertEquals("array", schema.get("type").getAsString());
            assertEquals("string", schema.getAsJsonObject("items").get("type").getAsString());
        }

        @Test
        void momentsStreamReturnsEventStream() {
            JsonObject paths = openApiJson.getAsJsonObject("paths");
            JsonObject stream = paths.getAsJsonObject("/moments/stream");
            JsonObject content = stream.getAsJsonObject("get")
                    .getAsJsonObject("responses")
                    .getAsJsonObject("200")
                    .getAsJsonObject("content");

            assertTrue(content.has("text/event-stream"),
                    "Moments stream should return text/event-stream content type");
        }

        @Test
        void heatmapHotspotsReturnsHotspotSchema() {
            JsonObject paths = openApiJson.getAsJsonObject("paths");
            JsonObject hotspots = paths.getAsJsonObject("/heatmap/hotspots/{type}");
            JsonObject schema = hotspots.getAsJsonObject("get")
                    .getAsJsonObject("responses")
                    .getAsJsonObject("200")
                    .getAsJsonObject("content")
                    .getAsJsonObject("application/json")
                    .getAsJsonObject("schema");

            assertEquals("#/components/schemas/HeatmapHotspots", schema.get("$ref").getAsString());
        }

        @Test
        void errorResponsesUseTextPlain() {
            JsonObject paths = openApiJson.getAsJsonObject("paths");
            JsonObject statsPath = paths.getAsJsonObject("/stats/{playerId}");
            JsonObject responses = statsPath.getAsJsonObject("get").getAsJsonObject("responses");

            // Check 400 response
            JsonObject badRequest = responses.getAsJsonObject("400");
            assertTrue(badRequest.getAsJsonObject("content").has("text/plain"));

            // Check 404 response
            JsonObject notFound = responses.getAsJsonObject("404");
            assertTrue(notFound.getAsJsonObject("content").has("text/plain"));
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void handlesNullPluginVersion() {
            OpenApiDocument docWithNullVersion = new OpenApiDocument(settings, null);
            String json = docWithNullVersion.toJson();

            // Should not throw, should produce valid JSON
            JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
            JsonObject info = parsed.getAsJsonObject("info");
            // Depending on Gson config, version may be missing or null when input is null
            // Both are acceptable - the key is that the document is still valid JSON
            if (info.has("version")) {
                JsonElement version = info.get("version");
                assertTrue(version.isJsonNull() || version.getAsString() == null);
            }
            // Document should still be valid even with null version
            assertTrue(parsed.has("paths"));
            assertTrue(parsed.has("components"));
        }

        @Test
        void handlesUnknownPluginVersion() {
            OpenApiDocument docWithUnknown = new OpenApiDocument(settings, "unknown");
            JsonObject parsed = JsonParser.parseString(docWithUnknown.toJson()).getAsJsonObject();

            assertEquals("unknown", parsed.getAsJsonObject("info").get("version").getAsString());
        }

        @Test
        void producesValidJsonOutput() {
            String json = document.toJson();

            // Should not throw when parsing
            assertDoesNotThrow(() -> JsonParser.parseString(json));

            // Should be a JSON object
            assertTrue(JsonParser.parseString(json).isJsonObject());
        }

        @Test
        void jsonIsPrettyPrinted() {
            String json = document.toJson();

            // Pretty printed JSON should contain newlines
            assertTrue(json.contains("\n"), "JSON output should be pretty-printed");
        }
    }

    @Nested
    class TypeValidation {

        @Test
        void uuidFieldsHaveCorrectFormat() {
            JsonObject schemas = openApiJson.getAsJsonObject("components").getAsJsonObject("schemas");

            // Check StatsRecord uuid field
            JsonObject statsRecordUuid = schemas.getAsJsonObject("StatsRecord")
                    .getAsJsonObject("properties")
                    .getAsJsonObject("uuid");
            assertEquals("string", statsRecordUuid.get("type").getAsString());
            assertEquals("uuid", statsRecordUuid.get("format").getAsString());

            // Check MomentEntry playerId field
            JsonObject momentPlayerId = schemas.getAsJsonObject("MomentEntry")
                    .getAsJsonObject("properties")
                    .getAsJsonObject("playerId");
            assertEquals("string", momentPlayerId.get("type").getAsString());
            assertEquals("uuid", momentPlayerId.get("format").getAsString());
        }

        @Test
        void timestampFieldsHaveInt64Format() {
            JsonObject schemas = openApiJson.getAsJsonObject("components").getAsJsonObject("schemas");

            // Check StatsRecord timestamps
            JsonObject statsProps = schemas.getAsJsonObject("StatsRecord").getAsJsonObject("properties");
            assertEquals("int64", statsProps.getAsJsonObject("firstJoin").get("format").getAsString());
            assertEquals("int64", statsProps.getAsJsonObject("lastJoin").get("format").getAsString());
            assertEquals("int64", statsProps.getAsJsonObject("playtimeMillis").get("format").getAsString());

            // Check MomentEntry timestamps
            JsonObject momentProps = schemas.getAsJsonObject("MomentEntry").getAsJsonObject("properties");
            assertEquals("int64", momentProps.getAsJsonObject("startedAt").get("format").getAsString());
            assertEquals("int64", momentProps.getAsJsonObject("endedAt").get("format").getAsString());
        }

        @Test
        void numericFieldsHaveCorrectTypes() {
            JsonObject schemas = openApiJson.getAsJsonObject("components").getAsJsonObject("schemas");
            JsonObject statsProps = schemas.getAsJsonObject("StatsRecord").getAsJsonObject("properties");

            // Integer fields
            assertEquals("integer", statsProps.getAsJsonObject("deaths").get("type").getAsString());
            assertEquals("integer", statsProps.getAsJsonObject("playerKills").get("type").getAsString());
            assertEquals("integer", statsProps.getAsJsonObject("mobKills").get("type").getAsString());

            // Number (double) fields
            assertEquals("number", statsProps.getAsJsonObject("distanceOverworld").get("type").getAsString());
            assertEquals("number", statsProps.getAsJsonObject("damageDealt").get("type").getAsString());
        }

        @Test
        void nullableFieldsAreMarked() {
            JsonObject schemas = openApiJson.getAsJsonObject("components").getAsJsonObject("schemas");

            // StatsRecord.lastDeathCause is nullable
            JsonObject lastDeathCause = schemas.getAsJsonObject("StatsRecord")
                    .getAsJsonObject("properties")
                    .getAsJsonObject("lastDeathCause");
            assertTrue(lastDeathCause.has("nullable") && lastDeathCause.get("nullable").getAsBoolean(),
                    "lastDeathCause should be marked as nullable");

            // MomentEntry.id is nullable
            JsonObject momentId = schemas.getAsJsonObject("MomentEntry")
                    .getAsJsonObject("properties")
                    .getAsJsonObject("id");
            assertTrue(momentId.has("nullable") && momentId.get("nullable").getAsBoolean(),
                    "MomentEntry.id should be marked as nullable");

            // MomentEntry.payload is nullable
            JsonObject payload = schemas.getAsJsonObject("MomentEntry")
                    .getAsJsonObject("properties")
                    .getAsJsonObject("payload");
            assertTrue(payload.has("nullable") && payload.get("nullable").getAsBoolean(),
                    "MomentEntry.payload should be marked as nullable");
        }

        @Test
        void arrayFieldsHaveItemsSchema() {
            JsonObject schemas = openApiJson.getAsJsonObject("components").getAsJsonObject("schemas");

            // StatsRecord.biomesVisited
            JsonObject biomesVisited = schemas.getAsJsonObject("StatsRecord")
                    .getAsJsonObject("properties")
                    .getAsJsonObject("biomesVisited");
            assertEquals("array", biomesVisited.get("type").getAsString());
            assertTrue(biomesVisited.has("items"));
            assertEquals("string", biomesVisited.getAsJsonObject("items").get("type").getAsString());
        }
    }
}
