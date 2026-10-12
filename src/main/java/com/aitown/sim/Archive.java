package com.aitown.sim;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Observer event store; only writings are made available to agent library reads. */
public final class Archive implements AutoCloseable {
    private final BufferedWriter log;
    private final Connection db;
    private final String session = UUID.randomUUID().toString();
    private long sequence = 0;

    public Archive(Path directory) throws IOException {
        try {
            Files.createDirectories(directory);
            db = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("observatory.sqlite3").toAbsolutePath());
            try (Statement s = db.createStatement()) {
                s.execute("PRAGMA busy_timeout=5000");
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("CREATE TABLE IF NOT EXISTS events (id TEXT PRIMARY KEY, schema_version INTEGER NOT NULL, session TEXT NOT NULL, tick INTEGER NOT NULL, type TEXT NOT NULL, agent_id INTEGER, payload TEXT NOT NULL, caused_by TEXT NOT NULL DEFAULT '[]')");
                s.execute("CREATE INDEX IF NOT EXISTS idx_events_agent_tick ON events(agent_id,tick)");
                s.execute("CREATE INDEX IF NOT EXISTS idx_events_type_tick ON events(type,tick)");
                s.execute("CREATE TABLE IF NOT EXISTS writings (id INTEGER PRIMARY KEY AUTOINCREMENT, session TEXT NOT NULL, tick INTEGER NOT NULL, agent_id INTEGER NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL)");
            }
            log = Files.newBufferedWriter(directory.resolve("events.jsonl"), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (SQLException e) { throw new IOException(e); }
    }

    private static String field(String json, String name) {
        // Existing engine emits a compact, trusted JSON envelope.
        java.util.regex.Matcher match = java.util.regex.Pattern.compile(
            "\"" + name + "\"\\s*:\\s*(\"[^\"]*\"|-?\\d+|null)").matcher(json);
        return match.find() ? match.group(1) : null;
    }
    public synchronized void event(String json) {
        try {
            String tickRaw = field(json, "tick");
            long tick = tickRaw == null ? 0 : Long.parseLong(tickRaw);
            String typeRaw = field(json, "type");
            String type = typeRaw == null ? "unknown" : typeRaw.substring(1, typeRaw.length() - 1);
            String agentRaw = field(json, "agent_id");
            Integer agent = agentRaw == null || agentRaw.equals("null") ? null : Integer.parseInt(agentRaw);
            String id = session + ":" + (++sequence);
            try (PreparedStatement stmt = db.prepareStatement(
                    "INSERT INTO events(id,schema_version,session,tick,type,agent_id,payload,caused_by) VALUES (?,?,?,?,?,?,?,?)")) {
                stmt.setString(1, id); stmt.setInt(2, 1);
                stmt.setString(3, session); stmt.setLong(4, tick);
                stmt.setString(5, type);
                if (agent == null) stmt.setNull(6, Types.INTEGER); else stmt.setInt(6, agent);
                stmt.setString(7, json); stmt.setString(8, "[]");
                stmt.executeUpdate();
            }
            log.write("{\"id\":\"" + id + "\",\"schema_version\":1,\"session\":\"" + session +
                "\",\"tick\":" + tick + ",\"type\":\"" + escape(type) +
                "\",\"agent_id\":" + (agent == null ? "null" : agent) +
                ",\"caused_by\":[],\"payload\":" + json + "}");
            log.newLine(); log.flush();
        } catch (SQLException | IOException e) { throw new IllegalStateException("Event persistence failed", e); }
    }

    public synchronized void write(int tick, int agentId, String title, String body) throws IOException {
        try (PreparedStatement stmt = db.prepareStatement(
                "INSERT INTO writings(session,tick,agent_id,title,body) VALUES (?,?,?,?,?)")) {
            stmt.setString(1, session); stmt.setInt(2, tick); stmt.setInt(3, agentId);
            stmt.setString(4, title); stmt.setString(5, body); stmt.executeUpdate();
        } catch (SQLException e) { throw new IOException(e); }
        event("{\"tick\":" + tick + ",\"type\":\"writing\",\"agent_id\":" + agentId +
            ",\"data\":{\"title\":\"" + escape(title) + "\"}}");
    }

    /** Agents can query writings but not the internal decision/event stream. */
    public synchronized List<String> read(String query) throws IOException {
        List<String> result = new ArrayList<>();
        try (PreparedStatement stmt = db.prepareStatement(
                "SELECT agent_id,title,body FROM writings WHERE title LIKE ? OR body LIKE ? ORDER BY id DESC LIMIT 100")) {
            String pattern = "%" + query + "%";
            stmt.setString(1, pattern); stmt.setString(2, pattern);
            try (ResultSet rows = stmt.executeQuery()) {
                while (rows.next()) result.add(rows.getInt(1) + ": " + rows.getString(2) + "\n" + rows.getString(3));
            }
        } catch (SQLException e) { throw new IOException(e); }
        return result;
    }

    public synchronized String searchEvents(String text, String type, Integer agent, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT id,session,tick,type,agent_id,payload,caused_by FROM events WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (text != null && !text.isBlank()) { sql.append(" AND payload LIKE ?"); params.add("%" + text + "%"); }
        if (type != null && !type.isBlank()) { sql.append(" AND type = ?"); params.add(type); }
        if (agent != null) { sql.append(" AND agent_id = ?"); params.add(agent); }
        sql.append(" ORDER BY rowid DESC LIMIT ?");
        params.add(Math.max(1, Math.min(limit, 500)));
        StringBuilder out = new StringBuilder("[");
        try (PreparedStatement stmt = db.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) stmt.setObject(i + 1, params.get(i));
            try (ResultSet rows = stmt.executeQuery()) {
                while (rows.next()) {
                    if (out.length() > 1) out.append(',');
                    out.append("{\"id\":\"").append(escape(rows.getString(1)))
                        .append("\",\"session\":\"").append(escape(rows.getString(2)))
                        .append("\",\"tick\":").append(rows.getLong(3))
                        .append(",\"type\":\"").append(escape(rows.getString(4)))
                        .append("\",\"agent_id\":").append(rows.getObject(5) == null ? "null" : rows.getInt(5))
                        .append(",\"payload\":").append(rows.getString(6))
                        .append(",\"caused_by\":").append(rows.getString(7)).append("}");
                }
            }
        }
        return out.append(']').toString();
    }
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
    @Override public synchronized void close() throws IOException {
        try { log.close(); db.close(); } catch (SQLException e) { throw new IOException(e); }
    }
}
