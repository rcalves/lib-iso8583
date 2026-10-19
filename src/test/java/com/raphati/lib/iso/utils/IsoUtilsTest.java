package com.raphati.lib.iso.utils;

import com.raphati.lib.iso.TestMessages;
import com.raphati.lib.iso.config.IsoParserConfig;
import com.raphati.lib.iso.exception.IsoParserException;
import com.raphati.lib.iso.loader.IsoDefinitionLoader;
import com.raphati.lib.iso.model.BitmapModel;
import com.raphati.lib.iso.model.DEModel;
import com.raphati.lib.iso.model.FieldDefinitionModel;
import com.raphati.lib.iso.model.MtiModel;
import com.raphati.lib.iso.type.CardNetworkType;
import com.raphati.lib.iso.type.LengthType;
import com.raphati.lib.iso.type.Sensitivity;
import com.raphati.lib.iso.type.SubelementFormat;
import com.raphati.lib.iso.type.mti.ClassType;
import com.raphati.lib.iso.type.mti.FunctionType;
import com.raphati.lib.iso.type.mti.OriginType;
import com.raphati.lib.iso.type.mti.VersionType;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.GenericPackager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IsoUtilsTest {

    private static final IsoDefinitionLoader LOADER = new IsoDefinitionLoader();

    private static FieldDefinitionModel tlvDef(String id, int offset) {
        return new FieldDefinitionModel(id, "test", 0, 999, "ans", LengthType.LLLVAR,
                SubelementFormat.TAG_LENGTH_VALUE, 2, 2, offset,
                List.of(new FieldDefinitionModel("01", "First", 0, 0, "ans", null, null, 0, 0, 0, null)));
    }

    private static FieldDefinitionModel berDef() {
        return new FieldDefinitionModel("55", "ICC", 0, 999, "ans", LengthType.LLLVAR,
                SubelementFormat.BER_TLV, 0, 0, 0,
                List.of(new FieldDefinitionModel("9F02", "Amount Authorised", 0, 0, "b", null, null, 0, 0, 0, null)));
    }

    @Test
    void hexRoundTrip() {
        byte[] bytes = {0x00, 0x7F, (byte) 0x80, (byte) 0xFF, 0x1A};
        assertThat(IsoUtils.bytesToHex(bytes)).isEqualTo("007F80FF1A");
        assertThat(IsoUtils.hexToBytes("007f80ff1a")).isEqualTo(bytes);
        assertThat(IsoUtils.hexToBytes(" 00 7F\n80FF 1A ")).isEqualTo(bytes);
    }

    @Test
    void invalidHexThrows() {
        assertThatThrownBy(() -> IsoUtils.hexToBytes(null)).isInstanceOf(IsoParserException.class);
        assertThatThrownBy(() -> IsoUtils.hexToBytes("ABC")).isInstanceOf(IsoParserException.class);
        assertThatThrownBy(() -> IsoUtils.hexToBytes("ZZ")).isInstanceOf(IsoParserException.class);
    }

    @Test
    void parsesMti() {
        MtiModel mti = IsoUtils.parseMti("0100");
        assertThat(mti.value()).isEqualTo("0100");
        assertThat(mti.version()).isEqualTo(VersionType.ISO_8583_1987);
        assertThat(mti.messageClass()).isEqualTo(ClassType.AUTHORIZATION);
        assertThat(mti.function()).isEqualTo(FunctionType.REQUEST);
        assertThat(mti.origin()).isEqualTo(OriginType.ACQUIRER);
    }

    @Test
    void invalidMtiThrows() {
        assertThatThrownBy(() -> IsoUtils.parseMti("01A0")).isInstanceOf(IsoParserException.class);
        assertThatThrownBy(() -> IsoUtils.parseMti("010")).isInstanceOf(IsoParserException.class);
        assertThatThrownBy(() -> IsoUtils.parseMti("01000")).isInstanceOf(IsoParserException.class);
        assertThatThrownBy(() -> IsoUtils.parseMti(null)).isInstanceOf(IsoParserException.class);
    }

    @Test
    void reservedMtiDigitsDecodeInsteadOfThrowing() {
        MtiModel mti = IsoUtils.parseMti("3006");

        assertThat(mti.version()).isEqualTo(VersionType.RESERVED_3);
        assertThat(mti.messageClass()).isEqualTo(ClassType.RESERVED_0);
        assertThat(mti.function()).isEqualTo(FunctionType.REQUEST);
        assertThat(mti.origin()).isEqualTo(OriginType.RESERVED_6);
    }

    @Test
    void parsesTagLengthValue() {
        Map<String, DEModel> subs = IsoUtils.parseTagLengthValue(TestMessages.GENERIC_DE48, tlvDef("48", 0));

        assertThat(subs.keySet()).containsExactly("01", "02");
        assertThat(subs.get("01").rawValue()).isEqualTo("HELLO");
        assertThat(subs.get("01").name()).isEqualTo("First");
        assertThat(subs.get("02").rawValue()).isEqualTo("ABC");
        assertThat(subs.get("02").name()).isEqualTo("Unknown");
    }

    @Test
    void parsesTagLengthValueWithTcc() {
        Map<String, DEModel> subs = IsoUtils.parseTagLengthValue(TestMessages.MASTERCARD_DE48, tlvDef("48", 1));

        assertThat(subs.keySet()).containsExactly("TCC", "42", "92");
        assertThat(subs.get("TCC").rawValue()).isEqualTo("R");
        assertThat(subs.get("42").rawValue()).isEqualTo("0102105");
        assertThat(subs.get("92").rawValue()).isEqualTo("123");
    }

    @Test
    void undeclaredOffsetPrefixIsUnknownAndFullyMasked() {
        FieldDefinitionModel wideOffset = tlvDef("48", 6);

        Map<String, DEModel> subs = IsoUtils.parseTagLengthValue("SECRET0105HELLO", wideOffset);

        assertThat(subs.keySet()).containsExactly("TCC", "01");
        assertThat(subs.get("TCC").rawValue()).isEqualTo("SECRET");
        assertThat(subs.get("TCC").name()).isEqualTo("Unknown");
        assertThat(subs.get("TCC").sensitivity()).isEqualTo(Sensitivity.FULL);
        assertThat(subs.get("TCC").toString()).doesNotContain("SECRET");
    }

    @Test
    void declaredOffsetPrefixTakesItsNameAndSensitivityFromTheDefinition() {
        FieldDefinitionModel def = new FieldDefinitionModel("48", "test", 0, 999, "ans", LengthType.LLLVAR,
                SubelementFormat.TAG_LENGTH_VALUE, 2, 2, 1, List.of(
                new FieldDefinitionModel("TCC", "Category", 0, 0, "ans", null, null, 0, 0, 0, null, Sensitivity.NONE)));

        Map<String, DEModel> declared = IsoUtils.parseTagLengthValue("R0105HELLO", def);
        Map<String, DEModel> shipped = IsoUtils.parseTagLengthValue(TestMessages.MASTERCARD_DE48,
                LOADER.find(CardNetworkType.MASTERCARD, "48").orElseThrow());

        assertThat(declared.get("TCC").name()).isEqualTo("Category");
        assertThat(declared.get("TCC").sensitivity()).isEqualTo(Sensitivity.NONE);
        assertThat(shipped.get("TCC").name()).isEqualTo("Transaction Category Code");
        assertThat(shipped.get("TCC").sensitivity()).isEqualTo(Sensitivity.NONE);
        assertThat(shipped.get("TCC").toString()).contains("rawValue=R,");
    }

    @ParameterizedTest
    @CsvSource({"0105HEL", "01", "01X5HELLO"})
    void malformedTagLengthValueThrows(String value) {
        assertThatThrownBy(() -> IsoUtils.parseTagLengthValue(value, tlvDef("48", 0)))
                .isInstanceOf(IsoParserException.class)
                .hasMessage("Malformed subelements in DE48");
    }

    @Test
    void duplicateTagLengthValueTagThrows() {
        assertThatThrownBy(() -> IsoUtils.parseTagLengthValue("0101A0101B", tlvDef("48", 0)))
                .isInstanceOf(IsoParserException.class)
                .hasMessage("Malformed subelements in DE48: duplicate tag 01");
    }

    @Test
    void duplicateBerTlvTagThrows() {
        byte[] data = IsoUtils.hexToBytes("9F020200019F02020002");

        assertThatThrownBy(() -> IsoUtils.parseBerTlv(data, berDef()))
                .isInstanceOf(IsoParserException.class)
                .hasMessage("Malformed subelements in DE55: duplicate tag 9F02");
    }

    @ParameterizedTest
    @CsvSource({"9F0202000100", "9F020200010000", "9F02020001FF", "00009F02020001", "FF9F0202000100FF"})
    void berTlvPaddingIsSkipped(String hex) {
        Map<String, DEModel> subs = IsoUtils.parseBerTlv(IsoUtils.hexToBytes(hex), berDef());

        assertThat(subs.keySet()).containsExactly("9F02");
        assertThat(subs.get("9F02").rawValue()).isEqualTo("0001");
    }

    @Test
    void berTlvOfOnlyPaddingIsEmpty() {
        assertThat(IsoUtils.parseBerTlv(IsoUtils.hexToBytes("0000FF"), berDef())).isEmpty();
    }

    @Test
    void parsesBerTlv() {
        Map<String, DEModel> subs = IsoUtils.parseBerTlv(IsoUtils.hexToBytes(TestMessages.DE55), berDef());

        assertThat(subs.keySet()).containsExactly("9F02", "5F2A", "9A");
        assertThat(subs.get("9F02").rawValue()).isEqualTo("000000001000");
        assertThat(subs.get("9F02").name()).isEqualTo("Amount Authorised");
        assertThat(subs.get("5F2A").rawValue()).isEqualTo("0986");
        assertThat(subs.get("9A").rawValue()).isEqualTo("261004");
        assertThat(subs.get("9A").name()).isEqualTo("Unknown");
    }

    @Test
    void parsesBerTlvLongFormLength() {
        String value = "AB".repeat(130);
        byte[] data = IsoUtils.hexToBytes("9F10" + "8182" + value + "9F3702A1B2");

        Map<String, DEModel> subs = IsoUtils.parseBerTlv(data, berDef());

        assertThat(subs.get("9F10").rawValue()).isEqualTo(value);
        assertThat(subs.get("9F37").rawValue()).isEqualTo("A1B2");
    }

    @ParameterizedTest
    @CsvSource({"9F020600000000", "9F", "9F02", "9F0281", "9F028300000001"})
    void truncatedBerTlvThrows(String hex) {
        assertThatThrownBy(() -> IsoUtils.parseBerTlv(IsoUtils.hexToBytes(hex), berDef()))
                .isInstanceOf(IsoParserException.class)
                .hasMessage("Malformed subelements in DE55");
    }

    @Test
    void packUnpackAndDecodeFields() {
        GenericPackager packager = IsoUtils.loadPackager(IsoParserConfig.DEFAULT_GENERIC_SINGLE_TEMPLATE);
        byte[] packed = IsoUtils.pack(TestMessages.build(packager, CardNetworkType.GENERIC));

        ISOMsg msg = IsoUtils.unpackHex(IsoUtils.bytesToHex(packed), packager);
        Map<String, DEModel> fields = IsoUtils.toFieldMap(msg, LOADER.load(CardNetworkType.GENERIC));

        assertThat(fields.keySet()).containsExactly("2", "3", "4", "7", "11", "22", "41", "42", "48", "49", "55");
        assertThat(fields.get("2").rawValue()).isEqualTo(TestMessages.PAN);
        assertThat(fields.get("2").name()).isEqualTo("Primary Account Number (PAN)");
        assertThat(fields.get("4").rawValue()).isEqualTo("000000001000");
        assertThat(fields.get("11").rawValue()).isEqualTo("123456");
        assertThat(fields.get("41").decodedValue()).isEqualTo("TERM0001");
        assertThat(fields.get("48").subelements().get("01").rawValue()).isEqualTo("HELLO");
        assertThat(fields.get("48").subelements().get("02").rawValue()).isEqualTo("ABC");
        assertThat(fields.get("55").subelements().get("9F02").rawValue()).isEqualTo("000000001000");
        assertThat(fields.get("55").subelements().get("5F2A").name()).isEqualTo("Transaction Currency Code");
        assertThat(fields.get("48").subelements().get("01").name()).isEqualTo("Additional data subelement 01");
        assertThat(fields.get("48").subelements().get("02").name()).isEqualTo("Additional data subelement 02");
        assertThat(fields.values()).allSatisfy(field -> assertThat(field.errors()).as("DE%s", field.id()).isEmpty());
    }

    private static FieldDefinitionModel fieldDef(String type, LengthType lengthType, int length, int maxLength) {
        return new FieldDefinitionModel("2", "test", length, maxLength, type, lengthType, null, 0, 0, 0, null);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "n|LLVAR|0|19|5555555555554444|",
            "n|LLVAR|0|19|5555555X55554444|DE2: contains characters not allowed for type n",
            "n|LLVAR|0|10|5555555555554444|DE2: length 16 exceeds the maximum length 10",
            "n|FIXED|12|16|5555555555554444|DE2: length 16 differs from the fixed length 12",
            "an|LLVAR|0|19|ABC 123|",
            "an|LLVAR|0|19|ABC-123|DE2: contains characters not allowed for type an",
            "ans|LLVAR|0|19|ABC-123/*|",
            "ans|LLVAR|0|19|AB\u0001C|DE2: contains characters not allowed for type ans",
            "z|LLVAR|0|37|5555555555554444=2512|",
            "z|LLVAR|0|37|5555555555554444^2512|DE2: contains characters not allowed for type z",
            "x+n|FIXED|9|9|C00000100|",
            "x+n|FIXED|9|9|X00000100|DE2: contains characters not allowed for type x+n",
            "ans|LLVAR|0|999|<100 chars>|DE2: length 100 does not fit a LLVAR prefix"
    })
    void validatesValuesAgainstTheirDefinition(String type, LengthType lengthType, int length, int maxLength,
                                               String value, String expectedError) throws Exception {
        String actualValue = value.equals("<100 chars>") ? "A".repeat(100) : value;
        ISOMsg msg = new ISOMsg();
        msg.set(2, actualValue);

        DEModel field = IsoUtils.toFieldMap(msg, Map.of("2", fieldDef(type, lengthType, length, maxLength))).get("2");

        assertThat(field.rawValue()).isEqualTo(actualValue);
        if (expectedError == null) {
            assertThat(field.errors()).isEmpty();
        } else {
            assertThat(field.errors()).containsExactly(expectedError);
        }
    }

    @Test
    void binaryLengthIsCountedInBytes() {
        ISOMsg msg = new ISOMsg();
        msg.set(52, IsoUtils.hexToBytes("0123456789ABCDEF"));
        FieldDefinitionModel def = new FieldDefinitionModel("52", "PIN", 8, 8, "b", LengthType.FIXED, null, 0, 0, 0, null);

        assertThat(IsoUtils.toFieldMap(msg, Map.of("52", def)).get("52").errors()).isEmpty();
    }

    @Test
    void malformedSubelementsAreReportedOnTheField() throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.set(2, TestMessages.PAN);
        msg.set(48, "0105HEL");
        msg.set(55, "9F0206");
        Map<String, FieldDefinitionModel> defs = LOADER.load(CardNetworkType.GENERIC);

        Map<String, DEModel> fields = IsoUtils.toFieldMap(msg, defs);

        assertThat(fields.get("2").errors()).isEmpty();
        assertThat(fields.get("48").errors()).containsExactly("Malformed subelements in DE48");
        assertThat(fields.get("48").subelements()).isEmpty();
        assertThat(fields.get("48").rawValue()).isEqualTo("0105HEL");
        assertThat(fields.get("55").errors()).containsExactly("Malformed subelements in DE55");
    }

    @Test
    void subelementsCarryTheirPathAndDeclaredSensitivity() {
        Map<String, DEModel> tlv = IsoUtils.parseTagLengthValue(TestMessages.MASTERCARD_DE48,
                LOADER.find(CardNetworkType.MASTERCARD, "48").orElseThrow());
        Map<String, DEModel> ber = IsoUtils.parseBerTlv(IsoUtils.hexToBytes("5A08" + TestMessages.PAN + "9F7C0101"),
                LOADER.find(CardNetworkType.GENERIC, "55").orElseThrow());

        assertThat(tlv.get("TCC").path()).isEqualTo("48/TCC");
        assertThat(tlv.get("TCC").sensitivity()).isEqualTo(Sensitivity.NONE);
        assertThat(tlv.get("42").sensitivity()).isEqualTo(Sensitivity.NONE);
        assertThat(tlv.get("92").path()).isEqualTo("48/92");
        assertThat(tlv.get("92").sensitivity()).isEqualTo(Sensitivity.FULL);
        assertThat(ber.get("5A").path()).isEqualTo("55/5A");
        assertThat(ber.get("5A").sensitivity()).isEqualTo(Sensitivity.PARTIAL);
        // undeclared tag: unknown content, masked
        assertThat(ber.get("9F7C").name()).isEqualTo("Unknown");
        assertThat(ber.get("9F7C").sensitivity()).isEqualTo(Sensitivity.FULL);
    }

    @Test
    void fieldsWithoutDefinitionAreMaskedAndUnsplittableFieldsAreFullyMasked() throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.set(4, "000000001000");
        msg.set(48, "0105HEL");
        msg.set(49, "986");
        Map<String, FieldDefinitionModel> defs = LOADER.load(CardNetworkType.GENERIC);

        Map<String, DEModel> fields = IsoUtils.toFieldMap(msg, Map.of("4", defs.get("4"), "48", defs.get("48")));

        assertThat(fields.get("4").sensitivity()).isEqualTo(Sensitivity.NONE);
        assertThat(fields.get("48").sensitivity()).isEqualTo(Sensitivity.FULL);
        assertThat(fields.get("49").sensitivity()).isEqualTo(Sensitivity.FULL);
        assertThat(fields.get("49").path()).isEqualTo("49");
    }

    @Test
    void trailingWhitespaceIsOnlyAcceptedWhenAllowed() {
        GenericPackager packager = IsoUtils.loadPackager(IsoParserConfig.DEFAULT_GENERIC_SINGLE_TEMPLATE);
        byte[] packed = IsoUtils.pack(TestMessages.build(packager, CardNetworkType.GENERIC));
        byte[] withLineBreak = java.util.Arrays.copyOf(packed, packed.length + 2);
        withLineBreak[packed.length] = '\r';
        withLineBreak[packed.length + 1] = '\n';

        assertThat(IsoUtils.unpack(withLineBreak, packager, true).getString(2)).isEqualTo(TestMessages.PAN);
        assertThatThrownBy(() -> IsoUtils.unpack(withLineBreak, packager))
                .isInstanceOf(IsoParserException.class)
                .hasMessageContaining("2 trailing byte(s)");
    }

    @Test
    void bitmapMatchesPackedMessage() {
        GenericPackager packager = IsoUtils.loadPackager(IsoParserConfig.DEFAULT_GENERIC_SINGLE_TEMPLATE);
        byte[] packed = IsoUtils.pack(TestMessages.build(packager, CardNetworkType.GENERIC));
        ISOMsg msg = IsoUtils.unpack(packed, packager);

        BitmapModel bitmap = IsoUtils.parseBitmap(msg);

        assertThat(bitmap.presentFields()).containsExactly(2, 3, 4, 7, 11, 22, 41, 42, 48, 49, 55);
        assertThat(bitmap.secondaryPresent()).isFalse();
        assertThat(bitmap.secondaryHex()).isNull();
        assertThat(bitmap.primaryHex()).isEqualTo(new String(packed, 4, 16, StandardCharsets.US_ASCII));
    }

    @Test
    void bitmapIsReadFromTheWire() {
        GenericPackager packager = IsoUtils.loadPackager(IsoParserConfig.DEFAULT_GENERIC_SINGLE_TEMPLATE);
        // Hand-built: bit 1 set with an all-zero secondary bitmap, DE2 and DE64 present.
        String wire = "0100" + "C000000000000001" + "0000000000000000" + "16" + TestMessages.PAN + "0123456789ABCDEF";
        ISOMsg msg = IsoUtils.unpack(wire.getBytes(StandardCharsets.US_ASCII), packager);

        BitmapModel bitmap = IsoUtils.parseBitmap(msg);

        assertThat(bitmap.primaryHex()).isEqualTo("C000000000000001");
        assertThat(bitmap.secondaryPresent()).isTrue();
        assertThat(bitmap.secondaryHex()).isEqualTo("0000000000000000");
        assertThat(bitmap.presentFields()).containsExactly(2, 64);
    }

    @Test
    void bitmapOfABuiltMessageIsDerivedFromItsFields() throws Exception {
        ISOMsg msg = new ISOMsg();
        msg.set(2, TestMessages.PAN);
        msg.set(102, TestMessages.DE102);

        BitmapModel bitmap = IsoUtils.parseBitmap(msg);

        assertThat(bitmap.primaryHex()).isEqualTo("C000000000000000");
        assertThat(bitmap.secondaryHex()).isEqualTo("0000000004000000");
        assertThat(bitmap.presentFields()).containsExactly(2, 102);
    }

    @Test
    void trailingBytesAreRejected() {
        GenericPackager packager = IsoUtils.loadPackager(IsoParserConfig.DEFAULT_GENERIC_SINGLE_TEMPLATE);
        byte[] packed = IsoUtils.pack(TestMessages.build(packager, CardNetworkType.GENERIC));
        byte[] withTail = java.util.Arrays.copyOf(packed, packed.length + 11);
        System.arraycopy("GARBAGETAIL".getBytes(StandardCharsets.US_ASCII), 0, withTail, packed.length, 11);

        assertThatThrownBy(() -> IsoUtils.unpack(withTail, packager))
                .isInstanceOf(IsoParserException.class)
                .hasMessageContaining("11 trailing byte(s)");
    }

    @Test
    void secondaryBitmapWhenFieldAbove64() {
        GenericPackager packager = IsoUtils.loadPackager(IsoParserConfig.DEFAULT_GENERIC_SINGLE_TEMPLATE);
        byte[] packed = IsoUtils.pack(TestMessages.build(packager, CardNetworkType.GENERIC, true));
        ISOMsg msg = IsoUtils.unpack(packed, packager);

        BitmapModel bitmap = IsoUtils.parseBitmap(msg);

        assertThat(bitmap.secondaryPresent()).isTrue();
        assertThat(bitmap.presentFields()).endsWith(102);
        assertThat(bitmap.primaryHex() + bitmap.secondaryHex())
                .isEqualTo(new String(packed, 4, 32, StandardCharsets.US_ASCII));
        assertThat(IsoUtils.toFieldMap(msg, LOADER.load(CardNetworkType.GENERIC)).get("102").decodedValue())
                .isEqualTo(TestMessages.DE102);
    }

    @Test
    void toIsoMsgRepacksIdentically() {
        GenericPackager packager = IsoUtils.loadPackager(IsoParserConfig.DEFAULT_GENERIC_SINGLE_TEMPLATE);
        ISOMsg original = TestMessages.build(packager, CardNetworkType.GENERIC, true);
        original.set(52, IsoUtils.hexToBytes("0123456789ABCDEF"));
        byte[] packed = IsoUtils.pack(original);
        Map<String, FieldDefinitionModel> defs = LOADER.load(CardNetworkType.GENERIC);
        Map<String, DEModel> fields = IsoUtils.toFieldMap(IsoUtils.unpack(packed, packager), defs);
        assertThat(fields.get("52").rawValue()).isEqualTo("0123456789ABCDEF");

        ISOMsg rebuilt = IsoUtils.toIsoMsg("0100", fields, defs, packager);

        assertThat(IsoUtils.pack(rebuilt)).isEqualTo(packed);
    }

    @ParameterizedTest
    @CsvSource({
            IsoParserConfig.DEFAULT_GENERIC_SINGLE_TEMPLATE + ",GENERIC",
            IsoParserConfig.DEFAULT_GENERIC_DUAL_TEMPLATE + ",GENERIC",
            IsoParserConfig.DEFAULT_MASTERCARD_SINGLE_TEMPLATE + ",MASTERCARD",
            IsoParserConfig.DEFAULT_MASTERCARD_DUAL_TEMPLATE + ",MASTERCARD"
    })
    void templatesMatchDefinitions(String template, CardNetworkType network) {
        GenericPackager packager = IsoUtils.loadPackager(template);
        Map<String, FieldDefinitionModel> defs = LOADER.load(network);

        for (int i = 2; i <= 128; i++) {
            assertThat(packager.getFieldPackager(i).getLength())
                    .as("DE%d in %s", i, template)
                    .isEqualTo(defs.get(String.valueOf(i)).maxLength());
        }
    }

    @ParameterizedTest
    @CsvSource({
            IsoParserConfig.DEFAULT_GENERIC_SINGLE_TEMPLATE + "," + IsoParserConfig.DEFAULT_GENERIC_DUAL_TEMPLATE,
            IsoParserConfig.DEFAULT_MASTERCARD_SINGLE_TEMPLATE + "," + IsoParserConfig.DEFAULT_MASTERCARD_DUAL_TEMPLATE
    })
    void singleAndDualTemplatesAreFieldForFieldEqual(String single, String dual) {
        GenericPackager singlePackager = IsoUtils.loadPackager(single);
        GenericPackager dualPackager = IsoUtils.loadPackager(dual);

        for (int i = 0; i <= 128; i++) {
            assertThat(dualPackager.getFieldPackager(i).getClass()).as("DE%d class", i)
                    .isEqualTo(singlePackager.getFieldPackager(i).getClass());
            assertThat(dualPackager.getFieldPackager(i).getLength()).as("DE%d length", i)
                    .isEqualTo(singlePackager.getFieldPackager(i).getLength());
        }
    }

    @Test
    void loadPackagerDoesNotCacheAndMissingTemplateThrows() {
        assertThat(IsoUtils.loadPackager(IsoParserConfig.DEFAULT_MASTERCARD_DUAL_TEMPLATE))
                .isNotSameAs(IsoUtils.loadPackager(IsoParserConfig.DEFAULT_MASTERCARD_DUAL_TEMPLATE));
        assertThatThrownBy(() -> IsoUtils.loadPackager("classpath:xml/missing.xml"))
                .isInstanceOf(IsoParserException.class);
        assertThatThrownBy(() -> IsoUtils.loadPackager(null)).isInstanceOf(IsoParserException.class);
    }
}
