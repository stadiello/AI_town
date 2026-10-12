"""Deterministic world model, independent of any rendering or LLM provider."""
from __future__ import annotations

from dataclasses import dataclass, field
import math
import random
from typing import Callable

TERRAINS = ("grass", "grass", "grass", "forest", "forest", "water", "sand")


@dataclass
class Agent:
    id: int
    x: int
    y: int
    hunger: float = 0
    fatigue: float = 0
    health: float = 100
    age: int = 0
    inventory: int = 0
    last_action: str = "spawn"
    last_reason: str = ""


@dataclass
class Cell:
    terrain: str
    food: int = 0

    @property
    def walkable(self) -> bool:
        return self.terrain != "water"


class World:
    def __init__(self, seed: int = 42, size: int = 32, population: int = 10,
                 emit: Callable[[dict], None] | None = None):
        self.rng = random.Random(seed)
        self.seed = seed
        self.size = size
        self.time = 0
        self.next_id = 1
        self.emit = emit or (lambda event: None)
        self.cells = self._generate()
        self.agents: list[Agent] = []
        for _ in range(population):
            self.spawn()

    @property
    def hour(self) -> float:
        return (self.time / 60) % 24

    @property
    def day(self) -> int:
        return self.time // 1440 + 1

    def _generate(self) -> list[list[Cell]]:
        cells = []
        for y in range(self.size):
            row = []
            for x in range(self.size):
                river = abs(x - self.size * .49 - 3 * math.sin(y * .21)) < 1.15
                v = self.rng.random()
                terrain = "water" if river else ("forest" if v < .24 else "grass")
                row.append(Cell(terrain, self.rng.randint(1, 5) if terrain == "forest" else
                                (1 if terrain == "grass" and v > .85 else 0)))
            cells.append(row)
        return cells

    def event(self, kind: str, agent: Agent | None = None, **details) -> None:
        self.emit({"tick": self.time, "day": self.day, "hour": round(self.hour, 2),
                   "type": kind, "agent_id": agent.id if agent else None, **details})

    def spawn(self) -> Agent:
        positions = [(x, y) for y in range(self.size) for x in range(self.size)
                     if self.cells[y][x].walkable]
        x, y = self.rng.choice(positions)
        agent = Agent(self.next_id, x, y)
        self.next_id += 1
        self.agents.append(agent)
        self.event("birth", agent, position=[x, y])
        return agent

    def _adjacent(self, agent: Agent) -> list[tuple[int, int]]:
        return [(x, y) for x, y in ((agent.x + 1, agent.y), (agent.x - 1, agent.y),
                                    (agent.x, agent.y + 1), (agent.x, agent.y - 1))
                if 0 <= x < self.size and 0 <= y < self.size and
                self.cells[y][x].walkable]

    def act(self, agent: Agent) -> None:
        cell = self.cells[agent.y][agent.x]
        observation = {"position": [agent.x, agent.y], "hunger": round(agent.hunger, 1),
                       "fatigue": round(agent.fatigue, 1), "food_here": cell.food,
                       "inventory": agent.inventory, "hour": round(self.hour, 2)}
        # Explicit, auditable policy: later replace with an LLM policy provider.
        if agent.hunger > 35 and agent.inventory > 0:
            action, reason = "eat", "hungry and carrying food"
            agent.inventory -= 1
            agent.hunger = max(0, agent.hunger - 45)
        elif agent.fatigue > 65:
            action, reason = "sleep", "fatigue exceeds safe threshold"
            agent.fatigue = max(0, agent.fatigue - 45)
            agent.hunger = min(100, agent.hunger + 4)
        elif cell.food > 0:
            action, reason = "harvest", "food is available at current tile"
            cell.food -= 1
            agent.inventory += 1
        else:
            choices = self._adjacent(agent)
            if choices:
                agent.x, agent.y = self.rng.choice(choices)
                action, reason = "move", "exploring for resources"
                agent.fatigue = min(100, agent.fatigue + 1)
            else:
                action, reason = "wait", "no traversable adjacent tile"
        agent.last_action, agent.last_reason = action, reason
        self.event("decision", agent, observation=observation, action=action, reason=reason)
        self.event("action", agent, action=action, result={"position": [agent.x, agent.y],
                      "inventory": agent.inventory})

    def step(self) -> None:
        """One simulation tick equals ten in-world minutes."""
        self.time += 10
        for agent in list(self.agents):
            agent.age += 10
            agent.hunger = min(100, agent.hunger + 1.4)
            agent.fatigue = min(100, agent.fatigue + .8)
            if agent.hunger >= 100:
                agent.health -= 8
            if agent.fatigue >= 100:
                agent.health -= 3
            if agent.health <= 0:
                self.event("death", agent, cause="unmet_needs", age_minutes=agent.age)
                self.agents.remove(agent)
                self.spawn()
            else:
                self.act(agent)
        if self.time % 1440 == 0:
            for row in self.cells:
                for cell in row:
                    if cell.terrain == "forest" and cell.food < 5 and self.rng.random() < .3:
                        cell.food += 1
            self.event("new_day")
