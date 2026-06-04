package com.manuskript.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Extrahiert Text aus OpenAI-kompatiblen {@code message.content}-Feldern
 * (String, Array mit type/text-Parts, verschachtelte Objekte — z. B. Kimi/Moonshot).
 */
public final class OpenAIMessageContentExtractor {

    private OpenAIMessageContentExtractor() {
    }

    public static String extractText(JsonElement contentElement) {
        if (contentElement == null || contentElement.isJsonNull()) {
            return null;
        }
        if (contentElement.isJsonPrimitive() && contentElement.getAsJsonPrimitive().isString()) {
            return contentElement.getAsString();
        }
        if (contentElement.isJsonArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonElement part : contentElement.getAsJsonArray()) {
                appendPartText(sb, part);
            }
            return sb.isEmpty() ? null : sb.toString();
        }
        if (contentElement.isJsonObject()) {
            JsonObject o = contentElement.getAsJsonObject();
            for (String key : new String[]{"text", "content", "value", "output", "message"}) {
                if (!o.has(key) || o.get(key).isJsonNull()) {
                    continue;
                }
                JsonElement inner = o.get(key);
                if (inner.isJsonPrimitive() && inner.getAsJsonPrimitive().isString()) {
                    return inner.getAsString();
                }
                String nested = extractText(inner);
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static void appendPartText(StringBuilder sb, JsonElement part) {
        if (part == null || part.isJsonNull()) {
            return;
        }
        if (part.isJsonPrimitive() && part.getAsJsonPrimitive().isString()) {
            appendSegment(sb, part.getAsString());
            return;
        }
        if (!part.isJsonObject()) {
            return;
        }
        JsonObject o = part.getAsJsonObject();
        String type = o.has("type") && o.get("type").isJsonPrimitive()
                ? o.get("type").getAsString() : "";
        if ("text".equalsIgnoreCase(type) || o.has("text")) {
            if (o.has("text") && !o.get("text").isJsonNull()) {
                appendSegment(sb, o.get("text").getAsString());
            }
            return;
        }
        if (o.has("content")) {
            String nested = extractText(o.get("content"));
            if (nested != null) {
                appendSegment(sb, nested);
            }
        }
    }

    private static void appendSegment(StringBuilder sb, String segment) {
        if (segment == null || segment.isEmpty()) {
            return;
        }
        if (!sb.isEmpty() && !sb.toString().endsWith("\n")) {
            sb.append('\n');
        }
        sb.append(segment);
    }

    /** Kurzbeschreibung für Logs, wenn content nicht als Text extrahiert werden kann. */
    public static String describe(JsonElement contentElement) {
        if (contentElement == null || contentElement.isJsonNull()) {
            return "null";
        }
        if (contentElement.isJsonPrimitive()) {
            return "primitive(" + contentElement.getAsJsonPrimitive().getClass().getSimpleName() + ")";
        }
        if (contentElement.isJsonArray()) {
            JsonArray arr = contentElement.getAsJsonArray();
            StringBuilder types = new StringBuilder();
            for (int i = 0; i < Math.min(arr.size(), 4); i++) {
                JsonElement el = arr.get(i);
                if (!types.isEmpty()) {
                    types.append(", ");
                }
                if (el.isJsonObject() && el.getAsJsonObject().has("type")) {
                    types.append(el.getAsJsonObject().get("type"));
                } else {
                    types.append(el.isJsonObject() ? "object" : el.isJsonArray() ? "array" : "other");
                }
            }
            return "array[" + arr.size() + "] parts=(" + types + ")";
        }
        if (contentElement.isJsonObject()) {
            return "object keys=" + contentElement.getAsJsonObject().keySet();
        }
        return contentElement.toString();
    }
}
