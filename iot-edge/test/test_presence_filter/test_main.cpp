// Host-side tests: pio test -e native
#include <unity.h>

#include "PresenceFilter.h"

static const float THRESHOLD = 50.0f;
static const uint32_t DEBOUNCE = 5000;

// Feed `value` every 200 ms from `start` until `end` (exclusive); returns the last time used.
static uint32_t feed(PresenceFilter &f, float value, uint32_t start, uint32_t end, int *flips = nullptr) {
    uint32_t t = start;
    for (; t < end; t += 200) {
        if (f.update(value, t) && flips) {
            (*flips)++;
        }
    }
    return t;
}

void setUp() {}
void tearDown() {}

void test_starts_empty() {
    PresenceFilter f(THRESHOLD, DEBOUNCE, 5);
    TEST_ASSERT_FALSE(f.isPresent());
    TEST_ASSERT_EQUAL_FLOAT(400.0f, f.averageCm());
}

void test_presence_needs_full_debounce_period() {
    PresenceFilter f(THRESHOLD, DEBOUNCE, 5);
    feed(f, 30.0f, 0, 4800);                 // 4.8 s of presence: not yet
    TEST_ASSERT_FALSE(f.isPresent());
    TEST_ASSERT_TRUE(f.isPending());
    feed(f, 30.0f, 4800, 6000);              // passes 5 s since the average dropped
    TEST_ASSERT_TRUE(f.isPresent());
}

void test_short_blip_is_ignored() {
    PresenceFilter f(THRESHOLD, DEBOUNCE, 5);
    uint32_t t = feed(f, 30.0f, 0, 2000);    // someone walks past for 2 s
    feed(f, 150.0f, t, t + 10000);
    TEST_ASSERT_FALSE(f.isPresent());
    TEST_ASSERT_FALSE(f.isPending());
}

void test_moving_average_smooths_and_debounce_absorbs_a_spike() {
    PresenceFilter f(THRESHOLD, DEBOUNCE, 5);
    uint32_t t = feed(f, 30.0f, 0, 6000);
    TEST_ASSERT_TRUE(f.isPresent());
    // One bogus 300 cm reading: average of (30,30,30,30,300) = 84 cm -> a flip is only *pending*.
    f.update(300.0f, t);
    TEST_ASSERT_EQUAL_FLOAT(84.0f, f.averageCm());
    TEST_ASSERT_TRUE(f.isPending());
    // Normal readings return; the spike slides out of the window and the pending flip is cancelled.
    feed(f, 30.0f, t + 200, t + 3000);
    TEST_ASSERT_TRUE(f.isPresent());
    TEST_ASSERT_FALSE(f.isPending());
    TEST_ASSERT_EQUAL_FLOAT(30.0f, f.averageCm());
}

void test_leaving_flips_back_after_debounce() {
    PresenceFilter f(THRESHOLD, DEBOUNCE, 5);
    int flips = 0;
    uint32_t t = feed(f, 30.0f, 0, 6000, &flips);
    t = feed(f, 180.0f, t, t + 7000, &flips);
    TEST_ASSERT_FALSE(f.isPresent());
    TEST_ASSERT_EQUAL_INT(2, flips);         // exactly one "sat" and one "left" event
}

void test_no_echo_counts_as_empty() {
    PresenceFilter f(THRESHOLD, DEBOUNCE, 5);
    uint32_t t = feed(f, 30.0f, 0, 6000);
    feed(f, -1.0f, t, t + 7000);             // sensor times out (nothing in range)
    TEST_ASSERT_FALSE(f.isPresent());
}

void test_standing_up_into_open_space_reads_empty() {
    // The sensor reports 350 cm when pulseIn times out (nothing within ~4 m).
    PresenceFilter f(THRESHOLD, DEBOUNCE, 5);
    int flips = 0;
    uint32_t t = feed(f, 32.0f, 0, 6000, &flips);
    TEST_ASSERT_TRUE(f.isPresent());
    feed(f, 350.0f, t, t + 5600, &flips);
    TEST_ASSERT_FALSE(f.isPresent());
    TEST_ASSERT_EQUAL_INT(2, flips);
}

void test_millis_wraparound() {
    PresenceFilter f(THRESHOLD, DEBOUNCE, 5);
    uint32_t start = 0xFFFFFFFFu - 2000;     // 2 s before millis() overflows
    for (uint32_t i = 0; i < 40; i++) {      // 8 s of presence across the wrap
        f.update(30.0f, start + i * 200);
    }
    TEST_ASSERT_TRUE(f.isPresent());
}

int main(int, char **) {
    UNITY_BEGIN();
    RUN_TEST(test_starts_empty);
    RUN_TEST(test_presence_needs_full_debounce_period);
    RUN_TEST(test_short_blip_is_ignored);
    RUN_TEST(test_moving_average_smooths_and_debounce_absorbs_a_spike);
    RUN_TEST(test_leaving_flips_back_after_debounce);
    RUN_TEST(test_no_echo_counts_as_empty);
    RUN_TEST(test_standing_up_into_open_space_reads_empty);
    RUN_TEST(test_millis_wraparound);
    return UNITY_END();
}
