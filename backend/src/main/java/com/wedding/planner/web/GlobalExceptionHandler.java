package com.wedding.planner.web;

import com.wedding.planner.exception.BadRequestException;
import com.wedding.planner.exception.ConflictException;
import com.wedding.planner.exception.EmailAlreadyExistsException;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.exception.UnsupportedMediaTypeException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Translates domain and security exceptions into RFC-7807 {@link ProblemDetail} responses with
 * appropriate status codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    ProblemDetail handleConflict(EmailAlreadyExistsException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        return ProblemDetails.of(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail handleBadRequest(BadRequestException ex) {
        return ProblemDetails.of(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(UnsupportedMediaTypeException.class)
    ProblemDetail handleUnsupportedMediaType(UnsupportedMediaTypeException ex) {
        return ProblemDetails.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return ProblemDetails.of(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @ExceptionHandler(org.springframework.security.authentication.DisabledException.class)
    ProblemDetail handleDisabled(org.springframework.security.authentication.DisabledException ex) {
        return ProblemDetails.of(HttpStatus.UNAUTHORIZED, "This account has been disabled");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return ProblemDetails.of(HttpStatus.FORBIDDEN, "You do not have access to this resource");
    }

    /**
     * A path segment that fails to convert to the declared type (e.g. a non-UUID RSVP token).
     * Mapped to the same 404 a well-formed-but-unknown token gets — otherwise the two cases are
     * distinguishable (a different error shape), which turns the public RSVP/invitation surface
     * into a token-format oracle.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ProblemDetails.of(HttpStatus.NOT_FOUND, "Not found");
    }

    @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
    ProblemDetail handleMethodValidation(
            org.springframework.web.method.annotation.HandlerMethodValidationException ex) {
        return ProblemDetails.of(HttpStatus.BAD_REQUEST, "Validation failed for one or more entries");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (a, b) -> a));
        ProblemDetail problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", fieldErrors);
        return problem;
    }
}
