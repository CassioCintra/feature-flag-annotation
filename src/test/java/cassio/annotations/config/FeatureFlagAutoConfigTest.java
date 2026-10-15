package cassio.annotations.config;

import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.consul.ConsulFlagSync;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureFlagAutoConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeatureFlagAutoConfig.class))
            .withPropertyValues("feature-flag.service-name=checkout-service");

    @Test
    void isRegisteredAsAutoConfiguration() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
                .contains(FeatureFlagAutoConfig.class.getName());
    }

    @Test
    void registersConsulSyncOnlyWhenConsulUrlIsSet() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ConsulFlagSync.class));
        runner.withPropertyValues("feature-flag.consul.url=http://localhost:8500")
                .run(context -> assertThat(context).hasSingleBean(ConsulFlagSync.class));
    }

    @Test
    void emptyDefaultIsDisabled() {
        runner.withPropertyValues("feature-flag.defaults.new-checkout=")
                .run(context -> assertThat(context.getBean(FeatureFlagCacheService.class)
                        .isEnabled("new-checkout", true)).isFalse());
    }
}
