package vn.vnuis.library.realtime;

import java.security.Principal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import vn.vnuis.library.security.AuthUser;
import vn.vnuis.library.security.JwtService;

/**
 * Reads {@code Authorization: Bearer <jwt>} from the STOMP CONNECT frame.
 * Anonymous connections may watch public topics; a bad token is rejected;
 * {@code /topic/devices} is limited to admins.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    public StompAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        if (accessor.getCommand() == StompCommand.CONNECT) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header != null && !header.isBlank()) {
                AuthUser user = jwtService.verifyHeader(header)
                        .orElseThrow(() -> new MessageDeliveryException("Invalid access token"));
                accessor.setUser(user);
            }
        } else if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            String destination = accessor.getDestination();
            Principal user = accessor.getUser();
            if (destination != null && destination.startsWith("/topic/devices")
                    && !(user instanceof AuthUser au && au.isAdmin())) {
                throw new MessageDeliveryException("Only admins may subscribe to " + destination);
            }
        }
        return message;
    }
}
