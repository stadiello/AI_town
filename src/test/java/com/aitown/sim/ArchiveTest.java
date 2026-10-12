package com.aitown.sim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ArchiveTest {
    @TempDir Path temp;

    @Test void indexedEventsSurviveRestart() throws Exception {
        try (Archive a = new Archive(temp)) {
            a.event("{\"tick\":10,\"type\":\"decision\",\"agent_id\":7,\"data\":{\"reason\":\"berries\"}}");
            a.event("{\"tick\":20,\"type\":\"death\",\"agent_id\":8,\"data\":{}}");
            assertTrue(a.searchEvents("berries", "decision", 7, 30).contains("\"tick\":10"));
            assertEquals("[]", a.searchEvents("", "decision", 8, 30));
        }
        try (Archive a = new Archive(temp)) {
            assertTrue(a.searchEvents("", "death", 8, 10).contains("\"tick\":20"));
        }
    }

    @Test void libraryDoesNotExposePrivateObservations() throws Exception {
        try (Archive a = new Archive(temp)) {
            a.event("{\"tick\":10,\"type\":\"decision\",\"agent_id\":3,\"data\":{\"secret\":\"hidden\"}}");
            assertEquals(List.of(), a.read("hidden"));
            a.write(20, 3, "Survival", "Berries are food");
            assertEquals(1, a.read("Berries").size());
            assertTrue(a.searchEvents("", "writing", 3, 20).contains("Survival"));
        }
    }
}
