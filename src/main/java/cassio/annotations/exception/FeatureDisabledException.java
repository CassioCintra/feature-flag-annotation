package cassio.annotations.exception;

/**
 * Thrown when a method or class protected by {@link cassio.annotations.FeatureFlag} is called while its flag is disabled.
 */
public class FeatureDisabledException extends RuntimeException {

    /** Name of the disabled flag. */
    private final String flagName;

    /**
     * Creates the exception with the default message.
     *
     * @param flagName name of the disabled flag
     */
    public FeatureDisabledException(String flagName) {
        super(String.format("Feature flag '%s' is disabled in this environment.", flagName));
        this.flagName = flagName;
    }

    /**
     * Creates the exception with a custom message, falling back to the default one when blank.
     *
     * @param flagName      name of the disabled flag
     * @param customMessage message from {@link cassio.annotations.FeatureFlag#message()}
     */
    public FeatureDisabledException(String flagName, String customMessage) {
        super(customMessage == null || customMessage.isBlank()
                ? String.format("Feature flag '%s' is disabled in this environment.", flagName)
                : customMessage);
        this.flagName = flagName;
    }

    /**
     * Name of the disabled flag.
     *
     * @return the flag name
     */
    public String getFlagName() {
        return flagName;
    }
}