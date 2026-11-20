package vn.vnuis.library.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** One enrol / verify attempt (for FAR/FRR and latency figures). */
@Entity
@Table(name = "face_verification_log")
public class FaceVerificationLog {

    public enum Purpose { ENROLL, VERIFY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String purpose;

    @Column(nullable = false)
    private String result;

    private BigDecimal score;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected FaceVerificationLog() {
    }

    public FaceVerificationLog(UUID userId, Purpose purpose, String result, Double score, Integer latencyMs) {
        this.userId = userId;
        this.purpose = purpose.name();
        this.result = result;
        this.score = score == null ? null : BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
        this.latencyMs = latencyMs;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getResult() {
        return result;
    }

    public BigDecimal getScore() {
        return score;
    }
}
