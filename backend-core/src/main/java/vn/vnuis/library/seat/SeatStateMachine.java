package vn.vnuis.library.seat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.vnuis.library.config.CreditProperties;
import vn.vnuis.library.config.SeatProperties;
import vn.vnuis.library.credit.CreditService;
import vn.vnuis.library.credit.CreditService.CreditChange;
import vn.vnuis.library.domain.Enums.BookingSource;
import vn.vnuis.library.domain.Reservation;
import vn.vnuis.library.domain.ReservationStatus;
import vn.vnuis.library.domain.SeatStatus;
import vn.vnuis.library.domain.User;
import vn.vnuis.library.error.ApiException;
import vn.vnuis.library.realtime.SeatEventBroadcaster;
import vn.vnuis.library.repository.ReservationRepository;
import vn.vnuis.library.repository.SeatRepository;
import vn.vnuis.library.repository.UserRepository;

/**
 * The 4-state seat machine (AVAILABLE / RESERVED / OCCUPIED / AWAY).
 *
 * <p>Every transition follows the same recipe:
 * <ol>
 *   <li>take the Redisson lock {@code lock:seat:booking:{seat}};</li>
 *   <li>re-read and validate the current state (handlers are idempotent - a late or
 *       duplicate timer event is simply ignored);</li>
 *   <li>commit the PostgreSQL change;</li>
 *   <li>update the Redis hash and timers, then broadcast over STOMP.</li>
 * </ol>
 * If the process dies between 3 and 4, {@link SeatStateBootstrapper} rebuilds Redis from PostgreSQL.
 */
@Service
public class SeatStateMachine {

    private static final Logger log = LoggerFactory.getLogger(SeatStateMachine.class);

    /** What a processed no-show did (null outcome = nothing to do). */
    private record NoShowOutcome(UUID userId, CreditChange change) {
    }

    private final SeatCatalog catalog;
    private final SeatStateStore store;
    private final SeatTimerService timers;
    private final SeatLockService locks;
    private final TransactionTemplate tx;
    private final SeatRepository seats;
    private final ReservationRepository reservations;
    private final UserRepository users;
    private final CreditService credit;
    private final SeatEventBroadcaster broadcaster;
    private final SeatProperties props;
    private final CreditProperties creditProps;

    public SeatStateMachine(SeatCatalog catalog, SeatStateStore store, SeatTimerService timers, SeatLockService locks,
                            TransactionTemplate tx, SeatRepository seats, ReservationRepository reservations,
                            UserRepository users, CreditService credit, SeatEventBroadcaster broadcaster,
                            SeatProperties props, CreditProperties creditProps) {
        this.catalog = catalog;
        this.store = store;
        this.timers = timers;
        this.locks = locks;
        this.tx = tx;
        this.seats = seats;
        this.reservations = reservations;
        this.users = users;
        this.credit = credit;
        this.broadcaster = broadcaster;
        this.props = props;
        this.creditProps = creditProps;
    }

    // =====================================================================
    // User commands
    // =====================================================================

