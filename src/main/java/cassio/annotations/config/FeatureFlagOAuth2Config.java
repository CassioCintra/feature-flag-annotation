package cassio.annotations.config;

import cassio.annotations.consul.ConsulJwtSupplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * Supplies the JWT used to log in to Consul, obtained with OAuth2 Client Credentials.
 *
 * <p>Active only when spring-security-oauth2-client is on the classpath, an
 * {@link OAuth2AuthorizedClientManager} bean exists and {@code feature-flag.consul.url} is set.
 * The flags are read on a background thread, outside any HTTP request, so the manager must work
 * without one, e.g. {@code AuthorizedClientServiceOAuth2AuthorizedClientManager}.
 */
@Configuration
@ConditionalOnClass(OAuth2AuthorizedClientManager.class)
@ConditionalOnBean(OAuth2AuthorizedClientManager.class)
@ConditionalOnProperty(prefix = "feature-flag.consul", name = "url")
public class FeatureFlagOAuth2Config {

    /** Imported by {@link FeatureFlagAutoConfig}. */
    public FeatureFlagOAuth2Config() {}

    /**
     * Creates the JWT supplier backed by the OAuth2 client registration.
     *
     * @param properties              library configuration, provides the client registration id
     * @param authorizedClientManager obtains and caches the access token
     * @return supplier of the access token for the {@code feature-flag.consul.client-registration-id} registration
     */
    @Bean
    @ConditionalOnMissingBean
    public ConsulJwtSupplier consulJwtSupplier(
            FeatureFlagProperties properties,
            OAuth2AuthorizedClientManager authorizedClientManager) {

        var registrationId = properties.getConsul().getClientRegistrationId();
        var request = OAuth2AuthorizeRequest
                .withClientRegistrationId(registrationId)
                .principal("feature-flag")
                .build();

        return () -> {
            var client = authorizedClientManager.authorize(request);
            if (client == null) {
                throw new IllegalStateException(
                        "Could not obtain a token for client registration '" + registrationId + "'.");
            }
            return client.getAccessToken().getTokenValue();
        };
    }
}
