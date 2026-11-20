package vn.vnuis.library.face;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import vn.vnuis.library.error.ApiException;

/**
 * Short-lived, single-use state in Redis:
 * <ul>
 *   <li>{@code face:challenge:{id}} = "userId|CENTER,LEFT,RIGHT" - the random head-turn order</li>
 *   <li>{@code face:token:{token}}  = userId - proof of a successful verification, spent by one booking</li>
 *   <li>{@code face:fail:{userId}}  = failed attempts in the current lockout window</li>
 * </ul>
 */
@Component
public class FaceChallengeStore {

    public static final String CENTER = "CENTER";
    public static final String LEFT = "LEFT";
    public static final String RIGHT = "RIGHT";

    public record Challenge(String challengeId, List<String> steps, Instant expiresAt) {
    }

    private final StringRedisTemplate redis;
    private final FaceProperties props;
    private final SecureRandom random = new SecureRandom();

    public FaceChallengeStore(StringRedisTemplate redis, FaceProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /** Always starts frontal (that frame is what gets matched), then LEFT/RIGHT in random order. */
    public Challenge create(UUID userId) {
        List<String> steps = new ArrayList<>(List.of(CENTER));
        steps.addAll(random.nextBoolean() ? List.of(LEFT, RIGHT) : List.of(RIGHT, LEFT));
        String id = UUID.randomUUID().toString();
        redis.opsForValue().set("face:challenge:" + id, userId + "|" + String.join(",", steps), props.challengeTtl());
        return new Challenge(id, List.copyOf(steps), Instant.now().plus(props.challengeTtl()));
    }

    /** Spends the challenge; it can be answered once, by the student it was issued to. */
    public List<String> consume(String challengeId, UUID userId) {
        String value = challengeId == null ? null : redis.opsForValue().getAndDelete("face:challenge:" + challengeId);
        if (value == null || !value.startsWith(userId + "|")) {
            throw ApiException.gone("FACE_CHALLENGE_EXPIRED", "The face challenge expired, start again");
        }
        return Arrays.asList(value.substring(value.indexOf('|') + 1).split(","));
    }

    public Instant issueToken(UUID userId, String token) {
        redis.opsForValue().set("face:token:" + token, userId.toString(), props.tokenTtl());
        return Instant.now().plus(props.tokenTtl());
    }

    /** True once per token, and only for the student who earned it. */
    public boolean spendToken(UUID userId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String owner = redis.opsForValue().getAndDelete("face:token:" + token);
        return userId.toString().equals(owner);
    }

    public long failures(UUID userId) {
        String v = redis.opsForValue().get("face:fail:" + userId);
        return v == null ? 0 : Long.parseLong(v);
    }

    public void recordFailure(UUID userId) {
        String key = "face:fail:" + userId;
        Long n = redis.opsForValue().increment(key);
        if (n != null && n == 1) {
            redis.expire(key, props.lockout());
        }
    }

    public void clearFailures(UUID userId) {
        redis.delete("face:fail:" + userId);
    }
}
