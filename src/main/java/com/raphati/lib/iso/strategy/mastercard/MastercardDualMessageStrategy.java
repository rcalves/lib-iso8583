package com.raphati.lib.iso.strategy.mastercard;

import com.raphati.lib.iso.config.IsoParserConfig;
import com.raphati.lib.iso.strategy.MessageProcessingStrategy;
import com.raphati.lib.iso.type.CardNetworkType;
import com.raphati.lib.iso.type.MessageType;

/**
 * Mastercard dual message strategy (ASCII approximation of the layout).
 */
public class MastercardDualMessageStrategy extends MessageProcessingStrategy {

    public static final String DEFAULT_TEMPLATE = IsoParserConfig.DEFAULT_MASTERCARD_DUAL_TEMPLATE;

    public MastercardDualMessageStrategy() {
        this(DEFAULT_TEMPLATE);
    }

    public MastercardDualMessageStrategy(String templatePath) {
        super(templatePath);
    }

    @Override
    public CardNetworkType cardNetwork() {
        return CardNetworkType.MASTERCARD;
    }

    @Override
    public MessageType messageType() {
        return MessageType.DUAL;
    }
}
