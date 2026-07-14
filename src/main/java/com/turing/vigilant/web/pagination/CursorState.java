package com.turing.vigilant.web.pagination;

/** Signed internal keyset state; callers see only the encoded cursor token. */
public record CursorState(
        int version,
        String resource,
        String queryHash,
        String sort,
        Long epochMillis,
        Double score,
        Long numericId,
        String stringId,
        Integer eventOrder) {
}
