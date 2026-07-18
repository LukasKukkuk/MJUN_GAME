package org.example.logic.managers;

import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.IPCListener;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.entities.Packet;
import com.jagrosh.discordipc.entities.ActivityType;
import com.google.gson.JsonObject;
import com.jagrosh.discordipc.entities.User;
import io.github.cdimascio.dotenv.Dotenv;

import java.time.OffsetDateTime;

public class DiscordRPCManager {
    // volatile - client/initialized/discordUserId se zapisují z připojovacího vlákna
    // (DiscordRPC-Connect) a čtou z herního vlákna i EDT (updatePresence/stop/getUserId).
    private static volatile IPCClient client;
    private static volatile boolean initialized = false;

    private static long APPLICATION_ID = 0;
    private static long startTimestamp = 0;

    // Nová proměnná pro uložení ID hráče
    private static volatile String discordUserId = null;

    static {
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            String appToken = dotenv.get("APPLICATION_TOKEN");

            if (appToken != null && !appToken.isEmpty()) {
                APPLICATION_ID = Long.parseLong(appToken.replace("L", "").replace("l", ""));
            } else {
                System.out.println("⚠️ APPLICATION_TOKEN nenalezen v .env souboru.");
            }
        } catch (Exception e) {
            System.out.println("⚠️ Chyba při načítání .env pro Discord RPC: " + e.getMessage());
        }
    }

    // Náš nový "most" pro získání ID do WebSockets
    public static String getUserId() {
        return discordUserId;
    }

    public static void start() {
        if (APPLICATION_ID == 0) {
            System.out.println("⚠️ Discord RPC zrušeno: Chybí platné APPLICATION_ID.");
            return;
        }

        // Spustí se v samostatném vlákně, protože zkouší opakovaně navázat spojení
        // s lokálním Discordem (handshake může selhat kvůli chybě v knihovně nebo
        // proto, že Discord ještě nebyl v okamžiku startu hry plně nastartovaný).
        new Thread(() -> {
            int maxAttempts = 5;
            long retryDelayMs = 4000;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    IPCClient newClient = new IPCClient(APPLICATION_ID);

                    newClient.setListener(new IPCListener() {
                        @Override
                        public void onReady(IPCClient client) {
                            System.out.println("✅ Osobní Discord RPC úspěšně napojeno na váš profil!");
                            initialized = true;
                            startTimestamp = OffsetDateTime.now().toEpochSecond();

                            // Vytáhneme si ID z běžícího Discordu
                            if (client.getCurrentUser() != null) {
                                discordUserId = client.getCurrentUser().getId();
                                System.out.println("👤 Automaticky detekováno Discord ID hráče: " + discordUserId);
                            }
                        }

                        @Override public void onClose(IPCClient client, JsonObject json) {}
                        @Override public void onDisconnect(IPCClient client, Throwable t) {}
                        public void onError(IPCClient client, Throwable t) {}
                        @Override public void onPacketSent(IPCClient client, Packet packet) {}
                        @Override public void onPacketReceived(IPCClient client, Packet packet) {}
                        @Override public void onActivityJoin(IPCClient client, String secret) {}
                        @Override public void onActivitySpectate(IPCClient client, String secret) {}
                        @Override public void onActivityJoinRequest(IPCClient ipcClient, String s, User user) {}
                        public void onActivityJoinRequest(IPCClient client, JsonObject request) {}
                    });

                    newClient.connect();
                    client = newClient;
                    return; // úspěch - žádné další pokusy
                } catch (Exception e) {
                    System.out.println("⚠️ Pokus " + attempt + "/" + maxAttempts + " o připojení k lokálnímu Discordu selhal: " + e.getMessage());
                    if (attempt < maxAttempts) {
                        try {
                            Thread.sleep(retryDelayMs);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }
            System.out.println("⚠️ Lokální Discord RPC se nepodařilo napojit po " + maxAttempts + " pokusech - hráč zůstane bez auto-detekovaného ID.");
        }, "DiscordRPC-Connect").start();
    }

    public static void updatePresence(int wave, int hp, int enemiesCount) {
        if (!initialized || client == null) return;

        try {
            RichPresence.Builder builder = new RichPresence.Builder();
            builder.setActivityType(ActivityType.Playing);

            if (hp > 0) {
                builder.setDetails("Vlna: " + wave)
                        .setState("Nepřátelé na mapě: " + enemiesCount + " | HP: " + hp + "/100")
                        .setStartTimestamp(startTimestamp);
            } else {
                builder.setDetails("Prohrál v " + wave + ". vlně")
                        .setState("Mrtvý (HP: 0/100)");
            }

            client.sendRichPresence(builder.build());
        } catch (Exception e) {
            System.out.println("Chyba při aktualizaci statusu: " + e.getMessage());
        }
    }

    public static void stop() {
        if (initialized && client != null) {
            client.close();
            initialized = false;
        }
    }
}