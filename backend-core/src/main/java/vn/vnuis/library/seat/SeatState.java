package vn.vnuis.library.seat;

import java.time.Instant;
import java.util.UUID;
import vn.vnuis.library.domain.SeatStatus;

/** Live seat state, mirrored in the Redis hash {@code seat:state:{seat_code}}. */
public record SeatState(
        String seatId,
        String seatCode,
        String zoneCode,
        SeatStatus status,
        boolean physicalOccupied,
        Double lastDistanceCm,
        String currentUserId,
        String currentReservationId,
        boolean deviceOnline,
        Instant stateUpdatedAt) {

    /** New status with a new owner (null user/reservation clears them). */
    public SeatState withStatus(SeatStatus next, UUID userId, UUID reservationId) {
        return new SeatState(seatId, seatCode, zoneCode, next, physicalOccupied, lastDistanceCm,
                userId == null ? null : userId.toString(),
                reservationId == null ? null : reservationId.toString(),
                deviceOnline, Instant.now());
    }

    /** New status, same owner. */
    public SeatState withStatus(SeatStatus next) {
        return new SeatState(seatId, seatCode, zoneCode, next, physicalOccupied, lastDistanceCm,
                currentUserId, currentReservationId, deviceOnline, Instant.now());
    }

    public SeatState withPhysical(boolean present, Double distanceCm) {
        return new SeatState(seatId, seatCode, zoneCode, status, present, distanceCm,
                currentUserId, currentReservationId, deviceOnline, stateUpdatedAt);
    }

    public SeatState withDeviceOnline(boolean online) {
        return new SeatState(seatId, seatCode, zoneCode, status, physicalOccupied, lastDistanceCm,
                currentUserId, currentReservationId, online, stateUpdatedAt);
    }

    public UUID currentUserUuid() {
        return currentUserId == null ? null : UUID.fromString(currentUserId);
    }
}
