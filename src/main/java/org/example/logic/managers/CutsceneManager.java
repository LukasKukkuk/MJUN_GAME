package org.example.logic.managers;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class CutsceneManager {

    // --- ENUM PRO TYPY PŘÍKAZŮ ---
    public enum Type { TEXT, BGM, SFX, SHAKE, CMD }

    // Třída reprezentující jednu repliku nebo příkaz v cutscéně
    public static class DialogLine {
        public Type type;
        public String characterName;
        public String text;
        public Image portrait;
        public String audioPath; // Cesta k souboru s dabingem (např. "/voice_boss1.wav")
        public String value;     // Hodnota pro příkazy (cesta k BGM, intenzita shaku)

        // Konstruktor 1: Pro zpětnou kompatibilitu (tvůj starý kód v GamePanelu)
        public DialogLine(String name, String text, Image portrait, String audioPath) {
            this.type = Type.TEXT;
            this.characterName = name;
            this.text = text;
            this.portrait = portrait;
            this.audioPath = audioPath;
        }

        // Konstruktor 2: Pro načítání TEXTU ze souboru
        public DialogLine(Type type, String name, String text, Image portrait, String audioPath) {
            this.type = type;
            this.characterName = name;
            this.text = text;
            this.portrait = portrait;
            this.audioPath = audioPath;
        }

        // Konstruktor 3: Pro ne-textové příkazy (BGM, SFX, SHAKE)
        public DialogLine(Type type, String value) {
            this.type = type;
            this.value = value;
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
    private boolean isFinished = true; // Změněno na true v základu
    private Runnable onFinishCallback; // Co se stane, když cutscéna skončí

    // Flagy pro GamePanel (např. otřes obrazovky)
    private boolean shakeRequested = false;
    private int shakeIntensity = 0;

    public CutsceneManager(AudioManager audioManager) {
        this.audioManager = audioManager;
    }

    // --- NOVINKA: Načtení a spuštění rovnou ze souboru ---
    public void startCutsceneFromFile(String path, Runnable onFinish) {
        sequence.clear();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(path)))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue; // Ignoruje komentáře

                // Přečtení parametrů (rozděleno středníkem)
                String[] p = line.split(";", -1);
                Type type = Type.valueOf(p[0].toUpperCase());

                if (type == Type.TEXT) {
                    // Přečte obrázek, pokud existuje
                    Image portrait = null;
                    if (p.length > 3 && !p[3].trim().isEmpty()) {
                        URL imgUrl = getClass().getResource(p[3]);
                        if (imgUrl != null) portrait = new ImageIcon(imgUrl).getImage();
                    }
                    // Přečte dabing, pokud existuje
                    String voice = (p.length > 4 && !p[4].trim().isEmpty()) ? p[4] : null;
                    sequence.add(new DialogLine(type, p[1], p[2], portrait, voice));
                } else {
                    // BGM, SFX, SHAKE, CMD
                    sequence.add(new DialogLine(type, p[1]));
                }
            }
        } catch (Exception e) {
            System.out.println("Chyba při načítání cutscény ze souboru: " + e.getMessage());
        }

        startCutscene(sequence, onFinish);
    }

    // --- PŮVODNÍ SPUŠTĚNÍ (kompatibilní) ---
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
        if (currentIndex >= sequence.size()) {
            finish();
            return;
        }

        DialogLine current = sequence.get(currentIndex);

        if (current.type == Type.TEXT) {
            displayedText = "";
            charIndex = 0;
            // Přehrání dabingu
            if (current.audioPath != null && !current.audioPath.isEmpty()) {
                audioManager.playVoice(current.audioPath); // Tvůj původní dabing
            }
        }
        else if (current.type == Type.BGM) {
            if (current.value.equals("STOP")) audioManager.stopMusic();
            else audioManager.playMusic(current.value);
            forceNextInternal(); // Okamžitě přeskočí na další řádek (nečeká na uživatele)
        }
        else if (current.type == Type.SFX) {
            audioManager.playSound(current.value);
            forceNextInternal();
        }
        else if (current.type == Type.SHAKE) {
            shakeRequested = true;
            try { shakeIntensity = Integer.parseInt(current.value); } catch (Exception e) { shakeIntensity = 10; }
            forceNextInternal();
        }
    }

    public void update() {
        if (isFinished || sequence.isEmpty()) return;

        DialogLine current = sequence.get(currentIndex);

        // Psací stroj (pouze u TEXT příkazů)
        if (current.type == Type.TEXT && charIndex < current.text.length()) {
            if (System.currentTimeMillis() - lastCharTime > TYPE_DELAY) {
                displayedText += current.text.charAt(charIndex);
                charIndex++;
                lastCharTime = System.currentTimeMillis();
            }
        }
    }

    // Funkce pro odkliknutí (Mezerník / Myš)
    public void next() {
        if (isFinished || sequence.isEmpty()) return;

        DialogLine current = sequence.get(currentIndex);

        if (current.type == Type.TEXT) {
            // Pokud se text ještě píše, kliknutím ho vypíšeme celý rovnou
            if (charIndex < current.text.length()) {
                displayedText = current.text;
                charIndex = current.text.length();
            } else {
                forceNextInternal();
            }
        }
    }

    // Vnitřní posunutí (používá se pro příkazy BGM/SFX, které přeskočí samy)
    private void forceNextInternal() {
        currentIndex++;
        if (currentIndex >= sequence.size()) {
            finish();
        } else {
            loadCurrentLine();
        }
    }

    private void finish() {
        isFinished = true;
        audioManager.stopVoice();
        if (onFinishCallback != null) {
            onFinishCallback.run();
        }
    }

    // API pro GamePanel k otřesení obrazovky z cutscény
    public boolean consumeShakeRequest() {
        if (shakeRequested) {
            shakeRequested = false;
            return true;
        }
        return false;
    }
    public int getShakeIntensity() { return shakeIntensity; }

    public void draw(Graphics2D g2, int screenW, int screenH) {
        if (isFinished || sequence.isEmpty()) return;

        DialogLine current = sequence.get(currentIndex);

        // Kreslíme UI pouze pokud jde o TEXT, u ostatních příkazů se nekreslí nic
        if (current.type != Type.TEXT) return;

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
        g2.setStroke(new BasicStroke(1));

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