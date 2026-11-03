package vn.vnuis.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.Type;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import vn.vnuis.library.domain.CreditLog;
import vn.vnuis.library.domain.Enums.BookingSource;
import vn.vnuis.library.domain.Enums.Connectivity;
import vn.vnuis.library.domain.Enums.Role;
import vn.vnuis.library.domain.Reservation;
import vn.vnuis.library.domain.ReservationStatus;
import vn.vnuis.library.domain.SeatStatus;
import vn.vnuis.library.domain.User;
import vn.vnuis.library.repository.CreditLogRepository;
import vn.vnuis.library.repository.IotDeviceRepository;
import vn.vnuis.library.repository.ReservationRepository;
import vn.vnuis.library.repository.SeatRepository;
import vn.vnuis.library.seat.SeatCatalog;
import vn.vnuis.library.seat.SeatState;
import vn.vnuis.library.seat.SeatStateBootstrapper;
import vn.vnuis.library.seat.SeatStateStore;
import vn.vnuis.library.seat.SeatTimerService;

/**
 * Phase 2 Definition of Done. Each test uses its own seat and student, so they are
 * independent even though they share one application context and one set of containers.
 * Timers (see {@link AbstractIntegrationTest}): reservation 5 s, away 3 s, walk-in 2 s, away-grace 1 s.
 */
