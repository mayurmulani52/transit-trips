package com.littlepay.trips.io;

import com.littlepay.trips.model.Tap;
import com.littlepay.trips.model.TapType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads taps from a CSV file with the columns:
 * {@code ID, DateTimeUTC, TapType, StopId, CompanyId, BusID, PAN}.
 *
 * <p>The first non-blank line is treated as a header. Whitespace around fields is ignored and blank
 * lines are skipped. Fields are not quoted, so a plain split on commas is enough for this format.
 */
public final class TapCsvReader {

    private static final Logger log = LoggerFactory.getLogger(TapCsvReader.class);

    private static final int EXPECTED_FIELDS = 7;
    private static final char BYTE_ORDER_MARK = '﻿';

    public List<Tap> read(Path path) throws IOException {
        log.debug("Reading taps from {}", path);
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return read(reader);
        }
    }

    public List<Tap> read(Reader source) throws IOException {
        BufferedReader reader = source instanceof BufferedReader buffered ? buffered : new BufferedReader(source);
        List<Tap> taps = new ArrayList<>();
        boolean headerSkipped = false;
        int lineNumber = 0;

        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (lineNumber == 1 && !line.isEmpty() && line.charAt(0) == BYTE_ORDER_MARK) {
                line = line.substring(1);
            }
            if (line.isBlank()) {
                continue;
            }
            if (!headerSkipped) {
                headerSkipped = true;
                continue;
            }
            taps.add(parse(line, lineNumber));
        }
        log.debug("Parsed {} taps", taps.size());
        return taps;
    }

    private Tap parse(String line, int lineNumber) {
        String[] fields = line.split(",", -1);
        if (fields.length != EXPECTED_FIELDS) {
            throw new CsvFormatException(lineNumber,
                    "expected " + EXPECTED_FIELDS + " fields but found " + fields.length);
        }
        try {
            return new Tap(
                    Long.parseLong(fields[0].trim()),
                    LocalDateTime.parse(fields[1].trim(), CsvFormat.TIMESTAMP),
                    TapType.valueOf(fields[2].trim().toUpperCase(Locale.ROOT)),
                    requireValue(fields[3], "StopId", lineNumber),
                    requireValue(fields[4], "CompanyId", lineNumber),
                    requireValue(fields[5], "BusID", lineNumber),
                    requireValue(fields[6], "PAN", lineNumber));
        } catch (CsvFormatException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CsvFormatException(lineNumber, e.getMessage(), e);
        }
    }

    private static String requireValue(String raw, String column, int lineNumber) {
        String value = raw.trim();
        if (value.isEmpty()) {
            throw new CsvFormatException(lineNumber, column + " is empty");
        }
        return value;
    }
}
