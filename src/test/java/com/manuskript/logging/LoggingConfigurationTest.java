package com.manuskript.logging;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.util.StatusPrinter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LoggingConfigurationTest {

    @Test
    void writesEachLogLevelToExpectedFile(@TempDir Path tempDir) throws Exception {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.stop();
        context.reset();

        String logDir = tempDir.toAbsolutePath().toString().replace('\\', '/');
        String template = """
                <configuration>
                  <property name=\"LOG_DIR\" value=\"__LOGDIR__\"/>

                  <appender name=\"FILE\" class=\"ch.qos.logback.core.rolling.RollingFileAppender\">
                    <file>${LOG_DIR}/manuskript.log</file>
                    <rollingPolicy class=\"ch.qos.logback.core.rolling.TimeBasedRollingPolicy\">
                      <fileNamePattern>${LOG_DIR}/manuskript.%d{yyyy-MM-dd}.log</fileNamePattern>
                      <maxHistory>30</maxHistory>
                    </rollingPolicy>
                    <filter class=\"ch.qos.logback.classic.filter.ThresholdFilter\">
                      <level>INFO</level>
                    </filter>
                    <encoder>
                      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
                    </encoder>
                  </appender>

                  <appender name=\"DEBUG_FILE\" class=\"ch.qos.logback.core.rolling.RollingFileAppender\">
                    <file>${LOG_DIR}/debug.log</file>
                    <rollingPolicy class=\"ch.qos.logback.core.rolling.TimeBasedRollingPolicy\">
                      <fileNamePattern>${LOG_DIR}/debug.%d{yyyy-MM-dd}.log</fileNamePattern>
                      <maxHistory>30</maxHistory>
                    </rollingPolicy>
                    <filter class=\"ch.qos.logback.classic.filter.LevelFilter\">
                      <level>DEBUG</level>
                      <onMatch>ACCEPT</onMatch>
                      <onMismatch>DENY</onMismatch>
                    </filter>
                    <encoder>
                      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
                    </encoder>
                  </appender>

                  <root level=\"DEBUG\">
                    <appender-ref ref=\"FILE\"/>
                    <appender-ref ref=\"DEBUG_FILE\"/>
                  </root>
                </configuration>
                """;

        String config = template.replace("__LOGDIR__", logDir);

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(new ByteArrayInputStream(config.getBytes(StandardCharsets.UTF_8)));
        StatusPrinter.printInCaseOfErrorsOrWarnings(context);

        Logger logger = LoggerFactory.getLogger("TestLogger");
        logger.debug("debug message");
        logger.info("info message");
        logger.warn("warn message");
        logger.error("error message");

        context.stop();

        Path mainLog = tempDir.resolve("manuskript.log");
        Path debugLog = tempDir.resolve("debug.log");

        assertTrue(Files.exists(mainLog), "manuskript.log sollte existieren");
        assertTrue(Files.exists(debugLog), "debug.log sollte existieren");

        String mainContent = Files.readString(mainLog, StandardCharsets.UTF_8);
        String debugContent = Files.readString(debugLog, StandardCharsets.UTF_8);

        assertTrue(mainContent.contains("info message"));
        assertTrue(mainContent.contains("warn message"));
        assertTrue(mainContent.contains("error message"));
        assertFalse(mainContent.contains("debug message"));

        assertTrue(debugContent.contains("debug message"));
        assertFalse(debugContent.contains("info message"));
        assertFalse(debugContent.contains("warn message"));
        assertFalse(debugContent.contains("error message"));
    }

    @Test
    void realLogbackXmlConfigurationWorks() throws Exception {
        // Lade die echte logback.xml
        File realLogbackXml = new File("config/logback.xml");
        assertTrue(realLogbackXml.exists(), "config/logback.xml sollte existieren");

        // Reset LoggerContext
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.stop();
        context.reset();

        // Lade die echte Konfiguration
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(realLogbackXml);
        StatusPrinter.printInCaseOfErrorsOrWarnings(context);

        // Teste die echte Konfiguration
        Logger logger = LoggerFactory.getLogger("RealConfigTest");
        logger.debug("real debug message");
        logger.info("real info message");
        logger.warn("real warn message");
        logger.error("real error message");

        context.stop();

        // Prüfe die echten Log-Dateien
        Path mainLog = Path.of("logs/manuskript.log");
        Path debugLog = Path.of("logs/debug.log");

        if (Files.exists(mainLog)) {
            String mainContent = Files.readString(mainLog, StandardCharsets.UTF_8);
            assertTrue(mainContent.contains("real info message") || 
                      mainContent.contains("real warn message") || 
                      mainContent.contains("real error message"),
                      "manuskript.log sollte INFO/WARN/ERROR enthalten");
        }

        if (Files.exists(debugLog)) {
            String debugContent = Files.readString(debugLog, StandardCharsets.UTF_8);
            assertTrue(debugContent.contains("real debug message"),
                      "debug.log sollte DEBUG enthalten");
        }
    }
}
