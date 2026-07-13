package com.turing.vigilant.casequeue;

/** Lifecycle of a case in the queue. */
public enum CaseStatus {
    /** Awaiting analyst review. */
    OPEN,
    /** Resolved by an analyst; a resolution and resolver are recorded. */
    RESOLVED
}
