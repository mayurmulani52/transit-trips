package com.littlepay.trips.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.littlepay.trips.model.Trip;
import com.littlepay.trips.model.TripStatus;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TripCsvWriterTest {

    private static final LocalDateTime START = LocalDateTime.of(2023, 1, 22, 13, 0, 0);

    @Test
    @DisplayName("writes a completed trip exactly as in the brief's example output")
    void completedTripMatchesBriefExample() {
        Trip trip = new Trip(START, START.plusMinutes(5), 300L, "Stop1", "Stop2", new BigDecimal("3.25"),
                "Company1", "Bus37", "5500005555555559", TripStatus.COMPLETED);

        assertEquals("22-01-2023 13:00:00, 22-01-2023 13:05:00, 300, Stop1, Stop2, $3.25, Company1, Bus37, "
                + "5500005555555559, COMPLETED", TripCsvWriter.toRow(trip));
    }

    @Test
    @DisplayName("leaves tap off fields empty for incomplete trips")
    void incompleteTripHasEmptyTapOffFields() {
        Trip trip = new Trip(START, null, null, "Stop2", null, new BigDecimal("5.50"),
                "Company1", "Bus37", "4111111111111111", TripStatus.INCOMPLETE);

        assertEquals("22-01-2023 13:00:00, , , Stop2, , $5.50, Company1, Bus37, 4111111111111111, INCOMPLETE",
                TripCsvWriter.toRow(trip));
    }

    @Test
    @DisplayName("formats charges to two decimal places")
    void formatsCharges() {
        Trip trip = new Trip(START, START.plusMinutes(2), 120L, "Stop1", "Stop1", BigDecimal.ZERO,
                "Company1", "Bus37", "4111111111111111", TripStatus.CANCELLED);

        assertEquals("22-01-2023 13:00:00, 22-01-2023 13:02:00, 120, Stop1, Stop1, $0.00, Company1, Bus37, "
                + "4111111111111111, CANCELLED", TripCsvWriter.toRow(trip));
    }

    @Test
    @DisplayName("writes the header even when there are no trips")
    void headerOnlyForNoTrips() throws IOException {
        StringWriter out = new StringWriter();

        new TripCsvWriter().write(List.of(), out);

        assertEquals("Started, Finished, DurationSecs, FromStopId, ToStopId, ChargeAmount, CompanyId, BusID, "
                + "PAN, Status\n", out.toString());
    }
}
