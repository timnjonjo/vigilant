package com.turing.vigilant.campaign;

import com.turing.vigilant.shared.CampaignId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised when a campaign is unknown <em>or</em> belongs to another tenant — the
 * two are deliberately indistinguishable so a caller can't probe which campaigns
 * exist in other tenants. Annotated so the web layer need not import it (keeps
 * module dependencies acyclic).
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class CampaignNotFoundException extends RuntimeException {

    public CampaignNotFoundException(CampaignId campaignId) {
        super("campaign not found: " + campaignId.value());
    }
}
