package vn.vnuis.library.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.UUID;

/** A student's enrolled ArcFace template (no image is ever stored). */
@Entity
@Table(name = "face_profiles")
public class FaceProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private byte[] embedding;

    @Column(nullable = false)
    private String model;

    @Column(name = "consent_at", nullable = false)
    private Instant consentAt;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    protected FaceProfile() {
    }

    public FaceProfile(UUID userId, float[] embedding, String model, Instant consentAt) {
        this.userId = userId;
        replace(embedding, model, consentAt);
    }

    public final void replace(float[] template, String modelName, Instant consent) {
        this.embedding = pack(template);
        this.model = modelName;
        this.consentAt = consent;
        this.enrolledAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public float[] getEmbedding() {
        return unpack(embedding);
    }

    public String getModel() {
        return model;
    }

    public Instant getConsentAt() {
        return consentAt;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    static byte[] pack(float[] v) {
        ByteBuffer buf = ByteBuffer.allocate(v.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : v) {
            buf.putFloat(f);
        }
        return buf.array();
    }

    static float[] unpack(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] v = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < v.length; i++) {
            v[i] = buf.getFloat();
        }
        return v;
    }
}
