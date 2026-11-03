package vn.vnuis.library.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ReservationStatus {
    PENDING,
    ACTIVE,
    COMPLETED,
    EXPIRED_NO_SHOW,
    AUTO_RELEASED_AWAY,
    CANCELLED;

    /** Statuses covered by the partial unique indexes (one open reservation per seat / per user). */
    public static final Set<ReservationStatus> OPEN = EnumSet.of(PENDING, ACTIVE);
}
