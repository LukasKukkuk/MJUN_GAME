package org.example.logic;

import org.example.logic.managers.AudioManager;
import org.example.logic.managers.ConfigManager;

import java.awt.*;
import java.awt.event.KeyEvent;

public class SettingsScreen {

    private final AudioManager audioManager;
    private final GameWindow window;

    private int currentWaveReference;

    private boolean isMuted = false;
    private int storedVolume = 50;

    private final Dimension[] resolutions = {
            new Dimension(800, 600),
            new Dimension(960, 540),
            new Dimension(1280, 720),
            new Dimension(1920, 1080)
    };
    private int currentResIndex = 0;

    public SettingsScreen(AudioManager audioManager, GameWindow window) {
        this.audioManager = audioManager;
        this.window = window;

        // PŘIDÁNO: Zjistí, které rozlišení z configu aktuálně používáme
        for (int i = 0; i < resolutions.length; i++) {
            if (resolutions[i].width == ConfigManager.resWidth && resolutions[i].height == ConfigManager.resHeight) {
                currentResIndex = i;
                break;
            }
        }
    }

    public void setCurrentWave(int wave) {
        this.currentWaveReference = wave;
    }

    public void update(int key) {
        if (key == KeyEvent.VK_F && window != null) {
            window.toggleFullscreen();
        }

        if (key == KeyEvent.VK_UP && ConfigManager.volume < 100) {
            ConfigManager.volume += 10;
            isMuted = false;
            saveAndApplyVolume();
        }

        if (key == KeyEvent.VK_DOWN && ConfigManager.volume > 0) {
            ConfigManager.volume -= 10;
            if (ConfigManager.volume == 0) isMuted = true;
            saveAndApplyVolume();
        }

        if (key == KeyEvent.VK_RIGHT && window != null && !window.isFullscreen()) {
            if (currentResIndex < resolutions.length - 1) {
                currentResIndex++;
                applyResolution();
            }
        }

        if (key == KeyEvent.VK_LEFT && window != null && !window.isFullscreen()) {
            if (currentResIndex > 0) {
                currentResIndex--;
                applyResolution();
            }
        }

        if (key == KeyEvent.VK_M) {
            isMuted = !isMuted;
            if (isMuted) {
                storedVolume = ConfigManager.volume > 0 ? ConfigManager.volume : 50;
                ConfigManager.volume = 0;
            } else {
                ConfigManager.volume = storedVolume;
            }
            saveAndApplyVolume();
        }

        if (key == KeyEvent.VK_R) {
            ConfigManager.volume = 50;
            isMuted = false;
            saveAndApplyVolume();

            if (window != null && !window.isFullscreen()) {
                currentResIndex = 0;
                applyResolution();
            }
        }
    }

    private void saveAndApplyVolume() {
        ConfigManager.save(currentWaveReference);
        audioManager.setVolume(ConfigManager.volume);
    }

    // PŘIDÁNO: Uložení do configu při změně rozlišení
    private void applyResolution() {
        Dimension res = resolutions[currentResIndex];
        ConfigManager.resWidth = res.width;
        ConfigManager.resHeight = res.height;
        ConfigManager.save(currentWaveReference);
        window.changeResolution(res.width, res.height);
    }

    public void draw(Graphics2D g2, int width, int height) {
        int centerX = width / 2;
        int startY = height / 4;

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 40));
        g2.drawString("NASTAVENÍ", centerX - 110, startY);

        g2.setFont(new Font("Arial", Font.PLAIN, 20));

        g2.setColor(window != null && window.isFullscreen() ? Color.GREEN : Color.GRAY);
        g2.drawString("[F] Fullscreen: " + (window != null && window.isFullscreen() ? "ZAPNUTO" : "VYPNUTO"), centerX - 150, startY + 60);

        g2.setColor(window != null && window.isFullscreen() ? Color.DARK_GRAY : Color.CYAN);
        Dimension res = resolutions[currentResIndex];
        g2.drawString("[Šipky Vlevo/Vpravo] Rozlišení: " + res.width + " x " + res.height, centerX - 150, startY + 100);

        g2.setColor(isMuted ? Color.RED : Color.GRAY);
        g2.drawString("[M] Ztlumit vše (Mute): " + (isMuted ? "ZAPNUTO" : "VYPNUTO"), centerX - 150, startY + 140);

        g2.setColor(Color.WHITE);
        g2.drawString("[Šipky Nahoru/Dolů] Hlasitost: " + ConfigManager.volume + " %", centerX - 150, startY + 190);

        g2.setColor(Color.DARK_GRAY);
        g2.fillRect(centerX - 150, startY + 210, 200, 20);
        g2.setColor(isMuted ? Color.RED : Color.GREEN);
        g2.fillRect(centerX - 150, startY + 210, ConfigManager.volume * 2, 20);
        g2.setColor(Color.WHITE);
        g2.drawRect(centerX - 150, startY + 210, 200, 20);

        g2.setColor(Color.GRAY);
        g2.drawString("[R] Resetovat nastavení", centerX - 150, startY + 270);

        g2.setColor(Color.YELLOW);
        g2.drawString("[ESC] Návrat do Menu", centerX - 100, height - 80);
    }
}