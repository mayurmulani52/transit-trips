package com.transit.trips.matching;

import com.transit.trips.model.Tap;
import com.transit.trips.model.TapType;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test helper for building taps with sensible defaults. PANs are well-known test card numbers.
 */
final class TapBuilder {

    static final String VISA_TEST_PAN = "4111111111111111";
    static final String MASTERCARD_TEST_PAN = "5500005555555559";

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2023, 1, 22, 9, 0);

    private static final AtomicLong nextId = new AtomicLong(1);

    private TapBuilder() {
    }

    static Tap on(String stopId, int minutesAfterBase) {
        return tap(TapType.ON, stopId, minutesAfterBase, "Bus37", VISA_TEST_PAN);
    }

    static Tap off(String stopId, int minutesAfterBase) {
        return tap(TapType.OFF, stopId, minutesAfterBase, "Bus37", VISA_TEST_PAN);
    }

    static Tap tap(TapType type, String stopId, int minutesAfterBase, String busId, String pan) {
        return new Tap(nextId.getAndIncrement(), BASE_TIME.plusMinutes(minutesAfterBase), type, stopId, "Company1", busId, pan);
    }
}
