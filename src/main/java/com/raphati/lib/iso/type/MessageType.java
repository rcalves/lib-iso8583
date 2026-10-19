package com.raphati.lib.iso.type;

import com.raphati.lib.iso.exception.InvalidTransactionTypeException;

import java.util.Locale;

/**
 * Message processing mode.
 * <ul>
 *     <li>{@link #SINGLE}: a single financial message carries authorization and clearing.</li>
 *     <li>{@link #DUAL}: authorization and clearing travel in separate messages.</li>
 * </ul>
 */
public enum MessageType {
    SINGLE,
    DUAL;

    /**
     * Resolves a message type by name (trimmed, case-insensitive).
     *
     * @throws InvalidTransactionTypeException if the value is null, blank or unknown
     */
    public static MessageType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidTransactionTypeException("Invalid transaction type: " + value);
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidTransactionTypeException("Invalid transaction type: " + value, e);
        }
    }
}
