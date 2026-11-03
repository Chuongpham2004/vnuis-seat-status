package vn.vnuis.library.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Seat timers and locking ({@code app.seat.*}). All durations are configurable for demos/tests. */
@ConfigurationProperties("app.seat")
public record SeatProperties(
        @DefaultValue("900") long reservationTtlSeconds,
        @DefaultValue("1800") long awayTtlSeconds,
        @DefaultValue("120") long walkinGraceSeconds,
        @DefaultValue("60") long awayGraceSeconds,
        @DefaultValue("5") long lockLeaseSeconds,
        @DefaultValue("3000") long lockWaitMillis,
        @DefaultValue("30000") long reconcileIntervalMs,
        @DefaultValue("2") long reconcileGraceSeconds) {

    public Duration reservationTtl() {
        return Duration.ofSeconds(reservationTtlSeconds);
    }

    public Duration awayTtl() {
        return Duration.ofSeconds(awayTtlSeconds);
    }

    public Duration walkinGrace() {
        return Duration.ofSeconds(walkinGraceSeconds);
    }

    public Duration awayGrace() {
        return Duration.ofSeconds(awayGraceSeconds);
    }

    public Duration reconcileGrace() {
        return Duration.ofSeconds(reconcileGraceSeconds);
    }
}
