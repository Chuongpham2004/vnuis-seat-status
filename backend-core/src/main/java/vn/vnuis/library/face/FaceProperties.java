package vn.vnuis.library.face;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Face verification before booking ({@code app.face.*}).
 *
 * @param enabled            when false, bookings need no face token (tests, fallback demo)
 * @param matchThreshold     minimum cosine similarity between the probe and the enrolled ArcFace template
 * @param maxFailures        failed verifications allowed per {@code lockoutSeconds} window
 */
@ConfigurationProperties("app.face")
public record FaceProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("http://localhost:8001") String baseUrl,
        @DefaultValue("") String internalKey,
        @DefaultValue("20") long readTimeoutSeconds,
        @DefaultValue("0.45") double matchThreshold,
        @DefaultValue("90") long challengeTtlSeconds,
        @DefaultValue("120") long tokenTtlSeconds,
        @DefaultValue("5") int maxFailures,
        @DefaultValue("600") long lockoutSeconds) {

    public Duration challengeTtl() {
        return Duration.ofSeconds(challengeTtlSeconds);
    }

    public Duration tokenTtl() {
        return Duration.ofSeconds(tokenTtlSeconds);
    }

    public Duration lockout() {
        return Duration.ofSeconds(lockoutSeconds);
    }
}
