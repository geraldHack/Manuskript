package com.manuskript;

import java.io.File;

/**
 * Auflösung von Programmverzeichnis und gebündeltem Demo-Ordner (jpackage-.app).
 */
public final class ApplicationPaths {

    private ApplicationPaths() {
    }

    /**
     * Verzeichnis der laufenden Anwendung (bei jpackage: {@code Contents/app}).
     */
    public static File getApplicationHomeDirectory() {
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && !appPath.isBlank()) {
            File appHome = new File(appPath);
            if (appHome.isDirectory()) {
                return appHome;
            }
        }

        try {
            String codeLocation = Launcher.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath();
            if (codeLocation != null && codeLocation.endsWith(".jar")) {
                File jarDir = new File(codeLocation).getParentFile();
                if (jarDir != null && jarDir.isDirectory()) {
                    return jarDir;
                }
            }
        } catch (Exception ignored) {
            // Fallback unten
        }

        return new File(System.getProperty("user.dir", "."));
    }

    /**
     * Demo-/Projektstamm {@code Manuskripte}: zuerst im App-Bundle, sonst im Arbeitsverzeichnis.
     */
    public static File resolveManuskripteDirectory() {
        File bundled = new File(getApplicationHomeDirectory(), "Manuskripte");
        if (bundled.isDirectory()) {
            return bundled;
        }
        File relative = new File("Manuskripte");
        if (relative.isDirectory()) {
            return relative;
        }
        return bundled;
    }
}
