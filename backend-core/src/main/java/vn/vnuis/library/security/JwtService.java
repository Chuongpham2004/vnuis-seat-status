package vn.vnuis.library.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import vn.vnuis.library.config.AppProperties;
import vn.vnuis.library.domain.Enums.Role;
import vn.vnuis.library.domain.User;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(AppProperties.Jwt props) {
        byte[] secret = props.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes (HS256)");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.ttl = Duration.ofMinutes(props.ttlMinutes());
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("sc", user.getStudentCode())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public Duration ttl() {
        return ttl;
    }

    /** Returns the caller if the token is valid and unexpired, otherwise empty. */
    public Optional<AuthUser> verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthUser(
                    UUID.fromString(claims.getSubject()),
                    claims.get("sc", String.class),
                    Role.valueOf(claims.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Accepts either "Bearer xxx" or a bare token. */
    public Optional<AuthUser> verifyHeader(String header) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        String token = header.regionMatches(true, 0, "Bearer ", 0, 7) ? header.substring(7) : header;
        return verify(token.trim());
    }
}
