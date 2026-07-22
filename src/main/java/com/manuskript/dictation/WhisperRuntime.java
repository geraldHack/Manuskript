package com.manuskript.dictation;

import com.manuskript.ApplicationPaths;
import com.manuskript.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Auflösung von whisper-cli / whisper.cpp (lokal, ohne API-Key).
 */
final class WhisperRuntime {

    private static final Logger logger = LoggerFactory.getLogger(WhisperRuntime.class);
    private static final String WHISPER_DIR = "whisper";
    private static final String[] MODEL_FILE_NAMES = {
            "ggml-base.bin",
            "ggml-small.bin",
            "ggml-tiny.bin"
    };

    private WhisperRuntime() {
    }

    static String resolveExecutable() {
        String configured = ResourceManager.getParameter("dictation.local_whisper_command", "").trim();
        if (!configured.isEmpty()) {
            File file = new File(configured);
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
            if (isRunnableCommand(configured)) {
                return configured;
            }
            logger.warn("Konfiguriertes whisper-cli nicht ausführbar: {}", configured);
        }

        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String[] names = isWindows
                ? new String[]{"whisper-cli.exe", "whisper.exe", "main.exe", "whisper-cpp.exe"}
                : new String[]{"whisper-cli", "whisper", "main", "whisper-cpp"};

        for (File whisperDir : whisperDirectories()) {
            for (String name : names) {
                File exe = new File(whisperDir, name);
                if (exe.isFile() && exe.canExecute()) {
                    logger.info("whisper-cli gefunden: {}", exe.getAbsolutePath());
                    return exe.getAbsolutePath();
                }
                File binExe = new File(whisperDir, "bin" + File.separator + name);
                if (binExe.isFile() && binExe.canExecute()) {
                    logger.info("whisper-cli gefunden: {}", binExe.getAbsolutePath());
                    return binExe.getAbsolutePath();
                }
            }
        }

        for (String name : names) {
            String found = findOnPath(name, isWindows);
            if (found != null) {
                logger.info("whisper-cli gefunden: {}", found);
                return found;
            }
        }
        logger.warn("whisper-cli nicht gefunden");
        return null;
    }

    static Path resolveModelPath() {
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

                2) Modell ablegen (einer der Ordner):
                   %s/whisper/models/ggml-base.bin
                   %s/whisper/models/ggml-base.bin

                Modell laden:
                   mkdir -p whisper/models
                   curl -L -o whisper/models/ggml-base.bin \\
                     https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin

                Optional in Parametern:
                   dictation.local_whisper_command (z. B. /opt/homebrew/bin/whisper-cli)
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

    private static String findOnPath(String command, boolean isWindows) {
        for (String dir : pathDirectories()) {
            File exe = new File(dir, command);
            if (exe.isFile() && exe.canExecute()) {
                return exe.getAbsolutePath();
            }
            if (isWindows) {
                File withExe = new File(dir, command + ".exe");
                if (withExe.isFile() && withExe.canExecute()) {
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
