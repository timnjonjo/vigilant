package com.turing.vigilant.ipreputation;

import com.turing.vigilant.shared.IpType;

import java.net.InetAddress;
import java.util.Optional;

/**
 * Tier 1 IP reputation: resolve the ASN locally, then classify against the
 * datacenter list and the Kenyan-carrier allowlist. The allowlist is checked
 * first so a mobile carrier is never miscategorised as datacenter. No network
 * calls, no checked exceptions leak out (spec conventions).
 */
public class LocalAsnReputationChecker implements IpReputationChecker {

    static final String SOURCE = "local-asn";
    static final double DATACENTER_RISK_SCORE = 0.70;

    private final AsnResolver resolver;
    private final DatacenterAsnCatalog catalog;

    public LocalAsnReputationChecker(AsnResolver resolver, DatacenterAsnCatalog catalog) {
        this.resolver = resolver;
        this.catalog = catalog;
    }

    @Override
    public IpReputationResult check(String ipAddress) {
        InetAddress address = IpAddresses.parseLiteral(ipAddress);
        Optional<Long> asn = resolver.resolveAsn(address);
        if (asn.isEmpty()) {
            return new IpReputationResult(IpType.UNKNOWN, 0.0, SOURCE);
        }
        long resolved = asn.get();
        if (catalog.isKenyanCarrier(resolved)) {
            return new IpReputationResult(IpType.MOBILE, 0.0, SOURCE);
        }
        if (catalog.isDatacenter(resolved)) {
            return new IpReputationResult(IpType.DATACENTER, DATACENTER_RISK_SCORE, SOURCE);
        }
        return new IpReputationResult(IpType.RESIDENTIAL, 0.0, SOURCE);
    }
}
