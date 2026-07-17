package org.example.logic.core;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public class ConfigManager {
    public static int volume = 50;
    public static int highestWave = 1;

    // PŘIDÁNO: Proměnné pro ukládání rozlišení
    public static int resWidth = 800;
    public static int resHeight = 600;

    // Odemčené achievementy (id) a lokální leaderboard (položky "vlna|datum")
    public static Set<String> unlockedAchievements = new LinkedHashSet<>();
    public static List<String> leaderboard = new ArrayList<>();
    private static final int LEADERBOARD_SIZE = 5;

    private static final String FILE_NAME = "config.properties";

    public static void load() {
        try (FileInputStream fis = new FileInputStream(FILE_NAME)) {
            Properties props = new Properties();
            props.load(fis);
            volume = Integer.parseInt(props.getProperty("volume", "50"));
            highestWave = Integer.parseInt(props.getProperty("highestWave", "1"));
            resWidth = Integer.parseInt(props.getProperty("resWidth", "800"));
            resHeight = Integer.parseInt(props.getProperty("resHeight", "600"));

            unlockedAchievements.clear();
            String ach = props.getProperty("achievements", "");
            if (!ach.isBlank()) {
                for (String id : ach.split(",")) {
                    if (!id.isBlank()) unlockedAchievements.add(id.trim());
                }
            }

            leaderboard.clear();
            String lb = props.getProperty("leaderboard", "");
            if (!lb.isBlank()) {
                for (String entry : lb.split(";")) {
                    if (!entry.isBlank()) leaderboard.add(entry.trim());
                }
            }
        } catch (Exception e) {
            save(1); // Vytvoří defaultní config, pokud neexistuje
        }
    }

    public static void save(int wave) {
        highestWave = Math.max(highestWave, wave);
        writeToDisk();
    }

    private static void writeToDisk() {
        try (FileOutputStream fos = new FileOutputStream(FILE_NAME)) {
            Properties props = new Properties();
            props.setProperty("volume", String.valueOf(volume));
            props.setProperty("highestWave", String.valueOf(highestWave));
            props.setProperty("resWidth", String.valueOf(resWidth));
            props.setProperty("resHeight", String.valueOf(resHeight));
            props.setProperty("achievements", String.join(",", unlockedAchievements));
            props.setProperty("leaderboard", String.join(";", leaderboard));
            props.store(fos, "Game Configuration");
        } catch (Exception e) {
            System.out.println("Nepodařilo se uložit config: " + e.getMessage());
        }
    }

    // --- ACHIEVEMENTY ---
    // Vrací true, pokud byl achievement odemčen POPRVÉ (pro zobrazení toastu).
    public static boolean unlockAchievement(String id) {
        boolean isNew = unlockedAchievements.add(id);
        if (isNew) writeToDisk();
        return isNew;
    }

    public static boolean isAchievementUnlocked(String id) {
        return unlockedAchievements.contains(id);
    }

    // --- LEADERBOARD ---
    public static void recordRunResult(int waveReached) {
        String date = new SimpleDateFormat("dd.MM. HH:mm").format(new Date());
        leaderboard.add(waveReached + "|" + date);
        leaderboard.sort((a, b) -> {
            int wa = Integer.parseInt(a.split("\\|")[0]);
            int wb = Integer.parseInt(b.split("\\|")[0]);
            return wb - wa; // sestupně podle vlny
        });
        while (leaderboard.size() > LEADERBOARD_SIZE) {
            leaderboard.remove(leaderboard.size() - 1);
        }
        writeToDisk();
    }

    // --- TRVALÁ META-PROGRESE ---
    // Permanentní bonusy odemčené za nejvyšší dosaženou vlnu napříč všemi runy.
    // Dává důvod hrát znovu i po smrti - postup se neztrácí úplně.
    public static int getMetaBonusMaxHp() {
        if (highestWave >= 8) return 30;
        if (highestWave >= 5) return 15;
        if (highestWave >= 3) return 5;
        return 0;
    }

    public static int getMetaBonusDamage() {
        if (highestWave >= 10) return 10;
        if (highestWave >= 6) return 5;
        return 0;
    }

    public static double getMetaBonusSpeed() {
        if (highestWave >= 7) return 0.5;
        return 0;
    }

    public static String describeMetaProgress() {
        StringBuilder sb = new StringBuilder();
        int hp = getMetaBonusMaxHp();
        int dmg = getMetaBonusDamage();
        double spd = getMetaBonusSpeed();
        if (hp <= 0 && dmg <= 0 && spd <= 0) return "Trvalé bonusy: žádné zatím (dosáhni vlny 3+)";
        sb.append("Trvalé bonusy:");
        if (hp > 0) sb.append(" +").append(hp).append(" HP");
        if (dmg > 0) sb.append("  +").append(dmg).append(" DMG");
        if (spd > 0) sb.append("  +").append(spd).append(" rychlost");
        return sb.toString();
    }
}
