package org.example.test.context;


import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExecutableInvoker;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.commons.annotation.Testable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.ParameterResolutionDelegate;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.RepeatableContainers;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.lang.Nullable;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestContextAnnotationUtils;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.support.PropertyProvider;
import org.springframework.test.context.support.TestConstructorUtils;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

public class SpringExtension implements BeforeAllCallback, AfterAllCallback, TestInstancePostProcessor, BeforeEachCallback, AfterEachCallback, BeforeTestExecutionCallback, AfterTestExecutionCallback, ParameterResolver {
    private static final ExtensionContext.Namespace TEST_CONTEXT_MANAGER_NAMESPACE = Namespace.create(new Object[]{SpringExtension.class});
    private static final ExtensionContext.Namespace AUTOWIRED_VALIDATION_NAMESPACE = Namespace.create(new Object[]{SpringExtension.class.getName() + "#autowired.validation"});
    private static final String NO_VIOLATIONS_DETECTED = "";
    private static final ExtensionContext.Namespace RECORD_APPLICATION_EVENTS_VALIDATION_NAMESPACE = Namespace.create(new Object[]{SpringExtension.class.getName() + "#recordApplicationEvents.validation"});
    private static final List<Class<? extends Annotation>> JUPITER_ANNOTATION_TYPES = List.of(BeforeAll.class, AfterAll.class, BeforeEach.class, AfterEach.class, Testable.class);
    private static final ReflectionUtils.MethodFilter autowiredTestOrLifecycleMethodFilter;

    public SpringExtension() {
    }

    public void beforeAll(ExtensionContext context) throws Exception {
        TestContextManager testContextManager = getTestContextManager(context);
        registerMethodInvoker(testContextManager, context);
        testContextManager.beforeTestClass();
    }

