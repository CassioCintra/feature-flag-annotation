package cassio.annotations.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * Registers a RestClient with OAuth2 Client Credentials support.
 *
 * <p>This configuration is isolated in its own class so the compiler only
 * processes it when spring-security-oauth2-client is on the classpath.
 * It is activated only when:
 * <ul>
 *   <li>{@code feature-flag.flag-service-url} is configured</li>
 *   <li>{@link OAuth2AuthorizedClientManager} is present in the Spring context</li>
 * </ul>
 *
 * <p>The consumer service just needs to declare an {@link OAuth2AuthorizedClientManager}
 * bean and configure {@code spring.security.oauth2.client.*} — no extra setup required.
 */
@Configuration
@ConditionalOnClass(OAuth2AuthorizedClientManager.class)
@ConditionalOnBean(OAuth2AuthorizedClientManager.class)
@ConditionalOnProperty(prefix = "feature-flag", name = "flag-service-url")
public class FeatureFlagOAuth2Config {

    @Bean
    @ConditionalOnMissingBean(name = "featureFlagRestClient")
    public RestClient featureFlagRestClient(
            FeatureFlagProperties properties,
            OAuth2AuthorizedClientManager authorizedClientManager) {

        return RestClient.builder()
                .baseUrl(properties.getFlagServiceUrl())
                .requestInterceptor(new OAuth2ClientHttpRequestInterceptor(authorizedClientManager))
                .build();
    }
}
