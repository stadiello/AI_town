package com.aitown.sim;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.*;

/**
 * Observer-only event store. Agent-accessible writings remain a separate API.
 * Every recorded event is also appended to JSONL for durable export.
 */
public final class Archive implements AutoCloseable {
    private static final Pattern FIELD = Pattern.compile("\\\"(tick|type|agent_id)\\\"\\s*:\\s*(\\\"(?:[^\\\"\\\\]|\\\\.)*\\\"|-?\\d+|null)");
    private final BufferedWriter log;
    private final Connection db;
    private final String session = UUID.randomUUID().toString();
    private final AtomicLong sequence = new AtomicLong();

    public Archive(Path directory) throws IOException {
        try {
            Files.createDirectories(directory);
            db = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("observatory.sqlite3").toAbsolutePath());
            try (Statement stmt = db.createStatement()) {
                stmt.execute("PRAGMA busy_timeout=5000");
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("CREATE TABLE IF NOT EXISTS events (id TEXT PRIMARY KEY, schema_version INTEGER NOT NULL, session TEXT NOT NULL, tick INTEGER NOT NULL, type TEXT NOT NULL, agent_id INTEGER, payload TEXT NOT NULL, caused_by TEXT NOT NULL DEFAULT '[]')");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_agent_tick ON events(agent_id,tick)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_type_tick ON events(type,tick)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_session_tick ON events(session,tick)");
                stmt.execute("CREATE TABLE IF NOT EXISTS writings (id INTEGER PRIMARY KEY AUTOINCREMENT, session TEXT NOT NULL, tick INTEGER NOT NULL, agent_id INTEGER NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL)");
            }
            log = Files.newBufferedWriter(directory.resolve("events.jsonl"), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (SQLException e) { throw new IOException("Could not initialize observatory database", e); }
    }

    /** Existing World event schema remains compatible; raw JSON stays in payload. */
    public synchronized void event(String json) {
        try {
            Map<String, String> fields = new HashMap<>();
            Matcher matcher = FIELD.matcher(json);
            while (matcher.find()) fields.put(matcher.group(1), matcher.group(2));
            long tick = Long.parseLong(fields.getOrDefault("tick", "0"));
            String type = fields.getOrDefault("type", "\\"unknown\\"").replace("\\"", "");
            String agentRaw = fields.getOrDefault("agent_id", "null");
            Integer agent = agentRaw.equals("null") ? null : Integer.valueOf(agentRaw);
            String id = session + ":" + sequence.incrementAndGet();
            String record = "{\\"id\\":\\"" + id + "\\",\\"schema_version\\":1,\\"session\\":\\"" + session +
                    "\\",\\"tick\\":" + tick + ",\\"type\\":\\"" + escape(type) +
                    "\\",\\"agent_id\\":" + agentRaw + ",\\"caused_by\\":[],\\"payload\\":" + json + "}";
            try (PreparedStatement statement = db.prepareStatement(
                    "INSERT INTO events(id,schema_version,session,tick,type,agent_id,payload,caused_by) VALUES (?,?,?,?,?,?,?,?)")) {
                statement.setString(1, id);
                statement.setInt(2, 1);
                statement.setString(3, session);
                statement.setLong(4, tick);
                statement.setString(5, type);
                if (agent == null) statement.setNull(6, Types.INTEGER); else statement.setInt(6, agent);
                statement.setString(7, json);
                statement.setString(8, "[]");
                statement.executeUpdate();
            }
            log.write(record);
            log.newLine();
            log.flush();
        } catch (SQLException | IOException ex) { throw new IllegalStateException("Cannot persist world event", ex); }
    }

    /** A library book is not an observer event: only this explicit API exposes it to agents. */
    public synchronized void write(int tick, int agentId, String title, String body) throws IOException {
        try (PreparedStatement stmt = db.prepareStatement(
                "INSERT INTO writings(session,tick,agent_id,title,body) VALUES (?,?,?,?,?)")) {
            stmt.setString(1, session);
            stmt.setInt(2, tick);
            stmt.setInt(3, agentId);
            stmt.setString(4, title);
            stmt.setString(5, body);
            stmt.executeUpdate();
        } catch (SQLException e) { throw new IOException(e); }
        event("{\\"tick\\":" + tick + ",\\"type\\":\\"writing\\",\\"agent_id\\":" + agentId +
                ",\\"data\\":{\\"title\\":\\"" + escape(title) + "\\"}}");
    }

    public synchronized List<String> read(String query) throws IOException {
        List<String> entries = new ArrayList<>();
        try (PreparedStatement stmt = db.prepareStatement(
                "SELECT agent_id,title,body FROM writings WHERE title LIKE ? OR body LIKE ? ORDER BY id DESC LIMIT 100")) {
            String term = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            stmt.setString(1, term); stmt.setString(2, term);
            // SQLite LIKE wildcard escaping (the backslash itself must be declared).
            try (ResultSet rows = stmt.executeQuery()) {
                while (rows.next()) entries.add(rows.getInt(1) + ": " + rows.getString(2) + "\\n" + rows.getString(3));
            }
        } catch (SQLException e) { throw new IOException(e); }
        return entries;
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
                    out.append("{\\"id\\":\\"").append(escape(rows.getString(1)))
                        .append("\\",\\"session\\":\\"").append(escape(rows.getString(2)))
                        .append("\\",\\"tick\\":").append(rows.getLong(3))
                        .append(",\\"type\\":\\"").append(escape(rows.getString(4)))
                        .append("\\",\\"agent_id\\":").append(rows.getObject(5) == null ? "null" : rows.getInt(5))
                        .append(",\\"payload\\":").append(rows.getString(6))
                        .append(",\\"caused_by\\":").append(rows.getString(7)).append("}");
                }
            }
        }
        return out.append(']').toString();
    }
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\\"", "\\\\"").replace("\\n", "\\n").replace("\\r", "\\r");
    }
    @Override public synchronized void close() throws IOException {
        try { log.close(); db.close(); } catch (SQLException e) { throw new IOException(e); }
    }
}
