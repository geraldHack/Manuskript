package com.manuskript.mammouth;

/**
 * Einstieg für das Fat-JAR.
 */
public final class MammouthMonitorLauncher {

    private MammouthMonitorLauncher() {
    }

    public static void main(String[] args) {
        MammouthMonitorApp.setLaunchArgs(args);
        MammouthMonitorApp.launch(MammouthMonitorApp.class, args);
    }
}
