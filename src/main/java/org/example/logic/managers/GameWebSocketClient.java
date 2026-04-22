package org.example.logic.managers;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;

public class GameWebSocketClient extends WebSocketClient {

    public GameWebSocketClient(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("🌐 ✅ Úspěšně připojeno k Python botovi na hostingu!");

        // Zde si vytáhneme ID hráče z tvého DiscordRPCManageru
        String discordId = DiscordRPCManager.getUserId();

        if (discordId != null) {
            // Jakmile se hra připojí, pošleme botovi tajnou zprávu s ID hráče
            // Bot díky tomu pozná: "Aha, tohle je hráč s tímto Discord ID!"
            String authMessage = "{\"action\": \"auth\", \"user_id\": \"" + discordId + "\"}";
            send(authMessage);
        } else {
            System.out.println("⚠️ Discord ID nebylo nalezeno, odesílám anonymní připojení.");
        }
    }

    @Override
    public void onMessage(String message) {
        System.out.println("📩 Přišla zpráva od Bota: " + message);
        // Tady budeš později řešit příkazy z Discordu
        // Např. Pokud message == "VOTE_KILL", zabij hráče ve hře
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("🌐 ❌ Odpojeno od bota. Důvod: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        System.out.println("🌐 ⚠️ Chyba WebSocketu: " + ex.getMessage());
    }
}