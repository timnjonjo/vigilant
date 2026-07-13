package com.turing.vigilant.ipreputation;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class MmdbAsnResolverTest {

    @Test
    void missingDatabaseFileFailsFastWithAClearError() {
        Path missing = Path.of("build", "no-such-database.mmdb");

        assertThatExceptionOfType(IpReputationDatabaseException.class)
                .isThrownBy(() -> new MmdbAsnResolver(missing))
                .withMessageContaining(missing.toString());
    }
}
