package org.example.logic.managers;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class ConfigManager {
    public static int volume = 50;
    public static int highestWave = 1;

    // PŘIDÁNO: Proměnné pro ukládání rozlišení
    public static int resWidth = 800;
    public static int resHeight = 600;

    private static final String FILE_NAME = "config.properties";

    public static void load() {
        try (FileInputStream fis = new FileInputStream(FILE_NAME)) {
            Properties props = new Properties();
            props.load(fis);
            volume = Integer.parseInt(props.getProperty("volume", "50"));
            highestWave = Integer.parseInt(props.getProperty("highestWave", "1"));
            resWidth = Integer.parseInt(props.getProperty("resWidth", "800"));
            resHeight = Integer.parseInt(props.getProperty("resHeight", "600"));
        } catch (Exception e) {
            save(1); // Vytvoří defaultní config, pokud neexistuje
        }
    }

    public static void save(int wave) {
        highestWave = Math.max(highestWave, wave);
        try (FileOutputStream fos = new FileOutputStream(FILE_NAME)) {
            Properties props = new Properties();
            props.setProperty("volume", String.valueOf(volume));
            props.setProperty("highestWave", String.valueOf(highestWave));
            props.setProperty("resWidth", String.valueOf(resWidth));
            props.setProperty("resHeight", String.valueOf(resHeight));
            props.store(fos, "Game Configuration");
        } catch (Exception e) {
            System.out.println("Nepodařilo se uložit config: " + e.getMessage());
        }
    }
}