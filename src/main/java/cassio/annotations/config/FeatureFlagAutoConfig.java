package cassio.annotations.config;

import cassio.annotations.aspect.FeatureFlagAspect;
import cassio.annotations.aspect.FeatureFlagFieldInjectorAspect;
import cassio.annotations.bootstrap.FeatureFlagBootstrap;
import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.messaging.FeatureFlagEventProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.client.RestClient;

/**
 * Auto-configuration for the feature flag library.
 *
 * <p>Registered automatically via:
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 *
 * <p>Each bean is conditionally registered:
 * <ul>
 *   <li>{@link FeatureFlagCacheService} — always active</li>
 *   <li>{@link FeatureFlagEventProcessor} — always active</li>
 *   <li>{@link FeatureFlagAspect} — always active</li>
 *   <li>{@link FeatureFlagFieldInjectorAspect} — always active</li>
 *   <li>{@link FeatureFlagBootstrap} — requires {@code feature-flag.flag-service-url}</li>
 *   <li>Kafka consumer — see {@code FeatureFlagKafkaConfig} (requires spring-kafka + bootstrap-servers)</li>
 *   <li>RabbitMQ consumer — see {@code FeatureFlagRabbitConfig} (requires spring-amqp + rabbitmq.host)</li>
 *   <li>ActiveMQ consumer — see {@code FeatureFlagActiveMqConfig} (requires spring-jms + activemq.broker-url)</li>
 *   <li>OAuth2 RestClient — see {@code FeatureFlagOAuth2Config} (requires oauth2-client + AuthorizedClientManager bean)</li>
 * </ul>
 */
@AutoConfiguration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(FeatureFlagProperties.class)
public class FeatureFlagAutoConfig {

    @Bean
    @ConditionalOnMissingBean
    public FeatureFlagCacheService featureFlagCacheService(FeatureFlagProperties properties) {
        return new FeatureFlagCacheService(properties);
    }

    /**
     * Shared event processor — delegates to all messaging consumers (Kafka, RabbitMQ, ActiveMQ).
     */
    @Bean
    @ConditionalOnMissingBean
    public FeatureFlagEventProcessor featureFlagEventProcessor(FeatureFlagProperties properties,
                                                               FeatureFlagCacheService cacheService) {
        return new FeatureFlagEventProcessor(properties, cacheService);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeatureFlagAspect featureFlagAspect(FeatureFlagCacheService cacheService) {
        return new FeatureFlagAspect(cacheService);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeatureFlagFieldInjectorAspect featureFlagFieldInjectorAspect(FeatureFlagCacheService cacheService) {
        return new FeatureFlagFieldInjectorAspect(cacheService);
    }

    /**
     * HTTP bootstrap — only registered if {@code feature-flag.flag-service-url} is configured.
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
     * RestClient without authentication — used when OAuth2AuthorizedClientManager
     * is not present in the context. See {@code FeatureFlagOAuth2Config} for the OAuth2 variant.
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
}
