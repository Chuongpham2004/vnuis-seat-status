package vn.vnuis.library.expiry;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import vn.vnuis.library.config.AppProperties;
import vn.vnuis.library.seat.SeatStateMachine;
import vn.vnuis.library.seat.SeatTimerService;

/**
 * Listens on {@code __keyevent@*__:expired} and routes our timer keys to the state machine:
 * <ul>
 *   <li>{@code timer:reservation:expire:{id}} - 15-min no-show</li>
 *   <li>{@code timer:seat:away:{seat}} - 30-min away release</li>
 *   <li>{@code timer:seat:walkin:{seat}} - walk-in confirmation</li>
 *   <li>{@code timer:seat:awaypending:{seat}} - OCCUPIED -> AWAY</li>
 * </ul>
 */
@Component
public class SeatTimerExpirationListener extends KeyExpirationEventMessageListener {

    private static final Logger log = LoggerFactory.getLogger(SeatTimerExpirationListener.class);

    private final SeatStateMachine stateMachine;

    public SeatTimerExpirationListener(RedisMessageListenerContainer container, SeatStateMachine stateMachine,
                                       AppProperties.Redis redisProps) {
        super(container);
        this.stateMachine = stateMachine;
        // Only applied (CONFIG SET) if the server currently has notifications disabled.
        setKeyspaceNotificationsConfigParameter(redisProps.keyspaceNotifications());
    }

    @Override
    protected void doHandleMessage(@NonNull Message message) {
        String key = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            if (key.startsWith(SeatTimerService.RESERVATION_EXPIRE)) {
                stateMachine.onReservationExpired(UUID.fromString(key.substring(SeatTimerService.RESERVATION_EXPIRE.length())));
            } else if (key.startsWith(SeatTimerService.SEAT_AWAY_PENDING)) {
                stateMachine.onAwayPendingTimer(key.substring(SeatTimerService.SEAT_AWAY_PENDING.length()));
            } else if (key.startsWith(SeatTimerService.SEAT_AWAY)) {
                stateMachine.onAwayTimer(key.substring(SeatTimerService.SEAT_AWAY.length()));
            } else if (key.startsWith(SeatTimerService.SEAT_WALKIN)) {
                stateMachine.onWalkinTimer(key.substring(SeatTimerService.SEAT_WALKIN.length()));
            }
        } catch (RuntimeException e) {
            // Reconciliation (no-show/away) or the next sensor heartbeat (walk-in/away-pending) retries.
            log.warn("Timer {} could not be processed: {}", key, e.toString());
        }
    }
}
