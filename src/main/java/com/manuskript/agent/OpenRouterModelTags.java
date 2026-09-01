package com.manuskript.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Kompakte Filter-Tags für OpenRouter-Modelle (nicht jedes API-Feld als Chip).
 */
public final class OpenRouterModelTags {

    /** Max. Anzahl Tag-Buttons in der UI — Rest über Textsuche. */
    public static final int MAX_UI_TAGS = 10;

    public static final String TAG_FREE = "free";
    public static final String TAG_VISION = "vision";
    public static final String TAG_TOOLS = "tools";
    public static final String TAG_REASONING = "reasoning";
    public static final String TAG_LONG_CONTEXT = "128k+";

    private OpenRouterModelTags() {
    }

    public static List<String> deriveTags(JsonObject model) {
        if (model == null) {
            return List.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        if (isFree(model)) {
            tags.add(TAG_FREE);
        }
        if (hasVision(model)) {
            tags.add(TAG_VISION);
        }
        if (hasTools(model)) {
            tags.add(TAG_TOOLS);
        }
        if (hasReasoning(model)) {
            tags.add(TAG_REASONING);
        }
        if (longContext(model)) {
            tags.add(TAG_LONG_CONTEXT);
        }
        return List.copyOf(tags);
    }

    public static String label(String tag) {
        if (tag == null) {
            return "";
        }
        return switch (tag) {
            case TAG_FREE -> "Free";
            case TAG_VISION -> "Vision";
            case TAG_TOOLS -> "Tools";
            case TAG_REASONING -> "Reasoning";
            case TAG_LONG_CONTEXT -> "128k+";
            default -> tag;
        };
    }

    /** Kurzer Hilfetext für Tag-Filter-Chips in der Modellauswahl. */
    public static String tooltip(String tag) {
        if (tag == null) {
            return "";
        }
        String combine = " Mehrere aktive Filter werden kombiniert (UND).";
        return switch (tag) {
            case TAG_FREE -> "Nur Modelle ohne API-Kosten (Eingabe- und Ausgabe-Preis 0)." + combine;
            case TAG_VISION -> "Modelle mit Bild-/Vision-Eingabe (Multimodal)." + combine;
            case TAG_TOOLS -> "Unterstützt Function Calling / Tools." + combine;
            case TAG_REASONING -> "Reasoning-Modelle (z. B. mit reasoning- oder include_reasoning-Parameter)." + combine;
            case TAG_LONG_CONTEXT -> "Kontextfenster ab 128.000 Tokens." + combine;
            default -> "Nach Tag „" + label(tag) + "“ filtern." + combine;
        };
    }

    private static boolean isFree(JsonObject model) {
        double prompt = pricingPerToken(model, "prompt", "input");
        double completion = pricingPerToken(model, "completion", "output");
        return prompt == 0.0 && completion == 0.0;
    }

    private static double pricingPerToken(JsonObject model, String... keys) {
        if (!model.has("pricing") || !model.get("pricing").isJsonObject()) {
            return Double.NaN;
        }
        JsonObject pricing = model.getAsJsonObject("pricing");
        for (String key : keys) {
            if (!pricing.has(key) || pricing.get(key).isJsonNull()) {
                continue;
            }
            try {
                JsonElement el = pricing.get(key);
                if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                    return Double.parseDouble(el.getAsString().replace(',', '.'));
                }
                return el.getAsDouble();
            } catch (Exception ignored) {
                // next key
            }
        }
        return Double.NaN;
    }

    private static boolean hasVision(JsonObject model) {
        if (!model.has("architecture") || !model.get("architecture").isJsonObject()) {
            return false;
        }
        JsonObject arch = model.getAsJsonObject("architecture");
        if (!arch.has("modality") || arch.get("modality").isJsonNull()) {
            return false;
        }
        String modality = arch.get("modality").getAsString().toLowerCase(Locale.ROOT);
        return modality.contains("image") || modality.contains("vision") || modality.contains("multimodal");
    }

    private static boolean hasTools(JsonObject model) {
        return supportedParametersContain(model, "tools");
    }

    private static boolean hasReasoning(JsonObject model) {
        if (model.has("reasoning") && !model.get("reasoning").isJsonNull()) {
            return true;
        }
        return supportedParametersContain(model, "reasoning")
                || supportedParametersContain(model, "include_reasoning");
    }

    private static boolean longContext(JsonObject model) {
        if (!model.has("context_length") || model.get("context_length").isJsonNull()) {
            return false;
        }
        try {
            return model.get("context_length").getAsLong() >= 128_000L;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean supportedParametersContain(JsonObject model, String needle) {
        if (!model.has("supported_parameters") || !model.get("supported_parameters").isJsonArray()) {
            return false;
        }
        JsonArray arr = model.getAsJsonArray("supported_parameters");
        for (JsonElement el : arr) {
            if (el != null && el.isJsonPrimitive() && needle.equalsIgnoreCase(el.getAsString())) {
                return true;
            }
        }
        return false;
    }

    /** Häufigste Tags zuerst, höchstens {@link #MAX_UI_TAGS}. */
    public static List<String> pickUiTags(List<ModelOption> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        int[] counts = new int[5];
        String[] ids = {TAG_FREE, TAG_VISION, TAG_TOOLS, TAG_REASONING, TAG_LONG_CONTEXT};
        for (ModelOption option : options) {
            if (option == null || option.tags == null) {
                continue;
            }
            for (String tag : option.tags) {
                for (int i = 0; i < ids.length; i++) {
                    if (ids[i].equals(tag)) {
                        counts[i]++;
                    }
                }
            }
        }
        List<String> ranked = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            if (counts[i] > 0) {
                ranked.add(ids[i]);
            }
        }
        if (ranked.size() <= MAX_UI_TAGS) {
            return ranked;
        }
        return ranked.subList(0, MAX_UI_TAGS);
    }
}
