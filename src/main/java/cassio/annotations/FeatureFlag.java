package cassio.annotations;

import cassio.annotations.model.FlagType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method, class, or field as protected by a feature flag.
 *
 * <p>Short form (backward-compatible):
 * <pre>{@code
 * @FeatureFlag("new-checkout")
 * public ResponseEntity<Order> checkout(OrderRequest request) { ... }
 * }</pre>
 *
 * <p>Full form with metadata used for auto-registration on startup:
 * <pre>{@code
 * @FeatureFlag(
 *   key       = "checkout_v2",
 *   type      = FlagType.ROLLOUT,
 *   rollout   = 30,
 *   envs      = { "production", "staging" },
 *   tags      = { "payments", "checkout" },
 *   service   = "checkout-api",
 *   owner     = "payments-team",
 *   expiresAt = "2026-09-01"
 * )
 * public void processCheckout() { ... }
 * }</pre>
 *
 * <p>Configure in the consumer service's application.yml:
 * <pre>
 * feature-flag:
 *   service-name: checkout-service
 *   environment: ${spring.profiles.active:dev}
 *   flag-service-url: http://ms-feature-flags:8081/feature-flag/v1
 *   strict: true   # blocks startup if any annotated flag is missing from the server
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FeatureFlag {

    /** Shorthand for {@link #key()} — {@code @FeatureFlag("foo")} kept for backward compatibility. */
    String value() default "";

    /** Named flag key. Takes precedence over {@link #value()} when set. */
    String key() default "";

    FlagType type() default FlagType.BOOLEAN;

    /** Percentage of traffic that receives this flag (0–100). Relevant for ROLLOUT type. */
    int rollout() default 100;

    /** Environments where this flag applies, e.g. {@code {"production", "staging"}}. */
    String[] envs() default {};

    String[] tags() default {};

    /** Overrides the global {@code feature-flag.service-name} for this specific flag. */
    String service() default "";

    String owner() default "";

    /** ISO date after which this flag should be removed, e.g. {@code "2026-09-01"}. */
    String expiresAt() default "";

    /**
     * Default value used when the flag is absent from cache
     * (bootstrap failed or flag not yet received via messaging).
     */
    boolean enabledByDefault() default false;

    /** Message included in the exception when the flag is disabled. */
    String message() default "";
}