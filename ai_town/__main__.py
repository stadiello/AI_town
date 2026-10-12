"""Application entry point."""
from __future__ import annotations
import argparse

from .storage import Archive
from .world import World


def main() -> None:
    parser = argparse.ArgumentParser(description="AI Town: persistent isometric agent world")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--size", type=int, default=32)
    parser.add_argument("--population", type=int, default=10)
    parser.add_argument("--data-dir", default="data")
    parser.add_argument("--headless", action="store_true")
    parser.add_argument("--ticks", type=int, default=1000)
    args = parser.parse_args()
    if args.size < 4 or args.population < 1 or args.ticks < 0:
        parser.error("size must be >= 4, population >= 1, ticks >= 0")
    archive = Archive(args.data_dir)
    try:
        world = World(seed=args.seed, size=args.size, population=args.population,
                      emit=archive.emit)
        if args.headless:
            for _ in range(args.ticks):
                world.step()
            print(f"Simulation completed: {world.day} days, "
                  f"{len(world.agents)} living agents, logs in {args.data_dir}")
        else:
            from .view import run
            run(world, archive)
    finally:
        archive.close()


if __name__ == "__main__":
    main()
