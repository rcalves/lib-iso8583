package com.raphati.lib.iso.type;

import com.raphati.lib.iso.exception.InvalidTransactionTypeException;
import com.raphati.lib.iso.exception.IsoParserException;
import com.raphati.lib.iso.type.mti.ClassType;
import com.raphati.lib.iso.type.mti.FunctionType;
import com.raphati.lib.iso.type.mti.OriginType;
import com.raphati.lib.iso.type.mti.VersionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MtiTypesTest {

    @ParameterizedTest
    @CsvSource({"0,ISO_8583_1987", "1,ISO_8583_1993", "2,ISO_8583_2003", "3,RESERVED_3", "4,RESERVED_4",
            "5,RESERVED_5", "6,RESERVED_6", "7,RESERVED_7", "8,NATIONAL_USE", "9,PRIVATE_USE"})
    void versionFromDigit(char digit, VersionType expected) {
        assertThat(VersionType.fromDigit(digit)).isEqualTo(expected);
        assertThat(expected.digit()).isEqualTo(digit);
        assertThat(expected.description()).isNotBlank();
    }

    @ParameterizedTest
    @CsvSource({"0,RESERVED_0", "1,AUTHORIZATION", "2,FINANCIAL", "3,FILE_ACTION", "4,REVERSAL_CHARGEBACK",
            "5,RECONCILIATION", "6,ADMINISTRATIVE", "7,FEE_COLLECTION", "8,NETWORK_MANAGEMENT", "9,RESERVED"})
    void classFromDigit(char digit, ClassType expected) {
        assertThat(ClassType.fromDigit(digit)).isEqualTo(expected);
        assertThat(expected.digit()).isEqualTo(digit);
    }

    @ParameterizedTest
    @CsvSource({"0,REQUEST", "1,REQUEST_RESPONSE", "2,ADVICE", "3,ADVICE_RESPONSE", "4,NOTIFICATION",
            "5,NOTIFICATION_ACKNOWLEDGEMENT", "6,INSTRUCTION", "7,INSTRUCTION_ACKNOWLEDGEMENT",
            "8,RESERVED_8", "9,RESERVED_9"})
    void functionFromDigit(char digit, FunctionType expected) {
        assertThat(FunctionType.fromDigit(digit)).isEqualTo(expected);
        assertThat(expected.digit()).isEqualTo(digit);
    }

    @ParameterizedTest
    @CsvSource({"0,ACQUIRER", "1,ACQUIRER_REPEAT", "2,ISSUER", "3,ISSUER_REPEAT", "4,OTHER", "5,OTHER_REPEAT",
            "6,RESERVED_6", "7,RESERVED_7", "8,RESERVED_8", "9,RESERVED_9"})
    void originFromDigit(char digit, OriginType expected) {
        assertThat(OriginType.fromDigit(digit)).isEqualTo(expected);
        assertThat(expected.digit()).isEqualTo(digit);
    }

    @Test
    void everyDecimalDigitMapsInAllFourPositions() {
        for (char digit = '0'; digit <= '9'; digit++) {
            assertThat(VersionType.fromDigit(digit).digit()).isEqualTo(digit);
            assertThat(ClassType.fromDigit(digit).digit()).isEqualTo(digit);
            assertThat(FunctionType.fromDigit(digit).digit()).isEqualTo(digit);
            assertThat(OriginType.fromDigit(digit).digit()).isEqualTo(digit);
        }
    }

    @Test
    void nonDigitsThrow() {
        assertThatThrownBy(() -> VersionType.fromDigit('A')).isInstanceOf(IsoParserException.class);
        assertThatThrownBy(() -> ClassType.fromDigit('X')).isInstanceOf(IsoParserException.class);
        assertThatThrownBy(() -> FunctionType.fromDigit('A')).isInstanceOf(IsoParserException.class);
        assertThatThrownBy(() -> OriginType.fromDigit(' ')).isInstanceOf(IsoParserException.class);
    }

    @ParameterizedTest
    @CsvSource({"SINGLE,SINGLE", "dual,DUAL", "' Single ',SINGLE", "DuAl,DUAL"})
    void messageTypeFromString(String value, MessageType expected) {
        assertThat(MessageType.fromString(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"TRIPLE", "  "})
    void messageTypeInvalid(String value) {
        assertThatThrownBy(() -> MessageType.fromString(value))
                .isInstanceOf(InvalidTransactionTypeException.class)
                .hasMessage("Invalid transaction type: " + value);
    }

    @ParameterizedTest
    @CsvSource({"GENERIC,GENERIC", "mastercard,MASTERCARD", "' MasterCard ',MASTERCARD"})
    void cardNetworkFromString(String value, CardNetworkType expected) {
        assertThat(CardNetworkType.fromString(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"AMEX_UNKNOWN"})
    void cardNetworkInvalid(String value) {
        assertThatThrownBy(() -> CardNetworkType.fromString(value)).isInstanceOf(IsoParserException.class);
    }
}