    public void afterAll(ExtensionContext context) throws Exception {
        try {
            TestContextManager testContextManager = getTestContextManager(context);
            registerMethodInvoker(testContextManager, context);
            testContextManager.afterTestClass();
        } finally {
            getStore(context).remove(context.getRequiredTestClass());
        }

    }

    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
        this.validateAutowiredConfig(context);
        this.validateRecordApplicationEventsConfig(context);
        TestContextManager testContextManager = getTestContextManager(context);
        registerMethodInvoker(testContextManager, context);
        testContextManager.prepareTestInstance(testInstance);
    }

    private void validateAutowiredConfig(ExtensionContext context) {
        ExtensionContext.Store store = context.getStore(AUTOWIRED_VALIDATION_NAMESPACE);
        String errorMessage = (String)store.getOrComputeIfAbsent(context.getRequiredTestClass(), (testClass) -> {
            Method[] methodsWithErrors = ReflectionUtils.getUniqueDeclaredMethods(testClass, autowiredTestOrLifecycleMethodFilter);
            return methodsWithErrors.length == 0 ? "" : String.format("Test methods and test lifecycle methods must not be annotated with @Autowired. You should instead annotate individual method parameters with @Autowired, @Qualifier, or @Value. Offending methods in test class %s: %s", testClass.getName(), Arrays.toString(methodsWithErrors));
        }, String.class);
        if (!errorMessage.isEmpty()) {
            throw new IllegalStateException(errorMessage);
        }
    }

    private void validateRecordApplicationEventsConfig(ExtensionContext context) {
        ExtensionContext.Store store = context.getStore(RECORD_APPLICATION_EVENTS_VALIDATION_NAMESPACE);
        String errorMessage = (String)store.getOrComputeIfAbsent(context.getRequiredTestClass(), (testClass) -> {
            boolean recording = TestContextAnnotationUtils.hasAnnotation(testClass, RecordApplicationEvents.class);
            if (!recording) {
                return "";
            } else if (context.getTestInstanceLifecycle().orElse(Lifecycle.PER_METHOD) == Lifecycle.PER_METHOD) {
                return "";
            } else {
                return context.getExecutionMode() == ExecutionMode.SAME_THREAD ? "" : "Test classes or @Nested test classes that @RecordApplicationEvents must not be run in parallel with the @TestInstance(PER_CLASS) lifecycle mode. Configure either @Execution(SAME_THREAD) or @TestInstance(PER_METHOD) semantics, or disable parallel execution altogether. Note that when recording events in parallel, one might see events published by other tests since the application context may be shared.";
            }
        }, String.class);
        if (!errorMessage.isEmpty()) {
            throw new IllegalStateException(errorMessage);
        }
    }

    public void beforeEach(ExtensionContext context) throws Exception {
        Object testInstance = context.getRequiredTestInstance();
        Method testMethod = context.getRequiredTestMethod();
        TestContextManager testContextManager = getTestContextManager(context);
        registerMethodInvoker(testContextManager, context);
        testContextManager.beforeTestMethod(testInstance, testMethod);
    }

    public void beforeTestExecution(ExtensionContext context) throws Exception {
        Object testInstance = context.getRequiredTestInstance();
        Method testMethod = context.getRequiredTestMethod();
        TestContextManager testContextManager = getTestContextManager(context);
        registerMethodInvoker(testContextManager, context);
        testContextManager.beforeTestExecution(testInstance, testMethod);
    }

    public void afterTestExecution(ExtensionContext context) throws Exception {
        Object testInstance = context.getRequiredTestInstance();
        Method testMethod = context.getRequiredTestMethod();
        Throwable testException = (Throwable)context.getExecutionException().orElse((Object)null);
        TestContextManager testContextManager = getTestContextManager(context);
        registerMethodInvoker(testContextManager, context);
        testContextManager.afterTestExecution(testInstance, testMethod, testException);
    }

    public void afterEach(ExtensionContext context) throws Exception {
        Object testInstance = context.getRequiredTestInstance();
        Method testMethod = context.getRequiredTestMethod();
        Throwable testException = (Throwable)context.getExecutionException().orElse((Object)null);
        TestContextManager testContextManager = getTestContextManager(context);
        registerMethodInvoker(testContextManager, context);
        testContextManager.afterTestMethod(testInstance, testMethod, testException);
    }

    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Parameter parameter = parameterContext.getParameter();
        Executable executable = parameter.getDeclaringExecutable();
        Class<?> testClass = extensionContext.getRequiredTestClass();
        PropertyProvider junitPropertyProvider = (propertyName) -> {
            return (String)extensionContext.getConfigurationParameter(propertyName).orElse((Object)null);
        };
        return TestConstructorUtils.isAutowirableConstructor(executable, testClass, junitPropertyProvider) || ApplicationContext.class.isAssignableFrom(parameter.getType()) || this.supportsApplicationEvents(parameterContext) || ParameterResolutionDelegate.isAutowirable(parameter, parameterContext.getIndex());
    }

    private boolean supportsApplicationEvents(ParameterContext parameterContext) {
        if (ApplicationEvents.class.isAssignableFrom(parameterContext.getParameter().getType())) {
            Assert.isTrue(parameterContext.getDeclaringExecutable() instanceof Method, "ApplicationEvents can only be injected into test and lifecycle methods");
            return true;
        } else {
            return false;
        }
    }

    @Nullable
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Parameter parameter = parameterContext.getParameter();
        int index = parameterContext.getIndex();
        Class<?> testClass = extensionContext.getRequiredTestClass();
        ApplicationContext applicationContext = getApplicationContext(extensionContext);
        return ParameterResolutionDelegate.resolveDependency(parameter, index, testClass, applicationContext.getAutowireCapableBeanFactory());
    }

    public static ApplicationContext getApplicationContext(ExtensionContext context) {
        return getTestContextManager(context).getTestContext().getApplicationContext();
    }

    static TestContextManager getTestContextManager(ExtensionContext context) {
        Assert.notNull(context, "ExtensionContext must not be null");
        Class<?> testClass = context.getRequiredTestClass();
        ExtensionContext.Store store = getStore(context);
        return (TestContextManager)store.getOrComputeIfAbsent(testClass, TestContextManager::new, TestContextManager.class);
    }

    private static ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getRoot().getStore(TEST_CONTEXT_MANAGER_NAMESPACE);
    }

    private static void registerMethodInvoker(TestContextManager testContextManager, ExtensionContext context) {
        TestContext var10000 = testContextManager.getTestContext();
        ExecutableInvoker var10001 = context.getExecutableInvoker();
        Objects.requireNonNull(var10001);
        var10000.setMethodInvoker(var10001::invoke);
    }

    private static boolean isAutowiredTestOrLifecycleMethod(Method method) {
        MergedAnnotations mergedAnnotations = MergedAnnotations.from(method, SearchStrategy.DIRECT, RepeatableContainers.none());
        if (!mergedAnnotations.isPresent(Autowired.class)) {
            return false;
        } else {
            Iterator var2 = JUPITER_ANNOTATION_TYPES.iterator();

            Class annotationType;
            do {
                if (!var2.hasNext()) {
                    return false;
                }

                annotationType = (Class)var2.next();
            } while(!mergedAnnotations.isPresent(annotationType));

            return true;
        }
    }

    static {
        autowiredTestOrLifecycleMethodFilter = ReflectionUtils.USER_DECLARED_METHODS.and(SpringExtension::isAutowiredTestOrLifecycleMethod);
    }
}

