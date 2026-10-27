# Workflow Documentation

## End-to-End Data Flow

```
taps.csv (input)
     │
     ▼
┌─────────────┐
│ TapCsvReader│  Parses each CSV row into an immutable Tap record.
│             │  Validates field count, types and non-empty values.
│             │  Strips UTF-8 BOM, trims whitespace, skips blank lines.
│             │  Reports the offending line number on any parse error.
└──────┬──────┘
       │  List<Tap>
       ▼
┌─────────────┐
│ TripMatcher │  Groups taps by PAN (card). Replays each card's taps in
│             │  chronological order through a single "open tap-on" slot.
│             │  Produces a list of priced trips and a list of unmatched
│             │  tap-offs.
│      │      │
│      │      │──► FareTable  Resolves the fare between two stops (symmetric
│             │               route lookup) and the maximum fare from a stop
│             │               (used to price incomplete trips).
└──────┬──────┘
       │  MatchResult (trips + unmatchedTaps)
       ▼
┌──────────────┐
│TripCsvWriter │  Formats each Trip as a CSV row with the exact column order,
│              │  timestamp format and currency notation from the brief.
│              │  Empty fields are written for tap-off columns on INCOMPLETE trips.
└──────┬───────┘
       │
       ▼
trips.csv (output)
```

Unmatched tap-offs are reported on **stderr** with masked card numbers and do not appear in
`trips.csv`.

---

## Card State Machine

Each card's taps are processed independently. The state for one card at any point is either
**no pending tap-on** or **one open tap-on**. There is no queue — at most one open tap-on
exists at a time.

```
             ┌────────────────────────────────────────────────────────────┐
             │                                                            │
      tap ON │                                              end of input  │
             ▼                                                            │
      ┌─────────────┐   tap ON                        ┌──────────────────┴─┐
      │  NO PENDING │ ──────────────────────────────► │     PENDING ON     │
      └─────────────┘                                 └──────────────────┬─┘
             ▲                                                            │
             │ tap OFF (same bus, same stop)  → CANCELLED ($0.00)        │
             │ tap OFF (same bus, diff stop)  → COMPLETED (route fare)   │
             └────────────────────────────────────────────────────────────┘

Exceptional transitions from PENDING ON:

  tap ON received while pending:
    ├── close existing tap-on as INCOMPLETE (max fare from its stop)
    └── open new tap-on as the new PENDING

  tap OFF on a different bus while pending:
    ├── close existing tap-on as INCOMPLETE (max fare from its stop)
    └── tap-off is unmatched (reported, not charged)

  end of input while pending:
    └── close as INCOMPLETE (max fare from its stop)

Exceptional transitions from NO PENDING:

  tap OFF received with no pending:
    └── unmatched (reported on stderr, not charged)
```

---

## Trip Outcome Decision Table

| Card state | Event | Outcome |
|---|---|---|
| No pending | Tap ON | Open a pending tap-on |
| Pending | Tap OFF — same bus, same stop | **CANCELLED**, $0.00 |
| Pending | Tap OFF — same bus, different stop | **COMPLETED**, route fare |
| Pending | Tap OFF — different bus | Pending → **INCOMPLETE** (max fare); tap-off → unmatched |
| Pending | Tap ON | Pending → **INCOMPLETE** (max fare); new tap-on → pending |
| Pending | End of input | **INCOMPLETE**, max fare from origin stop |
| No pending | Tap OFF | Unmatched, not charged, warning on stderr |

---

## Sorting and Ordering

**Input taps** are sorted chronologically per card before matching:

```
PRIMARY:   DateTimeUTC  (ascending)
SECONDARY: ID           (ascending — tie-breaker for simultaneous events)
```

**Output trips** are sorted before writing:

```
PRIMARY:   started      (ascending)
SECONDARY: pan          (ascending)
TERTIARY:  fromStopId   (ascending — ensures fully deterministic output)
```

**Unmatched tap-offs** in `MatchResult` are sorted chronologically (same comparator as input).

---

## Error Handling

| Condition | Behaviour |
|---|---|
| Input file not found or unreadable | Exit code **1**, message on stderr |
| CSV row has wrong number of fields | Exit code **1**, line number in message |
| Unparseable timestamp or tap type | Exit code **1**, line number in message |
| Empty required field (StopId, PAN, etc.) | Exit code **1**, field name and line number |
| Stop not in the fare table | Exit code **1**, stop name in message; no guessed charge |
| Too many command-line arguments | Exit code **2**, usage hint on stderr |
| Unmatched tap-off | Warning on stderr (masked PAN); processing continues normally |

Exit code `0` means all taps were read, matched and written successfully.

---

## Sequence for the Brief's Example Input

```
ID  Time           Type  Stop   Card …5559 state   Card …1111 state
──  ─────────────  ────  ─────  ─────────────────  ─────────────────
3   22-01 09:20    ON    Stop3  —                  PENDING(Stop3)
1   22-01 13:00    ON    Stop1  PENDING(Stop1)      —
2   22-01 13:05    OFF   Stop2  → COMPLETED $3.25  —
4   23-01 08:00    ON    Stop1  —                  PENDING(Stop1)
5   23-01 08:02    OFF   Stop1  —                  → CANCELLED $0.00
6   24-01 16:30    OFF   Stop2  unmatched           —

End of input:
  Card …1111 still has PENDING(Stop3) → INCOMPLETE $7.30
```

Output (sorted by started):

```
22-01-2023 09:20:00  Stop3  INCOMPLETE  $7.30
22-01-2023 13:00:00  Stop1→Stop2  COMPLETED  $3.25
23-01-2023 08:00:00  Stop1→Stop1  CANCELLED  $0.00
```
