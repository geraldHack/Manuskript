package com.manuskript.mindmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MindmapGraphParserTest {

    @Test
    void parsesFullGraph() {
        String raw = """
                {"nodes":[{"id":"a","label":"Lyra","group":"character"}],
                 "edges":[{"from":"a","to":"b","label":"kennt"}]}
                """;
        MindmapModel model = MindmapGraphParser.parseFull(raw);
        assertEquals(1, model.nodes.size());
        assertEquals("Lyra", model.nodes.get(0).heading);
        assertEquals(1, model.edges.size());
    }

    @Test
    void mergeAddsOnlyNew() {
        MindmapModel existing = new MindmapModel();
        existing.nodes.add(new MindmapModel.MindmapNode("a", "Lyra", "character"));
        String raw = """
                {"addNodes":[{"id":"b","label":"Paleus","group":"character"}],
                 "addEdges":[{"from":"a","to":"b","label":"Partner"}]}
                """;
        MindmapModel merged = MindmapGraphParser.mergeUpdate(existing, raw);
        assertEquals(2, merged.nodes.size());
        assertEquals(1, merged.edges.size());
    }

    @Test
    void mergeSkipsDuplicateLabel() {
        MindmapModel existing = new MindmapModel();
        existing.nodes.add(new MindmapModel.MindmapNode("a", "Lyra", "character"));
        String raw = """
                {"addNodes":[{"id":"x","label":"Lyra","group":"character"}]}
                """;
        MindmapModel merged = MindmapGraphParser.mergeUpdate(existing, raw);
        assertEquals(1, merged.nodes.size());
    }

    @Test
    void parsesHeadingAndText() {
        String raw = """
                {"nodes":[{"id":"a","heading":"Lyra","text":"Kapitänin","group":"character"}],
                 "edges":[]}
                """;
        MindmapModel model = MindmapGraphParser.parseFull(raw);
        assertEquals("Lyra", model.nodes.get(0).heading);
        assertEquals("Kapitänin", model.nodes.get(0).text);
    }

    @Test
    void migratesLegacyLabelWithNewline() {
        String raw = """
                {"nodes":[{"id":"a","label":"Lyra\\nKapitänin","group":"character"}],
                 "edges":[]}
                """;
        MindmapModel model = MindmapGraphParser.parseFull(raw);
        assertEquals("Lyra", model.nodes.get(0).heading);
        assertEquals("Kapitänin", model.nodes.get(0).text);
    }

    @Test
    void parsesEdgeArrows() {
        String raw = """
                {"nodes":[],"edges":[
                  {"from":"a","to":"b","arrows":"both"},
                  {"from":"b","to":"c","arrows":"none"}
                ]}
                """;
        MindmapModel model = MindmapGraphParser.parseFull(raw);
        assertEquals(2, model.edges.size());
        assertEquals("both", model.edges.get(0).arrows);
        assertEquals("none", model.edges.get(1).arrows);
    }

    @Test
    void extractsJsonFromMarkdownBlock() {
        String raw = "Hier:\n```json\n{\"nodes\":[],\"edges\":[]}\n```";
        MindmapModel model = MindmapGraphParser.parseFull(raw);
        assertTrue(model.nodes.isEmpty());
    }
}
