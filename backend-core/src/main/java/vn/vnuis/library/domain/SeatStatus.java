package vn.vnuis.library.domain;

/**
 * The 4 operational seat states and the transitions allowed between them.
 *
 * <pre>
 * AVAILABLE -> RESERVED (booking) | OCCUPIED (walk-in)
 * RESERVED  -> OCCUPIED (dual check-in) | AVAILABLE (no-show, cancel)
 * OCCUPIED  -> AWAY (absence) | AVAILABLE (check-out)
 * AWAY      -> OCCUPIED (returned) | AVAILABLE (away timeout, check-out)
 * </pre>
 */
public enum SeatStatus {
    AVAILABLE,
    RESERVED,
    OCCUPIED,
    AWAY;

    public boolean canTransitionTo(SeatStatus target) {
        return switch (this) {
            case AVAILABLE -> target == RESERVED || target == OCCUPIED;
            case RESERVED -> target == OCCUPIED || target == AVAILABLE;
            case OCCUPIED -> target == AWAY || target == AVAILABLE;
            case AWAY -> target == OCCUPIED || target == AVAILABLE;
        };
    }
}
