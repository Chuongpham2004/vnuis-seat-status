package vn.vnuis.library.web;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vnuis.library.face.FaceChallengeStore.Challenge;
import vn.vnuis.library.face.FaceServiceClient.Frame;
import vn.vnuis.library.face.FaceVerificationService;
import vn.vnuis.library.face.FaceVerificationService.Status;
import vn.vnuis.library.face.FaceVerificationService.Verified;
import vn.vnuis.library.security.AuthUser;
import vn.vnuis.library.web.dto.Dtos.FaceEnrollRequest;
import vn.vnuis.library.web.dto.Dtos.FaceFrame;
import vn.vnuis.library.web.dto.Dtos.FaceVerifyRequest;

/**
 * Face enrolment / verification for the signed-in student.
 * Flow: POST /challenge -> capture frames for each step -> POST /verify -> faceToken -> POST /reservations.
 */
@RestController
@RequestMapping("/api/v1/face")
public class FaceController {

    private final FaceVerificationService faces;

    public FaceController(FaceVerificationService faces) {
        this.faces = faces;
    }

    @GetMapping("/me")
    public Status status(@AuthenticationPrincipal AuthUser auth) {
        return faces.status(auth.id());
    }

    /** 200 {challengeId, steps, expiresAt} / 429 FACE_VERIFY_LOCKED */
    @PostMapping("/challenge")
    public Challenge challenge(@AuthenticationPrincipal AuthUser auth) {
        return faces.challenge(auth.id());
    }

    /** 200 / 400 CONSENT_REQUIRED / 409 FACE_ALREADY_ENROLLED / 410 FACE_CHALLENGE_EXPIRED / 422 NO_FACE|MULTIPLE_FACES|FACE_LIVENESS_FAILED */
    @PostMapping("/enroll")
    public Status enroll(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody FaceEnrollRequest request) {
        return faces.enroll(auth.id(), request.challengeId(), request.consent(), frames(request.frames()));
    }

    /** 200 {faceToken, score, expiresAt} / 409 FACE_NOT_ENROLLED / 410 / 422 FACE_NOT_MATCHED|... / 429 */
    @PostMapping("/verify")
    public Verified verify(@AuthenticationPrincipal AuthUser auth, @Valid @RequestBody FaceVerifyRequest request) {
        return faces.verify(auth.id(), request.challengeId(), frames(request.frames()));
    }

    /** Withdraw consent: the stored template is deleted. */
    @DeleteMapping("/me")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser auth) {
        faces.delete(auth.id());
        return ResponseEntity.noContent().build();
    }

    private static List<Frame> frames(List<FaceFrame> frames) {
        return frames.stream().map(f -> new Frame(f.step(), f.image())).toList();
    }
}
