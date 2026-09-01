package com.manuskript.dictation;

import com.manuskript.ApplicationPaths;
import com.manuskript.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Auflösung von whisper-cli / whisper.cpp (lokal, ohne API-Key).
 */
public final class WhisperRuntime {

    private static final Logger logger = LoggerFactory.getLogger(WhisperRuntime.class);
    private static final String WHISPER_DIR = "whisper";
    private static final String[] MODEL_FILE_NAMES = {
            "ggml-base.bin",
            "ggml-small.bin",
            "ggml-tiny.bin"
    };
    private static final String DEFAULT_MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin";
    /** Offizielles Windows-x64-Release (CPU) von ggml-org/whisper.cpp. */
    private static final String WINDOWS_BIN_URL =
            "https://github.com/ggml-org/whisper.cpp/releases/download/v1.7.6/whisper-bin-x64.zip";

    private WhisperRuntime() {
    }

    public static boolean isMacOS() {
        return isMac();
    }

    public static boolean isWindowsOS() {
        return isWindows();
    }

    public static boolean isExecutableMissing() {
        return resolveExecutable() == null;
    }

    public static boolean isModelMissing() {
        Path model = resolveModelPath();
        return model == null || !Files.isRegularFile(model);
    }

    public static boolean isHomebrewAvailable() {
        String brew = resolveBrewExecutable();
        if (brew == null) {
            return false;
        }
        Process process = null;
        try {
            process = new ProcessBuilder(brew, "--version")
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor(15, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            logger.debug("Homebrew-Prüfung fehlgeschlagen: {}", e.getMessage());
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    public static String resolveBrewExecutable() {
        String[] candidates = {
                "/opt/homebrew/bin/brew",
                "/usr/local/bin/brew",
                "brew"
        };
        for (String candidate : candidates) {
            if ("brew".equals(candidate)) {
                String onPath = findOnPath("brew", false);
                if (onPath != null) {
                    return onPath;
                }
                continue;
            }
            File file = new File(candidate);
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    /** Zielpfad für das Standard-Modell unter App-Home. */
    static Path defaultModelTargetPath() {
        return new File(ApplicationPaths.getApplicationHomeDirectory(), WHISPER_DIR)
                .toPath()
                .resolve("models")
                .resolve("ggml-base.bin");
    }

    /**
     * Installiert whisper.cpp: macOS via Homebrew, Windows via offizielles ZIP von GitHub.
     *
     * @return {@code null} bei Erfolg, sonst Fehlermeldung
     */
    public static String installWhisperCpp(Consumer<String> log) {
        if (isWindows()) {
            return installWhisperCppWindows(log);
        }
        if (isMacOS()) {
            return installWhisperCppViaHomebrew(log);
        }
        return "Automatische whisper-cli-Installation ist unter diesem Betriebssystem nicht verfügbar. "
                + "Bitte whisper.cpp manuell installieren oder OpenAI-Backend nutzen.";
    }

    /**
     * Installiert whisper.cpp via Homebrew (macOS).
     *
     * @return {@code null} bei Erfolg, sonst Fehlermeldung
     */
    public static String installWhisperCppViaHomebrew(Consumer<String> log) {
        Consumer<String> out = log != null ? log : msg -> {};
        if (!isMacOS()) {
            return "Homebrew-Installation ist nur unter macOS verfügbar.";
        }
        String brew = resolveBrewExecutable();
        if (brew == null) {
            return "Homebrew nicht gefunden. Bitte zuerst https://brew.sh installieren.";
        }
        out.accept("$ " + brew + " install whisper-cpp");
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(brew, "install", "whisper-cpp");
            pb.redirectErrorStream(true);
            enrichPath(pb);
            process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.accept(line);
                    if (output.length() < 8000) {
                        output.append(line).append('\n');
                    }
                }
            }
            boolean finished = process.waitFor(30, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                return "brew install whisper-cpp: Zeitlimit überschritten.";
            }
            if (process.exitValue() != 0) {
                return "brew install whisper-cpp fehlgeschlagen (Exit " + process.exitValue() + ").\n"
                        + abbreviate(output.toString(), 500);
            }
            if (resolveExecutable() == null) {
                return "whisper-cpp installiert, aber whisper-cli nicht im PATH gefunden. "
                        + "Terminal neu starten oder dictation.local_whisper_command setzen.";
            }
            out.accept("whisper-cli gefunden: " + resolveExecutable());
            return null;
        } catch (Exception e) {
            logger.warn("brew install whisper-cpp fehlgeschlagen", e);
            return "brew install fehlgeschlagen: " + e.getMessage();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * Lädt das offizielle Windows-x64-ZIP und entpackt es nach {@code whisper/} im App-Home.
     *
     * @return {@code null} bei Erfolg, sonst Fehlermeldung
     */
    public static String installWhisperCppWindows(Consumer<String> log) {
        Consumer<String> out = log != null ? log : msg -> {};
        if (!isWindows()) {
            return "Windows-ZIP-Installation ist nur unter Windows verfügbar.";
        }
        File targetDir = new File(ApplicationPaths.getApplicationHomeDirectory(), WHISPER_DIR);
        try {
            Files.createDirectories(targetDir.toPath());
            Path zipPath = targetDir.toPath().resolve("whisper-bin-x64.zip");
            out.accept("Lade Windows-Binary: " + WINDOWS_BIN_URL);
            out.accept("Zielordner: " + targetDir.getAbsolutePath());
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(WINDOWS_BIN_URL))
                    .timeout(Duration.ofMinutes(30))
                    .GET()
                    .build();
            Path partial = zipPath.resolveSibling(zipPath.getFileName() + ".partial");
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(partial));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                Files.deleteIfExists(partial);
                return "Windows-Binary-Download fehlgeschlagen (HTTP " + response.statusCode() + ").";
            }
            long size = Files.size(partial);
            if (size < 100_000) {
                Files.deleteIfExists(partial);
                return "Windows-Binary-Download verdächtig klein (" + size + " Bytes).";
            }
            try {
                Files.move(partial, zipPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception moveEx) {
                Files.move(partial, zipPath, StandardCopyOption.REPLACE_EXISTING);
            }
            out.accept("ZIP gespeichert (" + (size / 1_000_000) + " MB), entpacke…");
            extractZip(zipPath, targetDir.toPath(), out);
            try {
                Files.deleteIfExists(zipPath);
            } catch (Exception ignored) {
            }
            String exe = resolveExecutable();
            if (exe == null) {
                return "ZIP entpackt, aber whisper-cli.exe nicht gefunden unter " + targetDir.getAbsolutePath()
                        + ". Bitte dictation.local_whisper_command setzen.";
            }
            out.accept("whisper-cli gefunden: " + exe);
            out.accept("Hinweis: Bei Startfehlern ggf. „Microsoft Visual C++ Redistributable“ installieren.");
            return null;
        } catch (Exception e) {
            logger.warn("Windows Whisper-Installation fehlgeschlagen", e);
            return "Windows Whisper-Installation fehlgeschlagen: " + e.getMessage();
        }
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
                    if (log != null) {
                        log.accept("  " + entry.getName());
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Lädt das Standard-Modell ggml-base.bin nach {@link #defaultModelTargetPath()}.
     *
     * @return {@code null} bei Erfolg, sonst Fehlermeldung
     */
    public static String downloadDefaultModel(Consumer<String> log) {
        Consumer<String> out = log != null ? log : msg -> {};
        Path target = defaultModelTargetPath();
        try {
            if (Files.isRegularFile(target) && Files.size(target) > 1_000_000) {
                out.accept("Modell bereits vorhanden: " + target);
                return null;
            }
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName().toString() + ".partial");
            out.accept("Lade Modell: " + DEFAULT_MODEL_URL);
            out.accept("Ziel: " + target);
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(DEFAULT_MODEL_URL))
                    .timeout(Duration.ofMinutes(30))
                    .GET()
                    .build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(temp));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                Files.deleteIfExists(temp);
                return "Modell-Download fehlgeschlagen (HTTP " + response.statusCode() + ").";
            }
            long size = Files.size(temp);
            if (size < 1_000_000) {
                Files.deleteIfExists(temp);
                return "Modell-Download verdächtig klein (" + size + " Bytes).";
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception moveEx) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            out.accept("Modell gespeichert (" + (size / 1_000_000) + " MB): " + target);
            return null;
        } catch (Exception e) {
            logger.warn("Whisper-Modell-Download fehlgeschlagen", e);
            return "Modell-Download fehlgeschlagen: " + e.getMessage();
        }
    }

    private static void enrichPath(ProcessBuilder pb) {
        String path = pb.environment().getOrDefault("PATH", "");
        StringBuilder enriched = new StringBuilder();
        for (String dir : new String[]{"/opt/homebrew/bin", "/usr/local/bin", "/usr/bin", "/bin"}) {
            if (!path.contains(dir)) {
                if (!enriched.isEmpty()) {
                    enriched.append(File.pathSeparator);
                }
                enriched.append(dir);
            }
        }
        if (!enriched.isEmpty()) {
            pb.environment().put("PATH", enriched + File.pathSeparator + path);
        }
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, Math.max(0, max - 1)) + "…";
    }

