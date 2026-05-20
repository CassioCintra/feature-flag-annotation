package cassio.annotations.aspect;

import cassio.annotations.FeatureFlag;
import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.exception.FeatureDisabledException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
public class FeatureFlagAspect {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagAspect.class);

    private final FeatureFlagCacheService cacheService;

    public FeatureFlagAspect(FeatureFlagCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Around("@annotation(featureFlag)")
    public Object aroundMethod(ProceedingJoinPoint pjp, FeatureFlag featureFlag) throws Throwable {
        return checkAndProceed(pjp, featureFlag);
    }

    @Around("@within(featureFlag)")
    public Object aroundClass(ProceedingJoinPoint pjp, FeatureFlag featureFlag) throws Throwable {
        return checkAndProceed(pjp, featureFlag);
    }

    private Object checkAndProceed(ProceedingJoinPoint pjp, FeatureFlag featureFlag) throws Throwable {
        String flagName = featureFlag.value();
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