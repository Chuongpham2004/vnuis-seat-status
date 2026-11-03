#include "UltrasonicSensor.h"

// Speed of sound ~343 m/s = 0.0343 cm/us; the pulse travels there and back.
static const float CM_PER_US = 0.0343f / 2.0f;

UltrasonicSensor::UltrasonicSensor(uint8_t trigPin, uint8_t echoPin, float noEchoCm, uint32_t timeoutUs)
    : trigPin_(trigPin), echoPin_(echoPin), noEchoCm_(noEchoCm), timeoutUs_(timeoutUs) {}

void UltrasonicSensor::begin() {
    pinMode(trigPin_, OUTPUT);
    digitalWrite(trigPin_, LOW);
    pinMode(echoPin_, INPUT);
}

float UltrasonicSensor::pingOnce() {
    digitalWrite(trigPin_, LOW);
    delayMicroseconds(2);
    digitalWrite(trigPin_, HIGH);
    delayMicroseconds(10);
    digitalWrite(trigPin_, LOW);
    unsigned long echoUs = pulseIn(echoPin_, HIGH, timeoutUs_);
    if (echoUs == 0) {
        return noEchoCm_; // timeout: nothing within range -> treat as an empty seat
    }
    return echoUs * CM_PER_US;
}

float UltrasonicSensor::readCm() {
    float a = pingOnce();
    delay(10); // let echoes from the previous ping die out
    float b = pingOnce();
    delay(10);
    float c = pingOnce();

    // Median of three rejects a single spike (one stray echo or one timeout).
    if (a > b) { float t = a; a = b; b = t; }
    if (b > c) { float t = b; b = c; c = t; }
    if (a > b) { float t = a; a = b; b = t; }
    return b;
}
