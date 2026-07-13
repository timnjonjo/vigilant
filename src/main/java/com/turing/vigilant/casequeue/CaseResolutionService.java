package com.turing.vigilant.casequeue;

import com.turing.vigilant.shared.Decision;
import com.turing.vigilant.shared.TenantId;
import com.turing.vigilant.webhook.ResolutionCallback;
import com.turing.vigilant.webhook.WebhookDispatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Resolves a HOLD/REJECT case on behalf of an analyst and notifies the host of
 * the final action via the async decision webhook (spec section 7).
 */
@Service
public class CaseResolutionService {

    private final FraudCaseRepository repository;
    private final WebhookDispatcher webhookDispatcher;
    private final Clock clock;

    public CaseResolutionService(FraudCaseRepository repository,
                                 WebhookDispatcher webhookDispatcher, Clock clock) {
        this.repository = repository;
        this.webhookDispatcher = webhookDispatcher;
        this.clock = clock;
    }

    @Transactional
    public FraudCase resolve(TenantId tenantId, long caseId, Decision resolution, String resolvedBy) {
        FraudCase fraudCase = repository.findByIdAndTenantId(caseId, tenantId.value())
                .orElseThrow(() -> new CaseNotFoundException(caseId));

        fraudCase.resolve(resolution, resolvedBy, clock.instant());
        repository.save(fraudCase);

        webhookDispatcher.dispatch(tenantId, new ResolutionCallback(
                fraudCase.getReferralCode(), fraudCase.getRefereeUserId(), resolution, fraudCase.getId()));

        return fraudCase;
    }
}
