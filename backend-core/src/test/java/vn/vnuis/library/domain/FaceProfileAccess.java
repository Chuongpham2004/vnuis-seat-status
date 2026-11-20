package vn.vnuis.library.domain;

/** Test access to FaceProfile's package-private (de)serialisation. */
public final class FaceProfileAccess {

    private FaceProfileAccess() {
    }

    public static byte[] pack(float[] v) {
        return FaceProfile.pack(v);
    }

    public static float[] unpack(byte[] b) {
        return FaceProfile.unpack(b);
    }
}
