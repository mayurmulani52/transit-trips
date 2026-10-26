package com.littlepay.trips.matching;

import com.littlepay.trips.model.Tap;
import com.littlepay.trips.model.Trip;
import java.util.List;

/**
 * Output of matching taps into trips.
 *
 * @param trips         priced trips, ordered by start time
 * @param unmatchedTaps tap offs that could not be paired with a tap on; these are not charged
 */
public record MatchResult(List<Trip> trips, List<Tap> unmatchedTaps) {

    public MatchResult {
        trips = List.copyOf(trips);
        unmatchedTaps = List.copyOf(unmatchedTaps);
    }
}
