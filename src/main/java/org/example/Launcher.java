package org.example;

import org.example.logic.*;
import org.example.logic.managers.*;
import javax.swing.SwingUtilities;

public class Launcher {
    public static AudioManager audioManager;

    public static void main(String[] args) {
        // Načtení nastavení (včetně uloženého rozlišení)
        ConfigManager.load();

        SwingUtilities.invokeLater(() -> {
            GamePanel gamePanel = new GamePanel();

            // PŘIDÁNO: Aplikujeme uložené rozlišení hned při tvorbě okna
            gamePanel.updateDimensions(ConfigManager.resWidth, ConfigManager.resHeight);

            GameWindow window = new GameWindow(gamePanel);
            gamePanel.setWindow(window);
            gamePanel.startGame();
        });
    }
}