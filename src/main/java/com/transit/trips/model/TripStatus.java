package com.transit.trips.model;

/**
 * Outcome of matching a tap on with its tap off.
 */
public enum TripStatus {
    /** Tapped on and off at different stops; charged the fare between them. */
    COMPLETED,
    /** Tapped on but never tapped off; charged the maximum fare from the origin stop. */
    INCOMPLETE,
    /** Tapped on and off at the same stop; not charged. */
    CANCELLED
}
