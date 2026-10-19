package com.raphati.lib.iso.type.mti;

import com.raphati.lib.iso.exception.IsoParserException;

/**
 * MTI position 4: message origin.
 */
public enum OriginType {
    ACQUIRER('0', "Acquirer"),
    ACQUIRER_REPEAT('1', "Acquirer repeat"),
    ISSUER('2', "Issuer"),
    ISSUER_REPEAT('3', "Issuer repeat"),
    OTHER('4', "Other"),
    OTHER_REPEAT('5', "Other repeat"),
    RESERVED_6('6', "Reserved by ISO"),
    RESERVED_7('7', "Reserved by ISO"),
    RESERVED_8('8', "Reserved by ISO"),
    RESERVED_9('9', "Reserved by ISO");

    private final char digit;
    private final String description;

    OriginType(char digit, String description) {
        this.digit = digit;
        this.description = description;
    }

    public char digit() {
        return digit;
    }

    public String description() {
        return description;
    }

    /**
     * Every decimal digit maps to a constant; digits ISO leaves unassigned map to a reserved one.
     *
     * @throws IsoParserException if {@code digit} is not a decimal digit
     */
    public static OriginType fromDigit(char digit) {
        for (OriginType type : values()) {
            if (type.digit == digit) {
                return type;
            }
        }
        throw new IsoParserException("Unknown MTI origin digit: " + digit);
    }
}
