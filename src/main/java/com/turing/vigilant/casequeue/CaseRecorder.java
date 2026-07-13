package com.turing.vigilant.casequeue;

/**
 * Port the decisioning flow uses to log a HOLD/REJECT case. Implemented over
 * Postgres in this module; kept as an interface so decisions stay decoupled from
 * JPA and can be unit-tested with a stub.
 */
public interface CaseRecorder {

    /** Persists a new case and returns its generated id. */
    long record(CaseOpening opening);
}
