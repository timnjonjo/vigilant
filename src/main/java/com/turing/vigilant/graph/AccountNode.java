package com.turing.vigilant.graph;

import com.turing.vigilant.shared.IpType;

/**
 * A node attribute snapshot carried in the {@link ReferralNeighbourhood}: the
 * account's id, its last-known IP reputation classification, and whether it has
 * converted. Lets graph-pure scoring rules — and the dashboard subgraph — read
 * node state without re-running anything.
 */
public record AccountNode(String userId, IpType ipType, boolean converted) {

    /** Back-compat overload for callers that don't track conversion; defaults false. */
    public AccountNode(String userId, IpType ipType) {
        this(userId, ipType, false);
    }
}
