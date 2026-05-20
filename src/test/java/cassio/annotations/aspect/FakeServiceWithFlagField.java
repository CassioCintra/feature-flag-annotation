package cassio.annotations.aspect;

import cassio.annotations.FeatureFlag;
import org.springframework.stereotype.Service;

@Service
public class FakeServiceWithFlagField {

    public static final String FLAG_FIELD = "flag-field";
    public static final String FLAG_FIELD_DEFAULT = "flag-field-default";
    public static final String RESPONSE_NEW_FLOW = "new-flow";
    public static final String RESPONSE_OLD_FLOW = "old-flow";

    @FeatureFlag(FLAG_FIELD)
    private Boolean flagField;

    @FeatureFlag(value = FLAG_FIELD_DEFAULT, enabledByDefault = true)
    private Boolean flagFieldWithDefault;

    public String executeWithFlagField() {
        return Boolean.TRUE.equals(flagField) ? RESPONSE_NEW_FLOW : RESPONSE_OLD_FLOW;
    }

    public String executeWithFlagFieldDefault() {
        return Boolean.TRUE.equals(flagFieldWithDefault) ? RESPONSE_NEW_FLOW : RESPONSE_OLD_FLOW;
    }
}
