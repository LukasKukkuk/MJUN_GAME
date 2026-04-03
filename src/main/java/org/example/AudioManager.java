package org.example;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.URL;

public class AudioManager {
    private Clip currentClip;
    private int currentVolume = 100;
    private String currentPlayingPath = null;

    public void playMusicForWave(int level) {
        String fileName = "/level" + level + ".mp3";
        if (level >= 3) {
            fileName = "/level3boss.mp3";
        }
        if (getClass().getResource(fileName) == null) {
            fileName = "/menu.mp3";
        }
        playMusic(fileName, true);
    }

    public void playMenuMusic() {
        playMusic("/menu.mp3", true);
    }

    public void playGameOverMusic() {
        playMusic("/gameover.mp3", false);
    }

    public void stopMusic() {
        if (currentClip != null && currentClip.isRunning()) {
            currentClip.stop();
            currentClip.close();
        }
        currentPlayingPath = null;
    }

    private void playMusic(String path, boolean loop) {
        // Ignoruj, pokud už tato hudba hraje
        if (path.equals(currentPlayingPath) && currentClip != null && currentClip.isRunning()) {
            return;
        }

        stopMusic();

        try {
            URL url = getClass().getResource(path);
            if (url == null) {
                System.out.println("Audio " + path + " nenalezeno.");
                return;
            }

            // Načtení komprimovaného MP3 streamu
            AudioInputStream in = AudioSystem.getAudioInputStream(url);
            AudioFormat baseFormat = in.getFormat();

            // Dekódování MP3 do čistého PCM (jako WAV), abychom mohli měnit hlasitost
            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false
            );
            AudioInputStream din = AudioSystem.getAudioInputStream(decodedFormat, in);

            currentClip = AudioSystem.getClip();
            currentClip.open(din);
            currentPlayingPath = path;

            // Nastavíme hlasitost ihned po načtení
            applyVolume();

            // Přehrávání
            if (loop) {
                currentClip.loop(Clip.LOOP_CONTINUOUSLY); // Automatická dokonalá smyčka!
            } else {
                currentClip.start();
            }

        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.out.println("Chyba při přehrávání audia: " + e.getMessage());
        }
    }

    public void setVolume(int volume) {
        this.currentVolume = volume;
        applyVolume();
        System.out.println("Hlasitost nastavena na: " + volume + "%");
    }

    // Aplikuje matematicky správnou hlasitost na dekódovaný zvuk
    private void applyVolume() {
        if (currentClip != null && currentClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gainControl = (FloatControl) currentClip.getControl(FloatControl.Type.MASTER_GAIN);

            if (currentVolume == 0) {
                gainControl.setValue(gainControl.getMinimum()); // Úplné ticho
            } else {
                // Převod procent (0-100) na decibely (logaritmická stupnice, jak ji slyší ucho)
                float db = (float) (Math.log10(currentVolume / 100.0) * 20.0);
                gainControl.setValue(Math.max(gainControl.getMinimum(), db));
            }
        }
    }
}