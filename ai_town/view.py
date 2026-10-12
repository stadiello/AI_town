"""Procedural isometric visualizer. No external sprites required."""
from __future__ import annotations
import math
import pygame

TW, TH = 48, 24
COLORS = {"grass": (79, 143, 91), "forest": (49, 115, 76), "water": (48, 116, 166)}
AGENT_COLORS = [(234, 194, 104), (236, 130, 117), (164, 187, 242),
                (231, 163, 214), (170, 220, 163)]


def run(world, archive) -> None:
    pygame.init()
    screen = pygame.display.set_mode((1200, 800), pygame.RESIZABLE)
    pygame.display.set_caption("AI Town | Isometric simulation")
    clock = pygame.time.Clock()
    font = pygame.font.SysFont("monospace", 16)
    small = pygame.font.SysFont("monospace", 13)
    cam_x, cam_y, zoom = 420.0, 55.0, 1.0
    speed, paused, selected = 1, False, None
    accumulator = 0.0

    def project(x, y):
        return (cam_x + (x - y) * TW * zoom / 2,
                cam_y + (x + y) * TH * zoom / 2)

    running = True
    try:
        while running:
            dt = min(clock.tick(60) / 1000, 0.2)
            for event in pygame.event.get():
                if event.type == pygame.QUIT:
                    running = False
                elif event.type == pygame.KEYDOWN:
                    if event.key == pygame.K_ESCAPE:
                        running = False
                    elif event.key == pygame.K_SPACE:
                        paused = not paused
                    elif event.key in (pygame.K_1, pygame.K_2, pygame.K_3, pygame.K_4):
                        speed = {pygame.K_1: 1, pygame.K_2: 2,
                                 pygame.K_3: 5, pygame.K_4: 10}[event.key]
                elif event.type == pygame.MOUSEWHEEL:
                    zoom = max(.4, min(2.5, zoom * (1.12 if event.y > 0 else .89)))
                elif event.type == pygame.MOUSEBUTTONDOWN and event.button == 1:
                    mx, my = event.pos
                    candidates = [(abs(project(a.x, a.y)[0] - mx) +
                                   abs(project(a.x, a.y)[1] - my) * 2, a.id)
                                  for a in world.agents]
                    if candidates:
                        dist, ident = min(candidates)
                        if dist < 45 * zoom:
                            selected = ident
            keys = pygame.key.get_pressed()
            move = 390 * dt
            cam_x += (keys[pygame.K_a] or keys[pygame.K_LEFT]) * move
            cam_x -= (keys[pygame.K_d] or keys[pygame.K_RIGHT]) * move
            cam_y += (keys[pygame.K_w] or keys[pygame.K_UP]) * move
            cam_y -= (keys[pygame.K_s] or keys[pygame.K_DOWN]) * move

            if not paused:
                accumulator += dt * speed
                while accumulator >= .35:
                    world.step()
                    accumulator -= .35

            sunlight = max(.18, .5 + .5 * math.cos((world.hour - 12) * math.pi / 12))
            screen.fill((16, 27, 43))
            for y, row in enumerate(world.cells):
                for x, cell in enumerate(row):
                    sx, sy = project(x, y)
                    w, h = TW * zoom, TH * zoom
                    base = COLORS[cell.terrain]
                    shade = (.55 + .45 * sunlight)
                    color = tuple(int(v * shade) for v in base)
                    diamond = [(sx, sy), (sx + w / 2, sy + h / 2),
                               (sx, sy + h), (sx - w / 2, sy + h / 2)]
                    pygame.draw.polygon(screen, color, diamond)
                    pygame.draw.polygon(screen, tuple(int(v * .72) for v in color), diamond, 1)
                    if cell.terrain == "forest":
                        pygame.draw.polygon(screen, (25, int(95 * shade), 58),
                                            [(sx, sy - h * .6), (sx - w * .20, sy + h * .35),
                                             (sx + w * .20, sy + h * .35)])
                        pygame.draw.rect(screen, (100, 72, 43),
                                         (sx - 2 * zoom, sy + h * .25, 4 * zoom, 7 * zoom))
                    elif cell.food:
                        pygame.draw.circle(screen, (215, 100, 75),
                                           (int(sx), int(sy + h * .5)), max(1, int(2 * zoom)))
            for agent in sorted(world.agents, key=lambda a: a.x + a.y):
                sx, sy = project(agent.x, agent.y)
                cy = sy + TH * zoom * .2
                pygame.draw.ellipse(screen, (20, 37, 40),
                                    (sx - 7 * zoom, cy + 5 * zoom, 14 * zoom, 5 * zoom))
                pygame.draw.circle(screen, AGENT_COLORS[agent.id % len(AGENT_COLORS)],
                                   (int(sx), int(cy - 7 * zoom)), max(3, int(6 * zoom)))
                pygame.draw.circle(screen, (240, 209, 171),
                                   (int(sx), int(cy - 15 * zoom)), max(2, int(4 * zoom)))
                if selected == agent.id:
                    pygame.draw.circle(screen, (255, 250, 154),
                                       (int(sx), int(cy - 8 * zoom)), max(8, int(12 * zoom)), 2)
            width = screen.get_width()
            pygame.draw.rect(screen, (21, 31, 45), (0, 0, width, 40))
            status = f"AI TOWN  |  Day {world.day}  {int(world.hour):02d}:{int(world.hour%1*60):02d}  |  Agents {len(world.agents)}  |  {'PAUSE' if paused else f'x{speed}'}"
            screen.blit(font.render(status, True, (233, 238, 235)), (15, 11))
            if selected:
                agent = next((a for a in world.agents if a.id == selected), None)
                if agent:
                    pygame.draw.rect(screen, (21, 31, 45), (width - 280, 53, 265, 205))
                    lines = [f"AGENT #{agent.id}", f"Position: {agent.x}, {agent.y}",
                             f"Health:  {agent.health:.0f}/100",
                             f"Hunger:  {agent.hunger:.0f}/100",
                             f"Fatigue: {agent.fatigue:.0f}/100",
                             f"Food:    {agent.inventory}",
                             f"Action:  {agent.last_action}"]
                    for n, line in enumerate(lines):
                        screen.blit(font.render(line, True, (227, 233, 222)),
                                    (width - 265, 64 + 25 * n))
            help_text = "WASD: pan   Wheel: zoom   Click: inspect   SPACE: pause   1-4: speed"
            screen.blit(small.render(help_text, True, (235, 237, 230)),
                        (15, screen.get_height() - 30))
            pygame.display.flip()
    finally:
        pygame.quit()
