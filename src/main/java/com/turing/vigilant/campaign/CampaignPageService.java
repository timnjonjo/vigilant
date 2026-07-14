package com.turing.vigilant.campaign;

import com.turing.vigilant.shared.TenantId;
import com.turing.vigilant.web.pagination.CursorCodec;
import com.turing.vigilant.web.pagination.CursorPage;
import com.turing.vigilant.web.pagination.CursorState;
import com.turing.vigilant.web.pagination.InvalidCursorException;
import com.turing.vigilant.web.pagination.PageLimits;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class CampaignPageService {

    private static final String RESOURCE = "campaigns";
    private static final String SORT = "CREATED_AT";

    private final CampaignPageRepository repository;
    private final CursorCodec cursorCodec;

    public CampaignPageService(CampaignPageRepository repository, CursorCodec cursorCodec) {
        this.repository = repository;
        this.cursorCodec = cursorCodec;
    }

    public CursorPage<CampaignView> page(
            TenantId tenantId, String cursor, int requestedLimit) {
        int limit = PageLimits.requireValid(requestedLimit);
        String queryKey = tenantId.value();
        CursorState state = cursorCodec.decode(cursor, RESOURCE, queryKey, SORT);
        Instant cursorCreatedAt = null;
        String cursorCampaignId = null;
        if (state != null) {
            if (state.epochMillis() == null || state.stringId() == null) {
                throw new InvalidCursorException();
            }
            cursorCreatedAt = Instant.ofEpochMilli(state.epochMillis());
            cursorCampaignId = state.stringId();
        }

        List<Campaign> rows = repository.findPage(
                tenantId.value(), cursorCreatedAt, cursorCampaignId, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<Campaign> visible = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = null;
        if (hasMore) {
            Campaign last = visible.get(visible.size() - 1);
            nextCursor = cursorCodec.encode(
                    RESOURCE, queryKey, SORT, last.getCreatedAt().toEpochMilli(),
                    null, null, last.getCampaignId(), null);
        }
        return new CursorPage<>(visible.stream().map(CampaignView::of).toList(), nextCursor);
    }
}
