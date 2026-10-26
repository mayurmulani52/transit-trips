package com.littlepay.trips.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A single card tap recorded by a reader on a bus.
 *
 * @param id          unique id of the tap event
 * @param dateTimeUtc when the tap happened, in UTC
 * @param type        whether the passenger tapped on or off
 * @param stopId      stop where the tap happened
 * @param companyId   transit company operating the bus
 * @param busId       bus the reader is installed on
 * @param pan         primary account number of the card used
 */
public record Tap(
        long id,
        LocalDateTime dateTimeUtc,
        TapType type,
        String stopId,
        String companyId,
        String busId,
        String pan) {

    public Tap {
        Objects.requireNonNull(dateTimeUtc, "dateTimeUtc");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(stopId, "stopId");
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(busId, "busId");
        Objects.requireNonNull(pan, "pan");
    }

    /**
     * Two taps are on the same vehicle when they share the operating company and bus.
     */
    public boolean isOnSameVehicleAs(Tap other) {
        return companyId.equals(other.companyId) && busId.equals(other.busId);
    }

    @Override
    public String toString() {
        // Keep the full PAN out of logs and exception messages.
        return "Tap[id=%d, %s, %s at %s, %s/%s, pan=%s]"
                .formatted(id, dateTimeUtc, type, stopId, companyId, busId, Pans.mask(pan));
    }
}