    /** AVAILABLE -> RESERVED. Losing a lock race means someone else is taking the seat: 409 immediately. */
    public Reservation reserve(UUID userId, String seatCode, BookingSource source) {
        if (source == BookingSource.WALK_IN) {
            throw ApiException.badRequest("INVALID_SOURCE", "Walk-ins are detected by the seat sensor");
        }
        SeatInfo seat = catalog.require(seatCode);
        return locks.withLock(seat.seatCode(), 0, () -> {
            SeatState state = store.require(seat.seatCode());
            if (state.status() != SeatStatus.AVAILABLE) {
                throw ApiException.conflict("SEAT_NOT_AVAILABLE",
                        "Seat " + seat.seatCode() + " is " + state.status());
            }
            Instant now = Instant.now();
            Instant expireAt = now.plus(props.reservationTtl());
            CreditChange[] probation = new CreditChange[1];
            Reservation saved = inTx(() -> {
                // An expired ban below 60 is reset to 60 first, so the student can book again.
                probation[0] = credit.resetProbationIfDue(userId).orElse(null);
                User user = users.findById(userId)
                        .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
                if (user.isBannedAt(now)) {
                    throw ApiException.forbidden("USER_BANNED",
                            "Advance booking is suspended until " + user.getBannedUntil());
                }
                if (user.getCreditScore() < creditProps.minToBook()) {
                    throw ApiException.forbidden("INSUFFICIENT_CREDIT", "Credit score " + user.getCreditScore()
                            + " is below the minimum of " + creditProps.minToBook());
                }
                if (reservations.existsByUserIdAndStatusIn(userId, ReservationStatus.OPEN)) {
                    throw ApiException.conflict("USER_HAS_ACTIVE_RESERVATION",
                            "You already have an open reservation");
                }
                setSeatRow(seat, state.status(), SeatStatus.RESERVED, null, now);
                return reservations.saveAndFlush(Reservation.pending(userId, seat.seatId(), source, now, expireAt));
            });
            timers.startReservationExpiry(saved.getId(), seat.seatCode(), props.reservationTtl());
            publish(state.withStatus(SeatStatus.RESERVED, userId, saved.getId()));
            notifyCredit(probation[0]);
            broadcaster.notifyUser(userId, "RESERVATION_CREATED",
                    "Seat " + seat.seatCode() + " is held for you until " + expireAt,
                    data("reservationId", saved.getId(), "seatCode", seat.seatCode(), "expireAt", expireAt));
            log.info("RESERVED {} by {} via {} (reservation {})", seat.seatCode(), userId, source, saved.getId());
            return saved;
        });
    }

    /** Dual check-in: RESERVED -> OCCUPIED only if the QR token matches AND the sensor sees someone. */
    public Reservation checkIn(UUID userId, UUID reservationId, String qrCodeToken) {
        SeatInfo seat = catalog.requireById(ownedReservation(userId, reservationId).getSeatId());
        return locks.withLock(seat.seatCode(), props.lockWaitMillis(), () -> {
            Instant now = Instant.now();
            Reservation current = reservations.findById(reservationId).orElseThrow();
            if (current.getStatus() == ReservationStatus.EXPIRED_NO_SHOW
                    || (current.getStatus() == ReservationStatus.PENDING && current.getExpireAt().isBefore(now))) {
                throw ApiException.gone("RESERVATION_EXPIRED", "The 15-minute check-in window has passed");
            }
            if (current.getStatus() != ReservationStatus.PENDING) {
                throw ApiException.conflict("INVALID_RESERVATION_STATE", "Reservation is " + current.getStatus());
            }
            if (!constantTimeEquals(seat.qrCodeToken(), qrCodeToken)) {
                throw ApiException.badRequest("INVALID_QR_CODE", "This QR code does not belong to seat " + seat.seatCode());
            }
            SeatState state = store.require(seat.seatCode());
            if (!state.physicalOccupied()) {
                throw ApiException.conflict("NO_PHYSICAL_PRESENCE",
                        "The seat sensor does not detect anyone yet. Sit down and scan again.");
            }
            Reservation saved = inTx(() -> {
                Reservation r = reservations.findById(reservationId).orElseThrow();
                r.setStatus(ReservationStatus.ACTIVE);
                r.setCheckInAt(now);
                setSeatRow(seat, state.status(), SeatStatus.OCCUPIED, null, now);
                return r;
            });
            timers.cancelReservationExpiry(reservationId);
            timers.cancelAwayPending(seat.seatCode());
            publish(state.withStatus(SeatStatus.OCCUPIED, userId, reservationId));
            broadcaster.notifyUser(userId, "CHECKED_IN", "Checked in at seat " + seat.seatCode(),
                    data("reservationId", reservationId, "seatCode", seat.seatCode()));
            log.info("OCCUPIED {} - dual check-in by {}", seat.seatCode(), userId);
            return saved;
        });
    }

