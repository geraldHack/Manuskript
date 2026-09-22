package com.manuskript.discharge;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleJsonTest {

    @Test
    void roundTripObject() {
        String json = SimpleJson.stringify(Map.of("displayName", "Gerald", "n", 3));
        Map<String, Object> map = SimpleJson.parseObject(json);
        assertEquals("Gerald", map.get("displayName"));
        assertEquals(3L, map.get("n"));
    }

    @Test
    void parseApiStyle() {
        Map<String, Object> map = SimpleJson.parseObject(
                "{\"ok\":true,\"id\":\"Gerald.4711\",\"token\":\"abc\",\"friends\":[]}");
        assertTrue(Boolean.TRUE.equals(map.get("ok")));
        assertEquals("Gerald.4711", map.get("id"));
    }
}
