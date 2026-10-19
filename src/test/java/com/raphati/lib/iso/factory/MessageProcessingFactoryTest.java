package com.raphati.lib.iso.factory;

import com.raphati.lib.iso.config.IsoParserConfig;
import com.raphati.lib.iso.exception.InvalidTransactionTypeException;
import com.raphati.lib.iso.exception.IsoParserException;
import com.raphati.lib.iso.strategy.GenericDualMessageStrategy;
import com.raphati.lib.iso.strategy.GenericSingleMessageStrategy;
import com.raphati.lib.iso.strategy.MessageProcessingStrategy;
import com.raphati.lib.iso.strategy.mastercard.MastercardDualMessageStrategy;
import com.raphati.lib.iso.strategy.mastercard.MastercardSingleMessageStrategy;
import com.raphati.lib.iso.type.CardNetworkType;
import com.raphati.lib.iso.type.MessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageProcessingFactoryTest {

    private final MessageProcessingFactory factory = new MessageProcessingFactory();

    static Stream<Arguments> combinations() {
        return Stream.of(
                Arguments.of(CardNetworkType.GENERIC, MessageType.SINGLE, GenericSingleMessageStrategy.class,
                        IsoParserConfig.DEFAULT_GENERIC_SINGLE_TEMPLATE),
                Arguments.of(CardNetworkType.GENERIC, MessageType.DUAL, GenericDualMessageStrategy.class,
                        IsoParserConfig.DEFAULT_GENERIC_DUAL_TEMPLATE),
                Arguments.of(CardNetworkType.MASTERCARD, MessageType.SINGLE, MastercardSingleMessageStrategy.class,
                        IsoParserConfig.DEFAULT_MASTERCARD_SINGLE_TEMPLATE),
                Arguments.of(CardNetworkType.MASTERCARD, MessageType.DUAL, MastercardDualMessageStrategy.class,
                        IsoParserConfig.DEFAULT_MASTERCARD_DUAL_TEMPLATE));
    }

    @ParameterizedTest
    @MethodSource("combinations")
    void returnsExpectedStrategy(CardNetworkType network, MessageType type,
                                 Class<? extends MessageProcessingStrategy> expectedClass, String template) {
        MessageProcessingStrategy strategy = factory.getStrategy(network, type);

        assertThat(strategy).isExactlyInstanceOf(expectedClass);
        assertThat(strategy.cardNetwork()).isEqualTo(network);
        assertThat(strategy.messageType()).isEqualTo(type);
        assertThat(strategy.templatePath()).isEqualTo(template);
    }

    @Test
    void resolvesByName() {
        assertThat(factory.getStrategy("mastercard", "dual")).isInstanceOf(MastercardDualMessageStrategy.class);
        assertThat(factory.getStrategy(" Generic ", "SINGLE")).isInstanceOf(GenericSingleMessageStrategy.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "TRIPLE")
    void invalidMessageTypeThrows(String type) {
        assertThatThrownBy(() -> factory.getStrategy("GENERIC", type))
                .isInstanceOf(InvalidTransactionTypeException.class);
    }

    @Test
    void unknownNetworkThrows() {
        assertThatThrownBy(() -> factory.getStrategy("NOT_A_NETWORK", "SINGLE"))
                .isInstanceOf(IsoParserException.class)
                .isNotInstanceOf(InvalidTransactionTypeException.class);
        assertThatThrownBy(() -> factory.getStrategy((CardNetworkType) null, MessageType.SINGLE))
                .isInstanceOf(IsoParserException.class);
        assertThatThrownBy(() -> factory.getStrategy("NOT_A_NETWORK", "TRIPLE"))
                .isInstanceOf(IsoParserException.class)
                .isNotInstanceOf(InvalidTransactionTypeException.class);
    }

    @Test
    void removedTemplateHasNoStrategy() {
        MessageProcessingFactory narrowed = new MessageProcessingFactory(IsoParserConfig.builder()
                .removePackagerTemplate(CardNetworkType.MASTERCARD, MessageType.SINGLE)
                .removePackagerTemplate(CardNetworkType.MASTERCARD, MessageType.DUAL)
                .removeDefinitionPath(CardNetworkType.MASTERCARD)
                .build());

        assertThat(narrowed.getStrategy(CardNetworkType.GENERIC, MessageType.SINGLE))
                .isInstanceOf(GenericSingleMessageStrategy.class);
        assertThatThrownBy(() -> narrowed.getStrategy(CardNetworkType.MASTERCARD, MessageType.DUAL))
                .isInstanceOf(IsoParserException.class)
                .hasMessageContaining("No strategy registered");
    }

    @Test
    void honorsCustomTemplatePath() {
        String custom = "classpath:xml/custom-generic-single.xml";
        MessageProcessingFactory customFactory = new MessageProcessingFactory(IsoParserConfig.builder()
                .packagerTemplate(CardNetworkType.GENERIC, MessageType.SINGLE, custom)
                .build());

        assertThat(customFactory.getStrategy(CardNetworkType.GENERIC, MessageType.SINGLE).templatePath())
                .isEqualTo(custom);
        assertThat(customFactory.getStrategy(CardNetworkType.GENERIC, MessageType.DUAL).templatePath())
                .isEqualTo(IsoParserConfig.DEFAULT_GENERIC_DUAL_TEMPLATE);
    }
}