    /** OCCUPIED/AWAY -> AVAILABLE, +2 credit when the booking was checked in on time. */
    public Reservation checkOut(UUID userId, UUID reservationId) {
        SeatInfo seat = catalog.requireById(ownedReservation(userId, reservationId).getSeatId());
        return locks.withLock(seat.seatCode(), props.lockWaitMillis(), () -> {
            Reservation current = reservations.findById(reservationId).orElseThrow();
            if (current.getStatus() != ReservationStatus.ACTIVE) {
                throw ApiException.conflict("INVALID_RESERVATION_STATE",
                        "Only an active (checked-in) reservation can be checked out; this one is " + current.getStatus());
            }
            SeatState state = store.require(seat.seatCode());
            Instant now = Instant.now();
            CreditChange[] change = new CreditChange[1];
            Reservation saved = inTx(() -> {
                Reservation r = reservations.findById(reservationId).orElseThrow();
                r.setStatus(ReservationStatus.COMPLETED);
                r.setCheckOutAt(now);
                setSeatRow(seat, state.status(), SeatStatus.AVAILABLE, null, now);
                if (r.wasCheckedInOnTime()) {
                    change[0] = credit.apply(userId, creditProps.onTimeReward(), CreditService.REASON_ON_TIME, r.getId());
                }
                return r;
            });
            timers.cancelSeatTimers(seat.seatCode());
            SeatState released = state.withStatus(SeatStatus.AVAILABLE, null, null);
            publish(released);
            startIdleTimers(released);
            broadcaster.notifyUser(userId, "CHECKED_OUT", "Checked out of seat " + seat.seatCode(),
                    data("reservationId", reservationId, "seatCode", seat.seatCode()));
            notifyCredit(change[0]);
            log.info("AVAILABLE {} - checked out by {}", seat.seatCode(), userId);
            return saved;
        });
    }

    /** RESERVED -> AVAILABLE before check-in, no penalty. */
    public Reservation cancel(UUID userId, UUID reservationId) {
        SeatInfo seat = catalog.requireById(ownedReservation(userId, reservationId).getSeatId());
        return locks.withLock(seat.seatCode(), props.lockWaitMillis(), () -> {
            Reservation current = reservations.findById(reservationId).orElseThrow();
            if (current.getStatus() != ReservationStatus.PENDING) {
                throw ApiException.conflict("INVALID_RESERVATION_STATE",
                        "Only a pending reservation can be cancelled; this one is " + current.getStatus());
            }
            SeatState state = store.require(seat.seatCode());
            Instant now = Instant.now();
            Reservation saved = inTx(() -> {
                Reservation r = reservations.findById(reservationId).orElseThrow();
                r.setStatus(ReservationStatus.CANCELLED);
                r.setCheckOutAt(now);
                setSeatRow(seat, state.status(), SeatStatus.AVAILABLE, null, now);
                return r;
            });
            timers.cancelReservationExpiry(reservationId);
            SeatState released = state.withStatus(SeatStatus.AVAILABLE, null, null);
            publish(released);
            startIdleTimers(released);
            log.info("AVAILABLE {} - reservation {} cancelled", seat.seatCode(), reservationId);
            return saved;
        });
    }

