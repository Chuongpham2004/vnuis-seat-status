package vn.vnuis.library.config;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Smaller settings groups under {@code app.*}. */
public final class AppProperties {

    private AppProperties() {
    }

    @ConfigurationProperties("app.jwt")
    public record Jwt(String secret, @DefaultValue("480") long ttlMinutes) {
    }

    @ConfigurationProperties("app.mqtt")
    public record Mqtt(
            String url,
            String username,
            String password,
            @DefaultValue("backend-core") String clientIdPrefix,
            @DefaultValue("library/zone/+/seat/+/telemetry") String telemetryTopic,
            @DefaultValue("library/zone/+/seat/+/status") String statusTopic) {
    }

    @ConfigurationProperties("app.redis")
    public record Redis(@DefaultValue("Ex") String keyspaceNotifications) {
    }

    @ConfigurationProperties("app.ai")
    public record Ai(@DefaultValue("http://localhost:8000") String baseUrl,
                     @DefaultValue("120") long readTimeoutSeconds) {
    }

    @ConfigurationProperties("app.cors")
    public record Cors(@DefaultValue("http://localhost:5173") List<String> allowedOrigins) {
    }

    @ConfigurationProperties("app.stats")
    public record Stats(@DefaultValue("Asia/Ho_Chi_Minh") ZoneId zoneId, @DefaultValue List<String> examPeriods) {

        /** True when {@code date} falls inside one of the configured "start/end" ranges. */
        public boolean isExamPeriod(LocalDate date) {
            for (String range : examPeriods) {
                String[] parts = range.trim().split("/");
                if (parts.length != 2) {
                    continue;
                }
                LocalDate start = LocalDate.parse(parts[0].trim());
                LocalDate end = LocalDate.parse(parts[1].trim());
                if (!date.isBefore(start) && !date.isAfter(end)) {
                    return true;
                }
            }
            return false;
        }
    }
}
