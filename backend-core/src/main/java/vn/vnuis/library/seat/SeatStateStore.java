package vn.vnuis.library.seat;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import vn.vnuis.library.domain.SeatStatus;

/**
 * Reads/writes the Redis hash {@code seat:state:{seat_code}}:
 * {@code seatId, zoneCode, status, physicalOccupied ("0"|"1"), lastDistanceCm,
 * currentUserId, currentReservationId, deviceOnline ("0"|"1"), stateUpdatedAt}.
 * Empty strings stand for null.
 */
@Component
public class SeatStateStore {

    public static final String KEY_PREFIX = "seat:state:";

    static final String F_SEAT_ID = "seatId";
    static final String F_ZONE = "zoneCode";
    static final String F_STATUS = "status";
    static final String F_PHYSICAL = "physicalOccupied";
    static final String F_DISTANCE = "lastDistanceCm";
    static final String F_USER = "currentUserId";
    static final String F_RESERVATION = "currentReservationId";
    static final String F_ONLINE = "deviceOnline";
    static final String F_UPDATED = "stateUpdatedAt";

    private final StringRedisTemplate redis;

    public SeatStateStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public static String key(String seatCode) {
        return KEY_PREFIX + seatCode;
    }

    public Optional<SeatState> find(String seatCode) {
        Map<Object, Object> raw = redis.opsForHash().entries(key(seatCode));
        return raw.isEmpty() ? Optional.empty() : Optional.of(fromMap(seatCode, raw));
    }

    public SeatState require(String seatCode) {
        return find(seatCode).orElseThrow(() -> new IllegalStateException(
                "Seat state for " + seatCode + " is not initialised in Redis"));
    }

    /** One pipelined round trip for many seats; missing hashes are skipped. */
    @SuppressWarnings("unchecked")
    public Map<String, SeatState> findAll(Collection<String> seatCodes) {
        List<String> codes = List.copyOf(seatCodes);
        List<Object> results = redis.executePipelined(new SessionCallback<Object>() {
            @Override
            public <K, V> Object execute(@NonNull RedisOperations<K, V> operations) {
                for (String code : codes) {
                    operations.opsForHash().entries((K) key(code));
                }
                return null;
            }
        });
        Map<String, SeatState> out = new LinkedHashMap<>();
        for (int i = 0; i < codes.size(); i++) {
            Map<Object, Object> raw = (Map<Object, Object>) results.get(i);
            if (raw != null && !raw.isEmpty()) {
                out.put(codes.get(i), fromMap(codes.get(i), raw));
            }
        }
        return out;
    }

    public void save(SeatState state) {
        redis.opsForHash().putAll(key(state.seatCode()), toMap(state));
    }

    /** Sensor facts can be written without the seat lock (only the MQTT thread writes them). */
    public void updatePhysical(String seatCode, boolean present, Double distanceCm) {
        Map<String, String> fields = new HashMap<>();
        fields.put(F_PHYSICAL, present ? "1" : "0");
        fields.put(F_DISTANCE, distanceCm == null ? "" : String.valueOf(distanceCm));
        redis.opsForHash().putAll(key(seatCode), fields);
    }

    public void updateDeviceOnline(String seatCode, boolean online) {
        redis.opsForHash().put(key(seatCode), F_ONLINE, online ? "1" : "0");
    }

    static Map<String, String> toMap(SeatState s) {
        Map<String, String> m = new HashMap<>();
        m.put(F_SEAT_ID, nz(s.seatId()));
        m.put(F_ZONE, nz(s.zoneCode()));
        m.put(F_STATUS, s.status().name());
        m.put(F_PHYSICAL, s.physicalOccupied() ? "1" : "0");
        m.put(F_DISTANCE, s.lastDistanceCm() == null ? "" : String.valueOf(s.lastDistanceCm()));
        m.put(F_USER, nz(s.currentUserId()));
        m.put(F_RESERVATION, nz(s.currentReservationId()));
        m.put(F_ONLINE, s.deviceOnline() ? "1" : "0");
        m.put(F_UPDATED, s.stateUpdatedAt() == null ? Instant.now().toString() : s.stateUpdatedAt().toString());
        return m;
    }

    static SeatState fromMap(String seatCode, Map<Object, Object> m) {
        String distance = str(m, F_DISTANCE);
        String updated = str(m, F_UPDATED);
        String status = str(m, F_STATUS);
        return new SeatState(
                str(m, F_SEAT_ID),
                seatCode,
                str(m, F_ZONE),
                status == null ? SeatStatus.AVAILABLE : SeatStatus.valueOf(status),
                "1".equals(str(m, F_PHYSICAL)),
                distance == null ? null : Double.valueOf(distance),
                str(m, F_USER),
                str(m, F_RESERVATION),
                "1".equals(str(m, F_ONLINE)),
                updated == null ? null : Instant.parse(updated));
    }

    private static String str(Map<Object, Object> m, String field) {
        Object v = m.get(field);
        return v == null || v.toString().isEmpty() ? null : v.toString();
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }
}
