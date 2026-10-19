package com.raphati.lib.iso.strategy;

import com.raphati.lib.iso.config.IsoParserConfig;
import com.raphati.lib.iso.type.CardNetworkType;
import com.raphati.lib.iso.type.MessageType;

/**
 * Generic ISO 8583:1987 single message strategy.
 */
public class GenericSingleMessageStrategy extends MessageProcessingStrategy {

    public static final String DEFAULT_TEMPLATE = IsoParserConfig.DEFAULT_GENERIC_SINGLE_TEMPLATE;

    public GenericSingleMessageStrategy() {
        this(DEFAULT_TEMPLATE);
    }

    public GenericSingleMessageStrategy(String templatePath) {
        super(templatePath);
    }

    @Override
    public CardNetworkType cardNetwork() {
        return CardNetworkType.GENERIC;
    }

    @Override
    public MessageType messageType() {
        return MessageType.SINGLE;
    }
}
