package com.raphati.lib.iso.config;

import com.raphati.lib.iso.exception.IsoParserException;
import com.raphati.lib.iso.type.CardNetworkType;
import com.raphati.lib.iso.type.MessageType;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable parser configuration: packager templates, field definitions, charset and an optional AES key.
 * <p>
 * Paths starting with {@code classpath:} are loaded from the classpath; anything else is a filesystem path.
 * The encryption key is never hardcoded: it must be supplied by the caller and is never printed.
 */
public final class IsoParserConfig {

    public static final String DEFAULT_GENERIC_SINGLE_TEMPLATE = "classpath:xml/iso8583-generic-single.xml";
    public static final String DEFAULT_GENERIC_DUAL_TEMPLATE = "classpath:xml/iso8583-generic-dual.xml";
    public static final String DEFAULT_MASTERCARD_SINGLE_TEMPLATE = "classpath:xml/iso8583-mastercard-single.xml";
    public static final String DEFAULT_MASTERCARD_DUAL_TEMPLATE = "classpath:xml/iso8583-mastercard-dual.xml";
    public static final String DEFAULT_GENERIC_DEFINITIONS = "classpath:json/iso8583-generic-definitions.json";
    public static final String DEFAULT_MASTERCARD_DEFINITIONS = "classpath:json/iso8583-mastercard-definitions.json";

    private static final IsoParserConfig DEFAULTS = builder().build();

    private final Map<CardNetworkType, Map<MessageType, String>> packagerTemplates;
    private final Map<CardNetworkType, String> definitionPaths;
    private final Charset charset;
    private final byte[] encryptionKey;

    private IsoParserConfig(Builder builder) {
        Map<CardNetworkType, Map<MessageType, String>> templates = new EnumMap<>(CardNetworkType.class);
        builder.packagerTemplates.forEach((network, byType) ->
                templates.put(network, Collections.unmodifiableMap(new EnumMap<>(byType))));
        this.packagerTemplates = Collections.unmodifiableMap(templates);
        this.definitionPaths = Collections.unmodifiableMap(new EnumMap<>(builder.definitionPaths));
        this.charset = builder.charset;
        this.encryptionKey = builder.encryptionKey == null ? null : builder.encryptionKey.clone();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static IsoParserConfig defaults() {
        return DEFAULTS;
    }

    /**
     * @return the packager template path, or {@code null} if none is registered
     */
    public String packagerTemplate(CardNetworkType network, MessageType type) {
        Map<MessageType, String> byType = packagerTemplates.get(network);
        return byType == null ? null : byType.get(type);
    }

    /**
     * @return the field definition path, or {@code null} if none is registered
     */
    public String definitionPath(CardNetworkType network) {
        return definitionPaths.get(network);
    }

    /**
     * @return the charset {@code IsoParser.parseAscii} uses to turn its input string into wire bytes (ISO-8859-1 by
     * default). It has no effect on hex input, and field values are always decoded by jPOS as ISO-8859-1, so it must
     * be the charset the message bytes were decoded with when the string was produced
     */
    public Charset charset() {
        return charset;
    }

    /**
     * @return a copy of the AES key, if configured
     */
    public Optional<byte[]> encryptionKey() {
        return Optional.ofNullable(encryptionKey).map(byte[]::clone);
    }

    @Override
    public String toString() {
        return "IsoParserConfig[packagerTemplates=" + packagerTemplates
                + ", definitionPaths=" + definitionPaths
                + ", charset=" + charset
                + ", encryptionKey=" + (encryptionKey == null ? "<none>" : "***")
                + "]";
    }

    public static final class Builder {

        private final Map<CardNetworkType, Map<MessageType, String>> packagerTemplates =
                new EnumMap<>(CardNetworkType.class);
        private final Map<CardNetworkType, String> definitionPaths = new EnumMap<>(CardNetworkType.class);
        private Charset charset = StandardCharsets.ISO_8859_1;
        private byte[] encryptionKey;

        private Builder() {
            packagerTemplate(CardNetworkType.GENERIC, MessageType.SINGLE, DEFAULT_GENERIC_SINGLE_TEMPLATE);
            packagerTemplate(CardNetworkType.GENERIC, MessageType.DUAL, DEFAULT_GENERIC_DUAL_TEMPLATE);
            packagerTemplate(CardNetworkType.MASTERCARD, MessageType.SINGLE, DEFAULT_MASTERCARD_SINGLE_TEMPLATE);
            packagerTemplate(CardNetworkType.MASTERCARD, MessageType.DUAL, DEFAULT_MASTERCARD_DUAL_TEMPLATE);
            definitionPath(CardNetworkType.GENERIC, DEFAULT_GENERIC_DEFINITIONS);
            definitionPath(CardNetworkType.MASTERCARD, DEFAULT_MASTERCARD_DEFINITIONS);
        }

        public Builder packagerTemplate(CardNetworkType network, MessageType type, String path) {
            Objects.requireNonNull(network, "network");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(path, "path");
            packagerTemplates.computeIfAbsent(network, n -> new EnumMap<>(MessageType.class)).put(type, path);
            return this;
        }

        public Builder definitionPath(CardNetworkType network, String path) {
            Objects.requireNonNull(network, "network");
            Objects.requireNonNull(path, "path");
            definitionPaths.put(network, path);
            return this;
        }

        /**
         * Removes a packager template (including a default one), so no strategy is registered for the combination.
         */
        public Builder removePackagerTemplate(CardNetworkType network, MessageType type) {
            Objects.requireNonNull(network, "network");
            Objects.requireNonNull(type, "type");
            Map<MessageType, String> byType = packagerTemplates.get(network);
            if (byType != null) {
                byType.remove(type);
                if (byType.isEmpty()) {
                    packagerTemplates.remove(network);
                }
            }
            return this;
        }

        /**
         * Removes the field definition path of a network (including a default one).
         */
        public Builder removeDefinitionPath(CardNetworkType network) {
            Objects.requireNonNull(network, "network");
            definitionPaths.remove(network);
            return this;
        }

        /**
         * Sets the input encoding of {@code IsoParser.parseAscii} (see {@link IsoParserConfig#charset()}).
         */
        public Builder charset(Charset charset) {
            this.charset = Objects.requireNonNull(charset, "charset");
            return this;
        }

        /**
         * Sets the AES key (16, 24 or 32 bytes). The array is copied.
         *
         * @throws IsoParserException if the key length is invalid
         */
        public Builder encryptionKey(byte[] key) {
            if (key == null) {
                this.encryptionKey = null;
                return this;
            }
            if (key.length != 16 && key.length != 24 && key.length != 32) {
                throw new IsoParserException("AES key must be 16, 24 or 32 bytes");
            }
            this.encryptionKey = key.clone();
            return this;
        }

        public IsoParserConfig build() {
            return new IsoParserConfig(this);
        }
    }
}
