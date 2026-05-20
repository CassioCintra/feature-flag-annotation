package cassio.annotations.exception;

public class FeatureDisabledException extends RuntimeException {

    private final String flagName;

    public FeatureDisabledException(String flagName) {
        super(String.format("Feature flag '%s' is disabled in this environment.", flagName));
        this.flagName = flagName;
    }

    public FeatureDisabledException(String flagName, String customMessage) {
        super(customMessage == null || customMessage.isBlank()
                ? String.format("Feature flag '%s' is disabled in this environment.", flagName)
                : customMessage);
        this.flagName = flagName;
    }

    public String getFlagName() {
        return flagName;
    }
}