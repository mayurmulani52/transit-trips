# Pipeline and Build Documentation

## Project Layout

```
transit-trips/
├── src/
│   ├── main/
│   │   ├── java/com/transit/trips/
│   │   │   ├── TripsApplication.java        CLI entry point and orchestrator
│   │   │   ├── io/
│   │   │   │   ├── CsvFormat.java           Shared timestamp format and delimiter
│   │   │   │   ├── CsvFormatException.java  Structured parse error with line number
│   │   │   │   ├── TapCsvReader.java        CSV → List<Tap>
│   │   │   │   └── TripCsvWriter.java       List<Trip> → CSV
│   │   │   ├── matching/
│   │   │   │   ├── MatchResult.java         Immutable output: trips + unmatched taps
│   │   │   │   └── TripMatcher.java         Core matching and pricing logic
│   │   │   ├── model/
│   │   │   │   ├── Pans.java                PAN masking utility
│   │   │   │   ├── Tap.java                 Immutable tap record
│   │   │   │   ├── TapType.java             ON / OFF enum
│   │   │   │   ├── Trip.java                Immutable trip record with status invariants
│   │   │   │   └── TripStatus.java          COMPLETED / INCOMPLETE / CANCELLED enum
│   │   │   └── pricing/
│   │   │       ├── FareTable.java           Symmetric fare lookup with builder
│   │   │       └── UnknownFareException.java
│   │   └── resources/
│   │       └── logback.xml                  Production log configuration
│   └── test/
│       ├── java/com/transit/trips/
│       │   ├── TripsApplicationTest.java    End-to-end file comparison tests
│       │   ├── io/
│       │   │   ├── TapCsvReaderTest.java
│       │   │   └── TripCsvWriterTest.java
│       │   ├── matching/
│       │   │   ├── TapBuilder.java          Test helper — builds Tap fixtures
│       │   │   └── TripMatcherTest.java
│       │   └── pricing/
│       │       └── FareTableTest.java
│       └── resources/
│           ├── example/                     Example taps and expected trips
│           ├── scenarios/                   Extended multi-card scenario
│           └── logback-test.xml             Suppresses log output during tests
├── docs/                                    Project knowledge base (this folder)
├── .github/
│   └── workflows/
│       └── ci.yml                           GitHub Actions CI pipeline
├── Dockerfile                               Multi-stage container build
├── .dockerignore
├── build.gradle.kts                         Gradle build script (Kotlin DSL)
├── settings.gradle.kts
├── gradlew / gradlew.bat                    Gradle wrapper (no local Gradle needed)
├── taps.csv                                 Example input
└── trips.csv                                Generated output from the example input
```

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | Only requirement for local builds |
| Docker | Any recent version | Optional — for container builds only |
| Gradle | — | **Not required** — `./gradlew` downloads the correct version automatically |

---

## Local Build Commands

```bash
# Full build: compile + test + build fat JAR
./gradlew build

# Tests only
./gradlew test

# Run against taps.csv / trips.csv in the project root
./gradlew run

# Run against custom files
./gradlew run --args="path/to/input.csv path/to/output.csv"

# Build only the fat JAR (skips tests)
./gradlew jar

# Run the self-contained JAR directly (includes all runtime dependencies)
java -jar build/libs/transit-trips-1.0.0.jar taps.csv trips.csv
```

On Windows replace `./gradlew` with `gradlew.bat`.

### Exit codes

| Code | Meaning |
|---|---|
| `0` | Success |
| `1` | Processing error (file not found, bad CSV, unknown stop) |
| `2` | Bad arguments (more than two arguments provided) |

---

## Build Artifacts

| Artifact | Path | Description |
|---|---|---|
| Fat JAR | `build/libs/transit-trips-1.0.0.jar` | Self-contained — includes SLF4J and Logback |
| HTML test report | `build/reports/tests/test/index.html` | Human-readable test results |
| JUnit XML results | `build/test-results/test/` | Machine-readable — consumed by CI |

The fat JAR is built by the `jar` Gradle task configured in `build.gradle.kts`. It bundles all
`runtimeClasspath` dependencies alongside the application classes with `EXCLUDE` duplicate
strategy, so the JAR runs with a plain `java -jar` and no classpath argument.

---

## CI/CD Pipeline

The GitHub Actions workflow lives at `.github/workflows/ci.yml`.

### Triggers

| Event | Branches |
|---|---|
| Push | `main` |
| Pull request (opened, synchronised, reopened) | targeting `main` |

### Pipeline steps

```
Checkout
    │
    ▼
Set up JDK 21 (Eclipse Temurin, with Gradle dependency cache)
    │
    ▼
./gradlew build
    ├── compileJava
    ├── compileTestJava
    ├── test  (36 JUnit 5 tests)
    └── jar   (fat JAR)
    │
    ▼
Upload test report artifact
    └── build/reports/tests/test/  (retained 7 days, always uploaded even on failure)
```

### Permissions

The workflow requests only `contents: read` — the minimum required to check out the repository.
No write permissions are granted.

### Dependency caching

`actions/setup-java@v4` caches the Gradle dependency cache keyed on `build.gradle.kts`. A cache
hit avoids downloading dependencies on repeated runs.

---

## Docker

### Building the image

```bash
docker build -t transit-trips .
```

### Multi-stage Dockerfile

| Stage | Base image | Purpose |
|---|---|---|
| `build` | `eclipse-temurin:21-jdk-alpine` | Gradle wrapper + compile + `./gradlew jar` |
| runtime | `eclipse-temurin:21-jre-alpine` | Copy fat JAR only; no JDK, no source, no build tools |

**Layer caching**: the `build` stage copies `build.gradle.kts` and `gradle/` before copying
`src/`, so Gradle dependency downloads are cached as a Docker layer and only re-run when
`build.gradle.kts` changes.

### Running the container

```bash
# Mount input file read-only; write output to the current directory on the host
docker run --rm \
  -v "$(pwd)/taps.csv:/data/taps.csv:ro" \
  -v "$(pwd)":/data \
  transit-trips /data/taps.csv /data/trips.csv
```

The application runs as a non-root user (`trips`) inside the container — no root privileges
are needed or granted at runtime.

---

## Logging

Logging is provided by SLF4J with Logback. The default configuration at
`src/main/resources/logback.xml` sets:

- Root level: `WARN`
- `com.transit` package: `INFO`
- Output: `System.err`, pattern `HH:mm:ss.SSS LEVEL logger - message`

To enable `DEBUG`-level tap-matching diagnostics, replace the logger level in `logback.xml`:

```xml
<logger name="com.transit" level="DEBUG"/>
```

Or supply a custom configuration file at runtime:

```bash
java -Dlogback.configurationFile=/path/to/logback-debug.xml \
     -jar build/libs/transit-trips-1.0.0.jar taps.csv trips.csv
```

All log statements that reference card numbers use `Pans.mask(pan)` — raw PANs never appear
in log output regardless of the configured level.
