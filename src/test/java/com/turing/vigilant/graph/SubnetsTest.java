package com.turing.vigilant.graph;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubnetsTest {

    @Test
    void derivesSlash24SubnetFromIpv4() {
        assertThat(Subnets.subnetOf("197.232.14.88")).isEqualTo("197.232.14.0/24");
    }

    @Test
    void twoAddressesInSameSlash24ShareASubnet() {
        assertThat(Subnets.subnetOf("197.232.14.88"))
                .isEqualTo(Subnets.subnetOf("197.232.14.201"));
    }

    @Test
    void addressesInDifferentSlash24DoNotShareASubnet() {
        assertThat(Subnets.subnetOf("197.232.14.88"))
                .isNotEqualTo(Subnets.subnetOf("197.232.15.88"));
    }

    @Test
    void returnsRawValueForNonIpv4Input() {
        // IPv6 / malformed inputs are passed through unchanged rather than crashing ingestion.
        assertThat(Subnets.subnetOf("not-an-ip")).isEqualTo("not-an-ip");
    }
}
