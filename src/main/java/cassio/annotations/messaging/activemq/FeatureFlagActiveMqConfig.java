package cassio.annotations.messaging.activemq;

import cassio.annotations.messaging.FeatureFlagEventProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.core.JmsTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * ActiveMQ Artemis-specific configuration for the feature flag library.
 * Isolated so the compiler only processes it when spring-jms is on the classpath.
 *
 * <p>Activated when {@code spring.artemis.broker-url} is configured
 * and {@code spring-boot-starter-artemis} is on the classpath.
 */
@EnableJms
@Configuration
@ConditionalOnClass(JmsTemplate.class)
@ConditionalOnProperty(prefix = "spring.artemis", name = "broker-url")
public class FeatureFlagActiveMqConfig {

    @Bean
    @ConditionalOnMissingBean
    public FeatureFlagActiveMqConsumer featureFlagActiveMqConsumer(FeatureFlagEventProcessor processor) {
        return new FeatureFlagActiveMqConsumer(processor, new JsonMapper());
    }
}
