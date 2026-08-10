package com.wedding.planner.exception;

/** Thrown when an uploaded file's content type isn't on the attachment whitelist. Mapped to 415. */
public class UnsupportedMediaTypeException extends RuntimeException {

    public UnsupportedMediaTypeException(String message) {
        super(message);
    }
}
