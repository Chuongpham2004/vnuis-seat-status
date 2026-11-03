package vn.vnuis.library.credit;

import java.time.Instant;

/** Pure credit arithmetic, kept separate so it is trivially unit-testable. */
public final class CreditPolicy {

    private CreditPolicy() {
    }

    /** New balance after {@code delta}, clamped to [0, max]. */
    public static int apply(int current, int delta, int max) {
        return Math.max(0, Math.min(max, current + delta));
    }

    /** A penalty that leaves the balance below the booking threshold starts a ban. */
    public static boolean triggersBan(int newBalance, int delta, int minToBook) {
        return delta < 0 && newBalance < minToBook;
    }

    /**
     * Probation reset: once a ban has run out, a student still below the threshold is
     * put back at exactly {@code minToBook}. Without this a banned student could never
     * book again (credit is only earned by completing bookings).
     */
    public static boolean probationResetDue(int score, Instant bannedUntil, Instant now, int minToBook) {
        return bannedUntil != null && !bannedUntil.isAfter(now) && score < minToBook;
    }
}
