package com.giri.oms.common.openapi;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorCoded;
import com.giri.oms.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against the exact drift {@link ApiErrorCodes}'s javadoc warns about:
 * {@link GlobalExceptionHandler} decides which {@link ErrorCoded} exceptions the API
 * can throw, and {@code @ApiErrorCodes} decides which {@link ErrorCode}s each endpoint
 * documents — but nothing ties those two lists together. Add a new domain exception,
 * wire it into the handler, and forget the {@code @ApiErrorCodes} entry, and the
 * Swagger docs quietly go stale with no signal anywhere.
 * <p>
 * This test closes that gap by reflection: it collects every exception type named in
 * an {@code @ExceptionHandler} on {@link GlobalExceptionHandler}, keeps only the ones
 * that implement {@link ErrorCoded} (fixed-code cases like {@code BadCredentialsException}
 * are out of scope — those never had a per-exception code to drift), instantiates each
 * with dummy constructor arguments to read its {@link ErrorCode}, and checks that code
 * is referenced by at least one {@code @ApiErrorCodes} somewhere in the app.
 * <p>
 * Deliberately NOT flagged as missing: {@code ErrorCode.INSUFFICIENT_STOCK} — its
 * exception ({@code InsufficientStockException}) is never given an
 * {@code @ExceptionHandler} in the first place, because it never crosses the REST
 * boundary (see {@link ErrorCode} class Javadoc), so it's correctly excluded at the
 * first step rather than needing a special case here.
 */
class ErrorCodeApiDocumentationConsistencyTest {

    private static final String BASE_PACKAGE = "com.giri.oms";

    @Test
    void everyHandledErrorCodedExceptionHasAMatchingApiErrorCodesEntry() {
        Set<Class<?>> handledExceptionTypes = handledExceptionTypes();
        Set<ErrorCode> declaredCodes = declaredApiErrorCodes();

        List<String> undocumented = new ArrayList<>();
        for (Class<?> exceptionType : handledExceptionTypes) {
            if (!ErrorCoded.class.isAssignableFrom(exceptionType)) {
                // Handled, but carries no per-exception code (e.g. BadCredentialsException,
                // MethodArgumentNotValidException) — the handler assigns a fixed ErrorCode
                // inline instead, so there's nothing to reconcile against @ApiErrorCodes here.
                continue;
            }

            ErrorCode code = errorCodeOf(exceptionType);
            if (!declaredCodes.contains(code)) {
                undocumented.add(exceptionType.getSimpleName() + " -> " + code);
            }
        }

        assertThat(undocumented)
                .as("Every ErrorCoded exception handled by GlobalExceptionHandler must have its ErrorCode "
                        + "referenced by at least one @ApiErrorCodes annotation somewhere, or the Swagger docs "
                        + "silently fall out of sync with what the API can actually return. Missing "
                        + "(exception -> error code)")
                .isEmpty();
    }

    /** Every exception class named in an {@code @ExceptionHandler} on GlobalExceptionHandler. */
    private Set<Class<?>> handledExceptionTypes() {
        Set<Class<?>> types = new LinkedHashSet<>();
        for (Method method : GlobalExceptionHandler.class.getDeclaredMethods()) {
            ExceptionHandler annotation = method.getAnnotation(ExceptionHandler.class);
            if (annotation != null) {
                types.addAll(List.of(annotation.value()));
            }
        }
        return types;
    }

    /** The union of every ErrorCode referenced by an {@code @ApiErrorCodes} anywhere in the app. */
    private Set<ErrorCode> declaredApiErrorCodes() {
        Set<ErrorCode> codes = new LinkedHashSet<>();
        for (Class<?> controller : findControllerClasses()) {
            for (Method method : controller.getDeclaredMethods()) {
                ApiErrorCodes annotation = method.getAnnotation(ApiErrorCodes.class);
                if (annotation != null) {
                    codes.addAll(List.of(annotation.value()));
                }
            }
        }
        return codes;
    }

    /** Every {@code @RestController} under the app's base package, found via classpath scan. */
    private List<Class<?>> findControllerClasses() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition beanDefinition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            try {
                classes.add(Class.forName(beanDefinition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Failed to load scanned controller class", e);
            }
        }
        return classes;
    }

    /**
     * Instantiates the exception with dummy constructor arguments (shortest constructor
     * first) and reads back its {@link ErrorCode} — every {@link ErrorCoded} exception in
     * this codebase returns a fixed code regardless of the arguments it was built with,
     * so the actual values don't matter, only that construction succeeds.
     */
    private ErrorCode errorCodeOf(Class<?> exceptionType) {
        Constructor<?>[] constructors = exceptionType.getDeclaredConstructors();
        Arrays.sort(constructors, Comparator.comparingInt(Constructor::getParameterCount));

        for (Constructor<?> constructor : constructors) {
            try {
                constructor.setAccessible(true);
                Object[] args = Arrays.stream(constructor.getParameterTypes())
                        .map(this::dummyValueFor)
                        .toArray();
                ErrorCoded instance = (ErrorCoded) constructor.newInstance(args);
                return instance.getErrorCode();
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                // Try the next constructor shape.
            }
        }

        throw new IllegalStateException("Could not instantiate " + exceptionType
                + " with dummy arguments via any declared constructor — if it gained a new "
                + "constructor parameter type, teach dummyValueFor() about it.");
    }

    private Object dummyValueFor(Class<?> type) {
        if (type == String.class) {
            return "test";
        }
        if (type == Long.class || type == long.class) {
            return 1L;
        }
        if (type == Integer.class || type == int.class) {
            return 1;
        }
        if (type == Set.class) {
            return Set.of("test");
        }
        if (Throwable.class.isAssignableFrom(type)) {
            return new RuntimeException("test");
        }
        throw new IllegalArgumentException("No dummy value configured for constructor parameter type: " + type);
    }
}
