package com.turing.vigilant.web.pagination;

public final class PageLimits {

    public static final int DEFAULT = 25;
    public static final int MAX = 100;

    private PageLimits() {
    }

    public static int requireValid(int limit) {
        if (limit < 1 || limit > MAX) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX);
        }
        return limit;
    }
}
