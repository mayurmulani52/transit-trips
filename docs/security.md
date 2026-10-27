# Security Documentation

## Overview

This document describes the security controls applied to the Littlepay Trips application,
the threat model for the current CLI batch-processing context, and recommendations for
hardening before deployment as a network-accessible service.

---

## PII Handling

Card Primary Account Numbers (PANs) are sensitive personal data governed by PCI DSS and
applicable privacy regulations. The application handles them as follows.

### Masking

All PANs are masked to the last four digits before appearing in any log message, warning, or
exception text. Masking is performed exclusively by `Pans.mask(String pan)` in
`com.littlepay.trips.model.Pans`.

```
Raw PAN:     4111111111111111
Masked:      ************1111

Raw PAN:     5500005555555559
Masked:      ************5559
```

### Where masking is enforced

| Location | Class | Detail |
|---|---|---|
| `Tap.toString()` | `model.Tap` | Used in exception messages and debug output |
| `Trip.toString()` | `model.Trip` | Used in exception messages and debug output |
| Unmatched-tap warning | `TripsApplication` | Printed to stderr for operator review |
| TripMatcher debug logs | `matching.TripMatcher` | Every `log.debug` call uses `Pans.mask()` |

### What is never logged

- Full PAN at any log level
- Card expiry or CVV (not present in the input format)
- File contents (only counts and field names appear in log messages)

### Test data

All card numbers used in unit and integration tests are publicly documented test PANs from
payment network test suites. No real card numbers exist anywhere in the repository.

---

## Input Validation

Every field in the input CSV is validated before a `Tap` record is constructed.

| Field | Validation | On failure |
|---|---|---|
| Field count | Exactly 7 comma-separated fields per row | `CsvFormatException` with line number |
| `ID` | Parseable as `long` | `CsvFormatException` with line number |
| `DateTimeUTC` | Strict parse against `dd-MM-yyyy HH:mm:ss` | `CsvFormatException` with line number |
| `TapType` | `ON` or `OFF` (case-insensitive) | `CsvFormatException` with line number |
| `StopId` | Non-empty after whitespace trim | `CsvFormatException` with line number |
| `CompanyId` | Non-empty after whitespace trim | `CsvFormatException` with line number |
| `BusID` | Non-empty after whitespace trim | `CsvFormatException` with line number |
| `PAN` | Non-empty after whitespace trim | `CsvFormatException` with line number |

All validation errors halt processing immediately with exit code `1`. Partial output is
not written.

### Fare configuration

A stop that appears in the input but has no configured fare throws `UnknownFareException`
rather than silently charging zero or guessing an amount. This prevents incorrect charges
from reaching the output.

### Domain invariants

`Trip` enforces status-specific field constraints in its compact constructor:

- `INCOMPLETE` trips must have `null` for `finished`, `durationSecs` and `toStopId`.
- `COMPLETED` and `CANCELLED` trips must have non-null values for all three fields.

This means an invalid `Trip` state cannot be constructed — the compiler cannot produce one
and neither can a mistake in `TripMatcher`.

---

## Threat Model

### Scope

The application is a single-process, single-user CLI batch processor. It reads one file and
writes one file. There is no network interface, no database, no authentication layer and no
concurrent access.

### In-scope threats and mitigations

| Threat | Mitigation |
|---|---|
| PAN exposure in logs or error messages | `Pans.mask()` called at every log site; verified by code review and test for masked output |
| Malformed input causing incorrect charges | Strict field-by-field validation; parse errors halt processing before any output is written |
| Unknown stop silently charged $0 or wrong fare | `UnknownFareException` halts processing |
| Constructing a logically invalid Trip in code | Compact constructor enforces status invariants; tested in `TripCsvWriterTest` |
| Sensitive data leaking via exception stack traces | Only `e.getMessage()` is printed to stderr; full stack traces go to the structured log, not the terminal |

### Out-of-scope threats (operator's responsibility)

| Threat | Note |
|---|---|
| Unauthorised read/write access to CSV files | File system permissions and directory ACLs are the operator's responsibility |
| Tampering with the input file before processing | Integrity of the feed producing `taps.csv` is outside this application's boundary |
| Denial of service via an extremely large file | The in-memory assumption is documented; mitigation requires streaming per-card processing |
| Container escape | Standard container hardening (seccomp, AppArmor, read-only root FS) applies at the platform level |

---

## Supply Chain

| Dependency | Scope | Purpose |
|---|---|---|
| `org.slf4j:slf4j-api:2.0.16` | `implementation` | Logging facade |
| `ch.qos.logback:logback-classic:1.5.12` | `runtimeOnly` | Logging implementation |
| `org.junit:junit-bom:5.11.4` | `testImplementation` | Test dependency BOM (not in runtime JAR) |
| `org.junit.jupiter:junit-jupiter` | `testImplementation` | Test framework (not in runtime JAR) |

All dependency versions are pinned in `build.gradle.kts`. The runtime JAR bundles only the
first two. JUnit does not ship in any deployable artifact.

---

## Hardening Recommendations for Production

The following controls are not implemented in this exercise scope but would be appropriate
before deploying the application as part of a larger system or service:

### 1 — Input size limit

Reject input files above a configurable byte threshold to prevent out-of-memory conditions.
This is especially important if the input is received from an untrusted external system.

```java
long size = Files.size(path);
if (size > MAX_INPUT_BYTES) {
    throw new IOException("Input file exceeds maximum size: " + size + " bytes");
}
```

### 2 — Dependency vulnerability scanning

Add Trivy or OWASP Dependency-Check to the CI pipeline to alert on published CVEs in
pinned dependencies before they reach production.

```yaml
# Example addition to ci.yml
- name: Scan for vulnerabilities
  uses: aquasecurity/trivy-action@master
  with:
    scan-type: 'fs'
    scan-ref: '.'
```

### 3 — Static analysis (SAST)

Add SpotBugs or Semgrep to the Gradle build or CI pipeline for automated detection of
common Java security issues (null dereference, resource leaks, injection patterns).

### 4 — Read-only container filesystem

Run the Docker container with a read-only root filesystem and mount only the specific
input and output paths:

```bash
docker run --rm --read-only \
  -v "$(pwd)/taps.csv:/data/taps.csv:ro" \
  -v "$(pwd)/output:/data/output" \
  --tmpfs /tmp \
  littlepay-trips /data/taps.csv /data/output/trips.csv
```

### 5 — Structured audit log

In a multi-tenant deployment, emit a structured audit event for each processing run
recording the operator identity, input file name, tap count, trip count and outcome.
This supports compliance obligations and incident investigation.

### 6 — Secrets management for configuration

If the fare table is externalised to configuration (e.g. loaded from a file or API), any
credentials needed to access that configuration must come from environment variables or a
secrets manager — never from files committed to the repository.
