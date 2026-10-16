package cassio.annotations.consul;

import cassio.annotations.FeatureFlag;
import cassio.annotations.bootstrap.FeatureFlagScanner;
import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.config.FeatureFlagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Keeps the flag cache in sync with Consul.
 *
 * <p>On startup it loads every flag under {@code feature-flags/{workspace}/{service}/{environment}/} and
 * applies strict mode, then watches that prefix with blocking queries on a virtual thread. The workspace
 * is resolved on the first read and retried like any other read failure. Each change replaces
 * the cache, so a key removed from Consul falls back to the defaults. If Consul is unavailable, the
 * application starts with the defaults and the watch keeps retrying. After a failure the watch reads
 * from index 0, so a restarted Consul, whose index starts over, is seen as soon as it answers.
 */
public class ConsulFlagSync implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ConsulFlagSync.class);

    private final FeatureFlagProperties properties;
    private final FeatureFlagCacheService cacheService;
    private final FeatureFlagScanner scanner;
    private final ConsulClient client;
    private final Supplier<UUID> workspaceId;
    private final Duration wait;
    private final Duration retryDelay;
    private volatile String prefix;
    private volatile boolean running;
    private Thread watcher;

    /**
     * Creates the sync. Nothing is read until {@link #start()}.
     *
     * @param properties   library configuration
     * @param cacheService cache replaced on every change
     * @param scanner      finds the annotated flags checked by strict mode
     * @param client       Consul client
     * @param workspaceId  resolves the workspace whose flags are read
     * @param wait         maximum time each blocking query waits for a change
     * @param retryDelay   pause after a failed read before trying again
     */
    public ConsulFlagSync(FeatureFlagProperties properties,
                          FeatureFlagCacheService cacheService,
                          FeatureFlagScanner scanner,
                          ConsulClient client,
                          Supplier<UUID> workspaceId,
                          Duration wait,
                          Duration retryDelay) {
        this.properties = properties;
        this.cacheService = cacheService;
        this.scanner = scanner;
        this.client = client;
        this.workspaceId = workspaceId;
        this.wait = wait;
        this.retryDelay = retryDelay;
    }

    /**
     * Loads the flags, applies strict mode and starts the watch.
     *
     * @throws IllegalStateException if strict mode is on and an annotated flag is missing in Consul
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        var initialIndex = 0L;
        var snapshot = readInitialSnapshot();
        if (snapshot != null) {
            cacheService.replaceAll(snapshot.flags());
            initialIndex = snapshot.index();
            enforceStrictMode(scanner.scan(), snapshot.flags().keySet());
        }

        running = true;
        var startIndex = initialIndex;
        watcher = Thread.ofVirtual().name("feature-flag-consul-watch").start(() -> watch(startIndex));
    }

    /**
     * Stops the watch.
     */
    @Override
    public void destroy() {
        running = false;
        if (watcher != null) watcher.interrupt();
    }

    private String prefix() {
        if (prefix == null) {
            prefix = "feature-flags/" + workspaceId.get() + "/" + properties.getServiceName()
                    + "/" + properties.getEnvironment() + "/";
            log.info("Loading feature flags from Consul prefix '{}'", prefix);
        }
        return prefix;
    }

    private ConsulClient.Snapshot readInitialSnapshot() {
        try {
            return client.read(prefix(), 0, Duration.ZERO);
        } catch (RuntimeException ex) {
            log.error("Could not load feature flags from Consul. " +
                    "Application will start with configured default values. Error: {}", ex.getMessage());
            return null;
        }
    }

    private void watch(long index) {
        while (running) {
            try {
                var snapshot = client.read(prefix(), index, wait);
                if (snapshot.index() != index) cacheService.replaceAll(snapshot.flags());
                index = indexWentBackwards(snapshot, index) ? 0 : snapshot.index();
            } catch (RuntimeException ex) {
                if (!running) return;
                log.warn("Consul watch failed. Retrying in {}s. Error: {}", retryDelay.toSeconds(), ex.getMessage());
                index = 0;
                if (!sleepBeforeRetry()) return;
            }
        }
    }

    private static boolean indexWentBackwards(ConsulClient.Snapshot snapshot, long previousIndex) {
        return snapshot.index() < previousIndex;
    }

    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(retryDelay);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    private void enforceStrictMode(List<FeatureFlag> annotated, Set<String> consulKeys) {
        if (!properties.isStrict()) return;

        var missing = annotated.stream()
                .map(this::resolveKey)
                .filter(key -> !key.isEmpty() && !consulKeys.contains(key))
                .collect(Collectors.toSet());

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Strict mode is active. The following feature flags are annotated in the code " +
                    "but not found in Consul for service '" + properties.getServiceName() +
                    "' / environment '" + properties.getEnvironment() + "': " + missing +
                    ". Register them before starting this application.");
        }
    }

    private String resolveKey(FeatureFlag annotation) {
        return annotation.key().isEmpty() ? annotation.value() : annotation.key();
    }
}
