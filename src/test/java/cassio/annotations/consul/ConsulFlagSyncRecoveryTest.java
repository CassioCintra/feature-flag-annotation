package cassio.annotations.consul;

import cassio.annotations.bootstrap.FeatureFlagScanner;
import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.config.FeatureFlagProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsulFlagSyncRecoveryTest {

    private static final UUID WORKSPACE = UUID.fromString("7a1c0e2e-4d5b-4c8e-9f10-2b3c4d5e6f70");
    private static final String PREFIX = "feature-flags/" + WORKSPACE + "/checkout-service/dev/";
    private static final Duration WAIT = Duration.ofSeconds(30);

    private final ConsulClient client = mock(ConsulClient.class);
    private ConsulFlagSync sync;

    @AfterEach
    void stopSync() {
        if (sync != null) sync.destroy();
    }

    @Test
    void readsFromIndexZeroAfterAFailureSoARestartedConsulIsSeenAtOnce() {
        when(client.read(PREFIX, 0, Duration.ZERO))
                .thenReturn(new ConsulClient.Snapshot(50, Map.of("new-checkout", true)));
        when(client.read(PREFIX, 50, WAIT))
                .thenThrow(new ResourceAccessException("Consul restarted"));
        when(client.read(PREFIX, 0, WAIT))
                .thenReturn(new ConsulClient.Snapshot(30, Map.of("new-checkout", false)));
        when(client.read(eq(PREFIX), eq(30L), any())).thenAnswer(invocation -> {
            Thread.sleep(WAIT);
            return new ConsulClient.Snapshot(30, Map.of("new-checkout", false));
        });

        var properties = new FeatureFlagProperties();
        properties.setServiceName("checkout-service");
        properties.setEnvironment("dev");
        var cache = new FeatureFlagCacheService(properties);
        var scanner = mock(FeatureFlagScanner.class);
        when(scanner.scan()).thenReturn(List.of());

        sync = new ConsulFlagSync(properties, cache, scanner, client, () -> WORKSPACE, WAIT, Duration.ofMillis(100));
        sync.start();
        assertThat(cache.isEnabled("new-checkout", false)).isTrue();

        await().atMost(Duration.ofSeconds(5)).until(() -> !cache.isEnabled("new-checkout", true));
    }

    @Test
    void startsWithDefaultsAndRetriesWhenTheWorkspaceCannotBeResolvedYet() {
        when(client.read(eq(PREFIX), eq(0L), any()))
                .thenReturn(new ConsulClient.Snapshot(10, Map.of("new-checkout", true)));
        when(client.read(eq(PREFIX), eq(10L), any())).thenAnswer(invocation -> {
            Thread.sleep(WAIT);
            return new ConsulClient.Snapshot(10, Map.of("new-checkout", true));
        });
        var attempts = new AtomicInteger();
        Supplier<UUID> workspace = () -> {
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("ms-auth unavailable");
            return WORKSPACE;
        };

        var properties = new FeatureFlagProperties();
        properties.setServiceName("checkout-service");
        properties.setEnvironment("dev");
        var cache = new FeatureFlagCacheService(properties);
        var scanner = mock(FeatureFlagScanner.class);
        when(scanner.scan()).thenReturn(List.of());

        sync = new ConsulFlagSync(properties, cache, scanner, client, workspace, WAIT, Duration.ofMillis(100));
        sync.start();
        assertThat(cache.contains("new-checkout")).isFalse();

        await().atMost(Duration.ofSeconds(5)).until(() -> cache.isEnabled("new-checkout", false));
    }
}
