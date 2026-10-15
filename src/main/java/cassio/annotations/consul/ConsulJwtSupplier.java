package cassio.annotations.consul;

/**
 * Supplies the JWT exchanged for a Consul token at {@code /v1/acl/login}.
 */
@FunctionalInterface
public interface ConsulJwtSupplier {

    /**
     * Obtains the JWT for a new Consul login.
     *
     * @return a valid JWT accepted by the Consul auth method
     */
    String get();
}
