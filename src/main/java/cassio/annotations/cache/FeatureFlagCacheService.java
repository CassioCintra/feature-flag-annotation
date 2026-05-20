package cassio.annotations.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import cassio.annotations.config.FeatureFlagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class FeatureFlagCacheService {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagCacheService.class);

    private final Cache<String, Boolean> cache;
    private final FeatureFlagProperties properties;

    public FeatureFlagCacheService(FeatureFlagProperties properties) {
        this.properties = properties;
        this.cache = Caffeine.newBuilder()
                .maximumSize(1_000)
                .recordStats()
                .build();
    }

    public boolean isEnabled(String flagName, boolean annotationDefault) {
        Boolean cached = cache.getIfPresent(flagName);

        if (cached != null) {
            log.debug("Flag '{}' resolved from cache: {}", flagName, cached);
            return cached;
        }

        boolean fallback = properties.getDefault(flagName, annotationDefault);
        log.warn("Flag '{}' not found in cache. Using fallback: {}", flagName, fallback);
        return fallback;
    }

    public void populateAll(Map<String, Boolean> flags) {
        cache.putAll(flags);
        log.info("Cache populated with {} flag(s) via HTTP bootstrap.", flags.size());
    }

    public void put(String flagName, boolean enabled) {
        cache.put(flagName, enabled);
        log.info("Flag '{}' updated in cache: {}", flagName, enabled);
    }

    public void evict(String flagName) {
        cache.invalidate(flagName);
        log.info("Flag '{}' removed from cache.", flagName);
    }

    public long size() {
        return cache.estimatedSize();
    }

    public boolean contains(String flagName) {
        return cache.getIfPresent(flagName) != null;
    }
}