package cassio.annotations.messaging;

import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.config.FeatureFlagProperties;
import cassio.annotations.model.FeatureFlagEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

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
            case CREATED          -> processCreate(event);
            case UPDATED, TOGGLED -> processUpdateOrToggle(event);
            case DELETED          -> processDelete(event);
        }
    }

    private void processCreate(FeatureFlagEvent event) {
        if (isNotServiceRelevant(event)) {
            log.debug("Event ignored (different service): {}", event);
            return;
        }
        Boolean effective = resolveEffective(event);
        if (effective == null) {
            log.debug("Event ignored (environment '{}' not in flag environments): {}",
                    properties.getEnvironment(), event);
            return;
        }
        cacheService.put(event.getFlagName(), effective);
        log.info("Flag '{}' added to cache: enabled={}", event.getFlagName(), effective);
    }

    private void processUpdateOrToggle(FeatureFlagEvent event) {
        if (isNotServiceRelevant(event)) {
            log.debug("Event ignored (different service): {}", event);
            return;
        }
        Boolean effective = resolveEffective(event);
        if (effective == null) {
            log.debug("Event ignored (environment '{}' not in flag environments): {}",
                    properties.getEnvironment(), event);
            return;
        }
        cacheService.put(event.getFlagName(), effective);
        log.info("Flag '{}' updated in cache: enabled={} [action={}]",
                event.getFlagName(), effective, event.getAction());
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

    /**
     * Returns null when the current environment is not configured for this flag (event should be ignored).
     * Otherwise returns the effective enabled state: global enabled AND per-environment state.
     */
    private Boolean resolveEffective(FeatureFlagEvent event) {
        Map<String, Boolean> environments = event.getEnvironments();
        if (environments == null) return null;
        Boolean envState = environments.get(properties.getEnvironment());
        if (envState == null) return null;
        return Boolean.TRUE.equals(event.isEnabled()) && envState;
    }
}
