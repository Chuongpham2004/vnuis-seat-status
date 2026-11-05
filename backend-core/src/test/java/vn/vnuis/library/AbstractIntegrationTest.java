package vn.vnuis.library;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import vn.vnuis.library.domain.User;
import vn.vnuis.library.repository.UserRepository;
import vn.vnuis.library.security.JwtService;

/**
 * Boots the whole application against throwaway PostgreSQL 16, Redis 7 and Mosquitto
 * containers (shared by all integration tests) with very short timers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.seat.reservation-ttl-seconds=5",
        "app.seat.away-ttl-seconds=3",
        "app.seat.walkin-grace-seconds=2",
        "app.seat.away-grace-seconds=1",
        "app.seat.reconcile-interval-ms=2000",
})
public abstract class AbstractIntegrationTest {

    @SuppressWarnings("resource") // shared for the whole run; Testcontainers' Ryuk removes it
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("library");

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server", "--notify-keyspace-events", "Ex", "--hz", "50")
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2));

    @SuppressWarnings("resource")
    static final GenericContainer<?> MQTT = new GenericContainer<>("eclipse-mosquitto:2")
            .withExposedPorts(1883)
            .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
            .waitingFor(Wait.forLogMessage(".*mosquitto version .* running.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2));

    /** Stand-in for ai-service: echoes what the /api/v1/ai/** proxy forwarded. */
    static final com.sun.net.httpserver.HttpServer FAKE_AI;

    static {
        Startables.deepStart(POSTGRES, REDIS, MQTT).join();
        try {
            FAKE_AI = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        FAKE_AI.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            String json = "{\"method\":\"" + exchange.getRequestMethod() + "\",\"path\":\""
                    + exchange.getRequestURI().getPath() + "\",\"query\":\"" + exchange.getRequestURI().getRawQuery()
                    + "\",\"authForwarded\":" + (auth != null && auth.startsWith("Bearer "))
                    + ",\"bodyLength\":" + body.length() + "}";
            int status = body.contains("please-conflict") ? 409 : 200;
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        FAKE_AI.start();
    }

    private static MqttClient publisher;

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.mqtt.url", AbstractIntegrationTest::mqttUrl);
        registry.add("app.mqtt.username", () -> "");
        registry.add("app.ai.base-url", () -> "http://127.0.0.1:" + FAKE_AI.getAddress().getPort());
    }

    static String mqttUrl() {
        return "tcp://" + MQTT.getHost() + ":" + MQTT.getMappedPort(1883);
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected ObjectMapper mapper;

    @Autowired
    protected JwtService jwt;

    @Autowired
    protected UserRepository users;

    // ---------------------------------------------------------------- helpers

    protected String tokenFor(String studentCode) {
        User user = users.findByStudentCodeIgnoreCase(studentCode).orElseThrow();
        return jwt.issue(user);
    }

    protected ResponseEntity<String> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange("/api/v1" + path, method, new HttpEntity<>(body, headers), String.class);
    }

    protected ResponseEntity<String> book(String token, String seatCode) {
        return call(HttpMethod.POST, "/reservations", token, java.util.Map.of("seatCode", seatCode));
    }

    protected ResponseEntity<String> checkIn(String token, UUID reservationId, String qr) {
        return call(HttpMethod.POST, "/reservations/" + reservationId + "/check-in", token,
                java.util.Map.of("qrCodeToken", qr));
    }

    protected JsonNode json(ResponseEntity<String> response) {
        try {
            return mapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("Not JSON: " + response.getBody(), e);
        }
    }

    protected UUID idOf(ResponseEntity<String> response) {
        return UUID.fromString(json(response).get("id").asText());
    }

    /** Publishes a debounced sensor reading exactly like the ESP32 / simulator does. */
    protected void sensor(String seatCode, boolean present) {
        double distance = present ? 30.0 : 160.0;
        String zone = seatCode.substring(0, 1);
        publish("library/zone/" + zone + "/seat/" + seatCode + "/telemetry",
                "{\"deviceId\":\"SIM-" + seatCode.replace("-", "") + "\",\"seatCode\":\"" + seatCode
                        + "\",\"distanceCm\":" + distance + ",\"objectDetected\":" + present
                        + ",\"simulated\":true,\"ts\":" + System.currentTimeMillis() + "}");
    }

    protected synchronized void publish(String topic, String payload) {
        try {
            if (publisher == null || !publisher.isConnected()) {
                publisher = new MqttClient(mqttUrl(), "it-publisher-" + UUID.randomUUID(), new MemoryPersistence());
                MqttConnectOptions options = new MqttConnectOptions();
                options.setCleanSession(true);
                publisher.connect(options);
            }
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);
            publisher.publish(topic, message);
        } catch (Exception e) {
            throw new IllegalStateException("MQTT publish failed", e);
        }
    }
}
