package vn.vnuis.library.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import vn.vnuis.library.domain.Enums.BookingSource;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Null for anonymous walk-ins. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "seat_id", nullable = false)
    private UUID seatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_source", nullable = false)
    private BookingSource bookingSource;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    /** Null for walk-ins. */
    @Column(name = "expire_at")
    private Instant expireAt;

    @Column(name = "check_in_at")
    private Instant checkInAt;

    @Column(name = "check_out_at")
    private Instant checkOutAt;

    protected Reservation() {
    }

    public static Reservation pending(UUID userId, UUID seatId, BookingSource source, Instant now, Instant expireAt) {
        Reservation r = new Reservation();
        r.userId = userId;
        r.seatId = seatId;
        r.status = ReservationStatus.PENDING;
        r.bookingSource = source;
        r.reservedAt = now;
        r.expireAt = expireAt;
        return r;
    }

    public static Reservation walkIn(UUID seatId, Instant now) {
        Reservation r = new Reservation();
        r.seatId = seatId;
        r.status = ReservationStatus.ACTIVE;
        r.bookingSource = BookingSource.WALK_IN;
        r.reservedAt = now;
        r.checkInAt = now;
        return r;
    }

    /** "On time" = a booked reservation that was checked in before its no-show deadline. */
    public boolean wasCheckedInOnTime() {
        return userId != null
                && bookingSource != BookingSource.WALK_IN
                && checkInAt != null
                && expireAt != null
                && !checkInAt.isAfter(expireAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public BookingSource getBookingSource() {
        return bookingSource;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public Instant getExpireAt() {
        return expireAt;
    }

    public Instant getCheckInAt() {
        return checkInAt;
    }

    public void setCheckInAt(Instant checkInAt) {
        this.checkInAt = checkInAt;
    }

    public Instant getCheckOutAt() {
        return checkOutAt;
    }

    public void setCheckOutAt(Instant checkOutAt) {
        this.checkOutAt = checkOutAt;
    }
}
