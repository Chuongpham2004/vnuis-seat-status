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
import vn.vnuis.library.domain.Enums.SeatType;

@Entity
@Table(name = "seats")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Column(name = "seat_code", nullable = false, unique = true)
    private String seatCode;

    @Column(name = "qr_code_token", nullable = false, unique = true)
    private String qrCodeToken;

    @Column(name = "has_power_outlet", nullable = false)
    private boolean hasPowerOutlet;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", nullable = false)
    private SeatType seatType;

    @Column(name = "coord_x", nullable = false)
    private int coordX;

    @Column(name = "coord_y", nullable = false)
    private int coordY;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false)
    private SeatStatus currentStatus = SeatStatus.AVAILABLE;

    @Column(name = "away_since")
    private Instant awaySince;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Seat() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public String getSeatCode() {
        return seatCode;
    }

    public String getQrCodeToken() {
        return qrCodeToken;
    }

    public boolean isHasPowerOutlet() {
        return hasPowerOutlet;
    }

    public SeatType getSeatType() {
        return seatType;
    }

    public int getCoordX() {
        return coordX;
    }

    public int getCoordY() {
        return coordY;
    }

    public SeatStatus getCurrentStatus() {
        return currentStatus;
    }

    public Instant getAwaySince() {
        return awaySince;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
