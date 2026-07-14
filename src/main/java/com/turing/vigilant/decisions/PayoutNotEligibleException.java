package com.turing.vigilant.decisions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Generic fail-closed response for an invalid payout subject. */
@ResponseStatus(HttpStatus.CONFLICT)
public class PayoutNotEligibleException extends RuntimeException {

    public PayoutNotEligibleException() {
        super("payout subject is not an eligible converted referral");
    }
}
