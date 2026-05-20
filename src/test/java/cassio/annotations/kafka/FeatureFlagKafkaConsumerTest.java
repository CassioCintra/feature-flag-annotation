package cassio.annotations.kafka;

import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.config.FeatureFlagProperties;
import cassio.annotations.model.FeatureFlagEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureFlagKafkaConsumerTest {

    private FeatureFlagCacheService cacheService;
    private FeatureFlagKafkaConsumer kafkaConsumer;

    @BeforeEach
    void setUp() {
        FeatureFlagProperties properties = new FeatureFlagProperties();
        properties.setServiceName("checkout-service");
        properties.setEnvironment("dev");

        cacheService = new FeatureFlagCacheService(properties);
        kafkaConsumer = new FeatureFlagKafkaConsumer(properties, cacheService);
    }

    @Test
    void createdEvent_shouldAddFlagToCacheAsDisabled() {
        FeatureFlagEvent event = new FeatureFlagEvent(
                "new-checkout", "checkout-service", null, null, FeatureFlagEvent.Action.CREATED);

        kafkaConsumer.consume(event);

        assertThat(cacheService.isEnabled("new-checkout", true)).isFalse();
    }

    @Test
    void createdEvent_differentService_shouldNotAddToCache() {
        FeatureFlagEvent event = new FeatureFlagEvent(
                "new-checkout", "other-service", null, null, FeatureFlagEvent.Action.CREATED);

        kafkaConsumer.consume(event);

        assertThat(cacheService.size()).isEqualTo(0);
    }

    @Test
    void createdEvent_shouldNotFilterByEnvironment() {
        FeatureFlagEvent event = new FeatureFlagEvent(
                "new-checkout", "checkout-service", "prod", null, FeatureFlagEvent.Action.CREATED);

        kafkaConsumer.consume(event);

        assertThat(cacheService.isEnabled("new-checkout", true)).isFalse();
    }

    @Test
    void updatedEvent_shouldUpdateCacheWithNewValue() {
        FeatureFlagEvent event = new FeatureFlagEvent(
                "new-checkout", "checkout-service", "dev", true, FeatureFlagEvent.Action.UPDATED);

        kafkaConsumer.consume(event);

        assertThat(cacheService.isEnabled("new-checkout", false)).isTrue();
    }

    @Test
    void updatedEvent_differentService_shouldNotUpdateCache() {
        cacheService.put("new-checkout", false);

        FeatureFlagEvent event = new FeatureFlagEvent(
                "new-checkout", "other-service", "dev", true, FeatureFlagEvent.Action.UPDATED);

        kafkaConsumer.consume(event);

        assertThat(cacheService.isEnabled("new-checkout", true)).isFalse();
    }

    @Test
    void updatedEvent_differentEnvironment_shouldNotUpdateCache() {
        cacheService.put("new-checkout", false);

        FeatureFlagEvent event = new FeatureFlagEvent(
                "new-checkout", "checkout-service", "prod", true, FeatureFlagEvent.Action.UPDATED);

        kafkaConsumer.consume(event);

        assertThat(cacheService.isEnabled("new-checkout", true)).isFalse();
    }

    @Test
    void deletedEvent_shouldRemoveFlagFromCache() {
        cacheService.put("new-checkout", true);

        FeatureFlagEvent event = new FeatureFlagEvent(
                "new-checkout", "checkout-service", "dev", null, FeatureFlagEvent.Action.DELETED);

        kafkaConsumer.consume(event);

        assertThat(cacheService.isEnabled("new-checkout", true)).isTrue();
        assertThat(cacheService.isEnabled("new-checkout", false)).isFalse();
    }

    @Test
    void deletedEvent_shouldRemoveRegardlessOfService() {
        cacheService.put("new-checkout", true);

        FeatureFlagEvent event = new FeatureFlagEvent(
                "new-checkout", "other-service", "prod", null, FeatureFlagEvent.Action.DELETED);

        kafkaConsumer.consume(event);

        assertThat(cacheService.size()).isEqualTo(0);
    }

    @Test
    void deletedEvent_flagNotInCache_shouldNotLogRemoval() {
        assertThat(cacheService.size()).isEqualTo(0);

        FeatureFlagEvent event = new FeatureFlagEvent(
                "new-checkout", "checkout-service", null, null, FeatureFlagEvent.Action.DELETED);

        kafkaConsumer.consume(event);

        assertThat(cacheService.size()).isEqualTo(0);
    }
}