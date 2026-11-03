package vn.vnuis.library.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Spring Data Redis (Lettuce) handles seat hashes, TTL timers and expiry events;
 * Redisson is used only for distributed locks. Both point at the same server.
 */
@Configuration
public class RedisConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisConnectionDetails details) {
        RedisConnectionDetails.Standalone standalone = details.getStandalone();
        Config config = new Config();
        SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + standalone.getHost() + ":" + standalone.getPort())
                .setDatabase(standalone.getDatabase())
                .setConnectionMinimumIdleSize(4)
                .setConnectionPoolSize(32);
        if (details.getPassword() != null && !details.getPassword().isBlank()) {
            server.setPassword(details.getPassword());
        }
        if (details.getUsername() != null && !details.getUsername().isBlank()) {
            server.setUsername(details.getUsername());
        }
        return Redisson.create(config);
    }

    /** Container for the keyevent listener; started by Spring's lifecycle. */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
