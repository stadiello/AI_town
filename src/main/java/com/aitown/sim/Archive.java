package com.aitown.sim;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
/** Per-session append-only JSONL log and shared writings library. */
public final class Archive implements AutoCloseable {
    private final BufferedWriter log;
    private final Path library;
    private final String session = UUID.randomUUID().toString();
    public Archive(Path directory) throws IOException {
        Files.createDirectories(directory);
        log = Files.newBufferedWriter(directory.resolve("events.jsonl"),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        library = directory.resolve("library.jsonl");
    }
    public synchronized void event(String json) {
        try {
            log.write("{\"session\":\"" + session + "\",\"event\":" + json + "}\n");
            log.flush();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public synchronized void write(int tick, int agentId, String title, String body) throws IOException {
        String record = "{\"session\":\"" + session + "\",\"tick\":" + tick +
            ",\"agent_id\":" + agentId + ",\"title\":\"" + escape(title) +
            "\",\"body\":\"" + escape(body) + "\"}";
        Files.writeString(library, record + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        event("{\"tick\":" + tick + ",\"type\":\"writing\",\"agent_id\":" + agentId + "}");
    }
    public List<String> read(String text) throws IOException {
        if (!Files.exists(library)) return List.of();
        return Files.readAllLines(library, StandardCharsets.UTF_8).stream()
                .filter(line -> line.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT)))
                .toList();
    }
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
    @Override public void close() throws IOException { log.close(); }
}
