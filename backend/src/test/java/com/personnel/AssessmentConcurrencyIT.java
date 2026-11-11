package com.personnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personnel.common.constant.AssessmentConstants;
import com.personnel.common.util.JwtUtil;
import com.personnel.config.RateLimitProperties;
import com.personnel.dto.req.AdminUserCreateReqDTO;
import com.personnel.service.AssessmentScaleService;
import com.personnel.service.UserService;
import com.personnel.vo.AssessmentScaleVO;
import com.personnel.vo.UserVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in real HTTP load acceptance. The runner owns a new disposable database, never the local business DB. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "PERSONNEL_LOADTEST_DB", matches = "personnel_loadtest_[0-9a-f]{16}")
class AssessmentConcurrencyIT {
    private static final int USERS = 50;
    private static final String PASSWORD = "LoadFixture123";
    private static final List<String> TYPES = List.of("INTJ", "INTP", "ENTJ", "ENTP", "INFJ", "INFP", "ENFJ", "ENFP",
            "ISTJ", "ISFJ", "ESTJ", "ESFJ", "ISTP", "ISFP", "ESTP", "ESFP");
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired UserService users;
    @Autowired AssessmentScaleService scales;
    @Autowired JwtUtil jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired RateLimitProperties limits;
    @Autowired javax.sql.DataSource datasource;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1).build();
    private final ConcurrentLinkedQueue<Sample> samples = new ConcurrentLinkedQueue<>();
    private final Map<String, Integer> peakInFlight = new ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.atomic.AtomicInteger> inFlight = new ConcurrentHashMap<>();

    @DynamicPropertySource
    static void isolatedDatasource(DynamicPropertyRegistry registry) {
        String name = System.getenv("PERSONNEL_LOADTEST_DB");
        String url = System.getenv("PERSONNEL_LOADTEST_JDBC_URL");
        if (name == null || !name.matches("personnel_loadtest_[0-9a-f]{16}") || url == null
                || !URI.create(url.substring("jdbc:".length())).getPath().equals("/" + name)) {
            throw new IllegalStateException("Run only through tools/run_concurrency_check.py with its newly created isolated database");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv("PERSONNEL_LOADTEST_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("PERSONNEL_LOADTEST_DB_PASSWORD"));
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("server.address", () -> "127.0.0.1");
        // Keep actual business rate limits and the default Hikari/Tomcat sizes.
    }

    private record Sample(String phase, double milliseconds, boolean success, int httpStatus, int businessCode) { }

    private JsonNode request(String phase, String method, String path, String token, Object body, int status, int code) {
        long start = System.nanoTime();
        int concurrent = inFlight.computeIfAbsent(phase, ignored -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
        peakInFlight.merge(phase, concurrent, Math::max);
        boolean success = false;
        int actualStatus = 0, actualCode = 0;
        try {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(20));
            if (token != null) builder.header("Authorization", "Bearer " + token);
            if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
            else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            actualStatus = response.statusCode();
            JsonNode result = json.readTree(response.body());
            actualCode = result.path("code").asInt(0);
            // Never attach response bodies or tokens to assertion messages/performance reports.
            assertThat(actualStatus).as(phase + " HTTP status").isEqualTo(status);
            if (code != 0) assertThat(actualCode).as(phase + " business code").isEqualTo(code);
            success = true;
            return result.path("data");
        } catch (AssertionError error) { throw error; }
        catch (Exception error) { throw new AssertionError(phase + " request failed: " + error.getClass().getSimpleName()); }
        finally {
            samples.add(new Sample(phase, (System.nanoTime() - start) / 1_000_000.0, success, actualStatus, actualCode));
            inFlight.get(phase).decrementAndGet();
        }
    }

    private void wave(IntConsumer action) throws Exception {
        var executor = Executors.newFixedThreadPool(USERS);
        var ready = new CountDownLatch(USERS);
        var start = new CountDownLatch(1);
        var errors = new ConcurrentLinkedQueue<Throwable>();
        try {
            for (int index = 0; index < USERS; index++) {
                final int user = index;
                executor.submit(() -> {
                    ready.countDown();
                    try { start.await(); action.accept(user); }
                    catch (Throwable error) { errors.add(error); }
                });
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("50 client workers ready").isTrue();
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).as("50 requests finished").isTrue();
            if (!errors.isEmpty()) throw new AssertionError("Concurrent wave failed for " + errors.size() + " workers", errors.peek());
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    private Map<String, Integer> answers(AssessmentScaleVO scale, String type) {
        var answers = new LinkedHashMap<String, Integer>();
        for (var question : scale.definition().questions()) {
            int index = AssessmentConstants.DIMENSIONS.indexOf(question.dimension());
            answers.put(question.key(), question.highPole().equals(type.substring(index, index + 1)) ? 5 : 1);
        }
        return answers;
    }

    private Map<String, Integer> partialAnswers(AssessmentScaleVO scale, String type) {
        var partial = new LinkedHashMap<String, Integer>();
        answers(scale, type).entrySet().stream().limit(8).forEach(entry -> partial.put(entry.getKey(), entry.getValue()));
        return partial;
    }

    private double percentile(List<Double> sorted, double percentile) {
        return sorted.get(Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1));
    }

    private void writeReport(long start, boolean passed, Map<String, Object> checks, double p95Limit) throws Exception {
        var grouped = new LinkedHashMap<String, List<Sample>>();
        samples.forEach(sample -> grouped.computeIfAbsent(sample.phase(), ignored -> new ArrayList<>()).add(sample));
        var phases = new LinkedHashMap<String, Object>();
        grouped.forEach((name, values) -> {
            var times = values.stream().map(Sample::milliseconds).sorted().toList();
            phases.put(name, Map.of("requests", values.size(), "unexpectedResponses", values.stream().filter(v -> !v.success()).count(),
                    "p50Ms", percentile(times, .50), "p95Ms", percentile(times, .95), "p99Ms", percentile(times, .99),
                    "maxMs", times.get(times.size() - 1), "peakClientInFlight", peakInFlight.get(name)));
        });
        var report = new LinkedHashMap<String, Object>();
        report.put("result", passed ? "PASSED" : "FAILED");
        report.put("generatedAt", Instant.now().toString());
        report.put("scenario", "50 users, barrier-synchronized HTTP/1.1 waves, no think time, real MySQL isolated database");
        report.put("users", USERS);
        report.put("requests", samples.size());
        report.put("elapsedSeconds", (System.nanoTime() - start) / 1_000_000_000.0);
        report.put("p95LimitMs", p95Limit);
        report.put("rateLimitsEnabled", limits.isEnabled());
        report.put("credentialIpLimit", limits.getCredentialIpLimit());
        report.put("httpStatusCounts", samples.stream().collect(java.util.stream.Collectors.groupingBy(Sample::httpStatus, java.util.TreeMap::new, java.util.stream.Collectors.counting())));
        report.put("businessCodeCounts", samples.stream().collect(java.util.stream.Collectors.groupingBy(Sample::businessCode, java.util.TreeMap::new, java.util.stream.Collectors.counting())));
        report.put("hikariMaximumPoolSize", datasource.unwrap(com.zaxxer.hikari.HikariDataSource.class).getMaximumPoolSize());
        report.put("processors", Runtime.getRuntime().availableProcessors());
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("os", System.getProperty("os.name"));
        report.put("checks", checks);
        report.put("phases", phases);
        report.put("scope", "Local acceptance, not distributed or public HTTPS capacity; setup and DB creation excluded from timings; expected permission rejections are successful checks.");
        Path output = Path.of(System.getenv("PERSONNEL_LOADTEST_REPORT"));
        Files.createDirectories(output.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
    }

    @Test
    void fiftyUsersCompleteRealHttpAssessmentWithFrozenReportsAndIsolation() throws Exception {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo(System.getenv("PERSONNEL_LOADTEST_DB"));
        assertThat(limits.isEnabled()).as("Do not bypass production rate limits").isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_session", Long.class)).isZero();
        var scale = scales.listPublished().stream().filter(s -> AssessmentConstants.OEJTS_SCORING_VERSION.equals(s.definition().scoringVersion())).findFirst().orElseThrow();
        assertThat(scale.definition().questions()).hasSize(32);
        var people = new ArrayList<UserVO>();
        for (int index = 0; index < USERS; index++) people.add(users.createUser(new AdminUserCreateReqDTO("loaduser" + index, PASSWORD, "并发演练" + index, null, "EMPLOYEE", null, null)));
        var admin = users.createUser(new AdminUserCreateReqDTO("load_admin", PASSWORD, "并发验收管理员", null, "ADMIN", null, null));
        String adminToken = jwt.createToken(admin.id(), admin.roleCode(), admin.authVersion());
        long population = jdbc.queryForObject("SELECT COUNT(*) FROM employee_profile", Long.class);
        // Warm the HTTP server/JSON/auth path without polluting the measured request samples.
        request("warmup", "GET", "/api/user/me", adminToken, null, 200, 200);
        samples.clear(); peakInFlight.clear(); inFlight.clear();
        String[] tokens = new String[USERS];
        long[] ids = new long[USERS];
        JsonNode[] submitted = new JsonNode[USERS];
        double p95Limit = Double.parseDouble(System.getenv().getOrDefault("PERSONNEL_LOADTEST_P95_MS", "5000"));
        var checks = new LinkedHashMap<String, Object>();
        boolean passed = false;
        long start = System.nanoTime();
        try {
            wave(i -> {
                var data = request("login", "POST", "/api/user/login", null, Map.of("userName", people.get(i).userName(), "password", PASSWORD), 200, 200);
                assertThat(data.path("user").path("id").asLong()).isEqualTo(people.get(i).id());
                tokens[i] = data.path("accessToken").asText();
                assertThat(tokens[i]).isNotBlank();
            });
            wave(i -> {
                var data = request("start", "POST", "/api/assessment/sessions", tokens[i], Map.of("scaleId", scale.id(), "consentAccepted", true), 200, 200);
                ids[i] = data.path("id").asLong();
                assertThat(data.path("revision").asInt(-1)).isZero();
                assertThat(data.path("status").asText()).isEqualTo("DRAFT");
            });
            assertThat(java.util.Arrays.stream(ids).distinct().count()).isEqualTo(USERS);
            wave(i -> assertThat(request("repeatStart", "POST", "/api/assessment/sessions", tokens[i], Map.of("scaleId", scale.id(), "consentAccepted", true), 200, 200).path("id").asLong()).isEqualTo(ids[i]));
            wave(i -> {
                var partial = partialAnswers(scale, TYPES.get(i % TYPES.size()));
                var data = request("savePartial", "PUT", "/api/assessment/sessions/" + ids[i] + "/answers", tokens[i], Map.of("revision", 0, "answers", partial), 200, 200);
                assertThat(data.path("revision").asInt()).isEqualTo(1);
                assertThat(data.path("answers").size()).isEqualTo(8);
            });
            wave(i -> {
                var data = request("resume", "GET", "/api/assessment/current", tokens[i], null, 200, 200);
                assertThat(data.path("id").asLong()).isEqualTo(ids[i]);
                assertThat(data.path("revision").asInt()).isEqualTo(1);
                assertThat(data.path("answers")).isEqualTo(json.valueToTree(partialAnswers(scale, TYPES.get(i % TYPES.size()))));
            });
            wave(i -> {
                var data = request("saveComplete", "PUT", "/api/assessment/sessions/" + ids[i] + "/answers", tokens[i], Map.of("revision", 1, "answers", answers(scale, TYPES.get(i % TYPES.size()))), 200, 200);
                assertThat(data.path("revision").asInt()).isEqualTo(2);
            });
            wave(i -> {
                var data = request("submit", "POST", "/api/assessment/sessions/" + ids[i] + "/submit", tokens[i], Map.of("revision", 2, "answers", answers(scale, TYPES.get(i % TYPES.size()))), 200, 200);
                assertThat(data.path("id").asLong()).isEqualTo(ids[i]);
                assertThat(data.path("userId").asLong()).isEqualTo(people.get(i).id());
                assertThat(data.path("typeCode").asText()).isEqualTo(TYPES.get(i % TYPES.size()));
                assertThat(data.path("contentHash").asText()).isEqualTo(scale.contentHash());
                assertThat(data.path("report").path("reportVersion").asText()).isEqualTo(AssessmentConstants.REPORT_VERSION);
                assertThat(data.path("report").path("graphExplanation").path("graphVersion").asText()).isNotBlank();
                assertThat(data.path("report").path("growthPlans").size()).isGreaterThan(0);
                submitted[i] = data;
            });
            wave(i -> assertThat(request("repeatSubmit", "POST", "/api/assessment/sessions/" + ids[i] + "/submit", tokens[i], Map.of("revision", 2, "answers", answers(scale, TYPES.get(i % TYPES.size()))), 200, 200)).isEqualTo(submitted[i]));
            wave(i -> assertThat(request("report", "GET", "/api/assessment/results/" + ids[i], tokens[i], null, 200, 200)).isEqualTo(submitted[i]));
            wave(i -> {
                var data = request("history", "GET", "/api/assessment/history", tokens[i], null, 200, 200);
                assertThat(data.path("total").asInt()).isEqualTo(1);
                assertThat(data.path("records").get(0).path("id").asLong()).isEqualTo(ids[i]);
            });
            wave(i -> request("otherReportDenied", "GET", "/api/assessment/results/" + ids[(i + 1) % USERS], tokens[i], null, 200, 400));
            wave(i -> request("adminDenied", "GET", "/api/admin/talent/dashboard", tokens[i], null, 403, 0));
            wave(i -> {
                var data = request("finishedCurrent", "GET", "/api/assessment/current", tokens[i], null, 200, 200);
                assertThat(data.isMissingNode() || data.isNull()).isTrue();
            });
            checks.put("fiftyDistinctUsersAndSessions", true);
            checks.put("savedProgressResumedAndRevisionAdvanced", true);
            checks.put("expectedTypesAcrossAllSixteenTypes", true);
            checks.put("repeatedStartAndSubmitAreIdempotent", true);
            checks.put("reportAndTemplateSnapshotsRemainIdentical", true);
            checks.put("historyOwnershipAndAdminPermissions", true);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_session", Long.class)).isEqualTo(USERS);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM assessment_session WHERE status='COMPLETED' AND result_json IS NOT NULL AND JSON_LENGTH(answers_json)=32", Long.class)).isEqualTo(USERS);
            assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT user_id) FROM assessment_session", Long.class)).isEqualTo(USERS);
            checks.put("databaseCompletedSessions", USERS);
            checks.put("databaseDuplicateOrIncompleteSessions", 0);
            var dashboard = request("adminDashboard", "GET", "/api/admin/talent/dashboard", adminToken, null, 200, 200);
            assertThat(dashboard.path("employeeCount").asLong()).isEqualTo(population);
            assertThat(dashboard.path("completedCount").asLong()).isEqualTo(USERS);
            assertThat(dashboard.path("assessmentCount").asLong()).isEqualTo(USERS);
            for (String type : TYPES) {
                long expected = java.util.stream.IntStream.range(0, USERS).filter(i -> TYPES.get(i % TYPES.size()).equals(type)).count();
                var actual = new ArrayList<JsonNode>();
                dashboard.path("typeDistribution").forEach(actual::add);
                assertThat(actual.stream().filter(v -> type.equals(v.path("typeCode").asText())).findFirst().orElseThrow().path("employeeCount").asLong()).isEqualTo(expected);
            }
            checks.put("adminDashboardCountsAndDistribution", true);
            assertThat(samples).allMatch(Sample::success);
            for (String phase : Set.copyOf(peakInFlight.keySet())) {
                var times = samples.stream().filter(s -> s.phase().equals(phase)).map(Sample::milliseconds).sorted().toList();
                assertThat(percentile(times, .95)).as(phase + " P95 <= " + p95Limit + "ms").isLessThanOrEqualTo(p95Limit);
            }
            assertThat(peakInFlight.values().stream().mapToInt(Integer::intValue).max().orElse(0)).as("Measured peak of 50 client requests in flight").isEqualTo(USERS);
            checks.put("p95WithinConfiguredLimit", true);
            passed = true;
        } finally { writeReport(start, passed, checks, p95Limit); }
    }
}