    /** Admin/ops: put any seat back to AVAILABLE and close its open reservation. */
    public void forceRelease(String seatCode) {
        SeatInfo seat = catalog.require(seatCode);
        locks.withLock(seat.seatCode(), props.lockWaitMillis(), () -> {
            SeatState state = store.require(seat.seatCode());
            Instant now = Instant.now();
            Optional<UUID> closed = inTx(() -> {
                Optional<Reservation> open = reservations.findFirstBySeatIdAndStatusIn(seat.seatId(), ReservationStatus.OPEN);
                open.ifPresent(r -> {
                    r.setStatus(ReservationStatus.CANCELLED);
                    r.setCheckOutAt(now);
                });
                seats.updateStatus(seat.seatId(), SeatStatus.AVAILABLE, null, now);
                return open.map(Reservation::getId);
            });
            closed.ifPresent(timers::cancelReservationExpiry);
            timers.cancelSeatTimers(seat.seatCode());
            SeatState released = state.withStatus(SeatStatus.AVAILABLE, null, null);
            publish(released);
            startIdleTimers(released);
            broadcaster.notifyUser(state.currentUserUuid(), "SEAT_RELEASED_BY_ADMIN",
                    "Seat " + seat.seatCode() + " was released by the library staff", data("seatCode", seat.seatCode()));
            log.info("AVAILABLE {} - force released by admin", seat.seatCode());
        });
    }

    // =====================================================================
    // Sensor events (MQTT)
    // =====================================================================

    /**
     * New debounced sensor reading. Sensor facts are written straight to Redis; the
     * matching grace timer is started/cancelled, and AWAY -> OCCUPIED happens immediately.
     */
    public void onPresence(String seatCode, boolean present, Double distanceCm) {
        Optional<SeatState> current = store.find(seatCode);
        if (current.isEmpty()) {
            log.warn("Telemetry for unknown/uninitialised seat {}", seatCode);
            return;
        }
        boolean changed = current.get().physicalOccupied() != present;
        store.updatePhysical(seatCode, present, distanceCm);
        SeatState state = current.get().withPhysical(present, distanceCm);

        switch (state.status()) {
            case AVAILABLE -> {
                if (present) {
                    timers.startWalkinIfAbsent(seatCode, props.walkinGrace());
                } else {
                    timers.cancelWalkin(seatCode);
                }
            }
            case OCCUPIED -> {
                if (present) {
                    timers.cancelAwayPending(seatCode);
                } else {
                    timers.startAwayPendingIfAbsent(seatCode, props.awayGrace());
                }
            }
            case AWAY -> {
                if (present) {
                    returnFromAway(seatCode);
                    return; // returnFromAway broadcasts
                }
            }
            case RESERVED -> {
                // Waiting for the QR scan; presence is only recorded.
            }
        }
        if (changed) {
            broadcaster.seatChanged(state);
        }
    }

    /** AWAY -> OCCUPIED when the student comes back within the away window. */
    void returnFromAway(String seatCode) {
        SeatInfo seat = catalog.require(seatCode);
        locks.withLock(seatCode, props.lockWaitMillis(), () -> {
            SeatState state = store.require(seatCode);
            if (state.status() != SeatStatus.AWAY || !state.physicalOccupied()) {
                return;
            }
            Instant now = Instant.now();
            inTx(() -> setSeatRow(seat, SeatStatus.AWAY, SeatStatus.OCCUPIED, null, now));
            timers.cancelAway(seatCode);
            publish(state.withStatus(SeatStatus.OCCUPIED));
            broadcaster.notifyUser(state.currentUserUuid(), "RETURNED", "Welcome back to seat " + seatCode,
                    data("seatCode", seatCode));
            log.info("OCCUPIED {} - returned from AWAY", seatCode);
        });
    }

    // =====================================================================
    // Timer events (Redis key expiry / reconciliation)
    // =====================================================================

    /** Walk-in grace elapsed: AVAILABLE + presence still detected -> OCCUPIED (anonymous WALK_IN). */
    public void onWalkinTimer(String seatCode) {
        SeatInfo seat = catalog.require(seatCode);
        locks.withLock(seatCode, props.lockWaitMillis(), () -> {
            SeatState state = store.require(seatCode);
            if (state.status() != SeatStatus.AVAILABLE || !state.physicalOccupied()) {
                return;
            }
            Instant now = Instant.now();
            Reservation walkIn;
            try {
                walkIn = inTx(() -> {
                    setSeatRow(seat, SeatStatus.AVAILABLE, SeatStatus.OCCUPIED, null, now);
                    return reservations.saveAndFlush(Reservation.walkIn(seat.seatId(), now));
                });
            } catch (ApiException e) {
                log.warn("Walk-in on {} skipped: {}", seatCode, e.getMessage());
                return;
            }
            publish(state.withStatus(SeatStatus.OCCUPIED, null, walkIn.getId()));
            log.info("OCCUPIED {} - walk-in detected (reservation {})", seatCode, walkIn.getId());
        });
    }

