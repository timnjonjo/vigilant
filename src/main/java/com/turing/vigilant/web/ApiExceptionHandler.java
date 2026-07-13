package com.turing.vigilant.web;

import com.turing.vigilant.tenant.UnknownTenantException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain exceptions to HTTP responses for every controller. Campaign
 * exceptions self-annotate with {@code @ResponseStatus} (like
 * {@code CaseNotFoundException}) so this module needn't import them.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UnknownTenantException.class)
    ProblemDetail onUnknownTenant(UnknownTenantException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(TenantAccessDeniedException.class)
    ProblemDetail onTenantAccessDenied(TenantAccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail onIllegalArgument(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
