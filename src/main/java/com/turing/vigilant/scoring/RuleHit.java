package com.turing.vigilant.scoring;

import com.turing.vigilant.shared.ReasonCode;

/** A single rule firing: the reason it raised and its weight contribution. */
public record RuleHit(ReasonCode reasonCode, double weight) {
}
