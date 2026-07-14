package com.turing.vigilant.web.pagination;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** A malformed, tampered, expired-context, or wrong-query cursor. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidCursorException extends RuntimeException {

    public InvalidCursorException() {
        super("cursor is invalid for this resource, filter, or sort order");
    }
}
