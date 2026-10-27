package com.littlepay.trips.matching;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import com.littlepay.trips.model.Pans;
import com.littlepay.trips.model.Tap;
import com.littlepay.trips.model.Trip;
import com.littlepay.trips.model.TripStatus;
import com.littlepay.trips.pricing.FareTable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pairs tap ons with tap offs for each card and prices the resulting trips.
 *
 * <p>Taps are grouped by PAN and replayed in time order:
 * <ul>
 *   <li>ON followed by OFF on the same bus: COMPLETED, or CANCELLED if both taps are at the same stop.</li>
 *   <li>ON followed by another ON, or with no later tap: the first ON is an INCOMPLETE trip.</li>
 *   <li>ON followed by OFF on a different bus: the ON is INCOMPLETE and the OFF is unmatched.</li>
 *   <li>OFF with no preceding ON: unmatched, reported but not charged.</li>
 * </ul>
 */
public final class TripMatcher {

    private static final Logger log = LoggerFactory.getLogger(TripMatcher.class);

    private static final Comparator<Tap> CHRONOLOGICAL =
            Comparator.comparing(Tap::dateTimeUtc).thenComparingLong(Tap::id);

    private static final Comparator<Trip> OUTPUT_ORDER =
            Comparator.comparing(Trip::started).thenComparing(Trip::pan).thenComparing(Trip::fromStopId);

    private final FareTable fareTable;

    public TripMatcher(FareTable fareTable) {
        this.fareTable = Objects.requireNonNull(fareTable, "fareTable");
    }

    public MatchResult match(Collection<Tap> taps) {
        Map<String, List<Tap>> tapsByCard = taps.stream()
                .collect(groupingBy(Tap::pan, LinkedHashMap::new, toList()));

        List<Trip> trips = new ArrayList<>();
        List<Tap> unmatched = new ArrayList<>();
        for (List<Tap> cardTaps : tapsByCard.values()) {
            matchCardTaps(cardTaps, trips, unmatched);
        }

        trips.sort(OUTPUT_ORDER);
        unmatched.sort(CHRONOLOGICAL);
        return new MatchResult(trips, unmatched);
    }

    private void matchCardTaps(List<Tap> cardTaps, List<Trip> trips, List<Tap> unmatched) {
        Tap pendingOn = null;
        String maskedPan = cardTaps.isEmpty() ? "unknown" : Pans.mask(cardTaps.get(0).pan());
        log.debug("Matching {} taps for card {}", cardTaps.size(), maskedPan);

        for (Tap tap : cardTaps.stream().sorted(CHRONOLOGICAL).toList()) {
            switch (tap.type()) {
                case ON -> {
                    if (pendingOn != null) {
                        log.debug("Card {}: tap on at {} closed as INCOMPLETE (new tap on received)", maskedPan, pendingOn.stopId());
                        trips.add(incompleteTrip(pendingOn));
                    }
                    pendingOn = tap;
                }
                case OFF -> {
                    if (pendingOn == null) {
                        log.debug("Card {}: tap off at {} has no matching tap on — skipped", maskedPan, tap.stopId());
                        unmatched.add(tap);
                    } else if (!pendingOn.isOnSameVehicleAs(tap)) {
                        log.debug("Card {}: tap off on different bus — tap on at {} closed as INCOMPLETE", maskedPan, pendingOn.stopId());
                        trips.add(incompleteTrip(pendingOn));
                        unmatched.add(tap);
                        pendingOn = null;
                    } else {
                        trips.add(finishedTrip(pendingOn, tap));
                        pendingOn = null;
                    }
                }
            }
        }

        if (pendingOn != null) {
            log.debug("Card {}: tap on at {} closed as INCOMPLETE (end of input)", maskedPan, pendingOn.stopId());
            trips.add(incompleteTrip(pendingOn));
        }
    }

    private Trip finishedTrip(Tap on, Tap off) {
        boolean cancelled = on.stopId().equals(off.stopId());
        return new Trip(
                on.dateTimeUtc(),
                off.dateTimeUtc(),
                Duration.between(on.dateTimeUtc(), off.dateTimeUtc()).toSeconds(),
                on.stopId(),
                off.stopId(),
                cancelled ? FareTable.NO_CHARGE : fareTable.fareBetween(on.stopId(), off.stopId()),
                on.companyId(),
                on.busId(),
                on.pan(),
                cancelled ? TripStatus.CANCELLED : TripStatus.COMPLETED);
    }

    private Trip incompleteTrip(Tap on) {
        return new Trip(
                on.dateTimeUtc(),
                null,
                null,
                on.stopId(),
                null,
                fareTable.maxFareFrom(on.stopId()),
                on.companyId(),
                on.busId(),
                on.pan(),
                TripStatus.INCOMPLETE);
    }
}
