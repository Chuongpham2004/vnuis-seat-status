package vn.vnuis.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.vnuis.library.domain.Enums.Role;
import vn.vnuis.library.domain.FaceVerificationLog;
import vn.vnuis.library.domain.User;
import vn.vnuis.library.face.FaceServiceClient;
import vn.vnuis.library.face.FaceServiceClient.AnalyzeResult;
import vn.vnuis.library.repository.FaceVerificationLogRepository;
import vn.vnuis.library.repository.ReservationRepository;

/**
 * Booking behind the face check: enrol -> challenge -> verify -> one-time token -> reservation.
 * face-service is mocked; its own tests cover detection, liveness and embeddings.
 */
@TestPropertySource(properties = {"app.face.enabled=true", "app.face.max-failures=3"})
class FaceVerificationIT extends AbstractIntegrationTest {

    private static final String SEAT = "B-15";

    @MockitoBean
    FaceServiceClient faceService;

    @Autowired
    FaceVerificationLogRepository faceLogs;

    @Autowired
    ReservationRepository reservations;

    private final List<User> created = new ArrayList<>();

    @BeforeEach
    void faceServiceSeesTheEnrolledStudent() {
        when(faceService.analyze(anyList(), anyList())).thenReturn(ok(vector(1)));
    }

    @AfterEach
    void cleanUp() {
        for (User u : created) {
            reservations.findFirstByUserIdAndStatusIn(u.getId(), vn.vnuis.library.domain.ReservationStatus.OPEN)
                    .ifPresent(r -> call(HttpMethod.POST, "/reservations/" + r.getId() + "/cancel",
                            jwt.issue(u), null));
        }
    }

    // ------------------------------------------------------------------ tests

    @Test
    void bookingWithoutAFaceTokenIsRejected() {
        String token = jwt.issue(student());
        ResponseEntity<String> r = book(token, SEAT);
        assertThat(r.getStatusCode().value()).isEqualTo(403);
        assertThat(json(r).get("code").asText()).isEqualTo("FACE_VERIFICATION_REQUIRED");
    }

    @Test
    void enrolVerifyAndBookWithAOneTimeToken() {
        User user = student();
        String token = jwt.issue(user);
        assertThat(json(call(HttpMethod.GET, "/auth/me", token, null)).get("faceEnrolled").asBoolean()).isFalse();

        enrol(token);
        assertThat(json(call(HttpMethod.GET, "/auth/me", token, null)).get("faceEnrolled").asBoolean()).isTrue();

        when(faceService.analyze(anyList(), anyList())).thenReturn(ok(noisy(vector(1), 0.3)));
        JsonNode verified = json(verify(token));
        assertThat(verified.get("score").asDouble()).isGreaterThan(0.45);
        String faceToken = verified.get("faceToken").asText();

        ResponseEntity<String> booked = call(HttpMethod.POST, "/reservations", token,
                Map.of("seatCode", SEAT, "faceToken", faceToken));
        assertThat(booked.getStatusCode().value()).isEqualTo(201);
        call(HttpMethod.POST, "/reservations/" + idOf(booked) + "/cancel", token, null);

        // spent: the same token cannot book again
        ResponseEntity<String> again = call(HttpMethod.POST, "/reservations", token,
                Map.of("seatCode", SEAT, "faceToken", faceToken));
        assertThat(json(again).get("code").asText()).isEqualTo("FACE_VERIFICATION_REQUIRED");

        assertThat(faceLogs.findByUserIdOrderByCreatedAtDesc(user.getId()))
                .extracting(FaceVerificationLog::getResult).containsExactly("OK", "OK");
    }

    @Test
    void anotherPersonsFaceIsRejectedAndLogged() {
        User user = student();
        String token = jwt.issue(user);
        enrol(token);
        when(faceService.analyze(anyList(), anyList())).thenReturn(ok(vector(2)));
        ResponseEntity<String> r = verify(token);
        assertThat(r.getStatusCode().value()).isEqualTo(422);
        assertThat(json(r).get("code").asText()).isEqualTo("FACE_NOT_MATCHED");
        assertThat(faceLogs.findByUserIdOrderByCreatedAtDesc(user.getId()).getFirst().getResult())
                .isEqualTo("FACE_NOT_MATCHED");
    }

