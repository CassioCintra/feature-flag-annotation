package cassio.annotations.config;

import cassio.annotations.aspect.FeatureFlagAspect;
import cassio.annotations.aspect.FeatureFlagFieldInjectorAspect;
import cassio.annotations.bootstrap.FeatureFlagBootstrap;
import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.kafka.FeatureFlagKafkaConsumer;
import cassio.annotations.model.FeatureFlagEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Auto-configuration for the feature flag library.
 *
 * <p>Registered automatically via:
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 *
 * <p>Consumer services require no additional configuration —
 * simply add the dependency to pom.xml and configure application.properties.
 *
 * <p>Each bean is conditionally registered:
 * <ul>
 *   <li>{@link FeatureFlagCacheService} — always active</li>
 *   <li>{@link FeatureFlagAspect} — always active</li>
 *   <li>{@link FeatureFlagFieldInjectorAspect} — always active</li>
 *   <li>{@link FeatureFlagBootstrap} — requires {@code feature-flag.flag-service-url}</li>
 *   <li>{@link FeatureFlagKafkaConsumer} — requires {@code spring.kafka.bootstrap-servers}</li>
 * </ul>
 */
@AutoConfiguration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(FeatureFlagProperties.class)
public class FeatureFlagAutoConfig {

    /**
     * Local cache — source of truth consulted by the Aspect on every @FeatureFlag call.
     * Populated by the HTTP bootstrap and kept up to date via Kafka events.
     */
    @Bean
    @ConditionalOnMissingBean
    public FeatureFlagCacheService featureFlagCacheService(FeatureFlagProperties properties) {
        return new FeatureFlagCacheService(properties);
    }

    /**
     * AOP Aspect — intercepts methods and classes annotated with @FeatureFlag.
     * Allows consumer services to override by defining their own bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public FeatureFlagAspect featureFlagAspect(FeatureFlagCacheService cacheService) {
        return new FeatureFlagAspect(cacheService);
    }

    /**
     * AOP Aspect — updates @FeatureFlag Boolean fields before each method execution.
     * Runs with @Order(1), before FeatureFlagAspect, so field values are always current.
     * Allows consumer services to override by defining their own bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public FeatureFlagFieldInjectorAspect featureFlagFieldInjectorAspect(FeatureFlagCacheService cacheService) {
        return new FeatureFlagFieldInjectorAspect(cacheService);
    }

    /**
     * HTTP bootstrap — fetches all flags for this service on application startup.
     * Only registered if {@code feature-flag.flag-service-url} is configured.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feature-flag", name = "flag-service-url")
    public FeatureFlagBootstrap featureFlagBootstrap(FeatureFlagProperties properties,
                                                     FeatureFlagCacheService cacheService,
                                                     RestClient featureFlagRestClient) {
        return new FeatureFlagBootstrap(properties, cacheService, featureFlagRestClient);
    }

    /**
     * RestClient sem autenticação — usado quando OAuth2AuthorizedClientManager
     * não está disponível no contexto (consumidor sem oauth2-client).
     */
    @Bean
    @ConditionalOnProperty(prefix = "feature-flag", name = "flag-service-url")
    @ConditionalOnMissingBean(name = "featureFlagRestClient")
    @ConditionalOnMissingClass("org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager")
    public RestClient featureFlagRestClient(FeatureFlagProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getFlagServiceUrl())
                .build();
    }

    /**
     * Kafka consumer — listens for flag events and updates the local cache.
     * Only registered if {@code spring.kafka.bootstrap-servers} is configured.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
    public FeatureFlagKafkaConsumer featureFlagKafkaConsumer(FeatureFlagProperties properties,
                                                             FeatureFlagCacheService cacheService) {
        return new FeatureFlagKafkaConsumer(properties, cacheService);
    }

    /**
     * Kafka listener container factory configured to deserialize {@link FeatureFlagEvent}.
     * Inherits all consumer settings (SSL, SASL, etc.) from {@code spring.kafka.*} properties.
     * Consumer services can override by defining a bean named "featureFlagKafkaListenerContainerFactory".
     */
    @Bean(name = "featureFlagKafkaListenerContainerFactory")
    @ConditionalOnMissingBean(name = "featureFlagKafkaListenerContainerFactory")
    @ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
    public ConcurrentKafkaListenerContainerFactory<String, FeatureFlagEvent>
    featureFlagKafkaListenerContainerFactory(Environment env) {

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                env.getProperty("spring.kafka.bootstrap-servers"));
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JacksonJsonDeserializer<FeatureFlagEvent> deserializer =
                new JacksonJsonDeserializer<>(FeatureFlagEvent.class);
        deserializer.addTrustedPackages("cassio.annotations.model");

        ConsumerFactory<String, FeatureFlagEvent> factory =
                new DefaultKafkaConsumerFactory<>(
                        props,
                        new StringDeserializer(),
                        deserializer
                );

        ConcurrentKafkaListenerContainerFactory<String, FeatureFlagEvent> containerFactory =
                new ConcurrentKafkaListenerContainerFactory<>();
        containerFactory.setConsumerFactory(factory);
        return containerFactory;
    }
}