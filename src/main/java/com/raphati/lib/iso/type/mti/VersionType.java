package com.raphati.lib.iso.type.mti;

import com.raphati.lib.iso.exception.IsoParserException;

/**
 * MTI position 1: ISO 8583 version.
 */
public enum VersionType {
    ISO_8583_1987('0', "ISO 8583:1987"),
    ISO_8583_1993('1', "ISO 8583:1993"),
    ISO_8583_2003('2', "ISO 8583:2003"),
    RESERVED_3('3', "Reserved by ISO"),
    RESERVED_4('4', "Reserved by ISO"),
    RESERVED_5('5', "Reserved by ISO"),
    RESERVED_6('6', "Reserved by ISO"),
    RESERVED_7('7', "Reserved by ISO"),
    NATIONAL_USE('8', "Reserved for national use"),
    PRIVATE_USE('9', "Reserved for private use");

    private final char digit;
    private final String description;

    VersionType(char digit, String description) {
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
    public static VersionType fromDigit(char digit) {
        for (VersionType type : values()) {
            if (type.digit == digit) {
                return type;
            }
        }
        throw new IsoParserException("Unknown MTI version digit: " + digit);
    }
}
