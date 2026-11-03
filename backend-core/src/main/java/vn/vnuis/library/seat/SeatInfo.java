package vn.vnuis.library.seat;

import java.util.UUID;
import vn.vnuis.library.domain.Enums.SeatType;

/** Static (non-changing) seat facts, cached in memory by {@link SeatCatalog}. */
public record SeatInfo(
        UUID seatId,
        String seatCode,
        UUID zoneId,
        String zoneCode,
        boolean quietZone,
        boolean hasPowerOutlet,
        SeatType seatType,
        int coordX,
        int coordY,
        String qrCodeToken,
        String deviceId,
        boolean simulatedDevice,
        int thresholdCm) {
}
