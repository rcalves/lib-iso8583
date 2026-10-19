package com.raphati.lib.iso.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raphati.lib.iso.config.IsoParserConfig;
import com.raphati.lib.iso.exception.IsoParserException;
import com.raphati.lib.iso.model.FieldDefinitionModel;
import com.raphati.lib.iso.type.CardNetworkType;
import com.raphati.lib.iso.type.LengthType;
import com.raphati.lib.iso.type.Sensitivity;
import com.raphati.lib.iso.type.SubelementFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IsoDefinitionLoaderTest {

    private final IsoDefinitionLoader loader = new IsoDefinitionLoader();

    @ParameterizedTest
    @EnumSource(CardNetworkType.class)
    void loadsAll128Definitions(CardNetworkType network) {
        Map<String, FieldDefinitionModel> defs = loader.load(network);

        List<String> expectedIds = IntStream.rangeClosed(1, 128).mapToObj(String::valueOf).toList();
        assertThat(defs.keySet()).containsExactlyElementsOf(expectedIds);

        FieldDefinitionModel de2 = defs.get("2");
        assertThat(de2.type()).isEqualTo("n");
        assertThat(de2.lengthType()).isEqualTo(LengthType.LLVAR);
        assertThat(de2.maxLength()).isEqualTo(19);

        FieldDefinitionModel de55 = defs.get("55");
        assertThat(de55.subelementFormat()).isEqualTo(SubelementFormat.BER_TLV);
        assertThat(de55.subelements()).extracting(FieldDefinitionModel::id).contains("9F02");
    }

    @Test
    void de48OffsetsDifferPerNetwork() {
        FieldDefinitionModel generic = loader.find(CardNetworkType.GENERIC, "48").orElseThrow();
        assertThat(generic.subelementFormat()).isEqualTo(SubelementFormat.TAG_LENGTH_VALUE);
        assertThat(generic.subelementOffset()).isZero();
        assertThat(generic.subelementTagLength()).isEqualTo(2);
        assertThat(generic.subelementLengthLength()).isEqualTo(2);

        FieldDefinitionModel mastercard = loader.find(CardNetworkType.MASTERCARD, "48").orElseThrow();
        assertThat(mastercard.subelementOffset()).isEqualTo(1);
        assertThat(mastercard.subelements()).extracting(FieldDefinitionModel::id).contains("42");
    }

    @Test
    void shippedGenericDe48SubelementsAreTwoDigitTags() {
        FieldDefinitionModel generic = loader.find(CardNetworkType.GENERIC, "48").orElseThrow();

        assertThat(generic.subelements()).extracting(FieldDefinitionModel::id)
                .containsExactly("01", "02", "03", "04", "05", "06", "07", "08", "09", "10");
        assertThat(generic.subelements()).extracting(FieldDefinitionModel::description)
                .allMatch(d -> d.startsWith("Additional data subelement "));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "TAG_LENGTH_VALUE|[{\"id\": \"0\"}, {\"id\": \"A\"}]|Invalid subelement id",
            "TAG_LENGTH_VALUE|[{\"id\": \"01\"}, {\"id\": \"01\"}]|Duplicate subelement id",
            "TAG_LENGTH_VALUE|[{\"description\": \"no id\"}]|Invalid subelement id",
            "BER_TLV|[{\"id\": \"9F0\"}]|Invalid subelement id",
            "BER_TLV|[{\"id\": \"ZZ\"}]|Invalid subelement id",
            "BER_TLV|[{\"id\": \"9F02\"}, {\"id\": \"9f02\"}]|Duplicate subelement id"
    })
    void invalidSubelementDefinitionsThrow(String format, String subelements, String message, @TempDir Path dir)
            throws IOException {
        Path file = dir.resolve("subs.json");
        Files.writeString(file, """
                {"network": "GENERIC", "fields": [{"id": "48", "subelementFormat": "%s", "subelementTagLength": 2,
                  "subelementLengthLength": 2, "subelements": %s}]}
                """.formatted(format, subelements));
        IsoDefinitionLoader custom = new IsoDefinitionLoader(IsoParserConfig.builder()
                .definitionPath(CardNetworkType.GENERIC, file.toString())
                .build());

        assertThatThrownBy(() -> custom.load(CardNetworkType.GENERIC))
                .isInstanceOf(IsoParserException.class)
                .hasMessageContaining(message)
                .hasMessageContaining("DE48");
    }

    @Test
    void offsetPrefixCanBeDeclaredOnlyWhenTheFieldHasAnOffset(@TempDir Path dir) throws IOException {
        String template = """
                {"network": "GENERIC", "fields": [{"id": "48", "subelementFormat": "TAG_LENGTH_VALUE",
                  "subelementTagLength": 2, "subelementLengthLength": 2, "subelementOffset": %d,
                  "subelements": [{"id": "TCC", "sensitivity": "NONE"}, {"id": "01"}]}]}
                """;
        Path withOffset = dir.resolve("offset.json");
        Files.writeString(withOffset, template.formatted(1));
        Path withoutOffset = dir.resolve("no-offset.json");
        Files.writeString(withoutOffset, template.formatted(0));
        IsoDefinitionLoader custom = new IsoDefinitionLoader(IsoParserConfig.builder()
                .definitionPath(CardNetworkType.GENERIC, withOffset.toString())
                .build());

        assertThat(custom.find(CardNetworkType.GENERIC, "48").orElseThrow().subelements())
                .extracting(FieldDefinitionModel::id).containsExactly("TCC", "01");
        assertThatThrownBy(() -> new IsoDefinitionLoader(IsoParserConfig.builder()
                .definitionPath(CardNetworkType.GENERIC, withoutOffset.toString()).build())
                .load(CardNetworkType.GENERIC))
                .isInstanceOf(IsoParserException.class)
                .hasMessageContaining("Invalid subelement id in DE48")
                .hasMessageContaining("TCC");
    }

    @Test
    void shippedMastercardDeclaresTheTccPrefix() {
        FieldDefinitionModel tcc = loader.find(CardNetworkType.MASTERCARD, "48").orElseThrow().subelements().stream()
                .filter(s -> s.id().equals("TCC")).findFirst().orElseThrow();

        assertThat(tcc.description()).isEqualTo("Transaction Category Code");
        assertThat(tcc.sensitivity()).isEqualTo(Sensitivity.NONE);
    }

    @ParameterizedTest
    @CsvSource({
            "GENERIC, 2, PARTIAL", "GENERIC, 34, PARTIAL", "GENERIC, 102, PARTIAL", "GENERIC, 103, PARTIAL",
            "GENERIC, 14, FULL", "GENERIC, 35, FULL", "GENERIC, 36, FULL", "GENERIC, 45, FULL", "GENERIC, 52, FULL",
            "GENERIC, 120, FULL", "GENERIC, 4, NONE", "GENERIC, 48, NONE", "GENERIC, 55, NONE",
            "MASTERCARD, 102, PARTIAL", "MASTERCARD, 14, FULL", "MASTERCARD, 127, FULL", "MASTERCARD, 61, NONE"
    })
    void shippedDefinitionsDeclareSensitivity(CardNetworkType network, String id, Sensitivity expected) {
        assertThat(loader.find(network, id)).map(FieldDefinitionModel::sensitivity).contains(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "GENERIC, 48, 10, NONE", "GENERIC, 48, 01, NONE",
            "MASTERCARD, 48, 10, FULL", "MASTERCARD, 48, 43, FULL", "MASTERCARD, 48, 66, FULL",
            "MASTERCARD, 48, 92, FULL", "MASTERCARD, 48, 42, NONE",
            "GENERIC, 55, 5A, PARTIAL", "MASTERCARD, 55, 5A, PARTIAL", "MASTERCARD, 55, 57, FULL",
            "GENERIC, 55, 56, FULL", "GENERIC, 55, 5F20, FULL", "GENERIC, 55, 5F24, FULL", "GENERIC, 55, 99, FULL",
            "GENERIC, 55, 9F1F, FULL", "GENERIC, 55, 9F20, FULL", "GENERIC, 55, 9F6B, FULL", "GENERIC, 55, 9F02, NONE"
    })
    void shippedSubelementsDeclareSensitivity(CardNetworkType network, String field, String tag, Sensitivity expected) {
        FieldDefinitionModel sub = loader.find(network, field).orElseThrow().subelements().stream()
                .filter(s -> s.id().equals(tag)).findFirst().orElseThrow();

        assertThat(sub.sensitivity()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"json/iso8583-generic-definitions.json", "json/iso8583-mastercard-definitions.json"})
    void everyShippedDefinitionDeclaresItsSensitivity(String resource) throws IOException {
        JsonNode root;
        try (var in = getClass().getClassLoader().getResourceAsStream(resource)) {
            root = new ObjectMapper().readTree(in);
        }
        List<String> undeclared = new java.util.ArrayList<>();
        for (JsonNode field : root.get("fields")) {
            if (!field.hasNonNull("sensitivity")) {
                undeclared.add(field.get("id").asText());
            }
            for (JsonNode sub : field.path("subelements")) {
                if (!sub.hasNonNull("sensitivity")) {
                    undeclared.add(field.get("id").asText() + "/" + sub.get("id").asText());
                }
            }
        }

        assertThat(undeclared).as(resource).isEmpty();
    }

    @Test
    void undeclaredSensitivityIsFullAndInvalidOneThrows(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sensitivity.json");
        Files.writeString(file, """
                {"network": "GENERIC", "fields": [{"id": "4"}, {"id": "11", "sensitivity": "NONE"}]}
                """);
        Path invalid = dir.resolve("invalid.json");
        Files.writeString(invalid, """
                {"network": "MASTERCARD", "fields": [{"id": "4", "sensitivity": "SOMETIMES"}]}
                """);
        IsoDefinitionLoader custom = new IsoDefinitionLoader(IsoParserConfig.builder()
                .definitionPath(CardNetworkType.GENERIC, file.toString())
                .definitionPath(CardNetworkType.MASTERCARD, invalid.toString())
                .build());

        assertThat(custom.find(CardNetworkType.GENERIC, "4")).map(FieldDefinitionModel::sensitivity)
                .contains(Sensitivity.FULL);
        assertThat(custom.find(CardNetworkType.GENERIC, "11")).map(FieldDefinitionModel::sensitivity)
                .contains(Sensitivity.NONE);
        assertThatThrownBy(() -> custom.load(CardNetworkType.MASTERCARD)).isInstanceOf(IsoParserException.class);
    }

    @Test
    void findUnknownIdIsEmpty() {
        assertThat(loader.find(CardNetworkType.GENERIC, "999")).isEmpty();
    }

    @Test
    void loadIsCached() {
        assertThat(loader.load(CardNetworkType.MASTERCARD)).isSameAs(loader.load(CardNetworkType.MASTERCARD));
    }

    @Test
    void missingResourceThrows() {
        IsoParserConfig config = IsoParserConfig.builder()
                .definitionPath(CardNetworkType.GENERIC, "classpath:json/nope.json")
                .build();
        IsoDefinitionLoader custom = new IsoDefinitionLoader(config);

        assertThatThrownBy(() -> custom.load(CardNetworkType.GENERIC))
                .isInstanceOf(IsoParserException.class)
                .hasMessageContaining("classpath:json/nope.json");
    }

    @Test
    void loadsMinimalFileFromFilesystem(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("defs.json");
        Files.writeString(file, """
                {"network": "GENERIC", "fields": [
                  {"id": "4", "description": "Amount", "type": "n", "lengthType": "FIXED", "length": 12, "maxLength": 12},
                  {"id": "2", "description": "PAN", "type": "n", "lengthType": "LLVAR", "length": 0, "maxLength": 19}
                ]}
                """);
        IsoDefinitionLoader custom = new IsoDefinitionLoader(IsoParserConfig.builder()
                .definitionPath(CardNetworkType.GENERIC, file.toString())
                .build());

        Map<String, FieldDefinitionModel> defs = custom.load(CardNetworkType.GENERIC);

        assertThat(defs.keySet()).containsExactly("2", "4");
        assertThat(defs.get("4").subelementFormat()).isEqualTo(SubelementFormat.NONE);
        assertThat(defs.get("4").subelements()).isEmpty();
    }

    @Test
    void duplicateIdsThrow(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("dup.json");
        Files.writeString(file, """
                {"network": "GENERIC", "fields": [{"id": "2"}, {"id": "2"}]}
                """);
        IsoDefinitionLoader custom = new IsoDefinitionLoader(IsoParserConfig.builder()
                .definitionPath(CardNetworkType.GENERIC, file.toString())
                .build());

        assertThatThrownBy(() -> custom.load(CardNetworkType.GENERIC))
                .isInstanceOf(IsoParserException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void networkMismatchAndBadJsonThrow(@TempDir Path dir) throws IOException {
        Path mismatch = dir.resolve("mismatch.json");
        Files.writeString(mismatch, "{\"network\": \"GENERIC\", \"fields\": []}");
        Path broken = dir.resolve("broken.json");
        Files.writeString(broken, "{\"network\": ");
        IsoDefinitionLoader custom = new IsoDefinitionLoader(IsoParserConfig.builder()
                .definitionPath(CardNetworkType.MASTERCARD, mismatch.toString())
                .definitionPath(CardNetworkType.GENERIC, broken.toString())
                .build());

        assertThatThrownBy(() -> custom.load(CardNetworkType.MASTERCARD)).isInstanceOf(IsoParserException.class);
        assertThatThrownBy(() -> custom.load(CardNetworkType.GENERIC)).isInstanceOf(IsoParserException.class);
    }
}
