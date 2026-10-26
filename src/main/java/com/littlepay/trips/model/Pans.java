package com.littlepay.trips.model;

/**
 * Helpers for handling card numbers safely.
 */
public final class Pans {

    private static final int VISIBLE_DIGITS = 4;

    private Pans() {
    }

    /**
     * Masks all but the last four digits of a PAN, for use in logs and diagnostics.
     */
    public static String mask(String pan) {
        if (pan == null || pan.length() <= VISIBLE_DIGITS) {
            return "****";
        }
        int hidden = pan.length() - VISIBLE_DIGITS;
        return "*".repeat(hidden) + pan.substring(hidden);
    }
}
