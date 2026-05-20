package cassio.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method or class as protected by a feature flag.
 *
 * <p>When invoked, the method checks the local cache to determine if the flag
 * is active for the configured service and environment. The cache is populated
 * on application startup via HTTP bootstrap and kept up to date via Kafka events.
 *
 * <p>Usage examples:
 * <pre>{@code
 * @FeatureFlag("new-checkout")
 * public ResponseEntity<Order> checkout(OrderRequest request) {
 *     // executes only if "new-checkout" is active in the current environment
 * }
 *
 * @FeatureFlag(value = "pix-payment", enabledByDefault = false, message = "Pix unavailable.")
 * public void payWithPix() { ... }
 * }</pre>
 *
 * <p>Configure in the consumer service's application.properties:
 * <pre>
 * feature-flag.service-name=checkout-service
 * feature-flag.environment=${spring.profiles.active:dev}
 * feature-flag.flag-service-url=http://flag-management-service
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FeatureFlag {

    /**
     * The feature flag name — must match the name registered
     * in the flag management microservice.
     */
    String value();

    /**
     * Default value used when:
     * - The HTTP bootstrap failed (flag microservice unavailable)
     * - The flag has not yet been received via Kafka
     *
     * Default: false (safe — feature disabled by default)
     */
    boolean enabledByDefault() default false;

    /**
     * Message included in the exception when the flag is disabled.
     * If empty, a default message is used.
     */
    String message() default "";
}