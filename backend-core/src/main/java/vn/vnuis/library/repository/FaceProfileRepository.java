package vn.vnuis.library.repository;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.vnuis.library.domain.FaceProfile;

public interface FaceProfileRepository extends JpaRepository<FaceProfile, UUID> {

    @Query("select f.userId from FaceProfile f where f.userId in :ids")
    Set<UUID> findEnrolledAmong(Collection<UUID> ids);
}
