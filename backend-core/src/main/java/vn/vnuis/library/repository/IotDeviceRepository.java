package vn.vnuis.library.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vn.vnuis.library.domain.Enums.Connectivity;
import vn.vnuis.library.domain.IotDevice;

public interface IotDeviceRepository extends JpaRepository<IotDevice, UUID> {

    Optional<IotDevice> findBySeatId(UUID seatId);

    @Transactional
    @Modifying
    @Query("update IotDevice d set d.connectivityStatus = :status, d.lastHeartbeat = :at where d.seatId = :seatId")
    int updateConnectivity(@Param("seatId") UUID seatId, @Param("status") Connectivity status, @Param("at") Instant at);

    @Transactional
    @Modifying
    @Query("update IotDevice d set d.lastHeartbeat = :at where d.seatId = :seatId")
    int touchHeartbeat(@Param("seatId") UUID seatId, @Param("at") Instant at);
}
