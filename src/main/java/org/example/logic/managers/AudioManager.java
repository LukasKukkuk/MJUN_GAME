package org.example.logic.managers;

import javax.sound.sampled.*;
import java.net.URL;
import java.util.HashMap;

public class AudioManager {
    private final HashMap<String, Clip> audioCache = new HashMap<>();
    private Clip currentClip;
    private int currentVolume = 100;
    private String currentPlayingPath = null;

    public void preloadAudio() {
        System.out.println("Načítám hudbu do paměti...");
        // Vráceno na čisté MP3 soubory
        String[] tracks = {"/menu.mp3", "/level1.mp3", "/level2.mp3", "/level3boss.mp3", "/gameover.mp3"};

        for (String path : tracks) {
            try {
                URL url = getClass().getResource(path);
                if (url == null) continue;

                AudioInputStream in = AudioSystem.getAudioInputStream(url);

                // Čisté dekódování MP3
                AudioFormat baseFormat = in.getFormat();
                AudioFormat decodedFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED, baseFormat.getSampleRate(), 16,
                        baseFormat.getChannels(), baseFormat.getChannels() * 2, baseFormat.getSampleRate(), false
                );
                AudioInputStream din = AudioSystem.getAudioInputStream(decodedFormat, in);

                Clip clip = AudioSystem.getClip();
                clip.open(din);
                audioCache.put(path, clip);
                System.out.println("Načteno: " + path);
            } catch (Exception e) {
                System.out.println("Chyba při přednačítání " + path + ": " + e.getMessage());
            }
        }
    }

    public void playMusicForWave(int level) {
        String fileName = "/level" + level + ".mp3";
        if (level >= 3) fileName = "/level3boss.mp3";
        if (!audioCache.containsKey(fileName)) fileName = "/menu.mp3";
        playMusic(fileName, true);
    }

    public void playMenuMusic() { playMusic("/menu.mp3", true); }

    // Vráceno na gameover.mp3
    public void playGameOverMusic() { playMusic("/gameover.mp3", true); }

    public void stopMusic() {
        if (currentClip != null && currentClip.isRunning()) currentClip.stop();
        currentPlayingPath = null;
    }

    private void playMusic(String path, boolean loop) {
        if (path.equals(currentPlayingPath) && currentClip != null && currentClip.isRunning()) return;
        stopMusic();
        currentClip = audioCache.get(path);
        if (currentClip == null) return;

        currentPlayingPath = path;
        currentClip.setFramePosition(0);
        applyVolume();

        if (loop) currentClip.loop(Clip.LOOP_CONTINUOUSLY);
        else currentClip.start();
    }

    public void setVolume(int volume) {
        this.currentVolume = volume;
        applyVolume();
    }

    private void applyVolume() {
        if (currentClip != null && currentClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gainControl = (FloatControl) currentClip.getControl(FloatControl.Type.MASTER_GAIN);
            if (currentVolume == 0) gainControl.setValue(gainControl.getMinimum());
            else {
                float db = (float) (Math.log10(currentVolume / 100.0) * 20.0);
                gainControl.setValue(Math.max(gainControl.getMinimum(), db));
            }
        }
    }
}