package cassio.annotations.cache;

import cassio.annotations.config.FeatureFlagProperties;
import cassio.annotations.exception.FeatureDisabledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureFlagCacheServiceTest {

    private FeatureFlagCacheService cacheService;

    @BeforeEach
    void setUp() {
        FeatureFlagProperties properties = new FeatureFlagProperties();
        properties.setServiceName("service-example");
        properties.setEnvironment("dev");
        properties.setDefaults(Map.of("new-checkout", false));

        cacheService = new FeatureFlagCacheService(properties);
    }

    @Test
    void cacheMiss_flagInProperties_shouldUsePropertiesDefault() {
        assertThat(cacheService.isEnabled("new-checkout", true)).isFalse();
    }

    @Test
    void cacheMiss_flagNotInProperties_shouldUseAnnotationDefault() {
        assertThat(cacheService.isEnabled("unknown-flag", true)).isTrue();
        assertThat(cacheService.isEnabled("unknown-flag", false)).isFalse();
    }

    @Test
    void cacheHit_shouldReturnCachedValueIgnoringDefaults() {
        cacheService.put("new-checkout", true);

        assertThat(cacheService.isEnabled("new-checkout", false)).isTrue();
    }

    @Test
    void populateAll_shouldLoadAllFlagsIntoCache() {
        cacheService.populateAll(Map.of(
                "new-checkout", true,
                "pix-payment", false
        ));

        assertThat(cacheService.isEnabled("new-checkout", false)).isTrue();
        assertThat(cacheService.isEnabled("pix-payment", true)).isFalse();
        assertThat(cacheService.size()).isEqualTo(2);
    }

    @Test
    void evict_shouldRemoveFlagAndFallbackToDefault() {
        cacheService.put("new-checkout", true);
        cacheService.evict("new-checkout");

        assertThat(cacheService.isEnabled("new-checkout", true)).isFalse();
    }

    @Test
    void exception_shouldContainFlagNameInDefaultMessage() {
        FeatureDisabledException ex = new FeatureDisabledException("my-flag");
        assertThat(ex.getFlagName()).isEqualTo("my-flag");
        assertThat(ex.getMessage()).contains("my-flag");
    }

    @Test
    void exception_shouldUseCustomMessage() {
        FeatureDisabledException ex = new FeatureDisabledException("my-flag", "Feature unavailable.");
        assertThat(ex.getMessage()).isEqualTo("Feature unavailable.");
    }

    @Test
    void contains_shouldReturnTrueWhenFlagIsInCache() {
        cacheService.put("new-checkout", true);
        assertThat(cacheService.contains("new-checkout")).isTrue();
    }

    @Test
    void contains_shouldReturnFalseWhenFlagIsNotInCache() {
        assertThat(cacheService.contains("unknown-flag")).isFalse();
    }
}