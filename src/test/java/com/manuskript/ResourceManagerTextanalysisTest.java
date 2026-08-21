package com.manuskript;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceManagerTextanalysisTest {

    @Test
    void textanalysisPropertiesAreFoundInAppConfigOrClasspath() throws Exception {
        try (InputStream input = ResourceManager.getPropertiesResource("textanalysis.properties")) {
            assertNotNull(input, "textanalysis.properties muss im App-Config oder Classpath liegen");
            Properties props = new Properties();
            props.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
            assertTrue(props.containsKey("fuellwoerter") || props.containsKey("sprechwörter")
                            || props.containsKey("sprechwoerter"),
                    () -> "Unerwartete Keys: " + props.keySet());
        }
        assertTrue(ResourceManager.resolveConfigFile("textanalysis.properties").getAbsolutePath()
                .replace('\\', '/')
                .endsWith("/config/textanalysis.properties"));
    }
}
