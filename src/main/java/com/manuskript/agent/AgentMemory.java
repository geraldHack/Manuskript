package com.manuskript.agent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verwaltet das Gedächtnis eines Agenten als Dateisystem-Hierarchie.
 *
 * data/agents/{name}/{chapterName}/
 *   summary.md       (komprimiertes Langgedächtnis, max. 5KB)
 *   latest.md        (letzte Analyse, vollständig)
 *   open_issues.md   (nicht behobene Probleme)
 *   archive/         (historische Analysen)
 */
public class AgentMemory {
    private static final Logger logger = LoggerFactory.getLogger(AgentMemory.class);

    private final File agentDir;
    private final String agentName;
    private final String chapterName;

    public AgentMemory(File projectDir, String agentName, String chapterName) {
        this.agentName = agentName;
        this.chapterName = sanitizeChapterName(chapterName);
        this.agentDir = new File(projectDir, "data/agents/" + agentName + "/" + this.chapterName);
    }

    /**
     * Konstruktor für rückwärtskompatibilität (ohne Kapitelname)
     */
    public AgentMemory(File projectDir, String agentName) {
        this(projectDir, agentName, "global");
    }

    private String sanitizeChapterName(String name) {
        if (name == null || name.isEmpty()) return "global";
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    public File getAgentDir() {
        if (!agentDir.exists()) {
            agentDir.mkdirs();
        }
        return agentDir;
    }

    public File getSummaryFile() {
        return new File(getAgentDir(), "summary.md");
    }

    public File getLatestFile() {
        return new File(getAgentDir(), "latest.md");
    }

    public File getOpenIssuesFile() {
        return new File(getAgentDir(), "open_issues.md");
    }

    public File getArchiveDir() {
        File dir = new File(getAgentDir(), "archive");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public String loadSummary() {
        File f = getSummaryFile();
        if (f.exists()) {
            try {
                return Files.readString(f.toPath());
            } catch (IOException e) {
                logger.warn("Konnte summary.md nicht lesen: {}", e.getMessage());
            }
        }
        return "";
    }

    public void saveLatest(List<Finding> findings) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# ").append(agentName).append(" — Letzte Analyse\n\n");
            sb.append("Datum: ").append(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)).append("\n\n");

            if (findings.isEmpty()) {
                sb.append("✅ Keine Probleme gefunden.\n");
            } else {
                for (Finding f : findings) {
                    sb.append("## ").append(f.getSeverityStars()).append(" ").append(f.getProblem()).append("\n\n");
                    sb.append("- **Zitat:** \"").append(f.getQuote()).append("\"\n");
                    sb.append("- **Problem:** ").append(f.getProblem()).append("\n");
                    sb.append("- **Vorschlag:** ").append(f.getSuggestion()).append("\n\n");
                }
            }

            Files.writeString(getLatestFile().toPath(), sb.toString());

            // Auch ins Archiv schreiben
            String archiveName = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".md";
            Files.writeString(new File(getArchiveDir(), archiveName).toPath(), sb.toString());

            // Summary aktualisieren (komprimiert)
            updateSummary(findings);

        } catch (IOException e) {
            logger.error("Konnte latest.md nicht schreiben: {}", e.getMessage());
        }
    }

    private void updateSummary(List<Finding> findings) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# ").append(agentName).append(" — Zusammenfassung\n\n");

            if (findings.isEmpty()) {
                sb.append("Keine offenen Probleme.\n");
            } else {
                sb.append("Offene Probleme: ").append(findings.size()).append("\n\n");
                for (Finding f : findings) {
                    sb.append("- [").append(f.getSeverity()).append("/5] ").append(f.getProblem()).append("\n");
                }
            }

            String content = sb.toString();
            // Auf max. 5KB kürzen
            if (content.length() > 5120) {
                content = content.substring(0, 5100) + "\n\n... (gekürzt)";
            }

            Files.writeString(getSummaryFile().toPath(), content);
        } catch (IOException e) {
            logger.warn("Konnte summary.md nicht schreiben: {}", e.getMessage());
        }
    }

    public void updateOpenIssues(List<Finding> findings) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# ").append(agentName).append(" — Offene Probleme\n\n");

            if (findings.isEmpty()) {
                sb.append("✅ Alle Probleme behoben.\n");
            } else {
                for (Finding f : findings) {
                    sb.append("- [").append(f.getSeverity()).append("/5] ").append(f.getProblem()).append("\n");
                }
            }

            Files.writeString(getOpenIssuesFile().toPath(), sb.toString());
        } catch (IOException e) {
            logger.warn("Konnte open_issues.md nicht schreiben: {}", e.getMessage());
        }
    }

    /**
     * Löscht alle gespeicherten Gedächtnisdateien.
     * Nützlich, um alte Ergebnisse zu invalidieren.
     */
    public void clear() {
        try {
            File summaryFile = getSummaryFile();
            File latestFile = getLatestFile();
            File openIssuesFile = getOpenIssuesFile();

            if (summaryFile.exists()) {
                Files.delete(summaryFile.toPath());
                logger.info("Gedächtnis gelöscht: {}", summaryFile.getPath());
            }
            if (latestFile.exists()) {
                Files.delete(latestFile.toPath());
                logger.info("Gedächtnis gelöscht: {}", latestFile.getPath());
            }
            if (openIssuesFile.exists()) {
                Files.delete(openIssuesFile.toPath());
                logger.info("Gedächtnis gelöscht: {}", openIssuesFile.getPath());
            }
        } catch (IOException e) {
            logger.error("Konnte Gedächtnis nicht löschen: {}", e.getMessage());
        }
    }
}
