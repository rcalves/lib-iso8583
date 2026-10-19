package com.raphati.lib.iso.exception;

/**
 * Raised when a message type (single/dual) cannot be resolved.
 */
public class InvalidTransactionTypeException extends IsoParserException {

    public InvalidTransactionTypeException(String message) {
        super(message);
    }

    public InvalidTransactionTypeException(String message, Throwable cause) {
        super(message, cause);
    }
}
