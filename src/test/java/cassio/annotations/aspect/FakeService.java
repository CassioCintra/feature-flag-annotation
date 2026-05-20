package cassio.annotations.aspect;

import cassio.annotations.FeatureFlag;
import org.springframework.boot.test.context.TestComponent;

@TestComponent
public class FakeService {

    public static final String ACTIVE_FLAG = "active-flag";
    public static final String DISABLED_FLAG = "disabled-flag";
    public static final String NO_CACHE_FLAG = "no-cache-flag";
    public static final String FLAG_MESSAGE = "Feature unavailable.";
    public static final String RESPONSE_EXECUTED = "executed";

    @FeatureFlag(ACTIVE_FLAG)
    public String methodWithActiveFlag() {
        return RESPONSE_EXECUTED;
    }

    @FeatureFlag(DISABLED_FLAG)
    public void methodWithDisabledFlag() {
    }

    @FeatureFlag(value = DISABLED_FLAG, message = FLAG_MESSAGE)
    public void methodWithCustomMessage() {
    }

    @FeatureFlag(value = NO_CACHE_FLAG, enabledByDefault = true)
    public String methodWithDefaultTrue() {
        return RESPONSE_EXECUTED;
    }

    @FeatureFlag(value = NO_CACHE_FLAG, enabledByDefault = false)
    public void methodWithDefaultFalse() {
    }
}