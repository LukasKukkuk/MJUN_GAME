package org.example.logic.managers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

// Vlastní minimální implementace Discord "local RPC" IPC protokolu (named pipe).
// Dřív se používala knihovna io.github.CDAGaming:DiscordIPC, ale ta má nedořešený
// bug (NPE při parsování handshake odpovědi - "data" je null), na který existuje
// jen opuštěný, nikdy nesloučený pull request a žádná opravená verze není
// publikovaná na Maven Central ani na JitPacku. Protokol samotný je jednoduchý
// a dobře zdokumentovaný, takže je snazší a spolehlivější si ho napsat sami, než
// čekat na opravu knihovny nebo nutit hráče zadávat Discord ID ručně.
//
// Protokol: JSON zprávy přes pojmenovanou rouru \\.\pipe\discord-ipc-N (N = 0-9),
// každá zpráva má 8bajtovou hlavičku (opcode + délka payloadu, obojí 4B little-endian)
// následovanou samotným JSON payloadem v UTF-8.
public class DiscordRPCManager {
    private static final int OPCODE_HANDSHAKE = 0;
    private static final int OPCODE_FRAME = 1;

    // volatile - pipe/initialized/discordUserId se zapisují z připojovacího vlákna
    // (DiscordRPC-Connect) a čtou z herního vlákna i EDT (updatePresence/stop/getUserId).
    private static volatile RandomAccessFile pipe;
    private static volatile boolean initialized = false;
    private static volatile String discordUserId = null;

    private static final Object writeLock = new Object();

    private static long APPLICATION_ID = 0;
    private static long startTimestamp = 0;

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

        new Thread(() -> {
            int maxAttempts = 5;
            long retryDelayMs = 4000;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    connectAndHandshake();
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

    private static void connectAndHandshake() throws IOException {
        RandomAccessFile raf = openFirstAvailablePipe();

        JsonObject handshake = new JsonObject();
        handshake.addProperty("v", 1);
        handshake.addProperty("client_id", String.valueOf(APPLICATION_ID));
        writeFrame(raf, OPCODE_HANDSHAKE, handshake.toString());

        String responsePayload = readFramePayload(raf);
        JsonObject json = JsonParser.parseString(responsePayload).getAsJsonObject();

        if (json.has("evt") && "READY".equals(json.get("evt").getAsString()) && json.has("data")) {
            JsonObject data = json.getAsJsonObject("data");
            if (data != null && data.has("user")) {
                discordUserId = data.getAsJsonObject("user").get("id").getAsString();
                System.out.println("👤 Automaticky detekováno Discord ID hráče: " + discordUserId);
            }
        } else {
            raf.close();
            throw new IOException("Discord neodpověděl očekávanou READY událostí (dostal jsem: " + responsePayload + ")");
        }

        pipe = raf;
        initialized = true;
        startTimestamp = OffsetDateTime.now().toEpochSecond();
        System.out.println("✅ Osobní Discord RPC úspěšně napojeno na váš profil!");
    }

    private static RandomAccessFile openFirstAvailablePipe() throws IOException {
        IOException lastError = null;
        for (int i = 0; i < 10; i++) {
            try {
                return new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
            } catch (IOException e) {
                lastError = e;
            }
        }
        throw new IOException("Nenašel jsem žádnou běžící Discord IPC rouru (Discord pravděpodobně neběží).", lastError);
    }

    private static void writeFrame(RandomAccessFile raf, int opcode, String jsonPayload) throws IOException {
        byte[] payload = jsonPayload.getBytes(StandardCharsets.UTF_8);
        byte[] header = new byte[8];
        writeIntLE(header, 0, opcode);
        writeIntLE(header, 4, payload.length);

        synchronized (writeLock) {
            raf.write(header);
            raf.write(payload);
        }
    }

    private static String readFramePayload(RandomAccessFile raf) throws IOException {
        byte[] header = new byte[8];
        raf.readFully(header);
        int length = readIntLE(header, 4);
        byte[] payload = new byte[length];
        raf.readFully(payload);
        return new String(payload, StandardCharsets.UTF_8);
    }

    private static void writeIntLE(byte[] buf, int offset, int value) {
        buf[offset] = (byte) (value & 0xFF);
        buf[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buf[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buf[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private static int readIntLE(byte[] buf, int offset) {
        return (buf[offset] & 0xFF)
                | ((buf[offset + 1] & 0xFF) << 8)
                | ((buf[offset + 2] & 0xFF) << 16)
                | ((buf[offset + 3] & 0xFF) << 24);
    }

    public static void updatePresence(int wave, int hp, int enemiesCount) {
        if (!initialized || pipe == null) return;

        try {
            JsonObject activity = new JsonObject();
            if (hp > 0) {
                activity.addProperty("details", "Vlna: " + wave);
                activity.addProperty("state", "Nepřátelé na mapě: " + enemiesCount + " | HP: " + hp + "/100");
                JsonObject timestamps = new JsonObject();
                timestamps.addProperty("start", startTimestamp);
                activity.add("timestamps", timestamps);
            } else {
                activity.addProperty("details", "Prohrál v " + wave + ". vlně");
                activity.addProperty("state", "Mrtvý (HP: 0/100)");
            }

            JsonObject args = new JsonObject();
            args.addProperty("pid", ProcessHandle.current().pid());
            args.add("activity", activity);

            JsonObject cmd = new JsonObject();
            cmd.addProperty("cmd", "SET_ACTIVITY");
            cmd.add("args", args);
            cmd.addProperty("nonce", UUID.randomUUID().toString());

            writeFrame(pipe, OPCODE_FRAME, cmd.toString());
        } catch (Exception e) {
            System.out.println("Chyba při aktualizaci statusu: " + e.getMessage());
        }
    }

    public static void stop() {
        if (initialized && pipe != null) {
            try {
                pipe.close();
            } catch (IOException ignored) {
            }
            initialized = false;
        }
    }
}
