package com.raphati.lib.iso.type.mti;

import com.raphati.lib.iso.exception.IsoParserException;

/**
 * MTI position 2: message class.
 */
public enum ClassType {
    RESERVED_0('0', "Reserved by ISO"),
    AUTHORIZATION('1', "Authorization message"),
    FINANCIAL('2', "Financial message"),
    FILE_ACTION('3', "File action message"),
    REVERSAL_CHARGEBACK('4', "Reversal or chargeback message"),
    RECONCILIATION('5', "Reconciliation message"),
    ADMINISTRATIVE('6', "Administrative message"),
    FEE_COLLECTION('7', "Fee collection message"),
    NETWORK_MANAGEMENT('8', "Network management message"),
    RESERVED('9', "Reserved by ISO");

    private final char digit;
    private final String description;

    ClassType(char digit, String description) {
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
    public static ClassType fromDigit(char digit) {
        for (ClassType type : values()) {
            if (type.digit == digit) {
                return type;
            }
        }
        throw new IsoParserException("Unknown MTI class digit: " + digit);
    }
}
