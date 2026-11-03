package vn.vnuis.library.seat;

import java.util.UUID;

public record ZoneInfo(UUID zoneId, String code, String name, int floorNumber, boolean quietZone, int totalSeats) {
}
