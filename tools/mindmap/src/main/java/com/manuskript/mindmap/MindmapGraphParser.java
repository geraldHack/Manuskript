package com.manuskript.mindmap;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MindmapGraphParser {

    private static final Gson GSON = new Gson();
    private static final Pattern JSON_BLOCK = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private MindmapGraphParser() {
    }

    public static MindmapModel parseFull(String raw) {
        JsonObject root = extractRoot(raw);
        MindmapModel model = new MindmapModel();
        if (root == null) {
            return model;
        }
        model.nodes = parseNodes(root.get("nodes"));
        model.edges = parseEdges(root.get("edges"));
        return model;
    }

    public static MindmapModel mergeUpdate(MindmapModel existing, String raw) {
        if (existing == null) {
            existing = new MindmapModel();
        }
        if (existing.nodes == null) {
            existing.nodes = new ArrayList<>();
        }
        if (existing.edges == null) {
            existing.edges = new ArrayList<>();
        }
        JsonObject root = extractRoot(raw);
        if (root == null) {
            return existing;
        }
        JsonArray addNodes = root.has("addNodes") && root.get("addNodes").isJsonArray()
                ? root.getAsJsonArray("addNodes")
                : root.has("nodes") && root.get("nodes").isJsonArray()
                ? root.getAsJsonArray("nodes") : null;
        JsonArray addEdges = root.has("addEdges") && root.get("addEdges").isJsonArray()
                ? root.getAsJsonArray("addEdges")
                : root.has("edges") && root.get("edges").isJsonArray()
                ? root.getAsJsonArray("edges") : null;

        if (addNodes != null) {
            for (JsonElement el : addNodes) {
                MindmapModel.MindmapNode node = parseNode(el);
                if (node != null && !duplicateNode(existing, node)) {
                    node.id = ensureUniqueId(existing, node.id, nodeHeading(node));
                    existing.nodes.add(node);
                }
            }
        }
        if (addEdges != null) {
            for (JsonElement el : addEdges) {
                MindmapModel.MindmapEdge edge = parseEdge(el);
                if (edge != null && edge.from != null && edge.to != null && !duplicateEdge(existing, edge)) {
                    existing.edges.add(edge);
                }
            }
        }
        return existing;
    }

    static JsonObject extractRoot(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        Matcher m = JSON_BLOCK.matcher(text);
        if (m.find()) {
            text = m.group(1).trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        try {
            JsonElement el = JsonParser.parseString(text);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static List<MindmapModel.MindmapNode> parseNodes(JsonElement el) {
        List<MindmapModel.MindmapNode> out = new ArrayList<>();
        if (el == null || !el.isJsonArray()) {
            return out;
        }
        int i = 0;
        for (JsonElement item : el.getAsJsonArray()) {
            MindmapModel.MindmapNode node = parseNode(item);
            if (node != null) {
                if (node.id == null || node.id.isBlank()) {
                    node.id = "n" + (++i);
                }
                normalizeNodeFields(node);
                if (nodeHeading(node).isBlank()) {
                    node.heading = node.id;
                }
                out.add(node);
            }
        }
        return out;
    }

    private static List<MindmapModel.MindmapEdge> parseEdges(JsonElement el) {
        List<MindmapModel.MindmapEdge> out = new ArrayList<>();
        if (el == null || !el.isJsonArray()) {
            return out;
        }
        for (JsonElement item : el.getAsJsonArray()) {
            MindmapModel.MindmapEdge edge = parseEdge(item);
            if (edge != null) {
                out.add(edge);
            }
        }
        return out;
    }

    private static MindmapModel.MindmapNode parseNode(JsonElement el) {
        if (el == null || !el.isJsonObject()) {
            return null;
        }
        JsonObject o = el.getAsJsonObject();
        MindmapModel.MindmapNode node = new MindmapModel.MindmapNode();
        node.id = text(o, "id");
        node.heading = text(o, "heading");
        node.text = text(o, "text");
        node.label = text(o, "label");
        node.group = normalizeGroup(text(o, "group"));
        if (o.has("x") && !o.get("x").isJsonNull()) {
            node.x = o.get("x").getAsDouble();
        }
        if (o.has("y") && !o.get("y").isJsonNull()) {
            node.y = o.get("y").getAsDouble();
        }
        normalizeNodeFields(node);
        if (nodeHeading(node).isBlank()) {
            return null;
        }
        return node;
    }

    private static MindmapModel.MindmapEdge parseEdge(JsonElement el) {
        if (el == null || !el.isJsonObject()) {
            return null;
        }
        JsonObject o = el.getAsJsonObject();
        String from = text(o, "from");
        String to = text(o, "to");
        if (from == null || to == null || from.isBlank() || to.isBlank()) {
            return null;
        }
        MindmapModel.MindmapEdge edge = new MindmapModel.MindmapEdge(from.trim(), to.trim(), text(o, "label"));
        edge.arrows = normalizeArrows(text(o, "arrows"));
        return edge;
    }

    private static String normalizeArrows(String arrows) {
        if (arrows == null || arrows.isBlank()) {
            return null;
        }
        String value = arrows.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "to", "from", "both", "none" -> value;
            default -> null;
        };
    }

    private static String text(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        try {
            return o.get(key).getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeGroup(String group) {
        if (group == null || group.isBlank()) {
            return "other";
        }
        String g = group.trim().toLowerCase(Locale.ROOT);
        return switch (g) {
            case "character", "figur", "char" -> "character";
            case "place", "ort", "location" -> "place";
            case "plot", "handlung", "theme" -> "plot";
            case "chapter", "kapitel" -> "chapter";
            default -> "other";
        };
    }

    private static void normalizeNodeFields(MindmapModel.MindmapNode node) {
        if (node == null) {
            return;
        }
        if ((node.heading == null || node.heading.isBlank()) && node.label != null && !node.label.isBlank()) {
            String legacy = node.label.trim();
            int nl = legacy.indexOf('\n');
            if (nl >= 0) {
                node.heading = legacy.substring(0, nl).trim();
                if (node.text == null || node.text.isBlank()) {
                    node.text = legacy.substring(nl + 1).trim();
                }
            } else {
                node.heading = legacy;
            }
        }
        if (node.text == null) {
            node.text = "";
        } else {
            node.text = node.text.trim();
        }
        if (node.heading != null) {
            node.heading = node.heading.trim();
        }
    }

    private static String nodeHeading(MindmapModel.MindmapNode node) {
        if (node == null || node.heading == null) {
            return "";
        }
        return node.heading.trim();
    }

    private static boolean duplicateNode(MindmapModel model, MindmapModel.MindmapNode candidate) {
        normalizeNodeFields(candidate);
        String heading = nodeHeading(candidate).toLowerCase(Locale.ROOT);
        for (MindmapModel.MindmapNode n : model.nodes) {
            if (n.id != null && candidate.id != null && n.id.equals(candidate.id)) {
                return true;
            }
            if (!nodeHeading(n).isBlank() && nodeHeading(n).equalsIgnoreCase(candidate.heading)) {
                return true;
            }
        }
        return heading.isEmpty();
    }

    private static boolean duplicateEdge(MindmapModel model, MindmapModel.MindmapEdge candidate) {
        for (MindmapModel.MindmapEdge e : model.edges) {
            if (sameEdge(e, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameEdge(MindmapModel.MindmapEdge a, MindmapModel.MindmapEdge b) {
        return a.from.equals(b.from) && a.to.equals(b.to)
                && nullSafe(a.label).equalsIgnoreCase(nullSafe(b.label));
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String ensureUniqueId(MindmapModel model, String preferred, String heading) {
        if (preferred != null && !preferred.isBlank()) {
            boolean taken = model.nodes.stream().anyMatch(n -> preferred.equals(n.id));
            if (!taken) {
                return preferred;
            }
        }
        String base = heading == null ? "node" : heading.replaceAll("[^a-zA-Z0-9äöüÄÖÜß]+", "_").toLowerCase(Locale.ROOT);
        if (base.isBlank()) {
            base = "node";
        }
        for (int n = 1; ; n++) {
            String candidate = n == 1 ? base : base + "_" + n;
            boolean taken = false;
            for (MindmapModel.MindmapNode node : model.nodes) {
                if (candidate.equals(node.id)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return candidate;
            }
        }
    }
}
