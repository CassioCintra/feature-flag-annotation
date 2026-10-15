package cassio.annotations.config;

import cassio.annotations.aspect.FeatureFlagAspect;
import cassio.annotations.aspect.FeatureFlagFieldInjectorAspect;
import cassio.annotations.bootstrap.FeatureFlagScanner;
import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.consul.ConsulClient;
import cassio.annotations.consul.ConsulFlagSync;
import cassio.annotations.consul.ConsulJwtSupplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

import java.time.Duration;

/**
 * Auto-configuration for the feature flag library.
 *
 * <p>Registered automatically via:
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 *
 * <p>Each bean is conditionally registered:
 * <ul>
 *   <li>{@link FeatureFlagCacheService} — always active</li>
 *   <li>{@link FeatureFlagAspect} — always active</li>
 *   <li>{@link FeatureFlagFieldInjectorAspect} — always active</li>
 *   <li>{@link ConsulFlagSync} — requires {@code feature-flag.consul.url}</li>
 *   <li>Consul login JWT — see {@code FeatureFlagOAuth2Config} (requires oauth2-client + AuthorizedClientManager bean)</li>
 * </ul>
 */
@AutoConfiguration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(FeatureFlagProperties.class)
@Import(FeatureFlagOAuth2Config.class)
public class FeatureFlagAutoConfig {

    private static final Duration CONSUL_WAIT = Duration.ofMinutes(5);
    private static final Duration CONSUL_RETRY_DELAY = Duration.ofSeconds(5);

    @Bean
    @ConditionalOnMissingBean
    public FeatureFlagCacheService featureFlagCacheService(FeatureFlagProperties properties) {
        return new FeatureFlagCacheService(properties);
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

    @Bean
    @ConditionalOnMissingBean
    public FeatureFlagScanner featureFlagScanner(ApplicationContext ctx) {
        return new FeatureFlagScanner(ctx);
    }

    /**
     * Consul sync, registered only when {@code feature-flag.consul.url} is set.
     * Without a {@link ConsulJwtSupplier} bean, Consul is called without a token.
     *
     * @param properties         library configuration
     * @param cacheService       flag cache
     * @param featureFlagScanner finds the annotated flags checked by strict mode
     * @param jwtSupplier        optional source of the JWT used to log in to Consul
     * @return the Consul sync
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "feature-flag.consul", name = "url")
    public ConsulFlagSync consulFlagSync(FeatureFlagProperties properties,
                                         FeatureFlagCacheService cacheService,
                                         FeatureFlagScanner featureFlagScanner,
                                         ObjectProvider<ConsulJwtSupplier> jwtSupplier) {
        var consul = properties.getConsul();
        var client = new ConsulClient(consul.getUrl(), consul.getAuthMethod(),
                jwtSupplier.getIfAvailable(), CONSUL_WAIT.plusMinutes(1));
        return new ConsulFlagSync(properties, cacheService, featureFlagScanner, client,
                CONSUL_WAIT, CONSUL_RETRY_DELAY);
    }
}
