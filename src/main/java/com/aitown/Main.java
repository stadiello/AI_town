package com.aitown;

import com.aitown.sim.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import java.nio.file.Path;

public final class Main {
    public static void main(String[] args) throws Exception {
        boolean headless = false, llm = false;
        String model = "qwen3:8b", endpoint = "http://127.0.0.1:11434";
        int ticks = 1000, seed = 42;
        for (int i = 0; i < args.length; i++) {
            if ("--headless".equals(args[i])) headless = true;
            else if ("--llm".equals(args[i])) llm = true;
            else if ("--model".equals(args[i])) model = args[++i];
            else if ("--ollama-url".equals(args[i])) endpoint = args[++i];
            else if ("--ticks".equals(args[i])) ticks = Integer.parseInt(args[++i]);
            else if ("--seed".equals(args[i])) seed = Integer.parseInt(args[++i]);
        }
        Archive archive = new Archive(Path.of("data"));
        World world = new World(48, 10, seed, archive::event);
        OllamaPolicy policy = llm ? new OllamaPolicy(endpoint, model) : null;
        world.connect(archive, policy);
        if (headless) {
            try (archive) {
                for (int i = 0; i < ticks; i++) world.step();
                System.out.println("Day " + world.day() + ", agents " + world.agents.size());
                if (policy != null) policy.close();
            }
        } else {
            Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
            config.setTitle("AI Town | Java / LibGDX");
            config.setWindowedMode(1280, 820);
            config.useVsync(true);
            config.setForegroundFPS(60);
            new Lwjgl3Application(new TownGame(world, archive, policy), config);
        }
    }
}
