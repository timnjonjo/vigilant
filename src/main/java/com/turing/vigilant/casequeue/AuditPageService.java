package com.turing.vigilant.casequeue;

import com.turing.vigilant.casequeue.AuditPageRepository.AuditEventRow;
import com.turing.vigilant.web.pagination.CursorCodec;
import com.turing.vigilant.web.pagination.CursorPage;
import com.turing.vigilant.web.pagination.CursorState;
import com.turing.vigilant.web.pagination.InvalidCursorException;
import com.turing.vigilant.web.pagination.PageLimits;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AuditPageService {

    private static final String RESOURCE = "case-audit";
    private static final String SORT = "EVENT_AT";

    private final AuditPageRepository repository;
    private final CursorCodec cursorCodec;

    public AuditPageService(AuditPageRepository repository, CursorCodec cursorCodec) {
        this.repository = repository;
        this.cursorCodec = cursorCodec;
    }

    public CursorPage<AuditEntryView> page(
            String tenantId, long requestedCaseId, String referralCode,
            String cursor, int requestedLimit) {
        int limit = PageLimits.requireValid(requestedLimit);
        String queryKey = tenantId + "\u001f" + requestedCaseId + "\u001f" + referralCode;
        CursorState state = cursorCodec.decode(cursor, RESOURCE, queryKey, SORT);
        Instant cursorAt = null;
        Long cursorCaseId = null;
        Integer cursorEventOrder = null;
        if (state != null) {
            if (state.epochMillis() == null || state.numericId() == null || state.eventOrder() == null) {
                throw new InvalidCursorException();
            }
            cursorAt = Instant.ofEpochMilli(state.epochMillis());
            cursorCaseId = state.numericId();
            cursorEventOrder = state.eventOrder();
        }

        List<AuditEventRow> rows = repository.findPage(
                tenantId, referralCode, cursorAt, cursorCaseId, cursorEventOrder, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<AuditEventRow> visible = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = null;
        if (hasMore) {
            AuditEventRow last = visible.get(visible.size() - 1);
            nextCursor = cursorCodec.encode(
                    RESOURCE, queryKey, SORT, last.at().toEpochMilli(), null,
                    last.caseId(), null, last.eventOrder());
        }
        return new CursorPage<>(visible.stream().map(AuditEventRow::toView).toList(), nextCursor);
    }
}
