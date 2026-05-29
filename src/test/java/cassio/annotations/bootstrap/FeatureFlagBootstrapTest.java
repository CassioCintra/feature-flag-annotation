package cassio.annotations.bootstrap;

import cassio.annotations.FeatureFlag;
import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.config.FeatureFlagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureFlagBootstrapTest {

    @Mock private FeatureFlagProperties properties;
    @Mock private FeatureFlagCacheService cacheService;
    @Mock private RestClient restClient;
    @Mock private FeatureFlagScanner scanner;

    @Mock private RestClient.RequestHeadersUriSpec<?> getUriSpec;
    @Mock private RestClient.RequestHeadersSpec<?> getHeadersSpec;
    @Mock private RestClient.ResponseSpec getResponseSpec;

    private FeatureFlagBootstrap bootstrap;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        bootstrap = new FeatureFlagBootstrap(properties, cacheService, restClient, scanner);

        when(properties.getServiceName()).thenReturn("test-service");
        when(properties.getEnvironment()).thenReturn("dev");
        lenient().when(properties.isStrict()).thenReturn(false);

        when(restClient.get()).thenReturn((RestClient.RequestHeadersUriSpec) getUriSpec);
        // lenient: overridden per-test when simulating server unavailability
        lenient().when(getUriSpec.uri(any(java.util.function.Function.class))).thenReturn((RestClient.RequestHeadersSpec) getHeadersSpec);
        lenient().when(getHeadersSpec.retrieve()).thenReturn(getResponseSpec);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPopulateCacheWithFlagsReturnedByServer() {
        when(scanner.scan()).thenReturn(List.of());
        when(getResponseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of(
                new FeatureFlagServerResponse("checkout_v2", true),
                new FeatureFlagServerResponse("dark_mode", false)
        ));

        bootstrap.bootstrap();

        ArgumentCaptor<Map<String, Boolean>> captor = ArgumentCaptor.forClass(Map.class);
        verify(cacheService).populateAll(captor.capture());
        assertThat(captor.getValue())
                .containsEntry("checkout_v2", true)
                .containsEntry("dark_mode", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSkipCachePopulationWhenServerReturnsEmptyList() {
        when(scanner.scan()).thenReturn(List.of());
        when(getResponseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of());

        bootstrap.bootstrap();

        verify(cacheService, never()).populateAll(any());
    }

    @Test
    void shouldNotPopulateCacheWhenServerIsUnavailable() {
        when(scanner.scan()).thenReturn(List.of());
        when(getUriSpec.uri(any(java.util.function.Function.class)))
                .thenThrow(new RestClientException("connection refused"));

        bootstrap.bootstrap();

        verify(cacheService, never()).populateAll(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowOnStartupWhenStrictModeActiveAndFlagMissingFromServer() {
        when(properties.isStrict()).thenReturn(true);
        when(scanner.scan()).thenReturn(List.of(annotationOf("payments_v3")));
        when(getResponseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of());

        assertThatThrownBy(() -> bootstrap.bootstrap())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payments_v3")
                .hasMessageContaining("test-service")
                .hasMessageContaining("dev");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotThrowWhenStrictModeActiveAndAllFlagsPresent() {
        when(properties.isStrict()).thenReturn(true);
        when(scanner.scan()).thenReturn(List.of(annotationOf("payments_v3")));
        when(getResponseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of(
                new FeatureFlagServerResponse("payments_v3", true)
        ));

        bootstrap.bootstrap();

        verify(cacheService).populateAll(any());
    }

    // --- helper ---

    private FeatureFlag annotationOf(String key) {
        return FakeAnnotatedService.class.getDeclaredMethods()[0].getAnnotation(FeatureFlag.class);
    }

    static class FakeAnnotatedService {
        @FeatureFlag(key = "payments_v3")
        public void process() {}
    }
}
