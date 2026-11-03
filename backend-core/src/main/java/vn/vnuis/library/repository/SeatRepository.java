package vn.vnuis.library.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vn.vnuis.library.domain.Seat;
import vn.vnuis.library.domain.SeatStatus;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    Optional<Seat> findBySeatCode(String seatCode);

    List<Seat> findByCurrentStatusAndAwaySinceBefore(SeatStatus status, Instant cutoff);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Seat s
               set s.currentStatus = :status, s.awaySince = :awaySince, s.updatedAt = :now
             where s.id = :id
            """)
    int updateStatus(@Param("id") UUID id,
                     @Param("status") SeatStatus status,
                     @Param("awaySince") Instant awaySince,
                     @Param("now") Instant now);
}