    /** Absence grace elapsed: OCCUPIED + still nobody -> AWAY and start the 30-min away timer. */
    public void onAwayPendingTimer(String seatCode) {
        SeatInfo seat = catalog.require(seatCode);
        locks.withLock(seatCode, props.lockWaitMillis(), () -> {
            SeatState state = store.require(seatCode);
            if (state.status() != SeatStatus.OCCUPIED || state.physicalOccupied()) {
                return;
            }
            Instant now = Instant.now();
            inTx(() -> setSeatRow(seat, SeatStatus.OCCUPIED, SeatStatus.AWAY, now, now));
            timers.startAway(seatCode, props.awayTtl());
            publish(state.withStatus(SeatStatus.AWAY));
            Instant deadline = now.plus(props.awayTtl());
            broadcaster.notifyUser(state.currentUserUuid(), "AWAY_STARTED",
                    "You left seat " + seatCode + ". It will be released at " + deadline + " unless you return.",
                    data("seatCode", seatCode, "releaseAt", deadline));
            log.info("AWAY {} - release at {}", seatCode, deadline);
        });
    }

    /** Away window elapsed: AWAY -> AVAILABLE, reservation AUTO_RELEASED_AWAY. */
    public void onAwayTimer(String seatCode) {
        SeatInfo seat = catalog.require(seatCode);
        locks.withLock(seatCode, props.lockWaitMillis(), () -> {
            SeatState state = store.require(seatCode);
            if (state.status() != SeatStatus.AWAY) {
                return;
            }
            if (timers.awayExists(seatCode)) {
                return; // reconciliation ran early; the TTL key will fire on its own
            }
            Instant now = Instant.now();
            Optional<Reservation> released = inTx(() -> {
                Optional<Reservation> open = reservations.findFirstBySeatIdAndStatusIn(seat.seatId(), ReservationStatus.OPEN);
                open.ifPresent(r -> {
                    r.setStatus(ReservationStatus.AUTO_RELEASED_AWAY);
                    r.setCheckOutAt(now);
                });
                setSeatRow(seat, SeatStatus.AWAY, SeatStatus.AVAILABLE, null, now);
                return open;
            });
            timers.cancelSeatTimers(seatCode);
            SeatState next = state.withStatus(SeatStatus.AVAILABLE, null, null);
            publish(next);
            startIdleTimers(next);
            broadcaster.notifyUser(state.currentUserUuid(), "AUTO_RELEASED",
                    "Seat " + seatCode + " was released because you were away too long",
                    data("seatCode", seatCode, "reservationId", released.map(Reservation::getId).orElse(null)));
            log.info("AVAILABLE {} - auto-released after AWAY timeout", seatCode);
        });
    }

