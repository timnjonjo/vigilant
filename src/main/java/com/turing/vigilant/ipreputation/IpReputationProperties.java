package com.turing.vigilant.ipreputation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds {@code vigilant.ip-reputation.*}: the path to the local ASN database and
 * the two config-driven ASN lists (cloud/hosting, and the Kenyan-carrier
 * allowlist). Lists are here rather than hardcoded so they extend without code
 * changes; a Postgres-backed table is the later upgrade for no-redeploy updates.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "vigilant.ip-reputation")
public class IpReputationProperties {

    /** Filesystem path to the DB-IP IP-to-ASN Lite {@code .mmdb}. */
    private String databasePath;

    /** ASNs of known cloud/hosting providers — a datacenter/VPN risk signal. */
    private List<Long> datacenterAsns = new ArrayList<>();

    /** Known-good Kenyan mobile-carrier ASNs; short-circuit to MOBILE. */
    private List<Long> kenyanCarrierAsns = new ArrayList<>();
}
