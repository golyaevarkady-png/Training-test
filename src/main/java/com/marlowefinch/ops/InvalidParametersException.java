package com.marlowefinch.ops;

import java.util.List;

/** Thrown when request parameters fail validation; carries every problem found. */
public class InvalidParametersException extends RuntimeException {

    private final List<String> errors;

    public InvalidParametersException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }

    static void throwIfAny(List<String> errors) {
        if (!errors.isEmpty()) {
            throw new InvalidParametersException(errors);
        }
    }
}
