package com.wedding.planner.exception;

/** Thrown when registering with an email already in use. Mapped to HTTP 409. */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("Email already registered: " + email);
    }
}
