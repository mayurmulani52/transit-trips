package com.transit.trips.io;

import java.time.format.DateTimeFormatter;

/**
 * Formatting conventions shared by the tap and trip CSV files.
 */
final class CsvFormat {

    static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    /** The example files separate fields with a comma followed by a space. */
    static final String OUTPUT_DELIMITER = ", ";

    private CsvFormat() {
    }
}
