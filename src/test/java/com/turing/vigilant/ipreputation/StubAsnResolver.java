package com.turing.vigilant.ipreputation;

import java.net.InetAddress;
import java.util.Optional;

/**
 * Named test stub for {@link AsnResolver} (project convention: no anonymous
 * subclasses). Returns a fixed ASN for any address, or empty to simulate a
 * database miss. Never touches an {@code .mmdb} file.
 */
final class StubAsnResolver implements AsnResolver {

    private final Long asn;

    private StubAsnResolver(Long asn) {
        this.asn = asn;
    }

    static StubAsnResolver returning(long asn) {
        return new StubAsnResolver(asn);
    }

    static StubAsnResolver missing() {
        return new StubAsnResolver(null);
    }

    @Override
    public Optional<Long> resolveAsn(InetAddress address) {
        return Optional.ofNullable(asn);
    }
}
