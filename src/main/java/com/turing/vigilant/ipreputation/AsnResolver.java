package com.turing.vigilant.ipreputation;

import java.net.InetAddress;
import java.util.Optional;

/**
 * Resolves the Autonomous System Number for an IP address. The seam between the
 * {@code .mmdb} file and the classification logic: production reads a local
 * MaxMind-format database ({@link com.turing.vigilant.ipreputation.MmdbAsnResolver}); tests substitute a stub.
 */
public interface AsnResolver {

    /** The ASN owning this address, or empty if the database has no entry for it. */
    Optional<Long> resolveAsn(InetAddress address);
}
