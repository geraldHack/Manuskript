package com.manuskript.launcher;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramLauncherRunnerTest {

    @Test
    void expandsPlaceholders() {
        String expanded = ProgramLauncherRunner.expand(
                "--config-dir={configDir} --project={projectRoot} --chapter={chapterFile}",
                "/roman",
                "/app/config",
                "/roman/kapitel.docx");
        assertEquals("--config-dir=/app/config --project=/roman --chapter=/roman/kapitel.docx", expanded);
    }

    @Test
    void splitsQuotedArguments() {
        List<String> parts = ProgramLauncherRunner.splitArgs("--config-dir={configDir} \"Mein Roman\"");
        assertEquals(List.of("--config-dir={configDir}", "Mein Roman"), parts);
    }

    @Test
    void jarUsesJavaDashJar() {
        File jar = new File("/tmp/openrouter-monitor.jar");
        List<String> command = ProgramLauncherRunner.buildCommand(jar, List.of("--config-dir=/cfg"));
        assertTrue(command.get(0).toLowerCase().contains("java"));
        assertEquals("--add-modules", command.get(1));
        assertEquals("javafx.controls", command.get(2));
        assertEquals("-jar", command.get(3));
        assertEquals(jar.getAbsolutePath(), command.get(4));
        assertEquals("--config-dir=/cfg", command.get(5));
    }

    @Test
    void displayLabelFallsBackToFileName() {
        ProgramLauncher launcher = new ProgramLauncher("id", "  ", "/opt/tools/monitor.jar", null);
        assertEquals("monitor.jar", launcher.displayLabel());
    }
}
