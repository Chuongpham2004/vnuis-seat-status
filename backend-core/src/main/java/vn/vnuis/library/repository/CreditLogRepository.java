package vn.vnuis.library.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vnuis.library.domain.CreditLog;

public interface CreditLogRepository extends JpaRepository<CreditLog, Long> {

    List<CreditLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<CreditLog> findByReservationId(UUID reservationId);
}
