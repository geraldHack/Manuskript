package com.manuskript;

import com.manuskript.dictation.WhisperRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installations- und Prüflogik für optionale Tools (gebündelte Archive bevorzugt).
 */
public final class ToolSetupSupport {

    private static final Logger logger = LoggerFactory.getLogger(ToolSetupSupport.class);

    public enum ToolId {
        PANDOC,
        FFMPEG,
        WHISPER,
        LANGUAGE_TOOL,
        KI
    }

    private ToolSetupSupport() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    /** {@code null} wenn Pandoc nutzbar ist, sonst Kurzstatus. */
    public static String pandocStatus() {
        File exe = resolvePandocBinary();
        if (exe != null) {
            return null;
        }
        if (commandWorks("pandoc", "--version")) {
            return null;
        }
        File zip = resolvePandocArchive();
        if (zip != null) {
            return "Gebündeltes Archiv vorhanden – bitte entpacken";
        }
        return "Nicht gefunden (weder Bundle noch PATH)";
    }

    /** {@code null} wenn FFmpeg nutzbar ist, sonst Kurzstatus. */
    public static String ffmpegStatus() {
        File exe = resolveFfmpegBinary(false);
        if (exe != null) {
            return null;
        }
        if (commandWorks("ffmpeg", "-version")) {
            return null;
        }
        File zip = resolveFfmpegArchive();
        if (zip != null) {
            return "Gebündeltes Archiv vorhanden – bitte entpacken";
        }
        return "Nicht gefunden (weder Bundle noch PATH)";
    }

    public static String whisperStatus() {
        boolean exeOk = !WhisperRuntime.isExecutableMissing();
        boolean modelOk = !WhisperRuntime.isModelMissing();
        if (exeOk && modelOk) {
            return null;
        }
        if (!exeOk && !modelOk) {
            return "whisper-cli und Modell fehlen";
        }
        if (!exeOk) {
            return "whisper-cli fehlt";
        }
        return "Whisper-Modell fehlt";
    }

    public static String languageToolJarStatus() {
        File jar = resolveLanguageToolJar();
        if (jar == null) {
            return "languagetool-server.jar nicht gefunden";
        }
        return null;
    }

    /**
     * Entpackt gebündeltes Pandoc falls nötig.
     *
     * @return {@code null} bei Erfolg, sonst Fehlermeldung
     */
    public static String ensurePandoc(Consumer<String> log) {
        Consumer<String> out = logOrNull(log);
        File existing = resolvePandocBinary();
        if (existing != null) {
            ensureExecutable(existing.toPath());
            out.accept("Pandoc bereits vorhanden: " + existing.getAbsolutePath());
            return null;
        }
        if (commandWorks("pandoc", "--version")) {
            out.accept("Pandoc im PATH gefunden.");
            return null;
        }
        File zip = resolvePandocArchive();
        if (zip == null) {
            return "Kein Pandoc-Binary und kein Bundle-Archiv (pandoc-mac.zip / pandoc.zip).";
        }
        File targetDir = ApplicationPaths.resolvePandocDirectory();
        out.accept("Entpacke " + zip.getName() + " nach " + targetDir.getAbsolutePath());
        try {
            Files.createDirectories(targetDir.toPath());
            extractZip(zip.toPath(), targetDir.toPath(), out);
        } catch (Exception e) {
            logger.warn("Pandoc-Entpacken fehlgeschlagen", e);
            return "Pandoc entpacken fehlgeschlagen: " + e.getMessage();
        }
        File binary = resolvePandocBinary();
        if (binary == null) {
            return "Archiv entpackt, aber Pandoc-Binary nicht gefunden.";
        }
        ensureExecutable(binary.toPath());
        out.accept("Pandoc bereit: " + binary.getAbsolutePath());
        return null;
    }

