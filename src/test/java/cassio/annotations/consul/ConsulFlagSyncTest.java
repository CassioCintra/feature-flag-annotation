package cassio.annotations.consul;

import cassio.annotations.FeatureFlag;
import cassio.annotations.bootstrap.FeatureFlagScanner;
import cassio.annotations.cache.FeatureFlagCacheService;
import cassio.annotations.config.FeatureFlagProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.testcontainers.consul.ConsulContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
class ConsulFlagSyncTest {

    private static final String MANAGEMENT_TOKEN = "root";

    @Container
    static final ConsulContainer CONSUL = new ConsulContainer("hashicorp/consul:2.0.4")
            .withEnv("CONSUL_LOCAL_CONFIG", """
                    {"acl": {"enabled": true, "default_policy": "deny",
                             "tokens": {"initial_management": "%s"}}}""".formatted(MANAGEMENT_TOKEN));

    private static RSAKey signingKey;
    private static RestClient admin;

    private final UUID workspace = UUID.randomUUID();
    private final String service = "svc-" + UUID.randomUUID();
    private final AtomicInteger logins = new AtomicInteger();
    private final FeatureFlagScanner scanner = mock(FeatureFlagScanner.class);
    private FeatureFlagProperties properties;
    private FeatureFlagCacheService cache;
    private ConsulFlagSync sync;

