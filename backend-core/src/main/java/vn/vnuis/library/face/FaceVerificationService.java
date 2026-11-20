package vn.vnuis.library.face;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vnuis.library.domain.FaceProfile;
import vn.vnuis.library.domain.FaceVerificationLog;
import vn.vnuis.library.domain.FaceVerificationLog.Purpose;
import vn.vnuis.library.error.ApiException;
import vn.vnuis.library.face.FaceChallengeStore.Challenge;
import vn.vnuis.library.face.FaceServiceClient.AnalyzeResult;
import vn.vnuis.library.face.FaceServiceClient.Frame;
import vn.vnuis.library.repository.FaceProfileRepository;
import vn.vnuis.library.repository.FaceVerificationLogRepository;
import vn.vnuis.library.repository.UserRepository;

/**
 * Enrolment and 1:1 verification. face-service does detection, liveness and the embedding;
 * the decision (cosine vs. the stored template), lockout and booking tokens live here.
 */
@Service
public class FaceVerificationService {

    private static final Logger log = LoggerFactory.getLogger(FaceVerificationService.class);

    public record Verified(String faceToken, double score, Instant expiresAt) {
    }

    public record Status(boolean enrolled, Instant enrolledAt, String model, boolean requiredForBooking) {
    }

    private final FaceProperties props;
    private final FaceServiceClient client;
    private final FaceChallengeStore store;
    private final FaceProfileRepository profiles;
    private final FaceVerificationLogRepository logs;
    private final UserRepository users;

    public FaceVerificationService(FaceProperties props, FaceServiceClient client, FaceChallengeStore store,
                                   FaceProfileRepository profiles, FaceVerificationLogRepository logs,
                                   UserRepository users) {
        this.props = props;
        this.client = client;
        this.store = store;
        this.profiles = profiles;
        this.logs = logs;
        this.users = users;
    }

    public boolean enabled() {
        return props.enabled();
    }

    public Challenge challenge(UUID userId) {
        requireNotLocked(userId);
        return store.create(userId);
    }

    public Status status(UUID userId) {
        return profiles.findById(userId)
                .map(p -> new Status(true, p.getEnrolledAt(), p.getModel(), props.enabled()))
                .orElseGet(() -> new Status(false, null, null, props.enabled()));
    }

    /**
     * First enrolment only: replacing a template needs a librarian reset (or the student withdrawing
     * consent), so a shared account cannot quietly swap in someone else's face.
     */
    @Transactional(noRollbackFor = ApiException.class) // failed attempts must still be logged
    public Status enroll(UUID userId, String challengeId, boolean consent, List<Frame> frames) {
        if (!consent) {
            throw ApiException.badRequest("CONSENT_REQUIRED", "Consent to biometric processing is required");
        }
        users.findById(userId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        if (profiles.existsById(userId)) {
            throw ApiException.conflict("FACE_ALREADY_ENROLLED", "A face is already enrolled for this account");
        }
        AnalyzeResult result = analyze(userId, Purpose.ENROLL, challengeId, frames);
        profiles.save(new FaceProfile(userId, result.embedding(), result.model(), Instant.now()));
        logs.save(new FaceVerificationLog(userId, Purpose.ENROLL, "OK", null, result.latencyMs()));
        log.info("face enrolled for user {} ({})", userId, result.model());
        return status(userId);
    }

    @Transactional(noRollbackFor = ApiException.class) // failed attempts must still be logged
    public Verified verify(UUID userId, String challengeId, List<Frame> frames) {
        requireNotLocked(userId);
        FaceProfile profile = profiles.findById(userId)
                .orElseThrow(() -> ApiException.conflict("FACE_NOT_ENROLLED", "Enrol your face before booking"));
        AnalyzeResult result = analyze(userId, Purpose.VERIFY, challengeId, frames);
        double score = cosine(profile.getEmbedding(), result.embedding());
        if (score < props.matchThreshold()) {
            store.recordFailure(userId);
            logs.save(new FaceVerificationLog(userId, Purpose.VERIFY, "FACE_NOT_MATCHED", score, result.latencyMs()));
            log.info("face mismatch for user {}: {} < {}", userId, String.format("%.3f", score),
                    props.matchThreshold());
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FACE_NOT_MATCHED",
                    "The face does not match the enrolled student");
        }
        store.clearFailures(userId);
        logs.save(new FaceVerificationLog(userId, Purpose.VERIFY, "OK", score, result.latencyMs()));
        String token = UUID.randomUUID().toString();
        return new Verified(token, score, store.issueToken(userId, token));
    }

    /** Called right before a booking: spends the one-time token from {@link #verify}. */
    public void requireToken(UUID userId, String faceToken) {
        if (props.enabled() && !store.spendToken(userId, faceToken)) {
            throw ApiException.forbidden("FACE_VERIFICATION_REQUIRED", "Verify your face before booking");
        }
    }

    @Transactional
    public void delete(UUID userId) {
        profiles.deleteById(userId);
        store.clearFailures(userId);
    }

    // ------------------------------------------------------------------ internals

    private AnalyzeResult analyze(UUID userId, Purpose purpose, String challengeId, List<Frame> frames) {
        List<String> steps = store.consume(challengeId, userId);
        AnalyzeResult result = client.analyze(steps, frames);
        if (result.ok()) {
            return result;
        }
        String code = switch (result.status() == null ? "" : result.status()) {
            case "NO_FACE" -> "NO_FACE";
            case "MULTIPLE_FACES" -> "MULTIPLE_FACES";
            default -> "FACE_LIVENESS_FAILED";
        };
        // Bad light or someone walking behind is not an attack; only a failed liveness check counts.
        if (purpose == Purpose.VERIFY && "FACE_LIVENESS_FAILED".equals(code)) {
            store.recordFailure(userId);
        }
        logs.save(new FaceVerificationLog(userId, purpose, code, null, result.latencyMs()));
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code,
                result.reason() == null ? "Face check failed" : result.reason());
    }

    private void requireNotLocked(UUID userId) {
        if (store.failures(userId) >= props.maxFailures()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "FACE_VERIFY_LOCKED",
                    "Too many failed face checks, try again in " + props.lockout().toMinutes() + " minutes");
        }
    }

    /** Both vectors are L2-normalised by face-service, but normalise anyway to be safe. */
    static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return -1;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        return na == 0 || nb == 0 ? -1 : dot / Math.sqrt(na * nb);
    }
}
