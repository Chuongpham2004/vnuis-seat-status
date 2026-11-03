package vn.vnuis.library.iot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** JSON payloads defined in docs/mqtt-contract.md. */
public final class MqttPayloads {

    private MqttPayloads() {
    }

    /** {@code library/zone/{z}/seat/{s}/telemetry} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Telemetry(String deviceId, String seatCode, Double distanceCm, Boolean objectDetected,
                            Integer rssi, Boolean simulated, String fw, Long ts) {
    }

    /** {@code library/zone/{z}/seat/{s}/status} - ONLINE on connect, OFFLINE via Last Will. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(String deviceId, String status, Long ts) {
    }
}
