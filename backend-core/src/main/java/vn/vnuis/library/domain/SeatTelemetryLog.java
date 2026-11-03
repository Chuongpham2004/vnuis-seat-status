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
@Table(name = "seat_telemetry_logs")
public class SeatTelemetryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seat_id", nullable = false)
    private UUID seatId;

    @Column(name = "is_object_detected", nullable = false)
    private boolean objectDetected;

    @Column(name = "measured_distance_cm")
    private Double measuredDistanceCm;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected SeatTelemetryLog() {
    }

    public SeatTelemetryLog(UUID seatId, boolean objectDetected, Double measuredDistanceCm, Instant recordedAt) {
        this.seatId = seatId;
        this.objectDetected = objectDetected;
        this.measuredDistanceCm = measuredDistanceCm;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public boolean isObjectDetected() {
        return objectDetected;
    }

    public Double getMeasuredDistanceCm() {
        return measuredDistanceCm;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
