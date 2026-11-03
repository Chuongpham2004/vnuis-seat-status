package vn.vnuis.library.security;

import java.security.Principal;
import java.util.UUID;
import vn.vnuis.library.domain.Enums.Role;

/**
 * The authenticated caller, built from a verified JWT. Also used as the STOMP
 * principal, so {@link #getName()} (the user id) is the key for
 * {@code /user/queue/...} destinations.
 */
public record AuthUser(UUID id, String studentCode, Role role) implements Principal {

    @Override
    public String getName() {
        return id.toString();
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
