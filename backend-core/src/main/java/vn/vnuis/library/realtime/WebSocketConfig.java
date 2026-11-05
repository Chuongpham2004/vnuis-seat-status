package vn.vnuis.library.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import vn.vnuis.library.config.AppProperties;

/**
 * STOMP endpoints: {@code /ws} (native WebSocket) and {@code /ws-sockjs} (SockJS fallback).
 * <ul>
 *   <li>{@code /topic/seats} - every seat change</li>
 *   <li>{@code /topic/zones/{code}/occupancy} - zone counters</li>
 *   <li>{@code /topic/devices} - device ONLINE/OFFLINE (admins only)</li>
 *   <li>{@code /user/queue/notifications} - per-user reservation and credit alerts</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor authInterceptor;
    private final AppProperties.Cors cors;

    public WebSocketConfig(StompAuthInterceptor authInterceptor, AppProperties.Cors cors) {
        this.authInterceptor = authInterceptor;
        this.cors = cors;
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        String[] origins = cors.allowedOrigins().toArray(String[]::new);
        registry.addEndpoint("/ws").setAllowedOriginPatterns(origins);
        registry.addEndpoint("/ws-sockjs").setAllowedOriginPatterns(origins).withSockJS();
    }

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(@NonNull ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
