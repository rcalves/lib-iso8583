package com.raphati.lib.iso;

import com.raphati.lib.iso.config.IsoParserConfig;
import com.raphati.lib.iso.exception.IsoParserException;
import com.raphati.lib.iso.factory.MessageProcessingFactory;
import com.raphati.lib.iso.loader.IsoDefinitionLoader;
import com.raphati.lib.iso.model.IsoModel;
import com.raphati.lib.iso.model.ParserModel;
import com.raphati.lib.iso.strategy.MessageProcessingStrategy;
import com.raphati.lib.iso.type.CardNetworkType;
import com.raphati.lib.iso.type.MessageType;
import com.raphati.lib.iso.utils.IsoUtils;

import java.util.Objects;

/**
 * Entry point of the library: wires the config, the field definition loader and the strategy factory.
 * Instances are thread-safe and meant to be reused.
 */
public class IsoParser {

    private final IsoParserConfig config;
    private final IsoDefinitionLoader loader;
    private final MessageProcessingFactory factory;

    public IsoParser() {
        this(IsoParserConfig.defaults());
    }

    public IsoParser(IsoParserConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.loader = new IsoDefinitionLoader(config);
        this.factory = new MessageProcessingFactory(config);
    }

    /**
     * Parses a message given as the hex encoding of its packed bytes.
     *
     * @throws IsoParserException if the message cannot be parsed
     */
    public ParserModel parse(String hexMessage, CardNetworkType network, MessageType messageType) {
        MessageProcessingStrategy strategy = factory.getStrategy(network, messageType);
        IsoModel message = strategy.parse(hexMessage, loader);
        return new ParserModel(network, messageType, message);
    }

    /**
     * Same as {@link #parse(String, CardNetworkType, MessageType)} with network and type given by name. The network is
     * validated first.
     */
    public ParserModel parse(String hexMessage, String network, String messageType) {
        CardNetworkType cardNetwork = CardNetworkType.fromString(network);
        return parse(hexMessage, cardNetwork, MessageType.fromString(messageType));
    }

    /**
     * Packs a parsed (or edited) message with the packager of its network and type, the reverse of
     * {@link #parse(String, CardNetworkType, MessageType)}. The bitmap is rebuilt from the fields present, so a
     * redundant all-zero secondary bitmap is not reproduced (see
     * {@link MessageProcessingStrategy#pack(IsoModel, IsoDefinitionLoader)}).
     *
     * @return the hex encoding of the packed bytes (uppercase)
     * @throws IsoParserException if the message cannot be built or packed
     */
    public String pack(ParserModel parsed) {
        Objects.requireNonNull(parsed, "parsed");
        MessageProcessingStrategy strategy = factory.getStrategy(parsed.network(), parsed.messageType());
        return IsoUtils.bytesToHex(strategy.pack(parsed.message(), loader));
    }

    /**
     * Parses a raw ASCII message (not hex encoded). The string is turned into wire bytes with the configured
     * {@link IsoParserConfig#charset() charset}; field values are then always decoded by jPOS as ISO-8859-1.
     * Whitespace after the last field (such as the line break of a message copied from a log) is ignored.
     */
    public ParserModel parseAscii(String rawAscii, CardNetworkType network, MessageType messageType) {
        if (rawAscii == null) {
            throw new IsoParserException("ISO message must not be null");
        }
        MessageProcessingStrategy strategy = factory.getStrategy(network, messageType);
        IsoModel message = strategy.parseAscii(rawAscii, config.charset(), loader);
        return new ParserModel(network, messageType, message);
    }

    public IsoParserConfig config() {
        return config;
    }

    public IsoDefinitionLoader loader() {
        return loader;
    }

    public MessageProcessingFactory factory() {
        return factory;
    }
}
