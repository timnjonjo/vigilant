package com.turing.vigilant.casequeue;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised when a case id is not found within the tenant. Annotated so the web
 * layer need not import it — keeping module dependencies acyclic.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class CaseNotFoundException extends RuntimeException {

    public CaseNotFoundException(long caseId) {
        super("case not found: " + caseId);
    }
}
