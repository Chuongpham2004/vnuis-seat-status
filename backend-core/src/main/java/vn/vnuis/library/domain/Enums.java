package vn.vnuis.library.domain;

/** Small enums that mirror the CHECK constraints in V1__core_schema.sql. */
public final class Enums {

    private Enums() {
    }

    public enum Role {
        STUDENT,
        ADMIN
    }

    public enum SeatType {
        SINGLE,
        GROUP_TABLE
    }

    public enum BookingSource {
        APP_MAP,
        AI_CHATBOT,
        WALK_IN
    }

    public enum Connectivity {
        ONLINE,
        OFFLINE
    }
}
