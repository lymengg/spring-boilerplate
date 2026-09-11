package com.example.demo.config;

import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Set;

/**
 * Restricts the page size of {@link Pageable} request parameters to the allowed
 * set {10, 25, 50, 100}. Any other size is rejected with a 400 via
 * {@link GlobalExceptionHandler}.
 */
@Component
public class PageSizeLimitedPageableResolver extends PageableHandlerMethodArgumentResolver {

    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 25, 50, 100);

    public PageSizeLimitedPageableResolver() {
        // Default page size when no size param is sent; must be an allowed value.
        setFallbackPageable(PageRequest.of(0, 10));
    }

    @Override
    @NonNull
    public Pageable resolveArgument(@NonNull MethodParameter methodParameter, ModelAndViewContainer mavContainer,
            @NonNull NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Pageable pageable = super.resolveArgument(methodParameter, mavContainer, webRequest, binderFactory);
        if (!ALLOWED_SIZES.contains(pageable.getPageSize())) {
            throw new IllegalArgumentException("Page size must be one of: 10, 25, 50, 100");
        }
        return pageable;
    }
}
