package vn.vnuis.library.seat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.stereotype.Component;
import vn.vnuis.library.config.SeatProperties;
import vn.vnuis.library.domain.Enums.Connectivity;
import vn.vnuis.library.domain.IotDevice;
import vn.vnuis.library.domain.Reservation;
import vn.vnuis.library.domain.ReservationStatus;
import vn.vnuis.library.domain.Seat;
import vn.vnuis.library.domain.SeatStatus;
import vn.vnuis.library.repository.IotDeviceRepository;
import vn.vnuis.library.repository.ReservationRepository;
import vn.vnuis.library.repository.SeatRepository;

/**
 * On startup, rebuilds every {@code seat:state:{seat_code}} hash from PostgreSQL
 * (seats + open reservations + iot_devices) and re-arms missing TTL timers, so a
 * backend or Redis restart never leaves the two stores out of sync. Overdue
 * deadlines are then processed by {@link ReconciliationJob}, and only after that
 * does the MQTT adapter start consuming telemetry.
 */
@Component
public class SeatStateBootstrapper {

    private static final Logger log = LoggerFactory.getLogger(SeatStateBootstrapper.class);

    private final SeatCatalog catalog;
    private final SeatStateStore store;
    private final SeatTimerService timers;
    private final SeatRepository seats;
    private final ReservationRepository reservations;
    private final IotDeviceRepository devices;
    private final ReconciliationJob reconciliation;
    private final MqttPahoMessageDrivenChannelAdapter mqttAdapter;
    private final SeatProperties props;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    public SeatStateBootstrapper(SeatCatalog catalog, SeatStateStore store, SeatTimerService timers,
                                 SeatRepository seats, ReservationRepository reservations, IotDeviceRepository devices,
                                 ReconciliationJob reconciliation, MqttPahoMessageDrivenChannelAdapter mqttAdapter,
                                 SeatProperties props) {
        this.catalog = catalog;
        this.store = store;
        this.timers = timers;
        this.seats = seats;
        this.reservations = reservations;
        this.devices = devices;
        this.reconciliation = reconciliation;
        this.mqttAdapter = mqttAdapter;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        int repaired = syncAll();
        log.info("Seat state hydrated into Redis for {} seats ({} DB rows repaired)", catalog.all().size(), repaired);
        reconciliation.runOnce(Duration.ZERO);
        ready.set(true);
        reconciliation.markReady();
        mqttAdapter.start();
        log.info("MQTT inbound adapter started");
    }

    public boolean isReady() {
        return ready.get();
    }

    /** @return number of seat rows whose status had to be corrected */
    public int syncAll() {
        catalog.reload();
        Instant now = Instant.now();
        Map<UUID, Reservation> openBySeat = reservations.findByStatusIn(ReservationStatus.OPEN).stream()
                .collect(Collectors.toMap(Reservation::getSeatId, Function.identity(), (a, b) -> a));
        Map<UUID, IotDevice> deviceBySeat = devices.findAll().stream()
                .collect(Collectors.toMap(IotDevice::getSeatId, Function.identity()));

        int repaired = 0;
        for (Seat seat : seats.findAll()) {
            SeatInfo info = catalog.require(seat.getSeatCode());
            Reservation open = openBySeat.get(seat.getId());
            SeatStatus status = deriveStatus(seat, open);

            Instant awaySince = status == SeatStatus.AWAY
                    ? Optional.ofNullable(seat.getAwaySince()).orElse(now)
                    : null;
            if (status != seat.getCurrentStatus() || (status == SeatStatus.AWAY && seat.getAwaySince() == null)) {
                log.warn("Repairing seat {}: DB status {} -> {}", seat.getSeatCode(), seat.getCurrentStatus(), status);
                seats.updateStatus(seat.getId(), status, awaySince, now);
                repaired++;
            }

            // Sensor facts survive in Redis across a backend restart; keep them if present.
            Optional<SeatState> previous = store.find(seat.getSeatCode());
            IotDevice device = deviceBySeat.get(seat.getId());
            store.save(new SeatState(
                    seat.getId().toString(),
                    seat.getSeatCode(),
                    info.zoneCode(),
                    status,
                    previous.map(SeatState::physicalOccupied).orElse(false),
                    previous.map(SeatState::lastDistanceCm).orElse(null),
                    open == null || open.getUserId() == null ? null : open.getUserId().toString(),
                    open == null ? null : open.getId().toString(),
                    device != null && device.getConnectivityStatus() == Connectivity.ONLINE,
                    now));

            rearmTimers(seat.getSeatCode(), status, open, awaySince, now);
        }
        return repaired;
    }

    private static SeatStatus deriveStatus(Seat seat, Reservation open) {
        if (open == null) {
            return SeatStatus.AVAILABLE;
        }
        if (open.getStatus() == ReservationStatus.PENDING) {
            return SeatStatus.RESERVED;
        }
        return seat.getCurrentStatus() == SeatStatus.AWAY ? SeatStatus.AWAY : SeatStatus.OCCUPIED;
    }

    private void rearmTimers(String seatCode, SeatStatus status, Reservation open, Instant awaySince, Instant now) {
        if (status == SeatStatus.RESERVED && open.getExpireAt() != null && !timers.reservationExpiryExists(open.getId())) {
            Duration remaining = Duration.between(now, open.getExpireAt());
            if (!remaining.isNegative() && !remaining.isZero()) {
                timers.startReservationExpiry(open.getId(), seatCode, remaining);
            } // else overdue: handled by reconciliation right after the sync
        }
        if (status == SeatStatus.AWAY && !timers.awayExists(seatCode)) {
            Duration remaining = Duration.between(now, awaySince.plus(props.awayTtl()));
            if (!remaining.isNegative() && !remaining.isZero()) {
                timers.startAway(seatCode, remaining);
            }
        }
    }
}
