package com.turing.vigilant.campaign;

import com.turing.vigilant.shared.CampaignId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised when a referral code is requested against a campaign that exists and is
 * owned by the tenant, but is not {@code ACTIVE} (spec section 10a). Annotated so
 * the web layer need not import it (keeps module dependencies acyclic).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class CampaignNotActiveException extends RuntimeException {

    public CampaignNotActiveException(CampaignId campaignId, CampaignStatus status) {
        super("campaign " + campaignId.value() + " is not ACTIVE (status=" + status + ")");
    }
}
