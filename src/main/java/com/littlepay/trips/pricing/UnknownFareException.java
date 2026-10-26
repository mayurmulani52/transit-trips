package com.littlepay.trips.pricing;

/**
 * Raised when a trip touches a stop or route that has no configured fare.
 */
public class UnknownFareException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnknownFareException(String message) {
        super(message);
    }
}
