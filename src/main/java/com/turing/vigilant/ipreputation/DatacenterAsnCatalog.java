package com.turing.vigilant.ipreputation;

import java.util.Set;

/**
 * The two ASN lists that drive classification, both config-driven so they extend
 * without code changes (spec: maintainable, not a hardcoded {@code Set} in Java):
 * cloud/hosting ASNs (a risk signal) and a Kenyan mobile-carrier allowlist that
 * short-circuits false positives on CGNAT'd mobile ranges.
 */
public class DatacenterAsnCatalog {

    private final Set<Long> datacenterAsns;
    private final Set<Long> kenyanCarrierAsns;

    public DatacenterAsnCatalog(Set<Long> datacenterAsns, Set<Long> kenyanCarrierAsns) {
        this.datacenterAsns = Set.copyOf(datacenterAsns);
        this.kenyanCarrierAsns = Set.copyOf(kenyanCarrierAsns);
    }

    public boolean isDatacenter(long asn) {
        return datacenterAsns.contains(asn);
    }

    public boolean isKenyanCarrier(long asn) {
        return kenyanCarrierAsns.contains(asn);
    }
}
