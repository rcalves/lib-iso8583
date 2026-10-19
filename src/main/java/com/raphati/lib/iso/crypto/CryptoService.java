package com.raphati.lib.iso.crypto;

import com.raphati.lib.iso.config.IsoParserConfig;
import com.raphati.lib.iso.exception.CryptoException;
import com.raphati.lib.iso.model.DEModel;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BinaryOperator;

/**
 * AES-GCM encryption of sensitive values (e.g. a PAN).
 * <p>
 * Tokens are {@code Base64(version || keyIdLength || keyId || iv || ciphertext+tag)}: format version {@code 1} (one
 * byte), the key identifier (one length byte and 1..64 printable ASCII characters), a random 12-byte IV and the
 * ciphertext with a 128-bit tag. Version and key id are authenticated together with the caller's AAD, and
 * {@link #keyIdOf(String)} reads the key id without decrypting, so a token can be routed to the key that produced it
 * after a rotation. The key always comes from the caller or the config; it is never hardcoded, logged or included in
 * exception messages.
 * <p>
 * Random 96-bit IVs are safe for at most 2<sup>32</sup> encryptions per key (NIST SP 800-38D, 8.3); rotate the key
 * well before that.
 */
public final class CryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_KEY_ID_LENGTH = 64;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] KEY_ID_LABEL = "lib-iso-parser key id".getBytes(StandardCharsets.US_ASCII);
    private static final String DECRYPTION_FAILED =
            "Decryption failed: data was tampered with or the key/AAD is wrong";
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Role markers appended to the element path in the AAD of {@link #encryptField(DEModel)}. */
    private static final String RAW_ROLE = "#raw";
    private static final String DECODED_ROLE = "#decoded";

    private final SecretKey key;
    private final String keyId;
    /** {@code version || keyIdLength || keyId}, the token prefix, also authenticated as AAD. */
    private final byte[] header;

    /**
     * Creates a service whose key id is derived from the key (the first 4 bytes of an HMAC-SHA256 of a fixed label,
     * as 8 hex characters), so tokens of different keys are distinguishable without revealing the key.
     *
     * @param key AES key of 16, 24 or 32 bytes (copied)
     * @throws CryptoException if the key is null or has an invalid length
     */
    public CryptoService(byte[] key) {
        this(key, null);
    }

    /**
     * @param key   AES key of 16, 24 or 32 bytes (copied)
     * @param keyId identifier written into every token, e.g. the key version in your secret store (1..64 printable
     *              ASCII characters); {@code null} derives one from the key
     * @throws CryptoException if the key or the key id is invalid
     */
    public CryptoService(byte[] key, String keyId) {
        if (key == null || (key.length != 16 && key.length != 24 && key.length != 32)) {
            throw new CryptoException("AES key must be 16, 24 or 32 bytes");
        }
        if (keyId != null && !isValidKeyId(keyId)) {
            throw new CryptoException("Key id must be 1 to " + MAX_KEY_ID_LENGTH + " printable ASCII characters");
        }
        byte[] copy = key.clone();
        try {
            this.key = new SecretKeySpec(copy, "AES");
            this.keyId = keyId != null ? keyId : derivedKeyId(copy);
        } finally {
            Arrays.fill(copy, (byte) 0);
        }
        byte[] id = this.keyId.getBytes(StandardCharsets.US_ASCII);
        this.header = ByteBuffer.allocate(2 + id.length).put((byte) FORMAT_VERSION).put((byte) id.length).put(id).array();
    }

    private static boolean isValidKeyId(String keyId) {
        return !keyId.isEmpty() && keyId.length() <= MAX_KEY_ID_LENGTH
                && keyId.chars().allMatch(c -> c >= 0x21 && c <= 0x7E);
    }

    private static String derivedKeyId(byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(KEY_ID_LABEL), 0, 4);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Unable to derive the key id", e);
        }
    }

    /**
     * @return the key id written into the tokens of this service
     */
    public String keyId() {
        return keyId;
    }

    /**
     * Reads the key id of a token without decrypting it, to pick the key that can decrypt it.
     *
     * @throws CryptoException if the token is not a version 1 token
     */
    public static String keyIdOf(String token) {
        byte[] data = decodeToken(token);
        return new String(data, 2, data[1], StandardCharsets.US_ASCII);
    }

    /**
     * Decodes a token and checks its header: version 1, a valid key id, and room for the IV and the tag.
     */
    private static byte[] decodeToken(String token) {
        if (token == null) {
            throw new CryptoException(DECRYPTION_FAILED);
        }
        byte[] data;
        try {
            data = Base64.getDecoder().decode(token);
        } catch (IllegalArgumentException e) {
            throw new CryptoException(DECRYPTION_FAILED, e);
        }
        if (data.length < 2 || data[0] != FORMAT_VERSION) {
            throw new CryptoException(DECRYPTION_FAILED);
        }
        int idLength = data[1];
        if (idLength < 1 || idLength > MAX_KEY_ID_LENGTH || data.length < 2 + idLength + IV_LENGTH + TAG_BITS / 8
                || !isValidKeyId(new String(data, 2, idLength, StandardCharsets.US_ASCII))) {
            throw new CryptoException(DECRYPTION_FAILED);
        }
        return data;
    }

    /**
     * @throws CryptoException if the config has no encryption key
     */
    public static CryptoService fromConfig(IsoParserConfig config) {
        byte[] key = config.encryptionKey()
                .orElseThrow(() -> new CryptoException("No encryption key configured"));
        try {
            return new CryptoService(key);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /**
     * Generates a random AES key of 128, 192 or 256 bits.
     */
    public static byte[] generateKey(int bits) {
        if (bits != 128 && bits != 192 && bits != 256) {
            throw new CryptoException("AES key size must be 128, 192 or 256 bits");
        }
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(bits, RANDOM);
            return generator.generateKey().getEncoded();
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Unable to generate AES key", e);
        }
    }

    public String encrypt(String plaintext) {
        return encrypt(plaintext, null);
    }

    /**
     * @param aad optional additional authenticated data (may be null)
     */
    public String encrypt(String plaintext, String aad) {
        if (plaintext == null) {
            throw new CryptoException("Plaintext must not be null");
        }
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(header);
            if (aad != null) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(header.length + iv.length + ciphertext.length)
                    .put(header).put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Encryption failed", e);
        }
    }

    public String decrypt(String token) {
        return decrypt(token, null);
    }

    /**
     * @param aad the same additional authenticated data used to encrypt (may be null)
     * @throws CryptoException if the token is malformed, was tampered with, was produced with another key id, or the
     *                         key/AAD is wrong
     */
    public String decrypt(String token, String aad) {
        byte[] data = decodeToken(token);
        int headerLength = 2 + data[1];
        if (!Arrays.equals(data, 0, headerLength, header, 0, header.length)) {
            throw new CryptoException("Token was encrypted with key id " + keyIdOf(token) + ", not " + keyId);
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, headerLength, IV_LENGTH));
            cipher.updateAAD(header);
            if (aad != null) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }
            int offset = headerLength + IV_LENGTH;
            byte[] plaintext = cipher.doFinal(data, offset, data.length - offset);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new CryptoException(DECRYPTION_FAILED, e);
        }
    }

    /**
     * Encrypts the raw and decoded values of a field and, recursively, of all its subelements. The AAD is the
     * {@link DEModel#path() path} of each element plus the role of the value ({@code "48/92#raw"},
     * {@code "48/92#decoded"}), so an encrypted value cannot be moved to another element, nor the raw and decoded
     * tokens of one element swapped, without failing decryption, and a subelement gets the same AAD whether it is
     * encrypted on its own or through its parent. A {@code null} subelement is kept as is.
     */
    public DEModel encryptField(DEModel field) {
        return transform(field, this::encrypt);
    }

    /**
     * Reverses {@link #encryptField(DEModel)}.
     */
    public DEModel decryptField(DEModel field) {
        return transform(field, this::decrypt);
    }

    private static DEModel transform(DEModel field, BinaryOperator<String> op) {
        String path = field.path();
        Map<String, DEModel> subelements = new LinkedHashMap<>();
        field.subelements().forEach((tag, sub) -> subelements.put(tag, sub == null ? null : transform(sub, op)));
        return new DEModel(field.id(), field.name(),
                field.rawValue() == null ? null : op.apply(field.rawValue(), path + RAW_ROLE),
                field.decodedValue() == null ? null : op.apply(field.decodedValue(), path + DECODED_ROLE),
                subelements, field.errors(), path, field.sensitivity());
    }

    @Override
    public String toString() {
        return "CryptoService[AES-GCM]";
    }
}
