package cassio.annotations.model;

import cassio.annotations.utils.JsonUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a feature flag event published by the flag management
 * microservice to Kafka.
 *
 * <p>CREATED payload — enabled and environmentName are null (flag starts disabled for all environments):
 * <pre>
 * {
 *   "flagName":    "new-checkout",
 *   "serviceName": "checkout-service",
 *   "action":      "CREATED"
 * }
 * </pre>
 *
 * <p>UPDATED payload — only the environment that changed:
 * <pre>
 * {
 *   "flagName":       "new-checkout",
 *   "serviceName":    "checkout-service",
 *   "environmentName": "prod",
 *   "enabled":        true,
 *   "action":         "UPDATED"
 * }
 * </pre>
 *
 * <p>DELETED payload — enabled and environmentName are null (removes from all environments):
 * <pre>
 * {
 *   "flagName":    "new-checkout",
 *   "serviceName": "checkout-service",
 *   "action":      "DELETED"
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeatureFlagEvent {
    private String flagName;
    private String serviceName;
    private String environmentName;
    private Boolean enabled;
    private Action action;

    public enum Action {
        CREATED,
        UPDATED,
        DELETED
    }

    public FeatureFlagEvent() {}

    public FeatureFlagEvent(String flagName, String serviceName, String environmentName,
                            Boolean enabled, Action action) {
        this.flagName = flagName;
        this.serviceName = serviceName;
        this.environmentName = environmentName;
        this.enabled = enabled;
        this.action = action;
    }

    public String getFlagName() { return flagName; }
    public void setFlagName(String flagName) { this.flagName = flagName; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getEnvironmentName() { return environmentName; }
    public void setEnvironmentName(String environmentName) { this.environmentName = environmentName; }

    public Boolean isEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action; }

    @Override
    public String toString() {
        return JsonUtils.toJson(this);
    }
}