package com.wedding.planner.web;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds the RFC-7807 {@link ProblemDetail} shape shared by {@link GlobalExceptionHandler} (for
 * exceptions raised inside Spring MVC) and any servlet {@code Filter} that must reject a request
 * before MVC — and therefore {@code @RestControllerAdvice} — ever runs.
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
