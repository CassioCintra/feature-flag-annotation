package cassio.annotations.kafka;

import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.config.FeatureFlagProperties;
import cassio.annotations.model.FeatureFlagEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;

public class FeatureFlagKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagKafkaConsumer.class);

    private final FeatureFlagProperties properties;
    private final FeatureFlagCacheService cacheService;

    public FeatureFlagKafkaConsumer(FeatureFlagProperties properties,
                                    FeatureFlagCacheService cacheService) {
        this.properties = properties;
        this.cacheService = cacheService;
    }

    @KafkaListener(
            topics = "${feature-flag.kafka.topic:feature-flags.events}",
            groupId = "${feature-flag.kafka.group-id:feature-flag-consumer}",
            containerFactory = "featureFlagKafkaListenerContainerFactory"
    )
    public void consume(FeatureFlagEvent event) {
        log.debug("Event received: {}", event);

        switch (event.getAction()) {
            case CREATED -> validateAndProcessCreateEvent(event);
            case UPDATED -> validateAndProcessUpdateEvent(event);
            case DELETED -> validateAndProcessDeleteEvent(event);
        }
    }

    private void validateAndProcessDeleteEvent(FeatureFlagEvent event) {
        if (cacheService.contains(event.getFlagName())) {
            cacheService.evict(event.getFlagName());
            log.info("Flag '{}' removed from cache.", event.getFlagName());
        }
    }

    private void validateAndProcessUpdateEvent(FeatureFlagEvent event) {
        if (isNotServiceRelevant(event) || isNotEnvironmentRelevant(event)) {
            log.debug("Event ignored (different service or environment): {}", event);
            return;
        }
        cacheService.put(event.getFlagName(), event.isEnabled());
        log.info("Flag '{}' updated via Kafka: enabled={} [environment={}]",
                event.getFlagName(), event.isEnabled(), event.getEnvironmentName());
    }

    private void validateAndProcessCreateEvent(FeatureFlagEvent event) {
        if (isNotServiceRelevant(event)) {
            log.debug("Event ignored (different service): {}", event);
            return;
        }
        cacheService.put(event.getFlagName(), false);
        log.info("Flag '{}' added to cache: enabled=false [all environments]",
                event.getFlagName());
    }

    private boolean isNotEnvironmentRelevant(FeatureFlagEvent event) {
        return !properties.getEnvironment().equals(event.getEnvironmentName());
    }

    private boolean isNotServiceRelevant(FeatureFlagEvent event) {
        return !properties.getServiceName().equals(event.getServiceName());
    }
}