package com.raphati.lib.iso.model;

import com.raphati.lib.iso.type.Sensitivity;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DEModelTest {

    private static final String PAN = "5555555555554444";

    private static DEModel leaf(String id, String value, Sensitivity sensitivity) {
        return new DEModel(id, "test", value, value, null, null, null, sensitivity);
    }

    private static DEModel de48WithCvc2() {
        Map<String, DEModel> subs = new LinkedHashMap<>();
        subs.put("TCC", leaf("TCC", "R", Sensitivity.NONE));
        subs.put("42", leaf("42", "0102105", Sensitivity.NONE));
        subs.put("92", leaf("92", "987", Sensitivity.FULL));
        return new DEModel("48", "Additional Data", "R4207010210592039", "R4207010210592039", subs, null, null,
                Sensitivity.NONE);
    }

    @Test
    void fiveArgConstructorHasNoErrorsAndIsMaskedByDefault() {
        DEModel field = new DEModel("4", "Amount", "000000001000", "000000001000", null);

        assertThat(field.errors()).isEmpty();
        assertThat(field.hasErrors()).isFalse();
        assertThat(field.subelements()).isEmpty();
        assertThat(field.path()).isEqualTo("4");
        assertThat(field.sensitivity()).isEqualTo(Sensitivity.FULL);
        assertThat(field.toString()).doesNotContain("000000001000").contains("rawValue=****");
        assertThat(field.rawValue()).isEqualTo("000000001000");
    }

    @Test
    void masksBySensitivity() {
        assertThat(leaf("34", PAN, Sensitivity.PARTIAL).toString()).doesNotContain(PAN).contains("555555******4444");
        assertThat(leaf("2", "1234567890", Sensitivity.PARTIAL).toString()).contains("rawValue=******7890");
        assertThat(leaf("36", "015555555555554444=2512", Sensitivity.FULL).toString())
                .doesNotContain(PAN).contains("rawValue=****");
        assertThat(leaf("4", "000000001000", Sensitivity.NONE).toString()).contains("rawValue=000000001000");
    }

    @Test
    void parentCarryingASensitiveSubelementIsMasked() {
        String text = de48WithCvc2().toString();

        assertThat(text).doesNotContain("987").doesNotContain("R4207010210592039")
                .contains("rawValue=****")
                .contains("92=DEModel[id=92, name=test, rawValue=****")
                .contains("42=DEModel[id=42, name=test, rawValue=0102105");
    }

    @Test
    void subelementIsMaskedOnItsOwnAndInMapViews() {
        DEModel de48 = de48WithCvc2();
        DEModel cvc2 = de48.subelements().get("92");

        assertThat(cvc2.toString()).doesNotContain("987").contains("rawValue=****");
        assertThat(de48.subelements().toString()).doesNotContain("987").contains("rawValue=0102105");
        assertThat(de48.subelements().values().toString()).doesNotContain("987");
        assertThat(de48.subelements().entrySet().toString()).doesNotContain("987");
        assertThat(cvc2.rawValue()).isEqualTo("987");
    }

    @Test
    void subelementPathFollowsItsParent() {
        DEModel de48 = de48WithCvc2();

        assertThat(de48.path()).isEqualTo("48");
        assertThat(de48.subelements().get("92").path()).isEqualTo("48/92");
        assertThat(de48.subelements().get("TCC").path()).isEqualTo("48/TCC");

        Map<String, DEModel> nested = Map.of("01", leaf("01", "X", Sensitivity.NONE));
        DEModel inner = new DEModel("A1", "inner", "0101X", "0101X", nested, null, null, Sensitivity.NONE);
        DEModel outer = new DEModel("124", "outer", "A1050101X", "A1050101X", Map.of("A1", inner), null, null,
                Sensitivity.NONE);
        assertThat(outer.subelements().get("A1").subelements().get("01").path()).isEqualTo("124/A1/01");
    }

    @Test
    void masksEmvPanAndTrack2EquivalentInsideDe55() {
        String track2 = PAN + "D2512";
        Map<String, DEModel> subs = new LinkedHashMap<>();
        subs.put("5A", leaf("5A", PAN, Sensitivity.PARTIAL));
        subs.put("57", leaf("57", track2, Sensitivity.FULL));
        subs.put("9F02", leaf("9F02", "000000001000", Sensitivity.NONE));
        String raw = "5A08" + PAN + "570A" + track2 + "9F0206000000001000";
        DEModel de55 = new DEModel("55", "ICC", raw, raw, subs, null, null, Sensitivity.NONE);

        String text = de55.toString();

        assertThat(text).doesNotContain(PAN).doesNotContain(raw)
                .contains("555555******4444")
                .contains("9F02=DEModel[id=9F02, name=test, rawValue=000000001000");
        assertThat(de55.subelements().get("5A").toString()).doesNotContain(PAN).contains("555555******4444");
    }

    @Test
    void partialParentWithAFullySensitiveSubelementIsFullyMasked() {
        String raw = "ACCT5555555555553739";
        DEModel de102 = new DEModel("102", "Account Identification 1", raw, raw,
                Map.of("92", leaf("92", "3739", Sensitivity.FULL)), null, null, Sensitivity.PARTIAL);

        String text = de102.toString();

        assertThat(text).doesNotContain("3739").contains("rawValue=****, decodedValue=****");
    }

    @Test
    void parentIsMaskedByTheStrongestSensitivityInItsSubtree() {
        DEModel deepFull = new DEModel("A1", "inner", "0103987", "0103987",
                Map.of("01", leaf("01", "987", Sensitivity.FULL)), null, null, Sensitivity.PARTIAL);
        DEModel partialOverDeepFull = new DEModel("124", "outer", "A1070103987XXXXXXXXX", "A1070103987XXXXXXXXX",
                Map.of("A1", deepFull), null, null, Sensitivity.PARTIAL);
        DEModel partialOverPartial = new DEModel("102", "outer", "5A16" + PAN, "5A16" + PAN,
                Map.of("5A", leaf("5A", PAN, Sensitivity.PARTIAL)), null, null, Sensitivity.PARTIAL);
        DEModel noneOverPartial = new DEModel("55", "outer", "5A08" + PAN, "5A08" + PAN,
                Map.of("5A", leaf("5A", PAN, Sensitivity.PARTIAL)), null, null, Sensitivity.NONE);

        assertThat(partialOverDeepFull.toString()).doesNotContain("987").startsWith(
                "DEModel[id=124, name=outer, rawValue=****");
        assertThat(partialOverPartial.toString()).contains("rawValue=5A1655**********4444");
        // a parent that is not sensitive itself does not declare a masking window for its aggregate value
        assertThat(noneOverPartial.toString()).doesNotContain(PAN).contains("id=55, name=outer, rawValue=****");
    }

    @Test
    void nonSensitiveParentWithNonSensitiveSubelementsIsReadable() {
        DEModel de48 = new DEModel("48", "Additional Data", "3505HELLO", "3505HELLO",
                Map.of("35", leaf("35", "HELLO", Sensitivity.NONE)), null, null, Sensitivity.NONE);

        assertThat(de48.containsSensitiveData()).isFalse();
        assertThat(de48.toString()).contains("rawValue=3505HELLO").contains("35=DEModel[id=35, name=test, rawValue=HELLO");
    }

    @Test
    void errorsAloneDoNotMask() {
        DEModel de4 = new DEModel("4", "Amount", "00000000X000", "00000000X000", null,
                List.of("DE4: contains characters not allowed for type n"), null, Sensitivity.NONE);

        assertThat(de4.toString()).contains("rawValue=00000000X000")
                .contains("errors=[DE4: contains characters not allowed for type n]");
    }
}