    public static String resolveExecutable() {
        String configured = ResourceManager.getParameter("dictation.local_whisper_command", "").trim();
        if (!configured.isEmpty()) {
            File file = new File(configured);
            if (isUsableBinary(file)) {
                return file.getAbsolutePath();
            }
            if (isRunnableCommand(configured)) {
                return configured;
            }
            logger.warn("Konfiguriertes whisper-cli nicht ausführbar: {}", configured);
        }

        boolean windows = isWindows();
        String[] names = windows
                ? new String[]{"whisper-cli.exe", "whisper.exe", "main.exe", "whisper-cpp.exe"}
                : new String[]{"whisper-cli", "whisper", "main", "whisper-cpp"};

        for (File whisperDir : whisperDirectories()) {
            for (String name : names) {
                File exe = new File(whisperDir, name);
                if (isUsableBinary(exe)) {
                    logger.info("whisper-cli gefunden: {}", exe.getAbsolutePath());
                    return exe.getAbsolutePath();
                }
                File binExe = new File(whisperDir, "bin" + File.separator + name);
                if (isUsableBinary(binExe)) {
                    logger.info("whisper-cli gefunden: {}", binExe.getAbsolutePath());
                    return binExe.getAbsolutePath();
                }
                File releaseExe = new File(whisperDir, "Release" + File.separator + name);
                if (isUsableBinary(releaseExe)) {
                    logger.info("whisper-cli gefunden: {}", releaseExe.getAbsolutePath());
                    return releaseExe.getAbsolutePath();
                }
            }
            File nested = findNamedFile(whisperDir, windows ? "whisper-cli.exe" : "whisper-cli", 4);
            if (nested == null && windows) {
                nested = findNamedFile(whisperDir, "main.exe", 4);
            }
            if (nested != null && isUsableBinary(nested)) {
                logger.info("whisper-cli gefunden: {}", nested.getAbsolutePath());
                return nested.getAbsolutePath();
            }
        }

        for (String name : names) {
            String found = findOnPath(name, windows);
            if (found != null) {
                logger.info("whisper-cli gefunden: {}", found);
                return found;
            }
        }
        logger.warn("whisper-cli nicht gefunden");
        return null;
    }

