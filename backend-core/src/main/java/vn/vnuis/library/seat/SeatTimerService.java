package vn.vnuis.library.seat;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis TTL keys that drive time-based transitions. When one expires, Redis
 * publishes {@code __keyevent@0__:expired} and {@code SeatTimerExpirationListener} reacts.
 */
@Component
public class SeatTimerService {

    public static final String RESERVATION_EXPIRE = "timer:reservation:expire:";
    public static final String SEAT_AWAY = "timer:seat:away:";
    public static final String SEAT_WALKIN = "timer:seat:walkin:";
    public static final String SEAT_AWAY_PENDING = "timer:seat:awaypending:";

    private final StringRedisTemplate redis;

    public SeatTimerService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // ---- 15-min no-show ----
    public void startReservationExpiry(UUID reservationId, String seatCode, Duration ttl) {
        redis.opsForValue().set(RESERVATION_EXPIRE + reservationId, seatCode, ttl);
    }

    public void cancelReservationExpiry(UUID reservationId) {
        redis.delete(RESERVATION_EXPIRE + reservationId);
    }

    public boolean reservationExpiryExists(UUID reservationId) {
        return Boolean.TRUE.equals(redis.hasKey(RESERVATION_EXPIRE + reservationId));
    }

    // ---- 30-min away ----
    public void startAway(String seatCode, Duration ttl) {
        redis.opsForValue().set(SEAT_AWAY + seatCode, "1", ttl);
    }

    public void cancelAway(String seatCode) {
        redis.delete(SEAT_AWAY + seatCode);
    }

    public boolean awayExists(String seatCode) {
        return Boolean.TRUE.equals(redis.hasKey(SEAT_AWAY + seatCode));
    }

    // ---- presence must hold for walkin-grace before a walk-in is recorded ----
    /** SET NX: heartbeats do not restart a running grace period. */
    public void startWalkinIfAbsent(String seatCode, Duration grace) {
        redis.opsForValue().setIfAbsent(SEAT_WALKIN + seatCode, "1", grace);
    }

    public void cancelWalkin(String seatCode) {
        redis.delete(SEAT_WALKIN + seatCode);
    }

    // ---- absence must hold for away-grace before OCCUPIED -> AWAY ----
    public void startAwayPendingIfAbsent(String seatCode, Duration grace) {
        redis.opsForValue().setIfAbsent(SEAT_AWAY_PENDING + seatCode, "1", grace);
    }

    public void cancelAwayPending(String seatCode) {
        redis.delete(SEAT_AWAY_PENDING + seatCode);
    }

    /** Clear every seat-level timer (used when a seat is released). */
    public void cancelSeatTimers(String seatCode) {
        redis.delete(java.util.List.of(SEAT_AWAY + seatCode, SEAT_WALKIN + seatCode, SEAT_AWAY_PENDING + seatCode));
    }
}
