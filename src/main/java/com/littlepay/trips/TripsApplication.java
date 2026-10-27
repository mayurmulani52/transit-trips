package com.littlepay.trips;

import com.littlepay.trips.io.CsvFormatException;
import com.littlepay.trips.io.TapCsvReader;
import com.littlepay.trips.io.TripCsvWriter;
import com.littlepay.trips.matching.MatchResult;
import com.littlepay.trips.matching.TripMatcher;
import com.littlepay.trips.model.Pans;
import com.littlepay.trips.model.Tap;
import com.littlepay.trips.pricing.FareTable;
import com.littlepay.trips.pricing.UnknownFareException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Command line entry point. Converts a file of card taps into a file of priced trips.
 *
 * <pre>
 * Usage: trips [input-taps.csv] [output-trips.csv]
 * </pre>
 *
 * Defaults to {@code taps.csv} and {@code trips.csv} in the working directory.
 */
public final class TripsApplication {

    static final int EXIT_OK = 0;
    static final int EXIT_FAILURE = 1;
    static final int EXIT_USAGE = 2;

    private static final String DEFAULT_INPUT = "taps.csv";
    private static final String DEFAULT_OUTPUT = "trips.csv";

    private final TapCsvReader reader;
    private final TripMatcher matcher;
    private final TripCsvWriter writer;

    TripsApplication(TapCsvReader reader, TripMatcher matcher, TripCsvWriter writer) {
        this.reader = reader;
        this.matcher = matcher;
        this.writer = writer;
    }

    static TripsApplication withStandardFares() {
        return new TripsApplication(new TapCsvReader(), new TripMatcher(FareTable.standard()), new TripCsvWriter());
    }

    public static void main(String[] args) {
        int exitCode = withStandardFares().run(args, System.out, System.err);
        if (exitCode != EXIT_OK) {
            System.exit(exitCode);
        }
    }

    int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length > 2) {
            err.println("Usage: trips [input-taps.csv] [output-trips.csv]");
            return EXIT_USAGE;
        }
        Path input = Path.of(args.length > 0 ? args[0] : DEFAULT_INPUT);
        Path output = Path.of(args.length > 1 ? args[1] : DEFAULT_OUTPUT);

        try {
            List<Tap> taps = reader.read(input);
            MatchResult result = matcher.match(taps);
            writer.write(result.trips(), output);

            out.printf("Read %d taps from %s and wrote %d trips to %s%n",
                    taps.size(), input, result.trips().size(), output);
            reportUnmatched(result.unmatchedTaps(), err);
            return EXIT_OK;
        } catch (IOException e) {
            err.println("ERROR: could not process files: " + e);
        } catch (CsvFormatException e) {
            err.println("ERROR: invalid input in " + input + ": " + e.getMessage());
        } catch (UnknownFareException e) {
            err.println("ERROR: " + e.getMessage());
        }
        return EXIT_FAILURE;
    }

    private static void reportUnmatched(List<Tap> unmatched, PrintStream err) {
        for (Tap tap : unmatched) {
            err.printf("WARN: tap %d (%s at %s on %s, card %s) has no matching tap on and was not charged%n",
                    tap.id(), tap.type(), tap.stopId(), tap.busId(), Pans.mask(tap.pan()));
        }
    }
}
