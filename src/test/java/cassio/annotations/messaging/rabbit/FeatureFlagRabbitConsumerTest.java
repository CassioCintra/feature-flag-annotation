package cassio.annotations.messaging.rabbit;

import cassio.annotations.messaging.FeatureFlagEventProcessor;
import cassio.annotations.model.FeatureFlagEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FeatureFlagRabbitConsumerTest {

    @Mock
    private FeatureFlagEventProcessor processor;

    @InjectMocks
    private FeatureFlagRabbitConsumer consumer;

    @Test
    void shouldDelegateToProcessor() {
        FeatureFlagEvent event = new FeatureFlagEvent(
                "my-flag", "checkout-service", "dev", true, FeatureFlagEvent.Action.UPDATED);

        consumer.consume(event);

        verify(processor).process(event);
    }
}