    /**
     * Entpackt gebündeltes FFmpeg falls nötig.
     *
     * @return {@code null} bei Erfolg, sonst Fehlermeldung
     */
    public static String ensureFfmpeg(Consumer<String> log) {
        Consumer<String> out = logOrNull(log);
        File existing = resolveFfmpegBinary(false);
        if (existing != null) {
            ensureExecutable(existing.toPath());
            out.accept("FFmpeg bereits vorhanden: " + existing.getAbsolutePath());
            return null;
        }
        if (commandWorks("ffmpeg", "-version")) {
            out.accept("FFmpeg im PATH gefunden.");
            return null;
        }
        File zip = resolveFfmpegArchive();
        if (zip == null) {
            return "Kein FFmpeg-Binary und kein Bundle-Archiv (ffmpeg-mac.zip / ffmpeg.zip).";
        }
        File targetDir = ApplicationPaths.resolveBundledPath("ffmpeg");
        out.accept("Entpacke " + zip.getName() + " nach " + targetDir.getAbsolutePath());
        try {
            Files.createDirectories(targetDir.toPath());
            extractZip(zip.toPath(), targetDir.toPath(), out);
        } catch (Exception e) {
            logger.warn("FFmpeg-Entpacken fehlgeschlagen", e);
            return "FFmpeg entpacken fehlgeschlagen: " + e.getMessage();
        }
        File binary = resolveFfmpegBinary(false);
        if (binary == null) {
            return "Archiv entpackt, aber FFmpeg-Binary nicht gefunden.";
        }
        ensureExecutable(binary.toPath());
        File probe = new File(binary.getParentFile(), isWindows() ? "ffprobe.exe" : "ffprobe");
        if (probe.isFile()) {
            ensureExecutable(probe.toPath());
        }
        out.accept("FFmpeg bereit: " + binary.getAbsolutePath());
        return null;
    }

    /**
     * Richtet lokales Whisper ein (whisper-cli + Modell).
     *
     * @return {@code null} bei Erfolg, sonst Fehlermeldung
     */
    public static String ensureWhisper(Consumer<String> log) {
        Consumer<String> out = logOrNull(log);
        boolean needExe = WhisperRuntime.isExecutableMissing();
        boolean needModel = WhisperRuntime.isModelMissing();
        if (!needExe && !needModel) {
            out.accept("Whisper bereits eingerichtet.");
            return null;
        }
        if (needExe) {
            if (isMac()) {
                if (!WhisperRuntime.isHomebrewAvailable()) {
                    out.accept("Homebrew fehlt – öffne offiziellen Installer im Terminal…");
                    String brewErr = HomebrewSupport.openInstallInTerminal();
                    if (brewErr != null) {
                        return brewErr;
                    }
                    return "Homebrew-Installer im Terminal gestartet. Nach Abschluss hier erneut "
                            + "„Diktat einrichten“ wählen.";
                }
                out.accept("=== whisper-cpp installieren (Homebrew) ===");
            } else if (isWindows()) {
                out.accept("=== whisper-cli installieren (Windows-ZIP) ===");
            } else {
                return "Automatische whisper-cli-Installation ist unter diesem Betriebssystem nicht verfügbar. "
                        + "Bitte whisper.cpp manuell installieren oder OpenAI-Backend nutzen.";
            }
            String err = WhisperRuntime.installWhisperCpp(out);
            if (err != null) {
                return err;
            }
        }
        if (WhisperRuntime.isModelMissing()) {
            out.accept("=== Modell laden ===");
            String err = WhisperRuntime.downloadDefaultModel(out);
            if (err != null) {
                return err;
            }
        }
        if (WhisperRuntime.isExecutableMissing() || WhisperRuntime.isModelMissing()) {
            return "Whisper noch unvollständig eingerichtet.";
        }
        out.accept("Whisper bereit.");
        return null;
    }

    /**
     * Startet den gebündelten LanguageTool-Server.
     *
     * @return {@code null} bei Erfolg, sonst Fehlermeldung
     */
    public static String ensureLanguageTool(Consumer<String> log) {
        Consumer<String> out = logOrNull(log);
        File jar = resolveLanguageToolJar();
        if (jar == null) {
            return "languagetool-server.jar nicht gefunden (Ordner „language tool“).";
        }
        out.accept("JAR: " + jar.getAbsolutePath());
        out.accept("Starte LanguageTool-Server…");
        try {
            LanguageToolService service = new LanguageToolService();
            Boolean ok = service.ensureServerRunning().get(45, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(ok)) {
                out.accept("LanguageTool erreichbar.");
                return null;
            }
            return "LanguageTool-Server startete nicht (Timeout oder JAR-Fehler).";
        } catch (Exception e) {
            logger.warn("LanguageTool-Start fehlgeschlagen", e);
            return "LanguageTool-Start fehlgeschlagen: " + e.getMessage();
        }
    }

