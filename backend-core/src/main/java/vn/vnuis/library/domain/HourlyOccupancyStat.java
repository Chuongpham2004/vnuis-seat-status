package vn.vnuis.library.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hourly_occupancy_stats")
public class HourlyOccupancyStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Column(name = "bucket_hour", nullable = false)
    private Instant bucketHour;

    @Column(name = "avg_occupancy_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal avgOccupancyRate;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "is_weekend", nullable = false)
    private boolean weekend;

    @Column(name = "is_exam_period", nullable = false)
    private boolean examPeriod;

    protected HourlyOccupancyStat() {
    }

    public Long getId() {
        return id;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public Instant getBucketHour() {
        return bucketHour;
    }

    public BigDecimal getAvgOccupancyRate() {
        return avgOccupancyRate;
    }

    public short getDayOfWeek() {
        return dayOfWeek;
    }

    public boolean isWeekend() {
        return weekend;
    }

    public boolean isExamPeriod() {
        return examPeriod;
    }
}
