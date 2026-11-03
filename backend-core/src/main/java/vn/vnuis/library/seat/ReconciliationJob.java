package vn.vnuis.library.seat;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.vnuis.library.config.SeatProperties;
import vn.vnuis.library.domain.Reservation;
import vn.vnuis.library.domain.ReservationStatus;
import vn.vnuis.library.domain.Seat;
import vn.vnuis.library.domain.SeatStatus;
import vn.vnuis.library.repository.ReservationRepository;
import vn.vnuis.library.repository.SeatRepository;

/**
 * Safety net for Redis key-expiry events, which are best-effort: they may fire late
 * and are lost while the backend is down. Re-checks the deadlines stored in PostgreSQL.
 * All handlers are idempotent, so racing the expiry listener is harmless.
 */
@Component
public class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);

    private final ReservationRepository reservations;
    private final SeatRepository seats;
    private final SeatStateMachine stateMachine;
    private final SeatProperties props;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    public ReconciliationJob(ReservationRepository reservations, SeatRepository seats, SeatStateMachine stateMachine,
                             SeatProperties props) {
        this.reservations = reservations;
        this.seats = seats;
        this.stateMachine = stateMachine;
        this.props = props;
    }

    void markReady() {
        ready.set(true);
    }

    @Scheduled(fixedDelayString = "${app.seat.reconcile-interval-ms:30000}",
               initialDelayString = "${app.seat.reconcile-interval-ms:30000}")
    public void scheduled() {
        if (ready.get()) {
            runOnce(props.reconcileGrace());
        }
    }

    /** @param grace how far past a deadline before we step in (gives the Redis event a head start) */
    public void runOnce(Duration grace) {
        Instant now = Instant.now();
        int handled = 0;
        for (Reservation r : reservations.findByStatusAndExpireAtBefore(ReservationStatus.PENDING, now.minus(grace))) {
            try {
                stateMachine.onReservationExpired(r.getId());
                handled++;
            } catch (RuntimeException e) {
                log.warn("Reconcile no-show {} failed: {}", r.getId(), e.toString());
            }
        }
        Instant awayCutoff = now.minus(props.awayTtl()).minus(grace);
        for (Seat s : seats.findByCurrentStatusAndAwaySinceBefore(SeatStatus.AWAY, awayCutoff)) {
            try {
                stateMachine.onAwayTimer(s.getSeatCode());
                handled++;
            } catch (RuntimeException e) {
                log.warn("Reconcile away {} failed: {}", s.getSeatCode(), e.toString());
            }
        }
        if (handled > 0) {
            log.info("Reconciliation processed {} overdue deadline(s)", handled);
        }
    }
}
