package cassio.annotations.model;

import cassio.annotations.utils.JsonUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FeatureFlagEvent {

    private String flagName;
    private String serviceName;
    private Map<String, Boolean> environments;
    private Boolean enabled;
    private Action action;

    public enum Action {
        CREATED,
        UPDATED,
        TOGGLED,
        DELETED
    }

    public FeatureFlagEvent() {}

    public FeatureFlagEvent(String flagName, String serviceName, Map<String, Boolean> environments,
                            Boolean enabled, Action action) {
        this.flagName = flagName;
        this.serviceName = serviceName;
        this.environments = environments;
        this.enabled = enabled;
        this.action = action;
    }

    public String getFlagName() { return flagName; }
    public void setFlagName(String flagName) { this.flagName = flagName; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public Map<String, Boolean> getEnvironments() { return environments; }
    public void setEnvironments(Map<String, Boolean> environments) { this.environments = environments; }

    public Boolean isEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action; }

    @Override
    public String toString() {
        return JsonUtils.toJson(this);
    }
}
