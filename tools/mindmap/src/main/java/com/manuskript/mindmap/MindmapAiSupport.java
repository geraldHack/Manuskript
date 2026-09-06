package com.manuskript.mindmap;

public final class MindmapAiSupport {

    private MindmapAiSupport() {
    }

    public static String systemPromptInitial() {
        return """
                Du extrahierst Beziehungsgraphen für Roman-Planung (Mindmap).
                Antwort NUR als JSON (kein Markdown drumherum, kein Reasoning-Text):

                {"nodes":[{"id":"kurz_id","heading":"Anzeigename","text":"Kurznotiz optional","group":"character|place|plot|chapter|other"}],
                 "edges":[{"from":"id","to":"id","label":"Beziehung optional"}]}

                Regeln:
                - Nur Figuren, Orte, Handlungselemente und Kapitel, die im Kontext vorkommen.
                - IDs: kurz, eindeutig, lateinisch/snake_case (z.B. lyra, station_alpha).
                - group: character, place, plot, chapter oder other.
                - heading: einzeiliger Name/Titel; text: optional 1–2 Sätze Zusatzinfo (leerer String wenn nichts).
                - edges: sinnvolle Beziehungen (Freund, Feind, spielt in, führt zu, …).
                - label darf nicht verwendet werden — nur heading und text.
                - Lieber 8–25 Knoten als zu viele Einzelheiten.
                """;
    }

    public static String userPromptInitial(String context) {
        return """
                Lies den folgenden Projektkontext und baue eine erste Mindmap.

                """
                + context
                + """

                Antwort: nur das JSON-Objekt mit nodes und edges.
                """;
    }

    public static String systemPromptUpdate() {
        return """
                Du ergänzt eine bestehende Roman-Mindmap um NEUE Knoten und Kanten.
                Antwort NUR als JSON:

                {"addNodes":[{"id":"…","heading":"…","text":"…","group":"…"}],
                 "addEdges":[{"from":"id","to":"id","label":"…"}]}

                Regeln:
                - Nur NEUE Elemente, die noch nicht in der bestehenden Map sind (weder gleicher heading noch gleiche id).
                - Neue Kapitel, neue Figuren, neue Orte aus dem Kontext.
                - Bestehende Knoten nicht wiederholen.
                - Wenn nichts Neues: {"addNodes":[],"addEdges":[]}
                """;
    }

    public static String userPromptUpdate(String context, String existingCompactJson) {
        return """
                Bestehende Mindmap (id, heading, text, group):
                """
                + existingCompactJson
                + """

                Neuer/aktualisierter Projektkontext:
                """
                + context
                + """

                Antwort: nur JSON mit addNodes und addEdges (nur Ergänzungen).
                """;
    }

    /** Kleines JSON für Updates — weniger Tokens, schneller. */
    public static String compactGraphJson(MindmapModel model) {
        if (model == null || model.nodes == null) {
            return "{\"nodes\":[],\"edges\":[]}";
        }
        StringBuilder sb = new StringBuilder("{\"nodes\":[");
        for (int i = 0; i < model.nodes.size(); i++) {
            MindmapModel.MindmapNode n = model.nodes.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":\"").append(escape(n.id)).append("\",\"heading\":\"")
                    .append(escape(nodeHeading(n))).append("\",\"text\":\"")
                    .append(escape(n.text == null ? "" : n.text)).append("\",\"group\":\"")
                    .append(escape(n.group)).append("\"}");
        }
        sb.append("],\"edges\":[");
        if (model.edges != null) {
            for (int i = 0; i < model.edges.size(); i++) {
                MindmapModel.MindmapEdge e = model.edges.get(i);
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"from\":\"").append(escape(e.from)).append("\",\"to\":\"")
                        .append(escape(e.to)).append("\"}");
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String nodeHeading(MindmapModel.MindmapNode n) {
        if (n.heading != null && !n.heading.isBlank()) {
            return n.heading.trim();
        }
        if (n.label == null || n.label.isBlank()) {
            return "";
        }
        int nl = n.label.indexOf('\n');
        return nl >= 0 ? n.label.substring(0, nl).trim() : n.label.trim();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
