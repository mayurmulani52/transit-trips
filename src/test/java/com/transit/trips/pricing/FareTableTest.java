package com.transit.trips.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FareTableTest {

    private final FareTable fares = FareTable.standard();

    @Test
    @DisplayName("charges the configured fare for each route")
    void chargesConfiguredFares() {
        assertEquals(new BigDecimal("3.25"), fares.fareBetween("Stop1", "Stop2"));
        assertEquals(new BigDecimal("5.50"), fares.fareBetween("Stop2", "Stop3"));
        assertEquals(new BigDecimal("7.30"), fares.fareBetween("Stop1", "Stop3"));
    }

    @Test
    @DisplayName("fares are the same in both directions")
    void faresAreSymmetric() {
        assertEquals(fares.fareBetween("Stop1", "Stop2"), fares.fareBetween("Stop2", "Stop1"));
        assertEquals(fares.fareBetween("Stop2", "Stop3"), fares.fareBetween("Stop3", "Stop2"));
        assertEquals(fares.fareBetween("Stop1", "Stop3"), fares.fareBetween("Stop3", "Stop1"));
    }

    @Test
    @DisplayName("travel to the same stop is free")
    void sameStopIsFree() {
        assertEquals(new BigDecimal("0.00"), fares.fareBetween("Stop2", "Stop2"));
    }

    @Test
    @DisplayName("maximum fare from a stop is the most expensive route touching it")
    void maxFareFromEachStop() {
        assertEquals(new BigDecimal("7.30"), fares.maxFareFrom("Stop1"));
        assertEquals(new BigDecimal("5.50"), fares.maxFareFrom("Stop2"));
        assertEquals(new BigDecimal("7.30"), fares.maxFareFrom("Stop3"));
    }

    @Test
    @DisplayName("unknown stops are rejected rather than charged a guessed amount")
    void unknownStopsAreRejected() {
        assertThrows(UnknownFareException.class, () -> fares.fareBetween("Stop1", "Stop9"));
        assertThrows(UnknownFareException.class, () -> fares.maxFareFrom("Stop9"));
    }

    @Test
    @DisplayName("builder rejects invalid fares")
    void builderValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> FareTable.builder().fare("Stop1", "Stop1", "1.00"));
        assertThrows(IllegalArgumentException.class,
                () -> FareTable.builder().fare("Stop1", "Stop2", "-1.00"));
        assertThrows(IllegalArgumentException.class,
                () -> FareTable.builder().fare("Stop1", "Stop2", "1.00").fare("Stop2", "Stop1", "2.00"));
        assertThrows(ArithmeticException.class,
                () -> FareTable.builder().fare("Stop1", "Stop2", "1.005"));
    }

    @Test
    @DisplayName("custom fare tables support any number of stops")
    void customFareTable() {
        FareTable custom = FareTable.builder()
                .fare("A", "B", "1.00")
                .fare("B", "C", "2")
                .fare("C", "D", "4.50")
                .build();

        assertEquals(new BigDecimal("2.00"), custom.fareBetween("C", "B"));
        assertEquals(new BigDecimal("4.50"), custom.maxFareFrom("C"));
        assertEquals(new BigDecimal("1.00"), custom.maxFareFrom("A"));
    }
}
