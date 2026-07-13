package com.turing.vigilant.ipreputation;

import com.turing.vigilant.shared.IpType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class LocalAsnReputationCheckerTest {

    private static final long AWS_ASN = 16509L;          // datacenter (cloud)
    private static final long SAFARICOM_ASN = 33771L;    // Kenyan mobile carrier
    private static final long RESIDENTIAL_ASN = 5089L;   // some ISP, unlisted

    private final DatacenterAsnCatalog catalog =
            new DatacenterAsnCatalog(Set.of(AWS_ASN), Set.of(SAFARICOM_ASN));

    @Test
    void datacenterAsnIsFlaggedAsDatacenterWithRisk() {
        var checker = new LocalAsnReputationChecker(StubAsnResolver.returning(AWS_ASN), catalog);

        IpReputationResult result = checker.check("203.0.113.7");

        assertThat(result.type()).isEqualTo(IpType.DATACENTER);
        assertThat(result.riskScore()).isEqualTo(0.70);
        assertThat(result.source()).isEqualTo("local-asn");
    }

    @Test
    void kenyanCarrierAsnShortCircuitsToMobileWithNoRisk() {
        // Allowlist wins even though CGNAT'd mobile ranges can look datacenter-ish.
        var checker = new LocalAsnReputationChecker(StubAsnResolver.returning(SAFARICOM_ASN), catalog);

        IpReputationResult result = checker.check("197.232.14.88");

        assertThat(result.type()).isEqualTo(IpType.MOBILE);
        assertThat(result.riskScore()).isZero();
    }

    @Test
    void resolvedButUnlistedAsnIsResidentialWithNoRisk() {
        var checker = new LocalAsnReputationChecker(StubAsnResolver.returning(RESIDENTIAL_ASN), catalog);

        IpReputationResult result = checker.check("41.90.1.2");

        assertThat(result.type()).isEqualTo(IpType.RESIDENTIAL);
        assertThat(result.riskScore()).isZero();
    }

    @Test
    void unresolvableAsnIsUnknownWithNoRisk() {
        var checker = new LocalAsnReputationChecker(StubAsnResolver.missing(), catalog);

        IpReputationResult result = checker.check("192.0.2.1");

        assertThat(result.type()).isEqualTo(IpType.UNKNOWN);
        assertThat(result.riskScore()).isZero();
    }

    @Test
    void ipv6InputIsSupported() {
        var checker = new LocalAsnReputationChecker(StubAsnResolver.returning(AWS_ASN), catalog);

        IpReputationResult result = checker.check("2600:1f18::1");

        assertThat(result.type()).isEqualTo(IpType.DATACENTER);
    }

    @Test
    void malformedIpRaisesIllegalArgumentAndNeverCrashes() {
        var checker = new LocalAsnReputationChecker(StubAsnResolver.returning(AWS_ASN), catalog);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> checker.check("not-an-ip"));
    }
}
