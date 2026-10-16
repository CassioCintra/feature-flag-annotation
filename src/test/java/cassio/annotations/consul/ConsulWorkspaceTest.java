package cassio.annotations.consul;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsulWorkspaceTest {

    @Test
    void readsTheWorkspaceClaim() {
        var workspace = UUID.randomUUID();

        assertThat(ConsulWorkspace.fromJwt(jwt("{\"sub\":\"app\",\"workspace_id\":\"" + workspace + "\"}")))
                .isEqualTo(workspace);
    }

    @Test
    void refusesATokenWithoutWorkspace() {
        assertThatThrownBy(() -> ConsulWorkspace.fromJwt(jwt("{\"sub\":\"app\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("workspace_id");
    }

    @Test
    void refusesAWorkspaceThatIsNotAUuid() {
        assertThatThrownBy(() -> ConsulWorkspace.fromJwt(jwt("{\"workspace_id\":\"acme\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("acme");
    }

    @Test
    void refusesSomethingThatIsNotAJwt() {
        assertThatThrownBy(() -> ConsulWorkspace.fromJwt("opaque-token"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String jwt(String payload) {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".signature";
    }
}
