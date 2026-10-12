package com.aitown;

import com.aitown.sim.*;
import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import java.io.IOException;
import java.util.*;

/**
 * Presentation-only isometric renderer. Simulation ticks and visual motion are independent.
 * The view never modifies an agent's logical coordinates.
 */
public final class TownGame extends ApplicationAdapter {
    private static final float TILE_W = 58, TILE_H = 29, TICK_SECONDS = .35f;
    private static final Color INK = new Color(.91f, .95f, .98f, 1);
    private static final Color MUTED = new Color(.59f, .68f, .73f, 1);
    private static final Color PANEL = new Color(.045f, .085f, .12f, .96f);
    private static final Color[] AGENT_COLORS = {
        Color.valueOf("F2B45D"), Color.valueOf("60D5C7"), Color.valueOf("A7A2F6"),
        Color.valueOf("F48DAB"), Color.valueOf("81C5F4"), Color.valueOf("D0DD83")
    };
    private static final class Visual {
        float x, y, facing = 1, phase;
        Visual(float x, float y) { this.x = x; this.y = y; }
    }

    private final World world;
    private final Archive archive;
    private final OllamaPolicy policy;
    private final Map<Integer, Visual> visuals = new HashMap<>();
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private float camX, camY, zoom = 1.1f, accumulator, elapsed;
    private boolean paused, labels = true;
    private int speed = 1, selected = -1;
    private int screenW, screenH;

    public TownGame(World world, Archive archive, OllamaPolicy policy) {
        this.world = world; this.archive = archive; this.policy = policy;
    }

    @Override public void create() {
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(1.1f);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        for (World.Agent a : world.agents) visuals.put(a.id, new Visual(a.x, a.y));
    }

