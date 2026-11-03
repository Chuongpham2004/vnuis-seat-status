package vn.vnuis.library.web;

import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vnuis.library.config.CreditProperties;
import vn.vnuis.library.credit.CreditService;
import vn.vnuis.library.credit.CreditService.CreditChange;
import vn.vnuis.library.domain.HourlyOccupancyStat;
import vn.vnuis.library.domain.User;
import vn.vnuis.library.error.ApiException;
import vn.vnuis.library.realtime.SeatEventBroadcaster;
import vn.vnuis.library.repository.HourlyOccupancyStatRepository;
import vn.vnuis.library.repository.IotDeviceRepository;
import vn.vnuis.library.repository.UserRepository;
import vn.vnuis.library.seat.SeatCatalog;
import vn.vnuis.library.seat.SeatInfo;
import vn.vnuis.library.seat.SeatState;
import vn.vnuis.library.seat.SeatStateMachine;
import vn.vnuis.library.seat.SeatStateStore;
import vn.vnuis.library.seat.ZoneInfo;
import vn.vnuis.library.web.dto.Dtos.CreditAdjustRequest;
import vn.vnuis.library.web.dto.Dtos.DeviceView;
import vn.vnuis.library.web.dto.Dtos.HourlyStatView;
import vn.vnuis.library.web.dto.Dtos.SeatQrView;
import vn.vnuis.library.web.dto.Dtos.UserView;

/** ROLE_ADMIN only (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final IotDeviceRepository devices;
    private final UserRepository users;
    private final HourlyOccupancyStatRepository stats;
    private final SeatCatalog catalog;
    private final SeatStateStore store;
    private final SeatStateMachine stateMachine;
    private final CreditService credit;
    private final CreditProperties creditProps;
    private final TransactionTemplate tx;
    private final SeatEventBroadcaster broadcaster;

    public AdminController(IotDeviceRepository devices, UserRepository users, HourlyOccupancyStatRepository stats,
                           SeatCatalog catalog, SeatStateStore store, SeatStateMachine stateMachine,
                           CreditService credit, CreditProperties creditProps, TransactionTemplate tx,
                           SeatEventBroadcaster broadcaster) {
        this.devices = devices;
        this.users = users;
        this.stats = stats;
        this.catalog = catalog;
        this.store = store;
        this.stateMachine = stateMachine;
        this.credit = credit;
        this.creditProps = creditProps;
        this.tx = tx;
        this.broadcaster = broadcaster;
    }

    @GetMapping("/devices")
    public List<DeviceView> devices() {
        Map<String, SeatState> live = store.findAll(catalog.all().stream().map(SeatInfo::seatCode).toList());
        return devices.findAll().stream()
                .map(d -> {
                    SeatInfo seat = catalog.requireById(d.getSeatId());
                    SeatState s = live.get(seat.seatCode());
                    return DeviceView.of(d, seat, s != null && s.deviceOnline());
                })
                .sorted(Comparator.comparing(DeviceView::seatCode))
                .toList();
    }

    @GetMapping("/users")
    public List<UserView> users(@RequestParam(required = false) String q,
                                @RequestParam(defaultValue = "50") int limit) {
        Instant now = Instant.now();
        String query = q == null || q.isBlank() ? null : q.trim();
        return users.search(query, PageRequest.of(0, Math.min(limit, 200))).stream()
                .map(u -> UserView.of(u, creditProps.minToBook(), now))
                .toList();
    }

    @PatchMapping("/users/{id}/credit")
    public UserView adjustCredit(@PathVariable UUID id, @Valid @RequestBody CreditAdjustRequest request) {
        CreditChange change = tx.execute(status -> {
            CreditChange c = request.delta() == 0 ? null
                    : credit.apply(id, request.delta(), "ADMIN: " + request.reason(), null);
            if (request.clearBan()) {
                credit.clearBan(id);
            }
            return c;
        });
        if (change != null) {
            broadcaster.notifyUser(id, "CREDIT_CHANGED",
                    "Credit adjusted by library staff: " + request.delta() + " (" + request.reason() + ")", change);
        }
        User user = users.findById(id).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        return UserView.of(user, creditProps.minToBook(), Instant.now());
    }

    @PostMapping("/seats/{seatCode}/force-release")
    public ResponseEntity<Void> forceRelease(@PathVariable String seatCode) {
        stateMachine.forceRelease(seatCode);
        return ResponseEntity.noContent().build();
    }

    /** QR tokens for printing the table stickers. */
    @GetMapping("/seats/qr")
    public List<SeatQrView> qrCodes(@RequestParam(required = false) String zone) {
        return catalog.all().stream()
                .filter(s -> zone == null || s.zoneCode().equalsIgnoreCase(zone))
                .map(s -> new SeatQrView(s.seatCode(), s.qrCodeToken()))
                .toList();
    }

    /** Heatmap data: zone x hour occupancy averages (default: last 7 days). */
    @GetMapping("/occupancy/hourly")
    public List<HourlyStatView> hourly(@RequestParam(required = false) Instant from,
                                       @RequestParam(required = false) Instant to) {
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(7, ChronoUnit.DAYS) : from;
        Map<UUID, ZoneInfo> zones = catalog.zones().stream()
                .collect(Collectors.toMap(ZoneInfo::zoneId, Function.identity()));
        return stats.findByBucketHourBetweenOrderByBucketHour(start, end).stream()
                .map((HourlyOccupancyStat s) -> new HourlyStatView(
                        zones.containsKey(s.getZoneId()) ? zones.get(s.getZoneId()).code() : null,
                        s.getBucketHour(), s.getAvgOccupancyRate().doubleValue(), s.getDayOfWeek(),
                        s.isWeekend(), s.isExamPeriod()))
                .toList();
    }
}
