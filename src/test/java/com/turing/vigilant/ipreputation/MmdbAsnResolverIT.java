package com.turing.vigilant.ipreputation;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Proves {@link MmdbAsnResolver} binds against a real MaxMind-format ASN
 * database. The binary fixture is not committed (licensing + repo hygiene); drop
 * a small DB-IP IP-to-ASN Lite {@code .mmdb} at the path below and this test runs
 * — otherwise it self-skips with a clear reason rather than passing silently.
 */
class MmdbAsnResolverIT {

    private static final Path FIXTURE =
            Path.of("src/test/resources/ipreputation/dbip-asn-lite-sample.mmdb");

    @Test
    void resolvesAsnForAKnownAddress() throws Exception {
        assumeTrue(Files.isReadable(FIXTURE),
                "No ASN mmdb fixture at " + FIXTURE + " — see module README to provision one");

        MmdbAsnResolver resolver = new MmdbAsnResolver(FIXTURE);

        // 8.8.8.8 (Google) resolves to an ASN in any real ASN database.
        Optional<Long> asn = resolver.resolveAsn(InetAddress.getByName("8.8.8.8"));

        assertThat(asn).isPresent();
    }
}
