package com.takumistudios.liveevents.server;

import com.google.gson.JsonObject;
import com.takumistudios.liveevents.LiveEvents;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Aviso opcional a Discord al empezar o terminar un evento (§25.5). La URL del webhook solo vive en server.json: nunca
 * se envía a los clientes ni aparece en los logs. La petición es asíncrona y no bloquea el servidor.
 */
public final class DiscordNotifier {
    private final LiveEventsServer services;
    private HttpClient client;

    DiscordNotifier(LiveEventsServer services) {
        this.services = services;
    }

    public void notify(String text) {
        String url = services.config().discord_webhook;
        if (url == null || url.isEmpty()) {
            return;
        }
        if (client == null) {
            client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        }
        JsonObject body = new JsonObject();
        body.addProperty("content", text.length() > 1900 ? text.substring(0, 1900) : text);
        body.addProperty("username", "LiveEvents");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
            if (error != null) {
                LiveEvents.LOGGER.warn("Discord notification failed: {}", error.getClass().getSimpleName());
            } else if (response.statusCode() >= 300) {
                LiveEvents.LOGGER.warn("Discord notification rejected (HTTP {})", response.statusCode());
            }
        });
    }
}
