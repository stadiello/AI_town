"""Durable append-only world history and agent-accessible written knowledge."""
from __future__ import annotations
import json
from pathlib import Path
import sqlite3
import uuid


class Archive:
    def __init__(self, directory: str | Path):
        path = Path(directory)
        path.mkdir(parents=True, exist_ok=True)
        self.session = uuid.uuid4().hex
        self.log = (path / "events.jsonl").open("a", encoding="utf-8", buffering=1)
        self.db = sqlite3.connect(path / "library.sqlite3")
        self.db.execute("""CREATE TABLE IF NOT EXISTS writings (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session TEXT NOT NULL, tick INTEGER NOT NULL,
            agent_id INTEGER NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL
        )""")
        self.db.commit()

    def emit(self, event: dict) -> None:
        self.log.write(json.dumps({"session": self.session, **event}, ensure_ascii=False) + "\n")

    def write(self, tick: int, agent_id: int, title: str, body: str) -> None:
        self.db.execute(
            "INSERT INTO writings(session,tick,agent_id,title,body) VALUES (?,?,?,?,?)",
            (self.session, tick, agent_id, title, body))
        self.db.commit()
        self.emit({"tick": tick, "agent_id": agent_id, "type": "writing",
                   "title": title, "body": body})

    def read(self, query: str = "", limit: int = 20) -> list[tuple]:
        """Library interface for future agent policy tools."""
        return self.db.execute(
            "SELECT id,agent_id,title,body FROM writings WHERE title LIKE ? OR body LIKE ? "
            "ORDER BY id DESC LIMIT ?",
            (f"%{query}%", f"%{query}%", limit)).fetchall()

    def close(self) -> None:
        self.log.close()
        self.db.close()
