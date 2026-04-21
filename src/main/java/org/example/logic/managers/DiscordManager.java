package org.example.logic.managers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.logic.GamePanel;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;

public class DiscordManager extends WebSocketClient {
    private GamePanel game;

    // Konstruktor nyní nepotřebuje botToken, ale IP adresu Python serveru
    public DiscordManager(GamePanel game, String serverUri) throws URISyntaxException {
        super(new URI(serverUri));
        this.game = game;
    }

    @Override
    public void onOpen(ServerHandshake handshakeData) {
        System.out.println("🌐 ✅ Úspěšně připojeno k Python centrálnímu botovi!");

        // Zde v budoucnu můžeme odeslat JSON s Discord ID hráče pro statistiky
    }

    @Override
    public void onMessage(String message) {
        try {
            // Přijmeme zprávu a převedeme ji na JSON
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();

            if (json.has("action")) {
                String action = json.get("action").getAsString();

                // Podle toho, co vyhrálo v Pythonu, zavoláme metodu ve hře
                switch (action) {
                    case "v_heal" -> game.triggerDiscordAction("❤️ DIVÁCI TĚ VYLÉČILI!", () -> game.spawnSoulFromDiscord());
                    case "v_gank" -> game.triggerDiscordAction("😈 DIVÁCI POSLALI POSILY!", () -> game.spawnEnemyFromDiscord());
                    case "v_troll" -> game.triggerDiscordAction("🌀 TROLL: OBRÁCENÉ OVLÁDÁNÍ!", () -> game.activateTrollMode(5000));
                    case "v_freeze" -> game.triggerDiscordAction("❄️ DIVÁCI ZMRAZILI NEPŘÁTELE!", () -> game.freezeEnemiesFromDiscord());
                    default -> System.out.println("⚠️ Přijata neznámá akce od bota: " + action);
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Chyba při čtení dat ze serveru: " + e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("🌐 ❌ Odpojeno od Python bota: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        System.out.println("🌐 ⚠️ Chyba spojení: " + ex.getMessage());
    }
}