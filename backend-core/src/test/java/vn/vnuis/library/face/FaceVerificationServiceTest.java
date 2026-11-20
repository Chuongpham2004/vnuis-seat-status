package vn.vnuis.library.face;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import vn.vnuis.library.domain.FaceProfileAccess;

class FaceVerificationServiceTest {

    @Test
    void cosineOfIdenticalAndOppositeVectors() {
        float[] a = {0.6f, 0.8f, 0f};
        assertThat(FaceVerificationService.cosine(a, a)).isCloseTo(1.0, within(1e-6));
        assertThat(FaceVerificationService.cosine(a, new float[] {-0.6f, -0.8f, 0f})).isCloseTo(-1.0, within(1e-6));
        assertThat(FaceVerificationService.cosine(a, new float[] {0f, 0f, 1f})).isCloseTo(0.0, within(1e-6));
    }

    @Test
    void cosineIsScaleInvariant() {
        assertThat(FaceVerificationService.cosine(new float[] {1, 2, 3}, new float[] {2, 4, 6}))
                .isCloseTo(1.0, within(1e-6));
    }

    @Test
    void mismatchedOrEmptyVectorsNeverMatch() {
        assertThat(FaceVerificationService.cosine(new float[] {1, 0}, new float[] {1, 0, 0})).isEqualTo(-1);
        assertThat(FaceVerificationService.cosine(null, new float[] {1})).isEqualTo(-1);
        assertThat(FaceVerificationService.cosine(new float[] {0, 0}, new float[] {0, 0})).isEqualTo(-1);
    }

    @Test
    void embeddingRoundTripsThroughBytea() {
        float[] v = {0.1f, -0.25f, 3.5f, Float.MIN_VALUE};
        assertThat(FaceProfileAccess.unpack(FaceProfileAccess.pack(v))).containsExactly(v);
        assertThat(FaceProfileAccess.pack(new float[512])).hasSize(2048);
    }
}
