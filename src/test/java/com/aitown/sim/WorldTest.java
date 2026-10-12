package com.aitown.sim;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldTest {
    @Test void deterministicAcrossRuns() {
        World a = new World(16, 4, 42, event -> {});
        World b = new World(16, 4, 42, event -> {});
        for (int i = 0; i < 100; i++) { a.step(); b.step(); }
        for (int i = 0; i < a.agents.size(); i++) {
            assertEquals(a.agents.get(i).x, b.agents.get(i).x);
            assertEquals(a.agents.get(i).y, b.agents.get(i).y);
            assertEquals(a.agents.get(i).hunger, b.agents.get(i).hunger);
        }
    }
    @Test void deathCreatesNewAgent() {
        List<String> events = new ArrayList<>();
        World w = new World(8, 1, 1, events::add);
        int id = w.agents.get(0).id;
        w.agents.get(0).health = 1;
        w.agents.get(0).hunger = 100;
        w.step();
        assertEquals(1, w.agents.size());
        assertNotEquals(id, w.agents.get(0).id);
        assertTrue(events.stream().anyMatch(s -> s.contains("\"type\":\"death\"")));
    }
    @Test void walkable() {
        World w = new World(12, 5, 10, s -> {});
        for (int i = 0; i < 200; i++) {
            w.step();
            for (World.Agent a : w.agents) assertFalse(w.cells[a.y][a.x].water);
        }
    }
}
