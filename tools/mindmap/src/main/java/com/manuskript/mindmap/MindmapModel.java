package com.manuskript.mindmap;

import java.util.ArrayList;
import java.util.List;

/** Persistiert in {@code data/mindmap.json}. */
public final class MindmapModel {

    public int version = 2;
    public List<MindmapNode> nodes = new ArrayList<>();
    public List<MindmapEdge> edges = new ArrayList<>();

    public static final class MindmapNode {
        public String id;
        /** Einzeilige Überschrift (Anzeige im Knoten, größer). */
        public String heading;
        /** Optionaler Fließtext unter der Überschrift (kleiner). */
        public String text;
        /** Legacy-Feld — wird beim Laden nach {@link #heading}/{@link #text} migriert. */
        public String label;
        public String group;
        public Double x;
        public Double y;

        public MindmapNode() {
        }

        public MindmapNode(String id, String heading, String group) {
            this.id = id;
            this.heading = heading;
            this.group = group;
        }
    }

    public static final class MindmapEdge {
        public String from;
        public String to;
        public String label;
        /** Pfeilrichtung: {@code to} (A→B), {@code from} (B→A), {@code both}, {@code none}. */
        public String arrows;

        public MindmapEdge() {
        }

        public MindmapEdge(String from, String to, String label) {
            this.from = from;
            this.to = to;
            this.label = label;
        }
    }
}
