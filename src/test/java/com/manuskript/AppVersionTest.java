package com.manuskript;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AppVersionTest {

    @Test
    void currentReadsClasspathVersion() {
        String version = AppVersion.current();
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+"),
                () -> "Unerwartete Version: " + version);
    }
}