class SeatLifecycleIT extends AbstractIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    SeatCatalog catalog;
    @Autowired
    SeatStateStore store;
    @Autowired
    ReservationRepository reservations;
    @Autowired
    SeatRepository seats;
    @Autowired
    IotDeviceRepository devices;
    @Autowired
    CreditLogRepository creditLogs;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    SeatStateBootstrapper bootstrapper;
    @Autowired
    PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------------ 1. concurrency

    @Test
    void concurrentBookingsOnOneSeat_exactlyOneWins() throws Exception {
        int contenders = 25;
        String hash = passwordEncoder.encode("Password@123");
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            User u = users.saveAndFlush(new User(
                    "LT%03d".formatted(i), "lt%03d@test.local".formatted(i), hash, "Load Test " + i, Role.STUDENT, 100));
            tokens.add(jwt.issue(u));
        }

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
        for (String token : tokens) {
            futures.add(pool.submit(() -> {
                startGate.await();
                return book(token, "A-01");
            }));
        }
        startGate.countDown(); // release all requests at once
        List<ResponseEntity<String>> responses = new ArrayList<>();
        for (Future<ResponseEntity<String>> f : futures) {
            responses.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        long created = responses.stream().filter(r -> r.getStatusCode().value() == 201).count();
        long conflicts = responses.stream().filter(r -> r.getStatusCode().value() == 409).count();
        assertThat(created).as("201 Created").isEqualTo(1);
        assertThat(conflicts).as("409 Conflict").isEqualTo(contenders - 1);

        UUID seatId = catalog.require("A-01").seatId();
        Reservation winner = reservations.findFirstBySeatIdAndStatusIn(seatId, ReservationStatus.OPEN).orElseThrow();
        assertThat(winner.getStatus()).isEqualTo(ReservationStatus.PENDING);
        SeatState state = store.require("A-01");
        assertThat(state.status()).isEqualTo(SeatStatus.RESERVED);
        assertThat(state.currentReservationId()).isEqualTo(winner.getId().toString());
    }

    // ------------------------------------------------------------------ 2. dual check-in

    @Test
    void dualCheckIn_requiresQrAndPhysicalPresence_thenCheckoutRewards() {
        String token = tokenFor("SV004"); // 78 credit
        String qr = catalog.require("B-02").qrCodeToken();
        sensor("B-02", false);

        ResponseEntity<String> booked = book(token, "B-02");
        assertThat(booked.getStatusCode().value()).isEqualTo(201);
        UUID rid = idOf(booked);
        assertThat(redis.hasKey(SeatTimerService.RESERVATION_EXPIRE + rid)).isTrue();

        // Correct QR but nobody on the seat -> rejected
        ResponseEntity<String> noPresence = checkIn(token, rid, qr);
        assertThat(noPresence.getStatusCode().value()).isEqualTo(409);
        assertThat(json(noPresence).get("code").asText()).isEqualTo("NO_PHYSICAL_PRESENCE");

        // Wrong QR -> rejected
        ResponseEntity<String> wrongQr = checkIn(token, rid, "not-the-token");
        assertThat(wrongQr.getStatusCode().value()).isEqualTo(400);
        assertThat(json(wrongQr).get("code").asText()).isEqualTo("INVALID_QR_CODE");

        // HC-SR04 sees the student (< 50 cm) -> physicalOccupied == "1"
        sensor("B-02", true);
        await().atMost(TIMEOUT).until(() -> store.require("B-02").physicalOccupied());
        assertThat(redis.opsForHash().get(SeatStateStore.key("B-02"), "physicalOccupied")).isEqualTo("1");

        ResponseEntity<String> ok = checkIn(token, rid, qr);
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat(json(ok).get("status").asText()).isEqualTo("ACTIVE");
        assertThat(store.require("B-02").status()).isEqualTo(SeatStatus.OCCUPIED);
        assertThat(redis.hasKey(SeatTimerService.RESERVATION_EXPIRE + rid)).as("expire timer deleted").isFalse();

        // Proper check-out after an on-time check-in: +2 credit
        ResponseEntity<String> out = call(HttpMethod.POST, "/reservations/" + rid + "/check-out", token, null);
        assertThat(out.getStatusCode().value()).isEqualTo(200);
        assertThat(json(out).get("status").asText()).isEqualTo("COMPLETED");
        assertThat(users.findByStudentCodeIgnoreCase("SV004").orElseThrow().getCreditScore()).isEqualTo(80);
        assertThat(creditLogs.findByReservationId(rid)).extracting(CreditLog::getPointDelta).containsExactly(2);
        sensor("B-02", false);
    }

    // ------------------------------------------------------------------ 3. no-show + ban

    @Test
    void noShow_expiresReservation_deductsCredit_andBansBelow60() {
        String token = tokenFor("SV005"); // 65 credit
        ResponseEntity<String> booked = book(token, "C-12");
        assertThat(booked.getStatusCode().value()).isEqualTo(201);
        UUID rid = idOf(booked);
        assertThat(store.require("C-12").status()).isEqualTo(SeatStatus.RESERVED);

        // Nobody checks in; the 5 s Redis TTL key expires.
        await().atMost(TIMEOUT).until(() ->
                reservations.findById(rid).orElseThrow().getStatus() == ReservationStatus.EXPIRED_NO_SHOW);

        assertThat(store.require("C-12").status()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seats.findBySeatCode("C-12").orElseThrow().getCurrentStatus()).isEqualTo(SeatStatus.AVAILABLE);

        User sv005 = users.findByStudentCodeIgnoreCase("SV005").orElseThrow();
        assertThat(sv005.getCreditScore()).isEqualTo(55);
        Instant expectedBanEnd = Instant.now().plus(Duration.ofDays(3));
        assertThat(sv005.getBannedUntil()).isBetween(expectedBanEnd.minusSeconds(120), expectedBanEnd.plusSeconds(5));
        assertThat(creditLogs.findByReservationId(rid))
                .extracting(CreditLog::getPointDelta, CreditLog::getReason)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(-10, "NO_SHOW"));

        // Banned: advance booking refused
        ResponseEntity<String> again = book(token, "C-13");
        assertThat(again.getStatusCode().value()).isEqualTo(403);
        assertThat(json(again).get("code").asText()).isEqualTo("USER_BANNED");
    }

    // ------------------------------------------------------------------ 4. away timeout

    @Test
    void away_thenTimeout_autoReleasesSeat() {
        String token = tokenFor("SV002");
        UUID rid = bookAndCheckIn(token, "C-15");

        sensor("C-15", false); // > 50 cm: student left
        await().atMost(TIMEOUT).until(() -> store.require("C-15").status() == SeatStatus.AWAY);
        assertThat(seats.findBySeatCode("C-15").orElseThrow().getAwaySince()).isNotNull();

        await().atMost(TIMEOUT).until(() -> store.require("C-15").status() == SeatStatus.AVAILABLE);
        Reservation r = reservations.findById(rid).orElseThrow();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.AUTO_RELEASED_AWAY);
        assertThat(r.getCheckOutAt()).isNotNull();
        assertThat(seats.findBySeatCode("C-15").orElseThrow().getCurrentStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(store.require("C-15").currentReservationId()).isNull();
    }

    @Test
    void away_thenReturn_revertsToOccupied() {
        String token = tokenFor("SV003");
        UUID rid = bookAndCheckIn(token, "B-07");

        sensor("B-07", false);
        await().atMost(TIMEOUT).until(() -> store.require("B-07").status() == SeatStatus.AWAY);
        assertThat(redis.hasKey(SeatTimerService.SEAT_AWAY + "B-07")).isTrue();

        sensor("B-07", true); // back within the away window
        await().atMost(TIMEOUT).until(() -> store.require("B-07").status() == SeatStatus.OCCUPIED);
        assertThat(redis.hasKey(SeatTimerService.SEAT_AWAY + "B-07")).as("away timer deleted").isFalse();
        assertThat(reservations.findById(rid).orElseThrow().getStatus()).isEqualTo(ReservationStatus.ACTIVE);

        call(HttpMethod.POST, "/reservations/" + rid + "/check-out", token, null);
        sensor("B-07", false);
    }

    // ------------------------------------------------------------------ walk-in

    @Test
    void walkIn_presenceHeldOnAvailableSeat_becomesOccupied() {
        sensor("A-03", true);
        await().atMost(TIMEOUT).until(() -> store.require("A-03").status() == SeatStatus.OCCUPIED);
        Reservation walkIn = reservations
                .findFirstBySeatIdAndStatusIn(catalog.require("A-03").seatId(), ReservationStatus.OPEN).orElseThrow();
        assertThat(walkIn.getBookingSource()).isEqualTo(BookingSource.WALK_IN);
        assertThat(walkIn.getUserId()).isNull();
        assertThat(walkIn.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
    }

    // ------------------------------------------------------------------ startup sync

    @Test
    void bootstrap_rebuildsRedisFromPostgres() {
        String hash = passwordEncoder.encode("Password@123");
        User u = users.saveAndFlush(new User("BOOT01", "boot01@test.local", hash, "Bootstrap", Role.STUDENT, 100));
        ResponseEntity<String> booked = book(jwt.issue(u), "A-08");
        UUID rid = idOf(booked);

        // Simulate a Redis wipe
        redis.delete(SeatStateStore.key("A-08"));
        redis.delete(SeatTimerService.RESERVATION_EXPIRE + rid);
        assertThat(store.find("A-08")).isEmpty();

        bootstrapper.syncAll();

        SeatState restored = store.require("A-08");
        assertThat(restored.status()).isEqualTo(SeatStatus.RESERVED);
        assertThat(restored.currentReservationId()).isEqualTo(rid.toString());
        assertThat(restored.currentUserId()).isEqualTo(u.getId().toString());
        assertThat(redis.hasKey(SeatTimerService.RESERVATION_EXPIRE + rid)).as("timer re-armed").isTrue();
    }

    // ------------------------------------------------------------------ LWT

    @Test
    void lastWill_marksDeviceOfflineAndOnline() {
        publish("library/zone/C/seat/C-19/status", "{\"deviceId\":\"SIM-C19\",\"status\":\"ONLINE\"}");
        await().atMost(TIMEOUT).until(() -> store.require("C-19").deviceOnline());

        publish("library/zone/C/seat/C-19/status", "{\"deviceId\":\"SIM-C19\",\"status\":\"OFFLINE\"}");
        await().atMost(TIMEOUT).until(() -> !store.require("C-19").deviceOnline());
        assertThat(devices.findBySeatId(catalog.require("C-19").seatId()).orElseThrow().getConnectivityStatus())
                .isEqualTo(Connectivity.OFFLINE);
    }

    // ------------------------------------------------------------------ WebSocket

    @Test
    void websocket_broadcastsSeatChangesAndUserNotifications() throws Exception {
        String hash = passwordEncoder.encode("Password@123");
        User u = users.saveAndFlush(new User("WS01", "ws01@test.local", hash, "Socket", Role.STUDENT, 100));
        String token = jwt.issue(u);

        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(mapper);
        client.setMessageConverter(converter);
        StompHeaders connect = new StompHeaders();
        connect.add("Authorization", "Bearer " + token);
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                connect, new StompSessionHandlerAdapter() { }).get(10, TimeUnit.SECONDS);

        BlockingQueue<JsonNode> seatEvents = new LinkedBlockingQueue<>();
        BlockingQueue<JsonNode> notifications = new LinkedBlockingQueue<>();
        session.subscribe("/topic/seats", collector(seatEvents));
        session.subscribe("/user/queue/notifications", collector(notifications));
        Thread.sleep(500); // let the subscriptions register

        assertThat(book(token, "C-20").getStatusCode().value()).isEqualTo(201);

        JsonNode seatEvent = poll(seatEvents, "C-20");
        assertThat(seatEvent.get("status").asText()).isEqualTo("RESERVED");
        JsonNode note = notifications.poll(10, TimeUnit.SECONDS);
        assertThat(note).isNotNull();
        assertThat(note.get("type").asText()).isEqualTo("RESERVATION_CREATED");
        session.disconnect();
    }

    // ------------------------------------------------------------------ helpers

    private UUID bookAndCheckIn(String token, String seatCode) {
        ResponseEntity<String> booked = book(token, seatCode);
        assertThat(booked.getStatusCode().value()).as(booked.getBody()).isEqualTo(201);
        UUID rid = idOf(booked);
        sensor(seatCode, true);
        await().atMost(TIMEOUT).until(() -> store.require(seatCode).physicalOccupied());
        ResponseEntity<String> in = checkIn(token, rid, catalog.require(seatCode).qrCodeToken());
        assertThat(in.getStatusCode().value()).as(in.getBody()).isEqualTo(200);
        assertThat(store.require(seatCode).status()).isEqualTo(SeatStatus.OCCUPIED);
        return rid;
    }

    private StompFrameHandler collector(BlockingQueue<JsonNode> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((JsonNode) payload);
            }
        };
    }

    private static JsonNode poll(BlockingQueue<JsonNode> queue, String seatCode) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode n = queue.poll(500, TimeUnit.MILLISECONDS);
            if (n != null && Objects.equals(seatCode, n.path("seatCode").asText())) {
                return n;
            }
        }
        throw new AssertionError("No /topic/seats message for " + seatCode);
    }
}
