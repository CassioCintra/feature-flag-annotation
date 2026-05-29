package cassio.annotations.messaging;

import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.config.FeatureFlagProperties;
import cassio.annotations.model.FeatureFlagEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureFlagEventProcessorTest {

    private FeatureFlagCacheService cacheService;
    private FeatureFlagEventProcessor processor;

    private static final String FLAG = "new-checkout";
    private static final String CHECKOUT_SERVICE = "checkout-service";
    private static final String OTHER_SERVICE = "other-service";
    private static final String DEV = "dev";
    private static final String PROD = "prod";

    @BeforeEach
    void setUp() {
        FeatureFlagProperties properties = new FeatureFlagProperties();
        properties.setServiceName(CHECKOUT_SERVICE);
        properties.setEnvironment(DEV);

        cacheService = new FeatureFlagCacheService(properties);
        processor = new FeatureFlagEventProcessor(properties, cacheService);
    }

    // --- CREATED ---

    @Test
    void created_currentEnvEnabled_shouldAddToCacheAsTrue() {
        processor.process(event(CHECKOUT_SERVICE, Map.of(DEV, true), true, FeatureFlagEvent.Action.CREATED));

        assertThat(cacheService.isEnabled(FLAG, false)).isTrue();
    }

    @Test
    void created_currentEnvDisabled_shouldAddToCacheAsFalse() {
        processor.process(event(CHECKOUT_SERVICE, Map.of(DEV, false), true, FeatureFlagEvent.Action.CREATED));

        assertThat(cacheService.isEnabled(FLAG, true)).isFalse();
    }

    @Test
    void created_globallyDisabled_shouldAddToCacheAsFalseEvenWhenEnvEnabled() {
        processor.process(event(CHECKOUT_SERVICE, Map.of(DEV, true), false, FeatureFlagEvent.Action.CREATED));

        assertThat(cacheService.isEnabled(FLAG, true)).isFalse();
    }

    @Test
    void created_currentEnvNotInMap_shouldNotAddToCache() {
        processor.process(event(CHECKOUT_SERVICE, Map.of(PROD, true), true, FeatureFlagEvent.Action.CREATED));

        assertThat(cacheService.size()).isZero();
    }

    @Test
    void created_nullEnvironments_shouldNotAddToCache() {
        processor.process(event(CHECKOUT_SERVICE, null, true, FeatureFlagEvent.Action.CREATED));

        assertThat(cacheService.size()).isZero();
    }

    @Test
    void created_differentService_shouldNotAddToCache() {
        processor.process(event(OTHER_SERVICE, Map.of(DEV, true), true, FeatureFlagEvent.Action.CREATED));

        assertThat(cacheService.size()).isZero();
    }

    // --- UPDATED ---

    @Test
    void updated_currentEnvEnabled_shouldUpdateCache() {
        processor.process(event(CHECKOUT_SERVICE, Map.of(DEV, true), true, FeatureFlagEvent.Action.UPDATED));

        assertThat(cacheService.isEnabled(FLAG, false)).isTrue();
    }

    @Test
    void updated_differentService_shouldNotUpdateCache() {
        cacheService.put(FLAG, false);

        processor.process(event(OTHER_SERVICE, Map.of(DEV, true), true, FeatureFlagEvent.Action.UPDATED));

        assertThat(cacheService.isEnabled(FLAG, true)).isFalse();
    }

    @Test
    void updated_currentEnvNotInMap_shouldNotUpdateCache() {
        cacheService.put(FLAG, false);

        processor.process(event(CHECKOUT_SERVICE, Map.of(PROD, true), true, FeatureFlagEvent.Action.UPDATED));

        assertThat(cacheService.isEnabled(FLAG, true)).isFalse();
    }

    // --- TOGGLED ---

    @Test
    void toggled_currentEnvEnabled_shouldUpdateCacheAsTrue() {
        cacheService.put(FLAG, false);

        processor.process(event(CHECKOUT_SERVICE, Map.of(DEV, true), true, FeatureFlagEvent.Action.TOGGLED));

        assertThat(cacheService.isEnabled(FLAG, false)).isTrue();
    }

    @Test
    void toggled_globallyDisabled_shouldUpdateCacheAsFalseEvenWhenEnvEnabled() {
        cacheService.put(FLAG, true);

        processor.process(event(CHECKOUT_SERVICE, Map.of(DEV, true), false, FeatureFlagEvent.Action.TOGGLED));

        assertThat(cacheService.isEnabled(FLAG, true)).isFalse();
    }

    @Test
    void toggled_differentService_shouldNotUpdateCache() {
        cacheService.put(FLAG, false);

        processor.process(event(OTHER_SERVICE, Map.of(DEV, true), true, FeatureFlagEvent.Action.TOGGLED));

        assertThat(cacheService.isEnabled(FLAG, true)).isFalse();
    }

    @Test
    void toggled_currentEnvNotInMap_shouldNotUpdateCache() {
        cacheService.put(FLAG, false);

        processor.process(event(CHECKOUT_SERVICE, Map.of(PROD, true), true, FeatureFlagEvent.Action.TOGGLED));

        assertThat(cacheService.isEnabled(FLAG, true)).isFalse();
    }

    // --- DELETED ---

    @Test
    void deleted_shouldRemoveFlagFromCache() {
        cacheService.put(FLAG, true);

        processor.process(event(CHECKOUT_SERVICE, null, false, FeatureFlagEvent.Action.DELETED));

        assertThat(cacheService.size()).isZero();
    }

    @Test
    void deleted_shouldRemoveRegardlessOfService() {
        cacheService.put(FLAG, true);

        processor.process(event(OTHER_SERVICE, null, false, FeatureFlagEvent.Action.DELETED));

        assertThat(cacheService.size()).isZero();
    }

    @Test
    void deleted_flagNotInCache_shouldNotFail() {
        processor.process(event(CHECKOUT_SERVICE, null, false, FeatureFlagEvent.Action.DELETED));

        assertThat(cacheService.size()).isZero();
    }

    // --- helper ---

    private FeatureFlagEvent event(String service, Map<String, Boolean> environments,
                                   boolean enabled, FeatureFlagEvent.Action action) {
        return new FeatureFlagEvent(FLAG, service, environments, enabled, action);
    }
}
