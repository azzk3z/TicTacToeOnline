
package com.tictactoe.backend.exception;

public class DuplicateResourceException extends RuntimeException {

    private final String field;

    public DuplicateResourceException(String field, String message) {
        super(message);
        this.field = field;
    }

    public DuplicateResourceException(String message) {
        this(null, message);
    }

    public String getField() {
        return field;
    }
}
