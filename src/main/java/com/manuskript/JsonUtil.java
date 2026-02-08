package com.manuskript;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

/** Einfache JSON-Hilfe für Map &lt;String, Object&gt; (z. B. ComfyUI-API). */
public final class JsonUtil {

    private static final Gson GSON = new Gson();
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private JsonUtil() {}

    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    /** JSON formatiert (mehrzeilig, einrückt) – für Log-Ausgabe. */
    public static String toJsonPretty(Object obj) {
        return GSON_PRETTY.toJson(obj);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> fromJson(String json) {
        return GSON.fromJson(json, MAP_TYPE);
    }
}
