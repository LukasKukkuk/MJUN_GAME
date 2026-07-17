package org.example.logic.managers;

import org.example.logic.entities.Enemy;
import org.example.logic.enums.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class WaveManager {
    private int currentWave = 1;
    private int enemiesLeftToSpawn = 0;
    private long lastSpawnTime = 0;
    private final long spawnDelay = 800; // Prodleva mezi jednotlivými nepřáteli (v ms)

    // Zabraňuje dlouhým sériím stejného typu nepřítele za sebou (čirá náhoda
    // uměla nahodit vlnu samých ARCHERS/THIEVES a udělat obtížnost nezáměrně nárazovou).
    private Type lastSpawnedType = null;
    private int sameTypeStreak = 0;
    private static final int MAX_STREAK = 2;

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

        if (currentWave >= 2 && Math.random() < 0.15) {
            enemies.add(Enemy.createKamikaze(spawnX, spawnY, currentWave));
            return;
        }

        Type typeToSpawn = determineEnemyType();
        enemies.add(new Enemy(spawnX, spawnY, typeToSpawn, currentWave));
    }

    private Type determineEnemyType() {
        double rand = Math.random();
        Type candidate;

        if (currentWave == 1) {
            candidate = Type.THIEVES;
        } else if (currentWave == 2) {
            candidate = (rand < 0.3) ? Type.BANDITS : Type.THIEVES;
        } else if (rand < 0.2) {
            candidate = Type.BANDITS;
        } else if (rand < 0.5) {
            candidate = Type.ARCHERS;
        } else {
            candidate = Type.THIEVES;
        }

        if (candidate == lastSpawnedType && sameTypeStreak >= MAX_STREAK) {
            candidate = pickDifferentType(candidate);
        }

        if (candidate == lastSpawnedType) {
            sameTypeStreak++;
        } else {
            lastSpawnedType = candidate;
            sameTypeStreak = 1;
        }
        return candidate;
    }

    private Type pickDifferentType(Type exclude) {
        List<Type> pool = new ArrayList<>();
        if (currentWave == 2) {
            pool.add(Type.THIEVES);
            pool.add(Type.BANDITS);
        } else if (currentWave >= 3) {
            pool.add(Type.THIEVES);
            pool.add(Type.BANDITS);
            pool.add(Type.ARCHERS);
        }
        pool.remove(exclude);
        if (pool.isEmpty()) return exclude; // wave 1 má jen jeden dostupný typ
        return pool.get((int) (Math.random() * pool.size()));
    }

    public boolean isWaveFinished(CopyOnWriteArrayList<Enemy> enemies) {
        return enemiesLeftToSpawn <= 0 && enemies.isEmpty();
    }
}