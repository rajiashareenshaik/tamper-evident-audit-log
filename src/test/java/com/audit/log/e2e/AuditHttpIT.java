package com.audit.log.e2e;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditHttpIT {
    static final String SCHEMA = "audit_test_" + UUID.randomUUID().toString().replace("-", "");
    static final String URL = System.getenv("AUDIT_E2E_DB_URL");
    static final String USER = System.getenv().getOrDefault("AUDIT_E2E_DB_USER", "audit");
    static final String PASSWORD = System.getenv().getOrDefault("AUDIT_E2E_DB_PASSWORD", "audit");

    @org.springframework.test.context.DynamicPropertySource
    static void database(org.springframework.test.context.DynamicPropertyRegistry properties) throws Exception {
        if (URL == null) throw new IllegalStateException("Set AUDIT_E2E_DB_URL to a disposable PostgreSQL database");
        try (var connection = java.sql.DriverManager.getConnection(URL, USER, PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }
        properties.add("spring.datasource.url", () -> URL);
        properties.add("spring.datasource.username", () -> USER);
        properties.add("spring.datasource.password", () -> PASSWORD);
        properties.add("spring.datasource.hikari.schema", () -> SCHEMA);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
    }

    @AfterAll static void cleanup() throws Exception {
        if (URL == null) return;
        try (var connection = java.sql.DriverManager.getConnection(URL, USER, PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach void reset() {
        // Run only against the disposable database created for this suite.
        jdbc.execute("TRUNCATE audit_redaction_value, audit_event_archive, audit_event, audit_chain_head RESTART IDENTITY CASCADE");
    }

    HttpResponse<String> request(String method, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/audit" + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
    String event(String actor) {
        return "{\"eventType\":\"READ\",\"actorId\":\"" + actor + "\",\"resourceType\":\"ACCOUNT\",\"resourceId\":\"a1\",\"payload\":{\"email\":\"a@example.com\",\"nested\":{\"count\":7}},\"redactableFields\":[\"email\"]}";
    }
    JsonNode create(String actor) throws Exception {
        var response = request("POST", "/events", event(actor));
        assertEquals(201, response.statusCode(), response.body());
        return json.readTree(response.body());
    }
    void intact(long count) throws Exception {
        var response = request("GET", "/verify", null);
        assertEquals(200, response.statusCode());
        var body = json.readTree(response.body());
        assertTrue(body.get("intact").asBoolean(), response.body());
        assertEquals(count, body.get("recordsVerified").asLong());
    }

    @Test void writesSurviveDatabaseRoundTripAndCursorPagination() throws Exception {
        intact(0);
        var first = create("alice");
        create("bob");
        var third = create("alice");
        var page = request("GET", "/events?actorId=alice&limit=1&afterSequenceId=" + first.get("sequenceId").asLong(), null);
        var rows = json.readTree(page.body());
        assertEquals(1, rows.size());
        assertEquals(third.get("eventId"), rows.get(0).get("eventId"));
        assertEquals("a@example.com", rows.get(0).get("payload").get("email").asText());
        intact(3);
    }

    @Test void concurrentWritersKeepOneChain() throws Exception {
        try (var pool = Executors.newFixedThreadPool(6)) {
            var tasks = new ArrayList<Callable<JsonNode>>();
            for (int i = 0; i < 36; i++) {
                String actor = "writer-" + i;
                tasks.add(() -> create(actor));
            }
            Set<String> ids = new HashSet<>();
            Set<Long> sequences = new HashSet<>();
            for (var result : pool.invokeAll(tasks)) {
                var row = result.get();
                assertTrue(ids.add(row.get("eventId").asText()));
                assertTrue(sequences.add(row.get("sequenceId").asLong()));
            }
        }
        intact(36);
        assertEquals(36L, jdbc.queryForObject("SELECT event_count FROM audit_chain_head", Long.class));
    }

    @Test void invalidRequestsDoNotWriteAnything() throws Exception {
        assertEquals(400, request("POST", "/events", "{}").statusCode());
        assertEquals(400, request("POST", "/events", "{").statusCode());
        assertEquals(400, request("POST", "/events", event("x").replace("[\"email\"]", "[\"missing\"]")).statusCode());
        assertEquals(400, request("POST", "/events", event("x".repeat(201))).statusCode());
        intact(0);
    }

    @Test void failedHeadUpdateRollsBackEventAndSecrets() throws Exception {
        create("before");
        jdbc.execute("ALTER TABLE audit_chain_head ADD CONSTRAINT test_count_limit CHECK(event_count <= 1)");
        try {
            assertEquals(500, request("POST", "/events", event("rollback")).statusCode());
            assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM audit_event", Long.class));
            assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM audit_redaction_value", Long.class));
            intact(1);
        } finally {
            jdbc.execute("ALTER TABLE audit_chain_head DROP CONSTRAINT test_count_limit");
        }
        create("after");
        intact(2);
    }

    @Test void directPayloadTamperingIsDetected() throws Exception {
        create("alice");
        jdbc.update("UPDATE audit_event SET integrity_payload = '{\"changed\":true}'::jsonb");
        var result = json.readTree(request("GET", "/verify", null).body());
        assertFalse(result.get("intact").asBoolean());
        assertEquals("CONTENT_HASH_MISMATCH", result.get("firstInconsistency").get("violationType").asText());
    }

    @Test void missingTailIsDetected() throws Exception {
        create("alice");
        var tail = create("bob");
        UUID id = UUID.fromString(tail.get("eventId").asText());
        jdbc.update("DELETE FROM audit_redaction_value WHERE event_id=?", id);
        jdbc.update("DELETE FROM audit_event WHERE event_id=?", id);
        assertFalse(json.readTree(request("GET", "/verify", null).body()).get("intact").asBoolean());
    }

    @Test void redactionIsRepeatableAndKeepsChainValid() throws Exception {
        var row = create("alice");
        String path = "/events/" + row.get("eventId").asText() + "/redactions";
        assertEquals(1, json.readTree(request("POST", path, "{\"fields\":[\"email\"]}").body()).get("redactedFields").asInt());
        assertEquals(0, json.readTree(request("POST", path, "{\"fields\":[\"email\"]}").body()).get("redactedFields").asInt());
        var rows = json.readTree(request("GET", "/events", null).body());
        assertTrue(rows.get(0).get("payload").get("email").get("redacted").asBoolean());
        intact(1);
    }

    @Test void archivedEventsRemainInExportAndVerification() throws Exception {
        var row = create("alice");
        jdbc.update("INSERT INTO audit_event_archive(event_id,archived_at,policy_cutoff) VALUES (?,now(),now())",
                UUID.fromString(row.get("eventId").asText()));
        assertEquals(0, json.readTree(request("GET", "/events", null).body()).size());
        var export = request("GET", "/export?actorId=alice", null);
        assertEquals(200, export.statusCode());
        assertEquals(1, json.readTree(export.body()).get("records").size());
        assertEquals(400, request("GET", "/export", null).statusCode());
        intact(1);
    }

    @Test void blockedWriterTimesOutWithoutAppendingAndCanRecover() throws Exception {
        create("first");
        try (var connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.executeQuery("SELECT * FROM audit_chain_head FOR UPDATE");
                var response = request("POST", "/events", event("blocked"));
                assertEquals(503, response.statusCode(), response.body());
                assertEquals("1", response.headers().firstValue("Retry-After").orElseThrow());
            } finally { connection.rollback(); }
        }
        intact(1);
        create("recovered");
        intact(2);
    }

    @Test void accountAccessUsesItsTypedContract() throws Exception {
        String body = "{\"actorId\":\"alice\",\"accountId\":\"a1\",\"action\":\"READ\",\"outcome\":\"DENIED\",\"sourceApplication\":\"portal\",\"requestId\":\"req1\"}";
        assertEquals(201, request("POST", "/client-account-access", body).statusCode());
        assertEquals(400, request("POST", "/client-account-access", body.replace("DENIED", "INVALID")).statusCode());
        var rows = request("GET", "/client-account-access?actorId=alice&accountId=a1", null);
        assertEquals(200, rows.statusCode());
        assertEquals(1, json.readTree(rows.body()).size());
        intact(1);
    }
}
