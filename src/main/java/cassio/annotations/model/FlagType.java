package cassio.annotations.model;

/**
 * Kind of feature flag.
 */
public enum FlagType {
    /** On or off. */
    BOOLEAN,
    /** Enabled for a percentage of traffic. */
    ROLLOUT,
    /** Selects one of several variants. */
    MULTIVARIATE
}