    /**
     * Ollama: unter macOS via Homebrew, sonst Download-Seite im Browser.
     * OpenAI-Backend: Hinweis auf API-Key.
     *
     * @return {@code null} bei Erfolg / sinnvollem nächsten Schritt, sonst Fehlermeldung
     */
    public static String ensureKi(Consumer<String> log) {
        Consumer<String> out = logOrNull(log);
        String backend = ResourceManager.getParameter("agent.backend", "Ollama");
        if ("OpenAI".equalsIgnoreCase(backend)) {
            String key = ResourceManager.getParameter("agent.openai.api_key", "");
            if (key != null && !key.isBlank()) {
                out.accept("OpenAI-kompatibler API-Key ist gesetzt.");
                return null;
            }
            return "Kein API-Key: unter Parameter → Agent → agent.openai.api_key eintragen.";
        }
        return ensureOllama(log);
    }

    /**
     * Installiert bzw. startet Ollama unabhängig vom aktuellen {@code agent.backend}.
     *
     * @return {@code null} bei Erfolg / sinnvollem nächsten Schritt, sonst Fehlermeldung
     */
    public static String ensureOllama(Consumer<String> log) {
        Consumer<String> out = logOrNull(log);
        if (httpReachable("http://127.0.0.1:11434/api/tags")) {
            out.accept("Ollama läuft bereits.");
            return null;
        }
        if (isMac() && WhisperRuntime.isHomebrewAvailable()) {
            String brew = WhisperRuntime.resolveBrewExecutable();
            out.accept("$ " + brew + " install ollama");
            Process process = null;
            try {
                ProcessBuilder pb = new ProcessBuilder(brew, "install", "ollama");
                pb.redirectErrorStream(true);
                process = pb.start();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        out.accept(line);
                    }
                }
                boolean finished = process.waitFor(30, TimeUnit.MINUTES);
                if (!finished) {
                    process.destroyForcibly();
                    return "brew install ollama: Zeitlimit überschritten.";
                }
                if (process.exitValue() != 0) {
                    return "brew install ollama fehlgeschlagen (Exit " + process.exitValue() + ").";
                }
            } catch (Exception e) {
                return "Ollama-Installation fehlgeschlagen: " + e.getMessage();
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
            out.accept("Starte ollama serve…");
            try {
                ProcessBuilder serve = new ProcessBuilder(brew, "services", "start", "ollama");
                serve.redirectErrorStream(true);
                Process p = serve.start();
                p.waitFor(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.debug("ollama services start: {}", e.getMessage());
            }
            try {
                new ProcessBuilder("ollama", "serve").redirectErrorStream(true).start();
            } catch (Exception ignored) {
            }
            for (int i = 0; i < 15; i++) {
                if (httpReachable("http://127.0.0.1:11434/api/tags")) {
                    out.accept("Ollama erreichbar.");
                    return null;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            out.accept("Ollama installiert – ggf. App „Ollama“ starten, dann erneut prüfen.");
            return null;
        }
        out.accept("Öffne Ollama-Download im Browser…");
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create("https://ollama.com/download"));
            }
            return "Bitte Ollama installieren und starten, dann hier erneut prüfen.";
        } catch (Exception e) {
            return "Browser konnte nicht geöffnet werden. Bitte https://ollama.com/download besuchen.";
        }
    }

    public static String install(ToolId id, Consumer<String> log) {
        return switch (id) {
            case PANDOC -> ensurePandoc(log);
            case FFMPEG -> ensureFfmpeg(log);
            case WHISPER -> ensureWhisper(log);
            case LANGUAGE_TOOL -> ensureLanguageTool(log);
            case KI -> ensureKi(log);
        };
    }

    public static File resolvePandocBinary() {
        String name = isWindows() ? "pandoc.exe" : "pandoc";
        File[] candidates = {
                new File(ApplicationPaths.resolvePandocDirectory(), name),
                new File(ApplicationPaths.getApplicationHomeDirectory(), name),
                new File(name)
        };
        for (File c : candidates) {
            if (c.isFile()) {
                return c;
            }
        }
        File pandocDir = ApplicationPaths.resolvePandocDirectory();
        File nested = findNamedFile(pandocDir, name, 3);
        if (nested != null) {
            return nested;
        }
        String[] known = {"/opt/homebrew/bin/pandoc", "/usr/local/bin/pandoc", "/usr/bin/pandoc"};
        for (String path : known) {
            File f = new File(path);
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    public static File resolveFfmpegBinary(boolean allowPathFallback) {
        String name = isWindows() ? "ffmpeg.exe" : "ffmpeg";
        File dir = ApplicationPaths.resolveBundledPath("ffmpeg");
        File exe = new File(dir, name);
        if (exe.isFile()) {
            return exe;
        }
        File binExe = new File(dir, "bin" + File.separator + name);
        if (binExe.isFile()) {
            return binExe;
        }
        File nested = findNamedFile(dir, name, 3);
        if (nested != null) {
            return nested;
        }
        String[] known = {"/opt/homebrew/bin/ffmpeg", "/usr/local/bin/ffmpeg"};
        for (String path : known) {
            File f = new File(path);
            if (f.isFile()) {
                return f;
            }
        }
        if (allowPathFallback && commandWorks(name, "-version")) {
            return new File(name);
        }
        return null;
    }

    private static File resolvePandocArchive() {
        String[] names = isWindows()
                ? new String[]{"pandoc.zip"}
                : new String[]{"pandoc-mac.zip", "pandoc.zip"};
        File pandocDir = ApplicationPaths.resolvePandocDirectory();
        for (String name : names) {
            File inDir = new File(pandocDir, name);
            if (inDir.isFile()) {
                return inDir;
            }
            File inHome = new File(ApplicationPaths.getApplicationHomeDirectory(), name);
            if (inHome.isFile()) {
                return inHome;
            }
            File relative = new File(name);
            if (relative.isFile()) {
                return relative;
            }
        }
        return null;
    }

    private static File resolveFfmpegArchive() {
        String preferred = isWindows() ? "ffmpeg.zip"
                : (isMac() ? "ffmpeg-mac.zip" : "ffmpeg-linux.zip");
        File dir = ApplicationPaths.resolveBundledPath("ffmpeg");
        File preferredFile = new File(dir, preferred);
        if (preferredFile.isFile()) {
            return preferredFile;
        }
        File win = new File(dir, "ffmpeg.zip");
        if (win.isFile()) {
            return win;
        }
        return null;
    }

    private static File resolveLanguageToolJar() {
        String relative = "language tool/languagetool-server.jar";
        File bundled = ApplicationPaths.resolveBundledPath(relative);
        if (bundled.isFile()) {
            return bundled;
        }
        File alt = new File(ApplicationPaths.getApplicationHomeDirectory(), relative);
        return alt.isFile() ? alt : null;
    }

    private static File findNamedFile(File root, String fileName, int maxDepth) {
        if (root == null || !root.isDirectory() || maxDepth < 0) {
            return null;
        }
        File direct = new File(root, fileName);
        if (direct.isFile()) {
            return direct;
        }
        File[] children = root.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                File found = findNamedFile(child, fileName, maxDepth - 1);
                if (found != null) {
                    return found;
                }
            } else if (child.getName().equals(fileName)) {
                return child;
            }
        }
        return null;
    }

    private static void extractZip(Path zipPath, Path targetDir, Consumer<String> log) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(targetDir)) {
                    throw new IllegalStateException("Ungültiger ZIP-Eintrag: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (var os = Files.newOutputStream(entryPath)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                    }
                    String lower = entryPath.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (lower.equals("pandoc") || lower.equals("pandoc.exe")
                            || lower.equals("ffmpeg") || lower.equals("ffmpeg.exe")
                            || lower.equals("ffprobe") || lower.equals("ffprobe.exe")) {
                        ensureExecutable(entryPath);
                    }
                    if (log != null) {
                        log.accept("  " + entry.getName());
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void ensureExecutable(Path file) {
        if (file == null || isWindows() || !Files.isRegularFile(file)) {
            return;
        }
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            boolean changed = perms.add(PosixFilePermission.OWNER_EXECUTE)
                    | perms.add(PosixFilePermission.GROUP_EXECUTE)
                    | perms.add(PosixFilePermission.OTHERS_EXECUTE);
            if (changed) {
                Files.setPosixFilePermissions(file, perms);
            }
        } catch (Exception e) {
            file.toFile().setExecutable(true, false);
        }
    }

    private static boolean commandWorks(String command, String arg) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command, arg);
            pb.redirectErrorStream(true);
            process = pb.start();
            boolean finished = process.waitFor(8, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    public static boolean httpReachable(String url) {
        try {
            var conn = URI.create(url).toURL().openConnection();
            if (conn instanceof java.net.HttpURLConnection http) {
                http.setConnectTimeout(2500);
                http.setReadTimeout(2500);
                http.setRequestMethod("GET");
                int code = http.getResponseCode();
                http.disconnect();
                return code >= 200 && code < 500;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static Consumer<String> logOrNull(Consumer<String> log) {
        return log != null ? log : msg -> {};
    }
}
