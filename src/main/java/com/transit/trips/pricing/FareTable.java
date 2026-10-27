package com.transit.trips.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fares between pairs of stops. Fares are symmetric: travelling A to B costs the same as B to A.
 */
public final class FareTable {

    private static final int CURRENCY_SCALE = 2;
    public static final BigDecimal NO_CHARGE = BigDecimal.ZERO.setScale(CURRENCY_SCALE);

    private final Map<Route, BigDecimal> fares;

    private FareTable(Map<Route, BigDecimal> fares) {
        this.fares = Map.copyOf(fares);
    }

    /**
     * The fares defined in the exercise brief.
     */
    public static FareTable standard() {
        return builder()
                .fare("Stop1", "Stop2", "3.25")
                .fare("Stop2", "Stop3", "5.50")
                .fare("Stop1", "Stop3", "7.30")
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fare for travelling between two stops in either direction. Travelling to the same stop is free.
     *
     * @throws UnknownFareException if no fare is configured between the stops
     */
    public BigDecimal fareBetween(String fromStopId, String toStopId) {
        if (fromStopId.equals(toStopId)) {
            return NO_CHARGE;
        }
        BigDecimal fare = fares.get(Route.between(fromStopId, toStopId));
        if (fare == null) {
            throw new UnknownFareException("No fare configured between " + fromStopId + " and " + toStopId);
        }
        return fare;
    }

    /**
     * Highest fare from the given stop to any other stop. Used to charge incomplete trips.
     *
     * @throws UnknownFareException if the stop has no fares configured
     */
    public BigDecimal maxFareFrom(String stopId) {
        return fares.entrySet().stream()
                .filter(entry -> entry.getKey().touches(stopId))
                .map(Map.Entry::getValue)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new UnknownFareException("No fares configured from " + stopId));
    }

    /**
     * Unordered pair of stops. Normalised so that (A, B) and (B, A) are the same route.
     */
    private record Route(String first, String second) {

        static Route between(String a, String b) {
            return a.compareTo(b) <= 0 ? new Route(a, b) : new Route(b, a);
        }

        boolean touches(String stopId) {
            return first.equals(stopId) || second.equals(stopId);
        }
    }

    public static final class Builder {

        private final Map<Route, BigDecimal> fares = new HashMap<>();

        private Builder() {
        }

        public Builder fare(String stopA, String stopB, String amount) {
            return fare(stopA, stopB, new BigDecimal(amount));
        }

        public Builder fare(String stopA, String stopB, BigDecimal amount) {
            Objects.requireNonNull(stopA, "stopA");
            Objects.requireNonNull(stopB, "stopB");
            Objects.requireNonNull(amount, "amount");
            if (stopA.equals(stopB)) {
                throw new IllegalArgumentException("A fare must be between two different stops: " + stopA);
            }
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("Fare must not be negative: " + amount);
            }
            BigDecimal scaled = amount.setScale(CURRENCY_SCALE, RoundingMode.UNNECESSARY);
            if (fares.putIfAbsent(Route.between(stopA, stopB), scaled) != null) {
                throw new IllegalArgumentException("Duplicate fare between " + stopA + " and " + stopB);
            }
            return this;
        }

        public FareTable build() {
            return new FareTable(fares);
        }
    }
}
