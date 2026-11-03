package vn.vnuis.library.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import vn.vnuis.library.domain.Enums.Connectivity;

@Entity
@Table(name = "iot_devices")
public class IotDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "seat_id", nullable = false, unique = true)
    private UUID seatId;

    @Column(name = "device_id", nullable = false, unique = true)
    private String deviceId;

    @Column(name = "mac_address")
    private String macAddress;

    @Column(name = "mqtt_topic", nullable = false)
    private String mqttTopic;

    @Column(name = "threshold_cm", nullable = false)
    private int thresholdCm = 50;

    @Column(name = "is_simulated", nullable = false)
    private boolean simulated;

    @Enumerated(EnumType.STRING)
    @Column(name = "connectivity_status", nullable = false)
    private Connectivity connectivityStatus = Connectivity.OFFLINE;

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    protected IotDevice() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public String getMqttTopic() {
        return mqttTopic;
    }

    public int getThresholdCm() {
        return thresholdCm;
    }

    public boolean isSimulated() {
        return simulated;
    }

    public Connectivity getConnectivityStatus() {
        return connectivityStatus;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }
}
