// UserNotFoundException.java
//
// Exception for user not found business rule violation.
// - Adds serialVersionUID for serialization compatibility.
// - Provides constructor overloads for message, cause, and both.
// - Extends BusinessException for better classification.

package com.resilient.exception;

public class UserNotFoundException extends BusinessException {
    private static final long serialVersionUID = 1L;

    public UserNotFoundException(String message) {
        super(message);
    }

    public UserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public UserNotFoundException(Throwable cause) {
        super("User not found", cause);
    }
}
