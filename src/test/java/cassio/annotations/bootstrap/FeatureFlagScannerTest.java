package cassio.annotations.bootstrap;

import cassio.annotations.FeatureFlag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureFlagScannerTest {

    @Mock
    private ApplicationContext ctx;

    private FeatureFlagScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new FeatureFlagScanner(ctx);
    }

    @Test
    void shouldCollectAnnotationFromAnnotatedMethod() {
        when(ctx.getBeanDefinitionNames()).thenReturn(new String[]{"serviceBean"});
        when(ctx.getBean("serviceBean")).thenReturn(new ServiceWithAnnotatedMethod());

        List<FeatureFlag> result = scanner.scan();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).key()).isEqualTo("method-flag");
    }

    @Test
    void shouldCollectAnnotationFromAnnotatedClass() {
        when(ctx.getBeanDefinitionNames()).thenReturn(new String[]{"classBean"});
        when(ctx.getBean("classBean")).thenReturn(new ServiceWithAnnotatedClass());

        List<FeatureFlag> result = scanner.scan();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).key()).isEqualTo("class-flag");
    }

    @Test
    void shouldCollectAnnotationFromAnnotatedField() {
        when(ctx.getBeanDefinitionNames()).thenReturn(new String[]{"fieldBean"});
        when(ctx.getBean("fieldBean")).thenReturn(new ServiceWithAnnotatedField());

        List<FeatureFlag> result = scanner.scan();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).key()).isEqualTo("field-flag");
    }

    @Test
    void shouldCollectAllAnnotationsFromSameBean() {
        when(ctx.getBeanDefinitionNames()).thenReturn(new String[]{"mixedBean"});
        when(ctx.getBean("mixedBean")).thenReturn(new ServiceWithMultipleAnnotations());

        List<FeatureFlag> result = scanner.scan();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FeatureFlag::key)
                .containsExactlyInAnyOrder("flag-one", "flag-two");
    }

    @Test
    void shouldReturnEmptyListWhenNoBeanHasAnnotation() {
        when(ctx.getBeanDefinitionNames()).thenReturn(new String[]{"plainBean"});
        when(ctx.getBean("plainBean")).thenReturn(new PlainService());

        List<FeatureFlag> result = scanner.scan();

        assertThat(result).isEmpty();
    }

    @Test
    void shouldSkipBeanThatThrowsOnRetrieval() {
        when(ctx.getBeanDefinitionNames()).thenReturn(new String[]{"brokenBean"});
        when(ctx.getBean("brokenBean")).thenThrow(new RuntimeException("bean init failed"));

        List<FeatureFlag> result = scanner.scan();

        assertThat(result).isEmpty();
    }

    @Test
    void shouldCollectAnnotationsAcrossMultipleBeans() {
        when(ctx.getBeanDefinitionNames()).thenReturn(new String[]{"beanA", "beanB"});
        when(ctx.getBean("beanA")).thenReturn(new ServiceWithAnnotatedMethod());
        when(ctx.getBean("beanB")).thenReturn(new ServiceWithAnnotatedClass());

        List<FeatureFlag> result = scanner.scan();

        assertThat(result).hasSize(2);
    }

    // --- inner test fixtures ---

    static class ServiceWithAnnotatedMethod {
        @FeatureFlag(key = "method-flag")
        public void doSomething() {}
    }

    @FeatureFlag(key = "class-flag")
    static class ServiceWithAnnotatedClass {
        public void doSomething() {}
    }

    static class ServiceWithAnnotatedField {
        @FeatureFlag(key = "field-flag")
        private boolean flagEnabled;
    }

    static class ServiceWithMultipleAnnotations {
        @FeatureFlag(key = "flag-one")
        public void methodOne() {}

        @FeatureFlag(key = "flag-two")
        public void methodTwo() {}
    }

    static class PlainService {
        public void doSomething() {}
    }
}
