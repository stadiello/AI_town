# AI Town

A persistent, observable isometric world for autonomous agents.

## MVP (v0.1)

- Procedural isometric 2D map rendered with **pygame-ce** (no external artwork required).
- Day/night cycle, camera pan and zoom, clickable agents, pause and time acceleration.
- Agents with hunger, fatigue, health and autonomous rule-based survival decisions.
- Death and replacement without erasing history.
- Append-only JSONL event log and SQLite library of agent journals.
- Deterministic simulation with a configurable random seed. The renderer is independent of world rules.

**This first version uses rule-based agents, not LLM inference.** The action-selection interface is designed to be replaced by a model later. Logs capture observations, structured decisions, actions and outcomes, not hidden model chain-of-thought.

## Run

Requires Python 3.11+.

```bash
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\\Scripts\\activate
pip install -r requirements.txt
python -m ai_town
```

Run headless (no graphics):

```bash
python -m ai_town --headless --ticks 2000 --seed 42
```

Run tests:

```bash
python -m unittest discover -s tests -v
```

## Controls

| Key | Action |
| --- | --- |
| WASD or arrow keys | Move camera |
| Mouse wheel | Zoom |
| Left click | Select agent |
| Space | Pause/resume |
| 1 / 2 / 3 / 4 | Speed x1 / x2 / x5 / x10 |
| Esc | Quit |

World events: `data/events.jsonl`. Agent-written journal entries: `data/library.sqlite3`. Both paths can be changed with `--data-dir`. Each launch creates a new world session with a unique ID, without deleting previous logs.

## Structure

- `ai_town/world.py`: deterministic domain simulation and agent policy.
- `ai_town/storage.py`: append-only event store and persisted shared library.
- `ai_town/view.py`: isometric pygame renderer.
- `ai_town/__main__.py`: CLI entrypoint.

## Next steps

Introduce LLM policy (Ollama structured output), agent conversations, readable books inside a physical library, richer map assets, persistence/snapshot restore, and richer crafting and resource rules. Avoid relying on rendering FPS as simulation time.
