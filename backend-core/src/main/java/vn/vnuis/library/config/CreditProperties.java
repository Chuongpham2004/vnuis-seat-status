package vn.vnuis.library.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Credit score rules ({@code app.credit.*}). */
@ConfigurationProperties("app.credit")
public record CreditProperties(
        @DefaultValue("60") int minToBook,
        @DefaultValue("10") int noShowPenalty,
        @DefaultValue("2") int onTimeReward,
        @DefaultValue("100") int maxScore,
        @DefaultValue("3") int banDays) {
}
