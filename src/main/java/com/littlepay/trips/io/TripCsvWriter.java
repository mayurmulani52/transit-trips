package com.littlepay.trips.io;

import com.littlepay.trips.model.Trip;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Writes trips to CSV in the format shown in the exercise brief.
 *
 * <p>Values that do not apply to a trip (for example the tap off details of an incomplete trip) are
 * written as empty fields so every row has the same number of columns.
 */
public final class TripCsvWriter {

    static final String HEADER = String.join(CsvFormat.OUTPUT_DELIMITER,
            "Started", "Finished", "DurationSecs", "FromStopId", "ToStopId",
            "ChargeAmount", "CompanyId", "BusID", "PAN", "Status");

    private static final String LINE_SEPARATOR = "\n";

    public void write(List<Trip> trips, Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            write(trips, writer);
        }
    }

    public void write(List<Trip> trips, Writer writer) throws IOException {
        writer.write(HEADER);
        writer.write(LINE_SEPARATOR);
        for (Trip trip : trips) {
            writer.write(toRow(trip));
            writer.write(LINE_SEPARATOR);
        }
        writer.flush();
    }

    static String toRow(Trip trip) {
        return String.join(CsvFormat.OUTPUT_DELIMITER,
                timestamp(trip.started()),
                timestamp(trip.finished()),
                trip.durationSecs() == null ? "" : trip.durationSecs().toString(),
                trip.fromStopId(),
                trip.toStopId() == null ? "" : trip.toStopId(),
                currency(trip.chargeAmount()),
                trip.companyId(),
                trip.busId(),
                trip.pan(),
                trip.status().name());
    }

    private static String timestamp(LocalDateTime value) {
        return value == null ? "" : CsvFormat.TIMESTAMP.format(value);
    }

    private static String currency(BigDecimal amount) {
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
