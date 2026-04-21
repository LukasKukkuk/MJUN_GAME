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

    public DiscordManager(GamePanel game, String serverUri) throws URISyntaxException {
        super(new URI(serverUri));
        this.game = game;
    }

    @Override
    public void onOpen(ServerHandshake handshakeData) {
        System.out.println("🌐 ✅ Úspěšně připojeno k Python botovi! Čekám na Discord ID...");

        new Thread(() -> {
            try {
                int attempts = 0;
                // Čeká až 5 sekund na naběhnutí RPC klienta
                while (DiscordRPCManager.getUserId() == null && attempts < 10) {
                    Thread.sleep(500);
                    attempts++;
                }

                String finalId = DiscordRPCManager.getUserId();
                if (finalId == null) {
                    System.out.println("⚠️ RPC nedodalo ID včas, zkouším zálohu z .env...");
                    io.github.cdimascio.dotenv.Dotenv dotenv = io.github.cdimascio.dotenv.Dotenv.configure().ignoreIfMissing().load();
                    finalId = dotenv.get("PLAYER_DISCORD_ID", "0");
                }

                JsonObject welcomeMsg = new JsonObject();
                welcomeMsg.addProperty("type", "register");
                welcomeMsg.addProperty("discord_id", finalId);

                send(welcomeMsg.toString());
                System.out.println("📤 Registrace odeslána s ID: " + finalId);

            } catch (Exception e) {
                System.out.println("❌ Chyba při registraci k serveru: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();

            if (json.has("action")) {
                String action = json.get("action").getAsString();

                switch (action) {
                    case "v_heal" -> game.triggerDiscordAction("❤️ DIVÁCI TĚ VYLÉČILI!", () -> game.spawnSoulFromDiscord());
                    case "v_freeze" -> game.triggerDiscordAction("❄️ DIVÁCI ZMRAZILI NEPŘÁTELE!", () -> game.freezeEnemiesFromDiscord());
                    case "v_troll" -> game.triggerDiscordAction("🌀 TROLL: OBRÁCENÉ OVLÁDÁNÍ!", () -> game.activateTrollMode(5000));
                    case "v_horde" -> game.triggerDiscordAction("💀 HORDA: BLÍŽÍ SE SMRT!", () -> game.spawnHorde());
                    case "v_rage" -> game.triggerDiscordAction("⚡ RAGE: NEPŘÁTELÉ ZUŘÍ!", () -> game.rageModeFromDiscord());
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