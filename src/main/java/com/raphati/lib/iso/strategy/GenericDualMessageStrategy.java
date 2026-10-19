package com.raphati.lib.iso.strategy;

import com.raphati.lib.iso.config.IsoParserConfig;
import com.raphati.lib.iso.type.CardNetworkType;
import com.raphati.lib.iso.type.MessageType;

/**
 * Generic ISO 8583:1987 dual message strategy.
 */
public class GenericDualMessageStrategy extends MessageProcessingStrategy {

    public static final String DEFAULT_TEMPLATE = IsoParserConfig.DEFAULT_GENERIC_DUAL_TEMPLATE;

    public GenericDualMessageStrategy() {
        this(DEFAULT_TEMPLATE);
    }

    public GenericDualMessageStrategy(String templatePath) {
        super(templatePath);
    }

    @Override
    public CardNetworkType cardNetwork() {
        return CardNetworkType.GENERIC;
    }

    @Override
    public MessageType messageType() {
        return MessageType.DUAL;
    }
}
