package vn.vnuis.library.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "zones")
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "floor_number", nullable = false)
    private int floorNumber;

    @Column(name = "is_quiet_zone", nullable = false)
    private boolean quietZone;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    protected Zone() {
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public boolean isQuietZone() {
        return quietZone;
    }

    public int getTotalSeats() {
        return totalSeats;
    }
}
