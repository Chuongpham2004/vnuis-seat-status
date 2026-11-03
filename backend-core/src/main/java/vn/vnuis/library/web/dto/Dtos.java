package vn.vnuis.library.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.vnuis.library.domain.Enums.BookingSource;
import vn.vnuis.library.domain.Enums.Role;
import vn.vnuis.library.domain.Enums.SeatType;
import vn.vnuis.library.domain.IotDevice;
import vn.vnuis.library.domain.Reservation;
import vn.vnuis.library.domain.ReservationStatus;
import vn.vnuis.library.domain.SeatStatus;
import vn.vnuis.library.domain.User;
import vn.vnuis.library.seat.SeatInfo;
import vn.vnuis.library.seat.SeatState;

/** Request / response bodies of the REST and WebSocket API. */
public final class Dtos {

    private Dtos() {
    }

    // ---------------- auth ----------------

    /** {@code username} accepts a student code (SV001) or an email. */
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, UserView user) {
    }

    public record UserView(UUID id, String studentCode, String email, String fullName, Role role,
                           int creditScore, Instant bannedUntil, boolean canBook) {

        public static UserView of(User u, int minToBook, Instant now) {
            boolean canBook = !u.isBannedAt(now) && u.getCreditScore() >= minToBook;
            return new UserView(u.getId(), u.getStudentCode(), u.getEmail(), u.getFullName(), u.getRole(),
                    u.getCreditScore(), u.getBannedUntil(), canBook);
        }
    }

    // ---------------- seats / zones ----------------

    /** Shape of GET /seats items and of every /topic/seats message. */
    public record SeatView(String seatCode, String zoneCode, SeatType seatType, boolean hasPowerOutlet,
                           boolean quietZone, int coordX, int coordY, SeatStatus status, boolean physicalOccupied,
                           Double lastDistanceCm, boolean deviceOnline, boolean simulatedDevice, String deviceId,
                           Instant updatedAt) {

        public static SeatView of(SeatInfo info, SeatState state) {
            return new SeatView(info.seatCode(), info.zoneCode(), info.seatType(), info.hasPowerOutlet(),
                    info.quietZone(), info.coordX(), info.coordY(),
                    state == null ? SeatStatus.AVAILABLE : state.status(),
                    state != null && state.physicalOccupied(),
                    state == null ? null : state.lastDistanceCm(),
                    state != null && state.deviceOnline(),
                    info.simulatedDevice(), info.deviceId(),
                    state == null ? null : state.stateUpdatedAt());
        }
    }

    /** Shape of GET /zones items and of /topic/zones/{code}/occupancy messages. */
    public record ZoneView(String code, String name, int floorNumber, boolean quietZone, int totalSeats,
                           int available, int reserved, int occupied, int away, double occupancyRate) {
    }

    public record SeatRecommendation(SeatView seat, double score, double zoneOccupancyRate, List<String> reasons) {
    }

    public record OccupancyView(ZoneView zone, List<HourlyStatView> last24Hours) {
    }

    public record HourlyStatView(String zoneCode, Instant bucketHour, double avgOccupancyRate, int dayOfWeek,
                                 boolean weekend, boolean examPeriod) {
    }

    // ---------------- reservations ----------------

    /** {@code source} may be APP_MAP (default) or AI_CHATBOT; WALK_IN is created by sensors only. */
    public record CreateReservationRequest(@NotBlank String seatCode, BookingSource source) {
    }

    public record AiReserveRequest(@NotBlank String seatCode) {
    }

    public record CheckInRequest(@NotBlank String qrCodeToken) {
    }

    public record ReservationView(UUID id, String seatCode, String zoneCode, ReservationStatus status,
                                  BookingSource bookingSource, Instant reservedAt, Instant expireAt,
                                  Instant checkInAt, Instant checkOutAt, Long secondsUntilExpiry) {

        public static ReservationView of(Reservation r, SeatInfo seat) {
            Long remaining = null;
            if (r.getStatus() == ReservationStatus.PENDING && r.getExpireAt() != null) {
                remaining = Math.max(0, Duration.between(Instant.now(), r.getExpireAt()).toSeconds());
            }
            return new ReservationView(r.getId(), seat.seatCode(), seat.zoneCode(), r.getStatus(),
                    r.getBookingSource(), r.getReservedAt(), r.getExpireAt(), r.getCheckInAt(), r.getCheckOutAt(),
                    remaining);
        }
    }

    // ---------------- admin ----------------

    public record CreditAdjustRequest(@Min(-100) @Max(100) int delta, @NotBlank @Size(max = 200) String reason,
                                      boolean clearBan) {
    }

    public record DeviceView(String deviceId, String seatCode, String zoneCode, boolean simulated,
                             boolean online, Instant lastHeartbeat, int thresholdCm, String mqttTopic) {

        public static DeviceView of(IotDevice d, SeatInfo seat, boolean online) {
            return new DeviceView(d.getDeviceId(), seat.seatCode(), seat.zoneCode(), d.isSimulated(), online,
                    d.getLastHeartbeat(), d.getThresholdCm(), d.getMqttTopic());
        }
    }

    public record SeatQrView(String seatCode, String qrCodeToken) {
    }

    // ---------------- realtime ----------------

    /** Payload on {@code /user/queue/notifications}. */
    public record Notification(String type, String message, Object data, Instant at) {
    }
}
