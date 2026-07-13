package com.turing.vigilant.shared;

/**
 * Explicit, auditable reasons attached to every score. Rules-first so decisions
 * can be explained to compliance (spec section 2, principle 6).
 */
public enum ReasonCode {
    /** Referral fan-out spiked above the rolling population baseline. */
    VELOCITY_BURST,
    /** Accounts in the cluster share a device fingerprint. */
    DEVICE_COLLISION,
    /** Accounts in the cluster share an IP subnet within the time window. */
    IP_SUBNET_COLLISION,
    /** A referral cycle (self-referral ring) was detected. */
    CYCLE_DETECTED,
    /** An account in the cluster connected from a datacenter/VPN/cloud IP. */
    DATACENTER_OR_VPN_IP
}
