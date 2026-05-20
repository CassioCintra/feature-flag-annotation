package cassio.annotations.aspect;

import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.exception.FeatureDisabledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = FeatureFlagAspectTestConfig.class)
class FeatureFlagAspectTest {

    @Autowired
    private FakeService fakeService;

    @Autowired
    private FeatureFlagCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService.evict(FakeService.ACTIVE_FLAG);
        cacheService.evict(FakeService.DISABLED_FLAG);
        cacheService.evict(FakeService.NO_CACHE_FLAG);
    }

    @Test
    void activeFlag_shouldExecuteMethod() {
        cacheService.put(FakeService.ACTIVE_FLAG, true);

        assertThat(fakeService.methodWithActiveFlag()).isEqualTo(FakeService.RESPONSE_EXECUTED);
    }

    @Test
    void disabledFlag_shouldThrowFeatureDisabledException() {
        cacheService.put(FakeService.DISABLED_FLAG, false);

        assertThatThrownBy(() -> fakeService.methodWithDisabledFlag())
                .isInstanceOf(FeatureDisabledException.class)
                .hasMessageContaining(FakeService.DISABLED_FLAG);
    }

    @Test
    void disabledFlag_shouldThrowExceptionWithCustomMessage() {
        cacheService.put(FakeService.DISABLED_FLAG, false);

        assertThatThrownBy(() -> fakeService.methodWithCustomMessage())
                .isInstanceOf(FeatureDisabledException.class)
                .hasMessage(FakeService.FLAG_MESSAGE);
    }

    @Test
    void cacheMiss_enabledByDefaultTrue_shouldExecuteMethod() {
        assertThat(fakeService.methodWithDefaultTrue()).isEqualTo(FakeService.RESPONSE_EXECUTED);
    }

    @Test
    void cacheMiss_enabledByDefaultFalse_shouldThrowException() {
        assertThatThrownBy(() -> fakeService.methodWithDefaultFalse())
                .isInstanceOf(FeatureDisabledException.class);
    }

    @Test
    void flagUpdatedInCache_shouldReflectImmediately() {
        cacheService.put(FakeService.ACTIVE_FLAG, false);
        assertThatThrownBy(() -> fakeService.methodWithActiveFlag())
                .isInstanceOf(FeatureDisabledException.class);

        cacheService.put(FakeService.ACTIVE_FLAG, true);
        assertThat(fakeService.methodWithActiveFlag()).isEqualTo(FakeService.RESPONSE_EXECUTED);
    }
}