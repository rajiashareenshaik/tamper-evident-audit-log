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

@org.springframework.context.annotation.Import(AuditHttpIT.TestTime.class)
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

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class TestTime {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        AdjustableClock testClock() { return new AdjustableClock(); }
    }

    static class AdjustableClock extends java.time.Clock {
        volatile java.time.Instant now = java.time.Instant.parse("2026-10-01T12:00:00Z");
        public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        public java.time.Clock withZone(java.time.ZoneId zone) { return java.time.Clock.fixed(now, zone); }
        public java.time.Instant instant() { return now; }
    }

    @Autowired AdjustableClock testClock;
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach void reset() {
        testClock.now = java.time.Instant.parse("2026-10-01T12:00:00Z");
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

    @Test void retentionUsesStrictCutoffAndIsRepeatable() throws Exception {
        var now = testClock.now;
        var cutoff = now.minus(Duration.ofDays(365));
        testClock.now = cutoff.minusNanos(1000);
        var expired = create("retention");
        testClock.now = cutoff;
        var boundary = create("retention");
        testClock.now = cutoff.plusNanos(1000);
        var fresh = create("retention");
        testClock.now = now;
        var result = request("POST", "/retention/archive-expired", "{}");
        assertEquals(200, result.statusCode(), result.body());
        assertEquals(1, json.readTree(result.body()).get("archivedCount").asInt());
        assertEquals(0, json.readTree(request("POST", "/retention/archive-expired", "{}").body())
                .get("archivedCount").asInt());
        var visible = json.readTree(request("GET", "/events?actorId=retention", null).body());
        assertEquals(2, visible.size());
        assertEquals(boundary.get("eventId"), visible.get(0).get("eventId"));
        assertEquals(fresh.get("eventId"), visible.get(1).get("eventId"));
        var exported = json.readTree(request("GET", "/export?actorId=retention", null).body());
        assertEquals(3, exported.get("records").size());
        assertEquals(expired.get("eventId"), exported.get("records").get(0).get("eventId"));
        intact(3);
    }

    @Test void exportedBytesHaveAnIndependentlyVerifiableSignature() throws Exception {
        create("signature");
        var response = request("GET", "/export?actorId=signature", null);
        assertEquals(200, response.statusCode());
        var bundle = json.readTree(response.body());
        // Only the wire response and JDK crypto are used; no export service helpers.
        String signedContent = bundle.get("records").toString() + "|" + bundle.get("chainAnchor").toString();
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        String computed = HexFormat.of().formatHex(digest.digest(signedContent.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var manifest = bundle.get("manifest");
        assertEquals(computed, manifest.get("bundleDigest").asText());
        var key = java.security.KeyFactory.getInstance("Ed25519").generatePublic(
                new java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(manifest.get("publicKey").asText())));
        var verifier = java.security.Signature.getInstance("Ed25519");
        verifier.initVerify(key);
        verifier.update(computed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(manifest.get("signature").asText())));
        verifier.initVerify(key);
        verifier.update(("0" + computed.substring(1) + "changed").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertFalse(verifier.verify(Base64.getDecoder().decode(manifest.get("signature").asText())));
    }

    @Test void verificationAndExportsRemainConsistentDuringWrites() throws Exception {
        create("snapshot");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var writer = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 80; i++) create("snapshot");
                return null;
            });
            var reader = pool.submit(() -> {
                start.await();
                for (int i = 0; i < 30; i++) {
                    var verification = request("GET", "/verify", null);
                    assertEquals(200, verification.statusCode());
                    assertTrue(json.readTree(verification.body()).get("intact").asBoolean(), verification.body());
                    var exported = request("GET", "/export?actorId=snapshot", null);
                    assertEquals(200, exported.statusCode());
                    var bundle = json.readTree(exported.body());
                    assertEquals(bundle.get("records").size(), bundle.get("chainAnchor").get("chainEventCount").asInt());
                }
                return null;
            });
            start.countDown();
            writer.get(30, TimeUnit.SECONDS);
            reader.get(30, TimeUnit.SECONDS);
        }
        intact(81);
    }

    @Test void twoApplicationInstancesShareOneChain() throws Exception {
        try (var second = new org.springframework.boot.builder.SpringApplicationBuilder(com.audit.log.AuditLogServiceApplication.class)
                .run("--server.port=0", "--spring.datasource.url=" + URL,
                        "--spring.datasource.username=" + USER, "--spring.datasource.password=" + PASSWORD,
                        "--spring.datasource.hikari.schema=" + SCHEMA, "--spring.flyway.enabled=false")) {
            int otherPort = ((org.springframework.boot.web.server.context.WebServerApplicationContext) second).getWebServer().getPort();
            try (var pool = Executors.newFixedThreadPool(6)) {
                var tasks = new ArrayList<Callable<String>>();
                for (int i = 0; i < 60; i++) {
                    int destination = i % 2 == 0 ? port : otherPort;
                    String body = event("instance-" + i);
                    tasks.add(() -> {
                        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + destination + "/api/v1/audit/events"))
                                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
                        assertEquals(201, response.statusCode(), response.body());
                        return json.readTree(response.body()).get("eventId").asText();
                    });
                }
                var ids = new HashSet<String>();
                for (var future : pool.invokeAll(tasks)) assertTrue(ids.add(future.get()));
                assertEquals(60, ids.size());
            }
        }
        intact(60);
    }

    @Test void connectionPoolExhaustionReturns503AndRecovers() throws Exception {
        create("before-pool-exhaustion");
        var pool = (com.zaxxer.hikari.HikariDataSource) jdbc.getDataSource();
        var held = new ArrayList<java.sql.Connection>();
        try {
            for (int i = 0; i < pool.getMaximumPoolSize(); i++) held.add(pool.getConnection());
            var response = request("POST", "/events", event("no-connection"));
            assertEquals(503, response.statusCode(), response.body());
        } finally {
            for (var connection : held) connection.close();
        }
        intact(1);
        create("after-pool-exhaustion");
        intact(2);
    }


    @Test void burstDuringLockContentionRejectsWritesAndRecovers() throws Exception {
        create("before-burst");
        try (var connection = jdbc.getDataSource().getConnection();
             var pool = Executors.newFixedThreadPool(32)) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.executeQuery("SELECT * FROM audit_chain_head FOR UPDATE");
                var start = new CountDownLatch(1);
                var futures = new ArrayList<Future<HttpResponse<String>>>();
                for (int i = 0; i < 32; i++) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        return request("POST", "/events", event("burst"));
                    }));
                }
                start.countDown();
                int admissionRejections = 0;
                for (var future : futures) {
                    var response = future.get(20, TimeUnit.SECONDS);
                    assertEquals(503, response.statusCode(), response.body());
                    assertEquals("1", response.headers().firstValue("Retry-After").orElseThrow());
                    if (response.body().contains("Too many writes")) admissionRejections++;
                }
                assertTrue(admissionRejections > 0, "The burst should exhaust HTTP admission capacity");
            } finally { connection.rollback(); }
        }
        intact(1);
        create("after-burst");
        intact(2);
    }

    @Test void largeSupportedPayloadSurvivesRoundTrip() throws Exception {
        String content = "x".repeat(128 * 1024);
        String body = json.writeValueAsString(Map.of("eventType", "LARGE", "actorId", "large",
                "resourceType", "TEST", "resourceId", "large", "payload", Map.of("text", content)));
        var response = request("POST", "/events", body);
        assertEquals(201, response.statusCode(), response.body());
        var rows = json.readTree(request("GET", "/events?actorId=large", null).body());
        assertEquals(content, rows.get(0).get("payload").get("text").asText());
        intact(1);
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "AUDIT_RUN_SOAK", matches = "true")
    void sustainedWritesReconcileAcknowledgementsWithStoredEvents() throws Exception {
        int seconds = Integer.parseInt(System.getenv().getOrDefault("AUDIT_SOAK_SECONDS", "60"));
        assertTrue(seconds >= 1 && seconds <= 3600, "Use a duration between one second and one hour");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        var committed = new java.util.concurrent.atomic.AtomicLong();
        var rejected = new java.util.concurrent.atomic.AtomicLong();
        try (var pool = Executors.newFixedThreadPool(12)) {
            var tasks = new ArrayList<Callable<Void>>();
            for (int worker = 0; worker < 12; worker++) {
                tasks.add(() -> {
                    while (System.nanoTime() < deadline) {
                        var response = request("POST", "/events", event("soak"));
                        if (response.statusCode() == 201) committed.incrementAndGet();
                        else {
                            assertEquals(503, response.statusCode(), response.body());
                            rejected.incrementAndGet();
                        }
                    }
                    return null;
                });
            }
            for (var result : pool.invokeAll(tasks)) result.get();
        }
        assertTrue(committed.get() > 0);
        assertEquals(committed.get(), jdbc.queryForObject("SELECT count(*) FROM audit_event", Long.class));
        intact(committed.get());
        System.out.printf("Soak: duration=%ds, committed=%d, rejected=%d%n", seconds, committed.get(), rejected.get());
    }

}
