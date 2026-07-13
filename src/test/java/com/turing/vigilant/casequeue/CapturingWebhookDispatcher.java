package com.turing.vigilant.casequeue;

import com.turing.vigilant.shared.TenantId;
import com.turing.vigilant.webhook.ResolutionCallback;
import com.turing.vigilant.webhook.WebhookDispatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Named test stub that records dispatched callbacks instead of making HTTP calls,
 * so resolution flows can assert the webhook payload without a live host.
 */
class CapturingWebhookDispatcher extends WebhookDispatcher {

    record Dispatch(TenantId tenantId, ResolutionCallback callback) {
    }

    final List<Dispatch> dispatched = new ArrayList<>();

    CapturingWebhookDispatcher() {
        super(null, null, null);
    }

    @Override
    public void dispatch(TenantId tenantId, ResolutionCallback callback) {
        dispatched.add(new Dispatch(tenantId, callback));
    }
}
