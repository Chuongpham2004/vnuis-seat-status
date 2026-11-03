#pragma once

#include <stdint.h>

/**
 * Turns noisy HC-SR04 readings into a stable "someone is sitting" flag.
 *
 *  1. Moving average over the last N readings (a missing echo counts as max range).
 *  2. Time debounce: the averaged value must stay on the other side of the
 *     threshold for `debounceMs` before the published state flips.
 *
 * Pure C++ (no Arduino headers) so it can be unit-tested on the PC.
 */
class PresenceFilter {
public:
    static const uint8_t MAX_WINDOW = 16;

    PresenceFilter(float thresholdCm, uint32_t debounceMs, uint8_t window, float maxRangeCm = 400.0f);

    /**
     * Feed one raw reading in cm (negative = no echo / timeout).
     * @return true exactly when the debounced state changed on this call.
     */
    bool update(float rawCm, uint32_t nowMs);

    /** Debounced state. */
    bool isPresent() const { return present_; }

    /** Current moving average in cm (max range until the first sample). */
    float averageCm() const;

    /** What the average says right now, before debouncing. */
    bool candidatePresent() const { return count_ > 0 && averageCm() < thresholdCm_; }

    /** True while a state change is waiting for the debounce period to pass. */
    bool isPending() const { return pending_; }

private:
    float thresholdCm_;
    uint32_t debounceMs_;
    uint8_t window_;
    float maxRangeCm_;

    float samples_[MAX_WINDOW];
    uint8_t count_ = 0;
    uint8_t head_ = 0;

    bool present_ = false;
    bool pending_ = false;
    uint32_t pendingSince_ = 0;
};