    @Override public void resize(int width, int height) {
        // Preserve camera offset on ordinary resizes.
        if (screenW == 0) {
            camX = width * .44f;
            camY = height * .16f;
        } else {
            camX += (width - screenW) * .5f;
            camY += (height - screenH) * .5f;
        }
        screenW = width; screenH = height;
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    private Vector2 iso(float x, float y) {
        return new Vector2(camX + (x - y) * TILE_W * zoom * .5f,
                camY + (x + y) * TILE_H * zoom * .5f);
    }

    private void diamond(float x, float y, float w, float h) {
        shapes.triangle(x, y, x + w * .5f, y + h * .5f, x, y + h);
        shapes.triangle(x, y, x, y + h, x - w * .5f, y + h * .5f);
    }

    private void panel(float x, float y, float w, float h) {
        shapes.setColor(PANEL);
        shapes.rect(x, y, w, h);
        shapes.setColor(.14f, .24f, .29f, 1);
        shapes.rect(x, y, w, 1);
    }

    private String name(World.Agent a) { return "Agent " + String.format(Locale.ROOT, "%02d", a.id); }
    private Color agentColor(int id) { return AGENT_COLORS[Math.floorMod(id - 1, AGENT_COLORS.length)]; }

    private void controls(float dt) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit();
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) paused = !paused;
        if (Gdx.input.isKeyJustPressed(Input.Keys.L)) labels = !labels;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) speed = 1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) speed = 2;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) speed = 5;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4)) speed = 10;
        float pan = 460 * dt;
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) camX += pan;
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) camX -= pan;
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) camY -= pan;
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) camY += pan;
        if (Gdx.input.isKeyJustPressed(Input.Keys.EQUALS) || Gdx.input.isKeyJustPressed(Input.Keys.PLUS))
            zoom = Math.min(2.3f, zoom * 1.12f);
        if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS))
            zoom = Math.max(.5f, zoom / 1.12f);
        if (Gdx.input.justTouched()) {
            float mx = Gdx.input.getX(), my = screenH - Gdx.input.getY();
            float nearest = 25 * zoom;
            int clicked = -1;
            for (World.Agent a : world.agents) {
                Visual v = visuals.get(a.id);
                if (v == null) continue;
                Vector2 p = iso(v.x, v.y);
                float distance = Vector2.dst(mx, my, p.x, p.y + 19 * zoom);
                if (distance < nearest) { nearest = distance; clicked = a.id; }
            }
            // Right-hand legend is clickable as well.
            if (mx >= screenW - 226 && my < screenH - 96 && my > screenH - 114 - world.agents.size() * 30) {
                int row = (int)((screenH - 114 - my) / 30);
                if (row >= 0 && row < world.agents.size()) clicked = world.agents.get(row).id;
            }
            selected = clicked;
        }
    }

    private void advance(float dt) {
        if (!paused) {
            accumulator += dt * speed;
            int safety = 0;
            while (accumulator >= TICK_SECONDS && safety++ < 30) {
                world.step();
                accumulator -= TICK_SECONDS;
            }
            if (safety > 30) accumulator = 0;
        }
        Set<Integer> alive = new HashSet<>();
        for (World.Agent a : world.agents) {
            alive.add(a.id);
            Visual v = visuals.computeIfAbsent(a.id, id -> new Visual(a.x, a.y));
            if (paused) continue;
            float dx = a.x - v.x, dy = a.y - v.y;
            // Frame-rate independent exponential smoothing, bounded to prevent visual lag at high speed.
            float follow = 1 - (float)Math.exp(-dt * (8 + 2 * speed));
            v.x += dx * follow;
            v.y += dy * follow;
            if (Math.abs(dx) + Math.abs(dy) > .02f) {
                if (Math.abs(dx - dy) > .01f) v.facing = Math.signum(dx - dy);
                v.phase += dt * 12 * Math.min(2.5f, speed);
            }
        }
        visuals.keySet().retainAll(alive);
        if (selected >= 0 && !alive.contains(selected)) selected = -1;
    }

    private void drawWorld(float light) {
        for (int y = 0; y < world.size; y++) for (int x = 0; x < world.size; x++) {
            World.Cell cell = world.cells[y][x];
            Vector2 p = iso(x, y);
            float w = TILE_W * zoom, h = TILE_H * zoom;
            if (p.x < -w || p.x > screenW + w || p.y < -h || p.y > screenH + h) continue;
            // Subtle alternate grass tones break up the old flat tile grid.
            float texture = ((x * 17 + y * 31) % 7) * .008f;
            if (cell.water) shapes.setColor((.13f + texture) * light, .48f * light, .65f * light, 1);
            else if (cell.forest) shapes.setColor((.20f + texture) * light, .48f * light, .36f * light, 1);
            else shapes.setColor((.32f + texture) * light, .62f * light, .48f * light, 1);
            diamond(p.x, p.y, w + .6f, h + .6f);
        }
    }

    /** Elevated scenery is drawn after the entire terrain pass, in diagonal depth order. */
    private void drawDecorations(float light) {
        for (int depth = 0; depth <= 2 * (world.size - 1); depth++) {
            for (int x = 0; x < world.size; x++) {
                int y = depth - x;
                if (y < 0 || y >= world.size) continue;
                World.Cell cell = world.cells[y][x];
                Vector2 p = iso(x, y);
                float w = TILE_W * zoom, h = TILE_H * zoom;
                if (p.x < -w || p.x > screenW + w || p.y < -h || p.y > screenH + h) continue;
            if (cell.water) {
                shapes.setColor(.62f * light, .84f * light, .84f * light, .25f);
                shapes.rectLine(p.x - w * .19f, p.y + h * .48f, p.x + w * .10f, p.y + h * .48f, 1.1f);
            } else if (cell.forest) {
                shapes.setColor(.09f * light, .28f * light, .21f * light, 1);
                shapes.ellipse(p.x - 8 * zoom, p.y + h * .35f, 16 * zoom, 7 * zoom);
                shapes.setColor(.14f * light, .40f * light, .31f * light, 1);
                shapes.triangle(p.x, p.y + h * 2.05f, p.x - w * .27f, p.y + h * .3f,
                        p.x + w * .27f, p.y + h * .3f);
                shapes.setColor(.25f * light, .56f * light, .40f * light, 1);
                shapes.triangle(p.x, p.y + h * 2.3f, p.x - w * .19f, p.y + h * .85f,
                        p.x + w * .19f, p.y + h * .85f);
            } else if (cell.food > 0) {
                shapes.setColor(.96f, .70f, .40f, 1);
                shapes.circle(p.x, p.y + h * .58f, Math.max(2, 2.6f * zoom), 10);
            }
            }
        }
        Vector2 p = iso(world.libraryX, world.libraryY);
        shapes.setColor(.25f * light, .24f * light, .22f * light, .65f);
        shapes.ellipse(p.x - 22 * zoom, p.y + 2 * zoom, 44 * zoom, 11 * zoom);
        shapes.setColor(.88f * light, .72f * light, .50f * light, 1);
        shapes.rect(p.x - 15 * zoom, p.y + 5 * zoom, 30 * zoom, 28 * zoom);
        shapes.setColor(.32f * light, .28f * light, .36f * light, 1);
        shapes.triangle(p.x - 23 * zoom, p.y + 32 * zoom, p.x + 23 * zoom, p.y + 32 * zoom,
                p.x, p.y + 52 * zoom);
        shapes.setColor(.24f, .36f, .40f, 1);
        shapes.rect(p.x - 4 * zoom, p.y + 5 * zoom, 8 * zoom, 16 * zoom);
    }

    private void drawAgents() {
        // Sort by depth, so near agents obscure distant agents correctly.
        List<World.Agent> ordered = new ArrayList<>(world.agents);
        ordered.sort(Comparator.comparingDouble(a -> {
            Visual v = visuals.get(a.id);
            return v == null ? a.x + a.y : v.x + v.y;
        }));
        for (World.Agent a : ordered) {
            Visual v = visuals.get(a.id);
            if (v == null) continue;
            Vector2 p = iso(v.x, v.y);
            float x = p.x, y = p.y + TILE_H * zoom * .48f;
            float moving = Math.abs(a.x - v.x) + Math.abs(a.y - v.y);
            float bounce = !paused && moving > .025f ? Math.abs(MathUtils.sin(v.phase)) * 3 * zoom : 0;
            Color suit = agentColor(a.id);
            if (a.id == selected) {
                shapes.setColor(.99f, .89f, .53f, .85f);
                shapes.ellipse(x - 14 * zoom, y - 6 * zoom, 28 * zoom, 12 * zoom);
            }
            shapes.setColor(.04f, .14f, .18f, .55f);
            shapes.ellipse(x - 11 * zoom, y - 4 * zoom, 22 * zoom, 8 * zoom);
            // Boots, rounded jacket silhouette, face and hair.
            shapes.setColor(.12f, .20f, .26f, 1);
            shapes.rect(x - 6 * zoom, y + bounce, 4.5f * zoom, 7 * zoom);
            shapes.rect(x + 1.5f * zoom, y + bounce, 4.5f * zoom, 7 * zoom);
            shapes.setColor(suit);
            shapes.ellipse(x - 9 * zoom, y + 5 * zoom + bounce, 18 * zoom, 18 * zoom);
            shapes.setColor(.98f, .81f, .69f, 1);
            shapes.circle(x, y + 27 * zoom + bounce, 7 * zoom, 20);
            shapes.setColor(.20f, .25f, .32f, 1);
            shapes.arc(x, y + 29 * zoom + bounce, 7.2f * zoom, 0, 180, 14);
            shapes.setColor(.12f, .19f, .25f, 1);
            shapes.circle(x + v.facing * 2.5f * zoom, y + 27 * zoom + bounce, .9f * zoom, 8);
            if ("talk".equals(a.action)) {
                shapes.setColor(.85f, .96f, .92f, 1);
                shapes.circle(x + 12 * zoom, y + 37 * zoom, 4 * zoom, 12);
                shapes.circle(x + 19 * zoom, y + 42 * zoom, 2.5f * zoom, 10);
            } else if ("read".equals(a.action) || "write".equals(a.action)) {
                shapes.setColor(.98f, .91f, .71f, 1);
                shapes.rect(x + 5 * zoom, y + 10 * zoom, 10 * zoom, 7 * zoom);
            }
        }
    }

    private void drawPanels() {
        panel(0, screenH - 66, screenW, 66);
        panel(0, 0, screenW, 43);
        float right = Math.min(226, Math.max(184, screenW * .23f));
        panel(screenW - right, 66, right, Math.min(screenH - 150, 92 + world.agents.size() * 30 + (selected >= 0 ? 152 : 0)));
        shapes.setColor(.17f, .30f, .34f, 1);
        shapes.rect(screenW - right, 66, 2, Math.min(screenH - 150, 92 + world.agents.size() * 30 + (selected >= 0 ? 152 : 0)));
        for (int i = 0; i < world.agents.size(); i++) {
            World.Agent a = world.agents.get(i);
            float y = screenH - 115 - i * 30;
            if (y < 90) break;
            if (a.id == selected) {
                shapes.setColor(.20f, .32f, .38f, 1);
                shapes.rect(screenW - right + 8, y - 7, right - 16, 26);
            }
            shapes.setColor(agentColor(a.id));
            shapes.circle(screenW - right + 22, y + 4, 5, 12);
        }
    }

    private void text(String value, float x, float y, Color color) {
        font.setColor(color);
        font.draw(batch, value, x, y);
    }

    private void drawText() {
        float right = Math.min(226, Math.max(184, screenW * .23f));
        text("AI TOWN  /  LIVE WORLD", 18, screenH - 22, INK);
        text(String.format(Locale.ROOT, "DAY %02d    %02d:%02d    %d AGENTS    %s",
                world.day(), (int)world.hour(), (int)((world.hour() % 1) * 60),
                world.agents.size(), paused ? "PAUSED" : "x" + speed),
                18, screenH - 46, MUTED);
        text("WASD pan   +/- zoom   SPACE pause   1-4 speed   L labels   Click agent",
                18, 27, MUTED);
        float left = screenW - right + 15;
        text("RESIDENTS", left, screenH - 87, INK);
        for (int i = 0; i < world.agents.size(); i++) {
            World.Agent a = world.agents.get(i);
            float y = screenH - 112 - i * 30;
            if (y < 90) break;
            text(name(a), left + 20, y + 8, a.id == selected ? INK : MUTED);
            // State is shown as a separate line when an agent is inspected.
        }
        World.Agent picked = null;
        for (World.Agent a : world.agents) if (a.id == selected) { picked = a; break; }
        if (picked != null) {
            float y = screenH - 132 - world.agents.size() * 30;
            text("INSPECT  /  " + name(picked), left, y, INK);
            text("Action: " + picked.action, left, y - 25, MUTED);
            text("Health: " + (int)picked.health + "  Hunger: " + (int)picked.hunger, left, y - 49, MUTED);
            text("Fatigue: " + (int)picked.fatigue + "  Food: " + picked.inventory, left, y - 73, MUTED);
        }
        if (labels) {
            for (World.Agent a : world.agents) {
                Visual v = visuals.get(a.id);
                if (v == null) continue;
                Vector2 p = iso(v.x, v.y);
                float x = p.x - 22 * zoom, y = p.y + 57 * zoom;
                if (x < 0 || x > screenW - right - 30 || y < 48 || y > screenH - 66) continue;
                text(name(a), x, y, a.id == selected ? Color.GOLD : INK);
            }
        }
    }

    @Override public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), .08f);
        elapsed += dt;
        controls(dt);
        advance(dt);
        float daylight = Math.max(.54f, .78f + .22f *
                (float)Math.cos((world.hour() - 12) * Math.PI / 12));
        ScreenUtils.clear(.055f, .105f, .14f, 1);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawWorld(daylight);
        drawDecorations(daylight);
        drawAgents();
        drawPanels();
        shapes.end();
        batch.begin();
        drawText();
        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override public void dispose() {
        shapes.dispose(); batch.dispose(); font.dispose();
        if (policy != null) policy.close();
        try { archive.close(); }
        catch (IOException e) { Gdx.app.error("Archive", "Cannot close archive", e); }
    }
}
