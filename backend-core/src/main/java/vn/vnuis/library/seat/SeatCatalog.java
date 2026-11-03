package vn.vnuis.library.seat;

import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import vn.vnuis.library.domain.IotDevice;
import vn.vnuis.library.domain.Seat;
import vn.vnuis.library.domain.Zone;
import vn.vnuis.library.error.ApiException;
import vn.vnuis.library.repository.IotDeviceRepository;
import vn.vnuis.library.repository.SeatRepository;
import vn.vnuis.library.repository.ZoneRepository;

/**
 * In-memory copy of the static seat/zone/device layout (60 rows, never changes at runtime).
 * Live state lives in Redis ({@link SeatStateStore}).
 */
@Component
public class SeatCatalog {

    private final SeatRepository seats;
    private final ZoneRepository zones;
    private final IotDeviceRepository devices;

    private volatile Map<String, SeatInfo> byCode = Map.of();
    private volatile Map<UUID, SeatInfo> byId = Map.of();
    private volatile Map<String, ZoneInfo> zonesByCode = Map.of();

    public SeatCatalog(SeatRepository seats, ZoneRepository zones, IotDeviceRepository devices) {
        this.seats = seats;
        this.zones = zones;
        this.devices = devices;
    }

    @PostConstruct
    public synchronized void reload() {
        Map<UUID, Zone> zoneById = zones.findAll().stream().collect(Collectors.toMap(Zone::getId, Function.identity()));
        Map<UUID, IotDevice> deviceBySeat = devices.findAll().stream()
                .collect(Collectors.toMap(IotDevice::getSeatId, Function.identity()));

        Map<String, SeatInfo> codes = new LinkedHashMap<>();
        seats.findAll().stream()
                .sorted(Comparator.comparing(Seat::getSeatCode))
                .forEach(s -> {
                    Zone z = zoneById.get(s.getZoneId());
                    IotDevice d = deviceBySeat.get(s.getId());
                    codes.put(s.getSeatCode(), new SeatInfo(
                            s.getId(), s.getSeatCode(), z.getId(), z.getCode(), z.isQuietZone(),
                            s.isHasPowerOutlet(), s.getSeatType(), s.getCoordX(), s.getCoordY(), s.getQrCodeToken(),
                            d == null ? null : d.getDeviceId(),
                            d != null && d.isSimulated(),
                            d == null ? 50 : d.getThresholdCm()));
                });

        Map<String, ZoneInfo> zoneInfos = new LinkedHashMap<>();
        zoneById.values().stream()
                .sorted(Comparator.comparing(Zone::getCode))
                .forEach(z -> zoneInfos.put(z.getCode(), new ZoneInfo(
                        z.getId(), z.getCode(), z.getName(), z.getFloorNumber(), z.isQuietZone(),
                        (int) codes.values().stream().filter(s -> s.zoneId().equals(z.getId())).count())));

        this.byCode = Map.copyOf(codes);
        this.byId = codes.values().stream().collect(Collectors.toUnmodifiableMap(SeatInfo::seatId, Function.identity()));
        this.zonesByCode = zoneInfos;
    }

    public Optional<SeatInfo> find(String seatCode) {
        return Optional.ofNullable(seatCode).map(String::toUpperCase).map(byCode::get);
    }

    public SeatInfo require(String seatCode) {
        return find(seatCode).orElseThrow(() -> ApiException.notFound("SEAT_NOT_FOUND", "Seat " + seatCode + " does not exist"));
    }

    public SeatInfo requireById(UUID seatId) {
        SeatInfo info = byId.get(seatId);
        if (info == null) {
            throw ApiException.notFound("SEAT_NOT_FOUND", "Seat " + seatId + " does not exist");
        }
        return info;
    }

    /** Seats sorted by code. */
    public List<SeatInfo> all() {
        return byCode.values().stream().sorted(Comparator.comparing(SeatInfo::seatCode)).toList();
    }

    public List<SeatInfo> inZone(String zoneCode) {
        return all().stream().filter(s -> s.zoneCode().equalsIgnoreCase(zoneCode)).toList();
    }

    public Collection<ZoneInfo> zones() {
        return zonesByCode.values();
    }

    public Optional<ZoneInfo> zone(String zoneCode) {
        return Optional.ofNullable(zoneCode).map(String::toUpperCase).map(zonesByCode::get);
    }
}
