package com.turing.vigilant.casequeue;

import com.turing.vigilant.shared.ReasonCode;
import com.turing.vigilant.web.pagination.CursorCodec;
import com.turing.vigilant.web.pagination.CursorPage;
import com.turing.vigilant.web.pagination.CursorState;
import com.turing.vigilant.web.pagination.InvalidCursorException;
import com.turing.vigilant.web.pagination.PageLimits;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class CasePageService {

    private static final String RESOURCE = "cases";

    private final CasePageRepository repository;
    private final CursorCodec cursorCodec;

    public CasePageService(CasePageRepository repository, CursorCodec cursorCodec) {
        this.repository = repository;
        this.cursorCodec = cursorCodec;
    }

    public CursorPage<CaseView> page(
            String tenantId, CaseStatus status, String campaignId, ReasonCode reasonCode,
            String search, String sortBy, String cursor, int requestedLimit) {
        int limit = PageLimits.requireValid(requestedLimit);
        String campaign = normalise(campaignId);
        String searchTerm = normalise(search);
        CaseSort sort = CaseSort.parse(sortBy);
        String queryKey = queryKey(tenantId, status, campaign, reasonCode, searchTerm, sort);
        CursorState state = cursorCodec.decode(cursor, RESOURCE, queryKey, sort.name());

        Double cursorScore = null;
        Instant cursorOpenedAt = null;
        Long cursorId = null;
        if (state != null) {
            if (state.epochMillis() == null || state.numericId() == null
                    || (sort == CaseSort.SCORE && state.score() == null)) {
                throw new InvalidCursorException();
            }
            cursorScore = state.score();
            cursorOpenedAt = Instant.ofEpochMilli(state.epochMillis());
            cursorId = state.numericId();
        }

        List<FraudCase> rows = repository.findPage(
                tenantId, status, campaign, reasonCode, searchTerm, sort,
                cursorScore, cursorOpenedAt, cursorId, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<FraudCase> visible = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = null;
        if (hasMore) {
            FraudCase last = visible.get(visible.size() - 1);
            nextCursor = cursorCodec.encode(
                    RESOURCE, queryKey, sort.name(), last.getOpenedAt().toEpochMilli(),
                    sort == CaseSort.SCORE ? last.getScore() : null,
                    last.getId(), null, null);
        }
        return new CursorPage<>(visible.stream().map(CaseView::of).toList(), nextCursor);
    }

    private static String queryKey(String tenantId, CaseStatus status, String campaignId,
                                   ReasonCode reasonCode, String search, CaseSort sort) {
        return String.join("\u001f",
                tenantId,
                status == null ? "" : status.name(),
                campaignId == null ? "" : campaignId,
                reasonCode == null ? "" : reasonCode.name(),
                search == null ? "" : search,
                sort.name());
    }

    private static String normalise(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
