# Business Requirements

## Context

This application implements a simplified transit trip-pricing pipeline: it reads raw tap events
from buses and produces a priced trip record for each journey.

---

## Actors

| Actor | Description |
|---|---|
| Passenger | Holds a credit card (identified by PAN) and boards/leaves buses |
| Transit company | Operates one or more buses across a set of stops |
| Operator | Runs the application to process a batch of taps |

---

## Functional Requirements

### FR-1 — Trip types

Every tap-on event eventually resolves into exactly one of three trip outcomes:

| Status | Trigger | Charge |
|---|---|---|
| **COMPLETED** | Tap on and tap off at **different** stops on the same bus | Fare between the two stops |
| **CANCELLED** | Tap on and tap off at the **same** stop on the same bus | $0.00 |
| **INCOMPLETE** | Tap on with no matching tap off on the same bus | Maximum fare from the origin stop to any reachable stop |

### FR-2 — Fare table

Fares are symmetric — travel in either direction between two stops costs the same.

| Stop A | Stop B | Fare |
|---|---|---|
| Stop 1 | Stop 2 | $3.25 |
| Stop 2 | Stop 3 | $5.50 |
| Stop 1 | Stop 3 | $7.30 |

Maximum fares by origin stop (used for incomplete trips):

| Origin stop | Maximum fare |
|---|---|
| Stop 1 | $7.30 (to Stop 3) |
| Stop 2 | $5.50 (to Stop 3) |
| Stop 3 | $7.30 (to Stop 1) |

### FR-3 — Matching rules

1. Matching is per card (PAN). A card has at most one open tap-on at a time.
2. Taps are matched in chronological order by `DateTimeUTC`; `ID` is the tie-breaker.
3. A **tap-on followed by another tap-on** (same card) closes the first as `INCOMPLETE`.
4. A **tap-off must be on the same company and bus** as the open tap-on. A tap-off on a
   different bus leaves the open tap-on as `INCOMPLETE` and the tap-off unmatched.
5. A **tap-off with no open tap-on** is not charged and not written to the output. It is
   reported as a warning on stderr with the card number masked.

### FR-4 — Input format

CSV file with one header row, then one tap per row:

```
ID, DateTimeUTC, TapType, StopId, CompanyId, BusID, PAN
1, 22-01-2023 13:00:00, ON, Stop1, Company1, Bus37, 5500005555555559
```

| Field | Type | Notes |
|---|---|---|
| `ID` | Integer | Unique tap identifier; used as tie-breaker |
| `DateTimeUTC` | `dd-MM-yyyy HH:mm:ss` | Timestamps are in UTC |
| `TapType` | `ON` or `OFF` | Case-insensitive |
| `StopId` | String | Must match a stop in the fare table |
| `CompanyId` | String | Transit company identifier |
| `BusID` | String | Bus identifier |
| `PAN` | String | Primary Account Number — sensitive PII |

### FR-5 — Output format

CSV file with one header row, then one trip per row, sorted by `Started` then `PAN`:

```
Started, Finished, DurationSecs, FromStopId, ToStopId, ChargeAmount, CompanyId, BusID, PAN, Status
22-01-2023 13:00:00, 22-01-2023 13:05:00, 300, Stop1, Stop2, $3.25, Company1, Bus37, 5500005555555559, COMPLETED
```

- `Finished`, `DurationSecs` and `ToStopId` are **empty** for `INCOMPLETE` trips.
- `ChargeAmount` is always formatted as `$0.00` (two decimal places).

---

## Non-Functional Requirements

### NFR-1 — Correctness
- Monetary amounts use `BigDecimal` at exactly two decimal places. Floating-point arithmetic is
  not used anywhere in the pricing path.
- A stop with no configured fare causes an error rather than silently charging zero or the wrong amount.
- Output ordering is fully deterministic (sorted by started, then PAN, then fromStopId).

### NFR-2 — Security and PII
- Card numbers (PANs) must never appear in log output or warning messages in full. They are
  masked to the last four digits at every log call site.
- All card numbers in test data are publicly documented test PANs — no real card numbers are
  committed to the repository.

### NFR-3 — Portability
- Runs on any machine with JDK 21 via the Gradle wrapper or the self-contained JAR.
- A Docker image is provided for containerised environments.

### NFR-4 — Observability
- Structured logging (INFO/WARN/ERROR/DEBUG) via SLF4J with Logback.
- Process exit codes signal success (0), processing error (1) or bad arguments (2) to the
  calling process or pipeline.

### NFR-5 — Assumptions
- The whole input file fits in memory. For very large files, external sorting or per-card
  streaming would be required.
- There is no time limit between tap-on and tap-off. Any tap-off on the same bus completes the
  trip regardless of elapsed time.
- Company and bus identity of a trip are taken from the tap-on.
