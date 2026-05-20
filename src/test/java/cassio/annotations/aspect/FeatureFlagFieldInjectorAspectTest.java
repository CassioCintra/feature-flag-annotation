package cassio.annotations.aspect;

import cassio.annotations.cache.FeatureFlagCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = FeatureFlagFieldInjectorAspectTestConfig.class)
class FeatureFlagFieldInjectorAspectTest {

    @Autowired
    private FakeServiceWithFlagField fakeService;

    @Autowired
    private FeatureFlagCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService.evict(FakeServiceWithFlagField.FLAG_FIELD);
        cacheService.evict(FakeServiceWithFlagField.FLAG_FIELD_DEFAULT);
    }

    @Test
    void flagEnabled_shouldFollowNewFlow() {
        cacheService.put(FakeServiceWithFlagField.FLAG_FIELD, true);

        assertThat(fakeService.executeWithFlagField())
                .isEqualTo(FakeServiceWithFlagField.RESPONSE_NEW_FLOW);
    }

    @Test
    void flagDisabled_shouldFollowOldFlow() {
        cacheService.put(FakeServiceWithFlagField.FLAG_FIELD, false);

        assertThat(fakeService.executeWithFlagField())
                .isEqualTo(FakeServiceWithFlagField.RESPONSE_OLD_FLOW);
    }

    @Test
    void cacheMiss_enabledByDefaultTrue_shouldFollowNewFlow() {
        assertThat(fakeService.executeWithFlagFieldDefault())
                .isEqualTo(FakeServiceWithFlagField.RESPONSE_NEW_FLOW);
    }

    @Test
    void cacheMiss_enabledByDefaultFalse_shouldFollowOldFlow() {
        assertThat(fakeService.executeWithFlagField())
                .isEqualTo(FakeServiceWithFlagField.RESPONSE_OLD_FLOW);
    }

    @Test
    void flagUpdatedInCache_shouldReflectImmediately() {
        cacheService.put(FakeServiceWithFlagField.FLAG_FIELD, false);
        assertThat(fakeService.executeWithFlagField())
                .isEqualTo(FakeServiceWithFlagField.RESPONSE_OLD_FLOW);

        cacheService.put(FakeServiceWithFlagField.FLAG_FIELD, true);
        assertThat(fakeService.executeWithFlagField())
                .isEqualTo(FakeServiceWithFlagField.RESPONSE_NEW_FLOW);
    }
}
