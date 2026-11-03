#include "PresenceFilter.h"

PresenceFilter::PresenceFilter(float thresholdCm, uint32_t debounceMs, uint8_t window, float maxRangeCm)
    : thresholdCm_(thresholdCm),
      debounceMs_(debounceMs),
      window_(window == 0 ? 1 : (window > MAX_WINDOW ? MAX_WINDOW : window)),
      maxRangeCm_(maxRangeCm) {
    for (uint8_t i = 0; i < MAX_WINDOW; i++) {
        samples_[i] = maxRangeCm_;
    }
}

float PresenceFilter::averageCm() const {
    if (count_ == 0) {
        return maxRangeCm_;
    }
    float sum = 0;
    for (uint8_t i = 0; i < count_; i++) {
        sum += samples_[i];
    }
    return sum / count_;
}

bool PresenceFilter::update(float rawCm, uint32_t nowMs) {
    // A timeout (no echo) or an out-of-range value means nothing is in front of the sensor.
    float value = (rawCm < 0 || rawCm > maxRangeCm_) ? maxRangeCm_ : rawCm;
    samples_[head_] = value;
    head_ = (head_ + 1) % window_;
    if (count_ < window_) {
        count_++;
    }

    bool candidate = averageCm() < thresholdCm_;
    if (candidate == present_) {
        pending_ = false; // back to the current state: cancel any pending flip
        return false;
    }
    if (!pending_) {
        pending_ = true;
        pendingSince_ = nowMs;
    }
    // Unsigned subtraction stays correct across the ~49-day millis() wrap-around.
    if ((uint32_t)(nowMs - pendingSince_) >= debounceMs_) {
        present_ = candidate;
        pending_ = false;
        return true;
    }
    return false;
}