    @Test
    void aTokenOnlyWorksForTheStudentWhoEarnedIt() {
        String alice = jwt.issue(student());
        String bob = jwt.issue(student());
        enrol(alice);
        String faceToken = json(verify(alice)).get("faceToken").asText();
        ResponseEntity<String> r = call(HttpMethod.POST, "/reservations", bob,
                Map.of("seatCode", SEAT, "faceToken", faceToken));
        assertThat(r.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void chatbotBookingNeedsTheFaceTokenToo() {
        String token = jwt.issue(student());
        ResponseEntity<String> r = call(HttpMethod.POST, "/ai-tools/reservations", token, Map.of("seatCode", SEAT));
        assertThat(json(r).get("code").asText()).isEqualTo("FACE_VERIFICATION_REQUIRED");
    }

    @Test
    void verifyingBeforeEnrolmentAsksToEnrol() {
        ResponseEntity<String> r = verify(jwt.issue(student()));
        assertThat(r.getStatusCode().value()).isEqualTo(409);
        assertThat(json(r).get("code").asText()).isEqualTo("FACE_NOT_ENROLLED");
    }

    @Test
    void challengesAreSingleUseAndStartFrontal() {
        String token = jwt.issue(student());
        JsonNode challenge = json(call(HttpMethod.POST, "/face/challenge", token, null));
        assertThat(challenge.get("steps").get(0).asText()).isEqualTo("CENTER");
        assertThat(challenge.get("steps")).hasSize(3);
        String id = challenge.get("challengeId").asText();
        assertThat(call(HttpMethod.POST, "/face/enroll", token, enrolBody(id, true)).getStatusCode().value())
                .isEqualTo(200);
        ResponseEntity<String> replay = call(HttpMethod.POST, "/face/verify", token, verifyBody(id));
        assertThat(replay.getStatusCode().value()).isEqualTo(410);
    }

    @Test
    void someoneElsesChallengeCannotBeAnswered() {
        String alice = jwt.issue(student());
        String bob = jwt.issue(student());
        String id = json(call(HttpMethod.POST, "/face/challenge", alice, null)).get("challengeId").asText();
        assertThat(call(HttpMethod.POST, "/face/enroll", bob, enrolBody(id, true)).getStatusCode().value())
                .isEqualTo(410);
    }

    @Test
    void enrolmentNeedsConsentAndHappensOnce() {
        String token = jwt.issue(student());
        String id = challengeId(token);
        ResponseEntity<String> noConsent = call(HttpMethod.POST, "/face/enroll", token, enrolBody(id, false));
        assertThat(json(noConsent).get("code").asText()).isEqualTo("CONSENT_REQUIRED");
        enrol(token);
        ResponseEntity<String> twice = call(HttpMethod.POST, "/face/enroll", token, enrolBody(challengeId(token), true));
        assertThat(json(twice).get("code").asText()).isEqualTo("FACE_ALREADY_ENROLLED");
    }

    @Test
    void livenessFailureIsReportedAndRepeatedFailuresLockTheStudentOut() {
        String token = jwt.issue(student());
        enrol(token);
        when(faceService.analyze(anyList(), anyList()))
                .thenReturn(new AnalyzeResult("LIVENESS_FAILED", "step 2 (LEFT) not observed", null, null, "m", 50));
        for (int i = 0; i < 3; i++) {
            ResponseEntity<String> r = verify(token);
            assertThat(json(r).get("code").asText()).isEqualTo("FACE_LIVENESS_FAILED");
        }
        ResponseEntity<String> locked = call(HttpMethod.POST, "/face/challenge", token, null);
        assertThat(locked.getStatusCode().value()).isEqualTo(429);
        assertThat(json(locked).get("code").asText()).isEqualTo("FACE_VERIFY_LOCKED");
    }

    @Test
    void noFaceDoesNotCountTowardsTheLockout() {
        String token = jwt.issue(student());
        enrol(token);
        when(faceService.analyze(anyList(), anyList()))
                .thenReturn(new AnalyzeResult("NO_FACE", "no frontal face found", null, null, "m", 50));
        for (int i = 0; i < 4; i++) {
            assertThat(json(verify(token)).get("code").asText()).isEqualTo("NO_FACE");
        }
        assertThat(call(HttpMethod.POST, "/face/challenge", token, null).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void librarianResetAndStudentWithdrawal() {
        User user = student();
        String token = jwt.issue(user);
        enrol(token);
        assertThat(call(HttpMethod.DELETE, "/admin/users/" + user.getId() + "/face", token, null)
                .getStatusCode().value()).isEqualTo(403);
        assertThat(call(HttpMethod.DELETE, "/admin/users/" + user.getId() + "/face", tokenFor("ADMIN001"), null)
                .getStatusCode().value()).isEqualTo(204);
        assertThat(json(call(HttpMethod.GET, "/face/me", token, null)).get("enrolled").asBoolean()).isFalse();

        enrol(token);
        assertThat(call(HttpMethod.DELETE, "/face/me", token, null).getStatusCode().value()).isEqualTo(204);
        assertThat(json(call(HttpMethod.GET, "/face/me", token, null)).get("enrolled").asBoolean()).isFalse();
    }

    // ------------------------------------------------------------------ helpers

    private User student() {
        String code = "F" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        User u = users.save(new User(code, code.toLowerCase() + "@test.vnuis.edu.vn", "x", "Face " + code,
                Role.STUDENT, 100));
        created.add(u);
        return u;
    }

    private String challengeId(String token) {
        return json(call(HttpMethod.POST, "/face/challenge", token, null)).get("challengeId").asText();
    }

    private void enrol(String token) {
        when(faceService.analyze(anyList(), anyList())).thenReturn(ok(vector(1)));
        ResponseEntity<String> r = call(HttpMethod.POST, "/face/enroll", token, enrolBody(challengeId(token), true));
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(200);
    }

    private ResponseEntity<String> verify(String token) {
        return call(HttpMethod.POST, "/face/verify", token, verifyBody(challengeId(token)));
    }

    private static Map<String, Object> enrolBody(String challengeId, boolean consent) {
        return Map.of("challengeId", challengeId, "consent", consent, "frames", frames());
    }

    private static Map<String, Object> verifyBody(String challengeId) {
        return Map.of("challengeId", challengeId, "frames", frames());
    }

    private static List<Map<String, String>> frames() {
        return List.of(Map.of("step", "CENTER", "image", "aGVsbG8gd29ybGQgZnJhbWU="),
                Map.of("step", "LEFT", "image", "aGVsbG8gd29ybGQgZnJhbWU="),
                Map.of("step", "RIGHT", "image", "aGVsbG8gd29ybGQgZnJhbWU="));
    }

    private static AnalyzeResult ok(float[] embedding) {
        return new AnalyzeResult("OK", null, embedding, 0.9, "yolov8n-face+2d106det+w600k_mbf", 120);
    }

    /** Deterministic unit vector per seed (different seeds are nearly orthogonal in 512-d). */
    private static float[] vector(long seed) {
        java.util.Random rnd = new java.util.Random(seed);
        float[] v = new float[512];
        double n = 0;
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) rnd.nextGaussian();
            n += v[i] * v[i];
        }
        for (int i = 0; i < v.length; i++) {
            v[i] /= (float) Math.sqrt(n);
        }
        return v;
    }

    /** Same identity under a different camera frame: cosine ~ 1 / sqrt(1 + amount^2). */
    private static float[] noisy(float[] base, double amount) {
        float[] noise = vector(99);
        float[] v = new float[base.length];
        for (int i = 0; i < v.length; i++) {
            v[i] = base[i] + (float) amount * noise[i];
        }
        return v;
    }
}
