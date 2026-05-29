package cassio.annotations.messaging.activemq;

import cassio.annotations.messaging.FeatureFlagEventProcessor;
import cassio.annotations.model.FeatureFlagEvent;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureFlagActiveMqConsumerTest {

    @Mock
    private FeatureFlagEventProcessor processor;

    @Mock
    private TextMessage textMessage;

    private FeatureFlagActiveMqConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new FeatureFlagActiveMqConsumer(processor, JsonMapper.builder().build());
    }

    @Test
    void shouldDelegateToProcessor() throws Exception {
        FeatureFlagEvent event = new FeatureFlagEvent(
                "my-flag", "checkout-service", Map.of("dev", true), true, FeatureFlagEvent.Action.UPDATED);

        String json = JsonMapper.builder().build().writeValueAsString(event);
        when(textMessage.getText()).thenReturn(json);

        consumer.consume(textMessage);

        verify(processor).process(argThat(e ->
                e.getFlagName().equals("my-flag") &&
                e.getAction() == FeatureFlagEvent.Action.UPDATED
        ));
    }

    @Test
    void shouldIgnoreNonTextMessages() throws Exception {
        consumer.consume(mock(jakarta.jms.Message.class));

        verifyNoInteractions(processor);
    }
}
