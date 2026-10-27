# Littlepay Trips

Turns a CSV file of card taps (ON / OFF) into a CSV file of priced trips. Each trip is
`COMPLETED`, `INCOMPLETE` or `CANCELLED`.

## Requirements

- JDK 21
- No Gradle install needed; the Gradle wrapper (`./gradlew`) downloads Gradle on first run.

## Running

```bash
# Build, run the tests and generate trips.csv from taps.csv in the project root
./gradlew build
./gradlew run

# Or choose the input and output files
./gradlew run --args="path/to/taps.csv path/to/trips.csv"

# Or run the built jar directly (no runtime dependencies)
java -jar build/libs/littlepay-trips-1.0.0.jar taps.csv trips.csv
```

Arguments are optional and default to `taps.csv` and `trips.csv` in the working directory.
On Windows use `gradlew.bat` instead of `./gradlew`.

The exit code is `0` on success, `1` if the input cannot be read, parsed or priced, and `2` for bad
arguments. Tap offs that cannot be matched to a tap on are listed on stderr with the card number masked.

## Example output

[`taps.csv`](taps.csv) is the example input from the brief.
[`trips.csv`](trips.csv) is the output generated from it:

```
Started, Finished, DurationSecs, FromStopId, ToStopId, ChargeAmount, CompanyId, BusID, PAN, Status
22-01-2023 09:20:00, , , Stop3, , $7.30, Company1, Bus36, 4111111111111111, INCOMPLETE
22-01-2023 13:00:00, 22-01-2023 13:05:00, 300, Stop1, Stop2, $3.25, Company1, Bus37, 5500005555555559, COMPLETED
23-01-2023 08:00:00, 23-01-2023 08:02:00, 120, Stop1, Stop1, $0.00, Company1, Bus37, 4111111111111111, CANCELLED
```

Tap 6 (an OFF at Stop2 two days after card 5559's last trip ended) has no open tap on to close.
It is reported as unmatched and not charged.

## Design

```
TripsApplication          CLI: wires reader -> matcher -> writer, maps errors to exit codes
├── io.TapCsvReader       CSV -> Tap
├── matching.TripMatcher  Tap list -> MatchResult (trips + unmatched taps)
│   └── pricing.FareTable symmetric fares between stops, max fare from a stop
└── io.TripCsvWriter      Trip -> CSV
```

- **Matching** groups taps by PAN and replays each card's taps in time order with a single "open tap
  on" slot. This handles input in any order and interleaved cards in O(n log n).
- **Pricing** lives in `FareTable`, separate from matching, so fares can be configured without touching
  the matching rules. Routes are unordered stop pairs, so a fare applies in both directions. Amounts are
  `BigDecimal` at 2 decimal places.
- **Input/output** is kept at the edges. The matcher works only on domain records, which makes the
  business rules easy to unit test without files.
- **Card numbers** are masked in `toString` and in warnings so they do not end up in logs.

## Assumptions

1. **Matching is per card (PAN).** A card has at most one open tap on at a time.
2. **Taps are ordered by `DateTimeUTC`**, with `ID` as a tie-breaker, not by file order.
3. **A tap on followed by another tap on** means the passenger forgot to tap off. The first tap on
   becomes an `INCOMPLETE` trip.
4. **A tap off must be on the same company and bus as the open tap on** to complete it. If it is on a
   different bus, the tap on is treated as `INCOMPLETE` and the tap off as unmatched. A passenger cannot
   tap off a bus they did not board.
5. **A tap off with no open tap on** is not a trip. It is not written to `trips.csv` and is not charged.
   It is reported on stderr so it can be investigated.
6. **There is no time limit between tap on and tap off.** A tap off on the same bus completes the trip
   however long afterwards it occurs. In practice an operator would set a maximum trip duration.
7. **Incomplete trips** have empty `Finished`, `DurationSecs` and `ToStopId` fields, and are charged the
   maximum fare from their origin stop.
8. **Cancelled trips** are charged `$0.00` and show the same stop as both `FromStopId` and `ToStopId`.
9. **Company and bus on a trip** are taken from the tap on.
10. **Output is sorted by trip start time**, then by PAN, and uses the `, ` delimiter, `dd-MM-yyyy HH:mm:ss`
    timestamps and `$0.00` amounts shown in the brief.
11. **Stops with no configured fare are an error.** Processing stops rather than charging a guessed amount.
12. **The input is well formed** as the brief allows. The reader still trims whitespace, skips blank
    lines, accepts CRLF and a UTF-8 BOM, and reports the line number of any malformed row. Fields are not
    quoted, so a simple comma split is used rather than a CSV library.
13. **The whole file fits in memory.** For much larger inputs the taps could be sorted externally or
    streamed per card.

## Tests

```bash
./gradlew test
```

- `FareTableTest`: fares per route, symmetry, maximum fare per stop, validation.
- `TripMatcherTest`: completed, reverse-direction, cancelled and incomplete trips, consecutive tap ons,
  orphan tap offs, a tap off on a different bus, out-of-order input, interleaved cards and output order.
- `TapCsvReaderTest` / `TripCsvWriterTest`: parsing, formatting, and error reporting with line numbers.
- `TripsApplicationTest`: end-to-end runs that compare generated files byte for byte with expected
  files in `src/test/resources`: the brief's example and a wider scenario file covering several cards,
  buses and companies.

All card numbers used are public test numbers, not real cards.
