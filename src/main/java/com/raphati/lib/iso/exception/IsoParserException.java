package com.raphati.lib.iso.exception;

/**
 * Base unchecked exception for every failure raised by the ISO parser library.
 */
public class IsoParserException extends RuntimeException {

    public IsoParserException(String message) {
        super(message);
    }

    public IsoParserException(String message, Throwable cause) {
        super(message, cause);
    }
}
