package com.raphati.lib.iso.type.mti;

import com.raphati.lib.iso.exception.IsoParserException;

/**
 * MTI position 3: message function.
 */
public enum FunctionType {
    REQUEST('0', "Request"),
    REQUEST_RESPONSE('1', "Request response"),
    ADVICE('2', "Advice"),
    ADVICE_RESPONSE('3', "Advice response"),
    NOTIFICATION('4', "Notification"),
    NOTIFICATION_ACKNOWLEDGEMENT('5', "Notification acknowledgement"),
    INSTRUCTION('6', "Instruction"),
    INSTRUCTION_ACKNOWLEDGEMENT('7', "Instruction acknowledgement"),
    RESERVED_8('8', "Reserved by ISO"),
    RESERVED_9('9', "Reserved by ISO");

    private final char digit;
    private final String description;

    FunctionType(char digit, String description) {
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
    public static FunctionType fromDigit(char digit) {
        for (FunctionType type : values()) {
            if (type.digit == digit) {
                return type;
            }
        }
        throw new IsoParserException("Unknown MTI function digit: " + digit);
    }
}
