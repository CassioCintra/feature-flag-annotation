package cassio.annotations.aspect;

import cassio.annotations.FeatureFlag;
import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.exception.FeatureDisabledException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Blocks methods and classes annotated with {@link FeatureFlag} while their flag is disabled,
 * throwing {@link FeatureDisabledException}.
 */
@Aspect
public class FeatureFlagAspect {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagAspect.class);

    private final FeatureFlagCacheService cacheService;

    /**
     * Creates the aspect.
     *
     * @param cacheService source of the flag states
     */
    public FeatureFlagAspect(FeatureFlagCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * Checks the flag of an annotated method.
     *
     * @param pjp         the intercepted call
     * @param featureFlag the method annotation
     * @return the method result
     * @throws Throwable the method exception, or {@link FeatureDisabledException} when the flag is disabled
     */
    @Around("@annotation(featureFlag)")
    public Object aroundMethod(ProceedingJoinPoint pjp, FeatureFlag featureFlag) throws Throwable {
        return checkAndProceed(pjp, featureFlag);
    }

    /**
     * Checks the flag of an annotated class on each of its methods.
     *
     * @param pjp         the intercepted call
     * @param featureFlag the class annotation
     * @return the method result
     * @throws Throwable the method exception, or {@link FeatureDisabledException} when the flag is disabled
     */
    @Around("@within(featureFlag)")
    public Object aroundClass(ProceedingJoinPoint pjp, FeatureFlag featureFlag) throws Throwable {
        return checkAndProceed(pjp, featureFlag);
    }

    private Object checkAndProceed(ProceedingJoinPoint pjp, FeatureFlag featureFlag) throws Throwable {
        String flagName = featureFlag.key().isEmpty() ? featureFlag.value() : featureFlag.key();
        boolean enabled = cacheService.isEnabled(flagName, featureFlag.enabledByDefault());

        if (!enabled) {
            log.warn("Access blocked by feature flag '{}' at: {}",
                    flagName, pjp.getSignature().toShortString());
            throw new FeatureDisabledException(flagName, featureFlag.message());
        }

        log.debug("Feature flag '{}' is active. Executing: {}", flagName,
                pjp.getSignature().toShortString());
        return pjp.proceed();
    }
}