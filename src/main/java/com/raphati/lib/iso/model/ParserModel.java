package com.raphati.lib.iso.model;

import com.raphati.lib.iso.type.CardNetworkType;
import com.raphati.lib.iso.type.MessageType;

/**
 * Result of parsing a message: the network/type used and the parsed message.
 * {@code toString} is safe to log (sensitive values are masked).
 */
public record ParserModel(CardNetworkType network, MessageType messageType, IsoModel message) {
}
