package vn.vnuis.library.realtime;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import vn.vnuis.library.seat.SeatCatalog;
import vn.vnuis.library.seat.SeatInfo;
import vn.vnuis.library.seat.SeatQueryService;
import vn.vnuis.library.seat.SeatState;
import vn.vnuis.library.web.dto.Dtos.DeviceView;
import vn.vnuis.library.web.dto.Dtos.Notification;
import vn.vnuis.library.web.dto.Dtos.SeatView;

/** Pushes state changes to STOMP subscribers. Failures are logged, never thrown into business code. */
@Component
public class SeatEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(SeatEventBroadcaster.class);

    private final SimpMessagingTemplate messaging;
    private final SeatCatalog catalog;
    private final SeatQueryService queries;

    public SeatEventBroadcaster(SimpMessagingTemplate messaging, SeatCatalog catalog, SeatQueryService queries) {
        this.messaging = messaging;
        this.catalog = catalog;
        this.queries = queries;
    }

    public void seatChanged(SeatState state) {
        try {
            SeatInfo info = catalog.require(state.seatCode());
            messaging.convertAndSend("/topic/seats", SeatView.of(info, state));
            queries.zone(info.zoneCode()).ifPresent(zone ->
                    messaging.convertAndSend("/topic/zones/" + zone.code() + "/occupancy", zone));
        } catch (RuntimeException e) {
            log.warn("Could not broadcast seat {}: {}", state.seatCode(), e.toString());
        }
    }

    public void deviceChanged(DeviceView device) {
        try {
            messaging.convertAndSend("/topic/devices", device);
        } catch (RuntimeException e) {
            log.warn("Could not broadcast device {}: {}", device.deviceId(), e.toString());
        }
    }

    public void notifyUser(UUID userId, String type, String message, Object data) {
        if (userId == null) {
            return;
        }
        try {
            messaging.convertAndSendToUser(userId.toString(), "/queue/notifications",
                    new Notification(type, message, data, Instant.now()));
        } catch (RuntimeException e) {
            log.warn("Could not notify user {}: {}", userId, e.toString());
        }
    }
}
