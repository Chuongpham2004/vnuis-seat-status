package vn.vnuis.library.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vnuis.library.domain.SeatTelemetryLog;

public interface SeatTelemetryLogRepository extends JpaRepository<SeatTelemetryLog, Long> {

    long countBySeatId(UUID seatId);
}
