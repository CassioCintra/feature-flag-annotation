package cassio.annotations.messaging.kafka;

import cassio.annotations.messaging.FeatureFlagEventProcessor;
import cassio.annotations.model.FeatureFlagEvent;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * Kafka consumer for feature flag events.
 * Delegates all processing logic to {@link FeatureFlagEventProcessor}.
 *
 * <p>Activated when {@code spring.kafka.bootstrap-servers} is configured.
 */
public class FeatureFlagKafkaConsumer {

    private final FeatureFlagEventProcessor processor;

    public FeatureFlagKafkaConsumer(FeatureFlagEventProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(
            topics = "${feature-flag.kafka.topic:feature-flags.events}",
            groupId = "${feature-flag.kafka.group-id:feature-flag-consumer}",
            containerFactory = "featureFlagKafkaListenerContainerFactory"
    )
    public void consume(FeatureFlagEvent event) {
        processor.process(event);
    }
}
