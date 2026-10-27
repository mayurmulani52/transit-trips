package com.littlepay.trips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end tests that run the application on input files and compare the output file byte for byte.
 */
class TripsApplicationTest {

    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    @Test
    @DisplayName("produces the expected trips for the example input in the brief")
    void exampleFromBrief(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("trips.csv");

        int exitCode = run(resource("example/taps.csv").toString(), output.toString());

        assertEquals(TripsApplication.EXIT_OK, exitCode);
        assertEquals(Files.readString(resource("example/expected-trips.csv")), Files.readString(output));
        assertTrue(stdout().contains("Read 6 taps"));
        assertTrue(stdout().contains("wrote 3 trips"));
        assertTrue(stderr().contains("tap 6"), "the orphan tap off should be reported");
    }

    @Test
    @DisplayName("handles mixed scenarios across several cards, buses and companies")
    void mixedScenarios(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("trips.csv");

        int exitCode = run(resource("scenarios/taps.csv").toString(), output.toString());

        assertEquals(TripsApplication.EXIT_OK, exitCode);
        assertEquals(Files.readString(resource("scenarios/expected-trips.csv")), Files.readString(output));
        assertTrue(stderr().contains("tap 7"));
        assertTrue(stderr().contains("tap 8"));
    }

    @Test
    @DisplayName("masks card numbers in warnings")
    void masksPansInWarnings(@TempDir Path tempDir) throws Exception {
        run(resource("example/taps.csv").toString(), tempDir.resolve("trips.csv").toString());

        assertFalse(stderr().contains("5500005555555559"));
        assertTrue(stderr().contains("************5559"));
    }

    @Test
    @DisplayName("fails with a clear message when the input file does not exist")
    void missingInputFile(@TempDir Path tempDir) {
        int exitCode = run(tempDir.resolve("missing.csv").toString(), tempDir.resolve("trips.csv").toString());

        assertEquals(TripsApplication.EXIT_FAILURE, exitCode);
        assertTrue(stderr().startsWith("ERROR"));
        assertFalse(Files.exists(tempDir.resolve("trips.csv")));
    }

    @Test
    @DisplayName("fails with the line number when the input is malformed")
    void malformedInput(@TempDir Path tempDir) throws IOException {
        Path input = tempDir.resolve("taps.csv");
        Files.writeString(input, "ID, DateTimeUTC, TapType, StopId, CompanyId, BusID, PAN\n1, not-a-date\n");

        int exitCode = run(input.toString(), tempDir.resolve("trips.csv").toString());

        assertEquals(TripsApplication.EXIT_FAILURE, exitCode);
        assertTrue(stderr().contains("Line 2"));
    }

    @Test
    @DisplayName("rejects too many arguments")
    void tooManyArguments() {
        assertEquals(TripsApplication.EXIT_USAGE, run("a.csv", "b.csv", "c.csv"));
        assertTrue(stderr().contains("Usage"));
    }

    private int run(String... args) {
        return TripsApplication.withStandardFares().run(args,
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    private String stdout() {
        return stdout.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return stderr.toString(StandardCharsets.UTF_8);
    }

    private static Path resource(String name) throws URISyntaxException {
        return Path.of(TripsApplicationTest.class.getClassLoader().getResource(name).toURI());
    }
}
