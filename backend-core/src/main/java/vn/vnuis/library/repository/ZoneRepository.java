package vn.vnuis.library.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vnuis.library.domain.Zone;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    Optional<Zone> findByCode(String code);
}
