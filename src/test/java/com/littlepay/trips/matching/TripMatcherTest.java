package com.littlepay.trips.matching;

import static com.littlepay.trips.matching.TapBuilder.MASTERCARD_TEST_PAN;
import static com.littlepay.trips.matching.TapBuilder.VISA_TEST_PAN;
import static com.littlepay.trips.matching.TapBuilder.off;
import static com.littlepay.trips.matching.TapBuilder.on;
import static com.littlepay.trips.matching.TapBuilder.tap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.littlepay.trips.model.Tap;
import com.littlepay.trips.model.TapType;
import com.littlepay.trips.model.Trip;
import com.littlepay.trips.model.TripStatus;
import com.littlepay.trips.pricing.FareTable;
import com.littlepay.trips.pricing.UnknownFareException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TripMatcherTest {

    private final TripMatcher matcher = new TripMatcher(FareTable.standard());

    @Test
    @DisplayName("tap on and tap off at different stops is a completed trip")
    void completedTrip() {
        Tap on = on("Stop1", 0);
        Tap off = off("Stop2", 5);

        Trip trip = single(matcher.match(List.of(on, off)));

        assertEquals(TripStatus.COMPLETED, trip.status());
        assertEquals(new BigDecimal("3.25"), trip.chargeAmount());
        assertEquals("Stop1", trip.fromStopId());
        assertEquals("Stop2", trip.toStopId());
        assertEquals(on.dateTimeUtc(), trip.started());
        assertEquals(off.dateTimeUtc(), trip.finished());
        assertEquals(Long.valueOf(300), trip.durationSecs());
        assertEquals("Company1", trip.companyId());
        assertEquals("Bus37", trip.busId());
        assertEquals(VISA_TEST_PAN, trip.pan());
    }

    @Test
    @DisplayName("completed trips are priced the same in either direction")
    void completedTripInReverseDirection() {
        Trip trip = single(matcher.match(List.of(on("Stop3", 0), off("Stop1", 20))));

        assertEquals(TripStatus.COMPLETED, trip.status());
        assertEquals(new BigDecimal("7.30"), trip.chargeAmount());
    }

    @Test
    @DisplayName("tap on and tap off at the same stop is a cancelled trip with no charge")
    void cancelledTrip() {
        Trip trip = single(matcher.match(List.of(on("Stop1", 0), off("Stop1", 2))));

        assertEquals(TripStatus.CANCELLED, trip.status());
        assertEquals(new BigDecimal("0.00"), trip.chargeAmount());
        assertEquals("Stop1", trip.toStopId());
        assertEquals(Long.valueOf(120), trip.durationSecs());
    }

    @Test
    @DisplayName("tap on with no tap off is incomplete and charged the maximum fare from that stop")
    void incompleteTripWithNoTapOff() {
        Trip trip = single(matcher.match(List.of(on("Stop2", 0))));

        assertEquals(TripStatus.INCOMPLETE, trip.status());
        assertEquals(new BigDecimal("5.50"), trip.chargeAmount());
        assertEquals("Stop2", trip.fromStopId());
        assertNull(trip.toStopId());
        assertNull(trip.finished());
        assertNull(trip.durationSecs());
    }

    @Test
    @DisplayName("a second tap on closes the previous tap on as incomplete")
    void tapOnFollowedByTapOn() {
        MatchResult result = matcher.match(List.of(on("Stop1", 0), on("Stop2", 60), off("Stop3", 70)));

        assertEquals(2, result.trips().size());
        Trip first = result.trips().get(0);
        Trip second = result.trips().get(1);

        assertEquals(TripStatus.INCOMPLETE, first.status());
        assertEquals("Stop1", first.fromStopId());
        assertEquals(new BigDecimal("7.30"), first.chargeAmount());

        assertEquals(TripStatus.COMPLETED, second.status());
        assertEquals("Stop2", second.fromStopId());
        assertEquals("Stop3", second.toStopId());
        assertEquals(new BigDecimal("5.50"), second.chargeAmount());
    }

    @Test
    @DisplayName("a tap off with no prior tap on is reported as unmatched and not charged")
    void orphanTapOff() {
        Tap orphan = off("Stop2", 0);

        MatchResult result = matcher.match(List.of(orphan));

        assertTrue(result.trips().isEmpty());
        assertEquals(List.of(orphan), result.unmatchedTaps());
    }

    @Test
    @DisplayName("a tap off on a different bus does not complete the open tap on")
    void tapOffOnDifferentBus() {
        Tap on = tap(TapType.ON, "Stop1", 0, "Bus37", VISA_TEST_PAN);
        Tap offOtherBus = tap(TapType.OFF, "Stop2", 10, "Bus99", VISA_TEST_PAN);

        MatchResult result = matcher.match(List.of(on, offOtherBus));

        Trip trip = single(result);
        assertEquals(TripStatus.INCOMPLETE, trip.status());
        assertEquals(new BigDecimal("7.30"), trip.chargeAmount());
        assertEquals(List.of(offOtherBus), result.unmatchedTaps());
    }

    @Test
    @DisplayName("taps are matched in time order regardless of their order in the input")
    void outOfOrderInput() {
        Tap on = on("Stop1", 0);
        Tap off = off("Stop2", 5);

        Trip trip = single(matcher.match(List.of(off, on)));

        assertEquals(TripStatus.COMPLETED, trip.status());
        assertEquals(new BigDecimal("3.25"), trip.chargeAmount());
    }

    @Test
    @DisplayName("interleaved taps from different cards are matched independently")
    void multipleCards() {
        List<Tap> taps = List.of(
                tap(TapType.ON, "Stop1", 0, "Bus37", VISA_TEST_PAN),
                tap(TapType.ON, "Stop2", 1, "Bus37", MASTERCARD_TEST_PAN),
                tap(TapType.OFF, "Stop3", 10, "Bus37", VISA_TEST_PAN),
                tap(TapType.OFF, "Stop2", 11, "Bus37", MASTERCARD_TEST_PAN));

        MatchResult result = matcher.match(taps);

        assertEquals(2, result.trips().size());
        Trip visa = result.trips().get(0);
        Trip mastercard = result.trips().get(1);

        assertEquals(VISA_TEST_PAN, visa.pan());
        assertEquals(TripStatus.COMPLETED, visa.status());
        assertEquals(new BigDecimal("7.30"), visa.chargeAmount());

        assertEquals(MASTERCARD_TEST_PAN, mastercard.pan());
        assertEquals(TripStatus.CANCELLED, mastercard.status());
        assertEquals(new BigDecimal("0.00"), mastercard.chargeAmount());
    }

    @Test
    @DisplayName("trips are returned in start time order across cards")
    void tripsOrderedByStartTime() {
        List<Tap> taps = List.of(
                tap(TapType.ON, "Stop1", 30, "Bus37", VISA_TEST_PAN),
                tap(TapType.ON, "Stop2", 10, "Bus37", MASTERCARD_TEST_PAN),
                tap(TapType.ON, "Stop3", 20, "Bus36", "4444333322221111"));

        List<Trip> trips = matcher.match(taps).trips();

        assertEquals(List.of("Stop2", "Stop3", "Stop1"),
                trips.stream().map(Trip::fromStopId).toList());
    }

    @Test
    @DisplayName("a tap on at a stop with no configured fares fails rather than charging zero")
    void unknownStopFails() {
        assertThrows(UnknownFareException.class, () -> matcher.match(List.of(on("Stop9", 0))));
    }

    @Test
    @DisplayName("multiple tap offs with no prior tap on are all reported as unmatched")
    void multipleOrphanTapOffs() {
        Tap first = off("Stop1", 0);
        Tap second = off("Stop2", 5);

        MatchResult result = matcher.match(List.of(first, second));

        assertTrue(result.trips().isEmpty());
        assertEquals(List.of(first, second), result.unmatchedTaps());
    }

    @Test
    @DisplayName("a card can complete more than one trip in sequence")
    void twoCompletedTripsOnSameCard() {
        List<Tap> taps = List.of(
                on("Stop1", 0),
                off("Stop2", 5),
                on("Stop2", 60),
                off("Stop3", 70));

        List<Trip> trips = matcher.match(taps).trips();

        assertEquals(2, trips.size());
        assertEquals(TripStatus.COMPLETED, trips.get(0).status());
        assertEquals(new BigDecimal("3.25"), trips.get(0).chargeAmount());
        assertEquals(TripStatus.COMPLETED, trips.get(1).status());
        assertEquals(new BigDecimal("5.50"), trips.get(1).chargeAmount());
    }

    @Test
    @DisplayName("no taps gives no trips")
    void emptyInput() {
        MatchResult result = matcher.match(List.of());

        assertTrue(result.trips().isEmpty());
        assertTrue(result.unmatchedTaps().isEmpty());
    }

    private static Trip single(MatchResult result) {
        assertEquals(1, result.trips().size(), "expected exactly one trip");
        return result.trips().get(0);
    }
}
