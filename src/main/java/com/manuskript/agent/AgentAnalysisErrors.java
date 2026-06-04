package com.manuskript.agent;

import java.util.concurrent.CompletionException;

/**
 * Lesbare Fehlermeldungen für Agenten-Analysen (UI + Log).
 */
public final class AgentAnalysisErrors {

    private AgentAnalysisErrors() {
    }

    public static String format(Throwable throwable) {
        if (throwable == null) {
            return "Unbekannter Fehler";
        }
        Throwable root = unwrap(throwable);
        StringBuilder sb = new StringBuilder();
        sb.append(root.getClass().getSimpleName());
        String msg = root.getMessage();
        if (msg != null && !msg.isBlank()) {
            sb.append(": ").append(msg.trim());
        }
        if (root.getCause() != null && root.getCause() != root && root.getCause().getMessage() != null) {
            String causeMsg = root.getCause().getMessage().trim();
            if (!sb.toString().contains(causeMsg)) {
                sb.append(" (Ursache: ").append(causeMsg).append(')');
            }
        }
        return sb.toString();
    }

    public static Throwable unwrap(Throwable throwable) {
        Throwable t = throwable;
        while (t instanceof CompletionException && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}
