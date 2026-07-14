package com.turing.vigilant.events;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Fails closed without disclosing which referral tuple element was invalid. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ReferralEventNotFoundException extends RuntimeException {

    public ReferralEventNotFoundException() {
        super("referral event target was not found");
    }
}
