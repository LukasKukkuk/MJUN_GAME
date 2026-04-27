package org.example.logic.managers;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CutsceneManager {

    // Třída reprezentující jednu repliku (jeden "snímek" cutscény)
    public static class DialogLine {
        public String characterName;
        public String text;
        public Image portrait;
        public String voiceAudioPath; // Cesta k souboru s dabingem (např. "/voice_boss1.wav")

        public DialogLine(String name, String text, Image portrait, String voiceAudioPath) {
            this.characterName = name;
            this.text = text;
            this.portrait = portrait;
            this.voiceAudioPath = voiceAudioPath;
        }
    }

    private List<DialogLine> sequence = new ArrayList<>();
    private int currentIndex = 0;

    // Proměnné pro efekt psacího stroje (postupné vypisování)
    private String displayedText = "";
    private int charIndex = 0;
    private long lastCharTime = 0;
    private final int TYPE_DELAY = 30; // Rychlost psaní písmen (ms)

    private AudioManager audioManager;
    private boolean isFinished = false;
    private Runnable onFinishCallback; // Co se stane, když cutscéna skončí

    public CutsceneManager(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    // Začne novou cutscénu
    public void startCutscene(List<DialogLine> lines, Runnable onFinish) {
        this.sequence = lines;
        this.currentIndex = 0;
        this.isFinished = false;
        this.onFinishCallback = onFinish;

        if (!sequence.isEmpty()) {
            loadCurrentLine();
        } else {
            finish();
        }
    }

    private void loadCurrentLine() {
        displayedText = "";
        charIndex = 0;
        DialogLine current = sequence.get(currentIndex);

        // Přehrání dabingu!
        if (current.voiceAudioPath != null && !current.voiceAudioPath.isEmpty()) {
            audioManager.playVoice(current.voiceAudioPath);
        }
    }

    public void update() {
        if (isFinished || sequence.isEmpty()) return;

        DialogLine current = sequence.get(currentIndex);

        // Postupné přidávání písmenek (Psací stroj)
        if (charIndex < current.text.length()) {
            if (System.currentTimeMillis() - lastCharTime > TYPE_DELAY) {
                displayedText += current.text.charAt(charIndex);
                charIndex++;
                lastCharTime = System.currentTimeMillis();
            }
        }
    }

    // Funkce pro odkliknutí (Mezerník / Myš)
    public void next() {
        if (isFinished) return;

        DialogLine current = sequence.get(currentIndex);

        // Pokud se text ještě píše, kliknutím ho vypíšeme celý rovnou
        if (charIndex < current.text.length()) {
            displayedText = current.text;
            charIndex = current.text.length();
        } else {
            // Jdeme na další repliku
            currentIndex++;
            if (currentIndex >= sequence.size()) {
                finish();
            } else {
                loadCurrentLine();
            }
        }
    }

    private void finish() {
        isFinished = true;
        // Zastavíme dabing, pokud ještě mluví, a zavoláme akci po skončení
        audioManager.stopVoice();
        if (onFinishCallback != null) {
            onFinishCallback.run();
        }
    }

    public void draw(Graphics2D g2, int screenW, int screenH) {
        if (isFinished || sequence.isEmpty()) return;

        DialogLine current = sequence.get(currentIndex);

        // Ztmavíme pozadí hry (filmový efekt)
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(0, 0, screenW, screenH);

        // Vykreslení portrétu (pokud existuje)
        int boxH = 150;
        int boxY = screenH - boxH - 20;

        if (current.portrait != null) {
            g2.drawImage(current.portrait, 20, boxY - 50, 200, 200, null);
        }

        // Pozadí dialogového okna
        g2.setColor(new Color(20, 20, 20, 230));
        g2.fillRoundRect(230, boxY, screenW - 250, boxH, 20, 20);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(3));
        g2.drawRoundRect(230, boxY, screenW - 250, boxH, 20, 20);

        // Jméno postavy (např. žlutě)
        g2.setColor(Color.YELLOW);
        g2.setFont(new Font("Arial", Font.BOLD, 24));
        g2.drawString(current.characterName, 250, boxY + 35);

        // Samotný text
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.PLAIN, 20));

        // Jednoduché zalamování textu
        String[] words = displayedText.split(" ");
        String line = "";
        int textY = boxY + 70;

        for (String word : words) {
            if (g2.getFontMetrics().stringWidth(line + word) > screenW - 300) {
                g2.drawString(line, 250, textY);
                line = "";
                textY += 30;
            }
            line += word + " ";
        }
        g2.drawString(line, 250, textY);

        // Instrukce pro pokračování
        if (charIndex >= current.text.length()) {
            g2.setFont(new Font("Arial", Font.ITALIC, 14));
            g2.setColor(Color.GRAY);
            g2.drawString("[Stiskněte MEZERNÍK pro pokračování]", screenW - 280, boxY + boxH - 15);
        }
    }

    public boolean isFinished() { return isFinished; }
}