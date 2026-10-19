package com.raphati.lib.iso.exception;

/**
 * Raised when encryption or decryption fails. Messages never contain plaintext or key material.
 */
public class CryptoException extends IsoParserException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
