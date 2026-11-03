package vn.vnuis.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.vnuis.library.domain.Enums.BookingSource;
import vn.vnuis.library.domain.Enums.Role;
import vn.vnuis.library.domain.User;
import vn.vnuis.library.domain.Reservation;
import vn.vnuis.library.domain.ReservationStatus;
import vn.vnuis.library.domain.SeatStatus;
import vn.vnuis.library.repository.ReservationRepository;
import vn.vnuis.library.repository.SeatRepository;
import vn.vnuis.library.seat.SeatCatalog;
import vn.vnuis.library.seat.SeatStateBootstrapper;
import vn.vnuis.library.seat.SeatStateStore;

/** A sensor that goes OFFLINE must never leave its seat frozen in OCCUPIED. */
class OfflineDeviceIT extends AbstractIntegrationTest {

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
    SeatStateBootstrapper bootstrapper;
    @Autowired
    PasswordEncoder passwordEncoder;

    /** Each test gets its own student so no open reservation leaks into other test classes. */
    private String freshStudent(String code) {
        User u = users.saveAndFlush(new User(code, code.toLowerCase() + "@test.local", passwordEncoder.encode("x"),
                "Offline " + code, Role.STUDENT, 100));
        return jwt.issue(u);
    }

    private void lastWill(String seatCode) {
        publish("library/zone/" + seatCode.charAt(0) + "/seat/" + seatCode + "/status",
                "{\"deviceId\":\"SIM-" + seatCode.replace("-", "") + "\",\"status\":\"OFFLINE\"}");
    }

    @Test
    void occupiedSeatGoesAwayWhenItsSensorDies_andIsAutoReleased() {
        String token = freshStudent("OFF01");
        ResponseEntity<String> booked = book(token, "C-17");
        assertThat(booked.getStatusCode().value()).as(booked.getBody()).isEqualTo(201);
        UUID rid = idOf(booked);
        sensor("C-17", true);
        await().atMost(TIMEOUT).until(() -> store.require("C-17").physicalOccupied());
        assertThat(checkIn(token, rid, catalog.require("C-17").qrCodeToken()).getStatusCode().value()).isEqualTo(200);
        assertThat(store.require("C-17").status()).isEqualTo(SeatStatus.OCCUPIED);

        lastWill("C-17"); // broker publishes the Last Will: device OFFLINE

        // Immediately AWAY (no 1-min absence grace: presence can no longer be observed)
        await().atMost(Duration.ofSeconds(5)).until(() -> store.require("C-17").status() == SeatStatus.AWAY);
        assertThat(store.require("C-17").deviceOnline()).isFalse();
        assertThat(store.require("C-17").physicalOccupied()).isFalse();
        assertThat(seats.findBySeatCode("C-17").orElseThrow().getAwaySince()).isNotNull();

        // ...and the normal away timeout releases it
        await().atMost(TIMEOUT).until(() -> store.require("C-17").status() == SeatStatus.AVAILABLE);
        assertThat(reservations.findById(rid).orElseThrow().getStatus()).isEqualTo(ReservationStatus.AUTO_RELEASED_AWAY);
    }

    @Test
    void sensorComingBackWhileTheStudentIsStillThere_restoresOccupied() {
        String token = freshStudent("OFF02");
        ResponseEntity<String> booked = book(token, "C-18");
        UUID rid = idOf(booked);
        sensor("C-18", true);
        await().atMost(TIMEOUT).until(() -> store.require("C-18").physicalOccupied());
        checkIn(token, rid, catalog.require("C-18").qrCodeToken());

        lastWill("C-18");
        await().atMost(Duration.ofSeconds(5)).until(() -> store.require("C-18").status() == SeatStatus.AWAY);

        sensor("C-18", true); // device reconnects; the student never left
        await().atMost(TIMEOUT).until(() -> store.require("C-18").status() == SeatStatus.OCCUPIED);
        assertThat(store.require("C-18").deviceOnline()).isTrue();
        assertThat(reservations.findById(rid).orElseThrow().getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        call(org.springframework.http.HttpMethod.POST, "/reservations/" + rid + "/check-out", token, null);
        sensor("C-18", false);
    }

    @Test
    void startupRecoversSeatsStuckOccupiedWithAnOfflineDevice() {
        // A walk-in whose device died while the backend was down: OCCUPIED in both stores, device OFFLINE.
        sensor("A-06", true);
        await().atMost(TIMEOUT).until(() -> store.require("A-06").status() == SeatStatus.OCCUPIED);
        Reservation walkIn = reservations
                .findFirstBySeatIdAndStatusIn(catalog.require("A-06").seatId(), ReservationStatus.OPEN).orElseThrow();
        assertThat(walkIn.getBookingSource()).isEqualTo(BookingSource.WALK_IN);
        store.updateDeviceOnline("A-06", false); // offline, and no Last Will was ever processed

        bootstrapper.recoverSeatsWithOfflineDevices();

        assertThat(store.require("A-06").status()).isEqualTo(SeatStatus.AWAY);
        await().atMost(TIMEOUT).until(() -> store.require("A-06").status() == SeatStatus.AVAILABLE);
        assertThat(reservations.findById(walkIn.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.AUTO_RELEASED_AWAY);
    }
}
