package com.turing.vigilant.web.pagination;

import java.util.List;

/** A count-free keyset page. {@code nextCursor} is null on the final page. */
public record CursorPage<T>(List<T> items, String nextCursor) {

    public CursorPage {
        items = List.copyOf(items);
    }
}
