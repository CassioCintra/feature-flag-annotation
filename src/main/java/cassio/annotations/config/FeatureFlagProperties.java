package cassio.annotations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Library configuration properties read from application.properties.
 *
 * <p>Example configuration in the consumer service:
 * <pre>
 * feature-flag.service-name=checkout-service
 * feature-flag.environment=${spring.profiles.active:dev}
 * feature-flag.flag-service-url=http://flag-management-service
 *
 * # Defaults used only if the flag microservice is unavailable
 * feature-flag.defaults.new-checkout=false
 * feature-flag.defaults.pix-payment=false
 * </pre>
 */
@ConfigurationProperties(prefix = "feature-flag")
public class FeatureFlagProperties {

    private String serviceName;
    private String environment = "dev";
    private String flagServiceUrl;
    private Map<String, Boolean> defaults = new HashMap<>();

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getFlagServiceUrl() { return flagServiceUrl; }
    public void setFlagServiceUrl(String flagServiceUrl) { this.flagServiceUrl = flagServiceUrl; }

    public Map<String, Boolean> getDefaults() { return defaults; }
    public void setDefaults(Map<String, Boolean> defaults) { this.defaults = defaults; }

    /**
     * Returns the configured default for the flag, or the annotation default
     * if no properties default is defined.
     */
    public boolean getDefault(String flagName, boolean annotationDefault) {
        return defaults.getOrDefault(flagName, annotationDefault);
    }
}