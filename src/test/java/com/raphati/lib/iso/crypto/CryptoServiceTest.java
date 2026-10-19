package com.raphati.lib.iso.crypto;

import com.raphati.lib.iso.config.IsoParserConfig;
import com.raphati.lib.iso.exception.CryptoException;
import com.raphati.lib.iso.exception.IsoParserException;
import com.raphati.lib.iso.model.DEModel;
import com.raphati.lib.iso.type.Sensitivity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoServiceTest {

    private static final String PAN = "5555555555554444";

    @ParameterizedTest
    @ValueSource(ints = {128, 192, 256})
    void roundTripWithEveryKeySize(int bits) {
        byte[] key = CryptoService.generateKey(bits);
        assertThat(key).hasSize(bits / 8);
        CryptoService crypto = new CryptoService(key);

        String token = crypto.encrypt(PAN);

        assertThat(token).doesNotContain(PAN);
        assertThat(crypto.decrypt(token)).isEqualTo(PAN);
    }

    @Test
    void samePlaintextGivesDifferentTokens() {
        CryptoService crypto = new CryptoService(CryptoService.generateKey(256));

        assertThat(crypto.encrypt(PAN)).isNotEqualTo(crypto.encrypt(PAN));
    }

    @Test
    void tamperedCiphertextFails() {
        CryptoService crypto = new CryptoService(CryptoService.generateKey(256));
        byte[] data = Base64.getDecoder().decode(crypto.encrypt(PAN));
        data[data.length - 5] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(data);

        assertThatThrownBy(() -> crypto.decrypt(tampered))
                .isInstanceOf(CryptoException.class)
                .hasMessage("Decryption failed: data was tampered with or the key/AAD is wrong");
    }

    @Test
    void wrongKeyFails() {
        String token = new CryptoService(CryptoService.generateKey(128)).encrypt(PAN);
        CryptoService other = new CryptoService(CryptoService.generateKey(128));

        assertThatThrownBy(() -> other.decrypt(token)).isInstanceOf(CryptoException.class);
    }

    @Test
    void wrongAadFails() {
        CryptoService crypto = new CryptoService(CryptoService.generateKey(256));
        String token = crypto.encrypt(PAN, "2");

        assertThat(crypto.decrypt(token, "2")).isEqualTo(PAN);
        assertThatThrownBy(() -> crypto.decrypt(token, "35")).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> crypto.decrypt(token)).isInstanceOf(CryptoException.class);
    }

    @Test
    void malformedTokensFail() {
        CryptoService crypto = new CryptoService(CryptoService.generateKey(128));

        assertThatThrownBy(() -> crypto.decrypt("not base64!")).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> crypto.decrypt(Base64.getEncoder().encodeToString(new byte[10])))
                .isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> crypto.decrypt(null)).isInstanceOf(CryptoException.class);
    }

    @Test
    void invalidKeysAreRejected() {
        assertThatThrownBy(() -> new CryptoService(new byte[15])).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> new CryptoService(new byte[0])).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> new CryptoService(null)).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> CryptoService.generateKey(64)).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> IsoParserConfig.builder().encryptionKey(new byte[15]))
                .isInstanceOf(IsoParserException.class);
    }

    @Test
    void fieldRoundTripUsesFieldIdAsAad() {
        CryptoService crypto = new CryptoService(CryptoService.generateKey(256));
        DEModel de2 = new DEModel("2", "Primary Account Number (PAN)", PAN, PAN, null);

        DEModel encrypted = crypto.encryptField(de2);

        assertThat(encrypted.rawValue()).isNotEqualTo(PAN);
        assertThat(encrypted.decodedValue()).isNotEqualTo(PAN);
        assertThat(crypto.decrypt(encrypted.rawValue(), "2#raw")).isEqualTo(PAN);
        assertThat(crypto.decrypt(encrypted.decodedValue(), "2#decoded")).isEqualTo(PAN);
        assertThat(crypto.decryptField(encrypted)).isEqualTo(de2);
    }

    @Test
    void rawAndDecodedTokensCannotBeSwapped() {
        CryptoService crypto = new CryptoService(CryptoService.generateKey(256));
        DEModel de41 = new DEModel("41", "Terminal", "TERM0001  ", "TERM0001", null, null, null, Sensitivity.NONE);
        DEModel encrypted = crypto.encryptField(de41);
        DEModel swapped = new DEModel("41", "Terminal", encrypted.decodedValue(), encrypted.rawValue(), null, null,
                null, Sensitivity.NONE);

        assertThat(crypto.decryptField(encrypted)).isEqualTo(de41);
        assertThatThrownBy(() -> crypto.decryptField(swapped))
                .isInstanceOf(CryptoException.class)
                .hasMessage("Decryption failed: data was tampered with or the key/AAD is wrong");
    }

    @Test
    void nullSubelementIsPreservedAndSkipped() {
        CryptoService crypto = new CryptoService(CryptoService.generateKey(256));
        Map<String, DEModel> subs = new LinkedHashMap<>();
        subs.put("42", new DEModel("42", "ECI", "0102105", "0102105", null));
        subs.put("92", null);
        DEModel de48 = new DEModel("48", "Additional Data", "42070102105", "42070102105", subs);

        DEModel encrypted = crypto.encryptField(de48);

        assertThat(encrypted.subelements()).containsKeys("42", "92");
        assertThat(encrypted.subelements().get("92")).isNull();
        assertThat(encrypted.subelements().get("42").rawValue()).isNotEqualTo("0102105");
        assertThat(crypto.decryptField(encrypted)).isEqualTo(de48);
    }

    @Test
    void compositeFieldEncryptsSubelementsWithTheirPathAsAad() {
        CryptoService crypto = new CryptoService(CryptoService.generateKey(256));
        Map<String, DEModel> subs = new LinkedHashMap<>();
        subs.put("TCC", new DEModel("TCC", "Transaction Category Code", "R", "R", null));
        subs.put("10", new DEModel("10", "Encrypted PIN Block Key", "0123456789ABCDEF", "0123456789ABCDEF", null));
        subs.put("92", new DEModel("92", "CVC 2", "987", "987", null));
        String raw = "R10160123456789ABCDEF9203987";
        DEModel de48 = new DEModel("48", "Additional Data - Private Use", raw, raw, subs, List.of("an error"));

        DEModel encrypted = crypto.encryptField(de48);

        assertThat(encrypted.subelements().keySet()).containsExactly("TCC", "10", "92");
        assertThat(encrypted.subelements().values()).allSatisfy(sub -> {
            assertThat(sub.rawValue()).isNotEqualTo(subs.get(sub.id()).rawValue());
            assertThat(sub.decodedValue()).isNotEqualTo(subs.get(sub.id()).decodedValue());
        });
        assertThat(encrypted.rawValue()).isNotEqualTo(raw);
        assertThat(encrypted.errors()).containsExactly("an error");
        assertThat(crypto.decrypt(encrypted.subelements().get("92").rawValue(), "48/92#raw")).isEqualTo("987");
        assertThatThrownBy(() -> crypto.decrypt(encrypted.subelements().get("92").rawValue(), "48/10#raw"))
                .isInstanceOf(CryptoException.class);
        assertThat(crypto.decryptField(encrypted)).isEqualTo(de48);
    }

    @Test
    void tokenCarriesVersionAndKeyId() {
        CryptoService crypto = new CryptoService(CryptoService.generateKey(256), "pan-key-v3");

        String token = crypto.encrypt(PAN, "2");
        byte[] data = Base64.getDecoder().decode(token);

        assertThat(data[0]).isEqualTo((byte) 1);
        assertThat(data[1]).isEqualTo((byte) "pan-key-v3".length());
        assertThat(CryptoService.keyIdOf(token)).isEqualTo("pan-key-v3");
        assertThat(crypto.keyId()).isEqualTo("pan-key-v3");
        assertThat(crypto.decrypt(token, "2")).isEqualTo(PAN);
    }

    @Test
    void derivedKeyIdIsStablePerKeyAndDiffersBetweenKeys() {
        byte[] key = CryptoService.generateKey(128);

        String keyId = new CryptoService(key).keyId();

        assertThat(keyId).matches("[0-9a-f]{8}").isEqualTo(new CryptoService(key).keyId());
        assertThat(new CryptoService(CryptoService.generateKey(128)).keyId()).isNotEqualTo(keyId);
        assertThat(HexFormat.of().formatHex(key)).doesNotContain(keyId);
    }

    @Test
    void rotationRoutesTokensByKeyId() {
        byte[] oldKey = CryptoService.generateKey(256);
        byte[] newKey = CryptoService.generateKey(256);
        Map<String, CryptoService> keyring = Map.of(
                "v1", new CryptoService(oldKey, "v1"), "v2", new CryptoService(newKey, "v2"));
        String oldToken = keyring.get("v1").encrypt(PAN);

        assertThat(keyring.get(CryptoService.keyIdOf(oldToken)).decrypt(oldToken)).isEqualTo(PAN);
        assertThatThrownBy(() -> keyring.get("v2").decrypt(oldToken))
                .isInstanceOf(CryptoException.class)
                .hasMessage("Token was encrypted with key id v1, not v2");
    }

    @Test
    void tamperedHeaderFails() {
        byte[] key = CryptoService.generateKey(256);
        CryptoService crypto = new CryptoService(key, "k1");
        byte[] data = Base64.getDecoder().decode(crypto.encrypt(PAN));
        byte[] otherVersion = data.clone();
        otherVersion[0] = 2;
        byte[] otherId = data.clone();
        otherId[3] = '2';

        assertThatThrownBy(() -> crypto.decrypt(Base64.getEncoder().encodeToString(otherVersion)))
                .isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> crypto.decrypt(Base64.getEncoder().encodeToString(otherId)))
                .isInstanceOf(CryptoException.class);
        // same key under the forged id: the key id is authenticated, so GCM rejects it
        assertThatThrownBy(() -> new CryptoService(key, "k2").decrypt(Base64.getEncoder().encodeToString(otherId)))
                .isInstanceOf(CryptoException.class)
                .hasMessage("Decryption failed: data was tampered with or the key/AAD is wrong");
    }

    @Test
    void invalidKeyIdsAreRejected() {
        byte[] key = CryptoService.generateKey(128);

        assertThatThrownBy(() -> new CryptoService(key, "")).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> new CryptoService(key, "has space")).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> new CryptoService(key, "x".repeat(65))).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> new CryptoService(key, "chave-\u00e7")).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> CryptoService.keyIdOf("not base64!")).isInstanceOf(CryptoException.class);
    }

    @Test
    void parsedSubelementIsEncryptedWithItsPath() {
        CryptoService crypto = new CryptoService(CryptoService.generateKey(256));
        DEModel cvc2 = new DEModel("92", "CVC 2", "987", "987", null, null, "48/92", Sensitivity.FULL);

        DEModel encrypted = crypto.encryptField(cvc2);

        assertThat(encrypted.path()).isEqualTo("48/92");
        assertThat(encrypted.sensitivity()).isEqualTo(Sensitivity.FULL);
        assertThat(crypto.decrypt(encrypted.rawValue(), "48/92#raw")).isEqualTo("987");
        assertThat(crypto.decryptField(encrypted)).isEqualTo(cvc2);
    }

    @Test
    void fromConfig() {
        byte[] key = CryptoService.generateKey(256);
        CryptoService fromConfig = CryptoService.fromConfig(IsoParserConfig.builder().encryptionKey(key).build());

        assertThat(new CryptoService(key).decrypt(fromConfig.encrypt(PAN))).isEqualTo(PAN);
        assertThatThrownBy(() -> CryptoService.fromConfig(IsoParserConfig.defaults()))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void keyNeverAppearsInToString() {
        byte[] key = CryptoService.generateKey(256);
        IsoParserConfig config = IsoParserConfig.builder().encryptionKey(key).build();

        String text = config.toString();

        assertThat(text).contains("encryptionKey=***")
                .doesNotContain(Base64.getEncoder().encodeToString(key))
                .doesNotContainIgnoringCase(HexFormat.of().formatHex(key));
        assertThat(IsoParserConfig.defaults().toString()).contains("encryptionKey=<none>");
        assertThat(new CryptoService(key).toString()).isEqualTo("CryptoService[AES-GCM]");
    }

    @Test
    void configKeyIsDefensivelyCopied() {
        byte[] key = CryptoService.generateKey(128);
        IsoParserConfig config = IsoParserConfig.builder().encryptionKey(key).build();
        byte[] original = key.clone();

        key[0] ^= 0x01;
        config.encryptionKey().orElseThrow()[1] ^= 0x01;

        assertThat(config.encryptionKey()).hasValueSatisfying(k -> assertThat(k).isEqualTo(original));
    }
}
