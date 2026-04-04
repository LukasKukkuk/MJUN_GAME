package org.example.logic.managers;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.example.logic.GamePanel;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DiscordManager extends ListenerAdapter {
    private JDA jda;
    private GamePanel game;

    // Logika hlasování
    private boolean voteRunning = false;

    // OPRAVA 1: Mapujeme String (ID hráče) na String (název tlačítka, např. "v_troll")
    private Map<String, String> votes = new HashMap<>();

    private long lastVoteTime = 0;
    private final int COOLDOWN_MS = 30000; // 30 sekund mezi hlasováními

    // OPRAVA 2: Sem si uložíme "háček" na zprávu s hlasováním, abychom ji mohli smazat
    private InteractionHook voteMessageHook;

    public DiscordManager(GamePanel game, String botToken) {
        this.game = game;
        try {
            this.jda = JDABuilder.createDefault(botToken)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES)
                    .addEventListeners(this)
                    .build();

            jda.updateCommands().addCommands(
                    Commands.slash("vote", "Spustí 10s hlasování o osudu hráče")
            ).queue();
        } catch (Exception e) {
            System.out.println("❌ Chyba Discordu: " + e.getMessage());
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("vote")) {
            long now = System.currentTimeMillis();
            if (now - lastVoteTime < COOLDOWN_MS) {
                long remaining = (COOLDOWN_MS - (now - lastVoteTime)) / 1000;
                event.reply("⏳ Hlasování je v cooldownu! Počkej ještě " + remaining + "s.").setEphemeral(true).queue();
                return;
            }

            if (voteRunning) {
                event.reply("🗳️ Jedno hlasování už běží!").setEphemeral(true).queue();
                return;
            }

            startVote(event);
        }
    }

    private void startVote(SlashCommandInteractionEvent event) {
        voteRunning = true;
        votes.clear();
        lastVoteTime = System.currentTimeMillis();

        // Uložení háčku pro pozdější smazání
        this.voteMessageHook = event.getHook();

        event.reply("🎮 **HLASOVÁNÍ O OSUDU HRÁČE!** (10 sekund)\nVyberte, co se má stát:")
                .addActionRow(
                        Button.primary("v_heal", "❤️ Heal"),
                        Button.danger("v_gank", "😈 Gank"),
                        Button.secondary("v_troll", "🌀 Troll (Ovládání)"),
                        Button.success("v_freeze", "❄️ Freeze")
                ).queue();

        // Plánovač konce hlasování za 10 sekund
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(this::finishVote, 10, TimeUnit.SECONDS);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!voteRunning) {
            event.reply("❌ Hlasování už skončilo.").setEphemeral(true).queue();
            return;
        }

        // OPRAVA 1: Žádný Integer.valueOf(). Název tlačítka uložíme čistě jako text.
        votes.put(event.getUser().getId(), event.getComponentId());

        event.reply("✅ Tvůj hlas byl započítán!").setEphemeral(true).queue();
    }

    private void finishVote() {
        voteRunning = false;

        // OPRAVA 2: Smazání původní zprávy s anketou (a ignorování případných chyb z Discordu)
        if (voteMessageHook != null) {
            voteMessageHook.deleteOriginal().queue(null, error -> {});
            voteMessageHook = null;
        }

        if (votes.isEmpty()) return;

        // Spočítání hlasů (upraveno na String)
        Map<String, Integer> counts = new HashMap<>();
        for (String choice : votes.values()) {
            counts.put(choice, counts.getOrDefault(choice, 0) + 1);
        }

        // Najití vítěze
        String winner = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .get().getKey();

        // Vykonání akce v hře
        switch (winner) {
            case "v_heal" -> game.triggerDiscordAction("❤️ DIVÁCI TĚ VYLÉČILI!", () -> game.spawnSoulFromDiscord());
            case "v_gank" -> game.triggerDiscordAction("😈 DIVÁCI POSLALI POSILY!", () -> game.spawnEnemyFromDiscord());
            case "v_troll" -> game.triggerDiscordAction("🌀 TROLL: OBRÁCENÉ OVLÁDÁNÍ!", () -> game.activateTrollMode(5000));
            case "v_freeze" -> game.triggerDiscordAction("❄️ DIVÁCI ZMRAZILI NEPŘÁTELE!", () -> game.freezeEnemiesFromDiscord());
        }
    }
}