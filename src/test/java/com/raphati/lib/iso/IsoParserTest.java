package com.raphati.lib.iso;

import com.raphati.lib.iso.config.IsoParserConfig;
import com.raphati.lib.iso.crypto.CryptoService;
import com.raphati.lib.iso.exception.InvalidTransactionTypeException;
import com.raphati.lib.iso.exception.IsoParserException;
import com.raphati.lib.iso.model.DEModel;
import com.raphati.lib.iso.model.IsoModel;
import com.raphati.lib.iso.model.ParserModel;
import com.raphati.lib.iso.strategy.MessageProcessingStrategy;
import com.raphati.lib.iso.type.CardNetworkType;
import com.raphati.lib.iso.type.MessageType;
import com.raphati.lib.iso.type.mti.ClassType;
import com.raphati.lib.iso.utils.IsoUtils;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IsoParserTest {

    private final IsoParser parser = new IsoParser();

    @FunctionalInterface
    private interface Customizer {
        void apply(ISOMsg msg) throws ISOException;
    }

    static Stream<Arguments> combinations() {
        return Stream.of(CardNetworkType.values())
                .flatMap(n -> Stream.of(MessageType.values()).map(t -> Arguments.of(n, t)));
    }

    private byte[] packedFor(CardNetworkType network, MessageType type, boolean secondary) {
        return packedFor(network, type, secondary, msg -> { });
    }

    private byte[] packedFor(CardNetworkType network, MessageType type, boolean secondary, Customizer customizer) {
        MessageProcessingStrategy strategy = parser.factory().getStrategy(network, type);
        ISOMsg msg = TestMessages.build(strategy.packager(), network, secondary);
        try {
            customizer.apply(msg);
        } catch (ISOException e) {
            throw new IllegalStateException(e);
        }
        return IsoUtils.pack(msg);
    }

    private ParserModel parseGeneric(Customizer customizer) {
        String hex = IsoUtils.bytesToHex(packedFor(CardNetworkType.GENERIC, MessageType.SINGLE, false, customizer));
        return parser.parse(hex, CardNetworkType.GENERIC, MessageType.SINGLE);
    }

    private static String asciiHex(String ascii) {
        return IsoUtils.bytesToHex(ascii.getBytes(StandardCharsets.US_ASCII));
    }

    @ParameterizedTest
    @MethodSource("combinations")
    void parsesEveryNetworkAndType(CardNetworkType network, MessageType type) {
        String hex = IsoUtils.bytesToHex(packedFor(network, type, true));

        ParserModel result = parser.parse(hex, network, type);

        assertThat(result.network()).isEqualTo(network);
        assertThat(result.messageType()).isEqualTo(type);
        IsoModel message = result.message();
        assertThat(message.mti().value()).isEqualTo("0100");
        assertThat(message.mti().messageClass()).isEqualTo(ClassType.AUTHORIZATION);
        assertThat(message.bitmap().secondaryPresent()).isTrue();
        assertThat(message.bitmap().presentFields()).containsExactly(2, 3, 4, 7, 11, 22, 41, 42, 48, 49, 55, 102);
        assertThat(message.bitmap().primaryHex() + message.bitmap().secondaryHex()).isEqualTo(hex.substring(8, 72)
                .transform(IsoParserTest::fromAsciiHex));
        assertThat(message.field(2)).map(DEModel::rawValue).contains(TestMessages.PAN);
        assertThat(message.field(4)).map(DEModel::rawValue).contains("000000001000");
        assertThat(message.field(3)).map(DEModel::rawValue).contains("000000");
        assertThat(message.errors()).isEmpty();

        Map<String, DEModel> de48 = message.field(48).orElseThrow().subelements();
        if (network == CardNetworkType.MASTERCARD) {
            assertThat(de48.keySet()).containsExactly("TCC", "42", "92");
            assertThat(de48.get("42").name()).isEqualTo("Electronic Commerce Indicators");
            assertThat(de48.get("42").rawValue()).isEqualTo("0102105");
            assertThat(de48.get("92").name()).isEqualTo("CVC 2");
        } else {
            assertThat(de48.keySet()).containsExactly("01", "02");
            assertThat(de48.get("01").rawValue()).isEqualTo("HELLO");
            assertThat(de48.get("01").name()).isEqualTo("Additional data subelement 01");
            assertThat(de48.get("02").name()).isEqualTo("Additional data subelement 02");
        }

        Map<String, DEModel> de55 = message.field(55).orElseThrow().subelements();
        assertThat(de55.keySet()).containsExactly("9F02", "5F2A", "9A");
        assertThat(de55.get("9F02").rawValue()).isEqualTo("000000001000");
    }

    /** The packed bitmap is ASCII hex, so the hex dump of the wire holds the bitmap hex as ASCII codes. */
    private static String fromAsciiHex(String hexOfAscii) {
        return new String(IsoUtils.hexToBytes(hexOfAscii), StandardCharsets.US_ASCII);
    }

    @ParameterizedTest
    @MethodSource("combinations")
    void packIsTheReverseOfParse(CardNetworkType network, MessageType type) {
        String hex = IsoUtils.bytesToHex(packedFor(network, type, true));

        assertThat(parser.pack(parser.parse(hex, network, type))).isEqualTo(hex);
    }

    @Test
    void parsesByName() {
        String hex = IsoUtils.bytesToHex(packedFor(CardNetworkType.MASTERCARD, MessageType.DUAL, false));

        ParserModel result = parser.parse(hex, "mastercard", "dual");

        assertThat(result.network()).isEqualTo(CardNetworkType.MASTERCARD);
        assertThat(result.messageType()).isEqualTo(MessageType.DUAL);
        assertThat(result.message().bitmap().secondaryPresent()).isFalse();
    }

    @Test
    void byNameReportsTheNetworkFirst() {
        assertThatThrownBy(() -> parser.parse("00", "NOT_A_NETWORK", "TRIPLE"))
                .isInstanceOf(IsoParserException.class)
                .isNotInstanceOf(InvalidTransactionTypeException.class)
                .hasMessageContaining("NOT_A_NETWORK");
        assertThatThrownBy(() -> parser.parse("00", "generic", "TRIPLE"))
                .isInstanceOf(InvalidTransactionTypeException.class);
    }

    @Test
    void extractFieldsMatchesParse() {
        String hex = IsoUtils.bytesToHex(packedFor(CardNetworkType.GENERIC, MessageType.SINGLE, false));
        MessageProcessingStrategy strategy = parser.factory().getStrategy(CardNetworkType.GENERIC, MessageType.SINGLE);

        assertThat(strategy.extractFields(hex, parser.loader()))
                .isEqualTo(strategy.parse(hex, parser.loader()).fields());
    }

    @Test
    void parsesRawAscii() {
        byte[] packed = packedFor(CardNetworkType.GENERIC, MessageType.DUAL, false);
        String ascii = new String(packed, StandardCharsets.ISO_8859_1);

        ParserModel result = parser.parseAscii(ascii, CardNetworkType.GENERIC, MessageType.DUAL);

        assertThat(result.message().field(2)).map(DEModel::rawValue).contains(TestMessages.PAN);
        assertThat(result.message().field(41)).map(DEModel::decodedValue).contains("TERM0001");
    }

    @Test
    void hexWithWhitespaceIsAccepted() {
        String hex = IsoUtils.bytesToHex(packedFor(CardNetworkType.GENERIC, MessageType.SINGLE, false));
        String spaced = hex.substring(0, 8) + " \n" + hex.substring(8);

        assertThat(parser.parse(spaced, CardNetworkType.GENERIC, MessageType.SINGLE).message().field(11))
                .map(DEModel::rawValue).contains("123456");
    }

    @Test
    void invalidHexThrows() {
        assertThatThrownBy(() -> parser.parse("NOT-HEX", CardNetworkType.GENERIC, MessageType.SINGLE))
                .isInstanceOf(IsoParserException.class);
        assertThatThrownBy(() -> parser.parse("30313030", CardNetworkType.GENERIC, MessageType.SINGLE))
                .isInstanceOf(IsoParserException.class);
    }

    @Test
    void wireBitmapIsReportedAsIs() {
        String wire = "0100" + "C000000000000001" + "0000000000000000" + "16" + TestMessages.PAN + "0123456789ABCDEF";

        IsoModel message = parser.parse(asciiHex(wire), CardNetworkType.GENERIC, MessageType.SINGLE).message();

        assertThat(message.bitmap().primaryHex()).isEqualTo("C000000000000001");
        assertThat(message.bitmap().secondaryPresent()).isTrue();
        assertThat(message.bitmap().secondaryHex()).isEqualTo("0000000000000000");
        assertThat(message.field(64)).map(DEModel::rawValue).contains("0123456789ABCDEF");
    }

    @Test
    void trailingBytesAreRejected() {
        String hex = IsoUtils.bytesToHex(packedFor(CardNetworkType.GENERIC, MessageType.SINGLE, false))
                + asciiHex("GARBAGETAIL");

        assertThatThrownBy(() -> parser.parse(hex, CardNetworkType.GENERIC, MessageType.SINGLE))
                .isInstanceOf(IsoParserException.class)
                .hasMessageContaining("trailing byte");
    }

    @Test
    void invalidValueIsReportedOnTheField() {
        String wire = "0100" + "4000000000000000" + "16" + "5555555X55554444";

        IsoModel message = parser.parse(asciiHex(wire), CardNetworkType.GENERIC, MessageType.SINGLE).message();

        DEModel de2 = message.field(2).orElseThrow();
        assertThat(de2.rawValue()).isEqualTo("5555555X55554444");
        assertThat(de2.errors()).containsExactly("DE2: contains characters not allowed for type n");
        assertThat(message.errors()).containsOnlyKeys("2");
        assertThat(message.toString()).doesNotContain("5555555X55554444");
    }

    @Test
    void malformedSubelementsKeepTheRestOfTheMessage() {
        IsoModel message = parseGeneric(msg -> msg.set(55, "9F0206")).message();

        assertThat(message.field(2)).map(DEModel::rawValue).contains(TestMessages.PAN);
        assertThat(message.field(4)).map(DEModel::rawValue).contains("000000001000");
        assertThat(message.field(48).orElseThrow().subelements()).containsKeys("01", "02");
        DEModel de55 = message.field(55).orElseThrow();
        assertThat(de55.rawValue()).isEqualTo("9F0206");
        assertThat(de55.subelements()).isEmpty();
        assertThat(message.errors()).containsExactly(Map.entry("55", List.of("Malformed subelements in DE55")));
        assertThat(de55.toString()).contains("rawValue=****");
    }

    @Test
    void duplicateTlvTagIsReportedNotCollapsed() {
        DEModel de55 = parseGeneric(msg -> msg.set(55, "9F02020001" + "9F02020002")).message().field(55).orElseThrow();

        assertThat(de55.rawValue()).isEqualTo("9F020200019F02020002");
        assertThat(de55.subelements()).isEmpty();
        assertThat(de55.errors()).containsExactly("Malformed subelements in DE55: duplicate tag 9F02");
    }

    @Test
    void emvPaddingIsSkipped() {
        for (String padded : List.of("9F02020001" + "00", "9F02020001" + "0000", "9F02020001" + "FF")) {
            DEModel de55 = parseGeneric(msg -> msg.set(55, padded)).message().field(55).orElseThrow();

            assertThat(de55.subelements()).as(padded).containsOnlyKeys("9F02");
            assertThat(de55.errors()).as(padded).isEmpty();
        }
    }

    @Test
    void reservedMtiDigitsParse() {
        IsoModel message = parseGeneric(msg -> msg.setMTI("0106")).message();

        assertThat(message.mti().value()).isEqualTo("0106");
        assertThat(message.mti().origin().name()).isEqualTo("RESERVED_6");
    }

    @Test
    void toStringMasksPan() {
        String hex = IsoUtils.bytesToHex(packedFor(CardNetworkType.MASTERCARD, MessageType.SINGLE, false));

        String text = parser.parse(hex, CardNetworkType.MASTERCARD, MessageType.SINGLE).toString();

        assertThat(text).doesNotContain(TestMessages.PAN).contains(TestMessages.MASKED_PAN);
    }

    @Test
    void toStringMasksEverySensitiveField() {
        String track2 = TestMessages.PAN + "=2512201";
        String track2Equivalent = TestMessages.PAN + "D251220100000F";
        String hex = IsoUtils.bytesToHex(packedFor(CardNetworkType.MASTERCARD, MessageType.SINGLE, false, msg -> {
            msg.set(34, TestMessages.PAN);
            msg.set(35, track2);
            msg.set(36, "01" + track2);
            msg.set(45, "B" + TestMessages.PAN + "^TEST/CARD^2512201");
            msg.set(48, "R420701021059203987");
            msg.set(52, IsoUtils.hexToBytes("0123456789ABCDEF"));
            msg.set(55, TestMessages.DE55 + "5A08" + TestMessages.PAN + "570F" + track2Equivalent);
        }));

        ParserModel result = parser.parse(hex, CardNetworkType.MASTERCARD, MessageType.SINGLE);
        String text = result.toString();

        assertThat(result.message().errors()).isEmpty();
        assertThat(result.message().field(55).orElseThrow().subelements()).containsKeys("5A", "57");
        assertThat(text).doesNotContain(TestMessages.PAN)
                .doesNotContain("2512201")
                .doesNotContain("0123456789ABCDEF")
                .doesNotContain("9203987")
                .contains("92=DEModel[id=92, name=CVC 2, rawValue=****")
                .contains(TestMessages.MASKED_PAN)
                .contains("rawValue=0102105");
    }

    // ---- Round-2 review regressions -------------------------------------------------------------------------------

    private static final String CVC2 = "739";
    private static final String UCAF = "UCAFSECRET0123";
    private static final String AUTH_DATA = "AUTHDATA4242";
    private static final String TRACK2_EQUIVALENT = TestMessages.PAN + "D251220100000F";
    private static final String ACCOUNT_1 = "ACCT1234567890";
    private static final String ACCOUNT_2 = "ACCT9876543210";

    /** Mastercard message carrying every sensitive element the round-2 review named. */
    private ParserModel parseSensitiveMastercard() {
        String hex = IsoUtils.bytesToHex(packedFor(CardNetworkType.MASTERCARD, MessageType.SINGLE, false, msg -> {
            msg.set(14, "2712");
            msg.set(48, "R" + "42070102105" + "4314" + UCAF + "6612" + AUTH_DATA + "9203" + CVC2);
            msg.set(55, TestMessages.DE55 + "5A08" + TestMessages.PAN + "570F" + TRACK2_EQUIVALENT);
            msg.set(102, ACCOUNT_1);
            msg.set(103, ACCOUNT_2);
        }));
        return parser.parse(hex, CardNetworkType.MASTERCARD, MessageType.SINGLE);
    }

    @Test
    void detachedSensitiveSubelementsStayMasked() {
        IsoModel message = parseSensitiveMastercard().message();
        Map<String, DEModel> de55 = message.field(55).orElseThrow().subelements();
        Map<String, DEModel> de48 = message.field(48).orElseThrow().subelements();

        assertThat(message.errors()).isEmpty();
        assertThat(de55.get("5A").toString()).doesNotContain(TestMessages.PAN).contains(TestMessages.MASKED_PAN);
        assertThat(de55.get("57").toString()).doesNotContain(TRACK2_EQUIVALENT).contains("rawValue=****");
        assertThat(de48.get("92").toString()).doesNotContain("rawValue=" + CVC2).contains("rawValue=****");
        assertThat(de55.toString()).doesNotContain(TestMessages.PAN);
        assertThat(de55.values().toString()).doesNotContain(TestMessages.PAN);
        assertThat(de48.toString()).doesNotContain("rawValue=" + CVC2).doesNotContain(UCAF).doesNotContain(AUTH_DATA);
        assertThat(message.fields().toString()).doesNotContain(TestMessages.PAN);
        // the raw accessors stay unmasked
        assertThat(de55.get("5A").rawValue()).isEqualTo(TestMessages.PAN);
        assertThat(de48.get("92").rawValue()).isEqualTo(CVC2);
    }

    @Test
    void accountIdentificationAuthenticationDataAndExpiryAreMasked() {
        ParserModel result = parseSensitiveMastercard();
        String text = result.toString();

        assertThat(result.message().field(102)).map(DEModel::rawValue).contains(ACCOUNT_1);
        assertThat(text).doesNotContain(ACCOUNT_1).doesNotContain(ACCOUNT_2)
                .doesNotContain(UCAF).doesNotContain(AUTH_DATA)
                .doesNotContain("rawValue=2712")
                .contains("rawValue=0102105");
    }

    @Test
    void sensitiveEmvTagsHaveTheirNames() {
        Map<String, DEModel> de55 = parseSensitiveMastercard().message().field(55).orElseThrow().subelements();

        assertThat(de55.get("5A").name()).isEqualTo("Application Primary Account Number (PAN)");
        assertThat(de55.get("57").name()).isEqualTo("Track 2 Equivalent Data");
    }

    @Test
    void undeclaredSubelementIsMaskedWithItsParent() {
        String unknownValue = "A1B2C3D4";
        DEModel de55 = parseGeneric(msg -> msg.set(55, TestMessages.DE55 + "9F7C04" + unknownValue))
                .message().field(55).orElseThrow();

        assertThat(de55.subelements().get("9F7C").name()).isEqualTo("Unknown");
        assertThat(de55.toString()).doesNotContain(unknownValue)
                .contains("9F7C=DEModel[id=9F7C, name=Unknown, rawValue=****")
                .contains("9F02=DEModel[id=9F02, name=Amount Authorised, rawValue=000000001000");
    }

    @Test
    void validationErrorKeepsTheValueReadable() {
        DEModel de4 = parseGeneric(msg -> msg.set(4, "00000000X000")).message().field(4).orElseThrow();

        assertThat(de4.errors()).containsExactly("DE4: contains characters not allowed for type n");
        assertThat(de4.toString()).contains("rawValue=00000000X000");
    }

    @Test
    void genericDe48Subelement10DoesNotMaskTheParent() {
        DEModel de48 = parseGeneric(msg -> msg.set(48, "0105HELLO1003ABC")).message().field(48).orElseThrow();

        assertThat(de48.subelements()).containsOnlyKeys("01", "10");
        assertThat(de48.toString()).contains("rawValue=0105HELLO1003ABC").contains("rawValue=ABC");
    }

    @Test
    void parseAsciiAcceptsATrailingLineBreak() {
        String ascii = new String(packedFor(CardNetworkType.GENERIC, MessageType.SINGLE, false),
                StandardCharsets.ISO_8859_1);

        for (String suffix : List.of("\n", "\r\n", " \t\r\n")) {
            assertThat(parser.parseAscii(ascii + suffix, CardNetworkType.GENERIC, MessageType.SINGLE)
                    .message().field(2)).as("suffix %s", suffix.length()).map(DEModel::rawValue).contains(TestMessages.PAN);
        }
        assertThatThrownBy(() -> parser.parseAscii(ascii + "X\n", CardNetworkType.GENERIC, MessageType.SINGLE))
                .isInstanceOf(IsoParserException.class).hasMessageContaining("trailing byte");
        assertThatThrownBy(() -> parser.parse(IsoUtils.bytesToHex(ascii.getBytes(StandardCharsets.ISO_8859_1))
                + "0A", CardNetworkType.GENERIC, MessageType.SINGLE))
                .isInstanceOf(IsoParserException.class).hasMessageContaining("trailing byte");
    }

    @Test
    void binaryDe55GivesOneActionableError() {
        String binary = new String(IsoUtils.hexToBytes(TestMessages.DE55), StandardCharsets.ISO_8859_1);

        IsoModel message = parseGeneric(msg -> msg.set(55, binary)).message();

        DEModel de55 = message.field(55).orElseThrow();
        assertThat(de55.subelements()).isEmpty();
        assertThat(de55.errors()).containsExactly(
                "Malformed subelements in DE55: the value is not hex text (EMV data must be hex encoded)");
        assertThat(de55.toString()).contains("rawValue=****");
        assertThat(message.field(2)).map(DEModel::rawValue).contains(TestMessages.PAN);
    }

    @Test
    void packCanonicalizesARedundantSecondaryBitmap() {
        String mac = "0123456789ABCDEF";
        String wire = "0100" + "C000000000000001" + "0000000000000000" + "16" + TestMessages.PAN + mac;
        ParserModel parsed = parser.parse(asciiHex(wire), CardNetworkType.GENERIC, MessageType.SINGLE);

        String repacked = parser.pack(parsed);

        assertThat(parsed.message().bitmap().secondaryHex()).isEqualTo("0000000000000000");
        assertThat(repacked).isEqualTo(asciiHex("0100" + "4000000000000001" + "16" + TestMessages.PAN + mac));
        assertThat(parser.parse(repacked, CardNetworkType.GENERIC, MessageType.SINGLE).message().fields())
                .isEqualTo(parsed.message().fields());
    }

    @Test
    void charsetOnlyEncodesTheParseAsciiInput() {
        IsoParser utf8 = new IsoParser(IsoParserConfig.builder().charset(StandardCharsets.UTF_8).build());
        String acceptor = "CAF\u00c9 DO JO\u00c3O" + " ".repeat(19) + "SAO PAULO";   // DE43, 40 characters
        String ascii = "0100" + "4000000000200000" + "16" + TestMessages.PAN + acceptor;
        String hex = IsoUtils.bytesToHex(ascii.getBytes(StandardCharsets.ISO_8859_1));

        // hex input: the charset plays no part, jPOS decodes the field bytes as ISO-8859-1
        assertThat(utf8.parse(hex, CardNetworkType.GENERIC, MessageType.SINGLE))
                .isEqualTo(parser.parse(hex, CardNetworkType.GENERIC, MessageType.SINGLE));
        assertThat(parser.parseAscii(ascii, CardNetworkType.GENERIC, MessageType.SINGLE).message().field(43))
                .map(DEModel::rawValue).contains(acceptor);
        // ASCII input: the charset encodes the string, so a multi-byte encoding changes the wire length
        assertThatThrownBy(() -> utf8.parseAscii(ascii, CardNetworkType.GENERIC, MessageType.SINGLE))
                .isInstanceOf(IsoParserException.class);
    }

    static Stream<Arguments> handWrittenWires() {
        // Built by hand from the templates (ASCII MTI, ASCII hex bitmap, LL/LLL ASCII length prefixes), not with jPOS.
        String generic = "0200" + "7020000000C10200"
                + "16" + "4111111111111111"            // DE2
                + "000000"                             // DE3
                + "000000002500"                       // DE4
                + "000042"                             // DE11
                + "TERM0042"                           // DE41
                + "MERCHANT0000042"                    // DE42
                + "016" + "0105HELLO0203ABC"           // DE48
                + "028" + "9F02060000000025009A03261004"; // DE55
        String mastercard = "0100" + "7020000000C18200"
                + "16" + "5555555555554444"            // DE2
                + "000000"                             // DE3
                + "000000001000"                       // DE4
                + "000043"                             // DE11
                + "TERM0043"                           // DE41
                + "MERCHANT0000043"                    // DE42
                + "019" + "R420701021059203123"        // DE48: TCC, SE42, SE92
                + "986"                                // DE49
                + "028" + "9F02060000000010009A03261004"; // DE55
        return Stream.of(
                Arguments.of(CardNetworkType.GENERIC, MessageType.SINGLE, generic),
                Arguments.of(CardNetworkType.GENERIC, MessageType.DUAL, generic),
                Arguments.of(CardNetworkType.MASTERCARD, MessageType.SINGLE, mastercard),
                Arguments.of(CardNetworkType.MASTERCARD, MessageType.DUAL, mastercard));
    }

    @ParameterizedTest
    @MethodSource("handWrittenWires")
    void parsesHandWrittenWire(CardNetworkType network, MessageType type, String wire) {
        String hex = asciiHex(wire);

        IsoModel message = parser.parse(hex, network, type).message();

        assertThat(message.errors()).isEmpty();
        assertThat(message.bitmap().primaryHex()).isEqualTo(wire.substring(4, 20));
        assertThat(message.field(3)).map(DEModel::rawValue).contains("000000");
        assertThat(message.field(41)).map(DEModel::rawValue).isPresent();
        assertThat(message.field(55).orElseThrow().subelements()).containsOnlyKeys("9F02", "9A");
        if (network == CardNetworkType.MASTERCARD) {
            assertThat(message.field(48).orElseThrow().subelements()).containsOnlyKeys("TCC", "42", "92");
            assertThat(message.field(49)).map(DEModel::rawValue).contains("986");
        } else {
            assertThat(message.field(48).orElseThrow().subelements()).containsOnlyKeys("01", "02");
        }
        assertThat(parser.pack(parser.parse(hex, network, type))).isEqualTo(hex);
    }

    @Test
    void subelementAadDoesNotDependOnHowItWasReached() {
        CryptoService crypto = new CryptoService(CryptoService.generateKey(256));
        DEModel de48 = parseSensitiveMastercard().message().field(48).orElseThrow();

        String direct = crypto.encryptField(de48.subelements().get("92")).rawValue();
        String viaParent = crypto.encryptField(de48).subelements().get("92").rawValue();

        assertThat(crypto.decrypt(direct, "48/92#raw")).isEqualTo(CVC2);
        assertThat(crypto.decrypt(viaParent, "48/92#raw")).isEqualTo(CVC2);
    }

    @Test
    void eachParserKeepsItsOwnPackagers() {
        MessageProcessingStrategy strategy = parser.factory().getStrategy(CardNetworkType.GENERIC, MessageType.SINGLE);
        MessageProcessingStrategy other = new IsoParser().factory().getStrategy(CardNetworkType.GENERIC, MessageType.SINGLE);

        assertThat(strategy.packager()).isSameAs(strategy.packager()).isNotSameAs(other.packager());
    }

    @Test
    void concurrentParsingGivesTheSameResult() throws Exception {
        List<String> messages = new ArrayList<>();
        combinations().forEach(args -> messages.add(IsoUtils.bytesToHex(
                packedFor((CardNetworkType) args.get()[0], (MessageType) args.get()[1], true))));
        IsoParser shared = new IsoParser();
        List<ParserModel> expected = new ArrayList<>();
        List<Arguments> combos = combinations().toList();
        for (int i = 0; i < combos.size(); i++) {
            expected.add(parser.parse(messages.get(i), (CardNetworkType) combos.get(i).get()[0],
                    (MessageType) combos.get(i).get()[1]));
        }

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int t = 0; t < 200; t++) {
                int i = t % combos.size();
                tasks.add(() -> shared.parse(messages.get(i), (CardNetworkType) combos.get(i).get()[0],
                        (MessageType) combos.get(i).get()[1]).equals(expected.get(i)));
            }
            for (Future<Boolean> future : pool.invokeAll(tasks)) {
                assertThat(future.get()).isTrue();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
