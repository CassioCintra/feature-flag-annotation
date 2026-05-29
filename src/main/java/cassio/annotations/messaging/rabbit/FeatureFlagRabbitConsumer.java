package cassio.annotations.messaging.rabbit;

import cassio.annotations.messaging.FeatureFlagEventProcessor;
import cassio.annotations.model.FeatureFlagEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * RabbitMQ consumer for feature flag events.
 * Delegates all processing logic to {@link FeatureFlagEventProcessor}.
 *
 * <p>Activated when {@code spring.rabbitmq.host} is configured
 * and {@code spring-boot-starter-amqp} is on the classpath.
 */
public class FeatureFlagRabbitConsumer {

    private final FeatureFlagEventProcessor processor;

    public FeatureFlagRabbitConsumer(FeatureFlagEventProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(queues = "${feature-flag.rabbit.queue:flag.events}")
    public void consume(FeatureFlagEvent event) {
        processor.process(event);
    }
}
