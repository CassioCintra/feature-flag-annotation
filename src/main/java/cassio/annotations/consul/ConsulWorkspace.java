package cassio.annotations.consul;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Reads the workspace of the application from the {@code workspace_id} claim of its JWT.
 *
 * <p>The signature is not checked here: the token is the one the application obtained for itself
 * and Consul validates it at login. A token issued for another workspace is refused by the Consul ACL.
 */
public final class ConsulWorkspace {

    /**
     * Name of the claim that carries the workspace.
     */
    public static final String CLAIM = "workspace_id";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ConsulWorkspace() {}

    /**
     * Extracts the workspace from a JWT.
     *
     * @param jwt the JWT used to log in to Consul
     * @return the workspace in the {@code workspace_id} claim
     * @throws IllegalStateException if the JWT is malformed or has no valid {@code workspace_id} claim
     */
    public static UUID fromJwt(String jwt) {
        var parts = jwt == null ? new String[0] : jwt.split("\\.");
        if (parts.length != 3) {
            throw new IllegalStateException("The token used to log in to Consul is not a JWT");
        }
        var payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        var claim = JSON.readTree(payload).path(CLAIM);
        if (!claim.isString()) {
            throw new IllegalStateException("The token used to log in to Consul has no '" + CLAIM
                    + "' claim. Register the OAuth2 client of this application with a workspace.");
        }
        try {
            return UUID.fromString(claim.asString());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("The '" + CLAIM + "' claim is not a UUID: " + claim.asString());
        }
    }
}
