package com.manuskript.novelwizard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class NovelWizardSessionStore {
    private static final Logger logger = LoggerFactory.getLogger(NovelWizardSessionStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path projectDirectory;

    public NovelWizardSessionStore(Path projectDirectory) {
        this.projectDirectory = projectDirectory;
    }

    public Path sessionPath() {
        return projectDirectory.resolve("data").resolve("novel-wizard").resolve("session.json");
    }

    public boolean hasSession() {
        return Files.exists(sessionPath());
    }

    public Optional<NovelWizardSession> load() {
        Path sessionPath = sessionPath();
        if (!Files.exists(sessionPath)) {
            return Optional.empty();
        }
        try {
            NovelWizardSession session = GSON.fromJson(Files.readString(sessionPath, StandardCharsets.UTF_8),
                    NovelWizardSession.class);
            if (session != null) {
                session.ensurePhaseStatus();
                session.normalizeChatPhases();
                return Optional.of(session);
            }
        } catch (Exception e) {
            logger.warn("Roman-Assistent-Session konnte nicht geladen werden: {}", sessionPath, e);
        }
        return Optional.empty();
    }

    public void save(NovelWizardSession session) {
        if (session == null) {
            return;
        }
        try {
            session.touch();
            Path path = sessionPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(session), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Roman-Assistent-Session konnte nicht gespeichert werden", e);
        }
    }

    public void archiveCurrentSession() {
        Path path = sessionPath();
        if (!Files.exists(path)) {
            return;
        }
        try {
            Path archiveDir = path.getParent().resolve("session-archive");
            Files.createDirectories(archiveDir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Files.move(path, archiveDir.resolve("session-" + stamp + ".json"));
        } catch (IOException e) {
            logger.warn("Roman-Assistent-Session konnte nicht archiviert werden", e);
        }
    }
}
