package cassio.annotations.bootstrap;

import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.config.FeatureFlagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

public class FeatureFlagBootstrap {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagBootstrap.class);

    private final FeatureFlagProperties properties;
    private final FeatureFlagCacheService cacheService;
    private final RestClient restClient;

    public FeatureFlagBootstrap(FeatureFlagProperties properties,
                                FeatureFlagCacheService cacheService,
                                RestClient restClient) {
        this.properties = properties;
        this.cacheService = cacheService;
        this.restClient = restClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        String serviceName = properties.getServiceName();
        String environment = properties.getEnvironment();

        log.info("Starting feature flag bootstrap for service='{}' environment='{}'",
                serviceName, environment);

        try {
            Map<String, Boolean> flags = restClient.get()
                    .uri("/flags/{serviceName}/{environment}", serviceName, environment)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Boolean>>() {});

            if (flags == null || flags.isEmpty()) {
                log.warn("No flags returned by the flag microservice. " +
                        "Please verify if service '{}' is registered.", serviceName);
                return;
            }

            cacheService.populateAll(flags);
            log.info("Bootstrap completed. {} flag(s) loaded for environment '{}'.",
                    flags.size(), environment);

        } catch (RestClientException ex) {
            log.error("Feature flag bootstrap failed. " +
                            "Application will start with configured default values. Error: {}",
                    ex.getMessage());
        }
    }
}