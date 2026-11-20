package vn.vnuis.library.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.vnuis.library.domain.FaceVerificationLog;

public interface FaceVerificationLogRepository extends JpaRepository<FaceVerificationLog, Long> {

    List<FaceVerificationLog> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
