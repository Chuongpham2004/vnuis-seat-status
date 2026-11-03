package vn.vnuis.library.credit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CreditPolicyTest {

    @Test
    void rewardIsCappedAtMax() {
        assertThat(CreditPolicy.apply(99, 2, 100)).isEqualTo(100);
        assertThat(CreditPolicy.apply(100, 2, 100)).isEqualTo(100);
    }

    @Test
    void penaltyNeverGoesBelowZero() {
        assertThat(CreditPolicy.apply(5, -10, 100)).isZero();
    }

    @Test
    void noShowFrom65DropsTo55AndStartsBan() {
        int after = CreditPolicy.apply(65, -10, 100);
        assertThat(after).isEqualTo(55);
        assertThat(CreditPolicy.triggersBan(after, -10, 60)).isTrue();
    }

    @Test
    void penaltyThatStaysAboveThresholdDoesNotBan() {
        assertThat(CreditPolicy.triggersBan(70, -10, 60)).isFalse();
        assertThat(CreditPolicy.triggersBan(60, -10, 60)).isFalse();
    }

    @Test
    void rewardNeverStartsBan() {
        assertThat(CreditPolicy.triggersBan(50, 2, 60)).isFalse();
    }

    @Test
    void probationResetOnlyAfterBanEndsAndOnlyBelowThreshold() {
        Instant now = Instant.parse("2026-11-03T10:00:00Z");
        assertThat(CreditPolicy.probationResetDue(55, now.minusSeconds(1), now, 60)).as("ban over, 55").isTrue();
        assertThat(CreditPolicy.probationResetDue(55, now, now, 60)).as("ban ends exactly now").isTrue();
        assertThat(CreditPolicy.probationResetDue(55, now.plusSeconds(1), now, 60)).as("still banned").isFalse();
        assertThat(CreditPolicy.probationResetDue(55, null, now, 60)).as("never banned").isFalse();
        assertThat(CreditPolicy.probationResetDue(60, now.minusSeconds(1), now, 60)).as("already at 60").isFalse();
    }
}
