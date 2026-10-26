package com.littlepay.trips.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A priced trip built from a tap on and, where present, its matching tap off.
 *
 * <p>For {@link TripStatus#INCOMPLETE} trips there is no tap off, so {@code finished},
 * {@code durationSecs} and {@code toStopId} are {@code null}.
 */
public record Trip(
        LocalDateTime started,
        LocalDateTime finished,
        Long durationSecs,
        String fromStopId,
        String toStopId,
        BigDecimal chargeAmount,
        String companyId,
        String busId,
        String pan,
        TripStatus status) {

    public Trip {
        Objects.requireNonNull(started, "started");
        Objects.requireNonNull(fromStopId, "fromStopId");
        Objects.requireNonNull(chargeAmount, "chargeAmount");
        Objects.requireNonNull(companyId, "companyId");
        Objects.requireNonNull(busId, "busId");
        Objects.requireNonNull(pan, "pan");
        Objects.requireNonNull(status, "status");
        if (status == TripStatus.INCOMPLETE) {
            if (finished != null || durationSecs != null || toStopId != null) {
                throw new IllegalArgumentException(
                        "INCOMPLETE trip must not have finished, durationSecs or toStopId set");
            }
        } else {
            Objects.requireNonNull(finished, "finished");
            Objects.requireNonNull(durationSecs, "durationSecs");
            Objects.requireNonNull(toStopId, "toStopId");
        }
    }

    @Override
    public String toString() {
        return "Trip[%s %s -> %s, %s, %s, pan=%s]"
                .formatted(status, fromStopId, toStopId, chargeAmount, started, Pans.mask(pan));
    }
}
