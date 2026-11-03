package vn.vnuis.library.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import vn.vnuis.library.seat.SeatCatalog;
import vn.vnuis.library.seat.SeatInfo;
import vn.vnuis.library.seat.SeatStateMachine;

/**
 * Routes MQTT messages to the device registry and the seat state machine.
 * Never throws: an exception here would make Paho drop the connection.
 */
@Component
public class MqttInboundHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttInboundHandler.class);
    private static final Pattern TOPIC = Pattern.compile("^library/zone/([^/]+)/seat/([^/]+)/(telemetry|status)$");

    private final ObjectMapper mapper;
    private final SeatCatalog catalog;
    private final SeatStateMachine stateMachine;
    private final DeviceService devices;
    private final TelemetryLogWriter telemetryLog;

    public MqttInboundHandler(ObjectMapper mapper, SeatCatalog catalog, SeatStateMachine stateMachine,
                              DeviceService devices, TelemetryLogWriter telemetryLog) {
        this.mapper = mapper;
        this.catalog = catalog;
        this.stateMachine = stateMachine;
        this.devices = devices;
        this.telemetryLog = telemetryLog;
    }

    @ServiceActivator(inputChannel = MqttConfig.INBOUND_CHANNEL)
    public void handle(Message<?> message) {
        String topic = String.valueOf(message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC));
        Object raw = message.getPayload();
        String body = raw instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : String.valueOf(raw);
        try {
            Matcher m = TOPIC.matcher(topic);
            if (!m.matches()) {
                log.debug("Ignoring MQTT topic {}", topic);
                return;
            }
            Optional<SeatInfo> seat = catalog.find(m.group(2));
            if (seat.isEmpty()) {
                log.warn("MQTT message for unknown seat {} on {}", m.group(2), topic);
                return;
            }
            if ("telemetry".equals(m.group(3))) {
                onTelemetry(seat.get(), body);
            } else {
                onStatus(seat.get(), body);
            }
        } catch (Exception e) {
            log.warn("Bad MQTT message on {}: {} ({})", topic, abbreviate(body), e.toString());
        }
    }

    private void onTelemetry(SeatInfo seat, String body) throws Exception {
        MqttPayloads.Telemetry t = mapper.readValue(body, MqttPayloads.Telemetry.class);
        // Devices send the debounced flag; fall back to the raw distance only if it is missing.
        boolean present = t.objectDetected() != null
                ? t.objectDetected()
                : t.distanceCm() != null && t.distanceCm() >= 0 && t.distanceCm() < seat.thresholdCm();
        devices.onTelemetry(seat);
        telemetryLog.record(seat, present, t.distanceCm());
        stateMachine.onPresence(seat.seatCode(), present, t.distanceCm());
    }

    private void onStatus(SeatInfo seat, String body) throws Exception {
        if (body == null || body.isBlank()) {
            return; // retained message cleared
        }
        String status = body.trim().startsWith("{")
                ? mapper.readValue(body, MqttPayloads.Status.class).status()
                : body.trim().replace("\"", "");
        if ("ONLINE".equalsIgnoreCase(status)) {
            devices.onStatus(seat, true);
        } else if ("OFFLINE".equalsIgnoreCase(status)) {
            devices.onStatus(seat, false);
        } else {
            log.warn("Unknown device status '{}' for {}", status, seat.seatCode());
        }
    }

    private static String abbreviate(String s) {
        return s == null || s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
