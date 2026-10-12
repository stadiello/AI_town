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