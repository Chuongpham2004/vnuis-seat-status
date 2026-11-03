package vn.vnuis.library.web;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vnuis.library.config.CreditProperties;
import vn.vnuis.library.credit.CreditService;
import vn.vnuis.library.domain.User;
import vn.vnuis.library.error.ApiException;
import vn.vnuis.library.repository.UserRepository;
import vn.vnuis.library.security.AuthUser;
import vn.vnuis.library.security.JwtService;
import vn.vnuis.library.web.dto.Dtos.LoginRequest;
import vn.vnuis.library.web.dto.Dtos.LoginResponse;
import vn.vnuis.library.web.dto.Dtos.UserView;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CreditProperties creditProps;
    private final CreditService credit;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                          CreditProperties creditProps, CreditService credit) {
        this.credit = credit;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.creditProps = creditProps;
    }

    /** {@code username} is a student code (SV001) or an email address. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        String username = request.username().trim();
        User user = (username.contains("@")
                ? users.findByEmailIgnoreCase(username)
                : users.findByStudentCodeIgnoreCase(username))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "Wrong username or password"));
        return new LoginResponse(jwtService.issue(user), "Bearer", jwtService.ttl().toSeconds(), profile(user.getId()));
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal AuthUser auth) {
        return profile(auth.id());
    }

    /** Applies the probation reset (expired ban below 60 -> 60) before showing the profile. */
    private UserView profile(UUID userId) {
        credit.resetProbationIfDue(userId);
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        return UserView.of(user, creditProps.minToBook(), Instant.now());
    }
}
