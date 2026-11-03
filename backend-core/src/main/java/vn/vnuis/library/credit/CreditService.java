package vn.vnuis.library.credit;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vnuis.library.config.CreditProperties;
import vn.vnuis.library.domain.CreditLog;
import vn.vnuis.library.domain.User;
import vn.vnuis.library.error.ApiException;
import vn.vnuis.library.repository.CreditLogRepository;
import vn.vnuis.library.repository.UserRepository;

@Service
public class CreditService {

    public static final String REASON_NO_SHOW = "NO_SHOW";
    public static final String REASON_ON_TIME = "ON_TIME_CHECKOUT";
    public static final String REASON_PROBATION = "BAN_EXPIRED_PROBATION_RESET";

    private final UserRepository users;
    private final CreditLogRepository logs;
    private final CreditProperties props;

    public CreditService(UserRepository users, CreditLogRepository logs, CreditProperties props) {
        this.users = users;
        this.logs = logs;
        this.props = props;
    }

    /** Result of a credit change, used for notifications. */
    public record CreditChange(UUID userId, int requestedDelta, int appliedDelta, int balance,
                               Instant bannedUntil, boolean banStarted, String reason) {
    }

    /**
     * Applies {@code delta} (clamped to 0..max), writes a credit_logs row, and starts a
     * {@code ban-days} booking ban when a penalty drops the balance below {@code min-to-book}.
     * Must run inside the caller's transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CreditChange apply(UUID userId, int delta, String reason, UUID reservationId) {
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User " + userId + " not found"));
        Instant now = Instant.now();
        int before = user.getCreditScore();
        int after = CreditPolicy.apply(before, delta, props.maxScore());
        user.setCreditScore(after);

        boolean banStarted = false;
        if (CreditPolicy.triggersBan(after, delta, props.minToBook())) {
            user.setBannedUntil(now.plus(Duration.ofDays(props.banDays())));
            banStarted = true;
        }
        logs.save(new CreditLog(userId, reservationId, after - before, after, reason, now));
        return new CreditChange(userId, delta, after - before, after, user.getBannedUntil(), banStarted, reason);
    }

    /**
     * If the user's ban has expired while their score is still below {@code min-to-book},
     * resets the score to exactly {@code min-to-book}, clears {@code banned_until} and logs
     * {@value #REASON_PROBATION}. Called before booking and when the profile is read.
     * Joins the caller's transaction, or opens its own.
     */
    @Transactional
    public Optional<CreditChange> resetProbationIfDue(UUID userId) {
        Instant now = Instant.now();
        User user = users.findByIdForUpdate(userId).orElse(null);
        if (user == null
                || !CreditPolicy.probationResetDue(user.getCreditScore(), user.getBannedUntil(), now, props.minToBook())) {
            return Optional.empty();
        }
        int before = user.getCreditScore();
        int after = props.minToBook();
        user.setCreditScore(after);
        user.setBannedUntil(null);
        logs.save(new CreditLog(userId, null, after - before, after, REASON_PROBATION, now));
        return Optional.of(new CreditChange(userId, after - before, after - before, after, null, false, REASON_PROBATION));
    }

    /** Admin helper: lift an active ban. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void clearBan(UUID userId) {
        users.findByIdForUpdate(userId).ifPresent(u -> u.setBannedUntil(null));
    }
}
