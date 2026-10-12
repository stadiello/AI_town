package com.aitown.sim;

import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.io.IOException;

/** Pure, deterministic game state. One step = ten simulated minutes. */
public final class World {
    public static final class Cell {
        public final boolean water;
        public final boolean forest;
        public int food;
        Cell(boolean water, boolean forest, int food) {
            this.water = water; this.forest = forest; this.food = food;
        }
    }
    public static final class Agent {
        public final int id;
        public int x, y, ageMinutes, inventory;
        public double hunger, fatigue, health = 100;
        public String action = "spawn", reason = "";
        Agent(int id, int x, int y) { this.id = id; this.x = x; this.y = y; }
    }
    public final int size, seed;
    public final Cell[][] cells;
    public final List<Agent> agents = new ArrayList<>();
    public int minutes = 0;
    public final int libraryX, libraryY;
    private Archive archive;
    private OllamaPolicy policy;
    private final Map<Integer, CompletableFuture<OllamaPolicy.Decision>> pending = new HashMap<>();
    private final Map<Integer, OllamaPolicy.Decision> ready = new HashMap<>();
    private int nextId = 1;
    private final Random random;
    private final Consumer<String> events;

    public World(int size, int population, int seed, Consumer<String> events) {
        if (size < 4 || population < 1) throw new IllegalArgumentException("Invalid world dimensions");
        this.size = size; this.seed = seed; this.events = events;
        this.random = new Random(seed);
        this.cells = new Cell[size][size];
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                boolean river = Math.abs(x - size * 0.49 - 3 * Math.sin(y * .21)) < 1.1;
                double roll = random.nextDouble();
                boolean forest = !river && roll < .25;
                cells[y][x] = new Cell(river, forest, forest ? 1 + random.nextInt(5) :
                        (!river && roll > .88 ? 1 : 0));
            }
        int lx = -1, ly = -1;
        outer: for (int y = 0; y < size; y++) for (int x = 0; x < size; x++)
            if (!cells[y][x].water && Math.abs(x - size / 2) + Math.abs(y - size / 2) < size / 3) {
                lx = x; ly = y; break outer;
            }
        if (lx < 0) { for (int y = 0; y < size && lx < 0; y++) for (int x = 0; x < size; x++)
            if (!cells[y][x].water) { lx = x; ly = y; break; } }
        libraryX = lx; libraryY = ly;
        cells[ly][lx].food = 0;
        for (int i = 0; i < population; i++) spawn();
    }

    public void connect(Archive archive, OllamaPolicy policy) { this.archive = archive; this.policy = policy; }
    public boolean atLibrary(Agent a) { return a.x == libraryX && a.y == libraryY; }
    public int day() { return minutes / 1440 + 1; }
    public double hour() { return (minutes % 1440) / 60.0; }
    private void emit(String type, Agent agent, String payload) {
        events.accept("{\"tick\":" + minutes + ",\"type\":\"" + type +
                "\",\"agent_id\":" + (agent == null ? "null" : agent.id) +
                ",\"data\":" + payload + "}");
    }
    private void spawn() {
        int x, y;
        do { x = random.nextInt(size); y = random.nextInt(size); }
        while (cells[y][x].water);
        Agent a = new Agent(nextId++, x, y);
        agents.add(a);
        emit("birth", a, "{\"x\":" + x + ",\"y\":" + y + "}");
    }

    public void step() {
        minutes += 10;
        // Snapshot iteration allows death and replacement during the same step.
        for (Agent a : new ArrayList<>(agents)) {
            a.ageMinutes += 10;
            a.hunger = Math.min(100, a.hunger + 1.4);
            a.fatigue = Math.min(100, a.fatigue + .8);
            if (a.hunger >= 100) a.health -= 8;
            if (a.fatigue >= 100) a.health -= 3;
            if (a.health <= 0) {
                emit("death", a, "{\"cause\":\"unmet_needs\",\"age_minutes\":" + a.ageMinutes + "}");
                agents.remove(a);
                pending.remove(a.id);
                ready.remove(a.id);
                spawn();
                continue;
            }
            decide(a);
        }
        if (minutes % 1440 == 0) {
            for (Cell[] row : cells)
                for (Cell cell : row)
                    if (cell.forest && cell.food < 5 && random.nextDouble() < .3) cell.food++;
            emit("new_day", null, "{}");
        }
    }
    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
    private void move(Agent a) {
        int[][] shifts = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        List<int[]> possible = new ArrayList<>();
        for (int[] shift : shifts) {
            int x = a.x + shift[0], y = a.y + shift[1];
            if (x >= 0 && y >= 0 && x < size && y < size && !cells[y][x].water)
                possible.add(new int[]{x, y});
        }
        if (!possible.isEmpty()) {
            int[] dest = possible.get(random.nextInt(possible.size()));
            a.x = dest[0]; a.y = dest[1];
            a.fatigue = Math.min(100, a.fatigue + 1);
        }
    }
    private void decide(Agent a) {
        Cell cell = cells[a.y][a.x];
        // Completed inference is applied only on a future simulation tick.
        CompletableFuture<OllamaPolicy.Decision> future = pending.get(a.id);
        if (future != null && future.isDone()) {
            pending.remove(a.id);
            try { OllamaPolicy.Decision d = future.getNow(null);
                if (d != null) ready.put(a.id, d);
            } catch (Exception ignored) { }
        }
        if (policy != null && !pending.containsKey(a.id) && minutes % 60 == 0) {
            String context = "{\"id\":" + a.id + ",\"x\":" + a.x + ",\"y\":" + a.y +
                ",\"hunger\":" + a.hunger + ",\"fatigue\":" + a.fatigue +
                ",\"inventory\":" + a.inventory + ",\"foodHere\":" + cell.food +
                ",\"atLibrary\":" + atLibrary(a) + "}";
            pending.put(a.id, policy.request(context));
        }
        String observation = "{\"x\":" + a.x + ",\"y\":" + a.y +
                ",\"hunger\":" + a.hunger + ",\"fatigue\":" + a.fatigue +
                ",\"food\":" + cell.food + ",\"inventory\":" + a.inventory + "}";
        OllamaPolicy.Decision instruction = ready.remove(a.id);
        String proposed = instruction == null ? "" : instruction.action().toLowerCase(Locale.ROOT);
        boolean applied = false;
        if ("read".equals(proposed) && atLibrary(a) && archive != null) {
            try {
                List<String> books = archive.read("");
                String selected = books.isEmpty() ? "empty" : books.get(0);
                emit("library_read", a, "{\"excerpt\":\"" + escape(selected.substring(0, Math.min(180, selected.length()))) + "\"}");
                a.action = "read"; a.reason = "consult library"; applied = true;
            } catch (IOException ignored) { }
        } else if ("write".equals(proposed) && atLibrary(a) && archive != null) {
            try {
                String message = instruction.message();
                if (!message.isBlank()) {
                    archive.write(minutes, a.id, "Journal of agent " + a.id, message);
                    a.action = "write"; a.reason = "record experience"; applied = true;
                }
            } catch (IOException ignored) { }
        } else if ("talk".equals(proposed)) {
            for (Agent other : agents) {
                if (other.id != a.id && Math.abs(other.x - a.x) + Math.abs(other.y - a.y) <= 1) {
                    String message = instruction.message();
                    emit("conversation", a, "{\"to_agent_id\":" + other.id + ",\"message\":\"" +
                        escape(message) + "\"}");
                    a.action = "talk"; a.reason = "communicate with nearby agent"; applied = true;
                    break;
                }
            }
        } else if ("move".equals(proposed)) {
            move(a); a.action = "move"; a.reason = instruction.reason(); applied = true;
        } else if ("wait".equals(proposed)) {
            a.action = "wait"; a.reason = instruction.reason(); applied = true;
        }
        if (!applied && a.hunger > 35 && a.inventory > 0) {
            a.action = "eat"; a.reason = "hungry and carrying food";
            a.inventory--; a.hunger = Math.max(0, a.hunger - 45);
        } else if (!applied && a.fatigue > 65) {
            a.action = "sleep"; a.reason = "fatigue threshold";
            a.fatigue = Math.max(0, a.fatigue - 45);
            a.hunger = Math.min(100, a.hunger + 4);
        } else if (!applied && cell.food > 0) {
            a.action = "harvest"; a.reason = "food available";
            a.inventory++; cell.food--;
        } else if (!applied) {
            int[][] shifts = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            List<int[]> possible = new ArrayList<>();
            for (int[] shift : shifts) {
                int x = a.x + shift[0], y = a.y + shift[1];
                if (x >= 0 && y >= 0 && x < size && y < size && !cells[y][x].water)
                    possible.add(new int[] {x, y});
            }
            if (!possible.isEmpty()) {
                int[] destination = possible.get(random.nextInt(possible.size()));
                a.x = destination[0]; a.y = destination[1];
                a.fatigue = Math.min(100, a.fatigue + 1);
                a.action = "move"; a.reason = "explore for resources";
            } else {
                a.action = "wait"; a.reason = "blocked";
            }
        }
        emit("decision", a, "{\"observation\":" + observation +
                ",\"action\":\"" + a.action + "\",\"reason\":\"" + a.reason + "\"}");
        emit("action", a, "{\"action\":\"" + a.action + "\",\"x\":" +
                a.x + ",\"y\":" + a.y + ",\"inventory\":" + a.inventory + "}");
    }
}
