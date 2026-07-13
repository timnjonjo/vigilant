package com.turing.vigilant;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies the Spring Modulith application-module structure: no cycles between
 * modules and no access to another module's internals. This is the enforcement
 * mechanism standing in for a multi-module build.
 */
class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(VigilantApplication.class);

    @Test
    void modulesRespectBoundaries() {
        modules.verify();
    }
}
