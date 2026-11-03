package vn.vnuis.library.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_logs")
public class CreditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "point_delta", nullable = false)
    private int pointDelta;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(nullable = false)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CreditLog() {
    }

    public CreditLog(UUID userId, UUID reservationId, int pointDelta, int balanceAfter, String reason, Instant createdAt) {
        this.userId = userId;
        this.reservationId = reservationId;
        this.pointDelta = pointDelta;
        this.balanceAfter = balanceAfter;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public int getPointDelta() {
        return pointDelta;
    }

    public int getBalanceAfter() {
        return balanceAfter;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
