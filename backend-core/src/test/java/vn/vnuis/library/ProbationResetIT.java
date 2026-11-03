package vn.vnuis.library;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.vnuis.library.credit.CreditService;
import vn.vnuis.library.domain.CreditLog;
import vn.vnuis.library.domain.Enums.Role;
import vn.vnuis.library.domain.User;
import vn.vnuis.library.repository.CreditLogRepository;

/** Ban-expiry probation reset: a student below 60 whose ban has ended is put back at 60. */
class ProbationResetIT extends AbstractIntegrationTest {

    @Autowired
    CreditLogRepository creditLogs;
    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void sv005_banExpiredAt55_isResetTo60_andCanBookAgain() {
        User sv005 = users.findByStudentCodeIgnoreCase("SV005").orElseThrow();
        try {
            // State right after a no-show ban has run out
            sv005.setCreditScore(55);
            sv005.setBannedUntil(Instant.now().minusSeconds(60));
            users.saveAndFlush(sv005);
            String token = tokenFor("SV005");

            ResponseEntity<String> booked = book(token, "C-14");
            assertThat(booked.getStatusCode().value()).as(booked.getBody()).isEqualTo(201);
            UUID rid = idOf(booked);

            User after = users.findById(sv005.getId()).orElseThrow();
            assertThat(after.getCreditScore()).isEqualTo(60);
            assertThat(after.getBannedUntil()).isNull();

            CreditLog log = latestLog(sv005.getId());
            assertThat(log.getReason()).isEqualTo(CreditService.REASON_PROBATION);
            assertThat(log.getPointDelta()).isEqualTo(5);
            assertThat(log.getBalanceAfter()).isEqualTo(60);
            assertThat(log.getReservationId()).isNull();

            call(HttpMethod.POST, "/reservations/" + rid + "/cancel", token, null);
        } finally {
            // Restore the seed state other tests rely on (65 credit, no ban).
            User restore = users.findById(sv005.getId()).orElseThrow();
            restore.setCreditScore(65);
            restore.setBannedUntil(null);
            users.saveAndFlush(restore);
        }
    }

    @Test
    void resetIsAppliedWhenTheProfileIsRead_andOnlyOnce() {
        User u = users.saveAndFlush(new User("PROB01", "prob01@test.local", passwordEncoder.encode("x"),
                "Probation", Role.STUDENT, 40));
        u.setBannedUntil(Instant.now().minusSeconds(5));
        users.saveAndFlush(u);
        String token = jwt.issue(u);

        JsonNode me = json(call(HttpMethod.GET, "/auth/me", token, null));
        assertThat(me.get("creditScore").asInt()).isEqualTo(60);
        assertThat(me.has("bannedUntil")).isFalse(); // null fields are omitted
        assertThat(me.get("canBook").asBoolean()).isTrue();

        call(HttpMethod.GET, "/auth/me", token, null);
        List<CreditLog> logs = creditLogs.findByUserIdOrderByCreatedAtDesc(u.getId(), PageRequest.of(0, 10));
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getPointDelta()).isEqualTo(20);
    }

    @Test
    void noResetWhileTheBanIsStillActive() {
        User u = users.saveAndFlush(new User("PROB02", "prob02@test.local", passwordEncoder.encode("x"),
                "Still Banned", Role.STUDENT, 50));
        u.setBannedUntil(Instant.now().plusSeconds(3600));
        users.saveAndFlush(u);
        String token = jwt.issue(u);

        JsonNode me = json(call(HttpMethod.GET, "/auth/me", token, null));
        assertThat(me.get("creditScore").asInt()).isEqualTo(50);
        assertThat(me.get("canBook").asBoolean()).isFalse();
        assertThat(book(token, "C-16").getStatusCode().value()).isEqualTo(403);
        assertThat(creditLogs.findByUserIdOrderByCreatedAtDesc(u.getId(), PageRequest.of(0, 10))).isEmpty();
    }

    private CreditLog latestLog(UUID userId) {
        return creditLogs.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 1)).get(0);
    }
}
