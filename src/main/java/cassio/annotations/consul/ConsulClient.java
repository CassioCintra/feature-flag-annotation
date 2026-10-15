package cassio.annotations.consul;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads feature flags from the Consul KV store.
 *
 * <p>Each key under a prefix is one flag holding {@code {"enabled": true|false}}; keys with any
 * other value are ignored. When a {@link ConsulJwtSupplier} is given, the client logs in through
 * the Consul JWT auth method and logs in again whenever Consul rejects the token.
 */
public class ConsulClient {

    private static final Logger log = LoggerFactory.getLogger(ConsulClient.class);
    private static final ParameterizedTypeReference<List<KvEntry>> ENTRIES = new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final String authMethod;
    private final ConsulJwtSupplier jwtSupplier;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private volatile String token;

    /**
     * Creates a client for the given Consul.
     *
     * @param url         Consul HTTP address, e.g. {@code http://consul:8500}
     * @param authMethod  name of the Consul JWT auth method used to log in
     * @param jwtSupplier source of the JWT exchanged for a Consul token, or {@code null} for a Consul without ACL
     * @param readTimeout HTTP read timeout; must be longer than the blocking query wait
     */
    public ConsulClient(String url, String authMethod, ConsulJwtSupplier jwtSupplier, Duration readTimeout) {
        var requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().baseUrl(url).requestFactory(requestFactory).build();
        this.authMethod = authMethod;
        this.jwtSupplier = jwtSupplier;
    }

    /**
     * Reads every flag under {@code prefix}. With {@code index > 0} this is a Consul blocking query,
     * which returns only after a change under the prefix or after {@code wait}.
     *
     * @param prefix KV prefix ending with {@code /}
     * @param index  Consul index from the previous read, or {@code 0} to return immediately
     * @param wait   maximum time a blocking query waits for a change
     * @return the flags under the prefix and the index for the next read
     */
    public Snapshot read(String prefix, long index, Duration wait) {
        try {
            return doRead(prefix, index, wait);
        } catch (HttpClientErrorException.Forbidden ex) {
            if (jwtSupplier == null) throw ex;
            log.info("Consul rejected the token. Logging in again.");
            token = null;
            return doRead(prefix, index, wait);
        }
    }

    private Snapshot doRead(String prefix, long index, Duration wait) {
        if (jwtSupplier != null && token == null) token = login();

        var response = restClient.get()
                .uri(builder -> builder.path("/v1/kv/" + prefix)
                        .queryParam("recurse", true)
                        .queryParam("index", index)
                        .queryParam("wait", wait.toSeconds() + "s")
                        .build())
                .headers(headers -> {
                    if (token != null) headers.set("X-Consul-Token", token);
                })
                .retrieve()
                .onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (request, res) -> {})
                .toEntity(ENTRIES);

        var indexHeader = response.getHeaders().getFirst("X-Consul-Index");
        var newIndex = indexHeader == null ? 0 : Long.parseLong(indexHeader);
        var entries = response.getStatusCode().is2xxSuccessful() && response.getBody() != null
                ? response.getBody()
                : List.<KvEntry>of();
        return new Snapshot(newIndex, toFlags(prefix, entries));
    }

    private String login() {
        var response = restClient.post()
                .uri("/v1/acl/login")
                .body(Map.of("AuthMethod", authMethod, "BearerToken", jwtSupplier.get()))
                .retrieve()
                .body(Map.class);
        if (response == null || response.get("SecretID") == null) {
            throw new IllegalStateException("Consul login returned no token.");
        }
        log.debug("Logged in to Consul with auth method '{}'.", authMethod);
        return (String) response.get("SecretID");
    }

    private Map<String, Boolean> toFlags(String prefix, List<KvEntry> entries) {
        var flags = new HashMap<String, Boolean>();
        for (var entry : entries) {
            var flagName = entry.key().substring(prefix.length());
            if (flagName.isEmpty() || flagName.contains("/") || entry.value() == null) continue;
            var enabled = parseEnabled(entry);
            if (enabled != null) flags.put(flagName, enabled);
        }
        return flags;
    }

    private Boolean parseEnabled(KvEntry entry) {
        try {
            var json = new String(Base64.getDecoder().decode(entry.value()), StandardCharsets.UTF_8);
            var enabled = jsonMapper.readTree(json).get("enabled");
            if (enabled != null && enabled.isBoolean()) return enabled.booleanValue();
        } catch (IllegalArgumentException | JacksonException ignored) {
        }
        log.warn("Ignoring Consul key '{}': value is not {{\"enabled\": true|false}}.", entry.key());
        return null;
    }

    /**
     * Flags found under a prefix.
     *
     * @param index Consul index to use in the next blocking query
     * @param flags flag name to enabled state
     */
    public record Snapshot(long index, Map<String, Boolean> flags) {}

    record KvEntry(@JsonProperty("Key") String key, @JsonProperty("Value") String value) {}
}
