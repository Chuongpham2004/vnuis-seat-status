package vn.vnuis.library.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.vnuis.library.domain.SeatStatus.AVAILABLE;
import static vn.vnuis.library.domain.SeatStatus.AWAY;
import static vn.vnuis.library.domain.SeatStatus.OCCUPIED;
import static vn.vnuis.library.domain.SeatStatus.RESERVED;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SeatStatusTest {

    /** The full transition table from the specification. */
    private static final Map<SeatStatus, Set<SeatStatus>> ALLOWED = Map.of(
            AVAILABLE, EnumSet.of(RESERVED, OCCUPIED),
            RESERVED, EnumSet.of(OCCUPIED, AVAILABLE),
            OCCUPIED, EnumSet.of(AWAY, AVAILABLE),
            AWAY, EnumSet.of(OCCUPIED, AVAILABLE));

    @Test
    void everyPairMatchesTheSpecification() {
        for (SeatStatus from : SeatStatus.values()) {
            for (SeatStatus to : SeatStatus.values()) {
                assertThat(from.canTransitionTo(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(ALLOWED.get(from).contains(to));
            }
        }
    }

    @Test
    void reservedCannotJumpToAwayAndAvailableCannotGoAway() {
        assertThat(RESERVED.canTransitionTo(AWAY)).isFalse();
        assertThat(AVAILABLE.canTransitionTo(AWAY)).isFalse();
        assertThat(AWAY.canTransitionTo(RESERVED)).isFalse();
    }
}
