package vn.vnuis.library.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.vnuis.library.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByStudentCodeIgnoreCase(String studentCode);

    Optional<User> findByEmailIgnoreCase(String email);

    /** Row lock so concurrent credit changes for one user are serialised. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select u from User u
            where :q is null
               or lower(u.studentCode) like lower(concat('%', cast(:q as string), '%'))
               or lower(u.fullName) like lower(concat('%', cast(:q as string), '%'))
               or lower(u.email) like lower(concat('%', cast(:q as string), '%'))
            order by u.studentCode
            """)
    List<User> search(@Param("q") String q, Pageable pageable);
}
