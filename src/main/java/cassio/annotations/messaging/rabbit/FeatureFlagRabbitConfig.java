package cassio.annotations.messaging.rabbit;

import cassio.annotations.messaging.FeatureFlagEventProcessor;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * RabbitMQ-specific configuration for the feature flag library.
 * Isolated so the compiler only processes it when spring-amqp is on the classpath.
 *
 * <p>Activated when {@code spring.rabbitmq.host} is configured.
 */
@EnableRabbit
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(prefix = "spring.rabbitmq", name = "host")
public class FeatureFlagRabbitConfig {

    @Value("${feature-flag.rabbit.queue:flag.events}")
    private String queueName;

    @Bean
    @ConditionalOnMissingBean(name = "featureFlagRabbitQueue")
    public Queue featureFlagRabbitQueue() {
        return new Queue(queueName, true);
    }

    @Bean
    @ConditionalOnMissingBean(name = "featureFlagRabbitMessageConverter")
    public MessageConverter featureFlagRabbitMessageConverter() {
        return new JacksonJsonMessageConverter(new JsonMapper());
    }

    @Bean
    @ConditionalOnMissingBean
    public FeatureFlagRabbitConsumer featureFlagRabbitConsumer(FeatureFlagEventProcessor processor) {
        return new FeatureFlagRabbitConsumer(processor);
    }
}
