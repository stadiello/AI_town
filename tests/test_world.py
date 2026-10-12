import json
from pathlib import Path
import tempfile
import unittest

from ai_town.storage import Archive
from ai_town.world import World


class WorldTests(unittest.TestCase):
    def test_same_seed_same_world(self):
        a = World(seed=7, size=12, population=3)
        b = World(seed=7, size=12, population=3)
        for _ in range(80):
            a.step()
            b.step()
        self.assertEqual([(p.id, p.x, p.y, p.hunger, p.fatigue)
                          for p in a.agents],
                         [(p.id, p.x, p.y, p.hunger, p.fatigue)
                          for p in b.agents])

    def test_agents_stay_on_walkable_tiles(self):
        w = World(seed=15, size=16, population=5)
        for _ in range(400):
            w.step()
            for a in w.agents:
                self.assertTrue(w.cells[a.y][a.x].walkable)

    def test_death_keeps_population_and_history(self):
        with tempfile.TemporaryDirectory() as tmp:
            archive = Archive(tmp)
            world = World(seed=1, size=8, population=1, emit=archive.emit)
            original = world.agents[0]
            original.health = 1
            original.hunger = 100
            world.step()
            self.assertNotEqual(original.id, world.agents[0].id)
            archive.close()
            events = [json.loads(s) for s in
                      (Path(tmp) / "events.jsonl").read_text().splitlines()]
            self.assertTrue(any(e["type"] == "death" for e in events))
            self.assertTrue(any(e["type"] == "birth" and e["agent_id"] != original.id
                                for e in events))

    def test_library_survives_restarts(self):
        with tempfile.TemporaryDirectory() as tmp:
            archive = Archive(tmp)
            archive.write(50, 7, "Forest berries", "Red berries might be edible.")
            archive.close()
            reopened = Archive(tmp)
            self.assertEqual(reopened.read("berries")[0][1:], (
                7, "Forest berries", "Red berries might be edible."))
            reopened.close()


if __name__ == "__main__":
    unittest.main()
