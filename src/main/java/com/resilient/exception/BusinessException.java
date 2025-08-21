// BusinessException.java
//
// Base class for business rule violations in the application.
// - Extend for domain-specific exceptions (e.g., UserAlreadyExistsException,
// UserNotFoundException).
// - Supports message and cause for error context.

package com.resilient.exception;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    public BusinessException(Throwable cause) {
        super(cause);
    }
}
