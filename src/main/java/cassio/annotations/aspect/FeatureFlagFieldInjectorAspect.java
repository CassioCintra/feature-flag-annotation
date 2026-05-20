package cassio.annotations.aspect;

import cassio.annotations.FeatureFlag;
import cassio.annotations.cache.FeatureFlagCacheService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.util.ReflectionUtils;

/**
 * AOP Aspect responsible for injecting the current value of a feature flag
 * into fields annotated with {@link FeatureFlag} before each method execution.
 *
 * <p>This aspect complements {@link FeatureFlagAspect}, which blocks or allows
 * method execution. This one keeps {@code Boolean} fields up to date with the
 * live cache state — updated by Kafka events — so that in-method conditional
 * logic always reflects the current flag value.
 *
 * <p>Runs with {@code @Order(1)} to guarantee fields are updated before
 * {@link FeatureFlagAspect} (default order) evaluates them.
 *
 * <p>Usage:
 * <pre>{@code
 * @Service
 * public class PaymentService {
 *
 *     @FeatureFlag("new-payment-flow")
 *     private Boolean newPaymentFlowEnabled;
 *
 *     public void process(Order order) {
 *         if (Boolean.TRUE.equals(newPaymentFlowEnabled)) {
 *             // new flow
 *         } else {
 *             // legacy flow
 *         }
 *     }
 * }
 * }</pre>
 */
@Aspect
@Order(1)
public class FeatureFlagFieldInjectorAspect {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagFieldInjectorAspect.class);

    private final FeatureFlagCacheService cacheService;

    public FeatureFlagFieldInjectorAspect(FeatureFlagCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Around("within(@org.springframework.stereotype.Service *)" +
            " || within(@org.springframework.stereotype.Component *)")
    public Object injectFlagFields(ProceedingJoinPoint pjp) throws Throwable {
        Object target = pjp.getTarget();

        ReflectionUtils.doWithFields(target.getClass(), field -> {
            FeatureFlag annotation = field.getAnnotation(FeatureFlag.class);
            if (annotation == null || !field.getType().equals(Boolean.class)) return;

            field.setAccessible(true);
            boolean value = cacheService.isEnabled(annotation.value(), annotation.enabledByDefault());
            ReflectionUtils.setField(field, target, value);

            log.debug("Field '{}' updated with flag '{}' = {}", field.getName(), annotation.value(), value);
        });

        return pjp.proceed();
    }
}
