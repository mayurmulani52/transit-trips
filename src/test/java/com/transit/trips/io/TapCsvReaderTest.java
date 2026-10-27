package com.transit.trips.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.transit.trips.model.Tap;
import com.transit.trips.model.TapType;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TapCsvReaderTest {

    private static final String HEADER = "ID, DateTimeUTC, TapType, StopId, CompanyId, BusID, PAN\n";

    private final TapCsvReader reader = new TapCsvReader();

    @Test
    @DisplayName("parses taps in the format of the example file")
    void parsesExampleFormat() throws IOException {
        String csv = HEADER
                + "1, 22-01-2023 13:00:00, ON, Stop1, Company1, Bus37, 5500005555555559\n"
                + "2, 22-01-2023 13:05:00, OFF, Stop2, Company1, Bus37, 5500005555555559\n";

        List<Tap> taps = reader.read(new StringReader(csv));

        assertEquals(2, taps.size());
        assertEquals(new Tap(1, LocalDateTime.of(2023, 1, 22, 13, 0, 0), TapType.ON,
                "Stop1", "Company1", "Bus37", "5500005555555559"), taps.get(0));
        assertEquals(new Tap(2, LocalDateTime.of(2023, 1, 22, 13, 5, 0), TapType.OFF,
                "Stop2", "Company1", "Bus37", "5500005555555559"), taps.get(1));
    }

    @Test
    @DisplayName("tolerates extra whitespace, blank lines, Windows line endings and a byte order mark")
    void toleratesFormattingNoise() throws IOException {
        String csv = "﻿" + HEADER.replace("\n", "\r\n")
                + "\r\n"
                + "  1 ,22-01-2023 13:00:00,on,Stop1 ,Company1,Bus37,  4111111111111111  \r\n"
                + "\r\n";

        List<Tap> taps = reader.read(new StringReader(csv));

        assertEquals(1, taps.size());
        Tap tap = taps.get(0);
        assertEquals(1L, tap.id());
        assertEquals(TapType.ON, tap.type());
        assertEquals("Stop1", tap.stopId());
        assertEquals("4111111111111111", tap.pan());
    }

    @Test
    @DisplayName("a file with only a header has no taps")
    void headerOnly() throws IOException {
        assertTrue(reader.read(new StringReader(HEADER)).isEmpty());
    }

    @Test
    @DisplayName("reports the line number of a row with the wrong number of fields")
    void wrongFieldCount() {
        String csv = HEADER + "1, 22-01-2023 13:00:00, ON, Stop1, Company1, Bus37\n";

        CsvFormatException error = assertThrows(CsvFormatException.class, () -> reader.read(new StringReader(csv)));

        assertEquals(2, error.lineNumber());
    }

    @Test
    @DisplayName("rejects unparseable values with the line number")
    void invalidValues() {
        String badDate = HEADER + "1, 2023-01-22 13:00:00, ON, Stop1, Company1, Bus37, 4111111111111111\n";
        String badType = HEADER + "1, 22-01-2023 13:00:00, IN, Stop1, Company1, Bus37, 4111111111111111\n";
        String badId = HEADER + "x, 22-01-2023 13:00:00, ON, Stop1, Company1, Bus37, 4111111111111111\n";
        String blankPan = HEADER + "1, 22-01-2023 13:00:00, ON, Stop1, Company1, Bus37,  \n";

        for (String csv : List.of(badDate, badType, badId, blankPan)) {
            CsvFormatException error =
                    assertThrows(CsvFormatException.class, () -> reader.read(new StringReader(csv)));
            assertEquals(2, error.lineNumber());
        }
    }
}
