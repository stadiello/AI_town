package com.aitown;

import com.aitown.sim.*;
import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import java.io.IOException;
import java.util.*;

/** Smooth GPU-rendered isometric view, separate from simulation. */
public final class TownGame extends ApplicationAdapter {
    private final World world;
    private final Archive archive;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private float camX = 650, camY = 80, zoom = 1, accumulator = 0;
    private boolean paused;
    private int speed = 1, selected = -1;
    private static final float TW = 48, TH = 24;
    private final Vector2 point = new Vector2();

    public TownGame(World world, Archive archive) {
        this.world = world;
        this.archive = archive;
    }
    @Override public void create() {
        shapes = new ShapeRenderer();
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(1.15f);
    }
    private Vector2 iso(int x, int y) {
        point.set(camX + (x - y) * TW * zoom / 2,
                  camY + (x + y) * TH * zoom / 2);
        return point;
    }
    private void diamond(float x, float y, float w, float h) {
        shapes.triangle(x, y, x + w / 2, y + h / 2, x, y + h);
        shapes.triangle(x, y, x, y + h, x - w / 2, y + h / 2);
    }
    @Override public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), .1f);
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit();
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) paused = !paused;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) speed = 1;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) speed = 2;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) speed = 5;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4)) speed = 10;
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) camX += 350 * dt;
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) camX -= 350 * dt;
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) camY -= 350 * dt;
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) camY += 350 * dt;
        if (Gdx.input.isKeyJustPressed(Input.Keys.EQUALS)) zoom = Math.min(2.5f, zoom * 1.15f);
        if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS)) zoom = Math.max(.4f, zoom / 1.15f);
        if (Gdx.input.justTouched()) {
            float mx = Gdx.input.getX(), my = Gdx.graphics.getHeight() - Gdx.input.getY();
            float min = 28 * zoom;
            selected = -1;
            for (World.Agent a : world.agents) {
                Vector2 p = iso(a.x, a.y);
                float dist = Vector2.dst(mx, my, p.x, p.y + TH * zoom / 2);
                if (dist < min) { min = dist; selected = a.id; }
            }
        }
        if (!paused) {
            accumulator += dt * speed;
            while (accumulator >= .35f) { world.step(); accumulator -= .35f; }
        }
        float light = Math.max(.22f, .5f + .5f *
            (float)Math.cos((world.hour() - 12) * Math.PI / 12));
        ScreenUtils.clear(.055f, .09f, .14f, 1);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int y = 0; y < world.size; y++) {
            for (int x = 0; x < world.size; x++) {
                World.Cell cell = world.cells[y][x];
                Vector2 p = iso(x, y);
                float sx = p.x, sy = p.y, w = TW * zoom, h = TH * zoom;
                if (sx < -w || sx > Gdx.graphics.getWidth() + w ||
                    sy < -h || sy > Gdx.graphics.getHeight() + h) continue;
                if (cell.water) shapes.setColor(.20f * light, .49f * light, .72f * light, 1);
                else if (cell.forest) shapes.setColor(.16f * light, .46f * light, .29f * light, 1);
                else shapes.setColor(.35f * light, .63f * light, .40f * light, 1);
                diamond(sx, sy, w, h);
                if (cell.forest) {
                    shapes.setColor(.07f * light, .35f * light, .19f * light, 1);
                    shapes.triangle(sx, sy + h * 1.7f, sx - w * .27f, sy + h * .3f,
                            sx + w * .27f, sy + h * .3f);
                } else if (cell.food > 0) {
                    shapes.setColor(.84f, .34f, .28f, 1);
                    shapes.circle(sx, sy + h * .5f, Math.max(1.5f, 2.5f * zoom), 8);
                }
            }
        }
        for (World.Agent a : world.agents) {
            Vector2 p = iso(a.x, a.y);
            float x = p.x, y = p.y + TH * zoom / 2;
            shapes.setColor(.11f, .16f, .18f, .7f);
            shapes.ellipse(x - 8 * zoom, y - 3 * zoom, 16 * zoom, 6 * zoom);
            shapes.setColor((a.id % 3 == 0) ? .92f : .63f,
                            (a.id % 3 == 1) ? .82f : .55f, .64f, 1);
            shapes.circle(x, y + 9 * zoom, Math.max(3, 6 * zoom), 12);
            shapes.setColor(.96f, .79f, .64f, 1);
            shapes.circle(x, y + 17 * zoom, Math.max(2, 4 * zoom), 12);
            if (a.id == selected) {
                shapes.setColor(1, .94f, .36f, 1);
                shapes.rectLine(x - 12 * zoom, y, x + 12 * zoom, y, 2);
            }
        }
        shapes.end();
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "AI TOWN | Day " + world.day() + " | " +
                String.format(Locale.ROOT, "%02d:%02d", (int)world.hour(),
                        (int)((world.hour() % 1) * 60)) +
                " | Agents " + world.agents.size() + " | " + (paused ? "PAUSED" : "x" + speed),
                16, Gdx.graphics.getHeight() - 16);
        font.draw(batch, "WASD move  |  +/- zoom  |  Click inspect  |  SPACE pause  |  1-4 speed",
                16, 30);
        if (selected >= 0) for (World.Agent a : world.agents) if (a.id == selected) {
            int x = Gdx.graphics.getWidth() - 270, top = Gdx.graphics.getHeight() - 18;
            String[] lines = {"AGENT #" + a.id, "HP: " + (int)a.health,
                    "Hunger: " + (int)a.hunger, "Fatigue: " + (int)a.fatigue,
                    "Food: " + a.inventory, "Action: " + a.action};
            for (int i = 0; i < lines.length; i++) font.draw(batch, lines[i], x, top - i * 24);
        }
        batch.end();
    }
    @Override public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
        try { archive.close(); } catch (IOException e) { Gdx.app.error("Archive", "Cannot close archive", e); }
    }
}
