package org.example.logic.managers;

import org.example.logic.entities.Enemy;
import org.example.logic.enums.Type;
import java.util.concurrent.CopyOnWriteArrayList;

public class WaveManager {
    private int currentWave = 1;
    private int enemiesLeftToSpawn = 0;
    private long lastSpawnTime = 0;
    private final long spawnDelay = 800; // Prodleva mezi jednotlivými nepřáteli (v ms)

    public void startNextWave(int wave) {
        this.currentWave = wave;
        this.enemiesLeftToSpawn = 5 + (wave * 3);
        System.out.println("Vlna " + wave + " začíná! Počet nepřátel k vyvolání: " + enemiesLeftToSpawn);
    }

    public void update(CopyOnWriteArrayList<Enemy> enemies, int width, int height) {
        long currentTime = System.currentTimeMillis();

        if (enemiesLeftToSpawn > 0 && currentTime - lastSpawnTime >= spawnDelay) {
            spawnSingleEnemy(enemies, width, height);
            enemiesLeftToSpawn--;
            lastSpawnTime = currentTime;
        }
    }

    private void spawnSingleEnemy(CopyOnWriteArrayList<Enemy> enemies, int width, int height) {
        double spawnX, spawnY;

        if (Math.random() < 0.5) {
            spawnX = Math.random() < 0.5 ? -50 : width + 50;
            spawnY = Math.random() * height;
        } else {
            spawnX = Math.random() * width;
            spawnY = Math.random() < 0.5 ? -50 : height + 50;
        }

        Type typeToSpawn = determineEnemyType();
        enemies.add(new Enemy(spawnX, spawnY, typeToSpawn, currentWave));
    }

    private Type determineEnemyType() {
        double rand = Math.random();

        if (currentWave == 1) return Type.THIEVES;
        if (currentWave == 2) return (rand < 0.3) ? Type.BANDITS : Type.THIEVES;

        if (rand < 0.2) return Type.BANDITS;
        if (rand < 0.5) return Type.ARCHERS;
        return Type.THIEVES;
    }

    public boolean isWaveFinished(CopyOnWriteArrayList<Enemy> enemies) {
        return enemiesLeftToSpawn <= 0 && enemies.isEmpty();
    }
}