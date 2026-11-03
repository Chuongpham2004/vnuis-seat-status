package vn.vnuis.library.stats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.vnuis.library.config.AppProperties;
import vn.vnuis.library.repository.HourlyOccupancyStatRepository;
import vn.vnuis.library.seat.SeatCatalog;
import vn.vnuis.library.seat.SeatQueryService;
import vn.vnuis.library.seat.ZoneInfo;

/**
 * Samples each zone's live occupancy once a minute and keeps a running average per
 * hour bucket in {@code hourly_occupancy_stats} - the real-data counterpart of the
 * synthetic history the AI service trains on.
 */
@Component
public class OccupancyStatsSampler {

    private static final Logger log = LoggerFactory.getLogger(OccupancyStatsSampler.class);

    private record Bucket(Instant hour, double sum, int count) {
    }

    private final SeatCatalog catalog;
    private final SeatQueryService queries;
    private final HourlyOccupancyStatRepository stats;
    private final AppProperties.Stats props;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public OccupancyStatsSampler(SeatCatalog catalog, SeatQueryService queries, HourlyOccupancyStatRepository stats,
                                 AppProperties.Stats props) {
        this.catalog = catalog;
        this.queries = queries;
        this.stats = stats;
        this.props = props;
    }

    @Scheduled(cron = "0 * * * * *")
    public void sample() {
        Instant hour = Instant.now().truncatedTo(ChronoUnit.HOURS);
        for (ZoneInfo zone : catalog.zones()) {
            try {
                double rate = queries.zone(zone.code()).orElseThrow().occupancyRate();
                Bucket b = buckets.compute(zone.code(), (k, old) ->
                        old == null || !old.hour().equals(hour)
                                ? new Bucket(hour, rate, 1)
                                : new Bucket(hour, old.sum() + rate, old.count() + 1));
                ZonedDateTime local = hour.atZone(props.zoneId());
                int dow = local.getDayOfWeek().getValue();
                stats.upsert(zone.zoneId(), hour,
                        BigDecimal.valueOf(b.sum() / b.count()).setScale(4, RoundingMode.HALF_UP),
                        (short) dow, dow >= 6, props.isExamPeriod(local.toLocalDate()));
            } catch (RuntimeException e) {
                log.debug("Occupancy sample for zone {} skipped: {}", zone.code(), e.toString());
            }
        }
    }
}