    @BeforeAll
    static void configureAcl() throws JOSEException {
        signingKey = new RSAKeyGenerator(2048).generate();
        var publicKeyPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(signingKey.toRSAPublicKey().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";

        admin = RestClient.builder()
                .baseUrl(consulUrl())
                .defaultHeader("X-Consul-Token", MANAGEMENT_TOKEN)
                .build();
        admin.put().uri("/v1/acl/policy")
                .body(Map.of("Name", "flags-read", "Rules", "key_prefix \"feature-flags/\" { policy = \"read\" }"))
                .retrieve().toBodilessEntity();
        admin.put().uri("/v1/acl/role")
                .body(Map.of("Name", "flags-reader", "Policies", List.of(Map.of("Name", "flags-read"))))
                .retrieve().toBodilessEntity();
        admin.put().uri("/v1/acl/auth-method")
                .body(Map.of("Name", "ms-auth", "Type", "jwt", "MaxTokenTTL", "15m", "Config", Map.of(
                        "JWTValidationPubKeys", List.of(publicKeyPem),
                        "BoundAudiences", List.of("consul"),
                        "ListClaimMappings", Map.of("scope", "scopes"),
                        "ClaimMappings", Map.of("sub", "client_id"))))
                .retrieve().toBodilessEntity();
        admin.put().uri("/v1/acl/binding-rule")
                .body(Map.of("AuthMethod", "ms-auth", "BindType", "role", "BindName", "flags-reader",
                        "Selector", "\"flags:read\" in list.scopes"))
                .retrieve().toBodilessEntity();
    }

    @AfterEach
    void stopSync() {
        if (sync != null) sync.destroy();
    }

    @Test
    void loadsOnlyTheFlagsOfItsServiceAndEnvironment() {
        putFlag(workspace + "/" + service + "/dev/new-checkout", "{\"enabled\": true}");
        putFlag(workspace + "/" + service + "/dev/pix-payment", "{\"enabled\": false}");
        putFlag(workspace + "/" + service + "/dev/broken", "not json");
        putFlag(workspace + "/" + service + "/prod/prod-only", "{\"enabled\": true}");
        putFlag(workspace + "/other-service/dev/other-flag", "{\"enabled\": true}");
        putFlag(UUID.randomUUID() + "/" + service + "/dev/other-workspace-flag", "{\"enabled\": true}");

        startSync(consulUrl());

        assertThat(cache.isEnabled("new-checkout", false)).isTrue();
        assertThat(cache.isEnabled("pix-payment", true)).isFalse();
        assertThat(cache.contains("broken")).isFalse();
        assertThat(cache.contains("prod-only")).isFalse();
        assertThat(cache.contains("other-flag")).isFalse();
        assertThat(cache.contains("other-workspace-flag")).isFalse();
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    void followsChangesAndRemovalsInConsul() {
        putFlag(workspace + "/" + service + "/dev/new-checkout", "{\"enabled\": false}");
        startSync(consulUrl());
        assertThat(cache.isEnabled("new-checkout", true)).isFalse();

        putFlag(workspace + "/" + service + "/dev/new-checkout", "{\"enabled\": true}");
        await().atMost(Duration.ofSeconds(10)).until(() -> cache.isEnabled("new-checkout", false));

        putFlag(workspace + "/" + service + "/dev/pix-payment", "{\"enabled\": true}");
        await().atMost(Duration.ofSeconds(10)).until(() -> cache.contains("pix-payment"));

        admin.delete().uri("/v1/kv/feature-flags/" + workspace + "/" + service + "/dev/new-checkout").retrieve().toBodilessEntity();
        await().atMost(Duration.ofSeconds(10)).until(() -> !cache.contains("new-checkout"));
        assertThat(cache.isEnabled("new-checkout", false)).isFalse();
    }

    @Test
    void logsInAgainWhenConsulRejectsTheToken() {
        startSync(consulUrl());
        assertThat(logins).hasValue(1);

        var tokens = admin.get().uri("/v1/acl/tokens?authmethod=ms-auth")
                .retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        tokens.forEach(token -> admin.delete().uri("/v1/acl/token/" + token.get("AccessorID"))
                .retrieve().toBodilessEntity());

        putFlag(workspace + "/" + service + "/dev/new-checkout", "{\"enabled\": true}");

        await().atMost(Duration.ofSeconds(10)).until(() -> cache.isEnabled("new-checkout", false));
        assertThat(logins.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void strictModeBlocksStartupWhenAnAnnotatedFlagIsMissing() {
        putFlag(workspace + "/" + service + "/dev/new-checkout", "{\"enabled\": true}");
        when(scanner.scan()).thenReturn(List.of(
                StrictService.class.getAnnotation(FeatureFlag.class)));

        assertThatThrownBy(() -> startSync(consulUrl(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing-flag");
    }

    @Test
    void startsWithDefaultsWhenConsulIsUnavailable() {
        startSync("http://localhost:1");

        assertThat(cache.size()).isZero();
        assertThat(cache.isEnabled("new-checkout", true)).isTrue();
    }

    @FeatureFlag("missing-flag")
    private static class StrictService {}

    private void startSync(String url) {
        startSync(url, false);
    }

    private void startSync(String url, boolean strict) {
        properties = new FeatureFlagProperties();
        properties.setServiceName(service);
        properties.setEnvironment("dev");
        properties.setStrict(strict);
        cache = new FeatureFlagCacheService(properties);

        var client = new ConsulClient(url, "ms-auth", this::signedJwt, Duration.ofSeconds(40));
        sync = new ConsulFlagSync(properties, cache, scanner, client, () -> ConsulWorkspace.fromJwt(jwt()),
                Duration.ofSeconds(30), Duration.ofMillis(200));
        sync.start();
    }

    private String signedJwt() {
        logins.incrementAndGet();
        return jwt();
    }

    private String jwt() {
        try {
            var claims = new JWTClaimsSet.Builder()
                    .subject("checkout-service")
                    .audience("consul")
                    .claim("scope", List.of("flags:read"))
                    .claim("workspace_id", workspace.toString())
                    .issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                    .build();
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(signingKey));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void putFlag(String path, String value) {
        admin.put().uri("/v1/kv/feature-flags/" + path).body(value).retrieve().toBodilessEntity();
    }

    private static String consulUrl() {
        return "http://" + CONSUL.getHost() + ":" + CONSUL.getMappedPort(8500);
    }
}
