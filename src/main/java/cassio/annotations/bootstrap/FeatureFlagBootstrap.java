package cassio.annotations.bootstrap;

import cassio.annotations.FeatureFlag;
import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.config.FeatureFlagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FeatureFlagBootstrap {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagBootstrap.class);

    private final FeatureFlagProperties properties;
    private final FeatureFlagCacheService cacheService;
    private final RestClient restClient;
    private final FeatureFlagScanner scanner;

    public FeatureFlagBootstrap(FeatureFlagProperties properties,
                                FeatureFlagCacheService cacheService,
                                RestClient restClient,
                                FeatureFlagScanner scanner) {
        this.properties = properties;
        this.cacheService = cacheService;
        this.restClient = restClient;
        this.scanner = scanner;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        String serviceName = properties.getServiceName();
        String environment = properties.getEnvironment();

        log.info("Starting feature flag bootstrap for service='{}' environment='{}'",
                serviceName, environment);

        List<FeatureFlag> annotated = scanner.scan();
        load(serviceName, environment, annotated);
    }

    private void load(String serviceName, String environment, List<FeatureFlag> annotated) {
        try {
            List<FeatureFlagServerResponse> response = restClient.get()
                    .uri(builder -> builder
                            .path("/flags")
                            .queryParam("service", serviceName)
                            .queryParam("env", environment)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || response.isEmpty()) {
                log.warn("No flags returned for service='{}' environment='{}'. " +
                        "Verify if the service is registered.", serviceName, environment);
                enforceStrictMode(annotated, Set.of());
                return;
            }

            Map<String, Boolean> flags = response.stream()
                    .collect(Collectors.toMap(
                            FeatureFlagServerResponse::flagName,
                            FeatureFlagServerResponse::enabled
                    ));

            cacheService.populateAll(flags);
            log.info("Bootstrap completed. {} flag(s) loaded for environment '{}'.",
                    flags.size(), environment);

            enforceStrictMode(annotated, flags.keySet());

        } catch (RestClientException ex) {
            log.error("Feature flag bootstrap failed. " +
                    "Application will start with configured default values. Error: {}", ex.getMessage());
        }
    }

    private void enforceStrictMode(List<FeatureFlag> annotated, Set<String> serverKeys) {
        if (!properties.isStrict()) return;

        Set<String> missing = annotated.stream()
                .map(this::resolveKey)
                .filter(key -> !key.isEmpty() && !serverKeys.contains(key))
                .collect(Collectors.toSet());

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Strict mode is active. The following feature flags are annotated in the code " +
                    "but not found in the flag server for service '" + properties.getServiceName() +
                    "' / environment '" + properties.getEnvironment() + "': " + missing +
                    ". Register them before starting this application.");
        }
    }

    private String resolveKey(FeatureFlag annotation) {
        return annotation.key().isEmpty() ? annotation.value() : annotation.key();
    }
}
