package com.aitown.sim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;

/** Bounded background Ollama decisions: never blocks the LibGDX render thread. */
public final class OllamaPolicy implements AutoCloseable {
    public record Decision(String action, String message, String reason) {}
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "ai-town-ollama"); t.setDaemon(true); return t;
    });
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String endpoint, model;
    public OllamaPolicy(String endpoint, String model) {
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length()-1) : endpoint;
        this.model = model;
    }
    public CompletableFuture<Decision> request(String observation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var payload = json.createObjectNode();
                payload.put("model", model);
                payload.put("stream", false);
                payload.put("format", "json");
                payload.put("options", json.createObjectNode().put("temperature", 0.3));
                payload.put("prompt", """
                    You control one agent in a survival simulation. Return ONLY a JSON object with:
                    action: one of [move, harvest, eat, sleep, talk, read, write, wait],
                    message: short statement (for talk or write), reason: short public decision summary.
                    No imaginary actions or effects. Observation:
                    """ + observation);
                var request = HttpRequest.newBuilder(URI.create(endpoint + "/api/generate"))
                    .timeout(Duration.ofSeconds(6)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if(response.statusCode()!=200) return null;
                JsonNode node = json.readTree(response.body());
                JsonNode decision = json.readTree(node.path("response").asText());
                return new Decision(decision.path("action").asText("wait"),
                    decision.path("message").asText("").substring(0, Math.min(240, decision.path("message").asText("").length())),
                    decision.path("reason").asText("").substring(0, Math.min(240, decision.path("reason").asText("").length())));
            } catch (Exception e) { return null; }
        }, executor);
    }
    @Override public void close() { executor.shutdownNow(); }
}
