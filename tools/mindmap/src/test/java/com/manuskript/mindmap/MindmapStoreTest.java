package com.manuskript.mindmap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MindmapStoreTest {

    @TempDir
    Path temp;

    @Test
    void roundTripUmlauts() throws Exception {
        MindmapModel model = new MindmapModel();
        MindmapModel.MindmapNode node = new MindmapModel.MindmapNode("n1", "Größe & Müller", "character");
        node.text = "Straße, Köln — schön!";
        model.nodes.add(node);

        MindmapStore.save(temp, model);
        MindmapModel loaded = MindmapStore.load(temp);

        assertEquals("Größe & Müller", loaded.nodes.get(0).heading);
        assertEquals("Straße, Köln — schön!", loaded.nodes.get(0).text);
    }

    @Test
    void roundTripNodePositions() throws Exception {
        MindmapModel model = new MindmapModel();
        MindmapModel.MindmapNode node = new MindmapModel.MindmapNode("hero", "Lyra", "character");
        node.x = 120.5;
        node.y = -340.25;
        model.nodes.add(node);

        MindmapStore.save(temp, model);
        MindmapModel loaded = MindmapStore.load(temp);

        assertEquals(1, loaded.nodes.size());
        assertNotNull(loaded.nodes.get(0).x);
        assertNotNull(loaded.nodes.get(0).y);
        assertEquals(120.5, loaded.nodes.get(0).x, 0.001);
        assertEquals(-340.25, loaded.nodes.get(0).y, 0.001);
    }
}
