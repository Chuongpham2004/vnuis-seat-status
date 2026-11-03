#pragma once

#include <Arduino.h>

/** HC-SR04 driver: median of three pings to reject single-ping spikes. */
class UltrasonicSensor {
public:
    /**
     * @param noEchoCm distance reported when pulseIn times out (no obstacle within ~4 m).
     *                 It must be well above the presence threshold so that a student
     *                 standing up into open space reliably reads as "empty".
     */
    UltrasonicSensor(uint8_t trigPin, uint8_t echoPin, float noEchoCm = 350.0f, uint32_t timeoutUs = 30000);

    void begin();

    /** Distance in cm; noEchoCm when no echo came back (open space). */
    float readCm();

private:
    float pingOnce();

    uint8_t trigPin_;
    uint8_t echoPin_;
    float noEchoCm_;
    uint32_t timeoutUs_;
};
