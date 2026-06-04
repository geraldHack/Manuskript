package com.manuskript.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Normalisiert OpenAI-kompatible Chat-Completion-Antworten (Wurzel-Objekt oder -Array, z. B. Kimi).
 */
public final class OpenAIChatCompletionParser {

    private OpenAIChatCompletionParser() {
    }

    public static JsonElement parseRoot(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        String trimmed = responseBody.trim();
        try {
            return JsonParser.parseString(trimmed);
        } catch (JsonSyntaxException e) {
            throw e;
        }
    }

    /**
     * Wandelt Wurzel-{@link JsonElement} in ein Objekt mit {@code choices}-Array um.
     */
    public static JsonObject toCompletionEnvelope(JsonElement root) {
        if (root == null || root.isJsonNull()) {
            return null;
        }
        if (root.isJsonObject()) {
            return normalizeEnvelope(root.getAsJsonObject());
        }
        if (root.isJsonArray()) {
            return normalizeArrayRoot(root.getAsJsonArray());
        }
        return null;
    }

    private static JsonObject normalizeEnvelope(JsonObject o) {
        if (o.has("choices") && o.get("choices").isJsonArray()) {
            return o;
        }
        if (o.has("data") && o.get("data").isJsonObject()) {
            JsonObject data = o.getAsJsonObject("data");
            if (data.has("choices")) {
                return data;
            }
        }
        if (o.has("message") && o.get("message").isJsonObject()) {
            return wrapChoiceMessage(o.getAsJsonObject("message"));
        }
        if (o.has("result") && o.get("result").isJsonObject()) {
            return normalizeEnvelope(o.getAsJsonObject("result"));
        }
        return o;
    }

    private static JsonObject normalizeArrayRoot(JsonArray arr) {
        if (arr.isEmpty()) {
            return null;
        }

        JsonObject lastEnvelope = null;
        StringBuilder mergedDelta = new StringBuilder();

        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject item = el.getAsJsonObject();

            if (item.has("choices") && item.get("choices").isJsonArray()) {
                lastEnvelope = item;
                appendChoicesDeltas(mergedDelta, item.getAsJsonArray("choices"));
                continue;
            }
            if (item.has("message") && item.get("message").isJsonObject()) {
                lastEnvelope = wrapChoiceMessage(item.getAsJsonObject("message"));
                continue;
            }
            if (item.has("delta") && item.get("delta").isJsonObject()) {
                appendDeltaObject(mergedDelta, item.getAsJsonObject("delta"));
                continue;
            }
            if (item.has("text") && item.get("text").isJsonPrimitive()) {
                mergedDelta.append(item.get("text").getAsString());
            }
        }

        if (lastEnvelope != null) {
            if (!mergedDelta.isEmpty()) {
                injectMergedContent(lastEnvelope, mergedDelta.toString());
            }
            return lastEnvelope;
        }

        JsonElement first = arr.get(0);
        if (first.isJsonObject()) {
            JsonObject fo = first.getAsJsonObject();
            if (fo.has("message") || fo.has("delta")) {
                JsonObject envelope = new JsonObject();
                envelope.add("choices", arr);
                return envelope;
            }
            JsonObject single = normalizeEnvelope(fo);
            if (single != null && single.has("choices")) {
                return single;
            }
        }

        return null;
    }

    private static void appendChoicesDeltas(StringBuilder merged, JsonArray choices) {
        for (JsonElement choiceEl : choices) {
            if (!choiceEl.isJsonObject()) {
                continue;
            }
            JsonObject choice = choiceEl.getAsJsonObject();
            if (choice.has("delta") && choice.get("delta").isJsonObject()) {
                appendDeltaObject(merged, choice.getAsJsonObject("delta"));
            }
            if (choice.has("message") && choice.get("message").isJsonObject()) {
                JsonObject msg = choice.getAsJsonObject("message");
                if (msg.has("content") && !msg.get("content").isJsonNull()) {
                    String text = OpenAIMessageContentExtractor.extractText(msg.get("content"));
                    if (text != null && !text.isBlank()) {
                        merged.append(text);
                    }
                }
            }
        }
    }

    private static void appendDeltaObject(StringBuilder merged, JsonObject delta) {
        if (delta.has("content") && !delta.get("content").isJsonNull()) {
            String text = OpenAIMessageContentExtractor.extractText(delta.get("content"));
            if (text != null) {
                merged.append(text);
            }
        }
        if (delta.has("text") && delta.get("text").isJsonPrimitive()) {
            merged.append(delta.get("text").getAsString());
        }
    }

    private static void injectMergedContent(JsonObject envelope, String merged) {
        if (!envelope.has("choices") || !envelope.get("choices").isJsonArray()) {
            return;
        }
        JsonArray choices = envelope.getAsJsonArray("choices");
        if (choices.isEmpty()) {
            return;
        }
        JsonObject choice = choices.get(choices.size() - 1).getAsJsonObject();
        JsonObject message = choice.has("message") && choice.get("message").isJsonObject()
                ? choice.getAsJsonObject("message")
                : new JsonObject();
        message.addProperty("content", merged);
        choice.add("message", message);
    }

    private static JsonObject wrapChoiceMessage(JsonObject message) {
        JsonObject choice = new JsonObject();
        choice.add("message", message);
        JsonArray choices = new JsonArray();
        choices.add(choice);
        JsonObject envelope = new JsonObject();
        envelope.add("choices", choices);
        return envelope;
    }
}
