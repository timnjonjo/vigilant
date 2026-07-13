package com.turing.vigilant.ipreputation;

/**
 * Classifies an IP address by reputation. Tier 1 is a fast, local, zero-network
 * ASN lookup; a Tier 2 external checker is a separate implementation for later.
 */
public interface IpReputationChecker {

    /**
     * @throws IllegalArgumentException if {@code ipAddress} is not a valid IP literal
     */
    IpReputationResult check(String ipAddress);
}
