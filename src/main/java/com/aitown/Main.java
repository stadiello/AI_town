package com.aitown;

import com.aitown.sim.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import java.nio.file.Path;

public final class Main {
    public static void main(String[] args) throws Exception {
        boolean headless = false;
        int ticks = 1000, seed = 42;
        for (int i = 0; i < args.length; i++) {
            if ("--headless".equals(args[i])) headless = true;
            else if ("--ticks".equals(args[i])) ticks = Integer.parseInt(args[++i]);
            else if ("--seed".equals(args[i])) seed = Integer.parseInt(args[++i]);
        }
        Archive archive = new Archive(Path.of("data"));
        World world = new World(48, 10, seed, archive::event);
        if (headless) {
            try (archive) {
                for (int i = 0; i < ticks; i++) world.step();
                System.out.println("Day " + world.day() + ", agents " + world.agents.size());
            }
        } else {
            Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
            config.setTitle("AI Town | Java / LibGDX");
            config.setWindowedMode(1280, 820);
            config.useVsync(true);
            config.setForegroundFPS(60);
            new Lwjgl3Application(new TownGame(world, archive), config);
        }
    }
}
