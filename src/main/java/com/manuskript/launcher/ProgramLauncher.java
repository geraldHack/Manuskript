package com.manuskript.launcher;

/**
 * Ein vom Nutzer angelegter Programm-Starter (eigener Prozess, kein JAR in der JVM).
 */
public final class ProgramLauncher {

    private String id;
    private String label;
    private String path;
    private String arguments;

    public ProgramLauncher() {
    }

    public ProgramLauncher(String id, String label, String path, String arguments) {
        this.id = id;
        this.label = label;
        this.path = path;
        this.arguments = arguments;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    public String displayLabel() {
        if (label != null && !label.isBlank()) {
            return label.trim();
        }
        if (path != null && !path.isBlank()) {
            int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
        }
        return "Programm";
    }
}
