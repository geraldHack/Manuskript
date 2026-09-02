package com.manuskript.plugin;

import java.util.List;

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

    public static String nextPatch(String version) {
        return bump(version, 2);
    }

    public static String nextMinor(String version) {
        return bump(version, 1);
    }

    public static String nextMajor(String version) {
        return bump(version, 0);
    }

    public static List<String> successorVersions(String version) {
        java.util.LinkedHashSet<String> versions = new java.util.LinkedHashSet<>();
        String current = version == null ? "" : version.trim();
        if (current.isEmpty()) {
            return List.of();
        }
        String patch = nextPatch(current);
        versions.add(patch);
        versions.add(nextPatch(patch));
        versions.add(nextPatch(nextPatch(patch)));
        versions.add(nextMinor(current));
        versions.add(nextMajor(current));
        versions.remove(current);
        return List.copyOf(versions);
    }

    private static String bump(String version, int index) {
        int[] values = parts(version);
        if (values.length == 0) {
            return "1.0.0";
        }
        int[] next = new int[Math.max(3, values.length)];
        System.arraycopy(values, 0, next, 0, values.length);
        int at = Math.min(index, next.length - 1);
        next[at] = next[at] + 1;
        for (int i = at + 1; i < next.length; i++) {
            next[i] = 0;
        }
        StringBuilder out = new StringBuilder();
        int last = Math.max(2, values.length - 1);
        for (int i = 0; i <= last; i++) {
            if (i > 0) {
                out.append('.');
            }
            out.append(next[i]);
        }
        return out.toString();
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
