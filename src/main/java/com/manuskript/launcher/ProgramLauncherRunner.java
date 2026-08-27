package com.manuskript.launcher;

import com.manuskript.ApplicationPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Startet ein externes Programm in einem eigenen Prozess.
 */
public final class ProgramLauncherRunner {

    private static final Logger logger = LoggerFactory.getLogger(ProgramLauncherRunner.class);

    private ProgramLauncherRunner() {
    }

    public static void start(ProgramLauncher launcher, String projectRoot, String configDir, String chapterFile)
            throws Exception {
        if (launcher == null) {
            throw new IllegalArgumentException("Kein Starter gewählt.");
        }
        String path = launcher.getPath() == null ? "" : launcher.getPath().trim();
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Kein Programmpfad angegeben.");
        }
        path = expand(path, projectRoot, configDir, chapterFile);
        File target = resolveLauncherFile(new File(path));

        List<String> extraArgs = splitArgs(expand(
                launcher.getArguments() == null ? "" : launcher.getArguments(),
                projectRoot, configDir, chapterFile));

        List<String> command;
        if (target.exists()) {
            command = buildCommand(target, extraArgs);
        } else if (path.contains("/") || path.contains("\\")) {
            throw new IllegalArgumentException("Datei nicht gefunden: " + target.getAbsolutePath());
        } else {
            command = new ArrayList<>();
            command.add(path);
            command.addAll(extraArgs);
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        File scriptDir = target.getParentFile();
        boolean script = path.toLowerCase(Locale.ROOT).endsWith(".sh")
                || path.toLowerCase(Locale.ROOT).endsWith(".bat")
                || path.toLowerCase(Locale.ROOT).endsWith(".cmd");
        File workDir = script && scriptDir != null && scriptDir.isDirectory()
                ? scriptDir
                : (projectRoot != null && !projectRoot.isBlank() ? new File(projectRoot) : null);
        if (workDir != null && workDir.isDirectory()) {
            builder.directory(workDir);
        }
        builder.redirectErrorStream(true);
        if (ApplicationPreferences.isPackagedApplication()) {
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        } else {
            builder.inheritIO();
        }
        logger.info("Starter {}: {}", launcher.displayLabel(), String.join(" ", command));
        builder.start();
    }

    static String expand(String template, String projectRoot, String configDir, String chapterFile) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        String out = template;
        out = out.replace("{projectRoot}", nullToEmpty(projectRoot));
        out = out.replace("{configDir}", nullToEmpty(configDir));
        out = out.replace("{chapterFile}", nullToEmpty(chapterFile));
        return out;
    }

    static List<String> splitArgs(String arguments) {
        List<String> parts = new ArrayList<>();
        if (arguments == null || arguments.isBlank()) {
            return parts;
        }
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < arguments.length(); i++) {
            char c = arguments.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    static File resolveLauncherFile(File target) {
        if (target == null) {
            return null;
        }
        String path = target.getAbsolutePath();
        String lower = path.toLowerCase(Locale.ROOT);
        if (isWindows() && lower.endsWith(".sh")) {
            File bat = new File(path.substring(0, path.length() - 3) + ".bat");
            if (bat.isFile()) {
                return bat;
            }
        }
        if (!isWindows() && lower.endsWith(".bat")) {
            File sh = new File(path.substring(0, path.length() - 4) + ".sh");
            if (sh.isFile()) {
                return sh;
            }
        }
        return target;
    }

    static List<String> buildCommand(File target, List<String> extraArgs) {
        String path = target.getAbsolutePath();
        String lower = path.toLowerCase(Locale.ROOT);
        List<String> command = new ArrayList<>();
        if (lower.endsWith(".jar")) {
            command.add(javaBinary());
            command.add("--add-modules");
            command.add("javafx.controls");
            command.add("-jar");
            command.add(path);
            command.addAll(extraArgs);
            return command;
        }
        if (lower.endsWith(".app") && isMac()) {
            command.add("open");
            command.add(path);
            if (!extraArgs.isEmpty()) {
                command.add("--args");
                command.addAll(extraArgs);
            }
            return command;
        }
        if (lower.endsWith(".sh") && !isWindows()) {
            command.add("/bin/bash");
            command.add(path);
            command.addAll(extraArgs);
            return command;
        }
        command.add(path);
        command.addAll(extraArgs);
        return command;
    }

    private static String javaBinary() {
        String home = System.getProperty("java.home");
        if (home == null || home.isBlank()) {
            return "java";
        }
        File bin = new File(new File(home, "bin"), isWindows() ? "java.exe" : "java");
        return bin.isFile() ? bin.getAbsolutePath() : "java";
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
