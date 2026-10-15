package cassio.annotations.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import cassio.annotations.config.FeatureFlagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * In-memory state of the flags, filled from Consul, with the configured defaults as fallback.
 */
public class FeatureFlagCacheService {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagCacheService.class);

    private final Cache<String, Boolean> cache;
    private final FeatureFlagProperties properties;

    /**
     * Creates an empty cache and warns about defaults that are neither {@code true} nor {@code false}.
     *
     * @param properties library configuration, provides the defaults
     */
    public FeatureFlagCacheService(FeatureFlagProperties properties) {
        this.properties = properties;
        this.cache = Caffeine.newBuilder()
                .maximumSize(1_000)
                .recordStats()
                .build();
        properties.getDefaults().forEach((flagName, value) -> {
            if (!FeatureFlagProperties.isValidDefault(value)) {
                log.warn("Default for flag '{}' is '{}', not true or false. The flag will be treated as disabled.",
                        flagName, value);
            }
        });
    }

    /**
     * Current state of a flag, or its default when the flag is not in the cache.
     *
     * @param flagName          flag name
     * @param annotationDefault {@code enabledByDefault} from the annotation
     * @return whether the flag is enabled
     */
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

    /**
     * Replaces the cache content. Flags absent from {@code flags} are removed.
     *
     * @param flags flag name to enabled state
     */
    public void replaceAll(Map<String, Boolean> flags) {
        cache.putAll(flags);
        cache.asMap().keySet().retainAll(flags.keySet());
        log.info("Cache replaced with {} flag(s) from Consul.", flags.size());
    }

    /**
     * Sets the state of one flag.
     *
     * @param flagName flag name
     * @param enabled  new state
     */
    public void put(String flagName, boolean enabled) {
        cache.put(flagName, enabled);
        log.info("Flag '{}' updated in cache: {}", flagName, enabled);
    }

    /**
     * Removes a flag, so its default is used again.
     *
     * @param flagName flag name
     */
    public void evict(String flagName) {
        cache.invalidate(flagName);
        log.info("Flag '{}' removed from cache.", flagName);
    }

    /**
     * Approximate number of flags in the cache.
     *
     * @return the number of flags
     */
    public long size() {
        return cache.estimatedSize();
    }

    /**
     * Tells whether a flag is in the cache.
     *
     * @param flagName flag name
     * @return whether the flag is cached
     */
    public boolean contains(String flagName) {
        return cache.getIfPresent(flagName) != null;
    }
}