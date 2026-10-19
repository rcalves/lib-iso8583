package com.raphati.lib.iso.factory;

import com.raphati.lib.iso.config.IsoParserConfig;
import com.raphati.lib.iso.exception.IsoParserException;
import com.raphati.lib.iso.strategy.GenericDualMessageStrategy;
import com.raphati.lib.iso.strategy.GenericSingleMessageStrategy;
import com.raphati.lib.iso.strategy.MessageProcessingStrategy;
import com.raphati.lib.iso.strategy.mastercard.MastercardDualMessageStrategy;
import com.raphati.lib.iso.strategy.mastercard.MastercardSingleMessageStrategy;
import com.raphati.lib.iso.type.CardNetworkType;
import com.raphati.lib.iso.type.MessageType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Resolves the {@link MessageProcessingStrategy} for a card network and message type.
 * Strategies are registered at construction with the packager template paths from the config, but each template is
 * only read on the strategy's first use ({@link MessageProcessingStrategy#packager()}), so a wrong template path or
 * an invalid template surfaces as an {@link IsoParserException} on the first parse or pack, not here.
 */
public class MessageProcessingFactory {

    private final Map<CardNetworkType, Map<MessageType, MessageProcessingStrategy>> strategies =
            new EnumMap<>(CardNetworkType.class);

    public MessageProcessingFactory() {
        this(IsoParserConfig.defaults());
    }

    public MessageProcessingFactory(IsoParserConfig config) {
        Objects.requireNonNull(config, "config");
        register(config, CardNetworkType.GENERIC, MessageType.SINGLE, GenericSingleMessageStrategy::new);
        register(config, CardNetworkType.GENERIC, MessageType.DUAL, GenericDualMessageStrategy::new);
        register(config, CardNetworkType.MASTERCARD, MessageType.SINGLE, MastercardSingleMessageStrategy::new);
        register(config, CardNetworkType.MASTERCARD, MessageType.DUAL, MastercardDualMessageStrategy::new);
    }

    private void register(IsoParserConfig config, CardNetworkType network, MessageType type,
                          Function<String, MessageProcessingStrategy> constructor) {
        String template = config.packagerTemplate(network, type);
        if (template == null) {
            return;
        }
        strategies.computeIfAbsent(network, n -> new EnumMap<>(MessageType.class))
                .put(type, constructor.apply(template));
    }

    /**
     * @throws IsoParserException if either argument is null or no strategy is registered for the combination
     */
    public MessageProcessingStrategy getStrategy(CardNetworkType network, MessageType type) {
        if (network == null || type == null) {
            throw new IsoParserException("Card network and message type are required");
        }
        Map<MessageType, MessageProcessingStrategy> byType = strategies.get(network);
        MessageProcessingStrategy strategy = byType == null ? null : byType.get(type);
        if (strategy == null) {
            throw new IsoParserException("No strategy registered for " + network + "/" + type);
        }
        return strategy;
    }

    /**
     * Resolves by name (case-insensitive). The network is validated first.
     *
     * @throws IsoParserException                                            if the network is invalid
     * @throws com.raphati.lib.iso.exception.InvalidTransactionTypeException if the message type is invalid
     */
    public MessageProcessingStrategy getStrategy(String network, String messageType) {
        CardNetworkType cardNetwork = CardNetworkType.fromString(network);
        return getStrategy(cardNetwork, MessageType.fromString(messageType));
    }
}
