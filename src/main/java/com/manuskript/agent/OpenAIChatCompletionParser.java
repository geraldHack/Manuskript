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

    /**
     * Ein SSE-{@code data:}-Ereignis einer gestreamten Chat-Completion.
     *
     * @param content      neuer Text (kann leer sein)
     * @param finishReason {@code stop}/{@code length}/… oder {@code null}
     * @param done         {@code true} bei {@code [DONE]}
     * @param errorMessage Fehlermeldung aus dem JSON, sonst {@code null}
     */
    public record StreamEvent(String content, String finishReason, boolean done, String errorMessage) {
        public static StreamEvent empty() {
            return new StreamEvent("", null, false, null);
        }
    }

    /**
     * Parst den Wert nach {@code data:} (JSON oder {@code [DONE]}).
     */
    public static StreamEvent parseSseData(String data) {
        if (data == null) {
            return StreamEvent.empty();
        }
        String trimmed = data.trim();
        if (trimmed.isEmpty()) {
            return StreamEvent.empty();
        }
        if ("[DONE]".equalsIgnoreCase(trimmed)) {
            return new StreamEvent("", "stop", true, null);
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(trimmed);
        } catch (JsonSyntaxException e) {
            return StreamEvent.empty();
        }
        if (root == null || root.isJsonNull()) {
            return StreamEvent.empty();
        }
        if (root.isJsonObject()) {
            return streamEventFromObject(root.getAsJsonObject());
        }
        if (root.isJsonArray()) {
            StringBuilder merged = new StringBuilder();
            String finish = null;
            String error = null;
            for (JsonElement el : root.getAsJsonArray()) {
                if (!el.isJsonObject()) {
                    continue;
                }
                StreamEvent part = streamEventFromObject(el.getAsJsonObject());
                if (part.content() != null && !part.content().isEmpty()) {
                    merged.append(part.content());
                }
                if (part.finishReason() != null && !part.finishReason().isBlank()) {
                    finish = part.finishReason();
                }
                if (part.errorMessage() != null) {
                    error = part.errorMessage();
                }
            }
            return new StreamEvent(merged.toString(), finish, false, error);
        }
        return StreamEvent.empty();
    }

    private static StreamEvent streamEventFromObject(JsonObject o) {
        if (o.has("error")) {
            JsonElement err = o.get("error");
            String msg = err.isJsonObject() && err.getAsJsonObject().has("message")
                    ? err.getAsJsonObject().get("message").getAsString()
                    : err.toString();
            return new StreamEvent("", null, false, msg);
        }
        StringBuilder content = new StringBuilder();
        String finish = null;
        if (o.has("choices") && o.get("choices").isJsonArray()) {
            JsonArray choices = o.getAsJsonArray("choices");
            if (!choices.isEmpty() && choices.get(0).isJsonObject()) {
                JsonObject choice = choices.get(0).getAsJsonObject();
                if (choice.has("delta") && choice.get("delta").isJsonObject()) {
                    appendDeltaObject(content, choice.getAsJsonObject("delta"));
                }
                if (choice.has("message") && choice.get("message").isJsonObject()) {
                    JsonObject msg = choice.getAsJsonObject("message");
                    if (msg.has("content") && !msg.get("content").isJsonNull()) {
                        String text = OpenAIMessageContentExtractor.extractText(msg.get("content"));
                        if (text != null) {
                            content.append(text);
                        }
                    }
                }
                if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
                    finish = choice.get("finish_reason").getAsString();
                }
            }
        } else if (o.has("delta") && o.get("delta").isJsonObject()) {
            appendDeltaObject(content, o.getAsJsonObject("delta"));
        }
        return new StreamEvent(content.toString(), finish, false, null);
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
