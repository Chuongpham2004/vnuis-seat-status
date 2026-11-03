package vn.vnuis.library.iot;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.vnuis.library.domain.Enums.Connectivity;
import vn.vnuis.library.realtime.SeatEventBroadcaster;
import vn.vnuis.library.repository.IotDeviceRepository;
import vn.vnuis.library.seat.SeatInfo;
import vn.vnuis.library.seat.SeatState;
import vn.vnuis.library.seat.SeatStateStore;
import vn.vnuis.library.web.dto.Dtos.DeviceView;

/**
 * Device connectivity (ONLINE via status message or any telemetry, OFFLINE via Last Will)
 * and heartbeat bookkeeping. Heartbeat DB writes are throttled.
 */
@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);
    private static final Duration HEARTBEAT_WRITE_INTERVAL = Duration.ofSeconds(20);

    private final IotDeviceRepository devices;
    private final SeatStateStore store;
    private final SeatEventBroadcaster broadcaster;
    private final Map<String, Instant> lastHeartbeatWrite = new ConcurrentHashMap<>();

    public DeviceService(IotDeviceRepository devices, SeatStateStore store, SeatEventBroadcaster broadcaster) {
        this.devices = devices;
        this.store = store;
        this.broadcaster = broadcaster;
    }

    public void onTelemetry(SeatInfo seat) {
        Instant now = Instant.now();
        boolean knownOnline = store.find(seat.seatCode()).map(SeatState::deviceOnline).orElse(false);
        if (!knownOnline) {
            setConnectivity(seat, true, now); // telemetry proves the device is alive
            return;
        }
        Instant last = lastHeartbeatWrite.get(seat.seatCode());
        if (last == null || Duration.between(last, now).compareTo(HEARTBEAT_WRITE_INTERVAL) >= 0) {
            devices.touchHeartbeat(seat.seatId(), now);
            lastHeartbeatWrite.put(seat.seatCode(), now);
        }
    }

    public void onStatus(SeatInfo seat, boolean online) {
        setConnectivity(seat, online, Instant.now());
    }

    private void setConnectivity(SeatInfo seat, boolean online, Instant now) {
        devices.updateConnectivity(seat.seatId(), online ? Connectivity.ONLINE : Connectivity.OFFLINE, now);
        lastHeartbeatWrite.put(seat.seatCode(), now);
        store.updateDeviceOnline(seat.seatCode(), online);
        devices.findBySeatId(seat.seatId()).ifPresent(d -> broadcaster.deviceChanged(DeviceView.of(d, seat, online)));
        store.find(seat.seatCode()).ifPresent(broadcaster::seatChanged);
        log.info("Device {} on seat {} is {}", seat.deviceId(), seat.seatCode(), online ? "ONLINE" : "OFFLINE");
    }
}
