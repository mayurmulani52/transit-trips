package com.transit.trips.io;

/**
 * Raised when a line in an input file cannot be parsed.
 */
public class CsvFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int lineNumber;

    public CsvFormatException(int lineNumber, String message, Throwable cause) {
        super("Line " + lineNumber + ": " + message, cause);
        this.lineNumber = lineNumber;
    }

    public CsvFormatException(int lineNumber, String message) {
        this(lineNumber, message, null);
    }

    public int lineNumber() {
        return lineNumber;
    }
}
