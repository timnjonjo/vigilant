package com.turing.vigilant.shared;

/**
 * Reputation classification of an IP address, derived from its ASN (spec: IP
 * reputation Tier 1). Lives in {@code shared} so the graph snapshot, the scoring
 * rule and the ip-reputation module can all reference it without a module cycle.
 */
public enum IpType {
    /** Consumer/broadband ASN — normal for a genuine referee. */
    RESIDENTIAL,
    /** Mobile carrier ASN (incl. CGNAT'd mobile ranges) — treated as known-good. */
    MOBILE,
    /** Cloud/hosting/VPN ASN — a risk signal at payout time. */
    DATACENTER,
    /** ASN could not be resolved from the database. */
    UNKNOWN
}
