package com.turing.vigilant.graph;

import java.time.Instant;

/** A directed REFERRED edge, referrer → referee. */
public record ReferralEdge(String referrerUserId, String refereeUserId, Instant createdAt) {
}
