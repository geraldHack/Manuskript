package com.manuskript;

/**
 * Startklasse für jpackage: Ruft Main.main() auf, ohne selbst Application zu erweitern.
 * jpackage erkennt sonst, dass die Main-Klasse von Application erbt, und versucht
 * einen direkten JavaFX-Start, der bei nicht-modularen Apps fehlschlägt.
 */
public class Launcher {
    public static void main(String[] args) {
        Main.main(args);
    }
}
