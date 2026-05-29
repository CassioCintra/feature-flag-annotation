package cassio.annotations.messaging.kafka;

import cassio.annotations.messaging.FeatureFlagEventProcessor;
import cassio.annotations.model.FeatureFlagEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import java.util.Map;

/**
 * Kafka-specific configuration for the feature flag library.
 * Isolated so the compiler only processes it when spring-kafka is on the classpath.
 *
 * <p>Activated when {@code spring.kafka.bootstrap-servers} is configured.
 */
@Configuration
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
public class FeatureFlagKafkaConfig {

    @Bean(name = "featureFlagKafkaProperties")
    @ConditionalOnMissingBean(name = "featureFlagKafkaProperties")
    @ConfigurationProperties(prefix = "feature-flag.kafka")
    public FeatureFlagKafkaProperties featureFlagKafkaProperties() {
        return new FeatureFlagKafkaProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public FeatureFlagKafkaConsumer featureFlagKafkaConsumer(FeatureFlagEventProcessor processor) {
        return new FeatureFlagKafkaConsumer(processor);
    }

    @Bean(name = "featureFlagKafkaListenerContainerFactory")
    @ConditionalOnMissingBean(name = "featureFlagKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, FeatureFlagEvent>
    featureFlagKafkaListenerContainerFactory(Environment env) {

        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                env.getProperty("spring.kafka.bootstrap-servers"),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );

        JacksonJsonDeserializer<FeatureFlagEvent> deserializer =
                new JacksonJsonDeserializer<>(FeatureFlagEvent.class);
        deserializer.addTrustedPackages("cassio.annotations.model");

        ConsumerFactory<String, FeatureFlagEvent> factory =
                new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);

        ConcurrentKafkaListenerContainerFactory<String, FeatureFlagEvent> containerFactory =
                new ConcurrentKafkaListenerContainerFactory<>();
        containerFactory.setConsumerFactory(factory);
        return containerFactory;
    }
}
