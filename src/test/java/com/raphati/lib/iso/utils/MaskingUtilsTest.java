package com.raphati.lib.iso.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raphati.lib.iso.type.Sensitivity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingUtilsTest {

    @Test
    void masksPanKeepingFirstSixAndLastFour() {
        assertThat(MaskingUtils.maskPan("5555555555554444")).isEqualTo("555555******4444");
        assertThat(MaskingUtils.maskPan("4111111111111111111")).isEqualTo("411111*********1111");
    }

    @Test
    void shortPanShrinksTheVisiblePrefix() {
        assertThat(MaskingUtils.maskPan("1234567890")).isEqualTo("******7890");
        assertThat(MaskingUtils.maskPan("1234567890123")).isEqualTo("123******0123");
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 11, 12, 13, 14, 15, 16, 17, 18, 19})
    void alwaysMasksAtLeastSixCharacters(int length) {
        String pan = "1234567890123456789".substring(0, length);

        String masked = MaskingUtils.maskPan(pan);

        assertThat(masked).hasSize(length).endsWith(pan.substring(length - 4));
        assertThat(masked.chars().filter(c -> c == '*').count()).isGreaterThanOrEqualTo(6);
    }

    @Test
    void shortPanIsFullyMasked() {
        assertThat(MaskingUtils.maskPan("123456789")).isEqualTo("*********");
        assertThat(MaskingUtils.maskPan("")).isEmpty();
        assertThat(MaskingUtils.maskPan(null)).isNull();
    }

    @Test
    void masksByFieldId() {
        assertThat(MaskingUtils.maskValue("2", "5555555555554444")).isEqualTo("555555******4444");
        assertThat(MaskingUtils.maskValue("34", "5555555555554444")).isEqualTo("555555******4444");
        assertThat(MaskingUtils.maskValue("35", "5555555555554444=2512")).isEqualTo("****");
        assertThat(MaskingUtils.maskValue("36", "015555555555554444=2512")).isEqualTo("****");
        assertThat(MaskingUtils.maskValue("45", "B5555555555554444^TEST")).isEqualTo("****");
        assertThat(MaskingUtils.maskValue("52", "0123456789ABCDEF")).isEqualTo("****");
        assertThat(MaskingUtils.maskValue("4", "000000001000")).isEqualTo("000000001000");
        assertThat(MaskingUtils.maskValue("2", null)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "48/92, 123, ****",
            "48/10, 0123456789ABCDEF, ****",
            "48/43, UCAFSECRET0123, ****",
            "48/66, AUTHDATA4242, ****",
            "102, ACCT1234567890, ACCT******7890",
            "103, ACCT9876543210, ACCT******3210",
            "14, 2712, ****",
            "55/5F24, 271231, ****",
            "55/5A, 5555555555554444, 555555******4444",
            "55/5a, 5555555555554444, 555555******4444",
            "55/57, 5555555555554444D2512, ****",
            "55/9F6B, 5555555555554444D2512, ****",
            "55/5F20, CARDHOLDER/TEST, ****",
            "92, 123, 123",
            "48/35, HELLO, HELLO",
            "55/9F02, 000000001000, 000000001000"
    })
    void masksSubelementsByPath(String path, String value, String expected) {
        assertThat(MaskingUtils.maskValue(path, value)).isEqualTo(expected);
        assertThat(MaskingUtils.isSensitive(path)).isEqualTo(!expected.equals(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"46", "47", "56", "57", "58", "59", "60", "61", "62", "63", "105", "111", "112", "113", "119", "120",
            "123", "124", "125", "126", "127"})
    void opaqueAndReservedDataElementsAreFullyMasked(String path) {
        assertThat(MaskingUtils.isSensitive(path)).isTrue();
        assertThat(MaskingUtils.maskValue(path, "OPAQUE NETWORK DATA")).isEqualTo("****");
    }

    /**
     * The reference list must agree with every shipped definition file: a path declared {@code PARTIAL} or
     * {@code FULL} by any network is masked (the strongest declaration wins), a path every network declares
     * {@code NONE} is printed as is.
     */
    @Test
    void agreesWithEveryShippedDefinition() throws IOException {
        Map<String, Sensitivity> strongest = new TreeMap<>();
        for (String resource : List.of("json/iso8583-generic-definitions.json",
                "json/iso8583-mastercard-definitions.json")) {
            JsonNode root;
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
                root = new ObjectMapper().readTree(in);
            }
            for (JsonNode field : root.get("fields")) {
                String id = field.get("id").asText();
                strongest.merge(id, Sensitivity.valueOf(field.get("sensitivity").asText()), MaskingUtilsTest::max);
                for (JsonNode sub : field.path("subelements")) {
                    strongest.merge(id + "/" + sub.get("id").asText(),
                            Sensitivity.valueOf(sub.get("sensitivity").asText()), MaskingUtilsTest::max);
                }
            }
        }
        String value = "5555555555554444";

        assertThat(strongest).containsKeys("46", "47", "60", "112", "127", "48/92", "48/TCC", "55/5A");
        strongest.forEach((path, sensitivity) -> {
            assertThat(MaskingUtils.isSensitive(path)).as(path).isEqualTo(sensitivity != Sensitivity.NONE);
            assertThat(MaskingUtils.maskValue(path, value)).as(path).isEqualTo(sensitivity.mask(value));
        });
    }

    private static Sensitivity max(Sensitivity a, Sensitivity b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    @Test
    void maskFully() {
        assertThat(MaskingUtils.maskFully("anything")).isEqualTo("****");
        assertThat(MaskingUtils.maskFully(null)).isNull();
        assertThat(MaskingUtils.isSensitive(null)).isFalse();
    }
}
