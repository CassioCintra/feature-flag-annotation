package cassio.annotations.bootstrap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FeatureFlagServerResponse(String flagName, boolean enabled) {}
