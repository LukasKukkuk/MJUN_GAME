package org.example.logic.managers;

import org.example.logic.core.ConfigManager;

import java.util.List;

public class AchievementManager {
    public static final List<Achievement> ALL = List.of(
            new Achievement("first_blood", "První pád", "Poraz Temného Stína.", false),
            new Achievement("wave_10", "Nezastavitelný", "Dosáhni vlny 10.", false),
            new Achievement("crafter", "Alchymista", "Úspěšně dokonči kování v kovárně.", false),
            new Achievement("coop_victory", "Týmová práce", "Poraz bosse v co-op módu.", false),
            new Achievement("true_ending", "Šťastné narozeniny, MJUNe!", "Poraz Temný Stín: Probuzení a dohraj hru do konce.", true)
    );

    // Vrátí Achievement, pokud byl právě odemčen poprvé (pro zobrazení toastu), jinak null.
    public static Achievement tryUnlock(String id) {
        boolean isNew = ConfigManager.unlockAchievement(id);
        if (!isNew) return null;
        for (Achievement a : ALL) {
            if (a.id.equals(id)) return a;
        }
        return null;
    }
}
