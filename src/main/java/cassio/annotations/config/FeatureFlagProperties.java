package cassio.annotations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Library configuration properties read from application.properties.
 *
 * <p>Example configuration in the consumer service:
 * <pre>
 * feature-flag.service-name=checkout-service
 * feature-flag.environment=${spring.profiles.active:dev}
 * feature-flag.consul.url=http://consul:8500
 *
 * # Used while a flag is not found in Consul. An empty or invalid value means disabled.
 * feature-flag.defaults.new-checkout=false
 * feature-flag.defaults.pix-payment=false
 * </pre>
 */
@ConfigurationProperties(prefix = "feature-flag")
public class FeatureFlagProperties {

    private String serviceName;
    private String environment = "dev";
    private boolean strict = false;
    private Map<String, String> defaults = new HashMap<>();
    private Consul consul = new Consul();

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public boolean isStrict() { return strict; }
    public void setStrict(boolean strict) { this.strict = strict; }

    /**
     * Defaults from {@code feature-flag.defaults}, kept as text so that empty values are not lost.
     *
     * @return flag name to the configured value
     */
    public Map<String, String> getDefaults() { return defaults; }

    /**
     * Sets the defaults. Any value other than {@code true} or {@code false} disables the flag.
     *
     * @param defaults flag name to the configured value
     */
    public void setDefaults(Map<String, String> defaults) { this.defaults = defaults; }

    /**
     * Consul connection settings, under {@code feature-flag.consul}.
     *
     * @return the settings
     */
    public Consul getConsul() { return consul; }

    /**
     * Sets the Consul connection settings.
     *
     * @param consul the settings
     */
    public void setConsul(Consul consul) { this.consul = consul; }

    /**
     * Default used while a flag is not in the cache. An entry in {@code feature-flag.defaults}
     * wins over the annotation, and an entry that is not {@code true} disables the flag.
     *
     * @param flagName          flag name
     * @param annotationDefault {@code enabledByDefault} from the annotation
     * @return the default state of the flag
     */
    public boolean getDefault(String flagName, boolean annotationDefault) {
        if (!defaults.containsKey(flagName)) return annotationDefault;
        return "true".equalsIgnoreCase(defaults.get(flagName));
    }

    /**
     * Tells whether a default can be used as is, without disabling the flag.
     *
     * @param value a value from {@code feature-flag.defaults}
     * @return whether the value is {@code true} or {@code false}, ignoring case
     */
    public static boolean isValidDefault(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }

    /** Consul connection settings, under {@code feature-flag.consul}. */
    public static class Consul {

        /** Creates the settings with the default auth method and client registration. */
        public Consul() {}

        private String url;
        private String authMethod = "ms-auth";
        private String clientRegistrationId = "feature-flag";

        /**
         * Consul HTTP address. When unset, the Consul sync is disabled.
         *
         * @return the address
         */
        public String getUrl() { return url; }

        /**
         * Sets the Consul HTTP address.
         *
         * @param url the address, e.g. {@code http://consul:8500}
         */
        public void setUrl(String url) { this.url = url; }

        /**
         * Name of the Consul JWT auth method used to log in. Default {@code ms-auth}.
         *
         * @return the auth method name
         */
        public String getAuthMethod() { return authMethod; }

        /**
         * Sets the name of the Consul JWT auth method.
         *
         * @param authMethod the auth method name
         */
        public void setAuthMethod(String authMethod) { this.authMethod = authMethod; }

        /**
         * OAuth2 client registration used to obtain the JWT. Default {@code feature-flag}.
         *
         * @return the registration id
         */
        public String getClientRegistrationId() { return clientRegistrationId; }

        /**
         * Sets the OAuth2 client registration used to obtain the JWT.
         *
         * @param clientRegistrationId the registration id
         */
        public void setClientRegistrationId(String clientRegistrationId) { this.clientRegistrationId = clientRegistrationId; }
    }
}