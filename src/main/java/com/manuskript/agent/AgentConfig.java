package com.manuskript.agent;

import java.util.UUID;

/**
 * Konfiguration eines Agenten — global gespeichert, nicht projektspezifisch.
 */
public class AgentConfig {

    private String id;
    private String name;
    private String backend;
    private String systemPrompt;
    private String defaultPrompt;
    private String model;
    private double temperature;
    private int maxTokens;
    private double topP;
    private double repeatPenalty;

    public AgentConfig() {
        this.id = UUID.randomUUID().toString();
    }

    public AgentConfig(String name, String backend, String systemPrompt, String model,
                       double temperature, int maxTokens, double topP, double repeatPenalty) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.backend = backend;
        this.systemPrompt = systemPrompt;
        this.defaultPrompt = systemPrompt;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.topP = topP;
        this.repeatPenalty = repeatPenalty;
    }

    public void restoreDefaults() {
        this.systemPrompt = this.defaultPrompt;
    }

    // --- Getter/Setter ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBackend() { return backend; }
    public void setBackend(String backend) { this.backend = backend; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String getDefaultPrompt() { return defaultPrompt; }
    public void setDefaultPrompt(String defaultPrompt) { this.defaultPrompt = defaultPrompt; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public double getTopP() { return topP; }
    public void setTopP(double topP) { this.topP = topP; }

    public double getRepeatPenalty() { return repeatPenalty; }
    public void setRepeatPenalty(double repeatPenalty) { this.repeatPenalty = repeatPenalty; }
}