    /** Unter Windows oft false für canExecute() trotz gültiger .exe – daher isFile() genügt. */
    private static boolean isUsableBinary(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        if (isWindows()) {
            return true;
        }
        return file.canExecute();
    }

    private static File findNamedFile(File dir, String fileName, int maxDepth) {
        if (dir == null || !dir.isDirectory() || maxDepth < 0) {
            return null;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                File found = findNamedFile(child, fileName, maxDepth - 1);
                if (found != null) {
                    return found;
                }
            } else if (fileName.equalsIgnoreCase(child.getName())) {
                return child;
            }
        }
        return null;
    }

    public static Path resolveModelPath() {
        String configured = ResourceManager.getParameter("dictation.local_whisper_model", "").trim();
        if (!configured.isEmpty()) {
            Path direct = Path.of(configured);
            if (direct.isAbsolute() && Files.isRegularFile(direct)) {
                logger.info("Whisper-Modell (Parameter): {}", direct);
                return direct;
            }
            for (File base : baseDirectories()) {
                Path candidate = base.toPath().resolve(configured).normalize();
                if (Files.isRegularFile(candidate)) {
                    logger.info("Whisper-Modell (Parameter): {}", candidate);
                    return candidate;
                }
            }
            Path fallback = ApplicationPaths.getApplicationHomeDirectory().toPath().resolve(configured).normalize();
            logger.warn("Konfiguriertes Whisper-Modell nicht gefunden: {}", fallback);
            return fallback;
        }

        for (File whisperDir : whisperDirectories()) {
            File modelsDir = new File(whisperDir, "models");
            for (String fileName : MODEL_FILE_NAMES) {
                File model = new File(modelsDir, fileName);
                if (model.isFile()) {
                    logger.info("Whisper-Modell gefunden: {}", model.getAbsolutePath());
                    return model.toPath();
                }
            }
        }

        return new File(ApplicationPaths.getApplicationHomeDirectory(), WHISPER_DIR)
                .toPath().resolve("models/ggml-base.bin");
    }

    static String mapLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "de";
        }
        return language.trim().toLowerCase(Locale.ROOT);
    }

    static String buildSetupHint() {
        File appHome = ApplicationPaths.getApplicationHomeDirectory();
        String home = System.getProperty("user.home", "~");
        return """
                Lokales Whisper (offline, kein OpenAI-Key):

                1) whisper-cli installieren:
                   macOS: brew install whisper-cpp
                   Windows: in Manuskript „Diktat einrichten“ (lädt whisper-bin-x64.zip),
                            oder manuell von https://github.com/ggml-org/whisper.cpp/releases

                2) Modell ablegen (einer der Ordner):
                   %s/whisper/models/ggml-base.bin
                   %s/whisper/models/ggml-base.bin

                Modell laden (Beispiel):
                   curl -L -o whisper/models/ggml-base.bin \\
                     https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin

                Optional in Parametern:
                   dictation.local_whisper_command
                   dictation.local_whisper_model
                """.formatted(appHome.getAbsolutePath(), home);
    }

    private static List<File> baseDirectories() {
        Set<String> seen = new LinkedHashSet<>();
        List<File> bases = new ArrayList<>();
        addBase(bases, seen, ApplicationPaths.getApplicationHomeDirectory());
        addBase(bases, seen, new File(System.getProperty("user.dir", ".")));
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            addBase(bases, seen, new File(home));
        }
        return bases;
    }

    private static List<File> whisperDirectories() {
        List<File> dirs = new ArrayList<>();
        for (File base : baseDirectories()) {
            dirs.add(new File(base, WHISPER_DIR));
        }
        return dirs;
    }

    private static void addBase(List<File> bases, Set<String> seen, File dir) {
        if (dir == null) {
            return;
        }
        String abs = dir.getAbsolutePath();
        if (seen.add(abs)) {
            bases.add(dir);
        }
    }

    private static List<String> pathDirectories() {
        Set<String> dirs = new LinkedHashSet<>();
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null && !pathEnv.isBlank()) {
            for (String part : pathEnv.split(File.pathSeparator)) {
                if (!part.isBlank()) {
                    dirs.add(part);
                }
            }
        }
        if (isMac()) {
            dirs.add("/opt/homebrew/bin");
            dirs.add("/usr/local/bin");
        }
        return new ArrayList<>(dirs);
    }

    private static String findOnPath(String command, boolean windows) {
        for (String dir : pathDirectories()) {
            File exe = new File(dir, command);
            if (isUsableBinary(exe)) {
                return exe.getAbsolutePath();
            }
            if (windows) {
                File withExe = new File(dir, command.endsWith(".exe") ? command : command + ".exe");
                if (isUsableBinary(withExe)) {
                    return withExe.getAbsolutePath();
                }
            }
        }
        return null;
    }

    private static boolean isRunnableCommand(String command) {
        try {
            Process process = new ProcessBuilder(command, "--help")
                    .redirectErrorStream(true)
                    .start();
            process.waitFor();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    static List<String> buildCommand(String executable, Path model, Path audioFile, String language,
                                     Path outputPrefix, String initialPrompt) {
        List<String> cmd = new ArrayList<>();
        cmd.add(executable);
        cmd.add("-m");
        cmd.add(model.toAbsolutePath().toString());
        cmd.add("-f");
        cmd.add(audioFile.toAbsolutePath().toString());
        cmd.add("-l");
        cmd.add(mapLanguage(language));
        if (initialPrompt != null && !initialPrompt.isBlank()) {
            cmd.add("--prompt");
            cmd.add(initialPrompt.trim());
        }
        cmd.add("-otxt");
        cmd.add("-of");
        cmd.add(outputPrefix.toAbsolutePath().toString());
        cmd.add("-nt");
        // Unterdrückt Nicht-Sprache-Tokens (Musik/Outros). -nc gibt es in whisper-cli 1.9 nicht.
        cmd.add("-sns");
        cmd.add("-tp");
        cmd.add("0");
        cmd.add("-tpi");
        cmd.add("0");
        cmd.add("-nth");
        cmd.add("0.5");
        cmd.add("-et");
        cmd.add("2.2");
        return cmd;
    }

    static String readProcessOutput(Process process) throws Exception {
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
            Files.deleteIfExists(Path.of(path.toString() + ".txt"));
        } catch (Exception e) {
            logger.trace("Whisper-Tempdatei löschen: {}", e.getMessage());
        }
    }
}
