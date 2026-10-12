package com.aitown.sim;

import java.util.*;
import java.util.function.Consumer;

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
        for (int i = 0; i < population; i++) spawn();
    }

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
    private void decide(Agent a) {
        Cell cell = cells[a.y][a.x];
        String observation = "{\"x\":" + a.x + ",\"y\":" + a.y +
                ",\"hunger\":" + a.hunger + ",\"fatigue\":" + a.fatigue +
                ",\"food\":" + cell.food + ",\"inventory\":" + a.inventory + "}";
        if (a.hunger > 35 && a.inventory > 0) {
            a.action = "eat"; a.reason = "hungry and carrying food";
            a.inventory--; a.hunger = Math.max(0, a.hunger - 45);
        } else if (a.fatigue > 65) {
            a.action = "sleep"; a.reason = "fatigue threshold";
            a.fatigue = Math.max(0, a.fatigue - 45);
            a.hunger = Math.min(100, a.hunger + 4);
        } else if (cell.food > 0) {
            a.action = "harvest"; a.reason = "food available";
            a.inventory++; cell.food--;
        } else {
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
