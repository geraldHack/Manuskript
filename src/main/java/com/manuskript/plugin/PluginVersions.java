package com.manuskript.plugin;

/**
 * Vergleich gepunkteter Versionsnummern ({@code 2.1.70}).
 */
public final class PluginVersions {

    private PluginVersions() {
    }

    public static boolean meetsRequirement(String current, String requires) {
        if (requires == null || requires.isBlank()) {
            return true;
        }
        if (current == null || current.isBlank()) {
            return false;
        }
        return compare(current, requires) >= 0;
    }

    public static int compare(String left, String right) {
        int[] a = parts(left);
        int[] b = parts(right);
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) {
                return Integer.compare(av, bv);
            }
        }
        return 0;
    }

    private static int[] parts(String version) {
        String trimmed = version == null ? "" : version.trim();
        if (trimmed.isEmpty()) {
            return new int[0];
        }
        String[] bits = trimmed.split("[^0-9]+");
        int[] out = new int[bits.length];
        int count = 0;
        for (String bit : bits) {
            if (bit.isEmpty()) {
                continue;
            }
            try {
                out[count++] = Integer.parseInt(bit);
            } catch (NumberFormatException e) {
                out[count++] = 0;
            }
        }
        if (count == out.length) {
            return out;
        }
        int[] tight = new int[count];
        System.arraycopy(out, 0, tight, 0, count);
        return tight;
    }
}
