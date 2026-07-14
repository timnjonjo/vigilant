package com.turing.vigilant.casequeue;

import java.util.Locale;

public enum CaseSort {
    SCORE,
    AGE;

    static CaseSort parse(String value) {
        try {
            return value == null ? SCORE : valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("sortBy must be score or age");
        }
    }
}
