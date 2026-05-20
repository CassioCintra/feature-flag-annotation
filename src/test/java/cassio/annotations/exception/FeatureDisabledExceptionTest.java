package cassio.annotations.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureDisabledExceptionTest {

    @Test
    void shouldContainFlagNameInDefaultMessage() {
        FeatureDisabledException ex = new FeatureDisabledException("payment-pix");

        assertThat(ex.getFlagName()).isEqualTo("payment-pix");
        assertThat(ex.getMessage()).contains("payment-pix");
    }

    @Test
    void shouldUseCustomMessage() {
        FeatureDisabledException ex = new FeatureDisabledException("payment-pix", "Pix unavailable.");

        assertThat(ex.getMessage()).isEqualTo("Pix unavailable.");
        assertThat(ex.getFlagName()).isEqualTo("payment-pix");
    }

    @Test
    void emptyMessage_shouldFallbackToDefaultMessage() {
        FeatureDisabledException ex = new FeatureDisabledException("payment-pix", "");

        assertThat(ex.getMessage()).contains("payment-pix");
    }

    @Test
    void nullMessage_shouldFallbackToDefaultMessage() {
        FeatureDisabledException ex = new FeatureDisabledException("payment-pix", null);

        assertThat(ex.getMessage()).contains("payment-pix");
    }
}