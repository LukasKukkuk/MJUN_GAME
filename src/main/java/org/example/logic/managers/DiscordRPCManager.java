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
    private static IPCClient client;
    private static boolean initialized = false;

    private static long APPLICATION_ID = 0;
    private static long startTimestamp = 0;

    // Nová proměnná pro uložení ID hráče
    private static String discordUserId = null;

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

        try {
            client = new IPCClient(APPLICATION_ID);

            client.setListener(new IPCListener() {
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

            client.connect();
        } catch (Exception e) {
            System.out.println("⚠️ Nepodařilo se připojit lokální Discord: " + e.getMessage());
        }
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