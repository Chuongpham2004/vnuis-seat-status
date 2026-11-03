package vn.vnuis.library.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vnuis.library.domain.Reservation;
import vn.vnuis.library.domain.ReservationStatus;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findFirstBySeatIdAndStatusIn(UUID seatId, Collection<ReservationStatus> statuses);

    Optional<Reservation> findFirstByUserIdAndStatusIn(UUID userId, Collection<ReservationStatus> statuses);

    boolean existsByUserIdAndStatusIn(UUID userId, Collection<ReservationStatus> statuses);

    List<Reservation> findByStatusIn(Collection<ReservationStatus> statuses);

    List<Reservation> findByStatusAndExpireAtBefore(ReservationStatus status, Instant cutoff);

    List<Reservation> findByUserIdOrderByReservedAtDesc(UUID userId, Pageable pageable);
}
