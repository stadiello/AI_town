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


## Experimental Ollama agents, conversations, and physical library

Start a local Ollama server and download a model compatible with your machine:

```bash
ollama pull qwen3:8b
ollama serve
# In a separate terminal:
gradle run --args="--llm --model qwen3:8b"
```

Alternative fast headless run: `gradle run --args="--headless --ticks 2000 --llm --model qwen3:8b"`. Endpoint override: `--ollama-url http://127.0.0.1:11434`.

The physical library is drawn as a brown-roofed building on the isometric map. Agents can READ or WRITE only while standing **on its tile**. Nearby TALK actions create indexed `conversation` observer events; book content goes into the shared writings table, never into the agents' private observer logs. LLM requests run on a capped background pool with six-second deadlines; unavailable Ollama uses heuristic fallbacks, so the renderer doesn't block.

**Prototype limitations:** navigation currently uses random walking and does not yet intentionally route agents toward the library. The LLM may choose valid actions but is not yet guaranteed to do so; read/write/talk occur only when valid spatial conditions are met. A conversation is a single utterance, not a two-way thread yet. LLM outputs are not reproducible across simulation runs. There are no LLM credentials in the repository. Structured reason summaries are logged, not hidden model chain-of-thought.
