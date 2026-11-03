package vn.vnuis.library.seat;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import vn.vnuis.library.config.SeatProperties;
import vn.vnuis.library.error.ApiException;

/**
 * Serialises every state change of one seat with the Redisson lock
 * {@code lock:seat:booking:{seat_code}} (5 s lease, so a crashed holder cannot block the seat).
 */
@Component
public class SeatLockService {

    public static final String LOCK_PREFIX = "lock:seat:booking:";

    private final RedissonClient redisson;
    private final SeatProperties props;

    public SeatLockService(RedissonClient redisson, SeatProperties props) {
        this.redisson = redisson;
        this.props = props;
    }

    /**
     * Runs {@code action} while holding the seat lock.
     *
     * @param waitMillis 0 for user bookings (a competing booking means the seat is being taken),
     *                   a few seconds for system events that must not be dropped
     * @throws ApiException 409 SEAT_BUSY if the lock cannot be acquired in time
     */
    public <T> T withLock(String seatCode, long waitMillis, Supplier<T> action) {
        RLock lock = redisson.getLock(LOCK_PREFIX + seatCode);
        boolean acquired;
        try {
            acquired = lock.tryLock(waitMillis, TimeUnit.SECONDS.toMillis(props.lockLeaseSeconds()), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.conflict("SEAT_BUSY", "Seat " + seatCode + " is being updated, try again");
        }
        if (!acquired) {
            throw ApiException.conflict("SEAT_BUSY", "Seat " + seatCode + " is being booked by someone else");
        }
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public void withLock(String seatCode, long waitMillis, Runnable action) {
        withLock(seatCode, waitMillis, () -> {
            action.run();
            return null;
        });
    }
}
