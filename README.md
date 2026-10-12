# AI Town — Java / LibGDX

Persistent isometric simulation for autonomous agents. This implementation uses **Java 17 + LibGDX** instead of Python/Pygame.

## Start

Prerequisites: JDK 17+, Gradle installed.

```bash
gradle run
gradle test
gradle run --args="--headless --ticks 2000 --seed 42"
```

## Controls

WASD/arrows pan · +/- zoom · click an agent · Space pause · keys 1–4 simulation speed.

## Architecture

- `sim/World.java`: seeded, renderer-independent survival simulation.
- `sim/Archive.java`: durable JSONL events and persistent shared written library.
- `TownGame.java`: 2D isometric GPU renderer with day/night tint and agent inspection.
- `Main.java`: desktop/headless startup.

The first release has **heuristic agents**, not LLMs. Logs record structured decisions, not hidden internal chain-of-thought. Procedural geometric tiles are placeholders for a future pixel-art texture atlas. World snapshots, books as in-world objects, LLM decisions, and interactions remain future work.

No Gradle wrapper included yet; the build and tests have **not been executed** in this environment.

## AI Observatory (experimental)

The observatory is a **local-only** browser UI, independent of the LibGDX game:

```bash
gradle run --args="--headless --ticks 2000 --seed 42"
gradle observatory
# Open http://localhost:8765
```

Events are stored both in `data/events.jsonl` (append-only export) and `data/observatory.sqlite3` (indexed queries). The UI supports text, event type and agent-ID filters, newest events first, and inspection of raw event payloads.

**Access boundary:** the observatory reads the observer-only event table. The agent-facing `Archive.read(query)` method reads only explicitly published `writings` from SQLite. Agents cannot read private events through that method.

Current scope: schema version 1, session-scoped event IDs, timestamps in simulation ticks; `caused_by` is currently always an empty array. No genuine causal provenance, LLM integration, historical migration of old JSONL files, world replay or snapshots yet. The server binds to loopback and is intentionally unauthenticated: do not expose it to the public internet.
