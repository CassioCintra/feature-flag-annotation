package cassio.annotations.bootstrap;

import cassio.annotations.FeatureFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class FeatureFlagScanner {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagScanner.class);

    private final ApplicationContext ctx;

    public FeatureFlagScanner(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    public List<FeatureFlag> scan() {
        List<FeatureFlag> found = new ArrayList<>();
        for (String beanName : ctx.getBeanDefinitionNames()) {
            Class<?> targetClass = resolveClass(beanName);
            if (targetClass == null) continue;
            collect(targetClass, found);
        }
        log.debug("Scanner found {} @FeatureFlag annotation(s) across {} bean(s).",
                found.size(), ctx.getBeanDefinitionNames().length);
        return found;
    }

    private Class<?> resolveClass(String beanName) {
        try {
            return AopUtils.getTargetClass(ctx.getBean(beanName));
        } catch (Exception e) {
            log.trace("Skipping bean '{}' during @FeatureFlag scan: {}", beanName, e.getMessage());
            return null;
        }
    }

    private void collect(Class<?> clazz, List<FeatureFlag> found) {
        FeatureFlag classAnnotation = clazz.getAnnotation(FeatureFlag.class);
        if (classAnnotation != null) found.add(classAnnotation);

        for (Method method : clazz.getDeclaredMethods()) {
            FeatureFlag annotation = method.getAnnotation(FeatureFlag.class);
            if (annotation != null) found.add(annotation);
        }

        for (Field field : clazz.getDeclaredFields()) {
            FeatureFlag annotation = field.getAnnotation(FeatureFlag.class);
            if (annotation != null) found.add(annotation);
        }
    }
}
