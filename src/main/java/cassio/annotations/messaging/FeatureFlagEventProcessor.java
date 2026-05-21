package cassio.annotations.messaging;

import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.config.FeatureFlagProperties;
import cassio.annotations.model.FeatureFlagEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared event processing logic for all messaging consumers (Kafka, RabbitMQ, ActiveMQ).
 *
 * <p>Encapsulates cache update rules and service/environment filtering,
 * keeping each consumer implementation focused solely on message reception.
 */
public class FeatureFlagEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagEventProcessor.class);

    private final FeatureFlagProperties properties;
    private final FeatureFlagCacheService cacheService;

    public FeatureFlagEventProcessor(FeatureFlagProperties properties,
                                     FeatureFlagCacheService cacheService) {
        this.properties = properties;
        this.cacheService = cacheService;
    }

    public void process(FeatureFlagEvent event) {
        log.debug("Event received: {}", event);
        switch (event.getAction()) {
            case CREATED -> processCreate(event);
            case UPDATED -> processUpdate(event);
            case DELETED -> processDelete(event);
        }
    }

    private void processCreate(FeatureFlagEvent event) {
        if (isNotServiceRelevant(event)) {
            log.debug("Event ignored (different service): {}", event);
            return;
        }
        cacheService.put(event.getFlagName(), false);
        log.info("Flag '{}' added to cache: enabled=false [all environments]", event.getFlagName());
    }

    private void processUpdate(FeatureFlagEvent event) {
        if (isNotServiceRelevant(event) || isNotEnvironmentRelevant(event)) {
            log.debug("Event ignored (different service or environment): {}", event);
            return;
        }
        cacheService.put(event.getFlagName(), event.isEnabled());
        log.info("Flag '{}' updated: enabled={} [environment={}]",
                event.getFlagName(), event.isEnabled(), event.getEnvironmentName());
    }

    private void processDelete(FeatureFlagEvent event) {
        if (cacheService.contains(event.getFlagName())) {
            cacheService.evict(event.getFlagName());
            log.info("Flag '{}' removed from cache.", event.getFlagName());
        }
    }

    private boolean isNotServiceRelevant(FeatureFlagEvent event) {
        return !properties.getServiceName().equals(event.getServiceName());
    }

    private boolean isNotEnvironmentRelevant(FeatureFlagEvent event) {
        return !properties.getEnvironment().equals(event.getEnvironmentName());
    }
}
