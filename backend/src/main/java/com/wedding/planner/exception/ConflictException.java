package com.wedding.planner.exception;

/** Thrown when a request conflicts with existing state (e.g. a duplicate name). Mapped to 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
