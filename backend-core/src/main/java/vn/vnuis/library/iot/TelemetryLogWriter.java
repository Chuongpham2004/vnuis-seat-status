package vn.vnuis.library.iot;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import vn.vnuis.library.domain.SeatTelemetryLog;
import vn.vnuis.library.repository.SeatTelemetryLogRepository;
import vn.vnuis.library.seat.SeatInfo;

/**
 * Persists telemetry sparingly: every presence change, plus one sample per seat per minute.
 * (60-100 seats with 30 s heartbeats would otherwise write ~10k rows/hour of duplicates.)
 */
@Component
public class TelemetryLogWriter {

    private static final Duration SAMPLE_INTERVAL = Duration.ofMinutes(1);

    private record Last(boolean present, Instant at) {
    }

    private final SeatTelemetryLogRepository repository;
    private final Map<String, Last> last = new ConcurrentHashMap<>();

    public TelemetryLogWriter(SeatTelemetryLogRepository repository) {
        this.repository = repository;
    }

    public void record(SeatInfo seat, boolean present, Double distanceCm) {
        Instant now = Instant.now();
        Last previous = last.get(seat.seatCode());
        boolean changed = previous == null || previous.present() != present;
        boolean sampleDue = previous == null || Duration.between(previous.at(), now).compareTo(SAMPLE_INTERVAL) >= 0;
        if (changed || sampleDue) {
            repository.save(new SeatTelemetryLog(seat.seatId(), present, distanceCm, now));
            last.put(seat.seatCode(), new Last(present, now));
        }
    }
}
