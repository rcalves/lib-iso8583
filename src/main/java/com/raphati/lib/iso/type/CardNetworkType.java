package com.raphati.lib.iso.type;

import com.raphati.lib.iso.exception.IsoParserException;

import java.util.Locale;

/**
 * Card networks supported by the parser.
 * <p>
 * Adding a network means: adding a constant here, writing its single and dual strategies,
 * providing its XML packager templates and JSON field definitions, and registering it in
 * {@link com.raphati.lib.iso.factory.MessageProcessingFactory} (and the defaults in
 * {@link com.raphati.lib.iso.config.IsoParserConfig}).
 */
public enum CardNetworkType {
    GENERIC,
    MASTERCARD;

    /**
     * Resolves a network by name (trimmed, case-insensitive).
     *
     * @throws IsoParserException if the value is null or unknown
     */
    public static CardNetworkType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IsoParserException("Invalid card network: " + value);
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IsoParserException("Invalid card network: " + value, e);
        }
    }
}
