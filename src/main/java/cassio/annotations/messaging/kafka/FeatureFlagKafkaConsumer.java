package cassio.annotations.messaging.kafka;

import cassio.annotations.messaging.FeatureFlagEventProcessor;
import cassio.annotations.model.FeatureFlagEvent;
import org.springframework.kafka.annotation.KafkaListener;

public class FeatureFlagKafkaConsumer {

    private final FeatureFlagEventProcessor processor;

    public FeatureFlagKafkaConsumer(FeatureFlagEventProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(
            topics = "#{@featureFlagKafkaProperties.topic}",
            groupId = "#{@featureFlagKafkaProperties.groupId}",
            containerFactory = "featureFlagKafkaListenerContainerFactory"
    )
    public void consume(FeatureFlagEvent event) {
        processor.process(event);
    }
}
