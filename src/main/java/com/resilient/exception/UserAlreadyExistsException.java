// UserAlreadyExistsException.java
//
// Exception for user already exists business rule violation.
// - Adds serialVersionUID for serialization compatibility.
// - Provides constructor overloads for message, cause, and both.
// - Extends BusinessException for better classification.

package com.resilient.exception;

public class UserAlreadyExistsException extends BusinessException {
    private static final long serialVersionUID = 1L;

    public UserAlreadyExistsException(String message) {
        super(message);
    }

    public UserAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }

    public UserAlreadyExistsException(Throwable cause) {
        super("User already exists", cause);
    }
}
