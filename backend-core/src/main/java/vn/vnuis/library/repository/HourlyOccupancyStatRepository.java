package vn.vnuis.library.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vn.vnuis.library.domain.HourlyOccupancyStat;

public interface HourlyOccupancyStatRepository extends JpaRepository<HourlyOccupancyStat, Long> {

    List<HourlyOccupancyStat> findByZoneIdAndBucketHourBetweenOrderByBucketHour(UUID zoneId, Instant from, Instant to);

    List<HourlyOccupancyStat> findByBucketHourBetweenOrderByBucketHour(Instant from, Instant to);

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO hourly_occupancy_stats
                (zone_id, bucket_hour, avg_occupancy_rate, day_of_week, is_weekend, is_exam_period)
            VALUES (:zoneId, :bucketHour, :rate, :dayOfWeek, :weekend, :exam)
            ON CONFLICT (zone_id, bucket_hour)
            DO UPDATE SET avg_occupancy_rate = EXCLUDED.avg_occupancy_rate
            """)
    int upsert(@Param("zoneId") UUID zoneId,
               @Param("bucketHour") Instant bucketHour,
               @Param("rate") BigDecimal rate,
               @Param("dayOfWeek") short dayOfWeek,
               @Param("weekend") boolean weekend,
               @Param("exam") boolean exam);
}
