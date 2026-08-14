package com.manuskript;

import com.manuskript.dictation.WhisperRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Homebrew-Erkennung und Installation (macOS).
 */
public final class HomebrewSupport {

    private static final Logger logger = LoggerFactory.getLogger(HomebrewSupport.class);

    private static final String INSTALL_SCRIPT_URL =
            "https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh";

    private HomebrewSupport() {
    }

    public static boolean isMacOS() {
        return WhisperRuntime.isMacOS()
                || System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    public static boolean isAvailable() {
        return WhisperRuntime.isHomebrewAvailable();
    }

    public static String brewPath() {
        return WhisperRuntime.resolveBrewExecutable();
    }

    /** Erste Zeile von {@code brew --version}, oder {@code null}. */
    public static String versionLine() {
        String brew = brewPath();
        if (brew == null) {
            return null;
        }
        try {
            Process process = new ProcessBuilder(brew, "--version")
                    .redirectErrorStream(true)
                    .start();
            String line;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                line = reader.readLine();
            }
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            return line != null ? line.trim() : "Homebrew";
        } catch (Exception e) {
            logger.debug("brew --version: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Öffnet Terminal.app mit dem offiziellen, interaktiven Homebrew-Installer.
     * Ohne {@code NONINTERACTIVE}, damit sudo nach dem Admin-Passwort fragen kann.
     *
     * @return {@code null} bei Erfolg, sonst Fehlermeldung
     */
    public static String openInstallInTerminal() {
        if (!isMacOS()) {
            return "Homebrew-Installation ist nur unter macOS verfügbar.";
        }
        if (isAvailable()) {
            return "Homebrew ist bereits installiert (" + brewPath() + ").";
        }
        try {
            Path script = Files.createTempFile("manuskript-brew-install-", ".sh");
            String scriptBody = """
                    #!/bin/bash
                    set +e
                    clear
                    echo "=============================================="
                    echo " Homebrew Installation (Manuskript Setup)"
                    echo "=============================================="
                    echo ""
                    echo "Der Installer braucht oft Administratorrechte."
                    echo "Wenn 'Password:' erscheint: macOS-Admin-Passwort"
                    echo "eingeben und RETURN. Die Zeichen werden nicht"
                    echo "angezeigt — das ist normal (sudo)."
                    echo ""
                    echo "Bei der Rückfrage des Installers mit RETURN bestätigen."
                    echo ""
                    /bin/bash -c "$(curl -fsSL %s)"
                    status=$?
                    echo ""
                    if [ $status -eq 0 ]; then
                      if [ -x /opt/homebrew/bin/brew ]; then
                        grep -q 'brew shellenv' ~/.zprofile 2>/dev/null \\
                          || echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
                        eval "$(/opt/homebrew/bin/brew shellenv)"
                      elif [ -x /usr/local/bin/brew ]; then
                        grep -q 'brew shellenv' ~/.zprofile 2>/dev/null \\
                          || echo 'eval "$(/usr/local/bin/brew shellenv)"' >> ~/.zprofile
                        eval "$(/usr/local/bin/brew shellenv)"
                      fi
                      echo "Homebrew fertig. Zurück zu Manuskript → Setup → Erneut prüfen."
                    else
                      echo "Installation fehlgeschlagen (Exit $status)."
                      echo "Manuell: https://brew.sh"
                    fi
                    echo ""
                    exec "$SHELL" -l
                    """.formatted(INSTALL_SCRIPT_URL);
            Files.writeString(script, scriptBody, StandardCharsets.UTF_8);
            script.toFile().setExecutable(true);

            ProcessBuilder pb = new ProcessBuilder(
                    "osascript",
                    "-e", "tell application \"Terminal\" to activate",
                    "-e", "tell application \"Terminal\" to do script "
                            + appleScriptQuoted("bash " + script.toAbsolutePath()));
            Process process = pb.start();
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Terminal-Start: Zeitlimit überschritten.";
            }
            if (process.exitValue() != 0) {
                return "Terminal konnte nicht gestartet werden (Exit " + process.exitValue() + "). "
                        + "Bitte manuell: https://brew.sh";
            }
            return null;
        } catch (Exception e) {
            logger.warn("Homebrew-Terminal-Install fehlgeschlagen", e);
            return "Terminal-Start fehlgeschlagen: " + e.getMessage()
                    + "\nBitte manuell unter https://brew.sh installieren.";
        }
    }

    /**
     * Versucht die Installation nicht-interaktiv im Hintergrund (kann an sudo scheitern).
     *
     * @return {@code null} bei Erfolg, sonst Fehlermeldung
     */
    public static String installNonInteractive(Consumer<String> log) {
        Consumer<String> out = log != null ? log : msg -> {};
        if (!isMacOS()) {
            return "Homebrew-Installation ist nur unter macOS verfügbar.";
        }
        if (isAvailable()) {
            out.accept("Homebrew bereits vorhanden: " + brewPath());
            return null;
        }
        out.accept("Starte offiziellen Homebrew-Installer (NONINTERACTIVE=1)…");
        out.accept("Hinweis: Admin-Passwort kann nötig sein – sonst „Im Terminal installieren“ nutzen.");
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "/bin/bash", "-c",
                    "NONINTERACTIVE=1 /bin/bash -c \"$(curl -fsSL " + INSTALL_SCRIPT_URL + ")\"");
            pb.redirectErrorStream(true);
            process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.accept(line);
                    if (output.length() < 12000) {
                        output.append(line).append('\n');
                    }
                }
            }
            boolean finished = process.waitFor(45, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                return "Homebrew-Installer: Zeitlimit überschritten.";
            }
            if (process.exitValue() != 0) {
                return "Homebrew-Installer fehlgeschlagen (Exit " + process.exitValue() + ").\n"
                        + "Bitte „Im Terminal installieren“ wählen oder https://brew.sh\n"
                        + abbreviate(output.toString(), 600);
            }
            if (!isAvailable()) {
                return "Installer beendet, aber brew nicht gefunden. "
                        + "Neues Terminal öffnen oder Manuskript neu starten, dann erneut prüfen.";
            }
            out.accept("Homebrew gefunden: " + brewPath());
            return null;
        } catch (Exception e) {
            logger.warn("Homebrew NONINTERACTIVE install fehlgeschlagen", e);
            return "Installation fehlgeschlagen: " + e.getMessage();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static String appleScriptQuoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
