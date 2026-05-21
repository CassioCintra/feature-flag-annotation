package cassio.annotations.messaging;

import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.config.FeatureFlagProperties;
import cassio.annotations.model.FeatureFlagEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureFlagEventProcessorTest {

    private FeatureFlagCacheService cacheService;
    private FeatureFlagEventProcessor processor;

    private static final String NEW_CHECKOUT = "new-checkout";
    private static final String CHECKOUT_SERVICE = "checkout-service";
    private static final String OTHER_SERVICE = "other-service";
    private static final String DEVELOPMENT = "dev";
    private static final String PRODUCTION = "prod";
    
    @BeforeEach
    void setUp() {
        FeatureFlagProperties properties = new FeatureFlagProperties();
        properties.setServiceName(CHECKOUT_SERVICE);
        properties.setEnvironment(DEVELOPMENT);

        cacheService = new FeatureFlagCacheService(properties);
        processor = new FeatureFlagEventProcessor(properties, cacheService);
    }

    @Test
    void create_shouldAddFlagToCacheAsDisabled() {
        processor.process(event(CHECKOUT_SERVICE, null, null, FeatureFlagEvent.Action.CREATED));

        assertThat(cacheService.isEnabled(NEW_CHECKOUT, true)).isFalse();
    }

    @Test
    void create_differentService_shouldNotAddToCache() {
        processor.process(event(OTHER_SERVICE, null, null, FeatureFlagEvent.Action.CREATED));

        assertThat(cacheService.size()).isZero();
    }

    @Test
    void create_shouldNotFilterByEnvironment() {
        processor.process(event(CHECKOUT_SERVICE, PRODUCTION, null, FeatureFlagEvent.Action.CREATED));

        assertThat(cacheService.isEnabled(NEW_CHECKOUT, true)).isFalse();
    }

    @Test
    void update_shouldUpdateCacheWithNewValue() {
        processor.process(event(CHECKOUT_SERVICE, DEVELOPMENT, true, FeatureFlagEvent.Action.UPDATED));

        assertThat(cacheService.isEnabled(NEW_CHECKOUT, false)).isTrue();
    }

    @Test
    void update_differentService_shouldNotUpdateCache() {
        cacheService.put(NEW_CHECKOUT, false);

        processor.process(event(OTHER_SERVICE, DEVELOPMENT, true, FeatureFlagEvent.Action.UPDATED));

        assertThat(cacheService.isEnabled(NEW_CHECKOUT, true)).isFalse();
    }

    @Test
    void update_differentEnvironment_shouldNotUpdateCache() {
        cacheService.put(NEW_CHECKOUT, false);

        processor.process(event(CHECKOUT_SERVICE, PRODUCTION, true, FeatureFlagEvent.Action.UPDATED));

        assertThat(cacheService.isEnabled(NEW_CHECKOUT, true)).isFalse();
    }

    @Test
    void delete_shouldRemoveFlagFromCache() {
        cacheService.put(NEW_CHECKOUT, true);

        processor.process(event(CHECKOUT_SERVICE, null, null, FeatureFlagEvent.Action.DELETED));

        assertThat(cacheService.size()).isZero();
    }

    @Test
    void delete_shouldRemoveRegardlessOfService() {
        cacheService.put(NEW_CHECKOUT, true);

        processor.process(event(OTHER_SERVICE, null, null, FeatureFlagEvent.Action.DELETED));

        assertThat(cacheService.size()).isZero();
    }

    @Test
    void delete_flagNotInCache_shouldNotFail() {
        assertThat(cacheService.size()).isZero();

        processor.process(event(CHECKOUT_SERVICE, null, null, FeatureFlagEvent.Action.DELETED));

        assertThat(cacheService.size()).isZero();
    }

    private FeatureFlagEvent event(String service, String env,
                                   Boolean enabled, FeatureFlagEvent.Action action) {
        return new FeatureFlagEvent(NEW_CHECKOUT, service, env, enabled, action);
    }
}
