package com.manuskript.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.application.Platform;

/**
 * Zählt laufende Agenten-Aufgaben und informiert Listener (Statuszeile + Busy-Balken im Editor).
 */
public class AgentActivityTracker {

    public record State(boolean active, String message) {
    }

    private int activeCount;
    private String latestMessage = "";
    private final List<Consumer<State>> listeners = new ArrayList<>();

    public synchronized void begin(String message) {
        activeCount++;
        if (message != null && !message.isBlank()) {
            latestMessage = message.trim();
        }
        fireUpdate();
    }

    public synchronized void end() {
        if (activeCount > 0) {
            activeCount--;
        }
        fireUpdate();
    }

    public synchronized State snapshot() {
        if (activeCount <= 0) {
            return new State(false, "");
        }
        String text = latestMessage.isEmpty() ? "Agent läuft…" : latestMessage;
        if (activeCount > 1) {
            text = text + " (" + activeCount + " aktiv)";
        }
        return new State(true, text);
    }

    public void addListener(Consumer<State> listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        Platform.runLater(() -> listener.accept(snapshot()));
    }

    private void fireUpdate() {
        State state = snapshot();
        Platform.runLater(() -> {
            for (Consumer<State> listener : listeners) {
                listener.accept(state);
            }
        });
    }
}
