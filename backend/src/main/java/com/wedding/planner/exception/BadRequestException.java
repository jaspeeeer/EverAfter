package com.wedding.planner.exception;

/** Thrown for semantically invalid requests (e.g. a disallowed role). Mapped to HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
