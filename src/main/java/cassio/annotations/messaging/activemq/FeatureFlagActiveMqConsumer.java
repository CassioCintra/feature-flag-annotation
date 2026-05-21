package cassio.annotations.messaging.activemq;

import cassio.annotations.messaging.FeatureFlagEventProcessor;
import cassio.annotations.model.FeatureFlagEvent;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.springframework.jms.annotation.JmsListener;
import tools.jackson.databind.ObjectMapper;

/**
 * ActiveMQ Artemis consumer for feature flag events.
 * Delegates all processing logic to {@link FeatureFlagEventProcessor}.
 *
 * <p>Activated when {@code spring.artemis.broker-url} is configured
 * and {@code spring-boot-starter-artemis} is on the classpath.
 */
public class FeatureFlagActiveMqConsumer {

    private final FeatureFlagEventProcessor processor;
    private final ObjectMapper objectMapper;

    public FeatureFlagActiveMqConsumer(FeatureFlagEventProcessor processor, ObjectMapper objectMapper) {
        this.processor = processor;
        this.objectMapper = objectMapper;
    }

    @JmsListener(destination = "${feature-flag.artemis.queue:feature-flags.events}")
    public void consume(Message message) throws Exception {
        if (message instanceof TextMessage textMessage) {
            FeatureFlagEvent event = objectMapper.readValue(
                    textMessage.getText(), FeatureFlagEvent.class);
            processor.process(event);
        }
    }
}