    /** 15-min no-show: RESERVED -> AVAILABLE, reservation EXPIRED_NO_SHOW, -10 credit (+3-day ban below 60). */
    public void onReservationExpired(UUID reservationId) {
        Optional<Reservation> found = reservations.findById(reservationId);
        if (found.isEmpty() || found.get().getStatus() != ReservationStatus.PENDING) {
            return;
        }
        SeatInfo seat = catalog.requireById(found.get().getSeatId());
        locks.withLock(seat.seatCode(), props.lockWaitMillis(), () -> {
            Instant now = Instant.now();
            NoShowOutcome outcome = inTx(() -> {
                Reservation r = reservations.findById(reservationId).orElseThrow();
                if (r.getStatus() != ReservationStatus.PENDING || r.getExpireAt().isAfter(now.plusSeconds(1))) {
                    return null; // already handled by a concurrent event, or a stale timer
                }
                r.setStatus(ReservationStatus.EXPIRED_NO_SHOW);
                setSeatRow(seat, SeatStatus.RESERVED, SeatStatus.AVAILABLE, null, now);
                CreditChange c = r.getUserId() == null ? null
                        : credit.apply(r.getUserId(), -creditProps.noShowPenalty(), CreditService.REASON_NO_SHOW, r.getId());
                return new NoShowOutcome(r.getUserId(), c);
            });
            if (outcome == null) {
                return;
            }
            CreditChange change = outcome.change();
            timers.cancelReservationExpiry(reservationId);
            SeatState state = store.require(seat.seatCode());
            SeatState next = state.withStatus(SeatStatus.AVAILABLE, null, null);
            publish(next);
            startIdleTimers(next);
            broadcaster.notifyUser(outcome.userId(), "RESERVATION_EXPIRED",
                    "You did not check in at seat " + seat.seatCode() + " in time",
                    data("reservationId", reservationId, "seatCode", seat.seatCode()));
            notifyCredit(change);
            log.info("AVAILABLE {} - reservation {} EXPIRED_NO_SHOW{}", seat.seatCode(), reservationId,
                    change == null ? "" : " (credit " + change.appliedDelta() + " -> " + change.balance()
                            + (change.banStarted() ? ", banned until " + change.bannedUntil() : "") + ")");
        });
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private Reservation ownedReservation(UUID userId, UUID reservationId) {
        return reservations.findById(reservationId)
                .filter(r -> userId.equals(r.getUserId()))
                .orElseThrow(() -> ApiException.notFound("RESERVATION_NOT_FOUND", "Reservation not found"));
    }

    private void setSeatRow(SeatInfo seat, SeatStatus from, SeatStatus to, Instant awaySince, Instant now) {
        if (!from.canTransitionTo(to)) {
            throw new IllegalStateException("Illegal seat transition " + from + " -> " + to + " on " + seat.seatCode());
        }
        seats.updateStatus(seat.seatId(), to, awaySince, now);
    }

    private void publish(SeatState next) {
        store.save(next);
        broadcaster.seatChanged(next);
    }

    /** A freshly released seat that still detects someone starts the walk-in grace period. */
    private void startIdleTimers(SeatState state) {
        if (state.status() == SeatStatus.AVAILABLE && state.physicalOccupied()) {
            timers.startWalkinIfAbsent(state.seatCode(), props.walkinGrace());
        }
    }

    private void notifyCredit(CreditChange change) {
        if (change == null) {
            return;
        }
        String message = "Credit " + (change.appliedDelta() >= 0 ? "+" : "") + change.appliedDelta()
                + " (" + change.reason() + "), balance " + change.balance()
                + (change.banStarted() ? ". Advance booking suspended until " + change.bannedUntil() : "");
        broadcaster.notifyUser(change.userId(), "CREDIT_CHANGED", message, change);
    }

    /** Runs {@code work} in a transaction and maps the partial-unique-index violations to 409s. */
    private <T> T inTx(Supplier<T> work) {
        try {
            return tx.execute(status -> work.get());
        } catch (DataIntegrityViolationException e) {
            String msg = String.valueOf(e.getMostSpecificCause().getMessage());
            if (msg.contains("ux_reservations_user_open")) {
                throw ApiException.conflict("USER_HAS_ACTIVE_RESERVATION", "You already have an open reservation");
            }
            if (msg.contains("ux_reservations_seat_open")) {
                throw ApiException.conflict("SEAT_NOT_AVAILABLE", "Seat already has an open reservation");
            }
            throw e;
        }
    }

    private void inTx(Runnable work) {
        inTx(() -> {
            work.run();
            return null;
        });
    }

    private static boolean constantTimeEquals(String expected, String given) {
        if (given == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.trim().getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> data(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
