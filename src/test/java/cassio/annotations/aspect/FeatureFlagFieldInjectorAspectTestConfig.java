package cassio.annotations.aspect;

import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.config.FeatureFlagProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
public class FeatureFlagFieldInjectorAspectTestConfig {

    @Bean
    public FeatureFlagProperties featureFlagProperties() {
        FeatureFlagProperties properties = new FeatureFlagProperties();
        properties.setServiceName("test-service");
        properties.setEnvironment("dev");
        return properties;
    }

    @Bean
    public FeatureFlagCacheService featureFlagCacheService(FeatureFlagProperties properties) {
        return new FeatureFlagCacheService(properties);
    }

    @Bean
    public FeatureFlagFieldInjectorAspect featureFlagFieldInjectorAspect(FeatureFlagCacheService cacheService) {
        return new FeatureFlagFieldInjectorAspect(cacheService);
    }

    @Bean
    public FakeServiceWithFlagField fakeServiceWithFlagField() {
        return new FakeServiceWithFlagField();
    }
}
