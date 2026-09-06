package com.manuskript.publish;

/**
 * Ein Eintrag der Publish-Checkliste.
 */
public final class ChecklistItem {

    public enum Status {
        PASS, WARN, FAIL, INFO
    }

    private final String id;
    private final String label;
    private final Status status;
    private final String detail;

    public ChecklistItem(String id, String label, Status status, String detail) {
        this.id = id;
        this.label = label;
        this.status = status;
        this.detail = detail == null ? "" : detail;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public Status status() {
        return status;
    }

    public String detail() {
        return detail;
    }

    public String statusLabel() {
        return switch (status) {
            case PASS -> "OK";
            case WARN -> "Hinweis";
            case FAIL -> "Fehlt";
            case INFO -> "Info";
        };
    }
}
